from sqlalchemy import Column, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.sql import func

from chatbot.db import Base


class MensagemTemplateBot(Base):
    """Mapeia `mensagem_template_bot` — texto de cada campo (saudacao,
    opcoes, confirmacao, naoEntendi) de cada capacidade do assistente,
    por clinica."""

    __tablename__ = "mensagem_template_bot"

    id_clinica = Column(Integer, ForeignKey("clinica.id_clinica"), primary_key=True)
    capacidade_id = Column(String(40), primary_key=True)
    campo = Column(String(20), primary_key=True)
    texto = Column(Text, nullable=False)
    atualizado_em = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
