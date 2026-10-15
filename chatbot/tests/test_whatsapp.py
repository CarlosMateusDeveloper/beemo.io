import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock, patch
from pydantic import ValidationError
from chatbot.views.whatsapp import EnviarMensagemIn, AssumirIn
from chatbot.models.conversa import EstadoConversaBot
from chatbot.services import mensagem_service, whatsapp_painel_service as painel
from chatbot.services import comunicacao_policy_service
from chatbot.tenant import current_tenant
from chatbot.views.webhook import MensagemRecebida


class ValidacaoTest(unittest.TestCase):
    def setUp(self):
        self.tenant_marker = current_tenant.set(1)

    def tearDown(self):
        current_tenant.reset(self.tenant_marker)

    def test_rejeita_mensagem_vazia_e_nome_vazio(self):
        for modelo, dados in [(EnviarMensagemIn, {"texto": "  "}), (AssumirIn, {"agenteNome": "  "})]:
            with self.assertRaises(ValidationError):
                modelo(**dados)
        self.assertEqual(EnviarMensagemIn(texto=" Oi ", idempotencyKey="request-123").texto, "Oi")

    def test_nao_envia_sem_assumir(self):
        with patch.object(painel, "_buscar_conversa", return_value=SimpleNamespace(estado=EstadoConversaBot.bot)), patch.object(painel.whatsapp_service, "send_message_sync") as enviar:
            with self.assertRaises(ValueError):
                painel.enviar_mensagem_agente(MagicMock(), 1, "Olá", "request-123")
            enviar.assert_not_called()

    def test_assistente_sem_regras_tem_campos_completos(self):
        db = MagicMock()
        db.get.return_value = None
        regras = painel.obter_assistente(db)["regras"]
        self.assertEqual(regras["escalonamento"]["palavrasChave"], [])
        self.assertEqual(regras["disparos"]["confirmacaoPresencaHoras"], 24)


class EncaminhamentoTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.tenant_marker = current_tenant.set(1)

    def tearDown(self):
        current_tenant.reset(self.tenant_marker)

    async def test_encaminha_seguimento_e_respeita_atendente(self):
        for estado in [EstadoConversaBot.bot, EstadoConversaBot.aguardando, EstadoConversaBot.com_agente]:
            db = MagicMock()
            db.query.return_value.filter.return_value.count.return_value = 2
            conversa = SimpleNamespace(estado=estado, agente_nome=None)
            with patch.object(mensagem_service.contato_service, "buscar_por_telefone", return_value=None), patch.object(mensagem_service, "_obter_ou_criar_conversa", return_value=conversa), patch.object(mensagem_service, "send_welcome_message", new_callable=AsyncMock) as welcome:
                await mensagem_service._processar(db, MensagemRecebida(wamid="teste", telefone="5585999999999", conteudo="1"))
                esperado = EstadoConversaBot.aguardando if estado == EstadoConversaBot.bot else estado
                self.assertEqual(conversa.estado, esperado)
                welcome.assert_not_awaited()

    async def test_descadastro_recebido_revoga_e_nao_responde(self):
        db = MagicMock()
        db.query.return_value.filter.return_value.count.return_value = 0
        conversa = SimpleNamespace(estado=EstadoConversaBot.bot, agente_nome=None)
        entrada = MensagemRecebida(wamid="wamid-optout", telefone="5585999999999", conteudo="NÃO quero receber")
        with patch.object(mensagem_service.contato_service, "buscar_por_telefone", return_value=None), \
             patch.object(mensagem_service, "_obter_ou_criar_conversa", return_value=conversa), \
             patch.object(mensagem_service, "send_welcome_message", new_callable=AsyncMock) as welcome, \
             patch.object(mensagem_service.comunicacao_policy_service, "registrar_descadastro") as revogar:
            await mensagem_service._processar(db, entrada)
        revogar.assert_called_once()
        welcome.assert_not_awaited()
        self.assertEqual(conversa.estado, EstadoConversaBot.aguardando)

    def test_palavras_de_descadastro_sao_exatas(self):
        self.assertTrue(comunicacao_policy_service.texto_solicita_descadastro("  SAIR! "))
        self.assertTrue(comunicacao_policy_service.texto_solicita_descadastro("Não quero receber"))
        self.assertFalse(comunicacao_policy_service.texto_solicita_descadastro("Quero cancelar minha consulta"))


if __name__ == "__main__":
    unittest.main()
