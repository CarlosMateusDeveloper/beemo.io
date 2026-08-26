import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Plus } from 'lucide-react'
import ProntuarioHistorico from './ProntuarioHistorico'
import ProntuarioResumo from './ProntuarioResumo'
import ProntuarioDocumentos from './ProntuarioDocumentos'
import VerAtendimentoModal from './VerAtendimentoModal'
import NovoAtendimentoModal from './NovoAtendimentoModal'
import EditarCadastroModal from './EditarCadastroModal'
import { fetchProntuarioDocumentos, fetchProntuarioPaciente } from './api'
import { iniciaisDe } from './prontuarioData'
import './prontuario.css'

const TABS = [
  { id: 'historico', label: 'Histórico' },
  { id: 'resumo', label: 'Resumo' },
  { id: 'documentos', label: 'Documentos' },
]

export function ProntuarioDetalhe() {
  const { pacienteId } = useParams()
  const navigate = useNavigate()

  const [tab, setTab] = useState('historico')
  const [dados, setDados] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)

  const [documentos, setDocumentos] = useState([])
  const [carregandoDocumentos, setCarregandoDocumentos] = useState(true)

  const [verProntuarioId, setVerProntuarioId] = useState(null)
  const [continuarProntuarioId, setContinuarProntuarioId] = useState(null)
  const [modalNovo, setModalNovo] = useState(false)
  const [modalEditarCadastro, setModalEditarCadastro] = useState(false)
  const [recarregarEm, setRecarregarEm] = useState(0)

  useEffect(() => {
    let cancelado = false
    setCarregando(true)
    setErro(null)
    fetchProntuarioPaciente(pacienteId)
      .then((d) => { if (!cancelado) setDados(d) })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [pacienteId, recarregarEm])

  useEffect(() => {
    let cancelado = false
    setCarregandoDocumentos(true)
    fetchProntuarioDocumentos(pacienteId)
      .then((d) => { if (!cancelado) setDocumentos(d) })
      .catch(() => { if (!cancelado) setDocumentos([]) })
      .finally(() => { if (!cancelado) setCarregandoDocumentos(false) })
    return () => { cancelado = true }
  }, [pacienteId, recarregarEm])

  if (erro) {
    return (
      <div className="prontuario-page">
        <button type="button" className="prontuario-detalhe-voltar" onClick={() => navigate(-1)}>
          <ArrowLeft size={14} strokeWidth={2} />Voltar
        </button>
        <div className="prontuario-erro">Não foi possível carregar o prontuário ({erro}).</div>
      </div>
    )
  }

  if (carregando || !dados) {
    return (
      <div className="prontuario-page">
        <div className="prontuario-skel prontuario-skel-linha shine" style={{ width: 240, height: 24, marginBottom: 20 }} />
        <div className="prontuario-skel prontuario-skel-linha" style={{ width: '100%', height: 200 }} />
      </div>
    )
  }

  return (
    <div className="prontuario-page">
      <button type="button" className="prontuario-detalhe-voltar" onClick={() => navigate('/prontuario')}>
        <ArrowLeft size={14} strokeWidth={2} />Voltar para prontuários
      </button>

      <div className="prontuario-detalhe-head">
        <div className="prontuario-detalhe-quem">
          <span className="prontuario-detalhe-avatar">{iniciaisDe(dados.nome)}</span>
          <div>
            <div className="prontuario-detalhe-nome">{dados.nome}</div>
            <div className="prontuario-detalhe-meta">{dados.idade} anos · CPF {dados.cpf} · {dados.telefone}</div>
          </div>
        </div>
        <button type="button" className="prontuario-btn-primario" onClick={() => setModalNovo(true)}>
          <Plus size={15} strokeWidth={2.2} />Novo atendimento
        </button>
      </div>

      <div className="prontuario-tabs" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.id} type="button" role="tab" aria-selected={tab === t.id}
            className={`prontuario-tab${tab === t.id ? ' active' : ''}`} onClick={() => setTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'historico' && (
        <ProntuarioHistorico
          atendimentos={dados.atendimentos}
          onVerAtendimento={setVerProntuarioId}
          onContinuarAtendimento={setContinuarProntuarioId}
        />
      )}
      {tab === 'resumo' && (
        <ProntuarioResumo
          cadastro={{ telefone: dados.telefone, email: dados.email, convenio: dados.convenio, endereco: dados.endereco }}
          alergias={dados.alergias} comorbidades={dados.comorbidades} medicamentos={dados.medicamentos}
          onEditarCadastro={() => setModalEditarCadastro(true)}
        />
      )}
      {tab === 'documentos' && (
        <ProntuarioDocumentos carregando={carregandoDocumentos} documentos={documentos} />
      )}

      {verProntuarioId && (
        <VerAtendimentoModal prontuarioId={verProntuarioId} onClose={() => setVerProntuarioId(null)} />
      )}

      {continuarProntuarioId && (
        <NovoAtendimentoModal
          pacienteId={Number(pacienteId)} pacienteNome={dados.nome} prontuarioIdExistente={continuarProntuarioId}
          onClose={() => setContinuarProntuarioId(null)}
          onSalvo={() => { setContinuarProntuarioId(null); setRecarregarEm((v) => v + 1) }}
        />
      )}

      {modalNovo && (
        <NovoAtendimentoModal
          pacienteId={Number(pacienteId)} pacienteNome={dados.nome}
          onClose={() => setModalNovo(false)}
          onSalvo={() => { setModalNovo(false); setRecarregarEm((v) => v + 1) }}
        />
      )}

      {modalEditarCadastro && (
        <EditarCadastroModal
          pacienteId={Number(pacienteId)}
          onClose={() => setModalEditarCadastro(false)}
          onSalvo={() => { setModalEditarCadastro(false); setRecarregarEm((v) => v + 1) }}
        />
      )}
    </div>
  )
}
