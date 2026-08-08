import httpx


async def send_message(to: str, message: str, client: httpx.AsyncClient | None = None) -> None:
    """Envia uma mensagem via API do provedor de WhatsApp.

    Estrutura inicial apenas - integracao real ainda nao implementada.
    Recebe um httpx.AsyncClient opcional (injetavel) para quando a
    chamada HTTP de fato for implementada. Usara WHATSAPP_API_URL e
    WHATSAPP_API_TOKEN de chatbot.config.
    """
    raise NotImplementedError("Integracao com a API do WhatsApp ainda nao implementada.")
