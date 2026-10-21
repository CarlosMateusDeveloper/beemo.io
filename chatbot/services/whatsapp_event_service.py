"""Status da Meta e correlação de respostas com o fluxo de pós-venda."""

import json
from datetime import datetime, timezone

from sqlalchemy import text
from sqlalchemy.orm import Session

from chatbot.tenant import tenant_id
from chatbot.views.webhook import StatusRecebido


STATUS_ORDER = {"sent": 1, "delivered": 2, "read": 3}
STATUS_DB = {"sent": "enviada", "delivered": "entregue", "read": "lida", "failed": "falha"}


def process_statuses(db: Session, statuses: list[StatusRecebido]) -> None:
    for status in statuses:
        db_status = STATUS_DB.get(status.status)
        if not db_status:
            continue
        occurred = datetime.fromtimestamp(int(status.timestamp), timezone.utc) if status.timestamp else datetime.now(timezone.utc)
        error = status.errors[0] if status.errors else None
        inserted = db.execute(text("""
            INSERT INTO whatsapp_status_evento(
              id_mensagem_provedor,status,ocorrido_em,erro_codigo,erro_titulo,erro_detalhe,payload
            ) VALUES (:provider,:status,:occurred,:code,:title,:detail,CAST(:payload AS jsonb))
            ON CONFLICT(id_clinica,id_mensagem_provedor,status,ocorrido_em) DO NOTHING
            RETURNING id_evento
        """), {
            "provider": status.id, "status": db_status, "occurred": occurred,
            "code": str(error.code) if error and error.code is not None else None,
            "title": error.title[:300] if error and error.title else None,
            "detail": error.message[:800] if error and error.message else None,
            "payload": json.dumps(status.model_dump(), ensure_ascii=False),
        }).scalar_one_or_none()
        if inserted is None:
            continue
        current = db.execute(text("SELECT status_entrega,id_campanha,id_paciente FROM mensagem WHERE id_mensagem_provedor=:id"), {"id": status.id}).mappings().first()
        if current and (db_status == "falha" or _can_advance(current["status_entrega"], db_status)):
            db.execute(text("UPDATE mensagem SET status_entrega=:status WHERE id_mensagem_provedor=:id"), {"status": db_status, "id": status.id})
            if current["id_campanha"] and db_status in ("entregue", "lida"):
                changed = db.execute(text("""
                    UPDATE pos_venda_campanha_publico SET status='entregue'
                    WHERE id_campanha=:campaign AND id_paciente=:patient AND status IN ('incluido','enviado')
                    RETURNING id_paciente
                """), {"campaign": current["id_campanha"], "patient": current["id_paciente"]})
                if changed.scalar_one_or_none():
                    db.execute(text("""
                        UPDATE pos_venda_campanha SET entregues=(SELECT count(*) FROM pos_venda_campanha_publico
                          WHERE id_campanha=:campaign AND status IN ('entregue','respondido','convertido'))
                        WHERE id_campanha=:campaign
                    """), {"campaign": current["id_campanha"]})
    db.commit()


def _can_advance(current: str, new: str) -> bool:
    order = {"pendente": 0, "enviada": 1, "entregue": 2, "lida": 3}
    return order.get(new, -1) >= order.get(current, -1)


def correlate_incoming(db: Session, message_id: int, phone: str, wamid: str) -> int | None:
    """Liga uma resposta ao único caso aberto; ambiguidade fica em revisão humana."""
    context = db.execute(text("""
        SELECT id_pos_venda_caso,id_campanha,id_jornada_inscricao,id_paciente
        FROM mensagem WHERE telefone=:phone AND direcao='saida'
          AND criado_em>=now()-interval '30 days'
          AND (id_pos_venda_caso IS NOT NULL OR id_campanha IS NOT NULL OR id_jornada_inscricao IS NOT NULL)
        ORDER BY criado_em DESC,id_mensagem DESC LIMIT 1
    """), {"phone": phone}).mappings().first()
    if context:
        db.execute(text("""
            UPDATE mensagem SET id_campanha=:campaign,id_jornada_inscricao=:journey
            WHERE id_mensagem=:message
        """), {"campaign": context["id_campanha"], "journey": context["id_jornada_inscricao"], "message": message_id})
        if context["id_campanha"] and context["id_paciente"]:
            changed = db.execute(text("""
                UPDATE pos_venda_campanha_publico SET status='respondido'
                WHERE id_campanha=:campaign AND id_paciente=:patient
                  AND status IN ('incluido','enviado','entregue') RETURNING id_paciente
            """), {"campaign": context["id_campanha"], "patient": context["id_paciente"]}).scalar_one_or_none()
            if changed:
                db.execute(text("""
                    UPDATE pos_venda_campanha SET respostas=(SELECT count(*) FROM pos_venda_campanha_publico
                      WHERE id_campanha=:campaign AND status IN ('respondido','convertido'))
                    WHERE id_campanha=:campaign
                """), {"campaign": context["id_campanha"]})
    candidates = db.execute(text("""
        SELECT DISTINCT pv.id,pv.id_responsavel,p.id_paciente AS patient_id,
          coalesce(p.nome,pe.nome) AS patient_name
        FROM pos_venda_caso pv
        LEFT JOIN consulta c ON c.id_consulta=pv.id_consulta_origem
        LEFT JOIN paciente p ON p.id_paciente=c.id_paciente
        LEFT JOIN integracao_atendimento_externo ae ON ae.id_atendimento_externo=pv.id_atendimento_externo_origem
        LEFT JOIN integracao_paciente_externo pe ON pe.id_paciente_externo=ae.id_paciente_externo
        WHERE pv.status IN ('pendente','em_contato','sem_resposta','nova_falta','reagendado')
          AND (:preferred IS NULL OR pv.id=:preferred)
          AND regexp_replace(coalesce(nullif(concat(p.ddd,p.numero),''),pe.telefone,''),'\\D','','g')
              IN (:phone,:without_country)
        ORDER BY pv.id DESC
    """), {"phone": phone, "without_country": phone[2:] if phone.startswith("55") else phone,
            "preferred": context["id_pos_venda_caso"] if context else None}).mappings().all()
    if len(candidates) != 1:
        if len(candidates) > 1:
            db.execute(text("""
                INSERT INTO whatsapp_associacao_pendente(id_mensagem,telefone,casos_candidatos)
                VALUES (:message,:phone,CAST(:cases AS jsonb)) ON CONFLICT(id_clinica,id_mensagem) DO NOTHING
            """), {"message": message_id, "phone": phone,
                    "cases": json.dumps([{"id": row["id"], "paciente": row["patient_name"]} for row in candidates], ensure_ascii=False)})
            owner = db.execute(text("SELECT id_usuario FROM tenant_membro WHERE ativo ORDER BY id_usuario LIMIT 1")).scalar_one_or_none()
            if owner:
                db.execute(text("""
                    INSERT INTO pos_venda_tarefa(
                      paciente_referencia,titulo,descricao,id_responsavel,prioridade,prazo,origem,criado_por,atualizado_por
                    ) VALUES (:reference,'Revisar vínculo de resposta do WhatsApp',
                      'O telefone possui mais de um caso aberto. Selecione o caso correto antes de responder.',
                      :owner,'alta',now(),:origin,:owner,:owner)
                    ON CONFLICT(id_clinica,origem) WHERE origem LIKE 'whatsapp_resposta:%' DO NOTHING
                """), {"reference": candidates[0]["patient_name"], "owner": owner,
                        "origin": f"whatsapp_resposta:{wamid}"})
        db.commit()
        return None
    case = candidates[0]
    db.execute(text("UPDATE mensagem SET id_pos_venda_caso=:case WHERE id_mensagem=:message"), {"case": case["id"], "message": message_id})
    db.execute(text("""
        INSERT INTO pos_venda_evento(id_caso,tipo,canal,descricao)
        VALUES (:case,'resposta','whatsapp','Resposta recebida pelo WhatsApp.')
    """), {"case": case["id"]})
    db.execute(text("""
        UPDATE pos_venda_caso SET status=CASE WHEN status='reagendado' THEN status ELSE 'em_contato' END,
          proxima_acao=now() WHERE id=:case
    """), {"case": case["id"]})
    _cancel_pending_case_messages(db, case["id"], "resposta_recebida")
    db.execute(text("""
        INSERT INTO pos_venda_alerta(id_caso,tipo,mensagem)
        VALUES (:case,'resposta_pendente','O paciente respondeu no WhatsApp e aguarda a equipe.')
        ON CONFLICT(id_clinica,id_caso,tipo) WHERE status='aberto' DO NOTHING
    """), {"case": case["id"]})
    owner = case["id_responsavel"] or db.execute(text("""
        SELECT id_usuario FROM tenant_membro WHERE ativo ORDER BY id_usuario LIMIT 1
    """)).scalar_one_or_none()
    if owner:
        db.execute(text("""
            INSERT INTO pos_venda_tarefa(
              id_paciente,paciente_referencia,id_caso,titulo,descricao,id_responsavel,prioridade,
              prazo,origem,criado_por,atualizado_por
            ) VALUES (:patient,:reference,:case,'Responder paciente no WhatsApp',
              'Há uma nova resposta vinculada ao caso de pós-venda.',:owner,'alta',now(),:origin,:owner,:owner)
            ON CONFLICT(id_clinica,origem) WHERE origem LIKE 'whatsapp_resposta:%' DO NOTHING
        """), {"patient": case["patient_id"],
                "reference": case["patient_name"], "case": case["id"], "owner": owner,
                "origin": f"whatsapp_resposta:{wamid}"})
    db.commit()
    return case["id"]


def correlate_crm_incoming(db: Session, message_id: int, phone: str, wamid: str, patient_id: int | None = None) -> int | None:
    """Cria ou reutiliza a oportunidade comercial da conversa sem transformar telefone em identidade."""
    conversation_key = f"whatsapp:{phone}"
    db.execute(text("SELECT pg_advisory_xact_lock(hashtextextended(:key,:tenant))"),
               {"key": conversation_key, "tenant": tenant_id()})
    opportunity = db.execute(text("""
        SELECT o.id_oportunidade,o.etapa FROM crm_oportunidade o
        WHERE o.conversa_externa_id=:conversation AND o.etapa NOT IN ('agendado','perdido')
        ORDER BY o.id_oportunidade DESC LIMIT 1
    """), {"conversation": conversation_key}).mappings().first()
    # Uma conversa assistencial de paciente já conhecido não vira lead por padrão.
    # Se já houver oportunidade aberta, a conversa continua nela.
    if opportunity is None and patient_id is not None:
        return None
    if opportunity is None:
        interested = db.execute(text("""
            INSERT INTO crm_interessado(nome,telefone,canal_preferido,contato_permitido)
            VALUES (:name,:phone,'whatsapp',true) RETURNING id_interessado
        """), {"name": f"Contato WhatsApp •{phone[-4:]}", "phone": phone}).scalar_one()
        opportunity_id = db.execute(text("""
            INSERT INTO crm_oportunidade(
              id_interessado,canal_origem,origem,servico_interesse,idempotency_key,
              conversa_externa_id,primeiro_contato_em
            ) VALUES (:interested,'whatsapp','WhatsApp','A definir',:key,:conversation,now())
            RETURNING id_oportunidade
        """), {"interested": interested, "key": f"whatsapp:{wamid}",
                "conversation": conversation_key}).scalar_one()
        db.execute(text("""
            INSERT INTO crm_oportunidade_evento(id_oportunidade,tipo,etapa_nova,automatico,dados)
            VALUES (:opportunity,'criada','novo',true,CAST(:data AS jsonb))
        """), {"opportunity": opportunity_id,
                "data": json.dumps({"origem": "WhatsApp", "wamid": wamid}, ensure_ascii=False)})
    else:
        opportunity_id = opportunity["id_oportunidade"]
    db.execute(text("UPDATE mensagem SET id_crm_oportunidade=:opportunity WHERE id_mensagem=:message"),
               {"opportunity": opportunity_id, "message": message_id})
    db.execute(text("""
        INSERT INTO crm_oportunidade_evento(id_oportunidade,tipo,automatico,id_mensagem,dados)
        VALUES (:opportunity,'mensagem_entrada',true,:message,CAST(:data AS jsonb))
        ON CONFLICT(id_clinica,id_mensagem,tipo) WHERE id_mensagem IS NOT NULL DO NOTHING
    """), {"opportunity": opportunity_id, "message": message_id,
            "data": json.dumps({"canal": "whatsapp", "wamid": wamid}, ensure_ascii=False)})
    candidates = db.execute(text("""
        SELECT id_paciente AS id,nome FROM paciente
        WHERE regexp_replace(concat(ddd,numero),'\\D','','g') IN (:phone,:without_country)
        ORDER BY id_paciente LIMIT 10
    """), {"phone": phone, "without_country": phone[2:] if phone.startswith("55") else phone}).mappings().all()
    if len(candidates) > 1:
        db.execute(text("""
            INSERT INTO crm_identidade_revisao(id_oportunidade,motivo,candidatos)
            VALUES (:opportunity,'telefone_compartilhado',CAST(:candidates AS jsonb))
            ON CONFLICT(id_clinica,id_oportunidade,motivo) WHERE status='pendente'
            DO UPDATE SET candidatos=excluded.candidatos
        """), {"opportunity": opportunity_id,
                "candidates": json.dumps([{"id": row["id"], "nome": row["nome"]} for row in candidates], ensure_ascii=False)})
    db.commit()
    return opportunity_id


def link_crm_outgoing(db: Session, message_id: int, phone: str, automatic: bool) -> int | None:
    opportunity = db.execute(text("""
        SELECT o.id_oportunidade,o.etapa FROM crm_oportunidade o JOIN crm_interessado i ON i.id_interessado=o.id_interessado
        WHERE i.telefone=:phone AND o.etapa NOT IN ('agendado','perdido')
        ORDER BY o.id_oportunidade DESC LIMIT 2
    """), {"phone": phone}).mappings().all()
    if len(opportunity) != 1:
        return None
    opportunity_id = opportunity[0]["id_oportunidade"]
    event_type = "resposta_automatica" if automatic else "resposta_humana"
    db.execute(text("UPDATE mensagem SET id_crm_oportunidade=:opportunity WHERE id_mensagem=:message"),
               {"opportunity": opportunity_id, "message": message_id})
    db.execute(text("""
        UPDATE crm_oportunidade SET
          primeira_resposta_automatica_em=CASE WHEN :automatic THEN coalesce(primeira_resposta_automatica_em,now()) ELSE primeira_resposta_automatica_em END,
          primeira_resposta_humana_em=CASE WHEN NOT :automatic THEN coalesce(primeira_resposta_humana_em,now()) ELSE primeira_resposta_humana_em END,
          etapa=CASE WHEN NOT :automatic AND etapa='novo' THEN 'em_contato' ELSE etapa END
        WHERE id_oportunidade=:opportunity
    """), {"automatic": automatic, "opportunity": opportunity_id})
    db.execute(text("""
        INSERT INTO crm_oportunidade_evento(id_oportunidade,tipo,etapa_nova,automatico,id_mensagem,dados)
        VALUES (:opportunity,:type,
          CASE WHEN NOT :automatic AND :stage='novo' THEN 'em_contato' ELSE :stage END,
          :automatic,:message,'{"canal":"whatsapp"}'::jsonb)
    """), {"opportunity": opportunity_id, "type": event_type, "automatic": automatic,
            "message": message_id, "stage": opportunity[0]["etapa"]})
    return opportunity_id


def close_contact_after_optout(db: Session, phone: str) -> None:
    db.execute(text("""
        UPDATE pos_venda_caso pv SET status='nao_contatar',proxima_acao=NULL
        FROM consulta c JOIN paciente p ON p.id_paciente=c.id_paciente
        WHERE c.id_consulta=pv.id_consulta_origem
          AND regexp_replace(concat(p.ddd,p.numero),'\\D','','g') IN (:phone,:without_country)
          AND pv.status NOT IN ('recuperado','nao_deseja','nao_contatar','registro_corrigido')
    """), {"phone": phone, "without_country": phone[2:] if phone.startswith("55") else phone})
    db.execute(text("""
        UPDATE pos_venda_caso pv SET status='nao_contatar',proxima_acao=NULL
        FROM integracao_atendimento_externo ae
        JOIN integracao_paciente_externo pe ON pe.id_paciente_externo=ae.id_paciente_externo
        WHERE ae.id_atendimento_externo=pv.id_atendimento_externo_origem
          AND regexp_replace(pe.telefone,'\\D','','g') IN (:phone,:without_country)
          AND pv.status NOT IN ('recuperado','nao_deseja','nao_contatar','registro_corrigido')
    """), {"phone": phone, "without_country": phone[2:] if phone.startswith("55") else phone})
    db.execute(text("""
        UPDATE mensageria_outbox SET status='cancelada',atualizado_em=now(),erro_categoria='consentimento',
          erro_codigo='descadastro',erro_mensagem='Paciente solicitou descadastro no WhatsApp.'
        WHERE destino=:phone AND status IN ('pendente','retentativa')
    """), {"phone": phone})
    db.execute(text("""
        UPDATE mensagem m SET status_entrega='cancelada' FROM mensageria_outbox o
        WHERE m.id_outbox=o.id_outbox AND o.destino=:phone AND o.status='cancelada'
          AND m.status_entrega='pendente'
    """), {"phone": phone})
    db.commit()


def _cancel_pending_case_messages(db: Session, case_id: int, reason: str) -> None:
    db.execute(text("""
        UPDATE mensageria_outbox SET status='cancelada',atualizado_em=now(),erro_categoria='fluxo',
          erro_codigo=:reason,erro_mensagem='Envio cancelado após atualização do caso de pós-venda.'
        WHERE id_pos_venda_caso=:case AND status IN ('pendente','retentativa')
    """), {"case": case_id, "reason": reason})
    db.execute(text("""
        UPDATE mensagem m SET status_entrega='cancelada' FROM mensageria_outbox o
        WHERE m.id_outbox=o.id_outbox AND o.id_pos_venda_caso=:case
          AND o.status='cancelada' AND m.status_entrega='pendente'
    """), {"case": case_id})
