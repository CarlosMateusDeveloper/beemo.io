import { useEffect, useMemo, useState } from 'react'
import {
  atualizarProntuario, criarConsultaAgenda, criarProntuario,
  fetchMedicos, fetchPacientesParaSelecao, fetchProntuarioDetalhe,
} from './api'
import { agoraDatetimeLocal, iniciaisDe, separarDataHora, TIPOS_ATENDIMENTO } from './prontuarioData'

const FORM_VAZIO = {
  idMedico: '', tipo: 'Consulta', dataHora: agoraDatetimeLocal(),
  queixaPrincipal: '', historiaDoencaAtual: '', descricao: '', exameFisico: '',
  hipoteseDiagnostica: '', diagnostico: '', tipoDiagnostico: 'DEFINITIVO',
  prescricao: '', planoTerapeutico: '', conduta: '',
}

export default function NovoAtendimentoModal({ pacienteId, pacienteNome, prontuarioIdExistente, onClose, onSalvo }) {
  const continuando = !!prontuarioIdExistente

  const [etapa, setEtapa] = useState(pacienteId ? 'formulario' : 'selecionar-paciente')
  const [pacienteSelecionado, setPacienteSelecionado] = useState(
    pacienteId ? { id: pacienteId, nome: pacienteNome } : null,
  )
  const [buscaPaciente, setBuscaPaciente] = useState('')
  const [pacientesLista, setPacientesLista] = useState([])

  const [medicos, setMedicos] = useState([])
  const [form, setForm] = useState(FORM_VAZIO)
  const [consultaId, setConsultaId] = useState(null)
  const [prontuarioId, setProntuarioId] = useState(prontuarioIdExistente ?? null)
  const [agendaFixa, setAgendaFixa] = useState(null) // { profissional, tipo, dataTxt, hora } quando continuando

  const [carregandoInicial, setCarregandoInicial] = useState(continuando)
  const [salvando, setSalvando] = useState(null) // null | 'rascunho' | 'finalizar'
  const [erro, setErro] = useState(null)

  useEffect(() => {
    fetchMedicos().then(setMedicos).catch(() => { /* select fica vazio se falhar */ })
  }, [])

  useEffect(() => {
    if (etapa !== 'selecionar-paciente') return
    fetchPacientesParaSelecao().then(setPacientesLista).catch(() => { /* lista fica vazia se falhar */ })
  }, [etapa])

  useEffect(() => {
    if (!continuando) return
    fetchProntuarioDetalhe(prontuarioIdExistente)
      .then((d) => {
        setPacienteSelecionado({ id: d.pacienteId, nome: d.pacienteNome })
        setConsultaId(d.consultaId)
        setAgendaFixa({ profissional: d.profissional, tipo: d.tipo, dataTxt: d.dataTxt, hora: d.hora })
        setForm({
          idMedico: String(d.medicoId), tipo: d.tipo, dataHora: '',
          queixaPrincipal: d.queixaPrincipal ?? '', historiaDoencaAtual: d.historiaDoencaAtual ?? '',
          descricao: d.descricao ?? '', exameFisico: d.exameFisico ?? '',
          hipoteseDiagnostica: d.hipoteseDiagnostica ?? '', diagnostico: d.diagnostico ?? '',
          tipoDiagnostico: d.tipoDiagnostico ?? 'DEFINITIVO',
          prescricao: d.prescricao ?? '', planoTerapeutico: d.planoTerapeutico ?? '', conduta: d.conduta ?? '',
        })
      })
      .catch((err) => setErro(err.message))
      .finally(() => setCarregandoInicial(false))
  }, [continuando, prontuarioIdExistente])

  const pacientesFiltrados = useMemo(() => {
    const termo = buscaPaciente.trim().toLowerCase()
    if (!termo) return pacientesLista.slice(0, 30)
    return pacientesLista.filter((p) => p.nome.toLowerCase().includes(termo) || p.telefone.includes(termo)).slice(0, 30)
  }, [pacientesLista, buscaPaciente])

  function atualizar(campo, valor) {
    setForm((f) => ({ ...f, [campo]: valor }))
  }

  function escolherPaciente(p) {
    setPacienteSelecionado({ id: p.id, nome: p.nome })
    setEtapa('formulario')
  }

  const cabecalhoValido = !!pacienteSelecionado && form.idMedico && form.tipo && form.dataHora

  async function salvar(finalizar) {
    if (salvando) return
    setErro(null)

    if (!continuando && !cabecalhoValido) {
      setErro('Preencha profissional, tipo de atendimento e data/hora antes de salvar.')
      return
    }
    if (finalizar && (!form.descricao.trim() || !form.diagnostico.trim() || !form.prescricao.trim())) {
      setErro('Para finalizar, preencha evolução, diagnóstico e prescrição.')
      return
    }

    setSalvando(finalizar ? 'finalizar' : 'rascunho')
    try {
      let idConsultaAtual = consultaId
      let idProntuarioAtual = prontuarioId

      if (!idProntuarioAtual) {
        if (!idConsultaAtual) {
          const { dataSlot, horaSlot } = separarDataHora(form.dataHora)
          const consulta = await criarConsultaAgenda({
            idPaciente: pacienteSelecionado.id,
            idMedico: Number(form.idMedico),
            dataSlot, horaSlot,
            tipo: form.tipo,
            duracaoMinutos: 30,
            status: 'Realizada',
          })
          idConsultaAtual = consulta.id
          setConsultaId(idConsultaAtual)
        }
        const payload = montarPayload(finalizar)
        const salvo = await criarProntuario({ ...payload, consultaId: idConsultaAtual })
        idProntuarioAtual = salvo.id
        setProntuarioId(idProntuarioAtual)
      } else {
        await atualizarProntuario(idProntuarioAtual, montarPayload(finalizar))
      }
      onSalvo(finalizar)
    } catch (err) {
      setErro(err.message)
    } finally {
      setSalvando(null)
    }
  }

  function montarPayload(finalizar) {
    return {
      medicoResponsavelId: form.idMedico ? Number(form.idMedico) : null,
      queixaPrincipal: form.queixaPrincipal || null,
      historiaDoencaAtual: form.historiaDoencaAtual || null,
      descricao: form.descricao || null,
      exameFisico: form.exameFisico || null,
      hipoteseDiagnostica: form.hipoteseDiagnostica || null,
      diagnostico: form.diagnostico || null,
      tipoDiagnostico: form.tipoDiagnostico,
      prescricao: form.prescricao || null,
      planoTerapeutico: form.planoTerapeutico || null,
      conduta: form.conduta || null,
      finalizar,
    }
  }

  return (
    <div className="prontuario-overlay" onClick={onClose}>
      <div className={`prontuario-modal${etapa === 'selecionar-paciente' ? ' pequeno' : ''}`} onClick={(e) => e.stopPropagation()}>
        {etapa === 'selecionar-paciente' ? (
          <>
            <div className="prontuario-modal-title">Novo atendimento</div>
            <div className="prontuario-modal-subtitle">Selecione o paciente para começar</div>
            <div className="prontuario-modal-campo">
              <input
                className="prontuario-input" value={buscaPaciente} onChange={(e) => setBuscaPaciente(e.target.value)}
                placeholder="Buscar por nome ou telefone..." autoFocus
              />
            </div>
            <div className="prontuario-picker-lista">
              {pacientesFiltrados.length === 0 && <div className="prontuario-picker-vazio">Nenhum paciente encontrado.</div>}
              {pacientesFiltrados.map((p) => (
                <button key={p.id} type="button" className="prontuario-picker-item" onClick={() => escolherPaciente(p)}>
                  <span className="prontuario-picker-nome">{p.nome}</span>
                  <span className="prontuario-picker-sub">{p.telefone}</span>
                </button>
              ))}
            </div>
            <div className="prontuario-modal-footer">
              <button type="button" className="prontuario-btn-ghost" onClick={onClose}>Cancelar</button>
            </div>
          </>
        ) : carregandoInicial ? (
          <div className="prontuario-modal-subtitle">Carregando atendimento…</div>
        ) : !pacienteSelecionado ? (
          <>
            <div className="prontuario-modal-title">Não foi possível carregar</div>
            {erro && <div className="prontuario-modal-erro">{erro}</div>}
            <div className="prontuario-modal-footer">
              <button type="button" className="prontuario-btn-ghost" onClick={onClose}>Fechar</button>
            </div>
          </>
        ) : (
          <>
            <div className="prontuario-modal-title">
              {continuando ? 'Continuar atendimento' : 'Novo atendimento'}
            </div>
            <div className="prontuario-modal-subtitle">
              <span className="prontuario-avatar" style={{ display: 'inline-flex', width: 20, height: 20, fontSize: 9, verticalAlign: 'middle', marginRight: 6 }}>
                {iniciaisDe(pacienteSelecionado.nome)}
              </span>
              {pacienteSelecionado.nome}
            </div>

            {erro && <div className="prontuario-modal-erro">{erro}</div>}

            <div className="prontuario-modal-secao-titulo">Atendimento</div>
            {continuando ? (
              <div className="prontuario-modal-linha">
                <div className="prontuario-modal-campo">
                  <span className="prontuario-label">Tipo</span>
                  <div className="prontuario-registro-texto">{agendaFixa?.tipo}</div>
                </div>
                <div className="prontuario-modal-campo">
                  <span className="prontuario-label">Data/hora</span>
                  <div className="prontuario-registro-texto">{agendaFixa?.dataTxt} às {agendaFixa?.hora}</div>
                </div>
              </div>
            ) : (
              <div className="prontuario-modal-linha">
                <div className="prontuario-modal-campo">
                  <label className="prontuario-label" htmlFor="na-tipo">Tipo de atendimento</label>
                  <select id="na-tipo" className="prontuario-select" value={form.tipo} onChange={(e) => atualizar('tipo', e.target.value)}>
                    {TIPOS_ATENDIMENTO.map((t) => <option key={t} value={t}>{t}</option>)}
                  </select>
                </div>
                <div className="prontuario-modal-campo">
                  <label className="prontuario-label" htmlFor="na-data">Data/hora</label>
                  <input
                    id="na-data" type="datetime-local" className="prontuario-input"
                    value={form.dataHora} onChange={(e) => atualizar('dataHora', e.target.value)}
                  />
                </div>
              </div>
            )}

            <div className="prontuario-modal-campo">
              <label className="prontuario-label" htmlFor="na-medico">Profissional responsável</label>
              <select
                id="na-medico" className="prontuario-select" value={form.idMedico}
                onChange={(e) => atualizar('idMedico', e.target.value)}
              >
                <option value="">Selecione…</option>
                {medicos.map((m) => <option key={m.id} value={m.id}>{m.nome}</option>)}
              </select>
            </div>

            <div className="prontuario-modal-secao-titulo">Subjetivo</div>
            <div className="prontuario-modal-campo">
              <label className="prontuario-label" htmlFor="na-queixa">Queixa principal</label>
              <input
                id="na-queixa" className="prontuario-input" value={form.queixaPrincipal}
                onChange={(e) => atualizar('queixaPrincipal', e.target.value)}
              />
            </div>
            <div className="prontuario-modal-campo">
              <label className="prontuario-label" htmlFor="na-anamnese">Anamnese / história da doença atual</label>
              <textarea
                id="na-anamnese" className="prontuario-textarea" value={form.historiaDoencaAtual}
                onChange={(e) => atualizar('historiaDoencaAtual', e.target.value)}
              />
            </div>

            <div className="prontuario-modal-secao-titulo">Objetivo</div>
            <div className="prontuario-modal-campo">
              <label className="prontuario-label" htmlFor="na-exame">Exame físico</label>
              <textarea
                id="na-exame" className="prontuario-textarea" value={form.exameFisico}
                onChange={(e) => atualizar('exameFisico', e.target.value)}
              />
            </div>

            <div className="prontuario-modal-secao-titulo">Avaliação</div>
            <div className="prontuario-modal-campo">
              <label className="prontuario-label" htmlFor="na-evolucao">Evolução *</label>
              <textarea
                id="na-evolucao" className="prontuario-textarea" value={form.descricao}
                onChange={(e) => atualizar('descricao', e.target.value)}
                placeholder="Obrigatório para finalizar"
              />
            </div>
            <div className="prontuario-modal-campo">
              <label className="prontuario-label" htmlFor="na-hipotese">Hipótese diagnóstica</label>
              <input
                id="na-hipotese" className="prontuario-input" value={form.hipoteseDiagnostica}
                onChange={(e) => atualizar('hipoteseDiagnostica', e.target.value)}
              />
            </div>
            <div className="prontuario-modal-linha">
              <div className="prontuario-modal-campo">
                <label className="prontuario-label" htmlFor="na-diagnostico">Diagnóstico *</label>
                <input
                  id="na-diagnostico" className="prontuario-input" value={form.diagnostico}
                  onChange={(e) => atualizar('diagnostico', e.target.value)}
                  placeholder="Obrigatório para finalizar"
                />
              </div>
              <div className="prontuario-modal-campo" style={{ flex: '0 0 150px' }}>
                <label className="prontuario-label" htmlFor="na-tipodiag">Tipo</label>
                <select
                  id="na-tipodiag" className="prontuario-select" value={form.tipoDiagnostico}
                  onChange={(e) => atualizar('tipoDiagnostico', e.target.value)}
                >
                  <option value="PROVISORIO">Provisório</option>
                  <option value="DEFINITIVO">Definitivo</option>
                </select>
              </div>
            </div>

            <div className="prontuario-modal-secao-titulo">Plano</div>
            <div className="prontuario-modal-campo">
              <label className="prontuario-label" htmlFor="na-prescricao">Prescrição *</label>
              <textarea
                id="na-prescricao" className="prontuario-textarea" value={form.prescricao}
                onChange={(e) => atualizar('prescricao', e.target.value)}
                placeholder="Obrigatório para finalizar"
              />
            </div>
            <div className="prontuario-modal-campo">
              <label className="prontuario-label" htmlFor="na-plano">Plano terapêutico</label>
              <textarea
                id="na-plano" className="prontuario-textarea" value={form.planoTerapeutico}
                onChange={(e) => atualizar('planoTerapeutico', e.target.value)}
              />
            </div>
            <div className="prontuario-modal-campo">
              <label className="prontuario-label" htmlFor="na-conduta">Conduta</label>
              <textarea
                id="na-conduta" className="prontuario-textarea" value={form.conduta}
                onChange={(e) => atualizar('conduta', e.target.value)}
              />
            </div>

            <div className="prontuario-modal-footer">
              <button type="button" className="prontuario-btn-ghost" onClick={onClose} disabled={!!salvando}>Cancelar</button>
              <button type="button" className="prontuario-btn-secundario" onClick={() => salvar(false)} disabled={!!salvando}>
                {salvando === 'rascunho' ? 'Salvando…' : 'Salvar rascunho'}
              </button>
              <button type="button" className="prontuario-btn-primario" onClick={() => salvar(true)} disabled={!!salvando}>
                {salvando === 'finalizar' ? 'Finalizando…' : 'Finalizar atendimento'}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
