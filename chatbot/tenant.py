"""Tenant vem da sessao validada, nunca de cabecalhos ou parametros do navegador."""
from contextvars import ContextVar

current_tenant = ContextVar('clinicos_tenant', default=None)
current_permissions = ContextVar('clinicos_permissions', default=frozenset())

def tenant_id() -> int:
    value = current_tenant.get()
    if not isinstance(value, int) or value < 1:
        raise RuntimeError('Tenant autorizado ausente')
    return value

def has_permission(permission: str) -> bool:
    return permission in current_permissions.get()
