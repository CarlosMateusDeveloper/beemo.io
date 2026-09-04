import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import {
  atualizarProntuario, atualizarPaciente, criarConsultaAgenda, criarProntuario,
  fetchConvenios, fetchMedicos, fetchPaciente, fetchProntuarioDetalhe,
} from './api'
import { criarPaciente } from '../pacientes/api'
import { agoraDatetimeLocal, iniciaisDe, separarDataHora, TIPOS_ATENDIMENTO } from './prontuarioData'
import './prontuario.css'

const UFS = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG',
  'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO',
]

const FORM_VAZIO = {
  idMedico: '', tipo: 'Consulta', dataHora: agoraDatetimeLocal(),
  queixaPrincipal: '', historiaDoencaAtual: '', descricao: '', exameFisico: '',
  hipoteseDiagnostica: '', diagnostico: '', tipoDiagnostico: 'DEFINITIVO',
  prescricao: '', planoTerapeutico: '', conduta: '', retornoSugeridoDias: '',
}

const PACIENTE_VAZIO = {
  nome: '', cpf: '', dataNascimento: '', ddd: '', numero: '', email: '', convenioId: '',
  cep: '', logradouro: '', numeroEndereco: '', complemento: '', bairro: '', cidade: '', uf: '',
}

function apenasDigitos(v) {
  return v.replace(/\D/g, '')
}

function pacienteParaForm(p) {
  return {
    nome: p.nome || '', cpf: p.cpf || '', dataNascimento: p.dataNascimento || '',
    ddd: p.ddd || '', numero: p.numero || '', email: p.email || '',
    convenioId: p.convenio?.id ? String(p.convenio.id) : '',
    cep: p.cep || '', logradouro: p.logradouro || '', numeroEndereco: p.numeroEndereco || '',
    complemento: p.complemento || '', bairro: p.bairro || '', cidade: p.cidade || '', uf: p.uf || '',
  }
}

// Página de atendimento (SOAP + cadastro do paciente): substitui o antigo
// modal — um formulário deste tamanho merece rota própria, tela cheia, não
// um overlay de 640px. Aceita ?pacienteId= (paciente já existe — carrega o
// cadastro completo pra edição inline), ?prontuarioId= (retomar um
// rascunho) ou nenhum dos dois (paciente novo — formulário de cadastro em
// branco). Em qualquer um dos dois primeiros casos, o cadastro fica editável
// junto do atendimento — evita uma segunda viagem ao "Editar cadastro".
export function PaginaAtendimento() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const pacienteIdParam = searchParams.get('pacienteId')
  const prontuarioIdExistente = searchParams.get('prontuarioId')
  const continuando = !!prontuarioIdExistente
  const mostrarCadastroPaciente = !continuando

  const [pacienteSelecionado, setPacienteSelecionado] = useState(null)
  const [pacienteForm, setPacienteForm] = useState(PACIENTE_VAZIO)
  const [convenios, setConvenios] = useState([])

  const [medicos, setMedicos] = useState([])
  const [form, setForm] = useState(FORM_VAZIO)
  const [consultaId, setConsultaId] = useState(null)
  const [prontuarioId, setProntuarioId] = useState(prontuarioIdExistente ? Number(prontuarioIdExistente) : null)
  const [agendaFixa, setAgendaFixa] = useState(null) // { profissional, tipo, dataTxt, hora } quando continuando

  const [carregandoInicial, setCarregandoInicial] = useState(continuando || !!pacienteIdParam)
  const [salvando, setSalvando] = useState(null) // null | 'rascunho' | 'finalizar'
  const [erro, setErro] = useState(null)

  useEffect(() => {
    fetchMedicos().then(setMedicos).catch(() => { /* select fica vazio se falhar */ })
  }, [])

  useEffect(() => {
    if (!mostrarCadastroPaciente) return
    fetchConvenios().then(setConvenios).catch(() => { /* select fica só com "Particular" se falhar */ })
  }, [mostrarCadastroPaciente])

  // Paciente já existe (veio da própria ficha): carrega o cadastro completo
  // pra edição inline, em vez de um formulário em branco.
  useEffect(() => {
    if (!pacienteIdParam) return
    fetchPaciente(pacienteIdParam)
      .then((p) => {
        setPacienteSelecionado(p)
        setPacienteForm(pacienteParaForm(p))
      })
      .catch((err) => setErro(err.message))
      .finally(() => setCarregandoInicial(false))
  }, [pacienteIdParam])

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

  function atualizar(campo, valor) {
    setForm((f) => ({ ...f, [campo]: valor }))
  }

  function atualizarPacienteForm(campo, valor) {
    setPacienteForm((f) => ({ ...f, [campo]: valor }))
  }

  function voltar() {
    navigate(-1)
  }

  const pacienteFormValido = !mostrarCadastroPaciente || (
    pacienteForm.nome.trim().length > 0 && pacienteForm.cpf.length === 11
    && pacienteForm.dataNascimento && pacienteForm.ddd.length === 2 && pacienteForm.numero.trim().length > 0
  )
  const cabecalhoValido = pacienteFormValido && form.idMedico && form.tipo && form.dataHora

  function montarPayloadPaciente() {
    return {
      nome: pacienteForm.nome.trim(),
      cpf: pacienteForm.cpf,
      dataNascimento: pacienteForm.dataNascimento,
      ddd: pacienteForm.ddd,
      numero: pacienteForm.numero.trim(),
      email: pacienteForm.email.trim() || null,
      convenio: pacienteForm.convenioId ? { id: Number(pacienteForm.convenioId) } : null,
      cep: pacienteForm.cep || null,
      logradouro: pacienteForm.logradouro.trim() || null,
      numeroEndereco: pacienteForm.numeroEndereco.trim() || null,
      complemento: pacienteForm.complemento.trim() || null,
      bairro: pacienteForm.bairro.trim() || null,
      cidade: pacienteForm.cidade.trim() || null,
      uf: pacienteForm.uf || null,
    }
  }

  async function salvar(finalizar) {
    if (salvando) return
    setErro(null)

    if (!continuando && !cabecalhoValido) {
      setErro(mostrarCadastroPaciente && !pacienteFormValido
        ? 'Preencha nome, CPF, nascimento e telefone do paciente antes de salvar.'
        : 'Preencha profissional, tipo de atendimento e data/hora antes de salvar.')
      return
    }
    if (finalizar && (!form.descricao.trim() || !form.diagnostico.trim() || !form.prescricao.trim())) {
      setErro('Para finalizar, preencha evolução, diagnóstico e prescrição.')
      return
    }

    setSalvando(finalizar ? 'finalizar' : 'rascunho')
    try {
      let paciente = pacienteSelecionado
      if (mostrarCadastroPaciente) {
        if (paciente) {
          paciente = await atualizarPaciente(paciente.id, montarPayloadPaciente())
        } else {
          paciente = await criarPaciente(montarPayloadPaciente())
        }
        setPacienteSelecionado(paciente)
      }

      let idConsultaAtual = consultaId
      let idProntuarioAtual = prontuarioId

      if (!idProntuarioAtual) {
        if (!idConsultaAtual) {
          const { dataSlot, horaSlot } = separarDataHora(form.dataHora)
          const consulta = await criarConsultaAgenda({
            idPaciente: paciente.id,
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
      navigate(`/prontuario/${paciente.id}`)
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
      retornoSugeridoDias: form.retornoSugeridoDias ? Number(form.retornoSugeridoDias) : null,
      finalizar,
    }
  }

  return (
    <div className="prontuario-page">
      <button type="button" className="prontuario-detalhe-voltar" onClick={voltar}>
        <ArrowLeft size={14} strokeWidth={2} />Voltar
      </button>

      <div className="prontuario-painel atendimento-painel">
        {carregandoInicial ? (
          <div className="prontuario-modal-subtitle">Carregando atendimento…</div>
        ) : continuando && !pacienteSelecionado ? (
          <>
            <div className="prontuario-modal-title">Não foi possível carregar</div>
            {erro && <div className="prontuario-modal-erro">{erro}</div>}
          </>
        ) : (
          <>
            <div className="prontuario-modal-title">
              {continuando ? 'Continuar atendimento' : 'Novo atendimento'}
            </div>
            {continuando && pacienteSelecionado?.nome && (
              <div className="prontuario-modal-subtitle">
                <span className="prontuario-avatar" style={{ display: 'inline-flex', width: 20, height: 20, fontSize: 9, verticalAlign: 'middle', marginRight: 6 }}>
                  {iniciaisDe(pacienteSelecionado.nome)}
                </span>
                {pacienteSelecionado.nome}
              </div>
            )}

            {erro && <div className="prontuario-modal-erro">{erro}</div>}

            {mostrarCadastroPaciente && (
              <>
                <div className="prontuario-modal-secao-titulo">Dados do paciente</div>
                <div className="atendimento-grid">
                  <div className="prontuario-modal-campo span2">
                    <label className="prontuario-label" htmlFor="np-nome">Nome completo</label>
                    <input
                      id="np-nome" className="prontuario-input" value={pacienteForm.nome}
                      onChange={(e) => atualizarPacienteForm('nome', e.target.value)} placeholder="Nome do paciente"
                    />
                  </div>
                  <div className="prontuario-modal-campo">
                    <label className="prontuario-label" htmlFor="np-cpf">CPF</label>
                    <input
                      id="np-cpf" className="prontuario-input" value={pacienteForm.cpf} maxLength={11}
                      onChange={(e) => atualizarPacienteForm('cpf', apenasDigitos(e.target.value))} placeholder="Somente números"
                    />
                  </div>
                  <div className="prontuario-modal-campo">
                    <label className="prontuario-label" htmlFor="np-nasc">Nascimento</label>
                    <input
                      id="np-nasc" type="date" className="prontuario-input" value={pacienteForm.dataNascimento}
                      onChange={(e) => atualizarPacienteForm('dataNascimento', e.target.value)}
                    />
                  </div>

                  <div className="prontuario-modal-campo">
                    <label className="prontuario-label" htmlFor="np-ddd">DDD</label>
                    <input
                      id="np-ddd" className="prontuario-input" value={pacienteForm.ddd} maxLength={2}
                      onChange={(e) => atualizarPacienteForm('ddd', apenasDigitos(e.target.value))} placeholder="00"
                    />
                  </div>
                  <div className="prontuario-modal-campo">
                    <label className="prontuario-label" htmlFor="np-num">Telefone</label>
                    <input
                      id="np-num" className="prontuario-input" value={pacienteForm.numero} maxLength={10}
                      onChange={(e) => atualizarPacienteForm('numero', apenasDigitos(e.target.value))} placeholder="Número"
                    />
                  </div>
                  <div className="prontuario-modal-campo">
                    <label className="prontuario-label" htmlFor="np-email">E-mail</label>
                    <input
                      id="np-email" type="email" className="prontuario-input" value={pacienteForm.email}
                      onChange={(e) => atualizarPacienteForm('email', e.target.value)} placeholder="paciente@exemplo.com"
                    />
                  </div>

                  <div className="prontuario-modal-campo">
                    <label className="prontuario-label" htmlFor="np-conv">Convênio</label>
                    <select
                      id="np-conv" className="prontuario-select" value={pacienteForm.convenioId}
                      onChange={(e) => atualizarPacienteForm('convenioId', e.target.value)}
                    >
                      <option value="">Particular</option>
                      {convenios.map((c) => <option key={c.id} value={c.id}>{c.nome}</option>)}
                    </select>
                  </div>
                  <div className="prontuario-modal-campo">
                    <label className="prontuario-label" htmlFor="np-cep">CEP</label>
                    <input
                      id="np-cep" className="prontuario-input" value={pacienteForm.cep} maxLength={8}
                      onChange={(e) => atualizarPacienteForm('cep', apenasDigitos(e.target.value))} placeholder="Somente números"
                    />
                  </div>
                  <div className="prontuario-modal-campo span2">
                    <label className="prontuario-label" htmlFor="np-log">Logradouro</label>
                    <input
                      id="np-log" className="prontuario-input" value={pacienteForm.logradouro}
                      onChange={(e) => atualizarPacienteForm('logradouro', e.target.value)} placeholder="Rua, avenida…"
                    />
                  </div>

                  <div className="prontuario-modal-campo">
                    <label className="prontuario-label" htmlFor="np-numend">Número</label>
                    <input
                      id="np-numend" className="prontuario-input" value={pacienteForm.numeroEndereco}
                      onChange={(e) => atualizarPacienteForm('numeroEndereco', e.target.value)}
                    />
                  </div>
                  <div className="prontuario-modal-campo">
                    <label className="prontuario-label" htmlFor="np-compl">Complemento</label>
                    <input
                      id="np-compl" className="prontuario-input" value={pacienteForm.complemento}
                      onChange={(e) => atualizarPacienteForm('complemento', e.target.value)}
                    />
                  </div>
                  <div className="prontuario-modal-campo">
                    <label className="prontuario-label" htmlFor="np-bairro">Bairro</label>
                    <input
                      id="np-bairro" className="prontuario-input" value={pacienteForm.bairro}
                      onChange={(e) => atualizarPacienteForm('bairro', e.target.value)}
                    />
                  </div>

                  <div className="prontuario-modal-campo">
                    <label className="prontuario-label" htmlFor="np-cidade">Cidade</label>
                    <input
                      id="np-cidade" className="prontuario-input" value={pacienteForm.cidade}
                      onChange={(e) => atualizarPacienteForm('cidade', e.target.value)}
                    />
                  </div>
                  <div className="prontuario-modal-campo">
                    <label className="prontuario-label" htmlFor="np-uf">UF</label>
                    <select id="np-uf" className="prontuario-select" value={pacienteForm.uf} onChange={(e) => atualizarPacienteForm('uf', e.target.value)}>
                      <option value="">—</option>
                      {UFS.map((uf) => <option key={uf} value={uf}>{uf}</option>)}
                    </select>
                  </div>
                </div>
              </>
            )}

            <div className="prontuario-modal-secao-titulo">Atendimento</div>
            {continuando ? (
              <div className="atendimento-grid">
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
              <div className="atendimento-grid">
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
            <div className="prontuario-modal-campo" style={{ maxWidth: 340 }}>
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
                id="na-anamnese" className="prontuario-textarea atendimento-textarea" value={form.historiaDoencaAtual}
                onChange={(e) => atualizar('historiaDoencaAtual', e.target.value)}
              />
            </div>

            <div className="prontuario-modal-secao-titulo">Objetivo</div>
            <div className="prontuario-modal-campo">
              <label className="prontuario-label" htmlFor="na-exame">Exame físico</label>
              <textarea
                id="na-exame" className="prontuario-textarea atendimento-textarea" value={form.exameFisico}
                onChange={(e) => atualizar('exameFisico', e.target.value)}
              />
            </div>

            <div className="prontuario-modal-secao-titulo">Avaliação</div>
            <div className="prontuario-modal-campo">
              <label className="prontuario-label" htmlFor="na-evolucao">Evolução *</label>
              <textarea
                id="na-evolucao" className="prontuario-textarea atendimento-textarea" value={form.descricao}
                onChange={(e) => atualizar('descricao', e.target.value)}
                placeholder="Obrigatório para finalizar"
              />
            </div>
            <div className="atendimento-grid">
              <div className="prontuario-modal-campo span2">
                <label className="prontuario-label" htmlFor="na-hipotese">Hipótese diagnóstica</label>
                <input
                  id="na-hipotese" className="prontuario-input" value={form.hipoteseDiagnostica}
                  onChange={(e) => atualizar('hipoteseDiagnostica', e.target.value)}
                />
              </div>
              <div className="prontuario-modal-campo span2">
                <label className="prontuario-label" htmlFor="na-diagnostico">Diagnóstico *</label>
                <input
                  id="na-diagnostico" className="prontuario-input" value={form.diagnostico}
                  onChange={(e) => atualizar('diagnostico', e.target.value)}
                  placeholder="Obrigatório para finalizar"
                />
              </div>
              <div className="prontuario-modal-campo">
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
                id="na-prescricao" className="prontuario-textarea atendimento-textarea" value={form.prescricao}
                onChange={(e) => atualizar('prescricao', e.target.value)}
                placeholder="Obrigatório para finalizar"
              />
            </div>
            <div className="prontuario-modal-campo">
              <label className="prontuario-label" htmlFor="na-plano">Plano terapêutico</label>
              <textarea
                id="na-plano" className="prontuario-textarea atendimento-textarea" value={form.planoTerapeutico}
                onChange={(e) => atualizar('planoTerapeutico', e.target.value)}
              />
            </div>
            <div className="prontuario-modal-campo">
              <label className="prontuario-label" htmlFor="na-conduta">Conduta</label>
              <textarea
                id="na-conduta" className="prontuario-textarea atendimento-textarea" value={form.conduta}
                onChange={(e) => atualizar('conduta', e.target.value)}
              />
            </div>
            <div className="prontuario-modal-campo" style={{ maxWidth: 260 }}>
              <label className="prontuario-label" htmlFor="na-retorno">Retorno sugerido em (dias)</label>
              <input
                id="na-retorno" type="number" min={1} className="prontuario-input" value={form.retornoSugeridoDias}
                onChange={(e) => atualizar('retornoSugeridoDias', e.target.value)}
                placeholder="Ex: 30 — alimenta a fila de /retorno"
              />
            </div>

            <div className="prontuario-modal-footer">
              <button type="button" className="prontuario-btn-ghost" onClick={voltar} disabled={!!salvando}>Cancelar</button>
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
