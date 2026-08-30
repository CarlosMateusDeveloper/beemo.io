import { PERIODOS } from './conveniosData'

export default function ConveniosFiltros({
  periodo, onPeriodoChange, convenioId, onConvenioIdChange, convenios,
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

        <select className="convenios-sel" disabled title="Disponível quando a aba Glosas entrar em operação" aria-label="Status da glosa">
          <option>Status: todos</option>
        </select>

        <select className="convenios-sel" disabled title="Disponível quando a aba Glosas entrar em operação" aria-label="Responsável">
          <option>Responsável: todos</option>
        </select>
      </div>
    </div>
  )
}
