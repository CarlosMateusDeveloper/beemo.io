import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Check, X } from 'lucide-react'
import {
  aceitarGlosa, alterarResponsavelGlosa, anexarDocumentoRecurso, atualizarRecurso,
  classificarGlosa, criarRecurso, enviarRecurso, fetchGlosa, fetchUsuarios, registrarResultadoRecurso,
} from './api'
import {
  brl, CANAIS_ENVIO, CATEGORIAS_MOTIVO, corPrazoClasse, RECORRIBILIDADES,
  statusGlosaMeta, STATUS_GLOSA_TERMINAIS, statusRecursoMeta, STATUS_RECURSO_EDITAVEIS, TIPOS_DOCUMENTO_RECURSO,
} from './conveniosData'
import './convenios.css'

const DOSSIE_ITENS = [
  { chave: 'prontuario', rotulo: 'Prontuário' },
  { chave: 'guia', rotulo: 'Guia' },
  { chave: 'solicitacaoMedica', rotulo: 'Solicitação médica' },
  { chave: 'autorizacao', rotulo: 'Autorização' },
]

export default function GlosaDetalhePagina() {
  const { id } = useParams()
  const navigate = useNavigate()
  const location = useLocation()

  const idsFila = location.state?.idsFila?.length ? location.state.idsFila : [Number(id)]
  const voltarPara = location.state?.voltarPara ?? '/convenios?aba=glosas'
  const indiceAtual = idsFila.indexOf(Number(id))

  const [glosa, setGlosa] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [usuarios, setUsuarios] = useState([])
  const [salvando, setSalvando] = useState(false)
  const [erroAcao, setErroAcao] = useState(null)
  const [confirmandoPerda, setConfirmandoPerda] = useState(false)

  useEffect(() => {
    fetchUsuarios().then(setUsuarios).catch(() => setUsuarios([]))
  }, [])

  useEffect(() => {
    let cancelado = false
    setCarregando(true)
    setErroAcao(null)
    fetchGlosa(id)
      .then((d) => { if (!cancelado) { setGlosa(d); setErro(null) } })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [id])

  function irPara(idAlvo) {
    navigate(`/convenios/glosas/${idAlvo}`, { state: { idsFila, voltarPara } })
  }

  function avancarApósAção() {
    const proximo = idsFila[indiceAtual + 1]
    if (proximo != null) irPara(proximo)
    else navigate(voltarPara)
  }

  async function recarregar() {
    const d = await fetchGlosa(id)
    setGlosa(d)
    return d
  }

  async function executar(acao) {
    setSalvando(true)
    setErroAcao(null)
    try {
      await acao()
    } catch (err) {
      setErroAcao(err.message)
    } finally {
      setSalvando(false)
    }
  }

  if (erro) {
    return (
      <div className="convenios-page">
        <button type="button" className="convenios-voltar" onClick={() => navigate(voltarPara)}>
          <ArrowLeft size={15} strokeWidth={2} />Voltar para a fila
        </button>
        <div className="convenios-vazio"><div className="convenios-vazio-titulo">Não foi possível abrir esta glosa</div><div className="convenios-vazio-texto">{erro}</div></div>
      </div>
    )
  }

  if (carregando || !glosa) {
    return <div className="convenios-page"><div className="convenios-vazio">Carregando…</div></div>
  }

  const terminal = STATUS_GLOSA_TERMINAIS.has(glosa.status)
  const statusMeta = statusGlosaMeta(glosa.status)
  const recurso = glosa.recursoAtual
  const recursoEditavel = recurso && STATUS_RECURSO_EDITAVEIS.has(recurso.status)
  const recursoAguardando = recurso && !recursoEditavel && !terminal

  return (
    <div className="convenios-page">
      <div className="convenios-glosa-topo">
        <button type="button" className="convenios-voltar" style={{ marginBottom: 0 }} onClick={() => navigate(voltarPara)}>
          <ArrowLeft size={15} strokeWidth={2} />Voltar para a fila
        </button>
        {idsFila.length > 1 && (
          <div className="convenios-glosa-nav">
            <span>glosa {indiceAtual + 1} de {idsFila.length}</span>
            <button type="button" className="convenios-btn-ghost sm" disabled={indiceAtual <= 0} onClick={() => irPara(idsFila[indiceAtual - 1])}>‹ anterior</button>
            <button type="button" className="convenios-btn-ghost sm" disabled={indiceAtual >= idsFila.length - 1} onClick={() => irPara(idsFila[indiceAtual + 1])}>próxima ›</button>
          </div>
        )}
      </div>

      <div className="convenios-glosa-header">
        <div>
          <div className="convenios-glosa-header-nome">{glosa.atendimento?.pacienteNome}{glosa.atendimento?.procedimento ? ` — ${glosa.atendimento.procedimento}` : ''}</div>
          <div className="convenios-glosa-header-meta">{glosa.convenioNome} · atendimento em {glosa.atendimento?.dataTxt} · glosa registrada em {glosa.dataGlosaTxt}</div>
        </div>
        <div className="convenios-glosa-header-valor">
          <div className="convenios-valor" style={{ fontSize: 20 }}>{brl(glosa.valorGlosado)}</div>
          <div className={`convenios-prazo-val ${corPrazoClasse(glosa.corPrazo)}`}>{glosa.prazoRecursoTxt ?? 'sem prazo'}</div>
        </div>
      </div>

      {terminal ? (
        <div className="convenios-sub">
          <span className={`convenios-badge ${statusMeta.cls}`}>{statusMeta.rotulo}</span>
          <div style={{ marginTop: 14, display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14 }}>
            <div><div className="convenios-label">Valor glosado</div><div className="convenios-valor">{brl(glosa.valorGlosado)}</div></div>
            {recurso && (
              <>
                <div><div className="convenios-label">Valor recuperado</div><div className="convenios-valor" style={{ color: 'var(--success)' }}>{brl(recurso.valorRecuperado)}</div></div>
                <div><div className="convenios-label">Valor perdido</div><div className="convenios-valor" style={{ color: 'var(--danger)' }}>{brl(recurso.valorNaoRecuperado)}</div></div>
              </>
            )}
          </div>
          {recurso?.motivoNegativa && <div className="convenios-sub-vazio" style={{ marginTop: 10 }}><span>Motivo da negativa: {recurso.motivoNegativa}</span></div>}
        </div>
      ) : (
        <div className="convenios-glosa-grid">
          <div className="convenios-glosa-col-principal">
            <BlocoMotivo glosa={glosa} />

            {erroAcao && <div className="convenios-modal-erro">{erroAcao}</div>}

            {!glosa.recorribilidade && (
              <BlocoClassificacao
                salvando={salvando}
                onClassificar={(dados) => executar(async () => { await classificarGlosa(id, dados); await recarregar() })}
              />
            )}

            {glosa.recorribilidade === 'nao_recorrivel' && !recurso && (
              <div className="convenios-sub"><div className="convenios-sub-vazio"><strong>Classificada como não recorrível</strong><span>Não é possível criar recurso para esta glosa. Resta aceitar a perda.</span></div></div>
            )}

            {glosa.recorribilidade && glosa.recorribilidade !== 'nao_recorrivel' && !recurso && (
              <div className="convenios-sub">
                <div className="convenios-sub-vazio"><strong>Pronta para recurso</strong><span>O prazo de recurso da glosa é herdado automaticamente ao criar o recurso.</span></div>
                <div className="convenios-modal-footer">
                  <button type="button" className="convenios-btn-primario" disabled={salvando} onClick={() => executar(async () => { await criarRecurso(id, {}); await recarregar() })}>
                    Criar recurso
                  </button>
                </div>
              </div>
            )}

            {recursoEditavel && (
              <BlocoRecurso
                recurso={recurso} usuarios={usuarios} salvando={salvando}
                onSalvar={(dados) => executar(async () => { await atualizarRecurso(recurso.id, dados); await recarregar() })}
                onAnexar={(dados) => executar(async () => { await anexarDocumentoRecurso(recurso.id, dados); await recarregar() })}
                onEnviar={(dados) => executar(async () => { await enviarRecurso(recurso.id, dados); await recarregar(); avancarApósAção() })}
              />
            )}

            {recursoAguardando && (
              <BlocoResultado
                recurso={recurso} salvando={salvando}
                onRegistrar={(dados) => executar(async () => { await registrarResultadoRecurso(recurso.id, dados); await recarregar(); avancarApósAção() })}
              />
            )}

            {glosa.status !== 'recurso_enviado' && (
              <div className="convenios-sub">
                {!confirmandoPerda ? (
                  <button type="button" className="convenios-btn-ghost" onClick={() => setConfirmandoPerda(true)}>Aceitar perda</button>
                ) : (
                  <div className="convenios-sub-vazio">
                    <strong>Aceitar a perda desta glosa?</strong>
                    <span>Fica registrado no histórico quem aceitou e quando. Esta ação não pode ser desfeita.</span>
                    <div className="convenios-modal-footer">
                      <button type="button" className="convenios-btn-ghost" onClick={() => setConfirmandoPerda(false)}>Cancelar</button>
                      <button
                        type="button" className="convenios-btn-primario" disabled={salvando}
                        onClick={() => executar(async () => { await aceitarGlosa(id); setConfirmandoPerda(false); await recarregar(); avancarApósAção() })}
                      >
                        Confirmar perda
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          <div className="convenios-glosa-col-lateral">
            <div className="convenios-sub">
              <div className="convenios-sub-head"><h3>Dossiê</h3></div>
              {DOSSIE_ITENS.map((item) => {
                const disponivel = glosa.documentosDisponiveis?.[item.chave]
                return (
                  <div key={item.chave} className="convenios-checklist-item">
                    {disponivel ? <Check size={15} strokeWidth={2.4} style={{ color: 'var(--success)' }} /> : <X size={15} strokeWidth={2.4} style={{ color: 'var(--danger)' }} />}
                    {item.rotulo}
                  </div>
                )
              })}
            </div>

            <BlocoResponsavel
              nomeAtual={glosa.responsavelNome} usuarios={usuarios} salvando={salvando}
              onAlterar={(idUsuario) => executar(async () => { await alterarResponsavelGlosa(id, idUsuario); await recarregar() })}
            />

            <div className="convenios-sub">
              <div className="convenios-sub-head"><h3>Histórico</h3></div>
              <div className="convenios-timeline">
                {(glosa.historico ?? []).map((h, i) => (
                  <div key={i} className="convenios-timeline-item">
                    <div className="convenios-timeline-dot" />
                    <div>
                      <div className="convenios-timeline-evento">{h.evento}{h.usuarioNome ? ` — ${h.usuarioNome}` : ''}</div>
                      <div className="convenios-timeline-data">{h.dataHoraTxt}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function BlocoMotivo({ glosa }) {
  return (
    <div className="convenios-sub">
      <div className="convenios-sub-head"><h3>O que o convênio disse</h3></div>
      <div className="convenios-motivo-cod" style={{ marginBottom: 8 }}>{glosa.codigoMotivo ?? 'sem código'}</div>
      <p style={{ fontSize: 13.5, color: 'var(--text-primary)', margin: 0 }}>{glosa.motivo}</p>
      {glosa.categoriaMotivo && (
        <span className="convenios-badge st-neutro" style={{ marginTop: 10 }}>{CATEGORIAS_MOTIVO.find((c) => c.valor === glosa.categoriaMotivo)?.rotulo ?? glosa.categoriaMotivo}</span>
      )}
    </div>
  )
}

function BlocoClassificacao({ salvando, onClassificar }) {
  const [recorribilidade, setRecorribilidade] = useState('')
  const [categoriaMotivo, setCategoriaMotivo] = useState('')

  return (
    <div className="convenios-sub">
      <div className="convenios-sub-head"><h3>Classificar glosa</h3></div>
      <div className="convenios-sub-form-grid">
        <div className="convenios-modal-campo">
          <label className="convenios-label">Recorribilidade</label>
          <select className="convenios-input" value={recorribilidade} onChange={(e) => setRecorribilidade(e.target.value)}>
            <option value="">Selecione</option>
            {RECORRIBILIDADES.map((r) => <option key={r.valor} value={r.valor}>{r.rotulo}</option>)}
          </select>
        </div>
        <div className="convenios-modal-campo">
          <label className="convenios-label">Categoria do motivo</label>
          <select className="convenios-input" value={categoriaMotivo} onChange={(e) => setCategoriaMotivo(e.target.value)}>
            <option value="">Selecione</option>
            {CATEGORIAS_MOTIVO.map((c) => <option key={c.valor} value={c.valor}>{c.rotulo}</option>)}
          </select>
        </div>
      </div>
      <div className="convenios-modal-footer">
        <button
          type="button" className="convenios-btn-primario" disabled={salvando || !recorribilidade}
          onClick={() => onClassificar({ recorribilidade, categoriaMotivo: categoriaMotivo || null })}
        >
          Classificar
        </button>
      </div>
    </div>
  )
}

function BlocoRecurso({ recurso, usuarios, salvando, onSalvar, onAnexar, onEnviar }) {
  const [justificativa, setJustificativa] = useState(recurso.justificativa ?? '')
  const [prazoLimite, setPrazoLimite] = useState(recurso.prazoLimiteTxt ? isoDeTxt(recurso.prazoLimiteTxt) : '')
  const [responsavelId, setResponsavelId] = useState('')
  const [evidenciasConferidas, setEvidenciasConferidas] = useState(false)
  const [novoDocTipo, setNovoDocTipo] = useState('')
  const [novoDocDescricao, setNovoDocDescricao] = useState('')
  const [enviarAberto, setEnviarAberto] = useState(false)
  const [canalEnvio, setCanalEnvio] = useState('')
  const [protocolo, setProtocolo] = useState('')

  const checklist = recurso.checklist

  return (
    <div className="convenios-sub">
      <div className="convenios-sub-head"><h3>Recurso</h3><span className={`convenios-badge ${statusRecursoMeta(recurso.status).cls}`}>{statusRecursoMeta(recurso.status).rotulo}</span></div>

      <div className="convenios-modal-campo">
        <label className="convenios-label">Justificativa</label>
        <textarea className="convenios-textarea" rows={5} value={justificativa} onChange={(e) => setJustificativa(e.target.value)} placeholder="Explique por que a glosa deve ser reconsiderada…" />
      </div>

      <div className="convenios-sub-form-grid">
        <div className="convenios-modal-campo">
          <label className="convenios-label">Prazo limite</label>
          <input type="date" className="convenios-input" value={prazoLimite} onChange={(e) => setPrazoLimite(e.target.value)} />
        </div>
        <div className="convenios-modal-campo">
          <label className="convenios-label">Responsável</label>
          <select className="convenios-input" value={responsavelId} onChange={(e) => setResponsavelId(e.target.value)}>
            <option value="">{recurso.responsavelNome ?? 'Selecione'}</option>
            {usuarios.map((u) => <option key={u.id} value={u.id}>{u.nome}</option>)}
          </select>
        </div>
      </div>

      <label className="convenios-form-check" style={{ marginBottom: 10 }}>
        <input type="checkbox" checked={evidenciasConferidas} onChange={(e) => setEvidenciasConferidas(e.target.checked)} />
        Evidências conferidas
      </label>

      <div className="convenios-modal-footer" style={{ marginBottom: 14 }}>
        <button
          type="button" className="convenios-btn-ghost sm" disabled={salvando}
          onClick={() => onSalvar({ justificativa, prazoLimite: prazoLimite || null, idUsuarioResponsavel: responsavelId ? Number(responsavelId) : null, evidenciasConferidas })}
        >
          Salvar recurso
        </button>
      </div>

      <div className="convenios-sub-form">
        <div className="convenios-sub-form-head">Documentos / evidências</div>
        {(recurso.documentos ?? []).map((d) => (
          <div key={d.id} className="convenios-checklist-item"><Check size={14} strokeWidth={2.4} style={{ color: 'var(--success)' }} />{TIPOS_DOCUMENTO_RECURSO.find((t) => t.valor === d.tipo)?.rotulo ?? d.tipo}{d.descricao ? ` — ${d.descricao}` : ''}</div>
        ))}
        <div className="convenios-sub-form-grid" style={{ marginTop: 10 }}>
          <div className="convenios-modal-campo">
            <select className="convenios-input" value={novoDocTipo} onChange={(e) => setNovoDocTipo(e.target.value)}>
              <option value="">Tipo do documento</option>
              {TIPOS_DOCUMENTO_RECURSO.map((t) => <option key={t.valor} value={t.valor}>{t.rotulo}</option>)}
            </select>
          </div>
          <div className="convenios-modal-campo">
            <input className="convenios-input" placeholder="Descrição (opcional)" value={novoDocDescricao} onChange={(e) => setNovoDocDescricao(e.target.value)} />
          </div>
        </div>
        <button
          type="button" className="convenios-btn-ghost sm" disabled={salvando || !novoDocTipo}
          onClick={() => { onAnexar({ tipo: novoDocTipo, descricao: novoDocDescricao || null }); setNovoDocTipo(''); setNovoDocDescricao('') }}
        >
          Anexar
        </button>
      </div>

      <div className="convenios-sub-form">
        <div className="convenios-sub-form-head">Checklist antes do envio</div>
        <ChecklistLinha ok={checklist.motivoAnalisado} texto="Motivo analisado" />
        <ChecklistLinha ok={checklist.justificativaPreenchida} texto="Justificativa preenchida" />
        <ChecklistLinha ok={checklist.documentacaoAnexada} texto="Documentação obrigatória anexada" />
        <ChecklistLinha ok={checklist.evidenciasConferidas} texto="Evidências conferidas" />
        <ChecklistLinha ok={checklist.responsavelDefinido} texto="Responsável definido" />
        <ChecklistLinha ok={checklist.prazoValido} texto="Prazo válido" />

        {!enviarAberto ? (
          <div className="convenios-modal-footer">
            <button type="button" className="convenios-btn-primario" disabled={!checklist.podeEnviar || salvando} onClick={() => setEnviarAberto(true)}>
              Enviar recurso
            </button>
          </div>
        ) : (
          <div className="convenios-sub-form-grid" style={{ marginTop: 10 }}>
            <div className="convenios-modal-campo">
              <select className="convenios-input" value={canalEnvio} onChange={(e) => setCanalEnvio(e.target.value)}>
                <option value="">Canal de envio</option>
                {CANAIS_ENVIO.map((c) => <option key={c.valor} value={c.valor}>{c.rotulo}</option>)}
              </select>
            </div>
            <div className="convenios-modal-campo">
              <input className="convenios-input" placeholder="Protocolo (opcional)" value={protocolo} onChange={(e) => setProtocolo(e.target.value)} />
            </div>
            <div className="convenios-modal-footer" style={{ gridColumn: '1 / -1' }}>
              <button type="button" className="convenios-btn-ghost" onClick={() => setEnviarAberto(false)}>Cancelar</button>
              <button type="button" className="convenios-btn-primario" disabled={!canalEnvio || salvando} onClick={() => onEnviar({ canalEnvio, protocolo: protocolo || null })}>
                Confirmar envio
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function BlocoResultado({ recurso, salvando, onRegistrar }) {
  const [valorRecuperado, setValorRecuperado] = useState('')
  const [motivoNegativa, setMotivoNegativa] = useState('')
  const [protocoloResposta, setProtocoloResposta] = useState('')

  return (
    <div className="convenios-sub">
      <div className="convenios-sub-head"><h3>Aguardando retorno do convênio</h3><span className={`convenios-badge ${statusRecursoMeta(recurso.status).cls}`}>{statusRecursoMeta(recurso.status).rotulo}</span></div>
      <div className="convenios-sub-vazio"><span>Enviado em {recurso.enviadoEmTxt} · protocolo {recurso.protocolo ?? '—'}</span></div>

      <div className="convenios-sub-form">
        <div className="convenios-sub-form-head">Registrar resultado</div>
        <div className="convenios-sub-form-grid">
          <div className="convenios-modal-campo">
            <label className="convenios-label">Valor recuperado</label>
            <input type="number" step="0.01" className="convenios-input" value={valorRecuperado} onChange={(e) => setValorRecuperado(e.target.value)} />
          </div>
          <div className="convenios-modal-campo">
            <label className="convenios-label">Protocolo da resposta</label>
            <input className="convenios-input" value={protocoloResposta} onChange={(e) => setProtocoloResposta(e.target.value)} />
          </div>
          <div className="convenios-modal-campo" style={{ gridColumn: '1 / -1' }}>
            <label className="convenios-label">Motivo da negativa (se houver)</label>
            <input className="convenios-input" value={motivoNegativa} onChange={(e) => setMotivoNegativa(e.target.value)} />
          </div>
        </div>
        <div className="convenios-modal-footer">
          <button
            type="button" className="convenios-btn-primario" disabled={salvando || valorRecuperado === ''}
            onClick={() => onRegistrar({ valorRecuperado: Number(valorRecuperado), valorNaoRecuperado: null, motivoNegativa: motivoNegativa || null, protocoloResposta: protocoloResposta || null, documentoRespostaUrl: null })}
          >
            Registrar resultado
          </button>
        </div>
      </div>
    </div>
  )
}

function BlocoResponsavel({ nomeAtual, usuarios, salvando, onAlterar }) {
  return (
    <div className="convenios-sub">
      <div className="convenios-sub-head"><h3>Responsável</h3></div>
      {nomeAtual ? (
        <div className="convenios-resp" style={{ marginBottom: 10 }}><span className="convenios-resp-av">{nomeAtual.slice(0, 2).toUpperCase()}</span>{nomeAtual}</div>
      ) : (
        <div className="convenios-resp-vazio" style={{ marginBottom: 10 }}><span className="convenios-resp-vazio-av" />Sem responsável</div>
      )}
      <select
        className="convenios-input" value="" disabled={salvando}
        onChange={(e) => { if (e.target.value) onAlterar(Number(e.target.value)) }}
      >
        <option value="">Alterar responsável…</option>
        {usuarios.map((u) => <option key={u.id} value={u.id}>{u.nome}</option>)}
      </select>
    </div>
  )
}

function ChecklistLinha({ ok, texto }) {
  return (
    <div className="convenios-checklist-item">
      {ok ? <Check size={14} strokeWidth={2.4} style={{ color: 'var(--success)' }} /> : <X size={14} strokeWidth={2.4} style={{ color: 'var(--text-tertiary)' }} />}
      {texto}
    </div>
  )
}

// prazoLimiteTxt vem formatado dd/MM/yyyy — <input type="date"> precisa de yyyy-MM-dd.
function isoDeTxt(txt) {
  const [dia, mes, ano] = txt.split('/')
  return dia && mes && ano ? `${ano}-${mes}-${dia}` : ''
}
