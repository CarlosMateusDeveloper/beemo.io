import os

from dotenv import load_dotenv

load_dotenv()

DATABASE_URL = os.getenv("DATABASE_URL")
SISTEMA_API_URL = os.getenv("SISTEMA_API_URL")

# Placeholder de multiempresa: ainda nao existe sessao/login real (Fase 0 do
# roadmap), entao o chatbot opera sobre uma clinica so. Fica em variavel de
# ambiente para pelo menos nao ficar hardcoded no meio da regra de negocio.
ID_CLINICA_ATUAL = int(os.getenv("ID_CLINICA", "1"))

# WhatsApp Business Cloud API (Meta) — a API oficial.
# https://developers.facebook.com/docs/whatsapp/cloud-api
#
# A versao do Graph API fica em variavel de ambiente porque a Meta
# descontinua versoes antigas a cada ~2 anos: subir de versao deve ser
# trocar o .env, nao mexer no codigo.
WHATSAPP_API_URL = os.getenv("WHATSAPP_API_URL", "https://graph.facebook.com")
WHATSAPP_API_VERSION = os.getenv("WHATSAPP_API_VERSION", "v23.0")
WHATSAPP_API_TOKEN = os.getenv("WHATSAPP_API_TOKEN")
WHATSAPP_PHONE_NUMBER_ID = os.getenv("WHATSAPP_PHONE_NUMBER_ID")
# Token inventado por nos e repetido no painel da Meta ao cadastrar o
# webhook — a Meta devolve ele no handshake de verificacao (GET /webhook).
WHATSAPP_VERIFY_TOKEN = os.getenv("WHATSAPP_VERIFY_TOKEN")
# App Secret do app da Meta: valida a assinatura X-Hub-Signature-256 dos
# webhooks, garantindo que o POST veio mesmo da Meta.
WHATSAPP_APP_SECRET = os.getenv("WHATSAPP_APP_SECRET")
