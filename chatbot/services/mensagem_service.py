"""Regra de negocio do que acontece quando um paciente manda mensagem."""

import logging
from collections import OrderedDict
from datetime import datetime, timezone

from sqlalchemy.orm import Session

from chatbot.config import ID_CLINICA_ATUAL
from chatbot.models.clinica import Clinica  # noqa: F401 — registra `clinica` no metadata para as FKs de Conversa
from chatbot.models.conversa import Conversa, EstadoConversaBot
from chatbot.models.mensagem import DirecaoMensagem, Mensagem, RemetenteMensagem, TipoMensagem
from chatbot.models.usuario import Usuario  # noqa: F401 — mesmo motivo do import de Clinica
from chatbot.services import contato_service, whatsapp_service
from chatbot.views.webhook import MensagemRecebida

logger = logging.getLogger(__name__)

WELCOME_MESSAGE = (
    "Ola! Seja bem-vindo(a)!\n\n"
    "Ficamos felizes com seu contato!\n\n"
    "Para agilizar seu atendimento, escolha uma das opcoes:\n\n"
    "1 - Agendar uma consulta\n"
    "2 - Ja sou paciente\n"
    "3 - Conhecer nossas especialidades\n"
    "4 - Convenios atendidos\n"
    "5 - Atendimento particular"
)

# Tipos do payload da Meta -> enum `tipo_mensagem` do banco. O que nao
# estiver aqui entra como texto: o conteudo ja vem como um rotulo legivel
# ("[vídeo recebido]"), entao o atendente ve algo util no historico.
TIPOS_WHATSAPP = {
    "text": TipoMensagem.texto,
    "audio": TipoMensagem.audio,
    "voice": TipoMensagem.audio,
    "image": TipoMensagem.imagem,
    "sticker": TipoMensagem.imagem,
    "document": TipoMensagem.documento,
    "interactive": TipoMensagem.botao,
    "button": TipoMensagem.botao,
    "location": TipoMensagem.localizacao,
}

# A Meta reenvia o mesmo evento quando nao recebe 200 rapido o bastante, e
# reenvio duplicaria a mensagem no historico (e a boas-vindas). O certo
# seria uma coluna com o wamid em `mensagem` e um UNIQUE, mas schema nao e
# decisao deste modulo (ver README) — entao guardamos os ids recentes em
# memoria. Limitacao conhecida: reiniciar o processo zera a protecao.
_LIMITE_WAMIDS = 2000
_wamids_processados: OrderedDict[str, None] = OrderedDict()


async def processar_mensagens_recebidas(db: Session, mensagens: list[MensagemRecebida]) -> None:
    """Processa o lote de mensagens de um POST do webhook.

    Cada mensagem e isolada: falha em uma nao pode derrubar as outras nem
    impedir o 200 para a Meta (sem 200, ela reenvia o lote inteiro).
    """
    for mensagem in mensagens:
        if mensagem.wamid in _wamids_processados:
            logger.info("Evento repetido do WhatsApp ignorado (wamid=%s).", mensagem.wamid)
            continue
        try:
            await _processar(db, mensagem)
        except Exception:
            db.rollback()
            logger.exception("Falha ao processar mensagem do WhatsApp (wamid=%s).", mensagem.wamid)
        else:
            _marcar_processada(mensagem.wamid)


async def process_incoming_message(telefone: str, conteudo: str) -> None:
    """Compatibilidade: processa uma mensagem avulsa, fora do formato da Meta.

    Util para testar o fluxo pelo /docs sem montar um payload de webhook.
    """
    from chatbot.db import SessionLocal

    db = SessionLocal()
    try:
        await processar_mensagens_recebidas(db, [MensagemRecebida(
            wamid=f"local-{datetime.now(timezone.utc).timestamp()}",
            telefone=whatsapp_service.normalizar_telefone(telefone),
            conteudo=conteudo,
        )])
    finally:
        db.close()


async def send_welcome_message(db: Session, telefone: str, id_paciente: int | None = None) -> None:
    """Envia a mensagem de boas-vindas para um contato novo e registra no historico."""
    await _responder(db, telefone, WELCOME_MESSAGE, id_paciente)


def listar_por_telefone(db: Session, telefone: str) -> list[Mensagem]:
    return (
        db.query(Mensagem)
        .filter(Mensagem.telefone == whatsapp_service.normalizar_telefone(telefone))
        .order_by(Mensagem.criado_em.asc())
        .all()
    )


async def _processar(db: Session, mensagem: MensagemRecebida) -> None:
    telefone = whatsapp_service.normalizar_telefone(mensagem.telefone)
    contato = contato_service.buscar_por_telefone(db, telefone)
    id_paciente = contato.id_paciente if contato else None

    conversa = _obter_ou_criar_conversa(db, telefone, id_paciente)
    # Contado antes de gravar a mensagem nova: e o que define "primeiro
    # contato desse numero".
    primeiro_contato = db.query(Mensagem).filter(Mensagem.telefone == telefone).count() == 0

    db.add(Mensagem(
        id_paciente=id_paciente,
        telefone=telefone,
        direcao=DirecaoMensagem.entrada,
        tipo=TIPOS_WHATSAPP.get(mensagem.tipo_whatsapp, TipoMensagem.texto),
        conteudo=mensagem.conteudo,
        remetente=RemetenteMensagem.paciente,
    ))
    conversa.atualizado_em = datetime.now(timezone.utc)
    db.commit()

    if conversa.estado in (EstadoConversaBot.com_agente, EstadoConversaBot.aguardando):
        # Atendente humano conduzindo: o bot nao responde por cima dele.
        return

    if primeiro_contato:
        await send_welcome_message(db, telefone, id_paciente)
        return

    # Enquanto o motor de opções não estiver implementado, encaminha à recepção.
    conversa.estado = EstadoConversaBot.aguardando
    conversa.agente_nome = None
    conversa.atualizado_em = datetime.now(timezone.utc)
    db.commit()


def _obter_ou_criar_conversa(db: Session, telefone: str, id_paciente: int | None) -> Conversa:
    """Uma conversa por (clinica, telefone) — e o que alimenta a caixa de entrada do painel."""
    conversa = (
        db.query(Conversa)
        .filter(Conversa.id_clinica == ID_CLINICA_ATUAL, Conversa.telefone == telefone)
        .first()
    )
    if conversa is None:
        conversa = Conversa(id_clinica=ID_CLINICA_ATUAL, telefone=telefone, id_paciente=id_paciente)
        db.add(conversa)
        db.flush()
    elif conversa.id_paciente is None and id_paciente is not None:
        # O numero virou paciente cadastrado depois da primeira conversa.
        conversa.id_paciente = id_paciente
    return conversa


async def _responder(db: Session, telefone: str, texto: str, id_paciente: int | None) -> None:
    """Manda pelo WhatsApp e so entao grava — historico nao registra o que nao saiu.

    Falha de envio nao propaga: o webhook precisa devolver 200 mesmo quando
    a resposta automatica nao sai, senao a Meta reenvia a mensagem do
    paciente e ele receberia tudo duplicado quando a API voltar.
    """
    try:
        await whatsapp_service.send_message(to=telefone, message=texto)
    except whatsapp_service.WhatsAppError as erro:
        logger.error("Resposta automatica nao enviada para %s: %s", telefone, erro)
        return

    db.add(Mensagem(
        id_paciente=id_paciente,
        telefone=telefone,
        direcao=DirecaoMensagem.saida,
        tipo=TipoMensagem.texto,
        conteudo=texto,
        remetente=RemetenteMensagem.bot,
    ))
    db.commit()


def _marcar_processada(wamid: str) -> None:
    _wamids_processados[wamid] = None
    while len(_wamids_processados) > _LIMITE_WAMIDS:
        _wamids_processados.popitem(last=False)
