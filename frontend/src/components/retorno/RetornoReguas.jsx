import { useEffect, useState } from 'react'
import { fetchReguas, atualizarRegua } from './api'
import { rotuloGrupo } from './retornoData'

export default function RetornoReguas() {
  const [reguas, setReguas] = useState([])
  const [carregando, setCarregando] = useState(true)
  const [salvandoId, setSalvandoId] = useState(null)

  function carregar() {
    setCarregando(true)
    fetchReguas().then(setReguas).catch(() => {}).finally(() => setCarregando(false))
  }

  useEffect(carregar, [])

  async function alternarAtiva(regua) {
    setSalvandoId(regua.id)
    const atualizada = await atualizarRegua(regua.id, { ativa: !regua.ativa })
    setReguas((prev) => prev.map((r) => (r.id === regua.id ? atualizada : r)))
    setSalvandoId(null)
  }

  async function alterarPrazo(regua, prazoDias) {
    if (!prazoDias || prazoDias === regua.prazoDias) return
    setSalvandoId(regua.id)
    const atualizada = await atualizarRegua(regua.id, { prazoDias })
    setReguas((prev) => prev.map((r) => (r.id === regua.id ? atualizada : r)))
    setSalvandoId(null)
  }

  return (
    <div className="retorno-painel">
      <div className="retorno-painel-head">
        <div className="retorno-painel-titulo">Réguas de contato automático</div>
      </div>
      <div className="retorno-reguas-nota">
        Cada régua dispara uma mensagem quando o prazo vence. Ninguém marcado como "não contatar" entra
        nas réguas, e nenhum paciente recebe mais de uma mensagem automática a cada 30 dias.
      </div>

      {carregando ? (
        <div className="retorno-vazio"><span>Carregando…</span></div>
      ) : (
        <div className="retorno-tabela-scroll">
          <table className="retorno-tabela">
            <thead>
              <tr>
                <th>Régua</th>
                <th className="num">Prazo</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {reguas.map((r) => (
                <tr key={r.id}>
                  <td>{rotuloGrupo(r.grupo)} → mensagem com horários</td>
                  <td className="num">
                    <div className="retorno-prazo-editor">
                      <input
                        type="number" min={1} className="retorno-prazo-input" defaultValue={r.prazoDias}
                        disabled={salvandoId === r.id}
                        onBlur={(e) => alterarPrazo(r, Number(e.target.value))}
                      />
                      dias
                    </div>
                  </td>
                  <td>
                    <button
                      type="button"
                      className={`retorno-toggle${r.ativa ? ' on' : ''}`}
                      onClick={() => alternarAtiva(r)}
                      disabled={salvandoId === r.id}
                      aria-pressed={r.ativa}
                    >
                      <span className="retorno-toggle-bolinha" />
                    </button>
                    <span className="retorno-toggle-label">{r.ativa ? 'Ativa' : 'Pausada'}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
