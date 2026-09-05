import { useEffect, useState } from 'react'
import { ArrowLeft } from 'lucide-react'
import { fetchSugestoesLotes, fetchElegiveisLote, criarLote } from './api'
import { brl, statusAuditoriaMeta } from './conveniosData'

const PASSOS = ['Sugestão', 'Revisão', 'Confirmação']

function Progresso({ passo }) {
  return (
    <div className="convenios-wizard-passos">
      {PASSOS.map((label, i) => (
        <div key={label} className={`convenios-wizard-passo${i + 1 === passo ? ' active' : ''}${i + 1 < passo ? ' feito' : ''}`}>
          <span className="convenios-wizard-passo-num">{i + 1}</span>
          {label}
        </div>
      ))}
    </div>
  )
}

export default function LoteWizard({ onCancelar, onCriado }) {
  const [passo, setPasso] = useState(1)
  const [sugestoes, setSugestoes] = useState([])
  const [carregandoSugestoes, setCarregandoSugestoes] = useState(true)

  const [convenio, setConvenio] = useState(null) // { idConvenio, convenioNome }
  const [itens, setItens] = useState([])
  const [carregandoItens, setCarregandoItens] = useState(false)
  const [selecionados, setSelecionados] = useState(new Set())

  const [criando, setCriando] = useState(false)
  const [erro, setErro] = useState(null)

  useEffect(() => {
    fetchSugestoesLotes()
      .then(setSugestoes)
      .catch((err) => setErro(err.message))
      .finally(() => setCarregandoSugestoes(false))
  }, [])

  function revisarLote(sugestao) {
    setConvenio(sugestao)
    setCarregandoItens(true)
    setErro(null)
    fetchElegiveisLote(sugestao.idConvenio)
      .then((lista) => {
        setItens(lista)
        setSelecionados(new Set(lista.map((i) => i.idFatura)))
        setPasso(2)
      })
      .catch((err) => setErro(err.message))
      .finally(() => setCarregandoItens(false))
  }

  function toggleItem(idFatura) {
    setSelecionados((prev) => {
      const next = new Set(prev)
      if (next.has(idFatura)) next.delete(idFatura); else next.add(idFatura)
      return next
    })
  }

  function toggleTodos() {
    setSelecionados((prev) => (prev.size === itens.length ? new Set() : new Set(itens.map((i) => i.idFatura))))
  }

  const itensSelecionados = itens.filter((i) => selecionados.has(i.idFatura))
  const valorSelecionado = itensSelecionados.reduce((soma, i) => soma + Number(i.valor), 0)

  async function confirmarCriacao() {
    if (criando || selecionados.size === 0) return
    setCriando(true)
    setErro(null)
    try {
      const lote = await criarLote(convenio.idConvenio, [...selecionados])
      onCriado(lote)
    } catch (err) {
      setErro(err.message)
    } finally {
      setCriando(false)
    }
  }

  return (
    <div className="convenios-painel" style={{ padding: 20 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
        <button type="button" className="convenios-voltar" style={{ margin: 0 }} onClick={onCancelar}>
          <ArrowLeft size={15} strokeWidth={2} />Cancelar
        </button>
        <Progresso passo={passo} />
      </div>

      {erro && <div className="convenios-modal-erro">{erro}</div>}

      {passo === 1 && (
        <>
          <h3 style={{ fontSize: 15, fontWeight: 600, marginBottom: 4 }}>Novo lote recomendado</h3>
          <p style={{ fontSize: 13, color: 'var(--text-tertiary)', marginBottom: 16 }}>
            O ClinicOS agrupou os atendimentos faturados e ainda não enviados por convênio. Escolha um pra revisar.
          </p>
          {carregandoSugestoes ? (
            <div className="convenios-vazio">Carregando…</div>
          ) : sugestoes.length === 0 ? (
            <div className="convenios-vazio">
              <div className="convenios-vazio-titulo">Nenhum atendimento elegível pra lote agora</div>
              <div className="convenios-vazio-texto">
                Atendimentos entram aqui quando têm fatura pendente e não foram bloqueados pela auditoria.
              </div>
            </div>
          ) : (
            <div className="convenios-wizard-sugestoes">
              {sugestoes.map((s) => (
                <div key={s.idConvenio} className="convenios-wizard-sugestao">
                  <div>
                    <div className="convenios-wizard-sugestao-titulo">{s.convenioNome}</div>
                    <div className="convenios-wizard-sugestao-sub">{s.quantidade} atendimentos · {brl(s.valorTotal)}</div>
                  </div>
                  <button type="button" className="convenios-btn-primario" onClick={() => revisarLote(s)}>Revisar lote</button>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {passo === 2 && (
        <>
          <h3 style={{ fontSize: 15, fontWeight: 600, marginBottom: 4 }}>Revisão — {convenio.convenioNome}</h3>
          <p style={{ fontSize: 13, color: 'var(--text-tertiary)', marginBottom: 16 }}>
            Todos os atendimentos vêm marcados. Desmarque o que não deve entrar neste lote.
          </p>
          {carregandoItens ? (
            <div className="convenios-vazio">Carregando…</div>
          ) : (
            <>
              <div className="convenios-tabela-scroll">
                <table className="convenios-tabela convenios-tabela-sub">
                  <thead>
                    <tr>
                      <th style={{ width: 36 }}>
                        <input type="checkbox" checked={selecionados.size === itens.length && itens.length > 0} onChange={toggleTodos} />
                      </th>
                      <th>Paciente</th>
                      <th>Data</th>
                      <th>Tipo</th>
                      <th className="num">Valor</th>
                      <th>Auditoria</th>
                    </tr>
                  </thead>
                  <tbody>
                    {itens.map((item) => (
                      <tr key={item.idFatura} onClick={() => toggleItem(item.idFatura)} style={{ cursor: 'pointer' }}>
                        <td onClick={(e) => e.stopPropagation()}>
                          <input type="checkbox" checked={selecionados.has(item.idFatura)} onChange={() => toggleItem(item.idFatura)} />
                        </td>
                        <td>{item.pacienteNome}</td>
                        <td>{item.dataAtendimento}</td>
                        <td>{item.tipo}</td>
                        <td className="num convenios-valor">{brl(item.valor)}</td>
                        <td>
                          {item.statusAuditoria === 'atencao'
                            ? <span className={`convenios-badge ${statusAuditoriaMeta('atencao').cls}`}>Atenção</span>
                            : item.statusAuditoria === 'aprovado'
                              ? <span className="convenios-badge st-ok">Aprovado</span>
                              : <span className="convenios-sub" style={{ padding: 0, border: 'none', fontSize: 12, color: 'var(--text-tertiary)' }}>não avaliado</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="convenios-foot">
                <span>{selecionados.size} de {itens.length} atendimentos selecionados</span>
                <span className="convenios-valor">{brl(valorSelecionado)}</span>
              </div>
              <div className="convenios-modal-footer">
                <button type="button" className="convenios-btn-ghost" onClick={() => setPasso(1)}>Voltar</button>
                <button type="button" className="convenios-btn-primario" disabled={selecionados.size === 0} onClick={() => setPasso(3)}>
                  Continuar
                </button>
              </div>
            </>
          )}
        </>
      )}

      {passo === 3 && (
        <>
          <h3 style={{ fontSize: 15, fontWeight: 600, marginBottom: 4 }}>Confirmar criação do lote</h3>
          <p style={{ fontSize: 13, color: 'var(--text-tertiary)', marginBottom: 16 }}>
            O lote é criado como rascunho — dá pra revisar de novo antes de marcar como enviado.
          </p>
          <div className="convenios-sub-form-grid" style={{ marginBottom: 8 }}>
            <div><div className="convenios-label">Convênio</div><div>{convenio.convenioNome}</div></div>
            <div><div className="convenios-label">Atendimentos</div><div>{selecionados.size}</div></div>
            <div><div className="convenios-label">Valor total</div><div className="convenios-valor">{brl(valorSelecionado)}</div></div>
          </div>
          <div className="convenios-modal-footer">
            <button type="button" className="convenios-btn-ghost" onClick={() => setPasso(2)} disabled={criando}>Voltar</button>
            <button type="button" className="convenios-btn-primario" onClick={confirmarCriacao} disabled={criando}>
              {criando ? 'Criando…' : 'Criar lote'}
            </button>
          </div>
        </>
      )}
    </div>
  )
}
