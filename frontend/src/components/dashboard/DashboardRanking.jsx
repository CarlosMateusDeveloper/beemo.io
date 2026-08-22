function SkeletonRows() {
  return (
    <div className="dashboard-ranking-skel">
      {[0, 1, 2, 3, 4].map((i) => <div key={i} className="dashboard-skel dashboard-skel-linha shine" />)}
    </div>
  )
}

export default function DashboardRanking({ carregando, empty, linhas }) {
  return (
    <section className="dashboard-card" aria-label="Ranking de profissionais">
      <div className="dashboard-card-head">
        <span className="dashboard-card-titulo">Ranking de profissionais</span>
      </div>

      {carregando ? <SkeletonRows /> : empty ? (
        <div className="dashboard-chart-vazio small"><div>Sem dados no período selecionado</div></div>
      ) : (
        <div className="dashboard-tabela-scroll">
          <table className="dashboard-tabela">
            <thead>
              <tr>
                <th>Profissional</th>
                <th className="num">Atend.</th>
                <th>Receita</th>
                <th className="num">No-show</th>
              </tr>
            </thead>
            <tbody>
              {linhas.map((r) => (
                <tr key={r.id}>
                  <td>
                    <div className="dashboard-rank-nome">
                      <span className="dashboard-rank-avatar">{r.iniciais}</span>
                      <span>{r.nome}</span>
                    </div>
                  </td>
                  <td className="num">{r.atendimentosTxt}</td>
                  <td>
                    <div className="dashboard-rank-receita">
                      <span>{r.receitaTxt}</span>
                      <span className="dashboard-rank-barra"><span style={{ width: r.barraPct }} /></span>
                    </div>
                  </td>
                  <td className={`num ${r.noShowCor}`}>{r.noShowTxt}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
