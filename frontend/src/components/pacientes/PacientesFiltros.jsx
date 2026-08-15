import { useEffect, useRef, useState } from 'react'
import { ChevronDown, Plus, Search } from 'lucide-react'
import { CONVENIOS, PERIODOS } from './pacientesData'

export default function PacientesFiltros({
  busca, onBuscaChange, periodo, onPeriodoChange, convenios, onConveniosChange,
  status, onStatusChange, onNovoPaciente,
}) {
  const [convAberto, setConvAberto] = useState(false)
  const popoverRef = useRef(null)

  useEffect(() => {
    if (!convAberto) return
    function onPointerDown(e) {
      if (popoverRef.current && !popoverRef.current.contains(e.target)) setConvAberto(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    return () => document.removeEventListener('mousedown', onPointerDown)
  }, [convAberto])

  const rotuloConv = convenios.length === 0
    ? 'Todos os convênios'
    : convenios.length === 1
      ? convenios[0]
      : `${convenios.length} convênios`

  function toggleConv(nome) {
    onConveniosChange(convenios.includes(nome) ? convenios.filter((c) => c !== nome) : [...convenios, nome])
  }

  return (
    <div className="pacientes-head">
      <h1 className="pacientes-titulo">Pacientes</h1>

      <div className="pacientes-filtros">
        <div className="pacientes-busca">
          <Search size={15} strokeWidth={2} />
          <input
            type="search" value={busca} onChange={(e) => onBuscaChange(e.target.value)}
            placeholder="Buscar por nome, CPF ou telefone" aria-label="Buscar paciente"
          />
        </div>

        <select className="pacientes-sel" value={periodo} onChange={(e) => onPeriodoChange(e.target.value)} aria-label="Período">
          {PERIODOS.map((p) => <option key={p} value={p}>{p}</option>)}
        </select>

        <div className="pacientes-conv" ref={popoverRef}>
          <button
            type="button" className="pacientes-conv-trigger" onClick={() => setConvAberto((v) => !v)}
            aria-expanded={convAberto} aria-haspopup="listbox"
          >
            <span>{rotuloConv}</span>
            <ChevronDown size={13} strokeWidth={2} />
          </button>
          {convAberto && (
            <div className="pacientes-conv-popover" role="listbox" aria-label="Convênios">
              {CONVENIOS.map((nome) => (
                <label key={nome} className="pacientes-conv-item">
                  <input
                    type="checkbox" className="pacientes-conv-checkbox"
                    checked={convenios.includes(nome)} onChange={() => toggleConv(nome)}
                  />
                  <span className="pacientes-conv-nome">{nome}</span>
                </label>
              ))}
              <div className="pacientes-conv-footer">
                <button type="button" className="pacientes-link-btn" onClick={() => onConveniosChange([])}>Limpar seleção</button>
              </div>
            </div>
          )}
        </div>

        <select className="pacientes-sel" value={status} onChange={(e) => onStatusChange(e.target.value)} aria-label="Status do cadastro">
          <option value="Ativos">Ativos</option>
          <option value="Todos">Todos</option>
        </select>

        <button type="button" className="pacientes-btn-primario" onClick={onNovoPaciente}>
          <Plus size={15} strokeWidth={2.2} />Novo paciente
        </button>
      </div>
    </div>
  )
}
