import { PERIODOS, STATUS_GLOSA } from './conveniosData'

export default function ConveniosFiltros({
  periodo, onPeriodoChange, convenioId, onConvenioIdChange, convenios,
  statusGlosa, onStatusGlosaChange, responsavelId, onResponsavelIdChange,
  usuarios = [], mostrarFiltrosGlosa = false,
}) {
  return (
    <div className="convenios-head">
      <h1 className="convenios-titulo">Convênios</h1>

      <div className="convenios-filtros">
        <select className="convenios-sel" value={periodo} onChange={(e) => onPeriodoChange(e.target.value)} aria-label="Período">
          {PERIODOS.map((p) => <option key={p.valor} value={p.valor}>{p.rotulo}</option>)}
        </select>

        <select
          className="convenios-sel" value={convenioId}
          onChange={(e) => onConvenioIdChange(e.target.value)} aria-label="Convênio"
        >
          <option value="">Todos os convênios</option>
          {convenios.map((c) => <option key={c.id} value={c.id}>{c.nome}</option>)}
        </select>

        <select
          className="convenios-sel" aria-label="Status da glosa"
          disabled={!mostrarFiltrosGlosa} title={mostrarFiltrosGlosa ? undefined : 'Disponível na aba Glosas'}
          value={statusGlosa ?? ''} onChange={(e) => onStatusGlosaChange?.(e.target.value)}
        >
          <option value="">Status: todos</option>
          {STATUS_GLOSA.map((s) => <option key={s.valor} value={s.valor}>{s.rotulo}</option>)}
        </select>

        <select
          className="convenios-sel" aria-label="Responsável"
          disabled={!mostrarFiltrosGlosa} title={mostrarFiltrosGlosa ? undefined : 'Disponível na aba Glosas'}
          value={responsavelId ?? ''} onChange={(e) => onResponsavelIdChange?.(e.target.value)}
        >
          <option value="">Responsável: todos</option>
          {usuarios.map((u) => <option key={u.id} value={u.id}>{u.nome}</option>)}
        </select>
      </div>
    </div>
  )
}
