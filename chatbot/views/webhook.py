from pydantic import BaseModel


class WebhookPayload(BaseModel):
    """Estrutura inicial do payload recebido do provedor de WhatsApp.
    Formato real ainda depende de qual provedor for integrado (ex: WhatsApp
    Business Cloud API) — este e um schema minimo de partida."""

    telefone: str
    conteudo: str
