import { useEffect, useState } from 'react'
import { apiRequest } from '../lib/apiClient'

const ACTION_LABELS = {
  'prontuario.visualizar': 'Visualização de prontuário',
  'prontuario.editar': 'Alteração de prontuário',
  'permissao.gerenciar': 'Alteração de permissões',
  'glosa.aceitar_perda': 'Aceite de perda de glosa',
  'despesa.aprovar': 'Aprovação de despesa',
  'caixa.fechar': 'Fechamento de caixa',
  'fatura.editar': 'Alteração de fatura',
  'repasse.editar': 'Alteração de médico ou repasse',
  'campanha.configurar': 'Configuração de campanha',
}

export default function AuditoriaAcesso({ tenant }) {
  const [events, setEvents] = useState([])
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    apiRequest(`/api/tenants/${tenant.id}/audit?limit=50`)
      .then(data => { if (active) setEvents(data) })
      .catch(reason => { if (active) setError(reason.message) })
    return () => { active = false }
  }, [tenant.id])
  return <section className="access-audit">
    <h2>Auditoria de acessos</h2>
    <p>Últimas ações sensíveis registradas nesta clínica.</p>
    {error && <p role="alert">{error}</p>}
    {!error && events.length === 0 && <p>Nenhuma ação sensível registrada.</p>}
    {events.length > 0 && <div className="access-audit-table-wrap"><table>
      <thead><tr><th>Data</th><th>Usuário</th><th>Ação</th><th>Resultado</th></tr></thead>
      <tbody>{events.map(event => <tr key={event.id}>
        <td>{new Date(event.criado_em).toLocaleString('pt-BR')}</td>
        <td>{event.usuario || `Conta ${event.id_usuario || 'desconhecida'}`}</td>
        <td><strong>{ACTION_LABELS[event.acao] || event.acao}</strong><small>{event.recurso}</small></td>
        <td><span className={event.resultado < 400 ? 'audit-ok' : 'audit-denied'}>{event.resultado}</span></td>
      </tr>)}</tbody>
    </table></div>}
  </section>
}
