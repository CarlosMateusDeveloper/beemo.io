"""Integracao com a WhatsApp Business Cloud API (Meta) — a API oficial.

Referencia: https://developers.facebook.com/docs/whatsapp/cloud-api

Ha duas variantes de envio porque este modulo e consumido de dois lugares
com estilos diferentes: o webhook (assincrono, dentro do event loop do
FastAPI) e o painel do atendente (endpoints sincronos). A montagem do
request e a leitura da resposta sao compartilhadas entre as duas.
"""

import hashlib
import hmac
import logging
import re
import time

import httpx

from chatbot.config import (
    WHATSAPP_API_TOKEN,
    WHATSAPP_API_URL,
    WHATSAPP_API_VERSION,
    WHATSAPP_APP_SECRET,
    WHATSAPP_PHONE_NUMBER_ID,
)

logger = logging.getLogger(__name__)

TIMEOUT = httpx.Timeout(10.0)

# O numero de exibicao muda muito raramente; consultar o Graph a cada carga
# do painel seria desperdicio.
_TTL_CACHE_NUMERO_SEG = 300
_cache_numero: tuple[float, str] | None = None


class WhatsAppError(RuntimeError):
    """Falha ao falar com a Cloud API (rede, credencial ou recusa da Meta)."""

    def __init__(self, mensagem: str, codigo: int | None = None, status: int | None = None):
        super().__init__(mensagem)
        self.codigo = codigo
        self.status = status


class WhatsAppNaoConfiguradoError(WhatsAppError):
    """Credenciais ausentes: nada foi enviado, e nao adianta tentar de novo."""


def esta_configurado() -> bool:
    return bool(WHATSAPP_API_TOKEN and WHATSAPP_PHONE_NUMBER_ID)


def normalizar_telefone(telefone: str) -> str:
    """Formato aceito pela Cloud API: so digitos, com codigo do pais (E.164 sem '+').

    Numeros brasileiros digitados sem o 55 (10 ou 11 digitos) recebem o
    codigo do pais; qualquer outro comprimento passa como veio, porque ja
    inclui codigo de pais ou nao e um numero valido — e nesse caso quem
    recusa e a Meta, com mensagem de erro melhor que a nossa.
    """
    digitos = re.sub(r"\D", "", telefone or "")
    if len(digitos) in (10, 11):
        digitos = f"55{digitos}"
    return digitos


def validar_assinatura(assinatura: str | None, corpo: bytes) -> bool:
    """Confere o header X-Hub-Signature-256 do webhook contra o App Secret.

    Sem App Secret configurado a validacao e pulada (retorna True) — util
    em desenvolvimento com ngrok, mas em producao o App Secret deve estar
    sempre setado, senao qualquer um consegue injetar mensagens falsas.
    """
    if not WHATSAPP_APP_SECRET:
        logger.warning("WHATSAPP_APP_SECRET nao configurado: assinatura do webhook nao verificada.")
        return True
    if not assinatura or not assinatura.startswith("sha256="):
        return False
    esperado = hmac.new(WHATSAPP_APP_SECRET.encode(), corpo, hashlib.sha256).hexdigest()
    return hmac.compare_digest(esperado, assinatura.removeprefix("sha256="))


async def send_message(to: str, message: str, client: httpx.AsyncClient | None = None) -> str:
    """Envia uma mensagem de texto livre e devolve o id (wamid) gerado pela Meta.

    Texto livre so e aceito dentro da janela de 24h desde a ultima mensagem
    do paciente; fora dela a Meta recusa e o envio precisa ser por template
    (ver send_template).
    """
    return await _post(_payload_texto(to, message), client)


def send_message_sync(to: str, message: str) -> str:
    """Mesma coisa que send_message, para quem chama de codigo sincrono."""
    return _post_sync(_payload_texto(to, message))


async def send_template(
    to: str,
    nome_template: str,
    idioma: str = "pt_BR",
    parametros: list[str] | None = None,
    client: httpx.AsyncClient | None = None,
) -> str:
    """Envia um template aprovado pela Meta (unico envio valido fora da janela de 24h).

    `parametros` preenche as variaveis {{1}}, {{2}}... do corpo do template,
    na ordem. O template precisa estar aprovado no Gerenciador do WhatsApp
    antes — nao da pra criar um na hora do envio.
    """
    return await _post(_payload_template(to, nome_template, idioma, parametros), client)


def consultar_numero() -> str | None:
    """Numero de exibicao do remetente, ou None se a Meta nao responder.

    Serve tambem como teste real de credencial: se o token estiver errado
    ou vencido, isso falha — e o painel mostra "desconectado" em vez de
    afirmar uma conexao que nao existe.
    """
    global _cache_numero
    if not esta_configurado():
        return None
    if _cache_numero and (time.monotonic() - _cache_numero[0]) < _TTL_CACHE_NUMERO_SEG:
        return _cache_numero[1]
    try:
        with httpx.Client(timeout=TIMEOUT) as client:
            resposta = client.get(
                f"{WHATSAPP_API_URL}/{WHATSAPP_API_VERSION}/{WHATSAPP_PHONE_NUMBER_ID}",
                headers=_headers(),
                params={"fields": "display_phone_number,verified_name"},
            )
        numero = _ler_resposta(resposta).get("display_phone_number")
    except (httpx.HTTPError, WhatsAppError) as erro:
        logger.warning("Nao foi possivel consultar o numero na Cloud API: %s", erro)
        return None
    if numero:
        _cache_numero = (time.monotonic(), numero)
    return numero


def _headers() -> dict[str, str]:
    return {
        "Authorization": f"Bearer {WHATSAPP_API_TOKEN}",
        "Content-Type": "application/json",
    }


def _url_mensagens() -> str:
    return f"{WHATSAPP_API_URL}/{WHATSAPP_API_VERSION}/{WHATSAPP_PHONE_NUMBER_ID}/messages"


def _payload_texto(to: str, message: str) -> dict:
    return {
        "messaging_product": "whatsapp",
        "recipient_type": "individual",
        "to": normalizar_telefone(to),
        "type": "text",
        # preview_url=False: sem card de preview de link nas respostas do bot.
        "text": {"preview_url": False, "body": message},
    }


def _payload_template(to: str, nome: str, idioma: str, parametros: list[str] | None) -> dict:
    template: dict = {"name": nome, "language": {"code": idioma}}
    if parametros:
        template["components"] = [{
            "type": "body",
            "parameters": [{"type": "text", "text": p} for p in parametros],
        }]
    return {
        "messaging_product": "whatsapp",
        "recipient_type": "individual",
        "to": normalizar_telefone(to),
        "type": "template",
        "template": template,
    }


def _exigir_configuracao() -> None:
    if not esta_configurado():
        raise WhatsAppNaoConfiguradoError(
            "WhatsApp nao configurado: defina WHATSAPP_API_TOKEN e WHATSAPP_PHONE_NUMBER_ID."
        )


async def _post(payload: dict, client: httpx.AsyncClient | None) -> str:
    _exigir_configuracao()
    try:
        if client is None:
            async with httpx.AsyncClient(timeout=TIMEOUT) as proprio:
                resposta = await proprio.post(_url_mensagens(), headers=_headers(), json=payload)
        else:
            resposta = await client.post(_url_mensagens(), headers=_headers(), json=payload, timeout=TIMEOUT)
    except httpx.HTTPError as erro:
        raise WhatsAppError(f"Falha de rede ao chamar a Cloud API: {erro}") from erro
    return _id_da_mensagem(_ler_resposta(resposta))


def _post_sync(payload: dict) -> str:
    _exigir_configuracao()
    try:
        with httpx.Client(timeout=TIMEOUT) as client:
            resposta = client.post(_url_mensagens(), headers=_headers(), json=payload)
    except httpx.HTTPError as erro:
        raise WhatsAppError(f"Falha de rede ao chamar a Cloud API: {erro}") from erro
    return _id_da_mensagem(_ler_resposta(resposta))


def _ler_resposta(resposta: httpx.Response) -> dict:
    if resposta.status_code >= 400:
        erro = {}
        try:
            erro = resposta.json().get("error", {}) or {}
        except ValueError:
            pass
        detalhe = erro.get("message") or resposta.text[:200]
        codigo = erro.get("code")
        logger.error(
            "Cloud API recusou a chamada (HTTP %s, code %s): %s",
            resposta.status_code, codigo, detalhe,
        )
        raise WhatsAppError(f"WhatsApp recusou a chamada: {detalhe}", codigo=codigo, status=resposta.status_code)
    return resposta.json()


def _id_da_mensagem(corpo: dict) -> str:
    mensagens = corpo.get("messages") or []
    return mensagens[0].get("id", "") if mensagens else ""
