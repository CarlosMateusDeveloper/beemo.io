import enum

from sqlalchemy import BigInteger, Column, DateTime, ForeignKey, Integer, String
from sqlalchemy.dialects.postgresql import ENUM
from sqlalchemy.sql import func

from chatbot.db import Base


class DirecaoMensagem(str, enum.Enum):
    entrada = "entrada"
    saida = "saida"


class TipoMensagem(str, enum.Enum):
    texto = "texto"
    audio = "audio"
    imagem = "imagem"
    documento = "documento"
    botao = "botao"
    localizacao = "localizacao"


class Mensagem(Base):
    """Mapeia a tabela `mensagem`, ja existente no schema do sistema
    (database/schema_clinica.sql). create_type=False porque os tipos ENUM
    ja foram criados pelo schema — o SQLAlchemy so precisa referencia-los."""

    __tablename__ = "mensagem"

    id_mensagem = Column(BigInteger, primary_key=True)
    id_paciente = Column(Integer, ForeignKey("paciente.id_paciente"), nullable=True)
    telefone = Column(String(20), nullable=False)
    direcao = Column(ENUM(DirecaoMensagem, name="direcao_mensagem", create_type=False), nullable=False)
    tipo = Column(ENUM(TipoMensagem, name="tipo_mensagem", create_type=False), nullable=False, server_default="texto")
    conteudo = Column(String, nullable=False)
    criado_em = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
