import { iniciaisDe, statusLabel } from './prontuarioData'

const ESQUELETOS = ['82%', '68%', '74%', '60%', '70%', '55%']

function Skeleton() {
  return (
    <div>
      {ESQUELETOS.map((w, i) => (
        <div key={i} className="prontuario-skel-row">
          <div className="prontuario-skel prontuario-skel-avatar shine" />
          <div className="prontuario-skel prontuario-skel-linha shine" style={{ width: w, flex: 1 }} />
          <div className="prontuario-skel prontuario-skel-linha" style={{ width: 110 }} />
          <div className="prontuario-skel prontuario-skel-linha" style={{ width: 90 }} />
        </div>
      ))}
    </div>
  )
}

export default function ProntuarioTabela({ carregando, linhas, vazioGeral, onAbrirPaciente, onContinuarAtendimento }) {
  if (carregando) return <Skeleton />

  if (linhas.length === 0) {
    return (
      <div className="prontuario-vazio">
        <div className="prontuario-vazio-titulo">
          {vazioGeral ? 'Nenhum prontuário registrado ainda' : 'Nenhum resultado para essa busca'}
        </div>
        <div className="prontuario-vazio-texto">
          {vazioGeral
            ? 'Assim que o primeiro atendimento for documentado, ele aparece aqui.'
            : 'Ajuste a busca ou os filtros para encontrar o paciente.'}
        </div>
      </div>
    )
  }

  return (
    <div className="prontuario-tabela-scroll">
      <table className="prontuario-tabela">
        <thead>
          <tr>
            <th style={{ width: '32%' }}>Paciente</th>
            <th style={{ width: '18%' }}>Último atendimento</th>
            <th style={{ width: '22%' }}>Profissional</th>
            <th style={{ width: '14%' }}>Status</th>
            <th style={{ width: '14%', textAlign: 'right' }}>Ações</th>
          </tr>
        </thead>
        <tbody>
          {linhas.map((l) => (
            <tr key={l.pacienteId} onClick={() => onAbrirPaciente(l.pacienteId)}>
              <td>
                <div className="prontuario-quem">
                  <span className="prontuario-avatar">{iniciaisDe(l.nome)}</span>
                  <span>
                    <div className="prontuario-quem-nome">{l.nome}</div>
                    <div className="prontuario-quem-meta">{l.telefone}</div>
                  </span>
                </div>
              </td>
              <td className="prontuario-mono">{l.ultimaTxt}</td>
              <td>{l.profissional}</td>
              <td>
                <span className={`prontuario-badge prontuario-badge-${l.status}`}>
                  <span className="prontuario-dot" />{statusLabel(l.status)}
                </span>
              </td>
              <td>
                <div className="prontuario-acoes">
                  {l.status === 'pendente' && (
                    <button
                      type="button" className="prontuario-btn-secundario"
                      onClick={(e) => { e.stopPropagation(); onContinuarAtendimento(l) }}
                    >
                      Continuar
                    </button>
                  )}
                  <button
                    type="button" className="prontuario-btn-secundario"
                    onClick={(e) => { e.stopPropagation(); onAbrirPaciente(l.pacienteId) }}
                  >
                    Visualizar
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
