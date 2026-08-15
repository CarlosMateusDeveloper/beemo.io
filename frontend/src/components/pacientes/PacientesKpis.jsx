import { AlertTriangle, FileWarning, UserPlus, Users } from 'lucide-react'

function Skeleton() {
  return (
    <>
      <div className="pacientes-skel shine" style={{ height: 28, width: '84px', marginTop: 4 }} />
      <div className="pacientes-skel" style={{ height: 11, width: '150px', marginTop: 10 }} />
    </>
  )
}

export default function PacientesKpis({ carregando, filtro, onFiltro, dados }) {
  return (
    <div className="pacientes-kpis">
      <div className="pacientes-kpi-card">
        <div className="pacientes-kpi-lab"><span className="pacientes-kpi-icon"><Users size={15} strokeWidth={1.7} /></span>Base ativa</div>
        {carregando ? <Skeleton /> : (
          <>
            <div className="pacientes-kpi-val pacientes-mono">{dados.baseAtivaTxt}</div>
            <div className="pacientes-kpi-sub">{dados.baseApoio}</div>
          </>
        )}
      </div>

      <div className="pacientes-kpi-card">
        <div className="pacientes-kpi-lab"><span className="pacientes-kpi-icon"><UserPlus size={15} strokeWidth={1.7} /></span>Novos pacientes</div>
        {carregando ? <Skeleton /> : (
          <>
            <div className="pacientes-kpi-val pacientes-mono">{dados.novosTxt}</div>
            <div className="pacientes-kpi-sub up">{dados.novosApoio}</div>
          </>
        )}
      </div>

      <button
        type="button" className="pacientes-kpi-card" aria-pressed={filtro === 'risk'}
        onClick={() => onFiltro('risk')}
      >
        <div className="pacientes-kpi-lab"><span className="pacientes-kpi-icon"><AlertTriangle size={15} strokeWidth={1.7} /></span>Em risco de abandono</div>
        {carregando ? <Skeleton /> : (
          <>
            <div className={`pacientes-kpi-val pacientes-mono ${dados.riscoCor}`}>{dados.riscoTxt}</div>
            <div className={`pacientes-kpi-sub ${dados.riscoCor === 'danger' ? 'danger' : 'warn'}`}>{dados.riscoApoio}</div>
            <div className="pacientes-kpi-hint">Ver lista de recontato →</div>
          </>
        )}
      </button>

      <button
        type="button" className="pacientes-kpi-card" aria-pressed={filtro === 'inc'}
        onClick={() => onFiltro('inc')}
      >
        <div className="pacientes-kpi-lab"><span className="pacientes-kpi-icon"><FileWarning size={15} strokeWidth={1.7} /></span>Cadastros incompletos</div>
        {carregando ? <Skeleton /> : (
          <>
            <div className="pacientes-kpi-val pacientes-mono">{dados.incTxt}</div>
            <div className="pacientes-kpi-sub">{dados.incApoio}</div>
            <div className="pacientes-kpi-hint">Ver pendências →</div>
          </>
        )}
      </button>
    </div>
  )
}
