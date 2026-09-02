import { CalendarX2 } from 'lucide-react'

const STATUS_CLS = { Agendada: 'neutral', Confirmada: 'acc', 'Em Espera': 'warning' }

function SkeletonRows() {
  return (
    <div className="dashboard-ranking-skel">
      {[0, 1, 2].map((i) => <div key={i} className="dashboard-skel dashboard-skel-linha shine" />)}
    </div>
  )
}

// Issue #2: "atalho/indicador visual para a fila de atendimento" e "lista das
// próximas consultas" — independente do período do filtro (sempre hoje, a
// partir de agora), vem de DashboardResponse.hoje.proximas.
export default function DashboardProximas({ carregando, proximas }) {
  return (
    <section className="dashboard-card" aria-label="Próximas consultas de hoje">
      <div className="dashboard-card-head">
        <span className="dashboard-card-titulo">Próximas consultas de hoje</span>
      </div>

      {carregando ? <SkeletonRows /> : proximas.length === 0 ? (
        <div className="dashboard-chart-vazio small">
          <CalendarX2 size={18} strokeWidth={1.6} style={{ marginBottom: 6, color: 'var(--text-tertiary)' }} />
          <div>Nenhuma consulta agendada a partir de agora</div>
        </div>
      ) : (
        <div className="dashboard-tabela-scroll">
          <table className="dashboard-tabela">
            <thead>
              <tr>
                <th>Horário</th>
                <th>Paciente</th>
                <th>Profissional</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {proximas.map((p) => (
                <tr key={p.idConsulta}>
                  <td className="dashboard-mono">{p.hora}</td>
                  <td>{p.paciente}</td>
                  <td>{p.medico}</td>
                  <td className={STATUS_CLS[p.status] ?? 'neutral'}>{p.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
