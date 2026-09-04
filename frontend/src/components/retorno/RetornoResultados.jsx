import { useEffect, useState } from 'react'
import { fetchResultados } from './api'
import { brl, rotuloGrupo } from './retornoData'

export default function RetornoResultados() {
  const [dados, setDados] = useState(null)
  const [carregando, setCarregando] = useState(true)

  useEffect(() => {
    fetchResultados().then(setDados).catch(() => {}).finally(() => setCarregando(false))
  }, [])

  if (carregando) {
    return <div className="retorno-painel"><div className="retorno-vazio"><span>Carregando…</span></div></div>
  }

  const vazio = !dados || dados.mensagensEnviadas === 0

  return (
    <>
      <div className="retorno-resultados-kpis">
        <div className="retorno-kpi">
          <div className="retorno-kpi-lab">Mensagens enviadas</div>
          <div className="retorno-kpi-val">{dados?.mensagensEnviadas ?? 0}</div>
        </div>
        <div className="retorno-kpi">
          <div className="retorno-kpi-lab">Voltaram a agendar</div>
          <div className="retorno-kpi-val ok">{dados?.voltaramAAgendar ?? 0}</div>
          <div className="retorno-kpi-sub">{dados?.conversaoPct ?? 0}% de conversão</div>
        </div>
        <div className="retorno-kpi">
          <div className="retorno-kpi-lab">Receita gerada</div>
          <div className="retorno-kpi-val ok">{brl(dados?.receitaGerada)}</div>
          <div className="retorno-kpi-sub">consultas efetivamente realizadas</div>
        </div>
      </div>

      {vazio ? (
        <div className="retorno-painel">
          <div className="retorno-vazio">
            <div className="retorno-vazio-titulo">Nenhuma mensagem enviada neste período</div>
            <div className="retorno-vazio-texto">Envie mensagens na aba Pendentes pra começar a ver resultados aqui.</div>
          </div>
        </div>
      ) : (
        <>
          <div className="retorno-painel" style={{ marginBottom: 16 }}>
            <div className="retorno-painel-head"><div className="retorno-painel-titulo">Conversão por grupo</div></div>
            <div className="retorno-tabela-scroll">
              <table className="retorno-tabela">
                <thead>
                  <tr><th>Grupo</th><th className="num">Enviadas</th><th className="num">Converteram</th><th className="num">Conversão</th></tr>
                </thead>
                <tbody>
                  {dados.conversaoPorGrupo.map((g) => (
                    <tr key={g.grupo}>
                      <td>{rotuloGrupo(g.grupo)}</td>
                      <td className="num">{g.enviadas}</td>
                      <td className="num">{g.converteram}</td>
                      <td className="num retorno-valor">{g.conversaoPct}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="retorno-painel" style={{ marginBottom: 16 }}>
            <div className="retorno-painel-head"><div className="retorno-painel-titulo">Evolução mensal</div></div>
            <div className="retorno-tabela-scroll">
              <table className="retorno-tabela">
                <thead>
                  <tr><th>Mês</th><th className="num">Enviadas</th><th className="num">Converteram</th><th className="num">Receita</th></tr>
                </thead>
                <tbody>
                  {dados.evolucaoMensal.map((m) => (
                    <tr key={m.mes}>
                      <td>{m.mes}</td>
                      <td className="num">{m.enviadas}</td>
                      <td className="num">{m.converteram}</td>
                      <td className="num retorno-valor">{brl(m.receita)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="retorno-painel">
            <div className="retorno-painel-head"><div className="retorno-painel-titulo">Histórico de envios</div></div>
            <div className="retorno-tabela-scroll">
              <table className="retorno-tabela">
                <thead>
                  <tr><th>Data</th><th>Grupo</th><th className="num">Quantidade</th><th>Disparado por</th></tr>
                </thead>
                <tbody>
                  {dados.historico.map((h, i) => (
                    <tr key={i}>
                      <td>{h.data}</td>
                      <td>{rotuloGrupo(h.grupo)}</td>
                      <td className="num">{h.quantidade}</td>
                      <td>{h.disparadoPor}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </>
  )
}
