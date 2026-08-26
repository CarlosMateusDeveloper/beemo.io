from sqlalchemy import Boolean, Column, DateTime, ForeignKey, Integer, String
from sqlalchemy.sql import func

from chatbot.db import Base


class CapacidadeBotConfig(Base):
    """Mapeia `capacidade_bot_config` — liga/desliga por clinica. Nome e
    texto de impacto de cada capacidade ficam no catalogo em
    chatbot/services/whatsapp_painel_service.py — sao copy de produto, nao
    dado de linha."""

    __tablename__ = "capacidade_bot_config"

    id_clinica = Column(Integer, ForeignKey("clinica.id_clinica"), primary_key=True)
    capacidade_id = Column(String(40), primary_key=True)
    ativo = Column(Boolean, nullable=False, server_default="true")
    atualizado_em = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
