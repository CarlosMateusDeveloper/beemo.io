import { Download, MessageCircle, SearchX } from 'lucide-react'

const COLUNAS = [
  { id: 'nome', label: 'Paciente', width: 240, sortable: true },
  { id: 'contato', label: 'Contato', width: 180, sortable: false },
  { id: 'convenio', label: 'Convênio', width: 140, sortable: false },
  { id: 'ultima', label: 'Última consulta', width: 160, sortable: true },
  { id: 'proxima', label: 'Próxima consulta', width: 150, sortable: true },
  { id: 'status', label: 'Status', width: 170, sortable: true },
]

const ESQUELETOS = [
  { a: '176px', b: '104px' }, { a: '150px', b: '126px' }, { a: '196px', b: '108px' },
  { a: '160px', b: '118px' }, { a: '182px', b: '96px' },
]

export default function PacientesTabela({
  carregando, linhas, ordem, direcao, onOrdenar, onAbrirPaciente,
  selecionados, onToggleSelecionado, onToggleTodos, onAcaoLote, footTexto,
}) {
  const listaVazia = !carregando && linhas.length === 0
  const todosSelecionados = linhas.length > 0 && selecionados.size === linhas.length

  return (
    <>
      {selecionados.size > 0 && (
        <div className="pacientes-painel-foot" style={{ borderTop: 'none', borderBottom: '1px solid var(--border-subtle)' }}>
          <span>{selecionados.size} {selecionados.size === 1 ? 'paciente selecionado' : 'pacientes selecionados'}</span>
          <div style={{ display: 'flex', gap: 8 }}>
            <button type="button" className="pacientes-link-btn" onClick={() => onAcaoLote('exportar')}>
              <Download size={13} strokeWidth={2} style={{ verticalAlign: -2, marginRight: 5 }} />Exportar
            </button>
            <button type="button" className="pacientes-link-btn" onClick={() => onAcaoLote('recontato')}>
              <MessageCircle size={13} strokeWidth={2} style={{ verticalAlign: -2, marginRight: 5 }} />Enviar mensagem de recontato
            </button>
          </div>
        </div>
      )}

      {carregando && (
        <div>
          {ESQUELETOS.map((s, i) => (
            <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '14px 20px', borderBottom: '1px solid var(--border-faint)' }}>
              <div className="pacientes-skel shine" style={{ width: 30, height: 30, borderRadius: '50%', flex: 'none' }} />
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 7 }}>
                <div className="pacientes-skel shine" style={{ height: 12, width: s.a }} />
                <div className="pacientes-skel" style={{ height: 12, width: s.b }} />
              </div>
              <div className="pacientes-skel" style={{ height: 12, width: 96 }} />
              <div className="pacientes-skel" style={{ height: 12, width: 110 }} />
              <div className="pacientes-skel" style={{ height: 12, width: 90 }} />
            </div>
          ))}
        </div>
      )}

      {listaVazia && (
        <div className="pacientes-vazio">
          <SearchX size={22} strokeWidth={1.6} style={{ color: 'var(--text-tertiary)', marginBottom: 10 }} />
          <p>Nenhum paciente encontrado</p>
          <span>Ajuste a busca ou limpe os filtros ativos.</span>
        </div>
      )}

      {!carregando && !listaVazia && (
        <>
          <div className="pacientes-tabela-scroll">
            <table className="pacientes-tabela">
              <thead>
                <tr>
                  <th style={{ width: 36 }}>
                    <input
                      type="checkbox" aria-label="Selecionar todos" checked={todosSelecionados}
                      onChange={onToggleTodos}
                    />
                  </th>
                  {COLUNAS.map((c) => (
                    <th
                      key={c.id} style={{ width: c.width }}
                      className={c.sortable ? 'sortable' : ''}
                      onClick={c.sortable ? () => onOrdenar(c.id) : undefined}
                    >
                      {c.label}
                      {c.sortable && (
                        <span className="pacientes-th-seta" style={{ opacity: ordem === c.id ? 1 : 0 }}>
                          {ordem === c.id && direcao === 'asc' ? '▲' : '▼'}
                        </span>
                      )}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {linhas.map((p) => (
                  <tr key={p.id}>
                    <td onClick={(e) => e.stopPropagation()}>
                      <input
                        type="checkbox" aria-label={`Selecionar ${p.nome}`} checked={selecionados.has(p.id)}
                        onChange={() => onToggleSelecionado(p.id)}
                      />
                    </td>
                    <td onClick={() => onAbrirPaciente(p.nome)}>
                      <div className="pacientes-quem">
                        <span className="pacientes-avatar">{p.iniciais}</span>
                        <span>
                          <div className="pacientes-quem-nome">{p.nome}</div>
                          <div className="pacientes-quem-meta">{p.idade} anos · {p.sexo}</div>
                        </span>
                      </div>
                    </td>
                    <td onClick={() => onAbrirPaciente(p.nome)} className="pacientes-mono">
                      {p.whatsapp ? (
                        <span className="pacientes-wa">
                          <MessageCircle size={13} strokeWidth={2} />{p.telefone}
                        </span>
                      ) : p.telefone}
                    </td>
                    <td onClick={() => onAbrirPaciente(p.nome)}>
                      {p.convenio === 'Particular'
                        ? <span className="pacientes-badge pacientes-badge-part">Particular</span>
                        : p.convenio}
                    </td>
                    <td onClick={() => onAbrirPaciente(p.nome)}>
                      {p.ultimaTxt}<br /><span className="pacientes-sub">{p.ultimaEspecialidade}</span>
                    </td>
                    <td onClick={() => onAbrirPaciente(p.nome)} className={p.proximaTxt === '—' ? 'pacientes-dim' : ''}>
                      {p.proximaTxt}
                    </td>
                    <td onClick={() => onAbrirPaciente(p.nome)}>
                      <span className={`pacientes-badge pacientes-badge-${p.status}`}>
                        <span className="pacientes-dot" />{p.statusTxt}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="pacientes-painel-foot">
            <span>{footTexto}</span>
            <div className="pacientes-pager">
              <button type="button" aria-current="page">1</button>
              <button type="button">2</button>
              <button type="button">3</button>
              <button type="button">›</button>
            </div>
          </div>
        </>
      )}
    </>
  )
}
