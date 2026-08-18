import { useEffect, useRef, useState } from 'react'
import { ChevronDown } from 'lucide-react'
import { CONVENIOS, PERIODOS, RESPONSAVEIS } from './conveniosData'

export default function ConveniosFiltros({
  periodo, onPeriodoChange, convenios, onConveniosChange, status, onStatusChange, responsavel, onResponsavelChange,
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
    <div className="convenios-head">
      <h1 className="convenios-titulo">Convênios</h1>

      <div className="convenios-filtros">
        <select className="convenios-sel" value={periodo} onChange={(e) => onPeriodoChange(e.target.value)} aria-label="Período">
          {PERIODOS.map((p) => <option key={p} value={p}>{p}</option>)}
        </select>

        <div className="convenios-conv" ref={popoverRef}>
          <button
            type="button" className="convenios-conv-trigger" onClick={() => setConvAberto((v) => !v)}
            aria-expanded={convAberto} aria-haspopup="listbox"
          >
            <span>{rotuloConv}</span>
            <ChevronDown size={13} strokeWidth={2} />
          </button>
          {convAberto && (
            <div className="convenios-conv-popover" role="listbox" aria-label="Convênios">
              {CONVENIOS.map((nome) => (
                <label key={nome} className="convenios-conv-item">
                  <input
                    type="checkbox" className="convenios-conv-checkbox"
                    checked={convenios.includes(nome)} onChange={() => toggleConv(nome)}
                  />
                  <span className="convenios-conv-nome">{nome}</span>
                </label>
              ))}
              <div className="convenios-conv-footer">
                <button type="button" className="convenios-link-btn" onClick={() => onConveniosChange([])}>Limpar seleção</button>
              </div>
            </div>
          )}
        </div>

        <select className="convenios-sel" value={status} onChange={(e) => onStatusChange(e.target.value)} aria-label="Status da glosa">
          <option value="">Status: todos</option>
          <option value="analisar">A analisar</option>
          <option value="recurso">Em recurso</option>
          <option value="recorrida">Recorrida</option>
          <option value="revertida">Revertida</option>
          <option value="perdida">Perdida</option>
        </select>

        <select className="convenios-sel" value={responsavel} onChange={(e) => onResponsavelChange(e.target.value)} aria-label="Responsável">
          <option value="">Responsável: todos</option>
          {RESPONSAVEIS.map((r) => <option key={r} value={r}>{r}</option>)}
        </select>
      </div>
    </div>
  )
}
