import { ChevronRight, Info, SearchX } from 'lucide-react'

const COLUNAS = [
  { id: 'nome', label: 'Médico', width: 236, align: 'left', sortable: true },
  { id: 'crm', label: 'CRM', width: 84, align: 'left', sortable: false },
  { id: 'atend', label: 'Atend.', width: 118, align: 'right', sortable: true },
  { id: 'liq', label: 'Receita bruta', width: 156, align: 'right', sortable: true },
  { id: 'ocup', label: 'Ocupação', width: 132, align: 'left', sortable: true },
  { id: 'noshow', label: 'No-show', width: 80, align: 'right', sortable: true },
  { id: 'atraso', label: 'Atraso', width: 94, align: 'right', sortable: true },
  { id: 'retorno', label: 'Retorno', width: 80, align: 'right', sortable: true },
  { id: 'proximo', label: 'Próximo horário', width: 150, align: 'left', sortable: false },
]

const ESQUELETOS = [
  { a: '168px', b: '118px' }, { a: '196px', b: '104px' }, { a: '150px', b: '126px' },
  { a: '182px', b: '112px' }, { a: '160px', b: '132px' }, { a: '190px', b: '108px' },
]

export default function MedicosTabela({
  carregando, listaVazia, vazioPorVolume, linhas, ordem, direcao, onOrdenar, manual, onLimparOrdem,
  rodape, ocultos, textoOcultos, onAbrirMedico, onDesativarVolume,
}) {
  return (
    <section className="medicos-lista-painel">
      {manual && (
        <div className="medicos-sort-bar">
          <button type="button" className="medicos-link-btn" onClick={onLimparOrdem}>
            Voltar à ordem alfabética
          </button>
        </div>
      )}

      {carregando && (
        <div>
          {ESQUELETOS.map((s, i) => (
            <div key={i} className="medicos-skel-row">
              <div className="medicos-skel medicos-skel-avatar shine" />
              <div className="medicos-skel-row-main">
                <div className="medicos-skel medicos-skel-linha shine" style={{ width: s.a }} />
                <div className="medicos-skel medicos-skel-linha" style={{ width: s.b }} />
              </div>
              <div className="medicos-skel medicos-skel-cell" style={{ width: '76px' }} />
              <div className="medicos-skel medicos-skel-cell" style={{ width: '112px' }} />
              <div className="medicos-skel medicos-skel-cell" style={{ width: '124px' }} />
              <div className="medicos-skel medicos-skel-cell" style={{ width: '64px' }} />
              <div className="medicos-skel medicos-skel-cell" style={{ width: '80px' }} />
              <div className="medicos-skel medicos-skel-cell" style={{ width: '104px' }} />
            </div>
          ))}
        </div>
      )}

      {!carregando && listaVazia && (
        <div className="medicos-vazio">
          <span className="medicos-vazio-tile"><SearchX size={20} strokeWidth={1.6} /></span>
          <div className="medicos-vazio-titulo">
            {vazioPorVolume ? 'Nenhum médico atinge o volume mínimo' : 'Sem dados no período selecionado'}
          </div>
          <div className="medicos-vazio-texto">
            {vazioPorVolume
              ? 'No período escolhido, nenhum médico chega a 10 atendimentos. Desative o filtro para ver todos.'
              : 'Ajuste o período, a especialidade ou o status para ver o corpo clínico.'}
          </div>
          {vazioPorVolume && (
            <button type="button" className="medicos-btn-secundario" onClick={onDesativarVolume}>
              Desativar filtro de volume mínimo
            </button>
          )}
        </div>
      )}

      {!carregando && !listaVazia && (
        <>
          <div className="medicos-tabela-scroll">
            <table className="medicos-tabela">
              <thead>
                <tr>
                  {COLUNAS.map((c) => (
                    <th
                      key={c.id} style={{ width: c.width, textAlign: c.align }}
                      className={c.sortable ? 'sortable' : ''}
                      onClick={c.sortable ? () => onOrdenar(c.id) : undefined}
                    >
                      {c.label}
                      {c.sortable && (
                        <span className="medicos-th-seta" style={{ opacity: ordem === c.id ? 1 : 0 }}>
                          {ordem === c.id && direcao === 'asc' ? '▲' : '▼'}
                        </span>
                      )}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {linhas.map((r) => (
                  <tr key={r.id} onClick={() => onAbrirMedico(r.nome)}>
                    <td>
                      <div className="medicos-row-medico">
                        <span className={`medicos-avatar${r.inativo ? ' inativo' : ''}`}>{r.iniciais}</span>
                        <span className="medicos-row-nome-col">
                          <span className="medicos-row-nome">{r.nome}</span>
                          <span className="medicos-row-sub">
                            {r.especialidade}
                            <span className={`medicos-badge medicos-badge-${r.status}`}>{r.statusRotulo}</span>
                          </span>
                        </span>
                      </div>
                    </td>
                    <td className="medicos-mono medicos-crm">{r.crm}</td>
                    <td style={{ textAlign: 'right' }}>
                      <div className="medicos-mono">{r.atendTxt}</div>
                      <div className="medicos-row-sub2">{r.divisaoTxt}</div>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <div className="medicos-mono">{r.liqTxt}</div>
                      {r.brutoNotaTxt && <div className="medicos-row-sub2">{r.brutoNotaTxt}</div>}
                    </td>
                    <td>
                      <div className="medicos-row-ocup">
                        <span className="medicos-mono medicos-ocup-valor">{r.ocupTxt}</span>
                        <span className="medicos-ocup-trilho">
                          <span
                            className={`medicos-ocup-fill${r.inativo ? ' inativo' : ''}`}
                            style={{ width: r.ocupBarraPct }}
                          />
                        </span>
                      </div>
                    </td>
                    <td className={`medicos-mono ${r.noShowAlerta ? 'danger' : ''}`} style={{ textAlign: 'right' }}>{r.noShowTxt}</td>
                    <td className={`medicos-mono ${r.atrasoAlerta ? 'warning' : ''}`} style={{ textAlign: 'right' }}>{r.atrasoTxt}</td>
                    <td className="medicos-mono" style={{ textAlign: 'right' }}>{r.retornoTxt}</td>
                    <td>
                      <div className="medicos-row-proximo">
                        <span className="medicos-row-proximo-col">
                          <span className={`medicos-mono medicos-proximo-valor${r.proximoVazio ? ' vazio' : ''}`}>{r.proximoTxt}</span>
                          <span className="medicos-row-sub2">{r.proximoNotaTxt}</span>
                        </span>
                        <span className="medicos-row-abrir">abrir <ChevronRight size={12} strokeWidth={2} /></span>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td className="medicos-rodape-titulo">{rodape.titulo}</td>
                  <td />
                  <td style={{ textAlign: 'right' }}>
                    <div className="medicos-mono">{rodape.totalAtend}</div>
                    <div className="medicos-row-sub2">{rodape.totalDivisao}</div>
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <div className="medicos-mono">{rodape.totalLiq}</div>
                  </td>
                  <td className="medicos-mono medicos-rodape-valor">{rodape.mediaOcup}</td>
                  <td className="medicos-mono medicos-rodape-valor" style={{ textAlign: 'right' }}>{rodape.mediaNoShow}</td>
                  <td className="medicos-mono medicos-rodape-valor" style={{ textAlign: 'right' }}>{rodape.mediaAtraso}</td>
                  <td className="medicos-mono medicos-rodape-valor" style={{ textAlign: 'right' }}>{rodape.mediaRetorno}</td>
                  <td />
                </tr>
              </tfoot>
            </table>
          </div>
          {ocultos > 0 && (
            <div className="medicos-nota-oculto">
              <Info size={14} strokeWidth={1.8} />
              {textoOcultos}
            </div>
          )}
        </>
      )}
    </section>
  )
}
