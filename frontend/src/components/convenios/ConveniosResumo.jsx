import { AlertTriangle } from 'lucide-react'
import { brl, CONVENIOS_RESUMO } from './conveniosData'

export default function ConveniosResumo({ onAbrirConvenio }) {
  return (
    <div className="convenios-painel">
      <div className="convenios-tabela-scroll">
        <table className="convenios-tabela convenios-tabela-resumo">
          <thead>
            <tr>
              <th style={{ width: '20%' }}>Convênio</th>
              <th className="num">Atendimentos</th>
              <th className="num">Faturado</th>
              <th className="num">Recebido</th>
              <th className="num">% de glosa</th>
              <th>Prazo de pagamento</th>
              <th className="num">Taxa de reversão</th>
            </tr>
          </thead>
          <tbody>
            {CONVENIOS_RESUMO.map((c) => (
              <tr key={c.convenio} onClick={() => onAbrirConvenio(c)}>
                <td>
                  <div className="convenios-conv-cell">
                    <span className="convenios-conv-sigla">{c.convenioSigla}</span>{c.convenio}
                  </div>
                  {c.tabelaDesatualizada && (
                    <div className="convenios-alerta-tabela">
                      <AlertTriangle size={11} strokeWidth={2} />Tabela de preços desatualizada
                    </div>
                  )}
                </td>
                <td className="num">{c.atendimentos}</td>
                <td className="num convenios-valor">{brl(c.faturado)}</td>
                <td className="num convenios-valor">{brl(c.recebido)}</td>
                <td className={`num convenios-valor ${c.glosaPct > 5 ? 'p-red' : ''}`}>{c.glosaPct.toString().replace('.', ',')}%</td>
                <td>
                  <span className={c.prazoRealDias > c.prazoContratadoDias ? 'convenios-prazo-val p-amber' : 'convenios-prazo-val p-neutral'}>
                    {c.prazoRealDias} dias
                  </span>
                  <div className="convenios-prazo-sub">contratado {c.prazoContratadoDias} dias</div>
                </td>
                <td className="num convenios-valor">{c.taxaReversaoPct}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="convenios-foot">
        <span>Exibindo {CONVENIOS_RESUMO.length} convênios ativos</span>
      </div>
    </div>
  )
}
