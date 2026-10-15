import { useEffect, useState } from 'react'
import { AlertTriangle, CheckCircle2, Clock3, ShieldCheck, SlidersHorizontal } from 'lucide-react'
import { apiRequest } from '../../lib/apiClient'
import './PosVenda.css'

const numero = valor => valor == null ? '—' : Number(valor).toLocaleString('pt-BR', { maximumFractionDigits: 1 })
const percentual = (valor, total) => total > 0 ? `${numero((Number(valor) / Number(total)) * 100)}%` : '—'
const dataLocal = data => `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`

export default function PosVenda() {
  const [dados, setDados] = useState(null)
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(true)

  useEffect(() => {
    const hoje = new Date()
    const inicio = new Date(hoje.getFullYear(), hoje.getMonth(), 1)
    let ativo = true
    apiRequest(`/api/pos-venda/indicadores?${new URLSearchParams({ inicio: dataLocal(inicio), fim: dataLocal(hoje) })}`)
      .then(resposta => { if (ativo) setDados(resposta) })
      .catch(e => { if (ativo) setErro(e.message) })
      .finally(() => { if (ativo) setCarregando(false) })
    return () => { ativo = false }
  }, [])

  const r = dados?.resumo
  const total = Number(r?.faltas || 0)
  const stages = r ? [
    ['Casos identificados', r.faltas, 'Casos de falta da coorte', 'casos', `${numero(r.pacientes_unicos)} pacientes únicos`, 'identified'],
    ['Contatados', r.contatados, 'Ao menos uma tentativa registrada', 'casos', `${percentual(r.contatados, total)} de cobertura`, 'contacted'],
    ['Reagendados', r.reagendados, 'Nova consulta vinculada', 'casos', 'Ainda aguarda desfecho', 'scheduled'],
    ['Compareceram', r.recuperados, 'Presença confirmada na agenda', 'casos', `${percentual(r.recuperados, total)} de recuperação`, 'completed'],
  ] : []

  return <main className="pos-venda-module" aria-labelledby="pos-venda-title"><div className="pv-dashboard">
    <header className="pv-page-heading"><div><p>Pós-atendimento e retenção</p><h1 id="pos-venda-title">Visão geral</h1></div>{dados && <span className="pv-demo-label">Dados reais · {dados.fuso}</span>}</header>
    {carregando && <section className="pv-panel pv-dashboard-state" aria-live="polite"><Clock3 size={18} />Carregando indicadores da clínica…</section>}
    {erro && <section className="pv-panel pv-dashboard-state pv-dashboard-error" role="alert"><AlertTriangle size={18} /><div><strong>Não foi possível carregar os indicadores</strong><p>{erro}</p></div></section>}
    {dados && <>
      <section className="pv-kpis" aria-label="Indicadores de recuperação de faltosos">
        <Kpi title="Casos de falta" value={numero(r.faltas)} unit="casos" note={`${numero(r.pacientes_unicos)} pacientes únicos`} />
        <Kpi title="Pacientes recuperados" value={numero(r.recuperados_pacientes)} unit="pacientes" note={`${numero(r.recuperados)} casos com comparecimento`} tone="teal" />
        <Kpi title="Oportunidades em aberto" value={numero(r.abertos)} unit="casos" note={`${numero(r.sem_responsavel)} sem responsável`} />
        <Kpi title="Taxa de recuperação" value={r.taxa_recuperacao == null ? '—' : `${numero(r.taxa_recuperacao)}%`} note="Comparecimentos ÷ casos de falta" tone="teal" />
      </section>
      <section className="pv-panel pv-funnel" aria-labelledby="pv-funnel-title">
        <header className="pv-panel-heading"><div className="pv-panel-title"><SlidersHorizontal size={18} aria-hidden="true" /><h2 id="pv-funnel-title">Funil real de recuperação de faltosos</h2><span className="pv-tag">Coorte mensal</span></div><div className="pv-funnel-meta"><span>{dados.inicio.split('-').reverse().join('/')} a {dados.fim.split('-').reverse().join('/')}</span><span>Última atualização: <strong>{r.ultima_atualizacao ? new Date(r.ultima_atualizacao).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : 'sem registros'}</strong></span></div></header>
        {total === 0 ? <div className="pv-dashboard-empty"><strong>Nenhum caso de falta neste período</strong><p>A API respondeu normalmente; ainda não há casos para compor o funil.</p></div> : <ol className="pv-stages" style={{ gridTemplateRows: `repeat(${stages.length}, minmax(0, 1fr))` }}>{stages.map(([name, count, note, result, detail, tone], index) => <li className={`pv-stage pv-stage-${tone}`} key={name}>
          <div className="pv-stage-label"><h3>{index + 1}. {name} <span>{percentual(count, total)}</span></h3><p>{note}</p></div>
          <div className="pv-stage-track" aria-hidden="true"><div className="pv-stage-fill" style={{ width: `${Math.max(4, Number(count) / total * 100)}%` }}>{tone === 'completed' && <CheckCircle2 size={13} />}<span>{numero(count)}</span></div></div>
          <div className="pv-stage-result"><strong>{numero(count)} {result}</strong><span>{detail}</span></div>
        </li>)}</ol>}
        <div className="pv-insight"><ShieldCheck size={16} aria-hidden="true" /><p><strong>Regra de recuperação:</strong> reagendamento não encerra o caso. O caso só é recuperado quando a agenda registra o atendimento como realizado.</p><span>Receita: <strong>aguardando evento financeiro verificável</strong></span></div>
      </section>
      <div className="pv-details">
        <section className="pv-panel" aria-labelledby="pv-operation-title"><header className="pv-panel-heading"><div className="pv-panel-title"><Clock3 size={18} /><h2 id="pv-operation-title">Operação da fila</h2></div></header><div className="pv-detail-grid"><Metric title="Tempo até 1º contato" value={r.horas_primeiro_contato == null ? '—' : numero(r.horas_primeiro_contato)} unit={r.horas_primeiro_contato == null ? '' : 'horas'} note="Média dos casos contatados" /><Metric title="Pendências vencidas" value={numero(r.pendencias_vencidas)} note="Próxima ação já passou" tone="danger" /><Metric title="Sem responsável" value={numero(r.sem_responsavel)} note="Casos abertos para distribuir" tone="danger" /></div></section>
        <section className="pv-panel" aria-labelledby="pv-outcomes-title"><header className="pv-panel-heading"><div className="pv-panel-title"><ShieldCheck size={18} /><h2 id="pv-outcomes-title">Desfechos atuais</h2></div></header><div className="pv-detail-grid"><Metric title="Aguardam presença" value={numero(r.aguardando_comparecimento)} note="Reagendados sem desfecho" /><Metric title="Nova falta" value={numero(r.novas_faltas)} note="Voltaram ao acompanhamento" tone="danger" /><Metric title="Tentativas" value={numero(r.tentativas)} note="Eventos de contato registrados" tone="teal" /></div></section>
      </div>
      <p className="pv-data-note">Coorte por casos iniciados no período, no fuso {dados.fuso}. Várias faltas da mesma pessoa são casos distintos; pacientes únicos são apresentados separadamente. Valores financeiros não são estimados.</p>
    </>}
  </div></main>
}

function Kpi({ title, value, unit, note, tone = '' }) {
  return <article className={`pv-kpi ${tone ? `pv-kpi-${tone}` : ''}`}><div className="pv-kpi-heading"><h2>{title}</h2></div><p className="pv-kpi-value">{value} {unit && <span>{unit}</span>}</p><div className="pv-kpi-footer">{tone === 'teal' && <CheckCircle2 size={15} aria-hidden="true" />}<span>{note}</span></div></article>
}

function Metric({ title, value, unit, note, tone = '' }) {
  return <div className={`pv-metric ${tone ? `pv-metric-${tone}` : ''}`}><h3>{title}</h3><p>{value} {unit && <span>{unit}</span>}</p><small>{note}</small></div>
}
