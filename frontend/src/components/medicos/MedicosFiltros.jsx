import { useEffect, useMemo, useRef, useState } from 'react'
import { ChevronDown } from 'lucide-react'
import { ESPECIALIDADES, MEDICOS, PERIODOS } from './medicosData'

export default function MedicosFiltros({
  periodo, onPeriodoChange, status, onStatusChange, especialidades, onEspecialidadesChange,
}) {
  const [espAberto, setEspAberto] = useState(false)
  const popoverRef = useRef(null)

  useEffect(() => {
    if (!espAberto) return
    function onPointerDown(e) {
      if (popoverRef.current && !popoverRef.current.contains(e.target)) setEspAberto(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    return () => document.removeEventListener('mousedown', onPointerDown)
  }, [espAberto])

  const listaEsp = useMemo(() => ESPECIALIDADES.map((nome) => ({
    nome,
    selecionada: especialidades.includes(nome),
    total: MEDICOS.filter((m) => m.especialidade === nome && (status === 'Todos' || m.status !== 'desligado')).length,
  })), [especialidades, status])

  const rotuloEsp = especialidades.length === 0
    ? 'Todas as especialidades'
    : especialidades.length === 1
      ? especialidades[0]
      : `${especialidades.length} especialidades`

  function toggleEsp(nome) {
    onEspecialidadesChange(
      especialidades.includes(nome) ? especialidades.filter((e) => e !== nome) : [...especialidades, nome],
    )
  }

  return (
    <div className="medicos-filtros">
      <div className="medicos-seg" role="radiogroup" aria-label="Período">
        {PERIODOS.map((p) => (
          <label key={p} className={`medicos-seg-opt${periodo === p ? ' active' : ''}`}>
            <input
              type="radio" name="periodo" value={p} checked={periodo === p}
              onChange={() => onPeriodoChange(p)} className="medicos-sr-only"
            />
            {p}
          </label>
        ))}
      </div>

      <div className="medicos-esp" ref={popoverRef}>
        <button
          type="button" className="medicos-esp-trigger" onClick={() => setEspAberto((v) => !v)}
          aria-expanded={espAberto} aria-haspopup="listbox"
        >
          <span>{rotuloEsp}</span>
          <ChevronDown size={13} strokeWidth={2} />
        </button>
        {espAberto && (
          <div className="medicos-esp-popover" role="listbox" aria-label="Especialidades">
            {listaEsp.map((e) => (
              <label key={e.nome} className="medicos-esp-item">
                <input
                  type="checkbox" checked={e.selecionada} onChange={() => toggleEsp(e.nome)}
                  className="medicos-esp-checkbox"
                />
                <span className="medicos-esp-nome">{e.nome}</span>
                <span className="medicos-esp-contagem">{e.total}</span>
              </label>
            ))}
            <div className="medicos-esp-footer">
              <button type="button" className="medicos-link-btn" onClick={() => onEspecialidadesChange([])}>
                Limpar seleção
              </button>
            </div>
          </div>
        )}
      </div>

      <div className="medicos-seg" role="radiogroup" aria-label="Status">
        {['Ativos', 'Todos'].map((s) => (
          <label key={s} className={`medicos-seg-opt${status === s ? ' active' : ''}`}>
            <input
              type="radio" name="statusmed" value={s} checked={status === s}
              onChange={() => onStatusChange(s)} className="medicos-sr-only"
            />
            {s}
          </label>
        ))}
      </div>
    </div>
  )
}
