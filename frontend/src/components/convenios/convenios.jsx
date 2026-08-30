import { useEffect, useState } from 'react'
import ConveniosFiltros from './ConveniosFiltros'
import ConveniosKpis from './ConveniosKpis'
import ConveniosLista from './ConveniosLista'
import ConvenioDetalhe from './ConvenioDetalhe'
import ConveniosPlaceholder from './ConveniosPlaceholder'
import { fetchKpis } from './api'
import './convenios.css'

const ABAS = [
  { id: 'glosas', label: 'Glosas' },
  { id: 'auditoria', label: 'Auditoria' },
  { id: 'convenios', label: 'Convênios' },
  { id: 'lotes', label: 'Lotes' },
]

export function Convenios() {
  const [aba, setAba] = useState('convenios')
  const [periodo, setPeriodo] = useState('Últimos 30 dias')
  const [convenioId, setConvenioId] = useState('')
  const [convenios, setConvenios] = useState([])
  const [kpis, setKpis] = useState(null)
  const [carregandoKpis, setCarregandoKpis] = useState(true)
  const [convenioAbertoId, setConvenioAbertoId] = useState(null)
  const [recarregarSinal, setRecarregarSinal] = useState(0)

  useEffect(() => {
    let cancelado = false
    setCarregandoKpis(true)
    fetchKpis(periodo, convenioId || undefined)
      .then((dados) => { if (!cancelado) setKpis(dados) })
      .catch(() => {})
      .finally(() => { if (!cancelado) setCarregandoKpis(false) })
    return () => { cancelado = true }
  }, [periodo, convenioId, recarregarSinal])

  function abrirConvenio(id) {
    setConvenioAbertoId(id)
  }

  function fecharConvenio() {
    setConvenioAbertoId(null)
    setRecarregarSinal((n) => n + 1)
  }

  return (
    <div className="convenios-page">
      <ConveniosFiltros
        periodo={periodo} onPeriodoChange={setPeriodo}
        convenioId={convenioId} onConvenioIdChange={setConvenioId}
        convenios={convenios}
      />

      <ConveniosKpis kpis={kpis} carregando={carregandoKpis} />

      <div className="convenios-tabs">
        {ABAS.map((t) => (
          <button
            key={t.id} type="button"
            className={`convenios-tab ${aba === t.id ? 'active' : ''}`}
            onClick={() => setAba(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {aba === 'glosas' && (
        <ConveniosPlaceholder
          titulo="Glosas — em construção"
          texto="A fila de glosas, com prazos de recurso e ações em lote, entra numa próxima etapa."
        />
      )}

      {aba === 'auditoria' && (
        <ConveniosPlaceholder
          titulo="Auditoria — em construção"
          texto="O motor de regras já roda na configuração de cada convênio (aba Convênios); a esteira que aplica essas regras aos atendimentos antes do faturamento entra numa próxima etapa."
        />
      )}

      {aba === 'convenios' && (
        convenioAbertoId ? (
          <ConvenioDetalhe id={convenioAbertoId} onVoltar={fecharConvenio} onAtualizado={() => setRecarregarSinal((n) => n + 1)} />
        ) : (
          <ConveniosLista
            onAbrirConvenio={abrirConvenio}
            recarregarSinal={recarregarSinal}
            onListaAtualizada={(lista) => setConvenios(lista.map((c) => ({ id: c.id, nome: c.nome })))}
          />
        )
      )}

      {aba === 'lotes' && (
        <ConveniosPlaceholder
          titulo="Lotes — em construção"
          texto="Agrupamento de atendimentos aprovados na auditoria em remessas de faturamento entra numa próxima etapa."
        />
      )}
    </div>
  )
}
