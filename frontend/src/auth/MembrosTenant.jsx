import { useEffect, useState } from 'react'
import { apiRequest } from '../lib/apiClient'
import './Clinicas.css'

const ROLE_LABELS = { recepcionista: 'Recepcionista', medico: 'Médico', financeiro: 'Financeiro', administrador: 'Administrador' }
const SENSITIVE = [
  ['prontuario.visualizar', 'Visualizar prontuários'],
  ['pagamento.estornar', 'Estornar pagamentos'],
  ['despesa.aprovar', 'Aprovar ou cancelar despesas'],
  ['glosa.aceitar_perda', 'Aceitar perda de glosa'],
]

function MemberEditor({ member, roles, doctors, defaults, catalog, busy, onSave, onRevoke }) {
  const [selectedRoles, setSelectedRoles] = useState(member.papeis || [member.perfil])
  const [doctorId, setDoctorId] = useState(member.id_medico || '')
  const [permissions, setPermissions] = useState(new Set(member.permissoes || []))
  function toggleRole(role) {
    setSelectedRoles(current => {
      const nextRoles = current.includes(role) ? current.filter(item => item !== role) : [...current, role]
      const currentDefaults = new Set(current.flatMap(item => defaults[item] || []))
      const nextDefaults = new Set(nextRoles.flatMap(item => defaults[item] || []))
      setPermissions(existing => {
        const next = new Set(existing)
        for (const permission of catalog) {
          if (!currentDefaults.has(permission) && nextDefaults.has(permission)) next.add(permission)
          else if (currentDefaults.has(permission) && !nextDefaults.has(permission)) next.delete(permission)
        }
        return next
      })
      return nextRoles
    })
  }
  function togglePermission(permission) {
    setPermissions(current => {
      const next = new Set(current)
      if (next.has(permission)) next.delete(permission); else next.add(permission)
      return next
    })
  }
  function save() {
    const roleDefaults = new Set(selectedRoles.flatMap(role => defaults[role] || []))
    const permitir = catalog.filter(permission => permissions.has(permission) && !roleDefaults.has(permission))
    const negar = catalog.filter(permission => !permissions.has(permission) && roleDefaults.has(permission))
    onSave(member.id, { papeis: selectedRoles, idMedico: selectedRoles.includes('medico') && doctorId ? Number(doctorId) : null, permitir, negar })
  }
  return <li className="tenant-member-editor">
    <div><strong>{member.nome}</strong><span>{member.email || `Conta ${member.id}`}</span></div>
    <fieldset><legend>Papéis</legend>{roles.map(role => <label key={role}>
      <input type="checkbox" checked={selectedRoles.includes(role)} onChange={() => toggleRole(role)} disabled={busy} />{ROLE_LABELS[role] || role}
    </label>)}</fieldset>
    {selectedRoles.includes('medico') && <label>Médico vinculado<select value={doctorId} onChange={event => setDoctorId(event.target.value)} disabled={busy}>
      <option value="">Selecione…</option>{doctors.map(doctor => <option key={doctor.id} value={doctor.id}>{doctor.nome}</option>)}
    </select></label>}
    <fieldset><legend>Permissões sensíveis</legend>{SENSITIVE.map(([permission, label]) => <label key={permission}>
      <input type="checkbox" checked={permissions.has(permission)} onChange={() => togglePermission(permission)} disabled={busy} />{label}
    </label>)}</fieldset>
    <div className="tenant-member-actions"><button type="button" disabled={busy || selectedRoles.length === 0} onClick={save}>Salvar acesso</button>
      {member.ativo && <button type="button" className="danger" disabled={busy} onClick={() => onRevoke(member.id)}>Revogar</button>}</div>
  </li>
}

export default function MembrosTenant({ tenant }) {
  const [members, setMembers] = useState([])
  const [model, setModel] = useState({ papeis: [], padroes: {} })
  const [doctors, setDoctors] = useState([])
  const [email, setEmail] = useState('')
  const [role, setRole] = useState('recepcionista')
  const [invite, setInvite] = useState('')
  const [erro, setErro] = useState('')
  const [busy, setBusy] = useState(false)
  const base = `/api/tenants/${tenant.id}`
  async function reload() { setMembers(await apiRequest(base + '/members')) }
  useEffect(() => {
    let active = true
    Promise.all([apiRequest(base + '/members'), apiRequest(base + '/access-model'), apiRequest('/api/medicos')])
      .then(([items, accessModel, medics]) => { if (active) { setMembers(items); setModel(accessModel); setDoctors(medics) } })
      .catch(error => { if (active) setErro(error.message) })
    return () => { active = false }
  }, [base])
  async function inviteMember(event) {
    event.preventDefault(); setBusy(true); setErro(''); setInvite('')
    try {
      const result = await apiRequest(base + '/invites', { method: 'POST', body: JSON.stringify({ email: email.trim(), perfil: role }) })
      setInvite(result.url); setEmail(''); await reload()
    } catch (error) { setErro(error.message) } finally { setBusy(false) }
  }
  async function saveMember(id, payload) {
    setBusy(true); setErro('')
    try { await apiRequest(`${base}/members/${id}`, { method: 'PATCH', body: JSON.stringify(payload) }); await reload() }
    catch (error) { setErro(error.message) } finally { setBusy(false) }
  }
  async function revoke(id) {
    setBusy(true); setErro('')
    try { await apiRequest(`${base}/members/${id}`, { method: 'DELETE' }); await reload() }
    catch (error) { setErro(error.message) } finally { setBusy(false) }
  }
  return <section className="tenant-members"><h2>Usuários e permissões</h2>
    <p>Os papéis são cumulativos e valem somente para <strong>{tenant.nome}</strong>.</p>
    <form onSubmit={inviteMember}>
      <label>E-mail<input type="email" required maxLength={100} value={email} onChange={event => setEmail(event.target.value)} disabled={busy} /></label>
      <label>Papel inicial<select value={role} onChange={event => setRole(event.target.value)} disabled={busy}>
        {(model.papeis.length ? model.papeis : Object.keys(ROLE_LABELS)).map(item => <option value={item} key={item}>{ROLE_LABELS[item] || item}</option>)}
      </select></label>
      <button disabled={busy}>Gerar convite</button>
    </form>
    {invite && <div role="status"><p>Compartilhe este convite. Ele expira em 7 dias.</p><input aria-label="Link do convite" readOnly value={invite} onFocus={event => event.target.select()} /></div>}
    {erro && <p role="alert">{erro}</p>}
    <ul className="tenant-members-list">{members.map(member => <MemberEditor key={member.id} member={member}
      roles={model.papeis || []} doctors={doctors} defaults={model.padroes || {}} catalog={model.permissoes || []}
      busy={busy} onSave={saveMember} onRevoke={revoke} />)}</ul>
  </section>
}
