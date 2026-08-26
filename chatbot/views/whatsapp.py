from typing import Any, Optional

from pydantic import BaseModel


class StatusOut(BaseModel):
    conectado: bool
    numero: Optional[str] = None


class ConversaOut(BaseModel):
    id: int
    paciente: Optional[str] = None
    telefone: str
    estado: str
    agente: Optional[str] = None
    esperaMin: Optional[int] = None
    horario: Optional[str] = None
    ultimaMensagem: Optional[str] = None
    nota: Optional[str] = None


class MensagemBolhaOut(BaseModel):
    id: int
    tipo: str = "mensagem"
    remetente: str
    agente: Optional[str] = None
    texto: str
    horario: str


class AssumirIn(BaseModel):
    agenteNome: str


class EnviarMensagemIn(BaseModel):
    texto: str


class CapacidadeOut(BaseModel):
    id: str
    nome: str
    ativo: bool
    impactoDesligada: str


class AssistenteOut(BaseModel):
    capacidades: list[CapacidadeOut]
    mensagens: dict[str, dict[str, str]]
    regras: dict[str, Any]


class TogglecapacidadeIn(BaseModel):
    ativo: bool


class AtualizarMensagensIn(BaseModel):
    # parcial: so os campos que mudaram (saudacao/opcoes/confirmacao/naoEntendi)
    campos: dict[str, str]
