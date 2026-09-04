import { useEffect, useState } from 'react'
import { ArrowLeft, MessageCircle, SearchX } from 'lucide-react'
import { fetchGrupo, fetchModelos, adiarPacientes, marcarNaoContatar, enviarMensagem } from './api'
import { brl, iniciaisDe, rotuloGrupo } from './retornoData'

function NaoContatarModal({ quantidade, onCancelar, onConfirmar }) {
  const [motivo, setMotivo] = useState('')
  const [enviando, setEnviando] = useState(false)

  async function confirmar() {
    if (!motivo.trim() || enviando) return
    setEnviando(true)
    await onConfirmar(motivo.trim())
    setEnviando(false)
  }

  return (
    <div className="retorno-overlay" onClick={onCancelar}>
      <div className="retorno-modal" onClick={(e) => e.stopPropagation()}>
        <div className="retorno-modal-title">Marcar como não contatar</div>
        <div className="retorno-modal-subtitle">
          {quantidade} {quantidade === 1 ? 'paciente não vai' : 'pacientes não vão'} mais aparecer nas listas de retorno. Ação permanente.
        </div>
        <div className="retorno-modal-campo">
          <label className="retorno-label" htmlFor="rc-motivo">Motivo</label>
          <textarea
            id="rc-motivo" className="retorno-textarea" rows={3} value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
            placeholder="Ex: mudou de cidade, faleceu, pediu para não receber mensagens…"
            autoFocus
          />
        </div>
        <div className="retorno-modal-footer">
          <button type="button" className="retorno-btn-ghost" onClick={onCancelar} disabled={enviando}>Cancelar</button>
          <button type="button" className="retorno-btn-primario" onClick={confirmar} disabled={!motivo.trim() || enviando}>
            {enviando ? 'Salvando…' : 'Confirmar'}
          </button>
        </div>
      </div>
    </div>
  )
}

function GrupoCard({ grupo, onAbrir }) {
  return (
    <button type="button" className="retorno-grupo-card" onClick={() => onAbrir(grupo.grupo)}>
      <div className="retorno-grupo-nome">{rotuloGrupo(grupo.grupo)}</div>
      <div className="retorno-grupo-descricao">{grupo.descricao}</div>
      <div className="retorno-grupo-numeros">
        <span className="retorno-grupo-qtd">{grupo.quantidade}</span>
        <span className="retorno-grupo-valor">{brl(grupo.valorEstimado)}</span>
      </div>
    </button>
  )
}

export default function RetornoPendentes({ grupos, carregando, onAcaoConcluida }) {
  const [grupoAberto, setGrupoAberto] = useState(null)
  const [itens, setItens] = useState([])
  const [carregandoItens, setCarregandoItens] = useState(false)
  const [selecionados, setSelecionados] = useState(new Set())
  const [modelos, setModelos] = useState({})
  const [mostrarPreview, setMostrarPreview] = useState(false)
  const [textoMensagem, setTextoMensagem] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [modalNaoContatar, setModalNaoContatar] = useState(false)
  const [toast, setToast] = useState(null)

  useEffect(() => {
    fetchModelos().then((lista) => {
      const mapa = {}
      lista.forEach((m) => { mapa[m.grupo] = m.texto })
      setModelos(mapa)
    }).catch(() => {})
  }, [])

  useEffect(() => {
    if (!grupoAberto) return
    setCarregandoItens(true)
    setSelecionados(new Set())
    setMostrarPreview(false)
    fetchGrupo(grupoAberto)
      .then(setItens)
      .catch(() => setItens([]))
      .finally(() => setCarregandoItens(false))
  }, [grupoAberto])

  const todosSelecionados = itens.length > 0 && selecionados.size === itens.length

  function abrirGrupo(grupo) {
    setGrupoAberto(grupo)
  }

  function voltar() {
    setGrupoAberto(null)
    setItens([])
  }

  function toggleSelecionado(id) {
    setSelecionados((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id); else next.add(id)
      return next
    })
  }

  function toggleTodos() {
    setSelecionados((prev) => (prev.size === itens.length ? new Set() : new Set(itens.map((i) => i.idPaciente))))
  }

  function mostrarToast(texto) {
    setToast(texto)
    setTimeout(() => setToast(null), 3000)
  }

  function abrirPreview() {
    setTextoMensagem(modelos[grupoAberto] ?? '')
    setMostrarPreview(true)
  }

  async function confirmarEnvio() {
    if (enviando) return
    setEnviando(true)
    try {
      const ids = [...selecionados]
      const resp = await enviarMensagem(ids, grupoAberto, textoMensagem)
      mostrarToast(
        resp.pulados > 0
          ? `${resp.enviados} mensagens registradas, ${resp.pulados} já receberam contato nos últimos 30 dias.`
          : `${resp.enviados} mensagens registradas.`
      )
      setMostrarPreview(false)
      setSelecionados(new Set())
      onAcaoConcluida()
      fetchGrupo(grupoAberto).then(setItens).catch(() => {})
    } catch (err) {
      mostrarToast(err.message)
    } finally {
      setEnviando(false)
    }
  }

  async function confirmarAdiar() {
    const ids = [...selecionados]
    await adiarPacientes(ids)
    mostrarToast(`${ids.length} ${ids.length === 1 ? 'paciente adiado' : 'pacientes adiados'} por 30 dias.`)
    setSelecionados(new Set())
    onAcaoConcluida()
    fetchGrupo(grupoAberto).then(setItens).catch(() => {})
  }

  async function confirmarNaoContatar(motivo) {
    const ids = [...selecionados]
    await marcarNaoContatar(ids, motivo)
    mostrarToast(`${ids.length} ${ids.length === 1 ? 'paciente marcado' : 'pacientes marcados'} como não contatar.`)
    setModalNaoContatar(false)
    setSelecionados(new Set())
    onAcaoConcluida()
    fetchGrupo(grupoAberto).then(setItens).catch(() => {})
  }

  if (!grupoAberto) {
    if (carregando) {
      return (
        <div className="retorno-grupos-grid">
          {[0, 1, 2, 3].map((i) => <div key={i} className="retorno-grupo-card retorno-skel" />)}
        </div>
      )
    }
    const semDados = grupos.every((g) => g.quantidade === 0)
    if (semDados) {
      return (
        <div className="retorno-painel">
          <div className="retorno-vazio">
            <div className="retorno-vazio-titulo">Nenhum paciente pendente ainda</div>
            <div className="retorno-vazio-texto">
              Os grupos aparecem conforme o histórico de atendimentos se acumula — consultas realizadas,
              exames solicitados e retornos indicados nos atendimentos.
            </div>
          </div>
        </div>
      )
    }
    return (
      <div className="retorno-grupos-grid">
        {grupos.map((g) => <GrupoCard key={g.grupo} grupo={g} onAbrir={abrirGrupo} />)}
      </div>
    )
  }

  return (
    <div className="retorno-expandido">
      <button type="button" className="retorno-voltar" onClick={voltar}>
        <ArrowLeft size={14} strokeWidth={2} />Voltar aos grupos
      </button>

      <div className={`retorno-conteudo${mostrarPreview ? ' com-preview' : ''}`}>
        <div className="retorno-painel">
          <div className="retorno-painel-head">
            <div className="retorno-painel-titulo">{rotuloGrupo(grupoAberto)} <span>({itens.length})</span></div>
          </div>

          {selecionados.size > 0 && (
            <div className="retorno-bulk">
              <span className="retorno-bulk-txt">{selecionados.size} selecionado{selecionados.size > 1 ? 's' : ''}</span>
              <div className="retorno-bulk-actions">
                <button type="button" className="retorno-bbtn primary" onClick={abrirPreview}>Enviar mensagem</button>
                <button type="button" className="retorno-bbtn ghost" onClick={confirmarAdiar}>Adiar 30 dias</button>
                <button type="button" className="retorno-bbtn ghost" onClick={() => setModalNaoContatar(true)}>Marcar não contatar</button>
              </div>
            </div>
          )}

          {carregandoItens ? (
            <div className="retorno-vazio"><span>Carregando…</span></div>
          ) : itens.length === 0 ? (
            <div className="retorno-vazio">
              <SearchX size={20} strokeWidth={1.6} />
              <div className="retorno-vazio-titulo">Ninguém pendente neste grupo</div>
              <div className="retorno-vazio-texto">Boa notícia — não há pacientes aguardando contato aqui agora.</div>
            </div>
          ) : (
            <div className="retorno-tabela-scroll">
              <table className="retorno-tabela">
                <thead>
                  <tr>
                    <th style={{ width: 36 }}>
                      <input type="checkbox" checked={todosSelecionados} onChange={toggleTodos} aria-label="Selecionar todos" />
                    </th>
                    <th>Paciente</th>
                    <th>Última consulta</th>
                    <th>Contexto</th>
                    <th>Médico</th>
                    <th className="num">Valor estimado</th>
                    <th>Telefone</th>
                  </tr>
                </thead>
                <tbody>
                  {itens.map((it) => (
                    <tr key={it.idPaciente}>
                      <td onClick={(e) => e.stopPropagation()}>
                        <input
                          type="checkbox" checked={selecionados.has(it.idPaciente)}
                          onChange={() => toggleSelecionado(it.idPaciente)} aria-label={`Selecionar ${it.nome}`}
                        />
                      </td>
                      <td>
                        <div className="retorno-quem">
                          <span className="retorno-avatar">{iniciaisDe(it.nome)}</span>
                          <span>
                            <div className="retorno-quem-nome">{it.nome}</div>
                            <div className="retorno-quem-meta">{it.idade} anos</div>
                          </span>
                        </div>
                      </td>
                      <td>{it.ultimaConsultaData}<br /><span className="retorno-sub">{it.ultimaConsultaEspecialidade}</span></td>
                      <td className="retorno-contexto">{it.contexto}</td>
                      <td>{it.medico}</td>
                      <td className="num retorno-valor">{brl(it.valorEstimado)}</td>
                      <td>
                        {it.whatsapp ? (
                          <span className="retorno-wa"><MessageCircle size={13} strokeWidth={2} />{it.telefone}</span>
                        ) : it.telefone}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {mostrarPreview && (
          <div className="retorno-preview">
            <div className="retorno-preview-titulo">Mensagem para {selecionados.size} paciente{selecionados.size > 1 ? 's' : ''}</div>
            <textarea
              className="retorno-textarea" rows={8} value={textoMensagem}
              onChange={(e) => setTextoMensagem(e.target.value)}
            />
            <div className="retorno-preview-footer">
              <button type="button" className="retorno-btn-ghost" onClick={() => setMostrarPreview(false)} disabled={enviando}>Cancelar</button>
              <button type="button" className="retorno-btn-primario" onClick={confirmarEnvio} disabled={!textoMensagem.trim() || enviando}>
                {enviando ? 'Enviando…' : 'Enviar'}
              </button>
            </div>
          </div>
        )}
      </div>

      {modalNaoContatar && (
        <NaoContatarModal
          quantidade={selecionados.size}
          onCancelar={() => setModalNaoContatar(false)}
          onConfirmar={confirmarNaoContatar}
        />
      )}

      {toast && (
        <div className="retorno-toast"><span className="retorno-toast-dot" />{toast}</div>
      )}
    </div>
  )
}
