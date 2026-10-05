import { useEffect, useState } from 'react'
import { Info } from 'lucide-react'
import RetornoPendentes from './RetornoPendentes'
import RetornoReguas from './RetornoReguas'
import RetornoResultados from './RetornoResultados'
import PosVenda from './PosVenda'
import PosVendaIndicadores from './PosVendaIndicadores'
import { fetchResumo } from './api'
import { brl } from './retornoData'
import './retorno.css'

const ABAS = [
  { id: 'pos-venda', label: 'Pós-venda' },
  { id: 'indicadores-pos-venda', label: 'Indicadores de pós-venda' },
  { id: 'pendentes', label: 'Pendentes' },
  { id: 'reguas', label: 'Réguas' },
  { id: 'resultados', label: 'Resultados' },
]

export function Retorno() {
  const [aba, setAba] = useState('pos-venda')
  const [resumo, setResumo] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [recarregarSinal, setRecarregarSinal] = useState(0)

  useEffect(() => {
    let cancelado = false
    setCarregando(true)
    fetchResumo()
      .then((dados) => { if (!cancelado) setResumo(dados) })
      .catch(() => {})
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [recarregarSinal])

  function recarregar() {
    setRecarregarSinal((n) => n + 1)
  }

  return (
    <div className="retorno-page">
      <div className="retorno-head">
        {aba === 'pos-venda' || aba === 'indicadores-pos-venda' ? <div className="retorno-cabecalho">
          <span className="retorno-cabecalho-label">Relacionamento com o paciente</span>
          <span className="retorno-cabecalho-numero">Pós-venda</span>
        </div> : <div className="retorno-cabecalho">
          <span className="retorno-cabecalho-label">Deveriam ter voltado</span>
          <span className="retorno-cabecalho-numero">
            {carregando ? '—' : `${resumo?.totalPendentes ?? 0} pacientes`}
          </span>
          <span className="retorno-cabecalho-valor">
            {carregando ? '' : `${brl(resumo?.valorEstimado)} em consultas não agendadas`}
            {!carregando && (
              <span className="retorno-estimativa-tag" title="Estimativa: número de pacientes × ticket médio real da especialidade, calculado a partir do faturamento histórico.">
                <Info size={12} strokeWidth={2} />estimativa
              </span>
            )}
          </span>
        </div>}

        <nav className="retorno-tabs" aria-label="Seções de Retorno">
          {ABAS.map((t) => (
            <button
              key={t.id} type="button"
              className={`retorno-tab${aba === t.id ? ' active' : ''}`}
              onClick={() => setAba(t.id)}
            >
              {t.label}
            </button>
          ))}
        </nav>
      </div>

      {aba === 'pos-venda' && <PosVenda />}
      {aba === 'indicadores-pos-venda' && <PosVendaIndicadores />}
      {aba === 'pendentes' && (
        <RetornoPendentes grupos={resumo?.grupos ?? []} carregando={carregando} onAcaoConcluida={recarregar} />
      )}
      {aba === 'reguas' && <RetornoReguas />}
      {aba === 'resultados' && <RetornoResultados />}
    </div>
  )
}
