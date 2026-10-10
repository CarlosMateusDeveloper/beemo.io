import { useEffect, useState } from 'react'
import { Search } from 'lucide-react'
import { usePermissions } from '../../auth/permissions'
import { apiRequest } from '../../lib/apiClient'
import { Pacientes } from './pacientes'
import './pacientes.css'

export default function PacientesPermitidos() {
  const { can } = usePermissions()
  if (can('paciente.visualizar')) return <Pacientes />
  return <PacientesCobranca />
}

function PacientesCobranca() {
  const [busca, setBusca] = useState('')
  const [dados, setDados] = useState({ itens: [], total: 0 })
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(true)
  useEffect(() => {
    let active = true
    const timer = window.setTimeout(() => {
      setCarregando(true); setErro('')
      apiRequest(`/api/pacientes/cobranca?busca=${encodeURIComponent(busca)}&tamanho=50`)
        .then(result => { if (active) setDados(result) })
        .catch(error => { if (active) setErro(error.message) })
        .finally(() => { if (active) setCarregando(false) })
    }, 250)
    return () => { active = false; window.clearTimeout(timer) }
  }, [busca])
  const money = value => Number(value || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
  return <main className="pacientes-page">
    <header className="pacientes-head"><div><h1 className="pacientes-titulo">Pacientes para cobrança</h1><p className="pacientes-finance-note">Somente dados administrativos e financeiros necessários à cobrança.</p></div>
      <label className="pacientes-busca"><Search size={16} /><input value={busca} onChange={event => setBusca(event.target.value)} placeholder="Buscar paciente" /></label></header>
    <section className="pacientes-panel">
      {erro && <p role="alert" className="pacientes-state-error">{erro}</p>}
      {carregando && <p className="pacientes-state">Carregando cobranças…</p>}
      {!carregando && !erro && <div className="pacientes-table-wrap"><table className="pacientes-finance-table"><thead><tr><th>Paciente</th><th>Consulta</th><th>Procedimento</th><th>Convênio</th><th>Valor</th><th>Situação</th></tr></thead>
        <tbody>{dados.itens.map(item => <tr key={`${item.consultaId}-${item.pacienteId}`}><td><strong>{item.paciente}</strong></td><td>{item.data} {item.hora?.slice(0, 5)}</td><td>{item.procedimento}</td><td>{item.convenio || 'Particular'}</td><td>{money(item.valor)}</td><td><span className={`pacientes-payment pacientes-payment-${item.situacaoPagamento}`}>{item.situacaoPagamento}</span></td></tr>)}</tbody></table>
        {dados.itens.length === 0 && <p className="pacientes-state">Nenhuma cobrança encontrada.</p>}</div>}
      <footer className="pacientes-finance-footer">{dados.total} registro(s)</footer>
    </section>
  </main>
}
