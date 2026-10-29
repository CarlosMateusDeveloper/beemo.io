"""Regra de negocio do que acontece quando um paciente manda mensagem."""

import logging
from collections import OrderedDict
from datetime import datetime, timezone

from sqlalchemy.orm import Session

from chatbot.tenant import tenant_id
from chatbot.models.clinica import Clinica  # noqa: F401 — registra `clinica` no metadata para as FKs de Conversa
from chatbot.models.conversa import Conversa, EstadoConversaBot
from chatbot.models.mensagem import DirecaoMensagem, Mensagem, RemetenteMensagem, TipoMensagem
from chatbot.models.usuario import Usuario  # noqa: F401 — mesmo motivo do import de Clinica
from chatbot.services import comunicacao_policy_service, contato_service, mensageria_service, whatsapp_event_service, whatsapp_service
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
# reenvio duplicaria a mensagem no histórico. A tabela
# `mensageria_evento_recebido` é a garantia persistente; este cache reduz
# consultas repetidas no mesmo processo.
_LIMITE_WAMIDS = 2000
_wamids_processados: OrderedDict[str, None] = OrderedDict()


async def processar_mensagens_recebidas(db: Session, mensagens: list[MensagemRecebida]) -> None:
    """Processa o lote de mensagens de um POST do webhook.

    Cada mensagem e isolada: falha em uma nao pode derrubar as outras nem
    impedir o 200 para a Meta (sem 200, ela reenvia o lote inteiro).
    """
    for mensagem in mensagens:
        if mensagem.wamid in _wamids_processados:
            logger.info("Evento repetido do WhatsApp ignorado.")
            continue
        if not mensageria_service.register_incoming(db, mensagem.wamid):
            logger.info("Evento persistente repetido do WhatsApp ignorado.")
            continue
        try:
            await _processar(db, mensagem)
        except Exception as error:
            db.rollback()
            mensageria_service.finish_incoming(db, mensagem.wamid, str(error))
            logger.error("Falha ao processar mensagem do WhatsApp (tipo=%s).", type(error).__name__)
        else:
            mensageria_service.finish_incoming(db, mensagem.wamid)
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


async def send_welcome_message(db: Session, telefone: str, id_paciente: int | None = None, idempotency_key: str | None = None) -> None:
    """Envia a mensagem de boas-vindas para um contato novo e registra no historico."""
    await _responder(db, telefone, WELCOME_MESSAGE, id_paciente, idempotency_key or f"welcome:{telefone}")


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

    # Se uma etapa posterior falhar, o registro de idempotencia permite nova
    # tentativa. Nesse caso a mensagem ja pode ter sido persistida: reutiliza-la
    # evita duplicidade e deixa o processamento retomar da correlacao.
    received = db.query(Mensagem).filter(Mensagem.id_mensagem_provedor == mensagem.wamid).first()
    if received is None:
        received = Mensagem(
            id_paciente=id_paciente,
            telefone=telefone,
            direcao=DirecaoMensagem.entrada,
            tipo=TIPOS_WHATSAPP.get(mensagem.tipo_whatsapp, TipoMensagem.texto),
            conteudo=mensagem.conteudo,
            remetente=RemetenteMensagem.paciente,
            id_mensagem_provedor=mensagem.wamid,
            status_entrega="recebida",
        )
        db.add(received)
        db.flush()
    conversa.atualizado_em = datetime.now(timezone.utc)
    db.commit()

    if comunicacao_policy_service.texto_solicita_descadastro(mensagem.conteudo):
        comunicacao_policy_service.registrar_descadastro(
            db, paciente_id=id_paciente, telefone=telefone,
            evidencia=f"whatsapp:{mensagem.wamid}",
        )
        whatsapp_event_service.close_contact_after_optout(db, telefone)
        conversa.estado = EstadoConversaBot.aguardando
        conversa.agente_nome = None
        conversa.atualizado_em = datetime.now(timezone.utc)
        db.commit()
        return

    whatsapp_event_service.correlate_incoming(db, received.id_mensagem, telefone, mensagem.wamid)
    whatsapp_event_service.correlate_crm_incoming(db, received.id_mensagem, telefone, mensagem.wamid, id_paciente)

    if conversa.estado in (EstadoConversaBot.com_agente, EstadoConversaBot.aguardando):
        # Atendente humano conduzindo: o bot nao responde por cima dele.
        return

    if primeiro_contato:
        await send_welcome_message(db, telefone, id_paciente, f"welcome:{mensagem.wamid}")
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
        .filter(Conversa.id_clinica == tenant_id(), Conversa.telefone == telefone)
        .first()
    )
    if conversa is None:
        conversa = Conversa(id_clinica=tenant_id(), telefone=telefone, id_paciente=id_paciente)
        db.add(conversa)
        db.flush()
    elif conversa.id_paciente is None and id_paciente is not None:
        # O numero virou paciente cadastrado depois da primeira conversa.
        conversa.id_paciente = id_paciente
    return conversa


async def _responder(db: Session, telefone: str, texto: str, id_paciente: int | None, idempotency_key: str) -> None:
    """Valida a política e coloca a resposta automática na outbox."""
    try:
        decisao = comunicacao_policy_service.avaliar_e_registrar(
            db, paciente_id=id_paciente, telefone=telefone, canal="whatsapp",
            finalidade="atendimento", essencial=True, modelo_id=None,
            origem="whatsapp_bot",
        )
        queued, _created = mensageria_service.enqueue_text(
            db, idempotency_key=idempotency_key, telefone=telefone,
            texto_mensagem=texto, paciente_id=id_paciente,
            remetente=RemetenteMensagem.bot, tentativa_id=decisao.tentativa_id,
            consentimento_id=decisao.consentimento_id, modelo_id=decisao.modelo_id,
            modelo_versao=decisao.modelo_versao, finalidade="atendimento",
            origem="whatsapp_bot",
        )
        whatsapp_event_service.link_crm_outgoing(db, queued.id_mensagem, telefone, automatic=True)
    except (ValueError, comunicacao_policy_service.ComunicacaoBloqueadaError) as erro:
        logger.error("Resposta automatica nao enviada (tipo=%s).", type(erro).__name__)
        return


def _marcar_processada(wamid: str) -> None:
    _wamids_processados[wamid] = None
    while len(_wamids_processados) > _LIMITE_WAMIDS:
        _wamids_processados.popitem(last=False)
