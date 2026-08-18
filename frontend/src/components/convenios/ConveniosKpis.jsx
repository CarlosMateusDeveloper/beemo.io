import { AlarmClock, FileX, Undo2, Wallet } from 'lucide-react'
import { brl, KPI_BASE } from './conveniosData'

export default function ConveniosKpis({ filtroPrazo, onToggleFiltroPrazo }) {
  return (
    <div className="convenios-kpis">
      <div className="convenios-kpi">
        <div className="convenios-kpi-lab"><span>A receber</span><Wallet size={16} strokeWidth={1.7} /></div>
        <div className="convenios-kpi-val">{brl(KPI_BASE.aReceber)}</div>
        <div className="convenios-kpi-sub">{KPI_BASE.aReceberLotes} lotes · média {KPI_BASE.aReceberMediaDias} dias</div>
      </div>

      <div className="convenios-kpi">
        <div className="convenios-kpi-lab"><span>Glosado no mês</span><FileX size={16} strokeWidth={1.7} /></div>
        <div className="convenios-kpi-val">{brl(KPI_BASE.glosadoMes)}</div>
        <div className="convenios-kpi-sub warn">{KPI_BASE.glosadoPct.toString().replace('.', ',')}% do faturado</div>
      </div>

      <button
        type="button"
        className="convenios-kpi danger"
        aria-pressed={filtroPrazo}
        onClick={onToggleFiltroPrazo}
      >
        <div className="convenios-kpi-lab"><span>Prazo vencendo</span><AlarmClock size={16} strokeWidth={1.7} /></div>
        <div className="convenios-kpi-val">{brl(KPI_BASE.prazoVencendoValor)}</div>
        <div className="convenios-kpi-sub">{KPI_BASE.prazoVencendoQtd} recursos vencem em {KPI_BASE.prazoVencendoDias} dias</div>
        <div className="convenios-kpi-hint">{filtroPrazo ? 'Filtrando a fila →' : 'Ver fila com prazo crítico →'}</div>
      </button>

      <div className="convenios-kpi">
        <div className="convenios-kpi-lab"><span>Recuperado</span><Undo2 size={16} strokeWidth={1.7} /></div>
        <div className="convenios-kpi-val ok">{brl(KPI_BASE.recuperado)}</div>
        <div className="convenios-kpi-sub ok">{KPI_BASE.recuperadoTaxaPct}% dos recursos revertidos</div>
      </div>
    </div>
  )
}
