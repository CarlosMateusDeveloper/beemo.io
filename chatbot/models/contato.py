from sqlalchemy import CHAR, Column, Date, ForeignKey, Integer, String

from chatbot.db import Base


class Contato(Base):
    """Mapeia a tabela `paciente`, ja existente no schema do sistema
    (database/schema_clinica.sql). O chatbot chama de "contato" porque,
    do ponto de vista do WhatsApp, a pessoa pode ainda nao ter um
    cadastro completo de paciente na clinica."""

    __tablename__ = "paciente"

    id_paciente = Column(Integer, primary_key=True)
    nome = Column(String(100), nullable=False)
    cpf = Column(CHAR(11), nullable=False, unique=True)
    data_nascimento = Column(Date, nullable=False)
    ddd = Column(CHAR(2), nullable=False)
    numero = Column(String(10), nullable=False)
    id_convenio = Column(Integer, ForeignKey("convenio.id_convenio"), nullable=True)
