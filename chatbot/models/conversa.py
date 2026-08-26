import enum

from sqlalchemy import Column, DateTime, ForeignKey, Integer, String
from sqlalchemy.dialects.postgresql import ENUM
from sqlalchemy.sql import func

from chatbot.db import Base


class EstadoConversaBot(str, enum.Enum):
    bot = "bot"
    aguardando = "aguardando"
    com_agente = "com_agente"


class Conversa(Base):
    """Mapeia `conversa` (database/schema_clinica.sql, Fase 10) — o estado
    da caixa de entrada do WhatsApp por (clinica, telefone). O historico de
    mensagens continua em `mensagem`, correlacionado por telefone."""

    __tablename__ = "conversa"

    id_conversa = Column(Integer, primary_key=True)
    id_clinica = Column(Integer, ForeignKey("clinica.id_clinica"), nullable=False)
    id_paciente = Column(Integer, ForeignKey("paciente.id_paciente"), nullable=True)
    telefone = Column(String(20), nullable=False)
    estado = Column(ENUM(EstadoConversaBot, name="estado_conversa_bot", create_type=False), nullable=False, server_default="bot")
    # Sem FK pra usuario: ainda nao existe sessao/login real (Fase 0 do roadmap).
    agente_nome = Column(String(100), nullable=True)
    atualizado_em = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
    criado_em = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
