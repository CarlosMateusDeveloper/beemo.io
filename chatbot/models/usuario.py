from sqlalchemy import Column, Integer, String

from chatbot.db import Base


class Usuario(Base):
    """Mapeia `usuario`, ja existente no schema do sistema — dono e
    medico/administrador que acessa um painel (nao o paciente). Mapeado
    aqui so pra resolver a FK de mensagem.id_usuario_remetente; o chatbot
    nao gerencia usuario."""

    __tablename__ = "usuario"

    id = Column(Integer, primary_key=True)
    nome = Column(String(100), nullable=False)
    email = Column(String(100), nullable=False)
