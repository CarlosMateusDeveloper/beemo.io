import { useEffect, useRef, useState } from 'react'
import { X } from 'lucide-react'
import { VARIAVEIS, preencherVariaveis } from './whatsappData'
import { fetchAssistente, alternarCapacidade, salvarMensagensCapacidade, salvarRegras } from './api'

const CAMPOS = [
  { chave: 'saudacao', label: 'saudação' },
  { chave: 'opcoes', label: 'opções' },
  { chave: 'confirmacao', label: 'confirmação' },
  { chave: 'naoEntendi', label: 'não entendi' },
]

// Turnos genéricos do paciente na prévia — a prévia é uma simulação dos
// textos atuais, não uma conversa real registrada.
const RESPOSTAS_EXEMPLO = ['1', 'sim']
const SAMPLE_VARS = { nome: 'Renata', clinica: 'a clínica', data: '27/08', hora: '14:20', medico: 'Dr. Ivan Portela', especialidade: 'cardiologia' }
const AUTOSAVE_MS = 700

function Skeleton() {
  return <div className="dashboard-card-skel" />
}

function Capacidades({ capacidades, selecionadaId, onSelecionar, onToggle }) {
  const ativas = capacidades.filter((c) => c.ativo).length
  return (
    <div className="whatsapp-card">
      <div className="whatsapp-card-head">
        <span className="whatsapp-card-titulo">Capacidades</span>
        <span className="whatsapp-card-nota">{ativas} de {capacidades.length} ativas</span>
      </div>
      {capacidades.map((c) => (
        <div key={c.id} className={`whatsapp-cap-row${selecionadaId === c.id ? ' selected' : ''}${!c.ativo ? ' off' : ''}`}>
          <button type="button" className="whatsapp-cap-nome" onClick={() => onSelecionar(c.id)}>
            {selecionadaId === c.id && <span className="whatsapp-cap-dot" />}
            {c.nome}
          </button>
          <span className="whatsapp-cap-status">{selecionadaId === c.id ? 'editando' : c.ativo ? 'ativo' : 'desligado'}</span>
          <button
            type="button" role="switch" aria-checked={c.ativo}
            className={`whatsapp-toggle${c.ativo ? ' on' : ''}`}
            onClick={() => onToggle(c.id)}
          >
            <span className="whatsapp-toggle-knob" />
          </button>
        </div>
      ))}
      {(() => {
        const desligada = capacidades.find((c) => c.id === selecionadaId && !c.ativo)
        return desligada ? <div className="whatsapp-cap-impacto">{desligada.impactoDesligada}</div> : null
      })()}
    </div>
  )
}

function Mensagens({ capacidadeNome, textos, refs, onChange, onFocus, saveState, onInserirVariavel }) {
  return (
    <div className="whatsapp-card">
      <div className="whatsapp-card-head">
        <span className="whatsapp-card-titulo">Mensagens</span>
        <span className="whatsapp-card-nota">· {capacidadeNome}</span>
        <span className="whatsapp-flex" />
        <span className={`whatsapp-save-indicador ${saveState}`}>
          <span className="whatsapp-save-dot" />
          {saveState === 'saving' ? 'salvando…' : 'salvo automaticamente'}
        </span>
      </div>

      <div className="whatsapp-campos-grid">
        {CAMPOS.map(({ chave, label }) => (
          <div key={chave} className="whatsapp-campo">
            <span className="whatsapp-campo-label">{label}</span>
            <textarea
              ref={refs[chave]}
              value={textos[chave] || ''}
              onChange={(e) => onChange(chave, e.target.value)}
              onFocus={() => onFocus(chave)}
            />
          </div>
        ))}
      </div>

      <div className="whatsapp-variaveis">
        <span className="whatsapp-campo-label">variáveis</span>
        {VARIAVEIS.map((v) => (
          <button key={v} type="button" className="whatsapp-chip-var" onClick={() => onInserirVariavel(v)}>{`{${v}}`}</button>
        ))}
        <span className="whatsapp-flex" />
        <span className="whatsapp-campo-nota">clique para inserir no texto</span>
      </div>
    </div>
  )
}

function Regras({ regras, onChange }) {
  const [novaPalavra, setNovaPalavra] = useState('')
  const [adicionando, setAdicionando] = useState(false)

  function removerPalavra(p) {
    onChange({ ...regras, escalonamento: { ...regras.escalonamento, palavrasChave: regras.escalonamento.palavrasChave.filter((x) => x !== p) } })
  }
  function confirmarPalavra() {
    const p = novaPalavra.trim().toLowerCase()
    if (p && !regras.escalonamento.palavrasChave.includes(p)) {
      onChange({ ...regras, escalonamento: { ...regras.escalonamento, palavrasChave: [...regras.escalonamento.palavrasChave, p] } })
    }
    setNovaPalavra('')
    setAdicionando(false)
  }

  return (
    <div className="whatsapp-card">
      <div className="whatsapp-card-head">
        <span className="whatsapp-card-titulo">Regras</span>
        <span className="whatsapp-flex" />
        <span className="whatsapp-card-nota">valem para todas as capacidades</span>
      </div>

      <div className="whatsapp-regras-grid">
        <div className="whatsapp-regras-col">
          <span className="whatsapp-campo-label">quando chamar um humano</span>
          <label className="whatsapp-check-row">
            <input
              type="checkbox" checked={regras.escalonamento.pedirAtendenteSempre}
              onChange={(e) => onChange({ ...regras, escalonamento: { ...regras.escalonamento, pedirAtendenteSempre: e.target.checked } })}
            />
            <span>Paciente pediu atendente</span>
          </label>
          <label className="whatsapp-check-row">
            <input type="checkbox" checked readOnly />
            <span>Bot não entendeu</span>
            <input
              type="number" min="1" max="5" className="whatsapp-num-pill"
              value={regras.escalonamento.naoEntendeuLimite}
              onChange={(e) => onChange({ ...regras, escalonamento: { ...regras.escalonamento, naoEntendeuLimite: Number(e.target.value) || 1 } })}
            />
            <span className="whatsapp-campo-nota">vezes</span>
          </label>
          <label className="whatsapp-check-row">
            <input type="checkbox" checked readOnly />
            <span>Palavras-chave na mensagem</span>
          </label>
          <div className="whatsapp-palavras">
            {regras.escalonamento.palavrasChave.map((p) => (
              <span key={p} className="whatsapp-chip-palavra">
                {p}
                <button type="button" onClick={() => removerPalavra(p)} aria-label={`Remover ${p}`}><X size={9} strokeWidth={2.6} /></button>
              </span>
            ))}
            {adicionando ? (
              <input
                autoFocus type="text" className="whatsapp-palavra-input" value={novaPalavra}
                onChange={(e) => setNovaPalavra(e.target.value)}
                onBlur={confirmarPalavra}
                onKeyDown={(e) => e.key === 'Enter' && confirmarPalavra()}
              />
            ) : (
              <button type="button" className="whatsapp-chip-add" onClick={() => setAdicionando(true)}>+ adicionar</button>
            )}
          </div>
        </div>

        <div className="whatsapp-regras-col">
          <span className="whatsapp-campo-label">horário e disparos</span>
          <div className="whatsapp-linha-valor">
            <span>Atendimento humano</span>
            <input
              type="text" className="whatsapp-valor-pill"
              value={regras.horarios.atendimentoHumano}
              onChange={(e) => onChange({ ...regras, horarios: { ...regras.horarios, atendimentoHumano: e.target.value } })}
            />
          </div>
          <div className="whatsapp-linha-valor">
            <span>Sábado</span>
            <input
              type="text" className="whatsapp-valor-pill"
              value={regras.horarios.sabado}
              onChange={(e) => onChange({ ...regras, horarios: { ...regras.horarios, sabado: e.target.value } })}
            />
          </div>
          <div className="whatsapp-linha-valor">
            <span>Confirmação de presença</span>
            <span className="whatsapp-valor-pill">
              <input
                type="number" min="1" className="whatsapp-num-inline"
                value={regras.disparos.confirmacaoPresencaHoras}
                onChange={(e) => onChange({ ...regras, disparos: { ...regras.disparos, confirmacaoPresencaHoras: Number(e.target.value) || 1 } })}
              /> h antes
            </span>
          </div>
          <p className="whatsapp-regra-nota">
            Fora do horário o assistente responde 24h e avisa que a recepção retorna na abertura. Limite global de{' '}
            <input
              type="number" min="1" className="whatsapp-num-inline strong"
              value={regras.limiteMensagensPorPacientePorDia}
              onChange={(e) => onChange({ ...regras, limiteMensagensPorPacientePorDia: Number(e.target.value) || 1 })}
            />{' '}mensagens por paciente por dia, compartilhado com as réguas de /retorno.
          </p>
        </div>
      </div>
    </div>
  )
}

function Preview({ capacidadeNome, textos }) {
  const saudacao = preencherVariaveis(textos.saudacao || '', SAMPLE_VARS)
  const opcoes = preencherVariaveis(textos.opcoes || '', SAMPLE_VARS)
  const confirmacao = preencherVariaveis(textos.confirmacao || '', SAMPLE_VARS)

  const turnos = [
    { de: 'bot', texto: saudacao },
    { de: 'paciente', texto: RESPOSTAS_EXEMPLO[0] },
    { de: 'bot', texto: opcoes },
    { de: 'paciente', texto: RESPOSTAS_EXEMPLO[1] },
    { de: 'bot', texto: confirmacao },
  ].filter((t) => t.texto)

  return (
    <div className="whatsapp-card whatsapp-preview-card">
      <div className="whatsapp-card-head">
        <span className="whatsapp-card-titulo">Prévia</span>
        <span className="whatsapp-flex" />
        <span className="whatsapp-preview-pill">{capacidadeNome}</span>
      </div>

      <div className="whatsapp-preview-fone-wrap">
        <div className="whatsapp-preview-fone">
          <div className="whatsapp-preview-tela">
            <div className="whatsapp-preview-msgs">
              {turnos.map((t, i) => (
                <div key={i} className={`whatsapp-preview-bubble ${t.de}`}>
                  {t.texto.split('\n').map((linha, j) => <span key={j}>{j > 0 && <br />}{linha}</span>)}
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
      <div className="whatsapp-preview-legenda">atualiza enquanto você edita · dados de exemplo (var. preenchidas com {JSON.stringify(SAMPLE_VARS.nome)})</div>
    </div>
  )
}

export default function WhatsappAssistente() {
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [capacidades, setCapacidades] = useState([])
  const [selecionadaId, setSelecionadaId] = useState(null)
  const [mensagens, setMensagens] = useState({})
  const [regras, setRegras] = useState(null)
  const [campoFocado, setCampoFocado] = useState('saudacao')
  const [saveState, setSaveState] = useState('saved')
  const saveTimer = useRef(null)

  const saudacaoRef = useRef(null)
  const opcoesRef = useRef(null)
  const confirmacaoRef = useRef(null)
  const naoEntendiRef = useRef(null)
  const refs = { saudacao: saudacaoRef, opcoes: opcoesRef, confirmacao: confirmacaoRef, naoEntendi: naoEntendiRef }

  useEffect(() => {
    let cancelado = false
    fetchAssistente()
      .then((d) => {
        if (cancelado) return
        setCapacidades(d.capacidades)
        setMensagens(d.mensagens)
        setRegras(d.regras)
        setSelecionadaId(d.capacidades[0]?.id ?? null)
      })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [])

  function alterarTexto(campo, valor) {
    setMensagens((prev) => ({ ...prev, [selecionadaId]: { ...prev[selecionadaId], [campo]: valor } }))
    setSaveState('saving')
    clearTimeout(saveTimer.current)
    saveTimer.current = setTimeout(() => {
      salvarMensagensCapacidade(selecionadaId, { [campo]: valor })
        .then(() => setSaveState('saved'))
        .catch(() => setSaveState('saved'))
    }, AUTOSAVE_MS)
  }

  function alterarRegras(novasRegras) {
    setRegras(novasRegras)
    setSaveState('saving')
    clearTimeout(saveTimer.current)
    saveTimer.current = setTimeout(() => {
      salvarRegras(novasRegras)
        .then(() => setSaveState('saved'))
        .catch(() => setSaveState('saved'))
    }, AUTOSAVE_MS)
  }

  function alternarCapacidadeLocal(id) {
    const atual = capacidades.find((c) => c.id === id)
    if (!atual) return
    const novoAtivo = !atual.ativo
    setCapacidades((prev) => prev.map((c) => (c.id === id ? { ...c, ativo: novoAtivo } : c)))
    alternarCapacidade(id, novoAtivo).catch(() => {
      setCapacidades((prev) => prev.map((c) => (c.id === id ? { ...c, ativo: atual.ativo } : c)))
    })
  }

  function inserirVariavel(nomeVar) {
    const ref = refs[campoFocado]?.current
    const atual = mensagens[selecionadaId]?.[campoFocado] || ''
    if (!ref) {
      alterarTexto(campoFocado, `${atual}{${nomeVar}}`)
      return
    }
    const start = ref.selectionStart ?? atual.length
    const end = ref.selectionEnd ?? atual.length
    const inserido = `{${nomeVar}}`
    const novo = atual.slice(0, start) + inserido + atual.slice(end)
    alterarTexto(campoFocado, novo)
    requestAnimationFrame(() => {
      ref.focus()
      const pos = start + inserido.length
      ref.setSelectionRange(pos, pos)
    })
  }

  if (erro) {
    return <div className="dashboard-erro">Não foi possível carregar o assistente ({erro}).</div>
  }

  if (carregando || !regras) {
    return (
      <div className="whatsapp-assistente">
        <div className="whatsapp-assistente-col-esq">
          <div className="whatsapp-card"><Skeleton /></div>
          <div className="whatsapp-card"><Skeleton /></div>
        </div>
        <div className="whatsapp-assistente-col-dir"><div className="whatsapp-card"><Skeleton /></div></div>
      </div>
    )
  }

  const capacidadeSelecionada = capacidades.find((c) => c.id === selecionadaId)
  const textosSelecionados = mensagens[selecionadaId] || {}

  return (
    <div className="whatsapp-assistente">
      <div className="whatsapp-assistente-col-esq">
        <Capacidades capacidades={capacidades} selecionadaId={selecionadaId} onSelecionar={setSelecionadaId} onToggle={alternarCapacidadeLocal} />
        <Mensagens
          capacidadeNome={capacidadeSelecionada?.nome || ''} textos={textosSelecionados} refs={refs}
          onChange={alterarTexto} onFocus={setCampoFocado} saveState={saveState} onInserirVariavel={inserirVariavel}
        />
        <Regras regras={regras} onChange={alterarRegras} />
      </div>
      <div className="whatsapp-assistente-col-dir">
        <Preview capacidadeNome={capacidadeSelecionada?.nome || ''} textos={textosSelecionados} />
      </div>
    </div>
  )
}
