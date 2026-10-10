import { useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight, Download } from 'lucide-react'
import { apiDownload, apiRequest } from '../../lib/apiClient'
import { STATUS_POS_VENDA, dataHora } from './posVendaData'
import './posVenda.css'

function diaLocal(data) {
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`
}
const numero = valor => valor == null ? '—' : Number(valor).toLocaleString('pt-BR', { maximumFractionDigits: 1 })
const dataAtualizacao = valor => valor ? new Date(valor).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : 'Sem atualização registrada'

export default function PosVendaIndicadores() {
  const [inicio, setInicio] = useState(() => { const d = new Date(); d.setDate(1); return diaLocal(d) })
  const [fim, setFim] = useState(() => diaLocal(new Date()))
  const [consulta, setConsulta] = useState(() => ({ inicio, fim }))
  const [dados, setDados] = useState(null)
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(true)
  const [detalhe, setDetalhe] = useState(null)
  const [exportando, setExportando] = useState(false)

  useEffect(() => {
    let ativo = true
    apiRequest(`/api/pos-venda/indicadores?${new URLSearchParams(consulta)}`)
      .then(d => { if (ativo) setDados(d) })
      .catch(e => { if (ativo) setErro(e.message) })
      .finally(() => { if (ativo) setCarregando(false) })
    return () => { ativo = false }
  }, [consulta])

  function atualizar(e) {
    e.preventDefault()
    setDados(null); setDetalhe(null); setErro(''); setCarregando(true); setConsulta({ inicio, fim })
  }

  async function abrirCasos(metrica, titulo, pagina = 0) {
    setDetalhe(atual => ({ ...atual, metrica, titulo, pagina, carregando: true, erro: '' }))
    try {
      const query = new URLSearchParams({ inicio: dados.inicio, fim: dados.fim, metrica, pagina, tamanho: 25 })
      const resposta = await apiRequest(`/api/pos-venda/indicadores/casos?${query}`)
      setDetalhe({ ...resposta, titulo, carregando: false, erro: '' })
    } catch (e) {
      setDetalhe(atual => ({ ...atual, metrica, titulo, pagina, carregando: false, erro: e.message }))
    }
  }

  async function exportar() {
    if (!detalhe?.metrica || !dados) return
    setExportando(true)
    try {
      const query = new URLSearchParams({ inicio: dados.inicio, fim: dados.fim, metrica: detalhe.metrica })
      const blob = await apiDownload(`/api/pos-venda/indicadores/exportar?${query}`)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `pos-venda-${detalhe.metrica}-${dados.inicio}-${dados.fim}.csv`
      link.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setDetalhe(atual => ({ ...atual, erro: e.message }))
    } finally { setExportando(false) }
  }

  const r = dados?.resumo
  const kpisPrincipais = r ? [
    ['faltas', 'Casos de falta', r.faltas, `${numero(r.pacientes_unicos)} pacientes únicos`],
    ['contatados', 'Com tentativa de contato', r.contatados, `${numero(r.contatados_pacientes)} pacientes únicos`],
    ['reagendados', 'Com reagendamento', r.reagendados, 'Ainda não significa comparecimento'],
    ['recuperados', 'Compareceram', r.recuperados, `${numero(r.recuperados_pacientes)} pacientes únicos`],
  ] : []
  const kpisOperacionais = r ? [
    ['recuperados', 'Taxa de recuperação', r.taxa_recuperacao == null ? '—' : `${numero(r.taxa_recuperacao)}%`, 'Casos recuperados ÷ casos de falta'],
    ['contatados', 'Até o primeiro contato', r.horas_primeiro_contato == null ? '—' : `${numero(r.horas_primeiro_contato)} h`, 'Média desde o horário perdido'],
    ['abertos', 'Ainda em acompanhamento', numero(r.abertos), `${numero(r.tentativas)} tentativas registradas`],
    ['pendencias_vencidas', 'Pendências vencidas', numero(r.pendencias_vencidas), `${numero(r.sem_responsavel)} casos sem responsável`],
  ] : []

  return <section className="pv" aria-label="Indicadores de pós-venda" aria-busy={carregando}>
    <div className="pv-intro"><div><h2>O que aconteceu depois da falta?</h2><p>Meça o acompanhamento e abra qualquer total até os casos que o compõem.</p></div></div>
    <form className="pv-filtros" onSubmit={atualizar}>
      <label>Faltas a partir de<input required type="date" value={inicio} max={fim} onChange={e => setInicio(e.target.value)} /></label>
      <label>Até<input required type="date" value={fim} min={inicio} onChange={e => setFim(e.target.value)} /></label>
      <button type="submit" className="retorno-btn-primario" disabled={carregando}>{carregando ? 'Carregando…' : 'Atualizar indicadores'}</button>
    </form>
    {erro && <p className="pv-error" role="alert">Não foi possível carregar os indicadores. {erro} Use “Atualizar indicadores” para tentar novamente.</p>}
    {carregando && <p className="pv-note">Consultando casos e eventos da clínica…</p>}
    {dados && <>
      <div className="pv-metadata">
        <p><strong>Coorte:</strong> faltas de {dados.inicio.split('-').reverse().join('/')} a {dados.fim.split('-').reverse().join('/')}.</p>
        <p><strong>Fuso:</strong> {dados.fuso} · <strong>Última atualização:</strong> {dataAtualizacao(r.ultima_atualizacao)}</p>
      </div>
      <p className="pv-note">Os resultados mostram a situação atual dos casos iniciados no período, mesmo quando o contato ou comparecimento ocorreu depois. Correções de falta são excluídas. Receita não é estimada sem comparecimento e evento financeiro verificável.</p>
      <div className="pv-kpis">{kpisPrincipais.map(([key, label, value, sub]) => <Kpi key={label} label={label} value={numero(value)} sub={sub} onClick={() => abrirCasos(key, label)} />)}</div>
      {Number(r.faltas) === 0 && <p className="pv-empty">Nenhuma falta registrada neste período. Isso representa ausência de casos, não indisponibilidade da API.</p>}
      <div className="pv-kpis">{kpisOperacionais.map(([key, label, value, sub]) => <Kpi key={label} label={label} value={value} sub={sub} onClick={() => abrirCasos(key, label)} />)}</div>
      <p className="pv-note">Aguardando comparecimento: {numero(r.aguardando_comparecimento)} · Nova falta: {numero(r.novas_faltas)} · Não deseja reagendar: {numero(r.nao_deseja)} · Não contatar: {numero(r.nao_contatar)}. Reagendar não conta como recuperação.</p>

      {detalhe && <section className="retorno-painel pv-drilldown" aria-live="polite">
        <div className="retorno-painel-head"><div><strong>{detalhe.titulo}</strong><span>{detalhe.total == null ? 'Carregando casos…' : `${numero(detalhe.total)} casos no total`}</span></div><button type="button" className="retorno-btn-secundario" onClick={exportar} disabled={exportando || detalhe.carregando}><Download size={15} />{exportando ? 'Gerando…' : 'Exportar CSV'}</button></div>
        {detalhe.erro && <p className="pv-error" role="alert">{detalhe.erro}</p>}
        {detalhe.carregando ? <p className="pv-note">Carregando composição do indicador…</p> : <>
          <div className="retorno-tabela-scroll"><table className="retorno-tabela"><thead><tr><th>Caso</th><th>Paciente</th><th>Falta</th><th>Situação</th><th>Responsável</th><th>Tentativas</th><th>Próxima ação</th></tr></thead><tbody>
            {detalhe.itens?.map(c => <tr key={c.id}><td>#{c.id}</td><td><strong>{c.paciente}</strong><div className="retorno-sub">Paciente #{c.pacienteId} · {c.telefone || 'sem telefone'}</div></td><td>{dataHora(c.dataFalta)}</td><td><span className={`pv-status ${c.status}`}>{STATUS_POS_VENDA[c.status] || c.status}</span></td><td>{c.responsavel}</td><td>{numero(c.tentativas)}</td><td>{c.proximaAcao ? dataHora(c.proximaAcao) : 'A definir'}</td></tr>)}
            {!detalhe.itens?.length && <tr><td colSpan="7">Nenhum caso compõe este indicador.</td></tr>}
          </tbody></table></div>
          <div className="pv-paginacao"><button type="button" className="retorno-btn-secundario" disabled={detalhe.pagina === 0} onClick={() => abrirCasos(detalhe.metrica, detalhe.titulo, detalhe.pagina - 1)}><ChevronLeft size={15} />Anterior</button><span>Página {detalhe.pagina + 1} de {Math.max(1, Number(detalhe.totalPaginas))}</span><button type="button" className="retorno-btn-secundario" disabled={detalhe.pagina + 1 >= detalhe.totalPaginas} onClick={() => abrirCasos(detalhe.metrica, detalhe.titulo, detalhe.pagina + 1)}>Próxima<ChevronRight size={15} /></button></div>
        </>}
      </section>}

      <div className="pv-colunas">
        <section className="retorno-painel"><div className="retorno-painel-head"><strong>Motivos de falta</strong><span>10 mais registrados</span></div>
          <div className="retorno-tabela-scroll"><table className="retorno-tabela"><thead><tr><th>Motivo informado</th><th>Casos</th></tr></thead><tbody>{dados.motivos.map(m => <tr key={m.motivo}><td>{m.motivo}</td><td>{numero(m.quantidade)}</td></tr>)}</tbody></table></div>
        </section>
        <section className="retorno-painel"><div className="retorno-painel-head"><strong>Acompanhamento por responsável atual</strong></div>
          <div className="retorno-tabela-scroll"><table className="retorno-tabela"><thead><tr><th>Responsável</th><th>Casos</th><th>Contato</th><th>Abertos</th><th>Vencidos</th><th>Recuperados</th></tr></thead><tbody>{dados.equipe.map(u => <tr key={u.id_responsavel ?? 'sem'}><td>{u.responsavel}</td><td>{numero(u.casos)}</td><td>{numero(u.contatados)}</td><td>{numero(u.abertos)}</td><td>{numero(u.vencidos)}</td><td>{numero(u.recuperados)}</td></tr>)}</tbody></table></div>
        </section>
      </div>
      <p className="pv-note">A distribuição usa o responsável atual pelo caso. O autor histórico de cada contato permanece no histórico do caso.</p>
      <details className="pv-definicoes"><summary>Como cada indicador é calculado</summary><div>{dados.definicoes.map(item => <article key={item.chave}><strong>{item.nome}</strong><p>{item.formula}</p><small>Fonte: {item.fonte}</small></article>)}</div></details>
    </>}
  </section>
}

function Kpi({ label, value, sub, onClick }) {
  return <button type="button" className="retorno-kpi pv-kpi-link" onClick={onClick} title="Abrir casos que compõem este total"><span className="retorno-kpi-lab">{label}</span><span className="retorno-kpi-val">{value}</span><span className="retorno-kpi-sub">{sub}</span><span className="pv-kpi-open">Ver casos</span></button>
}
