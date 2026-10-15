"""Contrato do provedor e adaptador oficial da WhatsApp Cloud API."""

from dataclasses import dataclass
from typing import Protocol

import httpx

from chatbot.config import WHATSAPP_API_URL, WHATSAPP_API_VERSION
from chatbot.services.whatsapp_service import WhatsAppError, normalizar_telefone


@dataclass(frozen=True)
class WhatsAppCredentials:
    access_token: str
    phone_number_id: str
    app_secret: str


class WhatsAppProvider(Protocol):
    def send_text(self, to: str, text: str) -> str: ...
    def send_template(self, to: str, name: str, language: str, parameters: list[str]) -> str: ...
    def number(self) -> str | None: ...


class MetaWhatsAppProvider:
    def __init__(self, credentials: WhatsAppCredentials):
        self.credentials = credentials
        self.base = f"{WHATSAPP_API_URL}/{WHATSAPP_API_VERSION}"

    @property
    def headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self.credentials.access_token}", "Content-Type": "application/json"}

    def send_text(self, to: str, text: str) -> str:
        return self._post({
            "messaging_product": "whatsapp", "recipient_type": "individual",
            "to": normalizar_telefone(to), "type": "text",
            "text": {"preview_url": False, "body": text},
        })

    def send_template(self, to: str, name: str, language: str, parameters: list[str]) -> str:
        template: dict = {"name": name, "language": {"code": language}}
        if parameters:
            template["components"] = [{"type": "body", "parameters": [
                {"type": "text", "text": value} for value in parameters
            ]}]
        return self._post({
            "messaging_product": "whatsapp", "recipient_type": "individual",
            "to": normalizar_telefone(to), "type": "template", "template": template,
        })

    def number(self) -> str | None:
        try:
            with httpx.Client(timeout=10) as client:
                response = client.get(
                    f"{self.base}/{self.credentials.phone_number_id}", headers=self.headers,
                    params={"fields": "display_phone_number"},
                )
            return self._read(response).get("display_phone_number")
        except (httpx.HTTPError, WhatsAppError):
            return None

    def _post(self, payload: dict) -> str:
        try:
            with httpx.Client(timeout=10) as client:
                response = client.post(
                    f"{self.base}/{self.credentials.phone_number_id}/messages",
                    headers=self.headers, json=payload,
                )
        except httpx.HTTPError as error:
            raise WhatsAppError(f"Falha de rede ao chamar a Cloud API: {error}") from error
        messages = self._read(response).get("messages") or []
        if not messages or not messages[0].get("id"):
            raise WhatsAppError("A Cloud API não devolveu o identificador da mensagem.")
        return messages[0]["id"]

    @staticmethod
    def _read(response: httpx.Response) -> dict:
        if response.status_code < 400:
            return response.json()
        try:
            provider_error = response.json().get("error", {})
        except ValueError:
            provider_error = {}
        detail = provider_error.get("message") or response.text[:200]
        raise WhatsAppError(
            f"WhatsApp recusou a chamada: {detail}",
            codigo=provider_error.get("code"), status=response.status_code,
        )
