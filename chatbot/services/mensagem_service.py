from chatbot.services import whatsapp_service

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


async def process_incoming_message(telefone: str, conteudo: str) -> None:
    """Ponto de entrada para processar uma mensagem recebida do paciente.

    Estrutura inicial apenas - o fluxo de decisao completo (identificar se
    e a primeira mensagem, interpretar a opcao escolhida, etc) ainda nao
    foi implementado.
    """
    raise NotImplementedError("Processamento de mensagens ainda nao implementado.")


async def send_welcome_message(telefone: str) -> None:
    """Envia a mensagem de boas-vindas para um contato novo."""
    await whatsapp_service.send_message(to=telefone, message=WELCOME_MESSAGE)
