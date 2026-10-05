import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowLeft, RefreshCw, Phone, CheckCircle, CalendarDays } from 'lucide-react'
import { apiRequest } from '../../lib/apiClient'
import { STATUS_POS_VENDA as STATUS, ENCERRADOS, dataHora, inputData } from './posVendaData'
import './posVenda.css'

const api = '/api/pos-venda'

export default function PosVenda() {
  const [status, setStatus] = useState('abertos')
  const [fila, setFila] = useState('todos')
  const [busca, setBusca] = useState('')
  const [responsavel, setResponsavel] = useState('')
  const [pagina, setPagina] = useState(0)
  const [dados, setDados] = useState(null)
  const [usuarios, setUsuarios] = useState([])
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(true)
  const [sinal, setSinal] = useState(0)
  const [selecionado, setSelecionado] = useState(null)

  useEffect(() => {
    let ativo = true
    apiRequest(`${api}/responsaveis`).then(u => { if (ativo) setUsuarios(u) })
      .catch(e => { if (ativo) setErro(e.message) })
    return () => { ativo = false }
  }, [])

  useEffect(() => {
    let ativo = true
    const timeout = setTimeout(() => {
      setCarregando(true)
      setErro('')
      const params = new URLSearchParams({ status, busca, pagina, fila })
      if (responsavel) params.set('responsavel', responsavel)
      apiRequest(`${api}?${params}`).then(d => { if (ativo) setDados(d) })
        .catch(e => { if (ativo) { setErro(e.message); setDados(null) } })
        .finally(() => { if (ativo) setCarregando(false) })
    }, 200)
    return () => { ativo = false; clearTimeout(timeout) }
  }, [status, busca, responsavel, pagina, fila, sinal])

  useEffect(() => {
    const timer = setInterval(() => { if (!document.hidden) setSinal(n => n + 1) }, 30000)
    return () => clearInterval(timer)
  }, [])

  const resumo = dados?.resumo
  if (selecionado !== null) return <Detalhe key={selecionado} id={selecionado} usuarios={usuarios}
    onVoltar={() => { setSelecionado(null); setSinal(n => n + 1) }} />

  return <section className="pv" aria-label="Pós-venda de faltas">
    <div className="pv-intro">
      <div><h2>Da falta ao comparecimento</h2><p>Acompanhe cada paciente até a realização do atendimento.</p></div>
      <button className="retorno-btn-ghost" disabled={carregando} onClick={() => setSinal(n => n + 1)}><RefreshCw size={15} /> Atualizar</button>
    </div>
    <div className="pv-kpis">
      {[['a_contatar', 'Próximos contatos', 'Sem prazo ou com ação prevista até agora'], ['reagendados', 'Reagendados', 'Ainda aguardando comparecimento'],
        ['recuperados', 'Recuperados', 'Consultas vinculadas realizadas'], ['novas_faltas', 'Novas faltas', 'Precisam de novo acompanhamento']].map(([key, label, sub]) =>
        <div className="retorno-kpi" key={key}><div className="retorno-kpi-lab">{label}</div>
          <div className={`retorno-kpi-val${key === 'recuperados' ? ' ok' : ''}`}>{resumo?.[key] ?? '—'}</div><div className="retorno-kpi-sub">{sub}</div></div>)}
    </div>
    <p className="pv-note">Indicadores de todo o histórico. A fila inclui apenas faltas registradas na agenda. Atualização a cada 30 segundos.</p>
    <nav className="pv-atalhos" aria-label="Prioridades do pós-venda">
      {[['todos', 'Toda a fila'], ['hoje', 'Contatos para agora'], ['sem_responsavel', 'Sem responsável'], ['conferir_agenda', 'Conferir comparecimento']].map(([valor, label]) =>
        <button key={valor} className="retorno-btn-ghost" aria-pressed={fila === valor} onClick={() => { setFila(valor); setStatus('abertos'); setResponsavel(''); setPagina(0) }}>{label}</button>)}
    </nav>
    {fila === 'conferir_agenda' && <p className="pv-note">O horário reagendado já passou. Confira e registre o resultado na agenda para atualizar o acompanhamento.</p>}
    <div className="pv-filtros">
      <label>Paciente ou telefone<input value={busca} onChange={e => { setBusca(e.target.value); setPagina(0) }} placeholder="Buscar paciente" /></label>
      <label>Situação<select value={status} onChange={e => { setStatus(e.target.value); setPagina(0) }}><option value="abertos">Casos em acompanhamento</option>
        <option value="todos">Todos os casos</option>{Object.entries(STATUS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
      <label>Responsável<select value={responsavel} onChange={e => { setResponsavel(e.target.value); setPagina(0) }}><option value="">Toda a equipe</option>
        {usuarios.map(u => <option value={u.id} key={u.id}>{u.nome}</option>)}</select></label>
    </div>
    {erro && <p role="alert" className="pv-error">Não foi possível carregar o pós-venda. {erro} <button onClick={() => setSinal(n => n + 1)}>Tentar novamente</button></p>}
    <div className="retorno-painel" aria-busy={carregando}>
      <div className="retorno-painel-head"><strong>Fila de acompanhamento</strong><span>{carregando ? 'Carregando…' : `${dados?.total ?? 0} casos`}</span></div>
      {!carregando && dados?.itens.length === 0 && <div className="retorno-vazio"><CheckCircle size={28} /><strong>Nenhum caso neste filtro</strong><span>Ao marcar uma consulta como falta na agenda, o acompanhamento aparece aqui.</span></div>}
      {dados?.itens.length > 0 && <div className="retorno-tabela-scroll"><table className="retorno-tabela"><thead><tr>
        <th>Paciente</th><th>Atendimento perdido</th><th>Situação</th><th>Responsável / próxima ação</th><th><span className="pv-sr">Ação</span></th>
      </tr></thead><tbody>{dados.itens.map(c => <tr key={c.id}>
        <td><strong>{c.paciente}</strong><div className="retorno-sub">{c.telefone || 'Telefone não cadastrado'}</div></td>
        <td>{dataHora(c.data_falta)}<div className="retorno-sub">{c.tipo} · {c.especialidade}</div><div className="retorno-sub">{c.profissional}</div></td>
        <td><span className={`pv-status ${c.status}`}>{STATUS[c.status]}</span>{c.nao_contatar && c.status !== 'nao_contatar' && <div className="retorno-sub">Paciente pediu para não contatar</div>}<div className="retorno-sub">{c.tentativas} contato(s) registrado(s)</div></td>
        <td>{c.responsavel || 'Sem responsável'}<div className="retorno-sub">{c.status === 'reagendado' ? `Consulta: ${dataHora(`${c.data_reagendada}T${c.hora_reagendada}`)}` : ENCERRADOS.includes(c.status) ? 'Encerrado' : dataHora(c.proxima_acao)}</div></td>
        <td><button className="retorno-btn-ghost" disabled={carregando} onClick={() => setSelecionado(c.id)} aria-label={`Acompanhar ${c.paciente}`}>Acompanhar</button></td>
      </tr>)}</tbody></table></div>}
    </div>
    <div className="pv-paginacao"><button className="retorno-btn-ghost" disabled={pagina === 0 || carregando} onClick={() => setPagina(p => p - 1)}>Anterior</button>
      <span>Página {pagina + 1}</span><button className="retorno-btn-ghost" disabled={!dados || (pagina + 1) * 50 >= dados.total || carregando} onClick={() => setPagina(p => p + 1)}>Próxima</button></div>
  </section>
}

function Detalhe({ id, usuarios, onVoltar }) {
  const [dados, setDados] = useState(null)
  const [erro, setErro] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [sinal, setSinal] = useState(0)
  const [aviso, setAviso] = useState('')
  useEffect(() => {
    let ativo = true
    apiRequest(`${api}/${id}`).then(d => { if (ativo) { setDados(d); setErro('') } })
      .catch(e => { if (ativo) setErro(e.message) })
    return () => { ativo = false }
  }, [id, sinal])

  async function salvar(payload) {
    setSalvando(true); setErro(''); setAviso('')
    try {
      await apiRequest(`${api}/${id}/acoes`, { method: 'POST', body: JSON.stringify({ ...payload, versao: dados.caso.versao }) })
      setAviso('Acompanhamento salvo.'); setSinal(n => n + 1)
    } catch (e) { setErro(e.message) }
    finally { setSalvando(false) }
  }

  const c = dados?.caso
  return <section className="pv">
    <button className="retorno-voltar" onClick={onVoltar} disabled={salvando}><ArrowLeft size={16} /> Voltar à fila</button>
    {erro && <p role="alert" className="pv-error">{erro} <button disabled={salvando} onClick={() => { setDados(null); setSinal(n => n + 1) }}>Recarregar detalhes</button></p>}
    {aviso && <p role="status" className="pv-success">{aviso}</p>}
    {!c ? <p>Carregando acompanhamento…</p> : <>
      <div className="pv-intro"><div><h2>{c.paciente}</h2><p>{c.telefone || 'Telefone não cadastrado'} · Caso #{c.id}</p></div><span className={`pv-status ${c.status}`}>{STATUS[c.status]}</span></div>
      <div className="pv-contexto"><CalendarDays size={20} /><div><strong>{c.tipo} · {c.especialidade}</strong><p>Falta em {dataHora(c.data_falta)} · {c.profissional}</p>
        {c.id_consulta_reagendada && <p>Consulta vinculada #{c.id_consulta_reagendada}: {dataHora(`${c.data_reagendada}T${c.hora_reagendada}`)} · {c.status_consulta}</p>}</div></div>
      <div className="pv-colunas">
        {!ENCERRADOS.includes(c.status) && <Formulario key={`${id}-${c.versao}`} caso={c} usuarios={usuarios} consultas={dados.consultas} onSalvar={salvar} salvando={salvando} />}
        <section className="retorno-painel pv-historico"><div className="retorno-painel-head"><strong>Histórico do acompanhamento</strong><button className="retorno-btn-ghost" disabled={salvando} onClick={() => { setDados(null); setSinal(n => n + 1) }}>Atualizar</button></div>
          <ol>{dados.historico.map(h => <li key={h.id}><time>{dataHora(h.criado_em)}</time><p>{h.descricao}</p><small>{h.autor || (h.tipo === 'agenda' || h.tipo === 'falta' ? 'Agenda' : 'Operação sem usuário identificado')}{h.canal ? ` · ${h.canal}` : ''}</small></li>)}</ol>
        </section>
      </div>
    </>}
  </section>
}

function Formulario({ caso, usuarios, consultas, onSalvar, salvando }) {
  const [acao, setAcao] = useState(caso.nao_contatar ? 'nao_contatar' : 'contato')
  const [form, setForm] = useState({ idResponsavel: caso.id_responsavel || '', proximaAcao: inputData(caso.proxima_acao),
    motivoFalta: caso.motivo_falta || '', recorrente: caso.recorrente, canal: 'telefone', resultado: 'em_contato', observacao: '', idConsulta: '' })
  const enviando = useRef(false)
  const contato = acao === 'contato'
  const organizar = contato || acao === 'organizar'
  function campo(nome, valor) { setForm(f => ({ ...f, [nome]: valor })) }
  async function submit(e) {
    e.preventDefault()
    if (enviando.current) return
    enviando.current = true
    try { await onSalvar({ ...form, acao, idResponsavel: form.idResponsavel ? Number(form.idResponsavel) : null,
      idConsulta: form.idConsulta ? Number(form.idConsulta) : null,
      proximaAcao: form.proximaAcao ? new Date(form.proximaAcao).toISOString() : null }) }
    finally { enviando.current = false }
  }
  return <form className="retorno-painel pv-form" onSubmit={submit}>
    <h3><Phone size={18} /> Próxima ação</h3>
    {caso.nao_contatar && <p className="pv-note">Paciente pediu para não receber contatos. Encerre este acompanhamento respeitando essa preferência.</p>}
    <label>Ação<select value={acao} onChange={e => setAcao(e.target.value)} disabled={salvando}>
      {!caso.nao_contatar && <><option value="contato">Registrar contato realizado</option><option value="organizar">Definir responsável e próxima ação</option>
        {caso.status !== 'reagendado' && <option value="reagendar">Vincular novo agendamento</option>}<option value="nao_deseja">Encerrar: não deseja reagendar</option></>}
      <option value="nao_contatar">Encerrar: pediu para não contatar</option>
    </select></label>
    {(organizar || acao === 'reagendar') && <label>Responsável administrativo<select required value={form.idResponsavel} onChange={e => campo('idResponsavel', e.target.value)} disabled={salvando}>
      <option value="">Selecione</option>{usuarios.map(u => <option key={u.id} value={u.id}>{u.nome}</option>)}</select></label>}
    {contato && <><div className="pv-form-linha"><label>Canal<select value={form.canal} onChange={e => campo('canal', e.target.value)}><option value="telefone">Telefone</option><option value="whatsapp">WhatsApp</option><option value="presencial">Presencial</option></select></label>
      <label>Resultado<select value={form.resultado} onChange={e => campo('resultado', e.target.value)}><option value="em_contato">Conversou / em acompanhamento</option><option value="sem_resposta">Sem resposta</option></select></label></div>
      <p className="pv-note">Registre aqui o contato feito pela equipe. Salvar não envia mensagem ao paciente.</p></>}
    {organizar && <><label>Motivo da falta<input maxLength={500} value={form.motivoFalta} onChange={e => campo('motivoFalta', e.target.value)} placeholder="Ex.: esqueceu o horário" /></label>
      <label>Próxima ação<input type="datetime-local" required value={form.proximaAcao} onChange={e => campo('proximaAcao', e.target.value)} /></label>
      <label className="pv-checkbox"><input type="checkbox" checked={form.recorrente} onChange={e => campo('recorrente', e.target.checked)} /> Atendimento recorrente</label>
      {form.recorrente && <p className="pv-note">Confira o dia fixo com a equipe. Este fluxo não libera nem substitui a vaga automaticamente.</p>}</>}
    {acao === 'reagendar' && <><label>Nova consulta<select required value={form.idConsulta} onChange={e => campo('idConsulta', e.target.value)}><option value="">Selecione um agendamento</option>
      {consultas.map(c => <option key={c.id} value={c.id}>#{c.id} · {dataHora(`${c.data}T${c.hora}`)} · {c.profissional}</option>)}</select></label>
      <p className="pv-note">Consultas futuras deste paciente, do mesmo tipo e especialidade. Crie a nova consulta na <Link to="/agenda" target="_blank" rel="noopener noreferrer">Agenda</Link> e atualize os detalhes para selecioná-la.</p>
      <p className="pv-note">O caso será recuperado apenas quando a agenda registrar o atendimento como realizado.</p></>}
    {(contato || acao === 'nao_deseja' || acao === 'nao_contatar') && <label>{contato ? 'Registro do contato' : 'Motivo do encerramento'}<textarea required rows={3} maxLength={1500} value={form.observacao} onChange={e => campo('observacao', e.target.value)} /></label>}
    <button className="retorno-btn-primario" type="submit" disabled={salvando}>{salvando ? 'Salvando…' : 'Salvar acompanhamento'}</button>
  </form>
}
