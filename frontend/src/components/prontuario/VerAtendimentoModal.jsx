import { useEffect, useState } from 'react'
import { fetchProntuarioDetalhe } from './api'

function Campo({ label, valor }) {
  return (
    <div className="prontuario-registro-secao">
      <div className="prontuario-registro-titulo">{label}</div>
      <div className={`prontuario-registro-texto${valor ? '' : ' vazio'}`}>{valor || 'Não preenchido'}</div>
    </div>
  )
}

export default function VerAtendimentoModal({ prontuarioId, onClose }) {
  const [dados, setDados] = useState(null)
  const [erro, setErro] = useState(null)

  useEffect(() => {
    fetchProntuarioDetalhe(prontuarioId).then(setDados).catch((err) => setErro(err.message))
  }, [prontuarioId])

  return (
    <div className="prontuario-overlay" onClick={onClose}>
      <div className="prontuario-modal" onClick={(e) => e.stopPropagation()}>
        {erro && <div className="prontuario-modal-erro">{erro}</div>}

        {!dados && !erro && <div className="prontuario-modal-subtitle">Carregando atendimento…</div>}

        {dados && (
          <>
            <div className="prontuario-modal-title">{dados.pacienteNome}</div>
            <div className="prontuario-modal-subtitle">
              {dados.dataTxt} às {dados.hora} · {dados.profissional} · {dados.tipo}
              {dados.finalizado ? ' · Finalizado' : ' · Rascunho'}
            </div>

            <div className="prontuario-modal-secao-titulo">Subjetivo</div>
            <Campo label="Queixa principal" valor={dados.queixaPrincipal} />
            <Campo label="Anamnese / história da doença atual" valor={dados.historiaDoencaAtual} />

            <div className="prontuario-modal-secao-titulo">Objetivo</div>
            <Campo label="Exame físico" valor={dados.exameFisico} />

            <div className="prontuario-modal-secao-titulo">Avaliação</div>
            <Campo label="Evolução" valor={dados.descricao} />
            <Campo label="Hipótese diagnóstica" valor={dados.hipoteseDiagnostica} />
            <Campo label={`Diagnóstico${dados.tipoDiagnostico ? ` (${dados.tipoDiagnostico.toLowerCase()})` : ''}`} valor={dados.diagnostico} />

            <div className="prontuario-modal-secao-titulo">Plano</div>
            <Campo label="Prescrição" valor={dados.prescricao} />
            <Campo label="Plano terapêutico" valor={dados.planoTerapeutico} />
            <Campo label="Conduta" valor={dados.conduta} />

            <div className="prontuario-modal-footer">
              <button type="button" className="prontuario-btn-ghost" onClick={onClose}>Fechar</button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
