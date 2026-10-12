from typing import Any, Literal, Optional

from pydantic import BaseModel, Field, field_validator


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
    agenteNome: str = Field(min_length=1, max_length=120)

    @field_validator("agenteNome", mode="before")
    @classmethod
    def limpar_nome(cls, valor):
        return valor.strip() if isinstance(valor, str) else valor


class EnviarMensagemIn(BaseModel):
    texto: str = Field(min_length=1, max_length=4096)
    idempotencyKey: str = Field(min_length=8, max_length=160)

    @field_validator("texto", mode="before")
    @classmethod
    def limpar_texto(cls, valor):
        return valor.strip() if isinstance(valor, str) else valor


class LimiteMensageriaIn(BaseModel):
    escopo: Literal["clinica", "numero", "campanha", "provedor"]
    chave: str = Field(default="*", min_length=1, max_length=160)
    janelaSegundos: int = Field(ge=1, le=86400)
    maxEnvios: int = Field(ge=1, le=100000)
    ativo: bool = True

    @field_validator("chave", mode="before")
    @classmethod
    def limpar_chave(cls, valor):
        return valor.strip() if isinstance(valor, str) else valor


class WhatsAppConfigurationIn(BaseModel):
    phoneNumberId: str = Field(min_length=1, max_length=80)
    businessAccountId: str | None = Field(default=None, max_length=80)
    displayPhoneNumber: str | None = Field(default=None, max_length=30)
    accessToken: str | None = Field(default=None, max_length=4096)
    appSecret: str | None = Field(default=None, max_length=512)
    verifyToken: str | None = Field(default=None, max_length=512)
    active: bool = True


class PosVendaWhatsAppIn(BaseModel):
    text: str = Field(min_length=1, max_length=4096)
    modelId: int = Field(gt=0)
    idempotencyKey: str = Field(min_length=8, max_length=160)

    @field_validator("text", mode="before")
    @classmethod
    def limpar_texto(cls, valor):
        return valor.strip() if isinstance(valor, str) else valor


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
