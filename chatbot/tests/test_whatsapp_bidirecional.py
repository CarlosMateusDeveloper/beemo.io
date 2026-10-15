import base64
import hashlib
import hmac
import os
import unittest
from unittest.mock import patch

import httpx

from chatbot.services.whatsapp_event_service import _can_advance
from chatbot.services.whatsapp_integracao_service import decrypt, encrypt, verify_handshake, verify_signature
from chatbot.services.whatsapp_provider import MetaWhatsAppProvider, WhatsAppCredentials
from chatbot.services.whatsapp_service import WhatsAppError
from chatbot.views.webhook import WebhookPayload


class WhatsAppBidirecionalTest(unittest.TestCase):
    def test_secrets_are_encrypted_with_random_nonce(self):
        key = base64.urlsafe_b64encode(bytes(range(32))).decode()
        with patch.dict(os.environ, {"WHATSAPP_CREDENTIAL_ENCRYPTION_KEY": key}):
            first = encrypt("token-ultrassecreto")
            second = encrypt("token-ultrassecreto")
            self.assertNotEqual(first, second)
            self.assertNotIn("token-ultrassecreto", first)
            self.assertEqual("token-ultrassecreto", decrypt(first))

    def test_webhook_tokens_and_signature_are_constant_time_validated(self):
        token = "verify-value"
        credentials = {"verify_token_hash": hashlib.sha256(token.encode()).hexdigest()}
        self.assertTrue(verify_handshake(credentials, token))
        self.assertFalse(verify_handshake(credentials, "wrong"))
        body = b'{"object":"whatsapp_business_account"}'
        signature = "sha256=" + hmac.new(b"app-secret", body, hashlib.sha256).hexdigest()
        self.assertTrue(verify_signature("app-secret", signature, body))
        self.assertFalse(verify_signature("app-secret", "sha256=invalid", body))

    def test_payload_extracts_messages_and_delivery_statuses(self):
        payload = WebhookPayload.model_validate({"entry": [{"changes": [{"value": {
            "contacts": [{"wa_id": "5585999999999", "profile": {"name": "Ana"}}],
            "messages": [{"id": "wamid.in", "from": "5585999999999", "type": "text", "text": {"body": "Quero remarcar"}}],
            "statuses": [{"id": "wamid.out", "status": "delivered", "timestamp": "1791800000", "recipient_id": "5585999999999"}],
        }}]}]})
        self.assertEqual("Quero remarcar", payload.extrair_mensagens()[0].conteudo)
        self.assertEqual("delivered", payload.extrair_status()[0].status)

    def test_delivery_status_never_regresses(self):
        self.assertTrue(_can_advance("enviada", "entregue"))
        self.assertTrue(_can_advance("entregue", "lida"))
        self.assertFalse(_can_advance("lida", "entregue"))

    def test_provider_returns_meta_message_id_and_surfaces_failures(self):
        adapter = MetaWhatsAppProvider(WhatsAppCredentials("token", "phone-id", "secret"))
        ok = httpx.Response(200, json={"messages": [{"id": "wamid.sent"}]})
        with patch("httpx.Client.post", return_value=ok):
            self.assertEqual("wamid.sent", adapter.send_text("85999999999", "Olá"))
        rejected = httpx.Response(400, json={"error": {"code": 131047, "message": "Re-engagement message"}})
        with patch("httpx.Client.post", return_value=rejected):
            with self.assertRaises(WhatsAppError) as failure:
                adapter.send_text("85999999999", "Olá")
        self.assertEqual(131047, failure.exception.codigo)


if __name__ == "__main__":
    unittest.main()
