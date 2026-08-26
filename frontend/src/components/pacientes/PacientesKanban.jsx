import { useState } from 'react'

const ORDEM = ['agendado', 'confirmado', 'recepcao', 'atendimento', 'concluido']

function podeMover(de, para) {
  return Math.abs(ORDEM.indexOf(de) - ORDEM.indexOf(para)) === 1
}

function Card({ card, colId, index, onDragStart, onAbrirPaciente }) {
  let flag = null
  if (colId === 'recepcao' && card.esperaMin != null) {
    flag = { classe: card.esperaMin > 30 ? 'crit' : card.esperaMin > 15 ? 'warn' : null, texto: `espera ${card.esperaMin} min` }
  } else if (colId === 'concluido' && card.pendencia) {
    flag = { classe: 'pend', texto: card.pendencia }
  } else if (card.convenio) {
    flag = { classe: 'conv', texto: card.convenio }
  }

  return (
    <div
      className="pacientes-card" draggable
      onDragStart={(e) => onDragStart(e, colId, index)}
      onClick={() => onAbrirPaciente(card.idPaciente)}
    >
      <div className="pacientes-card-nome">{card.nome}</div>
      <div className="pacientes-card-meta"><span className="pacientes-mono">{card.hora}</span>·<span>{card.especialidade}</span></div>
      {flag && <span className={`pacientes-card-flag${flag.classe ? ` ${flag.classe}` : ''}`}>{flag.texto}</span>}
    </div>
  )
}

export default function PacientesKanban({ colunas, onMoverCard, onAbrirPaciente }) {
  const [arrastando, setArrastando] = useState(null)
  const [colunaSobre, setColunaSobre] = useState(null)

  function handleDragStart(e, colId, index) {
    setArrastando({ colId, index })
    e.dataTransfer.effectAllowed = 'move'
  }

  return (
    <div className="pacientes-board">
      {colunas.map((col) => {
        const aceita = arrastando && podeMover(arrastando.colId, col.id)
        return (
          <div
            key={col.id}
            className={`pacientes-col${colunaSobre === col.id && aceita ? ' over' : ''}`}
            onDragOver={(e) => { if (aceita) { e.preventDefault(); setColunaSobre(col.id) } }}
            onDragLeave={() => setColunaSobre((c) => (c === col.id ? null : c))}
            onDrop={(e) => {
              e.preventDefault()
              setColunaSobre(null)
              if (arrastando && aceita) onMoverCard(arrastando.colId, col.id, arrastando.index)
              setArrastando(null)
            }}
          >
            <div className="pacientes-col-head">
              <span className="pacientes-col-nome">{col.nome}</span>
              <span className="pacientes-col-contagem">{col.cards.length}</span>
            </div>
            {col.cards.map((card, i) => (
              <Card key={`${card.nome}-${i}`} card={card} colId={col.id} index={i} onDragStart={handleDragStart} onAbrirPaciente={onAbrirPaciente} />
            ))}
          </div>
        )
      })}
    </div>
  )
}
