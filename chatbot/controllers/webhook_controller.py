import hmac
import json
import os
from chatbot.tenant import current_tenant
from chatbot.config import WHATSAPP_PHONE_NUMBER_ID
import logging

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.responses import PlainTextResponse
from pydantic import ValidationError
from sqlalchemy.orm import Session

from chatbot.config import WHATSAPP_VERIFY_TOKEN
from chatbot.db import get_db
from chatbot.services import mensagem_service, whatsapp_event_service, whatsapp_integracao_service, whatsapp_service
from chatbot.views.webhook import WebhookPayload

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/webhook", tags=["webhook"])


@router.get("/{tenant}/{webhook_key}")
def verificar_webhook_tenant(
    tenant: int, webhook_key: str, db: Session = Depends(get_db),
    hub_mode: str | None = Query(default=None, alias="hub.mode"),
    hub_verify_token: str | None = Query(default=None, alias="hub.verify_token"),
    hub_challenge: str | None = Query(default=None, alias="hub.challenge"),
):
    marker = current_tenant.set(tenant)
    db.info["tenant_id"] = tenant
    try:
        credentials = whatsapp_integracao_service.webhook_credentials(db, tenant, webhook_key)
        if hub_mode == "subscribe" and credentials and whatsapp_integracao_service.verify_handshake(credentials, hub_verify_token):
            return PlainTextResponse(hub_challenge or "")
        raise HTTPException(status_code=403, detail="Token de verificação inválido")
    finally:
        current_tenant.reset(marker)


@router.post("/{tenant}/{webhook_key}")
async def receber_webhook_tenant(tenant: int, webhook_key: str, request: Request, db: Session = Depends(get_db)):
    marker = current_tenant.set(tenant)
    db.info["tenant_id"] = tenant
    try:
        credentials = whatsapp_integracao_service.webhook_credentials(db, tenant, webhook_key)
        if not credentials:
            raise HTTPException(status_code=404, detail="Webhook não encontrado")
        body = await request.body()
        if not whatsapp_integracao_service.verify_signature(credentials["app_secret"], request.headers.get("X-Hub-Signature-256"), body):
            raise HTTPException(status_code=403, detail="Assinatura inválida")
        return await _process_payload(db, body, credentials["phone_number_id"])
    finally:
        current_tenant.reset(marker)


@router.get("")
def verificar_webhook(
    hub_mode: str | None = Query(default=None, alias="hub.mode"),
    hub_verify_token: str | None = Query(default=None, alias="hub.verify_token"),
    hub_challenge: str | None = Query(default=None, alias="hub.challenge"),
):
    """Handshake de verificacao da Meta ao cadastrar a URL de callback.

    A Meta chama este GET uma vez com um desafio; so cadastra o webhook se
    a resposta for o desafio em texto puro (nao JSON).
    """
    token_confere = bool(
        WHATSAPP_VERIFY_TOKEN
        and hub_verify_token
        and hmac.compare_digest(hub_verify_token, WHATSAPP_VERIFY_TOKEN)
    )
    if hub_mode == "subscribe" and token_confere:
        return PlainTextResponse(hub_challenge or "")
    logger.warning("Verificacao de webhook recusada (mode=%s).", hub_mode)
    raise HTTPException(status_code=403, detail="Token de verificacao invalido")


@router.post("")
async def receber_webhook(request: Request, db: Session = Depends(get_db)):
    """Recebe eventos da WhatsApp Business Cloud API e delega ao service.

    Sempre responde 200 quando a origem e legitima, mesmo se o payload nao
    interessar ou o processamento falhar: sem 200 a Meta reenvia o evento
    varias vezes e, insistindo, desativa o webhook.
    """
    corpo = await request.body()
    if not whatsapp_service.validar_assinatura(request.headers.get("X-Hub-Signature-256"), corpo):
        logger.warning("Webhook com assinatura invalida descartado.")
        raise HTTPException(status_code=403, detail="Assinatura invalida")

    if not WHATSAPP_PHONE_NUMBER_ID:
        raise HTTPException(status_code=503, detail="Número receptor não configurado")
    tenant = int(os.getenv('ID_CLINICA', '1'))
    marker = current_tenant.set(tenant)
    db.info['tenant_id'] = tenant
    try:
        return await _process_payload(db, corpo, WHATSAPP_PHONE_NUMBER_ID)
    finally:
        current_tenant.reset(marker)


async def _process_payload(db: Session, body: bytes, expected_phone_id: str):
    try:
        payload = WebhookPayload.model_validate_json(body)
        envelope = json.loads(body)
    except (ValidationError, ValueError):
        logger.warning("Payload de webhook em formato inesperado, ignorado.", exc_info=True)
        return {"status": "ignorado"}
    values = [change.get("value", {}) for entry in envelope.get("entry", []) for change in entry.get("changes", [])]
    if any(value.get("metadata", {}).get("phone_number_id") != expected_phone_id for value in values):
        raise HTTPException(status_code=403, detail="Número receptor não autorizado")
    await mensagem_service.processar_mensagens_recebidas(db, payload.extrair_mensagens())
    whatsapp_event_service.process_statuses(db, payload.extrair_status())
    return {"status": "recebido"}
