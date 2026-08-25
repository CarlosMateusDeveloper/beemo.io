import { Plus, Search } from 'lucide-react'
import { PERIODOS } from './prontuarioData'

export default function ProntuarioFiltros({
  busca, onBuscaChange, idMedico, onIdMedicoChange, medicos,
  periodo, onPeriodoChange, status, onStatusChange, onNovoAtendimento,
}) {
  return (
    <div className="prontuario-head">
      <div className="prontuario-head-top">
        <div>
          <h1 className="prontuario-titulo">Prontuários</h1>
          <div className="prontuario-subtitulo">Consulte e gerencie os registros clínicos dos pacientes.</div>
        </div>
        <button type="button" className="prontuario-btn-primario" onClick={onNovoAtendimento}>
          <Plus size={15} strokeWidth={2.2} />Novo atendimento
        </button>
      </div>

      <div className="prontuario-filtros">
        <div className="prontuario-busca">
          <Search size={15} strokeWidth={2} />
          <input
            type="search" value={busca} onChange={(e) => onBuscaChange(e.target.value)}
            placeholder="Buscar paciente por nome, CPF ou telefone..." aria-label="Buscar paciente"
          />
        </div>

        <select
          className="prontuario-sel" value={idMedico} onChange={(e) => onIdMedicoChange(e.target.value)}
          aria-label="Profissional"
        >
          <option value="">Todos os profissionais</option>
          {medicos.map((m) => <option key={m.id} value={m.nome}>{m.nome}</option>)}
        </select>

        <select
          className="prontuario-sel" value={periodo} onChange={(e) => onPeriodoChange(e.target.value)}
          aria-label="Período do último atendimento"
        >
          {PERIODOS.map((p) => <option key={p} value={p}>{p}</option>)}
        </select>

        <select
          className="prontuario-sel" value={status} onChange={(e) => onStatusChange(e.target.value)}
          aria-label="Status do prontuário"
        >
          <option value="">Todos os status</option>
          <option value="finalizado">Finalizado</option>
          <option value="pendente">Pendente</option>
        </select>
      </div>
    </div>
  )
}
