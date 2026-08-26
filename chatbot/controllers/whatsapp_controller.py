from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from chatbot.db import get_db
from chatbot.services import whatsapp_painel_service as service
from chatbot.views.whatsapp import AssumirIn, AtualizarMensagensIn, EnviarMensagemIn, TogglecapacidadeIn

router = APIRouter(prefix="/whatsapp", tags=["whatsapp"])


@router.get("/status")
def status():
    return service.obter_status()


@router.get("/conversas")
def listar_conversas(db: Session = Depends(get_db)):
    return service.listar_conversas(db)


@router.get("/conversas/{conversa_id}/mensagens")
def listar_mensagens(conversa_id: int, db: Session = Depends(get_db)):
    mensagens = service.listar_mensagens_conversa(db, conversa_id)
    if mensagens is None:
        raise HTTPException(status_code=404, detail="Conversa não encontrada")
    return mensagens


@router.get("/conversas/{conversa_id}/contexto")
def obter_contexto(conversa_id: int, db: Session = Depends(get_db)):
    contexto = service.obter_contexto_conversa(db, conversa_id)
    # None e uma resposta valida (telefone nao cadastrado) — nao e 404.
    return contexto


@router.post("/conversas/{conversa_id}/assumir")
def assumir(conversa_id: int, body: AssumirIn, db: Session = Depends(get_db)):
    conversa = service.assumir_conversa(db, conversa_id, body.agenteNome)
    if conversa is None:
        raise HTTPException(status_code=404, detail="Conversa não encontrada")
    return {"status": "ok"}


@router.post("/conversas/{conversa_id}/devolver")
def devolver(conversa_id: int, db: Session = Depends(get_db)):
    conversa = service.devolver_conversa(db, conversa_id)
    if conversa is None:
        raise HTTPException(status_code=404, detail="Conversa não encontrada")
    return {"status": "ok"}


@router.post("/conversas/{conversa_id}/mensagens")
def enviar_mensagem(conversa_id: int, body: EnviarMensagemIn, db: Session = Depends(get_db)):
    mensagem = service.enviar_mensagem_agente(db, conversa_id, body.texto)
    if mensagem is None:
        raise HTTPException(status_code=404, detail="Conversa não encontrada")
    return mensagem


@router.get("/assistente")
def obter_assistente(db: Session = Depends(get_db)):
    return service.obter_assistente(db)


@router.patch("/assistente/capacidades/{capacidade_id}")
def alternar_capacidade(capacidade_id: str, body: TogglecapacidadeIn, db: Session = Depends(get_db)):
    service.alternar_capacidade(db, capacidade_id, body.ativo)
    return {"status": "ok"}


@router.patch("/assistente/mensagens/{capacidade_id}")
def atualizar_mensagens(capacidade_id: str, body: AtualizarMensagensIn, db: Session = Depends(get_db)):
    service.atualizar_mensagens(db, capacidade_id, body.campos)
    return {"status": "ok"}


@router.patch("/assistente/regras")
def atualizar_regras(regras: dict, db: Session = Depends(get_db)):
    service.atualizar_regras(db, regras)
    return {"status": "ok"}


@router.get("/desempenho")
def desempenho(periodo: str = "Últimos 30 dias", db: Session = Depends(get_db)):
    return service.calcular_desempenho(db, periodo)
