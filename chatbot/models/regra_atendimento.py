from sqlalchemy import Column, DateTime, ForeignKey, Integer
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.sql import func

from chatbot.db import Base


class RegraAtendimentoBot(Base):
    """Mapeia `regra_atendimento_bot` — regras de escalonamento/horario/
    disparo, uma linha por clinica. JSONB porque o formato ja e aninhado
    no frontend e nao ha necessidade de consultar campo a campo via SQL."""

    __tablename__ = "regra_atendimento_bot"

    id_clinica = Column(Integer, ForeignKey("clinica.id_clinica"), primary_key=True)
    regras = Column(JSONB, nullable=False)
    atualizado_em = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
