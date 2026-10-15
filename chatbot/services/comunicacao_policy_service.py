"""Política única de elegibilidade para qualquer mensagem enviada ao paciente."""

from dataclasses import dataclass
from datetime import datetime, timezone
import re
import unicodedata

from sqlalchemy import text
from sqlalchemy.orm import Session


PALAVRAS_DESCADASTRO = {
    "sair", "pare", "parar", "cancelar", "descadastrar", "descadastro",
    "remover", "nao quero receber", "nao me envie", "nao enviar mais",
}


class ComunicacaoBloqueadaError(ValueError):
    def __init__(self, motivo: str):
        super().__init__(f"Envio bloqueado pela política de comunicação: {motivo}.")
        self.motivo = motivo


@dataclass(frozen=True)
class Decisao:
    tentativa_id: int
    permitido: bool
    motivo: str
    consentimento_id: int | None
    modelo_id: int | None
    modelo_versao: int | None


def avaliar_e_registrar(
    db: Session, *, paciente_id: int | None, telefone: str, canal: str,
    finalidade: str, essencial: bool, modelo_id: int | None,
    origem: str, usuario_id: int | None = None,
) -> Decisao:
    row = db.execute(text("""
        SELECT * FROM comunicacao_registrar_tentativa(
            :paciente,:telefone,:canal,:finalidade,:essencial,:modelo,:origem,:usuario
        )
    """), {
        "paciente": paciente_id, "telefone": _digitos(telefone), "canal": canal,
        "finalidade": finalidade, "essencial": essencial, "modelo": modelo_id,
        "origem": origem, "usuario": usuario_id,
    }).mappings().one()
    decisao = Decisao(
        tentativa_id=row["id_tentativa"], permitido=row["permitido"], motivo=row["motivo"],
        consentimento_id=row["id_consentimento"], modelo_id=row["id_modelo"],
        modelo_versao=row["modelo_versao"],
    )
    db.commit()  # registra inclusive tentativas que falharem depois no provedor
    if not decisao.permitido:
        raise ComunicacaoBloqueadaError(decisao.motivo)
    return decisao


def texto_solicita_descadastro(conteudo: str) -> bool:
    normalizado = unicodedata.normalize("NFD", conteudo or "")
    normalizado = "".join(ch for ch in normalizado if unicodedata.category(ch) != "Mn")
    normalizado = re.sub(r"[^a-z0-9 ]+", " ", normalizado.lower())
    normalizado = re.sub(r"\s+", " ", normalizado).strip()
    return normalizado in PALAVRAS_DESCADASTRO


def registrar_descadastro(
    db: Session, *, paciente_id: int | None, telefone: str, evidencia: str,
) -> int:
    telefone_normalizado = _digitos(telefone)
    if paciente_id is None and not telefone_normalizado:
        raise ValueError("O descadastro precisa identificar o paciente ou o telefone.")
    row = db.execute(text("""
        INSERT INTO comunicacao_consentimento_evento(
            id_paciente,telefone,canal,finalidade,decisao,origem,
            evidencia_referencia,ocorrido_em
        ) VALUES (
            :paciente,CASE WHEN :paciente IS NULL THEN :telefone ELSE NULL END,
            'whatsapp','todos','revogado','canal',:evidencia,:ocorrido
        ) RETURNING id_consentimento
    """), {
        "paciente": paciente_id, "telefone": telefone_normalizado,
        "evidencia": evidencia[:300], "ocorrido": datetime.now(timezone.utc),
    }).scalar_one()
    db.commit()
    return row


def _digitos(value: str) -> str:
    return re.sub(r"\D", "", value or "")
