import { useAuth } from './AuthContext'

export function permissionsOf(user) {
  return new Set(user?.tenantAtivo?.permissoes || [])
}

export function hasPermission(user, required) {
  if (!required || (Array.isArray(required) && required.length === 0)) return true
  const permissions = permissionsOf(user)
  const alternatives = Array.isArray(required) ? required : [required]
  return alternatives.some(permission => permissions.has(permission))
}

export function usePermissions() {
  const { usuario } = useAuth()
  const permissions = permissionsOf(usuario)
  return {
    permissions,
    roles: usuario?.tenantAtivo?.papeis || [],
    doctorId: usuario?.tenantAtivo?.idMedico ?? null,
    can: permission => permissions.has(permission),
    canAny: required => required.some(permission => permissions.has(permission)),
  }
}
