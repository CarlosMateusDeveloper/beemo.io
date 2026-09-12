from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from chatbot.db import get_db
from chatbot.services import contato_service
from chatbot.views.contato import ContatoOut

router = APIRouter(prefix="/contatos", tags=["contatos"])


@router.get("/{telefone}", response_model=ContatoOut)
def buscar_contato(telefone: str, db: Session = Depends(get_db)):
    """Busca um contato pelo telefone (com ou sem codigo do pais)."""
    contato = contato_service.buscar_por_telefone(db, telefone)
    if contato is None:
        raise HTTPException(status_code=404, detail="Contato nao encontrado")
    return contato
