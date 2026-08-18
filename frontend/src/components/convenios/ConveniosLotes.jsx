import { brl, LOTES, statusLoteMeta } from './conveniosData'

export default function ConveniosLotes({ onAbrirLote }) {
  return (
    <div className="convenios-painel">
      <div className="convenios-tabela-scroll">
        <table className="convenios-tabela convenios-tabela-lotes">
          <thead>
            <tr>
              <th style={{ width: '16%' }}>Lote</th>
              <th>Convênio</th>
              <th>Data de envio</th>
              <th className="num">Guias</th>
              <th className="num">Valor enviado</th>
              <th className="num">Valor pago</th>
              <th className="num">Diferença</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {LOTES.map((l) => {
              const status = statusLoteMeta(l.status)
              const diferenca = l.valorPago == null ? null : l.valorPago - l.valorEnviado
              return (
                <tr key={l.id} onClick={() => onAbrirLote(l)}>
                  <td className="convenios-mono">{l.id}</td>
                  <td>
                    <div className="convenios-conv-cell">
                      <span className="convenios-conv-sigla">{l.convenioSigla}</span>{l.convenio}
                    </div>
                  </td>
                  <td>{l.dataEnvio}</td>
                  <td className="num">{l.guias}</td>
                  <td className="num convenios-valor">{brl(l.valorEnviado)}</td>
                  <td className="num convenios-valor">{l.valorPago == null ? '—' : brl(l.valorPago)}</td>
                  <td className={`num convenios-valor ${diferenca != null && diferenca < 0 ? 'p-red' : ''}`}>
                    {diferenca == null ? '—' : diferenca === 0 ? brl(0) : brl(diferenca)}
                  </td>
                  <td><span className={`convenios-badge ${status.cls}`}>{status.label}</span></td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      <div className="convenios-foot">
        <span>Exibindo {LOTES.length} lotes · pagamentos parciais podem indicar glosa não formalizada</span>
      </div>
    </div>
  )
}
