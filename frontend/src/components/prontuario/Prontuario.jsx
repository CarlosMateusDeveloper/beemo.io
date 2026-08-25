import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import ProntuarioFiltros from './ProntuarioFiltros'
import ProntuarioTabela from './ProntuarioTabela'
import NovoAtendimentoModal from './NovoAtendimentoModal'
import { fetchMedicos, fetchProntuarioListagem } from './api'
import { dentroDoPeriodo } from './prontuarioData'
import './prontuario.css'

export function Prontuario() {
  const navigate = useNavigate()

  const [busca, setBusca] = useState('')
  const [idMedico, setIdMedico] = useState('')
  const [periodo, setPeriodo] = useState('Todos')
  const [status, setStatus] = useState('')

  const [linhasApi, setLinhasApi] = useState([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [medicos, setMedicos] = useState([])
  const [recarregarEm, setRecarregarEm] = useState(0)

  const [modal, setModal] = useState(null) // null | { pacienteId?, prontuarioId? }

  useEffect(() => {
    let cancelado = false
    setCarregando(true)
    setErro(null)
    fetchProntuarioListagem()
      .then((dados) => { if (!cancelado) setLinhasApi(dados) })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [recarregarEm])

  useEffect(() => {
    let cancelado = false
    fetchMedicos().then((dados) => { if (!cancelado) setMedicos(dados) }).catch(() => { /* filtro fica só com "Todos" se a lista falhar */ })
    return () => { cancelado = true }
  }, [])

  const linhas = useMemo(() => {
    const termo = busca.trim().toLowerCase()
    const termoDigitos = termo.replace(/\D/g, '')
    return linhasApi.filter((l) => {
      if (idMedico && l.profissional !== idMedico) return false
      if (status && l.status !== status) return false
      if (!dentroDoPeriodo(l.ultimaData, periodo)) return false
      if (termo) {
        const casaNome = l.nome.toLowerCase().includes(termo)
        const casaDocumento = termoDigitos && (l.cpf.includes(termoDigitos) || l.telefone.replace(/\D/g, '').includes(termoDigitos))
        if (!casaNome && !casaDocumento) return false
      }
      return true
    })
  }, [linhasApi, busca, idMedico, periodo, status])

  function abrirPaciente(pacienteId) {
    navigate(`/prontuario/${pacienteId}`)
  }

  function continuarAtendimento(linha) {
    setModal({ pacienteId: linha.pacienteId, prontuarioId: linha.prontuarioId })
  }

  return (
    <div className="prontuario-page">
      <ProntuarioFiltros
        busca={busca} onBuscaChange={setBusca}
        idMedico={idMedico} onIdMedicoChange={setIdMedico} medicos={medicos}
        periodo={periodo} onPeriodoChange={setPeriodo}
        status={status} onStatusChange={setStatus}
        onNovoAtendimento={() => setModal({})}
      />

      {erro && (
        <div className="prontuario-erro">
          Não foi possível carregar os prontuários ({erro}). Confirme se a API está rodando em{' '}
          {import.meta.env.VITE_API_URL || 'http://localhost:8080'}.
        </div>
      )}

      <div className="prontuario-painel">
        <ProntuarioTabela
          carregando={carregando} linhas={linhas} vazioGeral={!carregando && linhasApi.length === 0}
          onAbrirPaciente={abrirPaciente} onContinuarAtendimento={continuarAtendimento}
        />
      </div>

      {modal && (
        <NovoAtendimentoModal
          pacienteId={modal.pacienteId} prontuarioIdExistente={modal.prontuarioId}
          onClose={() => setModal(null)}
          onSalvo={() => { setModal(null); setRecarregarEm((v) => v + 1) }}
        />
      )}
    </div>
  )
}
