const TIPO_ALERGIA_LABEL = { MEDICAMENTOSA: 'Medicamentosa', ALIMENTAR: 'Alimentar', AMBIENTAL: 'Ambiental', OUTRA: 'Outra' }

export default function ProntuarioResumo({ alergias, comorbidades, medicamentos }) {
  return (
    <div className="prontuario-resumo-grid">
      <div className="prontuario-resumo-card">
        <div className="prontuario-resumo-titulo">Alergias</div>
        {alergias.length === 0 ? (
          <div className="prontuario-resumo-vazio">Nenhuma alergia registrada.</div>
        ) : (
          <div className="prontuario-resumo-lista">
            {alergias.map((a, i) => (
              <div key={i}>
                <div className={`prontuario-resumo-item-nome prontuario-resumo-gravidade-${a.gravidade}`}>{a.substancia}</div>
                <div className="prontuario-resumo-item-sub">{TIPO_ALERGIA_LABEL[a.tipo] ?? a.tipo} · {a.gravidade.toLowerCase()}</div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="prontuario-resumo-card">
        <div className="prontuario-resumo-titulo">Comorbidades</div>
        {comorbidades.length === 0 ? (
          <div className="prontuario-resumo-vazio">Nenhuma comorbidade ativa.</div>
        ) : (
          <div className="prontuario-resumo-lista">
            {comorbidades.map((c, i) => (
              <div key={i}>
                <div className="prontuario-resumo-item-nome">{c.descricao}</div>
                {c.codigoCid && <div className="prontuario-resumo-item-sub">CID {c.codigoCid}</div>}
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="prontuario-resumo-card">
        <div className="prontuario-resumo-titulo">Medicamentos em uso contínuo</div>
        {medicamentos.length === 0 ? (
          <div className="prontuario-resumo-vazio">Nenhum medicamento em uso contínuo.</div>
        ) : (
          <div className="prontuario-resumo-lista">
            {medicamentos.map((m, i) => (
              <div key={i}>
                <div className="prontuario-resumo-item-nome">{m.medicamento}</div>
                <div className="prontuario-resumo-item-sub">{[m.dosagem, m.posologia].filter(Boolean).join(' · ') || '—'}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
