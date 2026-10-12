"""Outbox transacional e operacao confiavel dos envios externos."""

from __future__ import annotations

import json
import logging
import os
import socket
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Callable

from sqlalchemy import text
from sqlalchemy.orm import Session

from chatbot.models.mensagem import DirecaoMensagem, Mensagem, RemetenteMensagem, TipoMensagem
from chatbot.services import whatsapp_service
from chatbot.tenant import tenant_id

logger = logging.getLogger(__name__)
WORKER_ID = f"{socket.gethostname()}:{os.getpid()}"
CIRCUIT_FAILURE_THRESHOLD = int(os.getenv("MENSAGERIA_CIRCUIT_FAILURE_THRESHOLD", "5"))
CIRCUIT_OPEN_SECONDS = int(os.getenv("MENSAGERIA_CIRCUIT_OPEN_SECONDS", "300"))


@dataclass(frozen=True)
class ErrorDecision:
    transient: bool
    category: str
    code: str


def classify_error(error: Exception) -> ErrorDecision:
    if isinstance(error, whatsapp_service.WhatsAppNaoConfiguradoError):
        return ErrorDecision(False, "configuracao", "nao_configurado")
    if isinstance(error, whatsapp_service.WhatsAppError):
        status = error.status
        transient = status is None or status in (408, 425, 429) or status >= 500
        return ErrorDecision(transient, "transitoria" if transient else "permanente", str(error.codigo or status or "rede"))
    if isinstance(error, TimeoutError):
        return ErrorDecision(True, "transitoria", "timeout")
    return ErrorDecision(True, "transitoria", type(error).__name__)


def retry_delay_seconds(attempt: int) -> int:
    """Backoff exponencial limitado a uma hora; puro para ser testavel."""
    return min(3600, 15 * (2 ** max(0, attempt - 1)))


def enqueue_text(
    db: Session, *, idempotency_key: str, telefone: str, texto_mensagem: str,
    paciente_id: int | None, remetente: RemetenteMensagem, tentativa_id: int,
    consentimento_id: int | None, modelo_id: int | None, modelo_versao: int | None,
    finalidade: str, origem: str, id_campanha: int | None = None,
) -> tuple[Mensagem, bool]:
    key = idempotency_key.strip()
    if not key or len(key) > 160:
        raise ValueError("Chave de idempotência inválida.")
    telefone = whatsapp_service.normalizar_telefone(telefone)
    payload = json.dumps({"texto": texto_mensagem}, ensure_ascii=False)
    inserted = db.execute(text("""
        INSERT INTO mensageria_outbox(
            idempotency_key,canal,provedor,destino,tipo,payload,origem,id_campanha
        ) VALUES (:key,'whatsapp','meta',:destino,'texto',CAST(:payload AS jsonb),:origem,:campanha)
        ON CONFLICT(id_clinica,idempotency_key) DO NOTHING
        RETURNING id_outbox
    """), {"key": key, "destino": telefone, "payload": payload, "origem": origem, "campanha": id_campanha}).scalar_one_or_none()
    created = inserted is not None
    outbox_id = inserted
    if outbox_id is None:
        row = db.execute(text("""
            SELECT id_outbox,destino,payload->>'texto' AS texto
            FROM mensageria_outbox WHERE idempotency_key=:key
        """), {"key": key}).mappings().one()
        if row["destino"] != telefone or row["texto"] != texto_mensagem:
            db.rollback()
            raise ValueError("A chave de idempotência já foi usada em outra mensagem.")
        outbox_id = row["id_outbox"]

    mensagem = db.execute(text("""
        SELECT id_mensagem FROM mensagem WHERE id_outbox=:outbox
    """), {"outbox": outbox_id}).scalar_one_or_none()
    if mensagem is None:
        nova = Mensagem(
            id_paciente=paciente_id, telefone=telefone, direcao=DirecaoMensagem.saida,
            tipo=TipoMensagem.texto, conteudo=texto_mensagem, remetente=remetente,
            id_tentativa_comunicacao=tentativa_id, finalidade_comunicacao=finalidade,
            id_consentimento=consentimento_id, id_modelo_comunicacao=modelo_id,
            modelo_versao=modelo_versao, id_outbox=outbox_id, status_entrega="pendente",
        )
        db.add(nova)
        db.flush()
        mensagem_obj = nova
    else:
        mensagem_obj = db.get(Mensagem, mensagem)
    if created:
        _event(db, outbox_id, "enfileirada", {"origem": origem})
    db.commit()
    db.refresh(mensagem_obj)
    return mensagem_obj, created


def existing_message(db: Session, idempotency_key: str, telefone: str, texto_mensagem: str) -> Mensagem | None:
    row = db.execute(text("""
        SELECT o.id_outbox,o.destino,o.payload->>'texto' AS texto,m.id_mensagem
        FROM mensageria_outbox o LEFT JOIN mensagem m ON m.id_outbox=o.id_outbox
        WHERE o.idempotency_key=:key
    """), {"key": idempotency_key}).mappings().first()
    if row is None:
        return None
    if row["destino"] != whatsapp_service.normalizar_telefone(telefone) or row["texto"] != texto_mensagem:
        raise ValueError("A chave de idempotência já foi usada em outra mensagem.")
    return db.get(Mensagem, row["id_mensagem"]) if row["id_mensagem"] else None


def process_one(db: Session, sender: Callable[[str, str], str] | None = None) -> bool:
    """Processa no maximo um item devido. Nunca mantem lock durante HTTP."""
    recover_stuck(db)
    row = db.execute(text("""
        SELECT * FROM mensageria_outbox
        WHERE status IN ('pendente','retentativa') AND disponivel_em <= now()
        ORDER BY disponivel_em,id_outbox
        FOR UPDATE SKIP LOCKED LIMIT 1
    """)).mappings().first()
    if row is None:
        db.rollback()
        return False
    outbox_id = row["id_outbox"]
    if _circuit_open(db, row["provedor"]):
        _reschedule(db, outbox_id, 30, "circuito_aberto", increment=False)
        return True
    wait_seconds = _rate_limit_wait(db, row)
    if wait_seconds:
        _reschedule(db, outbox_id, wait_seconds, "limitada", increment=False)
        return True
    db.execute(text("""
        UPDATE mensageria_outbox SET status='processando',bloqueado_em=now(),bloqueado_por=:worker,
            atualizado_em=now() WHERE id_outbox=:id
    """), {"worker": WORKER_ID, "id": outbox_id})
    _event(db, outbox_id, "adquirida", {"worker": WORKER_ID})
    db.commit()

    send = sender or whatsapp_service.send_message_sync
    try:
        provider_id = send(row["destino"], row["payload"]["texto"])
    except Exception as error:  # classificacao abaixo decide retry versus DLQ
        _record_failure(db, row, error)
        return True

    db.execute(text("""
        UPDATE mensageria_outbox SET status='enviada',tentativas=tentativas+1,
            id_mensagem_provedor=:provider,enviado_em=now(),atualizado_em=now(),
            bloqueado_em=NULL,bloqueado_por=NULL,erro_categoria=NULL,erro_codigo=NULL,erro_mensagem=NULL
        WHERE id_outbox=:id
    """), {"provider": provider_id, "id": outbox_id})
    db.execute(text("""
        UPDATE mensagem SET status_entrega='enviada',id_mensagem_provedor=:provider
        WHERE id_outbox=:id
    """), {"provider": provider_id, "id": outbox_id})
    _event(db, outbox_id, "enviada", {"idMensagemProvedor": provider_id})
    _close_circuit(db, row["provedor"])
    db.commit()
    return True


def _record_failure(db: Session, row, error: Exception) -> None:
    decision = classify_error(error)
    attempt = row["tentativas"] + 1
    dead = not decision.transient or attempt >= row["max_tentativas"]
    status = "morta" if dead else "retentativa"
    delay = 0 if dead else retry_delay_seconds(attempt)
    db.execute(text("""
        UPDATE mensageria_outbox SET status=:status,tentativas=:attempt,
            disponivel_em=now()+(:delay * interval '1 second'),atualizado_em=now(),
            bloqueado_em=NULL,bloqueado_por=NULL,erro_categoria=:category,
            erro_codigo=:code,erro_mensagem=:message WHERE id_outbox=:id
    """), {"status": status, "attempt": attempt, "delay": delay, "category": decision.category,
           "code": decision.code[:80], "message": str(error)[:800], "id": row["id_outbox"]})
    if dead:
        db.execute(text("UPDATE mensagem SET status_entrega='falha' WHERE id_outbox=:id"), {"id": row["id_outbox"]})
    _event(db, row["id_outbox"], "morta" if dead else "retentativa", {
        "tentativa": attempt, "codigo": decision.code, "proximaEmSegundos": delay,
    })
    if decision.transient:
        _fail_circuit(db, row["provedor"], str(error))
    db.commit()


def _event(db: Session, outbox_id: int, event_type: str, details: dict | None = None, user_id: int | None = None) -> None:
    db.execute(text("""
        INSERT INTO mensageria_evento(id_outbox,tipo,detalhes,id_usuario)
        VALUES (:outbox,:tipo,CAST(:details AS jsonb),:user)
    """), {"outbox": outbox_id, "tipo": event_type,
           "details": json.dumps(details or {}, ensure_ascii=False), "user": user_id})


def _reschedule(db: Session, outbox_id: int, seconds: int, reason: str, *, increment: bool) -> None:
    db.execute(text("""
        UPDATE mensageria_outbox SET status='retentativa',disponivel_em=now()+(:seconds * interval '1 second'),
            tentativas=tentativas+:increment,atualizado_em=now(),bloqueado_em=NULL,bloqueado_por=NULL
        WHERE id_outbox=:id
    """), {"seconds": seconds, "increment": 1 if increment else 0, "id": outbox_id})
    _event(db, outbox_id, reason, {"proximaEmSegundos": seconds})
    db.commit()


def _rate_limit_wait(db: Session, row) -> int:
    # O lock serializa a reserva de capacidade entre múltiplos workers.
    limits = db.execute(text("SELECT * FROM mensageria_limite WHERE ativo ORDER BY janela_segundos FOR UPDATE")).mappings().all()
    keys = {"clinica": "*", "numero": row["destino"], "campanha": str(row["id_campanha"] or ""), "provedor": row["provedor"]}
    for limit in limits:
        key = keys[limit["escopo"]]
        if limit["chave"] not in ("*", key):
            continue
        filters = {"numero": "AND o.destino=:key", "campanha": "AND o.id_campanha::text=:key", "provedor": "AND o.provedor=:key"}.get(limit["escopo"], "")
        count = db.execute(text(f"""
            SELECT count(*) FROM mensageria_evento e JOIN mensageria_outbox o USING(id_clinica,id_outbox)
            WHERE e.tipo='adquirida' AND e.criado_em >= now()-(:window * interval '1 second') {filters}
        """), {"window": limit["janela_segundos"], "key": key}).scalar_one()
        if count >= limit["max_envios"]:
            return min(300, max(1, limit["janela_segundos"] // 4))
    return 0


def _circuit_open(db: Session, provider: str) -> bool:
    row = db.execute(text("SELECT estado,aberto_ate FROM mensageria_provedor_estado WHERE provedor=:p"), {"p": provider}).first()
    if not row or row.estado == "fechado":
        return False
    if row.aberto_ate and row.aberto_ate <= datetime.now(timezone.utc):
        db.execute(text("UPDATE mensageria_provedor_estado SET estado='meio_aberto',atualizado_em=now() WHERE provedor=:p"), {"p": provider})
        return False
    return True


def _fail_circuit(db: Session, provider: str, error: str) -> None:
    db.execute(text("""
        INSERT INTO mensageria_provedor_estado(id_clinica,provedor,falhas_consecutivas,ultimo_erro)
        VALUES (:tenant,:provider,1,:error)
        ON CONFLICT(id_clinica,provedor) DO UPDATE SET
          falhas_consecutivas=mensageria_provedor_estado.falhas_consecutivas+1,
          estado=CASE WHEN mensageria_provedor_estado.falhas_consecutivas+1>=:threshold THEN 'aberto' ELSE mensageria_provedor_estado.estado END,
          aberto_ate=CASE WHEN mensageria_provedor_estado.falhas_consecutivas+1>=:threshold
            THEN now()+(:seconds * interval '1 second') ELSE mensageria_provedor_estado.aberto_ate END,
          ultimo_erro=:error,atualizado_em=now()
    """), {"tenant": tenant_id(), "provider": provider, "error": error[:800],
           "threshold": CIRCUIT_FAILURE_THRESHOLD, "seconds": CIRCUIT_OPEN_SECONDS})


def _close_circuit(db: Session, provider: str) -> None:
    db.execute(text("""
        INSERT INTO mensageria_provedor_estado(id_clinica,provedor,estado,falhas_consecutivas)
        VALUES (:tenant,:provider,'fechado',0)
        ON CONFLICT(id_clinica,provedor) DO UPDATE SET estado='fechado',falhas_consecutivas=0,
          aberto_ate=NULL,ultimo_erro=NULL,atualizado_em=now()
    """), {"tenant": tenant_id(), "provider": provider})


def recover_stuck(db: Session) -> None:
    rows = db.execute(text("""
        UPDATE mensageria_outbox SET status='retentativa',disponivel_em=now(),
          bloqueado_em=NULL,bloqueado_por=NULL,atualizado_em=now(),erro_categoria='worker',erro_codigo='lock_expirado'
        WHERE status='processando' AND bloqueado_em < now()-interval '5 minutes'
        RETURNING id_outbox
    """)).scalars().all()
    for outbox_id in rows:
        _event(db, outbox_id, "retentativa", {"motivo": "lock_expirado"})
    if rows:
        db.commit()


def reprocess(db: Session, outbox_id: int, user_id: int | None = None) -> bool:
    changed = db.execute(text("""
        UPDATE mensageria_outbox SET status='retentativa',tentativas=0,disponivel_em=now(),
          bloqueado_em=NULL,bloqueado_por=NULL,erro_categoria=NULL,erro_codigo=NULL,
          erro_mensagem=NULL,atualizado_em=now() WHERE id_outbox=:id AND status='morta'
        RETURNING id_outbox
    """), {"id": outbox_id}).scalar_one_or_none()
    if changed is None:
        db.rollback()
        return False
    db.execute(text("UPDATE mensagem SET status_entrega='pendente' WHERE id_outbox=:id"), {"id": outbox_id})
    _event(db, outbox_id, "reprocessada", {}, user_id)
    db.commit()
    return True


def operation_summary(db: Session) -> dict:
    counts = dict(db.execute(text("SELECT status,count(*) FROM mensageria_outbox GROUP BY status")).all())
    oldest = db.execute(text("""
        SELECT coalesce(extract(epoch FROM now()-min(criado_em))::integer,0)
        FROM mensageria_outbox WHERE status IN ('pendente','retentativa','processando')
    """)).scalar_one()
    state = db.execute(text("SELECT * FROM mensageria_provedor_estado WHERE provedor='meta'")).mappings().first()
    hour = db.execute(text("""
        SELECT count(*) FILTER(WHERE tipo='enviada') AS enviadas,
          count(*) FILTER(WHERE tipo IN ('retentativa','morta')) AS erros
        FROM mensageria_evento WHERE criado_em >= now()-interval '1 hour'
    """)).mappings().one()
    latency = db.execute(text("""
        SELECT coalesce(avg(extract(epoch FROM enviado_em-criado_em))::integer,0)
        FROM mensageria_outbox WHERE status='enviada' AND enviado_em >= now()-interval '1 hour'
    """)).scalar_one()
    stuck = db.execute(text("""
        SELECT count(*) FROM mensageria_outbox
        WHERE status='processando' AND bloqueado_em < now()-interval '5 minutes'
    """)).scalar_one()
    alerts = []
    if counts.get("morta", 0): alerts.append(f"{counts['morta']} envio(s) precisam de intervenção")
    if oldest > 300: alerts.append("A fila possui mensagens esperando há mais de 5 minutos")
    if state and state["estado"] != "fechado": alerts.append("Circuito do provedor está aberto")
    if stuck: alerts.append(f"{stuck} envio(s) ficaram presos em processamento")
    return {"fila": counts.get("pendente", 0), "retentativas": counts.get("retentativa", 0),
            "mortas": counts.get("morta", 0), "processando": counts.get("processando", 0),
            "enviadas": counts.get("enviada", 0), "idadeMaisAntigaSeg": oldest,
            "enviadasUltimaHora": hour["enviadas"], "errosUltimaHora": hour["erros"],
            "latenciaMediaSeg": latency, "travadas": stuck,
            "provedor": dict(state) if state else {"provedor": "meta", "estado": "fechado", "falhas_consecutivas": 0},
            "alertas": alerts}


def list_outbox(db: Session, status: str | None = None, limit: int = 100) -> list[dict]:
    condition = "WHERE status=:status" if status else ""
    rows = db.execute(text(f"""
        SELECT id_outbox,idempotency_key,destino,origem,status,tentativas,max_tentativas,
          disponivel_em,criado_em,enviado_em,erro_codigo,erro_mensagem
        FROM mensageria_outbox {condition}
        ORDER BY criado_em DESC LIMIT :limit
    """), {"status": status, "limit": min(max(limit, 1), 200)}).mappings().all()
    return [dict(row) for row in rows]


def list_limits(db: Session) -> list[dict]:
    rows = db.execute(text("""
        SELECT id_limite,escopo,chave,janela_segundos,max_envios,ativo,atualizado_em
        FROM mensageria_limite ORDER BY escopo,chave
    """)).mappings().all()
    return [dict(row) for row in rows]


def upsert_limit(db: Session, scope: str, key: str, window_seconds: int, max_sends: int, active: bool) -> dict:
    row = db.execute(text("""
        INSERT INTO mensageria_limite(escopo,chave,janela_segundos,max_envios,ativo)
        VALUES (:scope,:key,:window,:max,:active)
        ON CONFLICT(id_clinica,escopo,chave) DO UPDATE SET janela_segundos=excluded.janela_segundos,
          max_envios=excluded.max_envios,ativo=excluded.ativo,atualizado_em=now()
        RETURNING id_limite,escopo,chave,janela_segundos,max_envios,ativo,atualizado_em
    """), {"scope": scope, "key": key.strip(), "window": window_seconds, "max": max_sends, "active": active}).mappings().one()
    db.commit()
    return dict(row)


def register_incoming(db: Session, external_id: str) -> bool:
    inserted = db.execute(text("""
        INSERT INTO mensageria_evento_recebido(provedor,id_externo)
        VALUES ('meta',:external) ON CONFLICT(id_clinica,provedor,id_externo) DO UPDATE
          SET status='processando',erro=NULL,processado_em=NULL
          WHERE mensageria_evento_recebido.status='erro'
        RETURNING id_recebido
    """), {"external": external_id}).scalar_one_or_none()
    db.commit()
    return inserted is not None


def finish_incoming(db: Session, external_id: str, error: str | None = None) -> None:
    db.execute(text("""
        UPDATE mensageria_evento_recebido SET status=:status,erro=:error,processado_em=now()
        WHERE provedor='meta' AND id_externo=:external
    """), {"status": "erro" if error else "processado", "error": error[:800] if error else None, "external": external_id})
    db.commit()
