import { FileText, UserPlus, X } from 'lucide-react'
import { brl, statusMeta } from './conveniosData'

function prazoClasse(dias) {
  if (dias == null) return 'p-off'
  if (dias <= 5) return 'p-red'
  if (dias < 15) return 'p-amber'
  return 'p-neutral'
}

function iniciais(nome) {
  return nome.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase()
}

export default function ConveniosTabela({
  linhas, total, selecionados, onToggle, onToggleTodos, onLimparSelecao, onAcaoLote, onAbrirGlosa,
}) {
  const n = selecionados.size
  const todasSelecionadas = linhas.length > 0 && n === linhas.length

  return (
    <div className="convenios-painel">
      {n > 0 && (
        <div className="convenios-bulk">
          <span className="convenios-bulk-txt">{n === 1 ? '1 glosa selecionada' : `${n} glosas selecionadas`}</span>
          <div className="convenios-bulk-actions">
            <button type="button" className="convenios-bbtn primary" onClick={() => onAcaoLote('recurso')}>
              <FileText size={13} strokeWidth={2} style={{ marginRight: 6, verticalAlign: -2 }} />Montar recurso em lote
            </button>
            <button type="button" className="convenios-bbtn ghost" onClick={() => onAcaoLote('responsavel')}>
              <UserPlus size={13} strokeWidth={2} style={{ marginRight: 6, verticalAlign: -2 }} />Atribuir responsável
            </button>
            <button type="button" className="convenios-bclear" onClick={onLimparSelecao} aria-label="Limpar seleção">
              <X size={15} strokeWidth={2} />
            </button>
          </div>
        </div>
      )}

      <div className="convenios-tabela-scroll">
        <table className="convenios-tabela">
          <thead>
            <tr>
              <th style={{ width: 36 }}>
                <input type="checkbox" className="convenios-chk" checked={todasSelecionadas} onChange={onToggleTodos} />
              </th>
              <th style={{ width: '24%' }}>Paciente / procedimento</th>
              <th>Convênio</th>
              <th>Motivo da glosa</th>
              <th className="num">Valor</th>
              <th>Prazo</th>
              <th>Status</th>
              <th>Responsável</th>
            </tr>
          </thead>
          <tbody>
            {linhas.map((g) => {
              const status = statusMeta(g.status)
              return (
                <tr key={g.id} className={g.status === 'perdida' ? 'perdida' : ''} onClick={() => onAbrirGlosa(g)}>
                  <td onClick={(e) => e.stopPropagation()}>
                    <input
                      type="checkbox" className="convenios-chk"
                      checked={selecionados.has(g.id)} onChange={() => onToggle(g.id)}
                    />
                  </td>
                  <td>
                    <div className="convenios-pac">{g.paciente}</div>
                    <div className="convenios-proc">{g.procedimento} · atend. {g.dataAtend}</div>
                  </td>
                  <td>
                    <div className="convenios-conv-cell">
                      <span className="convenios-conv-sigla">{g.convenioSigla}</span>{g.convenio}
                    </div>
                  </td>
                  <td>
                    <div className="convenios-motivo">
                      <span className="convenios-motivo-cod">{g.motivoCodigo}</span>
                      <span className="convenios-motivo-txt">{g.motivo}</span>
                    </div>
                  </td>
                  <td className="num convenios-valor">{brl(g.valor)}</td>
                  <td>
                    {g.prazoDias == null ? (
                      <>
                        <div className="convenios-prazo-val p-off">prazo expirado</div>
                        <div className="convenios-prazo-sub">em {g.venceTxt}</div>
                      </>
                    ) : (
                      <>
                        <div className={`convenios-prazo-val ${prazoClasse(g.prazoDias)}`}>{g.prazoDias} {g.prazoDias === 1 ? 'dia' : 'dias'}</div>
                        <div className="convenios-prazo-sub">vence {g.venceTxt}</div>
                      </>
                    )}
                  </td>
                  <td><span className={`convenios-badge ${status.cls}`}>{status.label}</span></td>
                  <td>
                    {g.responsavel ? (
                      <div className="convenios-resp">
                        <span className="convenios-resp-av">{iniciais(g.responsavel)}</span>{g.responsavel}
                      </div>
                    ) : (
                      <div className="convenios-resp-vazio">
                        <span className="convenios-resp-vazio-av" />Atribuir
                      </div>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      <div className="convenios-foot">
        <span>Exibindo {linhas.length} de {total} glosas · ordenado por prazo crescente</span>
      </div>
    </div>
  )
}
