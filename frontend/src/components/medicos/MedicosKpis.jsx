import { CalendarCheck2, Clock, Hourglass, RotateCcw } from 'lucide-react'

function KpiHeader({ Icon, label, dicaKey, dicaAberta, onAbrir, onFechar, texto, align = 'left' }) {
  return (
    <div className="medicos-kpi-head" onMouseEnter={() => onAbrir(dicaKey)} onMouseLeave={onFechar}>
      <span className="medicos-kpi-icon"><Icon size={17} strokeWidth={1.6} /></span>
      <span className="medicos-kpi-label">{label}</span>
      {dicaAberta === dicaKey && (
        <div className={`medicos-tooltip medicos-tooltip-${align}`} role="tooltip">{texto}</div>
      )}
    </div>
  )
}

function Skeleton({ valorW, apoioLinhas }) {
  return (
    <>
      <div className="medicos-skel medicos-skel-valor" style={{ width: valorW }} />
      {apoioLinhas.map((w, i) => (
        <div key={i} className={`medicos-skel medicos-skel-linha${i === 0 ? ' shine' : ''}`} style={{ width: w }} />
      ))}
    </>
  )
}

function Vazio({ texto = 'Sem dados no período selecionado' }) {
  return (
    <>
      <div className="medicos-kpi-vazio-valor">—</div>
      <div className="medicos-kpi-vazio-texto">{texto}</div>
    </>
  )
}

export default function MedicosKpis({ carregando, vazio, dados, dica, onAbrirDica, onFecharDica }) {
  const semAmostraPontualidade = !carregando && !vazio && dados.pontualidadeAmostra === 0

  return (
    <div className="medicos-kpis">
      <div className="medicos-kpi-card">
        <KpiHeader
          Icon={CalendarCheck2} label="Ocupação da agenda" dicaKey="ocupacao" dicaAberta={dica}
          onAbrir={onAbrirDica} onFechar={onFecharDica}
          texto="Horários preenchidos ÷ horários abertos na agenda, somando todos os médicos do filtro."
        />
        {carregando ? <Skeleton valorW="116px" apoioLinhas={['100%', '150px']} /> : vazio ? <Vazio /> : (
          <>
            <div className="medicos-kpi-valor-row">
              <span className="medicos-kpi-valor">{dados.ocupacaoPct}</span>
            </div>
            <div className="medicos-kpi-barra">
              <div className="medicos-kpi-barra-fill" style={{ width: dados.ocupacaoBarraPct }} />
            </div>
            <div className="medicos-kpi-apoio-texto">{dados.ocupacaoApoio}</div>
          </>
        )}
      </div>

      <div className="medicos-kpi-card">
        <KpiHeader
          Icon={Clock} label="Pontualidade" dicaKey="pontualidade" dicaAberta={dica}
          onAbrir={onAbrirDica} onFechar={onFecharDica}
          texto="Consultas iniciadas com até 15 minutos de atraso sobre o horário marcado."
        />
        {carregando ? <Skeleton valorW="96px" apoioLinhas={['150px', '126px']} /> : vazio ? <Vazio />
          : semAmostraPontualidade ? <Vazio texto="Ainda sem consultas com horário de início registrado neste período" /> : (
            <>
              <div className="medicos-kpi-valor-row">
                <span className={`medicos-kpi-valor ${dados.pontualidadeCor}`}>{dados.pontualidadePct}</span>
              </div>
              <div className={`medicos-kpi-pill ${dados.pontualidadeCor}`}>
                <span className="medicos-kpi-pill-dot" />atraso médio {dados.atrasoMedioMin} min
              </div>
              <div className="medicos-kpi-apoio-texto">meta 85% · tolerância 15 min</div>
            </>
          )}
      </div>

      <div className="medicos-kpi-card">
        <KpiHeader
          Icon={Hourglass} label="Horas perdidas" dicaKey="horas" dicaAberta={dica}
          onAbrir={onAbrirDica} onFechar={onFecharDica}
          texto="Horas de agenda perdidas por cancelamento do médico com menos de 24h de antecedência do horário marcado."
        />
        {carregando ? <Skeleton valorW="84px" apoioLinhas={['180px', '164px']} /> : vazio ? <Vazio /> : (
          <>
            <div className="medicos-kpi-valor-row">
              <span className="medicos-kpi-valor">{dados.horasPerdidasTxt}</span>
            </div>
            <div className="medicos-kpi-apoio-texto">cancelamentos com menos de 24h de antecedência</div>
          </>
        )}
      </div>

      <div className="medicos-kpi-card">
        <KpiHeader
          Icon={RotateCcw} label="Taxa de retorno" dicaKey="retorno" dicaAberta={dica}
          onAbrir={onAbrirDica} onFechar={onFecharDica} align="right"
          texto="Pacientes com mais de uma consulta com o mesmo médico, sobre o total de pacientes atendidos por ele."
        />
        {carregando ? <Skeleton valorW="96px" apoioLinhas={['186px', '140px']} /> : vazio ? <Vazio /> : (
          <>
            <div className="medicos-kpi-valor-row">
              <span className="medicos-kpi-valor">{dados.retornoPct}</span>
            </div>
            <div className="medicos-kpi-apoio-texto">mesmo médico, considerando todo o histórico</div>
          </>
        )}
      </div>
    </div>
  )
}
