import { FileText } from 'lucide-react'

const ORIGEM_LABEL = { paciente: 'Enviado pelo paciente', clinica: 'Enviado pela clínica' }

function formatarData(iso) {
  return new Date(iso).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

export default function ProntuarioDocumentos({ carregando, documentos }) {
  if (carregando) return <div className="prontuario-resumo-vazio">Carregando documentos…</div>

  if (documentos.length === 0) {
    return (
      <div className="prontuario-vazio">
        <div className="prontuario-vazio-titulo">Nenhum documento anexado</div>
        <div className="prontuario-vazio-texto">Documentos enviados pelo paciente ou pela clínica aparecem aqui.</div>
      </div>
    )
  }

  return (
    <div>
      {documentos.map((d) => (
        <div key={d.id} className="prontuario-doc-item">
          <span className="prontuario-doc-icone"><FileText size={16} strokeWidth={1.8} /></span>
          <span>
            <div className="prontuario-doc-nome">{d.nomeArquivo}</div>
            <div className="prontuario-doc-meta">{formatarData(d.enviadoEm)} · {ORIGEM_LABEL[d.origem] ?? d.origem}</div>
          </span>
        </div>
      ))}
    </div>
  )
}
