"""Valida sessão e CSRF dos operadores no backend do clinicOS."""
import os
import httpx
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import JSONResponse

class SessionMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):
        if request.method == 'OPTIONS' or request.url.path.rstrip('/') in ('/health', '/webhook'):
            return await call_next(request)
        token = request.headers.get('authorization', '')
        cookie = request.cookies.get('clinicos_session', '')
        if (not cookie and not token.startswith('Bearer ')) or len(token) > 8192:
            return JSONResponse({'detail': 'Faça login para continuar.'}, status_code=401)
        headers = {'Authorization': token, 'X-CSRF-TOKEN': request.headers.get('x-csrf-token', '')}
        # Somente os cookies necessários atravessam a chamada interna.
        headers['Cookie'] = '; '.join(f'{name}={request.cookies[name]}' for name in ('clinicos_session', 'clinicos_csrf') if name in request.cookies)
        method = 'GET' if request.method in ('GET', 'HEAD', 'OPTIONS') else 'POST'
        try:
            async with httpx.AsyncClient(timeout=5, follow_redirects=False) as client:
                response = await client.request(method, os.getenv('AUTH_API_URL', 'http://localhost:8080').rstrip('/') + '/api/auth/session/check', headers=headers)
            if response.status_code == 403:
                return JSONResponse({'code': 'csrf_invalid', 'detail': 'Atualize a sessão e tente novamente.'}, status_code=403)
            if response.status_code == 401:
                return JSONResponse({'detail': 'Sessão expirada. Faça login novamente.'}, status_code=401)
            if response.status_code != 200:
                return JSONResponse({'detail': 'Não foi possível validar a sessão.'}, status_code=503)
        except httpx.HTTPError:
            return JSONResponse({'detail': 'Não foi possível validar a sessão.'}, status_code=503)
        return await call_next(request)
