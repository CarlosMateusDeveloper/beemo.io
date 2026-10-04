import { ArrowUpRight, CheckCircle2, Gauge, ShieldCheck, SlidersHorizontal, Zap } from 'lucide-react'
import './PosVenda.css'

const stages = [
  ['Identificados', 214, '100%', 'Base total ativa', 'pacientes', '100% elegíveis', 'identified'],
  ['Contatados', 167, '78,0%', '47 pacientes sem contato', 'contatados', '78,0% cobertura', 'contacted'],
  ['Responderam', 89, '41,6%', '53,3% dos contatados', 'respostas', '53,3% engajamento', 'answered'],
  ['Agendamentos', 52, '24,3%', '58,4% das respostas', 'consultas', '58,4% conversão', 'scheduled'],
  ['Concluídos', 43, '20,1%', '82,7% comparecimento', 'retornos', 'R$ 12.480 faturados', 'completed'],
]

export default function PosVenda() {
  return <main className="pos-venda-module" aria-labelledby="pos-venda-title"><div className="pv-dashboard">
    <header className="pv-page-heading"><div><p>Pós-atendimento e retenção</p><h1 id="pos-venda-title">Visão geral</h1></div><span className="pv-demo-label">Dados demonstrativos</span></header>
    <section className="pv-kpis" aria-label="Indicadores de pós-venda">
      <article className="pv-kpi"><div className="pv-kpi-heading"><h2>Receita recuperada</h2><span className="pv-badge"><ArrowUpRight size={14} aria-hidden="true" />18,4%</span></div><p className="pv-kpi-value">R$ 12.480</p><div className="pv-kpi-footer"><span>Ticket médio: <strong>R$ 290/cons.</strong></span><div className="pv-sparkline" aria-hidden="true">{[30, 42, 58, 72, 100].map(height => <i key={height} style={{ height: `${height}%` }} />)}</div></div></article>
      <article className="pv-kpi"><div className="pv-kpi-heading"><h2>Pacientes recuperados</h2><span className="pv-badge">+7 esta sem.</span></div><p className="pv-kpi-value">43 <span>pacientes</span></p><div className="pv-kpi-footer"><CheckCircle2 size={15} aria-hidden="true" /><span>100% faturados em prontuário</span></div></article>
      <article className="pv-kpi"><div className="pv-kpi-heading"><h2>Oportunidades em aberto</h2><span className="pv-badge pv-neutral">Fila ativa</span></div><p className="pv-kpi-value pv-teal">171 <span>elegíveis</span></p><div className="pv-kpi-footer">114 recall preventivo · 57 pós-tratamento</div></article>
      <article className="pv-kpi"><div className="pv-kpi-heading"><h2>Taxa de conversão real</h2><span className="pv-badge">+5,7 pp vs meta</span></div><p className="pv-kpi-value">25,7% <span>(43/167 cont.)</span></p><div className="pv-kpi-footer"><span>Benchmark: <strong>20%</strong></span><span className="pv-benchmark-line" aria-hidden="true" /></div></article>
    </section>
    <section className="pv-panel pv-funnel" aria-labelledby="pv-funnel-title">
      <header className="pv-panel-heading"><div className="pv-panel-title"><SlidersHorizontal size={18} aria-hidden="true" /><h2 id="pv-funnel-title">Funil de Eficiência de Retorno</h2><span className="pv-tag">Pipeline Clínico BI</span></div><div className="pv-funnel-meta"><span>Benchmark setorial: <strong>15–20%</strong></span><span>Ciclo médio: <strong>3,8 dias</strong></span><span className="pv-badge"><ArrowUpRight size={13} aria-hidden="true" />Eficiência geral: 20,1%</span></div></header>
      <ol className="pv-stages">{stages.map(([name, count, percent, note, result, detail, tone], index) => <li className={`pv-stage pv-stage-${tone}`} key={name}>
        <div className="pv-stage-label"><h3>{index + 1}. {name} <span>{percent}</span></h3><p>{note}</p></div>
        <div className="pv-stage-track" aria-hidden="true"><div className="pv-stage-fill" style={{ width: `${count / 214 * 100}%` }}>{tone === 'completed' && <CheckCircle2 size={13} />}<span>{count}</span></div></div>
        <div className="pv-stage-result"><strong>{count} {result}</strong><span>{detail}</span></div>
      </li>)}</ol>
      <div className="pv-insight"><Zap size={16} aria-hidden="true" /><p><strong>Diagnóstico BI de Aceleração:</strong> cenário ilustrativo — resposta em menos de 5 min e ganho de 23% na conversão da etapa 3 para 4.</p><span>Perda acumulada: <strong>79,9%</strong> · <strong className="pv-teal">Meta atingida: 100,5%</strong></span></div>
    </section>
    <div className="pv-details">
      <section className="pv-panel" aria-labelledby="pv-speed-title"><header className="pv-panel-heading"><div className="pv-panel-title"><Gauge size={18} aria-hidden="true" /><h2 id="pv-speed-title">Velocidade de Retorno & Ciclo Médio</h2></div><span className="pv-badge">−1,2 dias vs mês anterior</span></header><div className="pv-detail-grid"><Metric title="Tempo médio geral" value="3,8" unit="dias" note="Identificação ao retorno" /><Metric title="SLA contato < 2 min" value="64%" unit="respostas" note="Meta: 60%" tone="teal" /><Metric title="Espera confirmação" value="42" unit="min" note="−18 min com IA ativa" /></div></section>
      <section className="pv-panel" aria-labelledby="pv-risk-title"><header className="pv-panel-heading"><div className="pv-panel-title"><ShieldCheck size={18} aria-hidden="true" /><h2 id="pv-risk-title">Receita em Risco & Perda Evitada</h2></div><span className="pv-tag">≈ R$ 39 mil em pipeline ativo</span></header><div className="pv-detail-grid"><Metric title="Perda evitada" value="R$ 12.480" note="43 pacientes retidos" tone="teal" /><Metric title="Em risco iminente" value="R$ 8.920" note="12 pacientes sem retorno há mais de 90 dias" tone="danger" /><Metric title="Potencial ativo" value="R$ 29.680" note="171 elegíveis no funil" tone="teal" /></div></section>
    </div>
    <p className="pv-data-note">Prévia com valores de exemplo. Indicadores, comparativos e diagnósticos ainda não estão conectados aos dados da clínica.</p>
  </div></main>
}

function Metric({ title, value, unit, note, tone = '' }) {
  return <div className={`pv-metric ${tone ? `pv-metric-${tone}` : ''}`}><h3>{title}</h3><p>{value} {unit && <span>{unit}</span>}</p><small>{note}</small></div>
}
