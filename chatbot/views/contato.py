from pydantic import BaseModel, ConfigDict


class ContatoOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id_paciente: int
    nome: str
    ddd: str
    numero: str
