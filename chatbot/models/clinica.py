from sqlalchemy import Column, DateTime, Integer, String
from sqlalchemy.sql import func

from chatbot.db import Base


class Clinica(Base):
    """Mapeia `clinica` (database/schema_clinica.sql, Fase 10) — tabela de
    tenant minima, referenciada pelas FKs das tabelas novas do painel
    WhatsApp. Precisa existir como model mesmo sem query direta: sem ela o
    SQLAlchemy não resolve as ForeignKey("clinica.id_clinica") na hora do
    flush/commit."""

    __tablename__ = "clinica"

    id_clinica = Column(Integer, primary_key=True)
    nome = Column(String(150), nullable=False)
    criado_em = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
