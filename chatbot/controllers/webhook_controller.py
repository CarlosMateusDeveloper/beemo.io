from fastapi import APIRouter

from chatbot.services import mensagem_service
from chatbot.views.webhook import WebhookPayload

router = APIRouter(prefix="/webhook", tags=["webhook"])


@router.post("")
async def receber_webhook(payload: WebhookPayload):
    """Recebe eventos do provedor de WhatsApp e delega o processamento ao service."""
    await mensagem_service.process_incoming_message(payload.telefone, payload.conteudo)
    return {"status": "recebido"}
