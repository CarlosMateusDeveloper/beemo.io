import { useEffect, useMemo, useRef, useState } from 'react'
import { Search, AlertTriangle, UserPlus } from 'lucide-react'
import { ordenarConversas } from './whatsappData'
import { fetchConversas, fetchMensagens, fetchContexto, assumirConversa, devolverConversa, enviarMensagem } from './api'

import { useAuth } from '../../auth/AuthContext'

const FILTROS = [
  { id: 'aguardando', label: 'Aguardando atendente' },
  { id: 'com_agente', label: 'Em atendimento' },
  { id: 'bot', label: 'Com assistente' },
  { id: 'todas', label: 'Todas' },
]

function iniciaisDe(nome) {
  return nome.split(' ').map((w) => w[0]).slice(0, 2).join('').toUpperCase()
}

function destacarTexto(texto, destaque) {
  if (!destaque) return texto
  const literal = destaque.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const partes = texto.split(new RegExp(`(${literal})`, 'i'))
  return partes.map((p, i) => (p.toLowerCase() === destaque.toLowerCase()
    ? <mark key={i} className="whatsapp-msg-destaque">{p}</mark>
    : p))
}

function Badge({ conversa }) {
  if (conversa.estado === 'aguardando') {
    return <span className="whatsapp-badge warn">aguardando</span>
  }
  if (conversa.estado === 'com_agente') {
    return <span className="whatsapp-badge info">com {conversa.agente}</span>
  }
  return <span className="whatsapp-badge neutro">bot</span>
}

function SkeletonLista() {
  return (
    <div className="whatsapp-lista">
      <div className="whatsapp-lista-scroll">
        {[0, 1, 2].map((i) => <div key={i} className="dashboard-card-skel" style={{ height: 64, margin: '8px 12px' }} />)}
      </div>
    </div>
  )
}

function ListaConversas({ conversas, filtro, onFiltro, busca, onBusca, selecionadoId, onSelecionar, contadorAguardando }) {
  const filtradas = useMemo(() => {
    const porFiltro = conversas.filter((c) => {
      if (filtro === 'aguardando') return c.estado === 'aguardando'
      if (filtro === 'bot') return c.estado === 'bot'
      if (filtro === 'com_agente') return c.estado === 'com_agente'
      return true
    })
    const termo = busca.trim().toLowerCase()
    const porBusca = termo
      ? porFiltro.filter((c) => (c.paciente || '').toLowerCase().includes(termo) || (c.telefone || '').includes(termo))
      : porFiltro
    return ordenarConversas(porBusca)
  }, [conversas, filtro, busca])

  return (
    <div className="whatsapp-lista">
      <div className="whatsapp-lista-busca">
        <Search size={14} strokeWidth={2} />
        <input
          type="text" placeholder="Buscar paciente ou telefone"
          value={busca} onChange={(e) => onBusca(e.target.value)}
        />
      </div>

      <div className="whatsapp-lista-filtros">
        {FILTROS.map((f) => (
          <button
            key={f.id} type="button"
            className={`whatsapp-chip${filtro === f.id ? ' active' : ''}`}
            onClick={() => onFiltro(f.id)}
          >
            {f.label}{f.id === 'aguardando' && ` · ${contadorAguardando}`}
          </button>
        ))}
      </div>

      <div className="whatsapp-lista-scroll">
        {filtradas.length === 0 ? (
          <div className="whatsapp-lista-vazia">Nenhuma conversa aqui</div>
        ) : filtradas.map((c) => (
          <button
            key={c.id} type="button"
            className={`whatsapp-conversa-item${selecionadoId === c.id ? ' active' : ''}`}
            onClick={() => onSelecionar(c.id)}
          >
            <span className="whatsapp-conversa-linha1">
              <span className="whatsapp-conversa-nome">{c.paciente || c.telefone}</span>
              <span className="whatsapp-conversa-hora">{c.horario}</span>
            </span>
            <span className="whatsapp-conversa-ultima">{c.ultimaMensagem}</span>
            <span className="whatsapp-conversa-linha3">
              <Badge conversa={c} />
              {c.estado === 'aguardando' && <span className="whatsapp-conversa-nota">{c.nota || `há ${c.esperaMin} min`}</span>}
              {c.estado === 'bot' && c.nota && <span className="whatsapp-conversa-nota">{c.nota}</span>}
            </span>
          </button>
        ))}
      </div>
    </div>
  )
}

function ConversaThread({ conversa, mensagens, resposta, onRespostaChange, onEnviar, onAssumir, onDevolver, erroEnvio, ocupado, carregandoMensagens, erroMensagens, mensagensRef }) {
  if (!conversa) {
    return <div className="whatsapp-thread whatsapp-thread-vazia">Selecione uma conversa</div>
  }
  const podeResponder = conversa.estado === 'com_agente'

  return (
    <div className="whatsapp-thread">
      <div className="whatsapp-thread-head">
        <span className="whatsapp-thread-avatar">{iniciaisDe(conversa.paciente || conversa.telefone)}</span>
        <span className="whatsapp-thread-quem">
          <span className="whatsapp-thread-nome">{conversa.paciente || 'Paciente não identificado'}</span>
          <span className="whatsapp-thread-tel">{conversa.telefone}</span>
        </span>
        <span className="whatsapp-thread-spacer" />
        {conversa.estado === 'aguardando' && (
          <span className="whatsapp-espera-pill">aguardando atendente · {conversa.nota || `há ${conversa.esperaMin} min`}</span>
        )}
        {conversa.estado === 'com_agente' && <span className="whatsapp-espera-pill info">com {conversa.agente}</span>}
      </div>

      <div className="whatsapp-thread-msgs" ref={mensagensRef}>
        {carregandoMensagens && <div className="whatsapp-lista-vazia">Carregando mensagens…</div>}
        {erroMensagens && <div role="alert" className="dashboard-erro">{erroMensagens}</div>}
        {mensagens.map((m) => {
          if (m.tipo === 'escalonamento') {
            return (
              <div key={m.id} className="whatsapp-escalonamento">
                <AlertTriangle size={14} strokeWidth={2} />
                <span className="whatsapp-escalonamento-txt">Encaminhado para atendente · regra {m.motivo} · {m.horario}</span>
                <span className="whatsapp-escalonamento-pausado">assistente pausado</span>
              </div>
            )
          }
          return (
            <div key={m.id} className={`whatsapp-bubble ${m.remetente}`}>
              {m.remetente !== 'paciente' && (
                <span className={`whatsapp-bubble-label ${m.remetente}`}>{m.remetente === 'bot' ? 'assistente' : `${m.agente || 'Recepção'} · atendente`}</span>
              )}
              <span className="whatsapp-bubble-txt">
                {(m.texto || '').split('\n').map((linha, i) => (
                  <span key={i}>{i > 0 && <br />}{destacarTexto(linha, m.destaque)}</span>
                ))}
              </span>
              <span className="whatsapp-bubble-hora">{m.horario}</span>
            </div>
          )
        })}
      </div>

      <div className="whatsapp-composer">
        <textarea
          placeholder={podeResponder ? `Escreva uma resposta para ${conversa.paciente || 'o paciente'}…` : 'Assuma a conversa para responder'}
          value={resposta} onChange={(e) => onRespostaChange(e.target.value)}
          disabled={!podeResponder || ocupado}
        />
        {/* O envio passa pela API oficial do WhatsApp e pode ser recusado
            (janela de 24h, credencial vencida). O atendente precisa saber
            que a mensagem NÃO chegou — o texto fica no campo pra reenviar. */}
        {erroEnvio && <div className="dashboard-erro">Não foi possível concluir: {erroEnvio}</div>}
        <div className="whatsapp-composer-acoes">
          {conversa.estado !== 'com_agente' ? (
            <button type="button" className="whatsapp-btn-primario" onClick={onAssumir} disabled={ocupado}>Assumir conversa</button>
          ) : (
            <>
              <button type="button" className="whatsapp-btn-primario" onClick={onEnviar} disabled={!resposta.trim() || ocupado}>{ocupado ? 'Aguarde…' : 'Enviar'}</button>
              <button type="button" className="whatsapp-btn-secundario" onClick={onDevolver} disabled={ocupado}>Devolver ao assistente</button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}

function PainelContexto({ conversa, contexto, contextoCarregado }) {
  if (!conversa) return <div className="whatsapp-contexto" />

  if (contextoCarregado && !contexto) {
    return (
      <div className="whatsapp-contexto">
        <div className="whatsapp-contexto-card">
          <span className="whatsapp-contexto-titulo">paciente</span>
          <div className="whatsapp-contexto-vazio">
            <UserPlus size={20} strokeWidth={1.6} />
            <span>Telefone não cadastrado</span>
          </div>
        </div>
      </div>
    )
  }

  if (!contexto) return <div className="whatsapp-contexto" />

  const { paciente, proximaConsulta, ultimasVisitas, pendencias, assistente } = contexto

  return (
    <div className="whatsapp-contexto">
      <div className="whatsapp-contexto-card">
        <span className="whatsapp-contexto-titulo">paciente</span>
        <span className="whatsapp-contexto-nome">{paciente.nome}</span>
        <span className="whatsapp-contexto-meta">
          {paciente.idade} anos · {paciente.convenio}
          {paciente.clienteDesde && ` · desde ${paciente.clienteDesde}`}
        </span>
      </div>

      <div className="whatsapp-contexto-card">
        <span className="whatsapp-contexto-titulo">próxima consulta</span>
        {proximaConsulta ? (
          <>
            <span className="whatsapp-contexto-linha">{proximaConsulta.data} · {proximaConsulta.hora}</span>
            <span className="whatsapp-contexto-meta">{proximaConsulta.especialidade} · {proximaConsulta.profissional}</span>
            {!proximaConsulta.confirmacaoEnviada && (
              <span className="whatsapp-contexto-alerta">confirmação ainda não enviada</span>
            )}
          </>
        ) : <span className="whatsapp-contexto-meta">Sem consulta futura agendada</span>}
      </div>

      {ultimasVisitas.length > 0 && (
        <div className="whatsapp-contexto-card">
          <span className="whatsapp-contexto-titulo">últimas visitas</span>
          {ultimasVisitas.map((v, i) => (
            <span key={i} className="whatsapp-contexto-par">
              <span>{v.especialidade} · {v.profissional}</span>
              <span className="whatsapp-contexto-data">{v.data}</span>
            </span>
          ))}
        </div>
      )}

      {pendencias.length > 0 && (
        <div className="whatsapp-contexto-card">
          <span className="whatsapp-contexto-titulo">pendências</span>
          {pendencias.map((p, i) => (
            <span key={i} className={`whatsapp-contexto-pendencia${p.critico ? ' critico' : ''}`}>
              <span className="whatsapp-contexto-dot" />
              <span className="whatsapp-contexto-pendencia-label">{p.label}</span>
              <span className="whatsapp-contexto-pendencia-valor">{p.valor || p.nota}</span>
            </span>
          ))}
        </div>
      )}

      {assistente && (
        <div className="whatsapp-contexto-card destaque">
          <span className="whatsapp-contexto-titulo destaque">o que o assistente estava fazendo</span>
          <span className="whatsapp-contexto-linha">
            {assistente.capacidade}
            {assistente.totalPassos > 1 && <span className="whatsapp-contexto-passo">passo {assistente.passoAtual} de {assistente.totalPassos}</span>}
          </span>
          <span className="whatsapp-contexto-meta">Parou em <b>{assistente.parouEm}</b></span>
          {assistente.perguntas?.length > 0 && (
            <>
              <span className="whatsapp-contexto-divisor" />
              <span className="whatsapp-contexto-titulo">já perguntado</span>
              {assistente.perguntas.map((q, i) => (
                <span key={i} className="whatsapp-contexto-par">
                  <span>{q.label}</span>
                  <span className={q.pendente ? 'whatsapp-contexto-pendente' : ''}>{q.valor}</span>
                </span>
              ))}
            </>
          )}
        </div>
      )}
    </div>
  )
}

function Atendimento({ conversa, onAtualizar }) {
  const { usuario } = useAuth()
  const [mensagens, setMensagens] = useState([])
  const [contexto, setContexto] = useState(null)
  const [contextoCarregado, setContextoCarregado] = useState(false)
  const [erroContexto, setErroContexto] = useState(null)
  const [carregandoMensagens, setCarregandoMensagens] = useState(true)
  const [erroMensagens, setErroMensagens] = useState(null)
  const [resposta, setResposta] = useState('')
  const [erroEnvio, setErroEnvio] = useState(null)
  const [ocupado, setOcupado] = useState(false)
  const ativo = useRef(false)
  const emAcao = useRef(false)
  const versao = useRef(0)
  const mensagensRef = useRef(null)
  const pertoDoFim = useRef(true)
  const id = conversa.id

  useEffect(() => {
    ativo.current = true
    return () => { ativo.current = false }
  }, [])

  useEffect(() => {
    let cancelado = false
    let timer
    async function atualizar() {
      const atual = versao.current
      try {
        const lista = await fetchMensagens(id)
        if (!cancelado && atual === versao.current && !emAcao.current) {
          const el = mensagensRef.current
          pertoDoFim.current = !el || el.scrollHeight - el.scrollTop - el.clientHeight < 80
          setMensagens(lista)
          setErroMensagens(null)
        }
      } catch (err) {
        if (!cancelado) setErroMensagens(`Não foi possível atualizar o histórico (${err.message}).`)
      } finally {
        if (!cancelado) {
          setCarregandoMensagens(false)
          timer = setTimeout(atualizar, 5000)
        }
      }
    }
    atualizar()
    return () => { cancelado = true; clearTimeout(timer) }
  }, [id])

  useEffect(() => {
    if (pertoDoFim.current && mensagensRef.current) {
      mensagensRef.current.scrollTop = mensagensRef.current.scrollHeight
    }
  }, [mensagens])

  useEffect(() => {
    let cancelado = false
    fetchContexto(id)
      .then((dados) => { if (!cancelado) setContexto(dados) })
      .catch((err) => { if (!cancelado) setErroContexto(err.message) })
      .finally(() => { if (!cancelado) setContextoCarregado(true) })
    return () => { cancelado = true }
  }, [id])

  async function executar(acao) {
    if (emAcao.current) return
    emAcao.current = true
    versao.current += 1
    setOcupado(true)
    setErroEnvio(null)
    try {
      await acao()
      if (ativo.current) onAtualizar()
    } catch (err) {
      if (ativo.current) setErroEnvio(err.message)
    } finally {
      emAcao.current = false
      if (ativo.current) setOcupado(false)
    }
  }

  function enviar() {
    const texto = resposta.trim()
    if (!texto) return
    executar(async () => {
      const nova = await enviarMensagem(id, texto)
      if (!ativo.current) return
      pertoDoFim.current = true
      setMensagens((prev) => prev.some((m) => m.id === nova.id) ? prev : [...prev, nova])
      setResposta('')
    })
  }

  return (
    <>
      <ConversaThread
        conversa={conversa} mensagens={mensagens} resposta={resposta} onRespostaChange={setResposta}
        onEnviar={enviar} onAssumir={() => executar(() => assumirConversa(id, usuario?.nome || 'Recepção'))}
        onDevolver={() => executar(() => devolverConversa(id))} erroEnvio={erroEnvio}
        ocupado={ocupado} carregandoMensagens={carregandoMensagens} erroMensagens={erroMensagens} mensagensRef={mensagensRef}
      />
      {erroContexto ? <div className="whatsapp-contexto" role="alert">Não foi possível carregar os dados do paciente ({erroContexto}).</div>
        : <PainelContexto conversa={conversa} contexto={contexto} contextoCarregado={contextoCarregado} />}
    </>
  )
}

export default function WhatsappConversas({ onConversasAtualizadas }) {
  const [conversas, setConversas] = useState([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [filtro, setFiltro] = useState('todas')
  const [busca, setBusca] = useState('')
  const [selecionadoId, setSelecionadoId] = useState(null)
  const [revisao, setRevisao] = useState(0)

  useEffect(() => {
    let cancelado = false
    let timer
    async function atualizar() {
      try {
        const lista = await fetchConversas()
        if (!cancelado) {
          setConversas(lista)
          onConversasAtualizadas?.(lista)
          setErro(null)
        }
      } catch (err) {
        if (!cancelado) setErro(err.message)
      } finally {
        if (!cancelado) {
          setCarregando(false)
          timer = setTimeout(atualizar, 5000)
        }
      }
    }
    atualizar()
    return () => { cancelado = true; clearTimeout(timer) }
  }, [onConversasAtualizadas, revisao])

  const conversa = conversas.find((c) => c.id === selecionadoId) || null
  const contadorAguardando = conversas.filter((c) => c.estado === 'aguardando').length

  return (
    <>
      {erro && <div role="alert" className="dashboard-erro">Não foi possível atualizar as conversas ({erro}). Tentaremos novamente automaticamente.</div>}
      <div className="whatsapp-conversas">
        {carregando ? <SkeletonLista /> : (
          <ListaConversas
            conversas={conversas} filtro={filtro} onFiltro={setFiltro} busca={busca} onBusca={setBusca}
            selecionadoId={selecionadoId} onSelecionar={setSelecionadoId} contadorAguardando={contadorAguardando}
          />
        )}
        {conversa ? <Atendimento key={conversa.id} conversa={conversa} onAtualizar={() => setRevisao((n) => n + 1)} />
          : <div className="whatsapp-thread whatsapp-thread-vazia">Selecione uma conversa</div>}
      </div>
    </>
  )
}
