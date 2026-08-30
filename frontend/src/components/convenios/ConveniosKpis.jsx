import { AlertTriangle, FileX, Undo2, Wallet } from 'lucide-react'
import { brl } from './conveniosData'

const VAZIO = {
  aReceber: { valor: 0, lotes: 0, mediaDias: 0 },
  emRisco: { valor: 0, atendimentosPendentes: 0 },
  glosado: { valor: 0, quantidadeGlosas: 0 },
  recuperado: { valor: 0, quantidadeRecursos: 0 },
}

export default function ConveniosKpis({ kpis, carregando }) {
  const k = kpis ?? VAZIO

  return (
    <div className="convenios-kpis">
      <div className="convenios-kpi">
        <div className="convenios-kpi-lab"><span>A receber</span><Wallet size={16} strokeWidth={1.7} /></div>
        <div className="convenios-kpi-val">{carregando ? '—' : brl(k.aReceber.valor)}</div>
        <div className="convenios-kpi-sub">{k.aReceber.lotes} lotes · média {k.aReceber.mediaDias} dias</div>
      </div>

      <div className="convenios-kpi">
        <div className="convenios-kpi-lab"><span>Em risco</span><AlertTriangle size={16} strokeWidth={1.7} /></div>
        <div className="convenios-kpi-val warn">{carregando ? '—' : brl(k.emRisco.valor)}</div>
        <div className="convenios-kpi-sub">{k.emRisco.atendimentosPendentes} atendimentos pendentes de auditoria</div>
      </div>

      <div className="convenios-kpi">
        <div className="convenios-kpi-lab"><span>Glosado</span><FileX size={16} strokeWidth={1.7} /></div>
        <div className="convenios-kpi-val">{carregando ? '—' : brl(k.glosado.valor)}</div>
        <div className="convenios-kpi-sub">{k.glosado.quantidadeGlosas} glosas no período</div>
      </div>

      <div className="convenios-kpi">
        <div className="convenios-kpi-lab"><span>Recuperado</span><Undo2 size={16} strokeWidth={1.7} /></div>
        <div className="convenios-kpi-val ok">{carregando ? '—' : brl(k.recuperado.valor)}</div>
        <div className="convenios-kpi-sub ok">{k.recuperado.quantidadeRecursos} recursos revertidos</div>
      </div>
    </div>
  )
}
