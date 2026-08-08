from datetime import datetime

from pydantic import BaseModel, ConfigDict


class MensagemOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id_mensagem: int
    telefone: str
    direcao: str
    tipo: str
    conteudo: str
    criado_em: datetime
