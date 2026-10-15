import unittest

from chatbot.services.mensageria_service import classify_error, retry_delay_seconds
from chatbot.services.whatsapp_service import WhatsAppError, WhatsAppNaoConfiguradoError


class MensageriaPolicyTest(unittest.TestCase):
    def test_timeout_and_provider_unavailable_are_transient(self):
        self.assertTrue(classify_error(TimeoutError()).transient)
        self.assertTrue(classify_error(WhatsAppError("indisponível", status=503)).transient)

    def test_rate_limit_is_transient(self):
        decision = classify_error(WhatsAppError("limite", codigo=4, status=429))
        self.assertTrue(decision.transient)
        self.assertEqual("4", decision.code)

    def test_bad_request_and_missing_configuration_are_permanent(self):
        self.assertFalse(classify_error(WhatsAppError("inválida", status=400)).transient)
        self.assertFalse(classify_error(WhatsAppNaoConfiguradoError("sem credencial")).transient)

    def test_backoff_is_exponential_and_capped(self):
        self.assertEqual([15, 30, 60, 120], [retry_delay_seconds(n) for n in range(1, 5)])
        self.assertEqual(3600, retry_delay_seconds(20))


if __name__ == "__main__":
    unittest.main()
