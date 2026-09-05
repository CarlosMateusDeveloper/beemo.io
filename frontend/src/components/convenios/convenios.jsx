import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import ConveniosFiltros from './ConveniosFiltros'
import ConveniosKpis from './ConveniosKpis'
import ConveniosLista from './ConveniosLista'
import GlosasFila from './GlosasFila'
import AuditoriaLista from './AuditoriaLista'
import LotesLista from './LotesLista'
import { fetchKpis, fetchUsuarios } from './api'
import './convenios.css'

const ABAS = [
  { id: 'glosas', label: 'Glosas' },
  { id: 'auditoria', label: 'Auditoria' },
  { id: 'convenios', label: 'Convênios' },
  { id: 'lotes', label: 'Lotes' },
]

export function Convenios() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const aba = searchParams.get('aba') || 'glosas'
  const statusGlosa = searchParams.get('status') || ''
  const responsavelId = searchParams.get('responsavel') || ''

  const [periodo, setPeriodo] = useState('Últimos 30 dias')
  const [convenioId, setConvenioId] = useState('')
  const [convenios, setConvenios] = useState([])
  const [usuarios, setUsuarios] = useState([])
  const [kpis, setKpis] = useState(null)
  const [carregandoKpis, setCarregandoKpis] = useState(true)

  useEffect(() => {
    let cancelado = false
    setCarregandoKpis(true)
    fetchKpis(periodo, convenioId || undefined)
      .then((dados) => { if (!cancelado) setKpis(dados) })
      .catch(() => {})
      .finally(() => { if (!cancelado) setCarregandoKpis(false) })
    return () => { cancelado = true }
  }, [periodo, convenioId])

  useEffect(() => {
    fetchUsuarios().then(setUsuarios).catch(() => setUsuarios([]))
  }, [])

  function setAba(novaAba) {
    const proximo = new URLSearchParams(searchParams)
    proximo.set('aba', novaAba)
    setSearchParams(proximo)
  }

  function setStatusGlosa(valor) {
    const proximo = new URLSearchParams(searchParams)
    if (valor) proximo.set('status', valor); else proximo.delete('status')
    setSearchParams(proximo)
  }

  function setResponsavelId(valor) {
    const proximo = new URLSearchParams(searchParams)
    if (valor) proximo.set('responsavel', valor); else proximo.delete('responsavel')
    setSearchParams(proximo)
  }

  return (
    <div className="convenios-page">
      <ConveniosFiltros
        periodo={periodo} onPeriodoChange={setPeriodo}
        convenioId={convenioId} onConvenioIdChange={setConvenioId}
        convenios={convenios}
        statusGlosa={statusGlosa} onStatusGlosaChange={setStatusGlosa}
        responsavelId={responsavelId} onResponsavelIdChange={setResponsavelId}
        usuarios={usuarios} mostrarFiltrosGlosa={aba === 'glosas'}
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
        <GlosasFila
          statusGlosa={statusGlosa} idConvenio={convenioId || ''} idUsuarioResponsavel={responsavelId}
          usuarios={usuarios} searchParams={searchParams}
          onAbrirGlosa={(id, idsFila) => navigate(`/convenios/glosas/${id}`, { state: { idsFila, voltarPara: `/convenios?${searchParams.toString()}` } })}
        />
      )}

      {aba === 'auditoria' && <AuditoriaLista />}

      {aba === 'convenios' && (
        <ConveniosLista
          onAbrirConvenio={(id) => navigate(`/convenios/${id}`)}
          onListaAtualizada={(lista) => setConvenios(lista.map((c) => ({ id: c.id, nome: c.nome })))}
        />
      )}

      {aba === 'lotes' && (
        <LotesLista onAbrirLote={(id) => navigate(`/convenios/lotes/${id}`)} />
      )}
    </div>
  )
}
