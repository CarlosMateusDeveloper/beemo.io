"""Envio assistido a partir de uma oportunidade de pós-venda."""

from datetime import datetime, timezone

from sqlalchemy import text
from sqlalchemy.orm import Session

from chatbot.models.conversa import Conversa
from chatbot.models.mensagem import RemetenteMensagem
from chatbot.services import comunicacao_policy_service, mensageria_service, whatsapp_integracao_service, whatsapp_service
from chatbot.tenant import current_user


def _case(db: Session, case_id: int):
    return db.execute(text("""
        SELECT pv.id,pv.status,pv.id_responsavel,coalesce(p.id_paciente,NULL) AS patient_id,
          coalesce(p.nome,pe.nome) AS patient_name,
          coalesce(nullif(concat(p.ddd,p.numero),''),pe.telefone) AS phone
        FROM pos_venda_caso pv
        LEFT JOIN consulta c ON c.id_consulta=pv.id_consulta_origem
        LEFT JOIN paciente p ON p.id_paciente=c.id_paciente
        LEFT JOIN integracao_atendimento_externo ae ON ae.id_atendimento_externo=pv.id_atendimento_externo_origem
        LEFT JOIN integracao_paciente_externo pe ON pe.id_paciente_externo=ae.id_paciente_externo
        WHERE pv.id=:case
    """), {"case": case_id}).mappings().first()


def prepare(db: Session, case_id: int) -> dict | None:
    case = _case(db, case_id)
    if not case:
        return None
    phone = whatsapp_service.normalizar_telefone(case["phone"] or "")
    session_open = bool(phone and db.execute(text("""
        SELECT EXISTS(SELECT 1 FROM mensagem WHERE telefone=:phone AND direcao='entrada'
          AND criado_em>=now()-interval '24 hours')
    """), {"phone": phone}).scalar_one())
    models = db.execute(text("""
        SELECT m.id_modelo AS id,m.nome,m.conteudo,m.idioma,s.provedor_modelo_id AS provider_template
        FROM comunicacao_modelo m JOIN LATERAL(
          SELECT status,provedor_modelo_id FROM comunicacao_modelo_status_evento
          WHERE id_modelo=m.id_modelo ORDER BY id_status DESC LIMIT 1
        ) s ON true
        WHERE m.canal='whatsapp' AND m.finalidade='pos_venda' AND s.status='aprovado'
        ORDER BY m.nome,m.versao DESC
    """)).mappings().all()
    first_name = (case["patient_name"] or "paciente").split()[0]
    suggestion = f"Olá, {first_name}! Sentimos sua falta e queremos ajudar. Deseja remarcar seu atendimento?"
    configuration = whatsapp_integracao_service.get_configuration(db)
    return {
        "caseId": case_id, "patient": case["patient_name"], "phone": phone,
        "sessionOpen": session_open, "suggestedText": suggestion,
        "configured": configuration.get("configured", False) and configuration.get("active", False),
        "models": [dict(row) for row in models],
    }


def send(db: Session, case_id: int, body) -> dict | None:
    case = _case(db, case_id)
    if not case:
        return None
    phone = whatsapp_service.normalizar_telefone(case["phone"] or "")
    if not phone:
        raise ValueError("O caso não possui um telefone válido.")
    model = db.execute(text("""
        SELECT m.id_modelo,m.versao,m.idioma,s.provedor_modelo_id
        FROM comunicacao_modelo m JOIN LATERAL(
          SELECT status,provedor_modelo_id FROM comunicacao_modelo_status_evento
          WHERE id_modelo=m.id_modelo ORDER BY id_status DESC LIMIT 1
        ) s ON true
        WHERE m.id_modelo=:model AND m.canal='whatsapp' AND m.finalidade='pos_venda' AND s.status='aprovado'
    """), {"model": body.modelId}).mappings().first()
    if not model:
        raise ValueError("Selecione um modelo de pós-venda aprovado.")
    decision = comunicacao_policy_service.avaliar_e_registrar(
        db, paciente_id=case["patient_id"], telefone=phone, canal="whatsapp",
        finalidade="pos_venda", essencial=False, modelo_id=body.modelId,
        origem="pos_venda_oportunidade", usuario_id=current_user.get(),
    )
    session_open = db.execute(text("""
        SELECT EXISTS(SELECT 1 FROM mensagem WHERE telefone=:phone AND direcao='entrada'
          AND criado_em>=now()-interval '24 hours')
    """), {"phone": phone}).scalar_one()
    override = None
    if not session_open:
        override = {"template": model["provedor_modelo_id"], "idioma": model["idioma"], "parametros": []}
    conversation = db.query(Conversa).filter(Conversa.telefone == phone).first()
    if conversation is None:
        conversation = Conversa(id_clinica=db.info["tenant_id"], telefone=phone, id_paciente=case["patient_id"])
        db.add(conversation)
        db.flush()
    elif conversation.id_paciente is None and case["patient_id"]:
        conversation.id_paciente = case["patient_id"]
    conversation.atualizado_em = datetime.now(timezone.utc)
    message, created = mensageria_service.enqueue_text(
        db, idempotency_key=body.idempotencyKey, telefone=phone, texto_mensagem=body.text,
        paciente_id=case["patient_id"], remetente=RemetenteMensagem.agente,
        tentativa_id=decision.tentativa_id, consentimento_id=decision.consentimento_id,
        modelo_id=decision.modelo_id, modelo_versao=decision.modelo_versao,
        finalidade="pos_venda", origem="pos_venda_oportunidade",
        id_pos_venda_caso=case_id, payload_override=override,
    )
    return {"messageId": message.id_mensagem, "conversationId": conversation.id_conversa,
            "status": message.status_entrega, "created": created,
            "mode": "session" if session_open else "template"}
