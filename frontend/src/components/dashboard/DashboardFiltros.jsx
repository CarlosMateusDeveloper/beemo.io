import { PERIODOS } from './dashboardData'

export default function DashboardFiltros({ periodo, onPeriodoChange, profissionalId, onProfissionalChange, profissionais }) {
  return (
    <div className="dashboard-filtros">
      <div className="dashboard-seg" role="radiogroup" aria-label="Período">
        {PERIODOS.map((p) => (
          <label key={p} className={`dashboard-seg-opt${periodo === p ? ' active' : ''}`}>
            <input
              type="radio" name="dashboard-periodo" value={p} checked={periodo === p}
              onChange={() => onPeriodoChange(p)} className="dashboard-sr-only"
            />
            {p}
          </label>
        ))}
      </div>

      <select
        className="dashboard-sel"
        value={profissionalId}
        onChange={(e) => onProfissionalChange(e.target.value)}
        aria-label="Profissional"
      >
        <option value="todos">Todos os profissionais</option>
        {profissionais.map((p) => (
          <option key={p.id} value={p.id}>{p.nome}</option>
        ))}
      </select>
    </div>
  )
}
