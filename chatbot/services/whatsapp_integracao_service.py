"""Credenciais por clínica, validação do webhook e criação do provedor."""

import base64
import hashlib
import hmac
import json
import os
import secrets

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from sqlalchemy import text
from sqlalchemy.orm import Session

from chatbot.config import WHATSAPP_APP_SECRET, WHATSAPP_API_TOKEN, WHATSAPP_PHONE_NUMBER_ID
from chatbot.services.whatsapp_provider import MetaWhatsAppProvider, WhatsAppCredentials
from chatbot.services.whatsapp_service import WhatsAppNaoConfiguradoError
from chatbot.tenant import tenant_id


def _cipher() -> AESGCM:
    encoded = os.getenv("WHATSAPP_CREDENTIAL_ENCRYPTION_KEY", "")
    try:
        key = base64.urlsafe_b64decode(encoded.encode())
    except Exception as error:
        raise ValueError("WHATSAPP_CREDENTIAL_ENCRYPTION_KEY inválida.") from error
    if len(key) != 32:
        raise ValueError("WHATSAPP_CREDENTIAL_ENCRYPTION_KEY deve ser uma chave base64 de 32 bytes.")
    return AESGCM(key)


def encrypt(value: str) -> str:
    nonce = os.urandom(12)
    ciphertext = _cipher().encrypt(nonce, value.encode(), b"clinisis-whatsapp-v1")
    return base64.urlsafe_b64encode(nonce + ciphertext).decode()


def decrypt(value: str) -> str:
    try:
        raw = base64.urlsafe_b64decode(value.encode())
        return _cipher().decrypt(raw[:12], raw[12:], b"clinisis-whatsapp-v1").decode()
    except Exception as error:
        raise ValueError("Não foi possível abrir as credenciais do WhatsApp com a chave configurada.") from error


def save_configuration(db: Session, data: dict, user_id: int | None) -> dict:
    current = db.execute(text("SELECT * FROM whatsapp_integracao WHERE id_clinica=:tenant"), {"tenant": tenant_id()}).mappings().first()
    access_token = (data.get("accessToken") or "").strip()
    app_secret = (data.get("appSecret") or "").strip()
    verify_token = (data.get("verifyToken") or "").strip()
    phone_number_id = (data.get("phoneNumberId") or "").strip()
    if not phone_number_id:
        raise ValueError("Informe o ID do número do WhatsApp.")
    if not current and not all((access_token, app_secret, verify_token)):
        raise ValueError("Informe token de acesso, App Secret e token de verificação na primeira configuração.")
    token_encrypted = encrypt(access_token) if access_token else current["access_token_criptografado"]
    secret_encrypted = encrypt(app_secret) if app_secret else current["app_secret_criptografado"]
    verify_hash = hashlib.sha256(verify_token.encode()).hexdigest() if verify_token else current["verify_token_hash"]
    webhook_key = current["webhook_key"] if current else secrets.token_urlsafe(32)
    db.execute(text("""
        INSERT INTO whatsapp_integracao(
          id_clinica,webhook_key,phone_number_id,business_account_id,numero_exibicao,
          access_token_criptografado,app_secret_criptografado,verify_token_hash,ativo,atualizado_por
        ) VALUES (:tenant,:key,:phone,:business,:number,:token,:secret,:verify,:active,:user)
        ON CONFLICT(id_clinica) DO UPDATE SET phone_number_id=excluded.phone_number_id,
          business_account_id=excluded.business_account_id,numero_exibicao=excluded.numero_exibicao,
          access_token_criptografado=excluded.access_token_criptografado,
          app_secret_criptografado=excluded.app_secret_criptografado,
          verify_token_hash=excluded.verify_token_hash,ativo=excluded.ativo,
          atualizado_por=excluded.atualizado_por,atualizado_em=now()
    """), {
        "tenant": tenant_id(), "key": webhook_key, "phone": phone_number_id,
        "business": (data.get("businessAccountId") or "").strip() or None,
        "number": (data.get("displayPhoneNumber") or "").strip() or None,
        "token": token_encrypted, "secret": secret_encrypted, "verify": verify_hash,
        "active": bool(data.get("active", True)), "user": user_id,
    })
    db.commit()
    return get_configuration(db)


def get_configuration(db: Session) -> dict:
    row = db.execute(text("""
        SELECT phone_number_id,business_account_id,numero_exibicao,webhook_key,ativo,
          access_token_criptografado IS NOT NULL AS token_configurado,
          app_secret_criptografado IS NOT NULL AS segredo_configurado
        FROM whatsapp_integracao WHERE id_clinica=:tenant
    """), {"tenant": tenant_id()}).mappings().first()
    if not row:
        return {"configured": False, "active": False}
    return {
        "configured": True, "active": row["ativo"], "phoneNumberId": row["phone_number_id"],
        "businessAccountId": row["business_account_id"], "displayPhoneNumber": row["numero_exibicao"],
        "accessTokenConfigured": row["token_configurado"], "appSecretConfigured": row["segredo_configurado"],
        "webhookPath": f"/webhook/{tenant_id()}/{row['webhook_key']}",
    }


def _row(db: Session, *, tenant: int | None = None, webhook_key: str | None = None):
    filters = ["id_clinica=:tenant", "ativo"]
    params = {"tenant": tenant or tenant_id()}
    if webhook_key is not None:
        filters.append("webhook_key=:key")
        params["key"] = webhook_key
    return db.execute(text(f"SELECT * FROM whatsapp_integracao WHERE {' AND '.join(filters)}"), params).mappings().first()


def provider(db: Session) -> MetaWhatsAppProvider:
    row = _row(db)
    if row:
        credentials = WhatsAppCredentials(
            decrypt(row["access_token_criptografado"]), row["phone_number_id"],
            decrypt(row["app_secret_criptografado"]),
        )
        return MetaWhatsAppProvider(credentials)
    if WHATSAPP_API_TOKEN and WHATSAPP_PHONE_NUMBER_ID and WHATSAPP_APP_SECRET:
        return MetaWhatsAppProvider(WhatsAppCredentials(WHATSAPP_API_TOKEN, WHATSAPP_PHONE_NUMBER_ID, WHATSAPP_APP_SECRET))
    raise WhatsAppNaoConfiguradoError("WhatsApp não configurado para esta clínica.")


def webhook_credentials(db: Session, tenant: int, webhook_key: str):
    row = _row(db, tenant=tenant, webhook_key=webhook_key)
    if not row:
        return None
    return {
        "phone_number_id": row["phone_number_id"], "app_secret": decrypt(row["app_secret_criptografado"]),
        "verify_token_hash": row["verify_token_hash"],
    }


def verify_handshake(credentials: dict, supplied_token: str | None) -> bool:
    if not supplied_token:
        return False
    return hmac.compare_digest(hashlib.sha256(supplied_token.encode()).hexdigest(), credentials["verify_token_hash"])


def verify_signature(app_secret: str, signature: str | None, body: bytes) -> bool:
    if not signature or not signature.startswith("sha256="):
        return False
    expected = hmac.new(app_secret.encode(), body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature.removeprefix("sha256="))


def send_outbox(db: Session, row) -> str:
    adapter = provider(db)
    payload = row["payload"] if isinstance(row["payload"], dict) else json.loads(row["payload"])
    if row["tipo"] == "template":
        return adapter.send_template(
            row["destino"], payload["template"], payload.get("idioma", "pt_BR"), payload.get("parametros", []),
        )
    return adapter.send_text(row["destino"], payload["texto"])
