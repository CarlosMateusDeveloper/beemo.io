from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from chatbot.db import get_db
from chatbot.services import mensageria_service, whatsapp_integracao_service, whatsapp_painel_service as service, whatsapp_pos_venda_service
from chatbot.tenant import current_user
from chatbot.views.whatsapp import AssumirIn, AtualizarMensagensIn, EnviarMensagemIn, LimiteMensageriaIn, PosVendaWhatsAppIn, TogglecapacidadeIn, WhatsAppConfigurationIn

router = APIRouter(prefix="/whatsapp", tags=["whatsapp"])


@router.get("/status")
def status(db: Session = Depends(get_db)):
    return service.obter_status(db)


@router.get("/atualizacoes")
async def atualizacoes(after: str | None = None, db: Session = Depends(get_db)):
    return await service.wait_for_updates(db, after)


@router.get("/configuracao")
def obter_configuracao(db: Session = Depends(get_db)):
    return whatsapp_integracao_service.get_configuration(db)


@router.put("/configuracao")
def salvar_configuracao(body: WhatsAppConfigurationIn, db: Session = Depends(get_db)):
    try:
        return whatsapp_integracao_service.save_configuration(db, body.model_dump(), current_user.get())
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error


@router.get("/pos-venda/casos/{case_id}")
def preparar_mensagem_pos_venda(case_id: int, db: Session = Depends(get_db)):
    result = whatsapp_pos_venda_service.prepare(db, case_id)
    if result is None:
        raise HTTPException(status_code=404, detail="Caso não encontrado")
    return result


@router.post("/pos-venda/casos/{case_id}/mensagens")
def enviar_mensagem_pos_venda(case_id: int, body: PosVendaWhatsAppIn, db: Session = Depends(get_db)):
    try:
        result = whatsapp_pos_venda_service.send(db, case_id, body)
    except ValueError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
    if result is None:
        raise HTTPException(status_code=404, detail="Caso não encontrado")
    return result


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
    try:
        mensagem = service.enviar_mensagem_agente(db, conversa_id, body.texto, body.idempotencyKey)
    except ValueError as erro:
        raise HTTPException(status_code=409, detail=str(erro)) from erro
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


@router.get("/operacao/mensageria/resumo")
def resumo_mensageria(db: Session = Depends(get_db)):
    return mensageria_service.operation_summary(db)


@router.get("/operacao/mensageria")
def listar_mensageria(status: str | None = None, limite: int = 100, db: Session = Depends(get_db)):
    permitidos = {"pendente", "processando", "retentativa", "enviada", "morta", "cancelada"}
    if status is not None and status not in permitidos:
        raise HTTPException(status_code=422, detail="Status de mensageria inválido")
    return mensageria_service.list_outbox(db, status, limite)


@router.post("/operacao/mensageria/{outbox_id}/reprocessar")
def reprocessar_mensagem(outbox_id: int, db: Session = Depends(get_db)):
    if not mensageria_service.reprocess(db, outbox_id, current_user.get()):
        raise HTTPException(status_code=409, detail="Somente mensagens na fila de falhas podem ser reprocessadas.")
    return {"status": "reprocessada"}


@router.get("/operacao/mensageria/limites")
def listar_limites_mensageria(db: Session = Depends(get_db)):
    return mensageria_service.list_limits(db)


@router.put("/operacao/mensageria/limites")
def salvar_limite_mensageria(body: LimiteMensageriaIn, db: Session = Depends(get_db)):
    return mensageria_service.upsert_limit(
        db, body.escopo, body.chave, body.janelaSegundos, body.maxEnvios, body.ativo
    )
