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
from chatbot.services import mensagem_service, whatsapp_service
from chatbot.views.webhook import WebhookPayload

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/webhook", tags=["webhook"])


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

    try:
        payload = WebhookPayload.model_validate_json(corpo)
    except ValidationError:
        logger.warning("Payload de webhook em formato inesperado, ignorado.", exc_info=True)
        return {"status": "ignorado"}

    envelope = json.loads(corpo)
    values = [change.get('value', {}) for entry in envelope.get('entry', []) for change in entry.get('changes', [])]
    if not WHATSAPP_PHONE_NUMBER_ID or any(value.get('metadata', {}).get('phone_number_id') != WHATSAPP_PHONE_NUMBER_ID for value in values):
        raise HTTPException(status_code=403, detail='Numero receptor nao autorizado')
    tenant = int(os.getenv('ID_CLINICA', '1'))
    marker = current_tenant.set(tenant)
    db.info['tenant_id'] = tenant
    try:
        await mensagem_service.processar_mensagens_recebidas(db, payload.extrair_mensagens())
    finally:
        current_tenant.reset(marker)
    return {"status": "recebido"}
