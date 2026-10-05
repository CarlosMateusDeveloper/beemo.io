import { useEffect, useState } from 'react'
import { apiRequest } from '../../lib/apiClient'
import './posVenda.css'

function diaLocal(data) {
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`
}
const numero = valor => valor == null ? '—' : Number(valor).toLocaleString('pt-BR', { maximumFractionDigits: 1 })

export default function PosVendaIndicadores() {
  const [inicio, setInicio] = useState(() => { const d = new Date(); d.setDate(1); return diaLocal(d) })
  const [fim, setFim] = useState(() => diaLocal(new Date()))
  const [consulta, setConsulta] = useState(() => ({ inicio, fim }))
  const [dados, setDados] = useState(null)
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(true)

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
    setDados(null); setErro(''); setCarregando(true); setConsulta({ inicio, fim })
  }
  const r = dados?.resumo
  return <section className="pv" aria-label="Indicadores de pós-venda" aria-busy={carregando}>
    <div className="pv-intro"><div><h2>O que aconteceu depois da falta?</h2><p>Meça o acompanhamento e o comparecimento dos casos de um período.</p></div></div>
    <form className="pv-filtros" onSubmit={atualizar}>
      <label>Faltas a partir de<input required type="date" value={inicio} max={fim} onChange={e => setInicio(e.target.value)} /></label>
      <label>Até<input required type="date" value={fim} min={inicio} onChange={e => setFim(e.target.value)} /></label>
      <button type="submit" className="retorno-btn-primario" disabled={carregando}>{carregando ? 'Carregando…' : 'Atualizar indicadores'}</button>
    </form>
    {erro && <p className="pv-error" role="alert">Não foi possível carregar os indicadores. {erro} Use “Atualizar indicadores” para tentar novamente.</p>}
    {dados && <>
      <p className="pv-note">Faltas de {dados.inicio.split('-').reverse().join('/')} a {dados.fim.split('-').reverse().join('/')}. Os números mostram o resultado atual desses casos, incluindo ações posteriores ao período. Registros corrigidos são excluídos. Um paciente pode ter mais de um caso.</p>
      <div className="pv-kpis">{[
        ['faltas', 'Casos de falta', 'Casos iniciados no período'],
        ['contatados', 'Com tentativa de contato', 'Ao menos um contato registrado'],
        ['reagendados', 'Com reagendamento', 'Ao menos uma consulta vinculada'],
        ['recuperados', 'Compareceram', 'Atendimento realizado na agenda'],
      ].map(([key, label, sub]) => <div className="retorno-kpi" key={key}><div className="retorno-kpi-lab">{label}</div><div className="retorno-kpi-val">{numero(r[key])}</div><div className="retorno-kpi-sub">{sub}</div></div>)}</div>
      {Number(r.faltas) === 0 && <p className="pv-note">Nenhuma falta registrada neste período.</p>}
      <div className="pv-kpis">{[
        [r.taxa_recuperacao == null ? '—' : `${numero(r.taxa_recuperacao)}%`, 'Taxa de recuperação', 'Compareceram ÷ casos de falta'],
        [r.horas_primeiro_contato == null ? '—' : `${numero(r.horas_primeiro_contato)} h`, 'Até o primeiro contato', 'Média desde o horário perdido; só casos com contato'],
        [numero(r.tentativas), 'Tentativas registradas', 'Inclui contatos sem resposta'],
        [numero(r.abertos), 'Ainda em acompanhamento', 'Inclui quem aguarda a nova consulta'],
      ].map(([valor, label, sub]) => <div className="retorno-kpi" key={label}><div className="retorno-kpi-lab">{label}</div><div className="retorno-kpi-val">{valor}</div><div className="retorno-kpi-sub">{sub}</div></div>)}</div>
      <p className="pv-note">Aguardando comparecimento: {numero(r.aguardando_comparecimento)} · Nova falta: {numero(r.novas_faltas)} · Não deseja reagendar: {numero(r.nao_deseja)} · Não contatar: {numero(r.nao_contatar)}. Reagendar não conta como recuperação; um caso pode ser reagendado sem contato registrado.</p>
      <div className="pv-colunas">
        <section className="retorno-painel"><div className="retorno-painel-head"><strong>Motivos de falta</strong><span>10 mais registrados</span></div>
          <div className="retorno-tabela-scroll"><table className="retorno-tabela"><thead><tr><th>Motivo informado</th><th>Casos</th></tr></thead><tbody>{dados.motivos.map(m => <tr key={m.motivo}><td>{m.motivo}</td><td>{numero(m.quantidade)}</td></tr>)}</tbody></table></div>
        </section>
        <section className="retorno-painel"><div className="retorno-painel-head"><strong>Acompanhamento por responsável</strong></div>
          <div className="retorno-tabela-scroll"><table className="retorno-tabela"><thead><tr><th>Responsável atual</th><th>Casos</th><th>Com contato</th><th>Em aberto</th><th>Recuperados</th></tr></thead><tbody>{dados.equipe.map(u => <tr key={u.id_responsavel ?? 'sem'}><td>{u.responsavel}</td><td>{numero(u.casos)}</td><td>{numero(u.contatados)}</td><td>{numero(u.abertos)}</td><td>{numero(u.recuperados)}</td></tr>)}</tbody></table></div>
        </section>
      </div>
      <p className="pv-note">A distribuição usa o responsável atual pelo caso, não o autor de cada contato. Motivos iguais são agrupados pelo texto informado.</p>
    </>}
  </section>
}
