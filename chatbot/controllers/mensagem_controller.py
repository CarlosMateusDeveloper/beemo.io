from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from chatbot.db import get_db
from chatbot.services import mensagem_service
from chatbot.views.mensagem import MensagemOut

router = APIRouter(prefix="/mensagens", tags=["mensagens"])


@router.get("/{telefone}", response_model=list[MensagemOut])
def listar_mensagens(telefone: str, db: Session = Depends(get_db)):
    """Historico de mensagens de um telefone, da mais antiga para a mais recente."""
    return mensagem_service.listar_por_telefone(db, telefone)
