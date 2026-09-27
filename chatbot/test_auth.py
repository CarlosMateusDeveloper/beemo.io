import unittest
from unittest.mock import patch, AsyncMock
from fastapi import FastAPI
from fastapi.testclient import TestClient
import httpx
from chatbot.auth import SessionMiddleware

class SessionTest(unittest.TestCase):
    def setUp(self):
        app = FastAPI()
        app.add_middleware(SessionMiddleware)
        @app.get('/whatsapp/status')
        def status(): return {'ok': True}
        @app.post('/whatsapp/send')
        def send(): return {'ok': True}
        @app.get('/health')
        def health(): return {'ok': True}
        self.client = TestClient(app)

    def test_sem_sessao_e_health(self):
        self.assertEqual(self.client.get('/whatsapp/status').status_code, 401)
        self.assertEqual(self.client.get('/health').status_code, 200)

    def test_valida_sessao_e_falha_fechada(self):
        for upstream, expected in [(200, 200), (401, 401), (403, 403), (500, 503)]:
            with patch('chatbot.auth.httpx.AsyncClient') as mock:
                mock.return_value.__aenter__.return_value.request = AsyncMock(return_value=httpx.Response(upstream))
                r = self.client.get('/whatsapp/status', headers={'Authorization': 'Bearer test-session-only'})
                self.assertEqual(r.status_code, expected)

    def test_backend_indisponivel(self):
        with patch('chatbot.auth.httpx.AsyncClient') as mock:
            mock.return_value.__aenter__.return_value.request = AsyncMock(side_effect=httpx.ConnectError('offline'))
            self.assertEqual(self.client.get('/whatsapp/status', headers={'Authorization': 'Bearer test-session-only'}).status_code, 503)

    def test_cookie_e_csrf_encaminhados(self):
        self.client.cookies.set('clinicos_session', 'test-session')
        self.client.cookies.set('clinicos_csrf', 'test-csrf')
        with patch('chatbot.auth.httpx.AsyncClient') as mock:
            upstream = AsyncMock(return_value=httpx.Response(200))
            mock.return_value.__aenter__.return_value.request = upstream
            self.assertEqual(self.client.post('/whatsapp/send', headers={'X-CSRF-TOKEN': 'test-csrf'}).status_code, 200)
            args, kwargs = upstream.call_args
            self.assertEqual(args[0], 'POST')
            self.assertTrue(args[1].endswith('/api/auth/session/check'))
            self.assertEqual(kwargs['headers']['X-CSRF-TOKEN'], 'test-csrf')
            self.assertIn('clinicos_session=test-session', kwargs['headers']['Cookie'])

if __name__ == '__main__': unittest.main()
