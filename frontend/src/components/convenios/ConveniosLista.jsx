import { useEffect, useState } from 'react'
import { Plus, SearchX } from 'lucide-react'
import { fetchListagem } from './api'
import NovoConvenioModal from './NovoConvenioModal'

export default function ConveniosLista({ onAbrirConvenio, recarregarSinal, onListaAtualizada }) {
  const [lista, setLista] = useState([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [modalAberto, setModalAberto] = useState(false)

  useEffect(() => {
    let cancelado = false
    setCarregando(true)
    fetchListagem()
      .then((dados) => {
        if (cancelado) return
        setLista(dados)
        setErro(null)
        onListaAtualizada?.(dados)
      })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [recarregarSinal])

  function aoCriar(convenio) {
    setModalAberto(false)
    setLista((prev) => [
      ...prev,
      { id: convenio.id, nome: convenio.nome, ativo: convenio.ativo, totalProcedimentos: 0, totalRegras: 0, ultimaAtualizacao: '—' },
    ])
    onAbrirConvenio(convenio.id)
  }

  return (
    <div className="convenios-painel">
      <div className="convenios-painel-head">
        <div className="convenios-painel-titulo">Convênios cadastrados <span>({lista.length})</span></div>
        <button type="button" className="convenios-btn-primario" onClick={() => setModalAberto(true)}>
          <Plus size={15} strokeWidth={2} />Novo convênio
        </button>
      </div>

      {erro && <div className="convenios-modal-erro" style={{ margin: '0 16px 16px' }}>{erro}</div>}

      {!carregando && lista.length === 0 && !erro ? (
        <div className="convenios-vazio">
          <span className="convenios-vazio-tile"><SearchX size={20} strokeWidth={1.6} /></span>
          <div className="convenios-vazio-titulo">Nenhum convênio cadastrado</div>
          <div className="convenios-vazio-texto">Cadastre o primeiro convênio para configurar planos, procedimentos e regras de auditoria.</div>
        </div>
      ) : (
        <div className="convenios-tabela-scroll">
          <table className="convenios-tabela convenios-tabela-lista">
            <thead>
              <tr>
                <th>Convênio</th>
                <th>Status</th>
                <th className="num">Procedimentos</th>
                <th className="num">Regras</th>
                <th>Última atualização</th>
              </tr>
            </thead>
            <tbody>
              {lista.map((c) => (
                <tr key={c.id} onClick={() => onAbrirConvenio(c.id)}>
                  <td>{c.nome}</td>
                  <td>
                    <span className={`convenios-badge ${c.ativo ? 'st-ok' : 'st-neutro'}`}>{c.ativo ? 'Ativo' : 'Inativo'}</span>
                  </td>
                  <td className="num">{c.totalProcedimentos}</td>
                  <td className="num">{c.totalRegras}</td>
                  <td>{c.ultimaAtualizacao}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modalAberto && <NovoConvenioModal onClose={() => setModalAberto(false)} onCriado={aoCriar} />}
    </div>
  )
}
