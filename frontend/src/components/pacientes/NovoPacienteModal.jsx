import { useEffect, useState } from 'react'
import { criarPaciente, fetchConvenios } from './api'

const VAZIO = { nome: '', cpf: '', dataNascimento: '', ddd: '', numero: '', convenioId: '' }

function apenasDigitos(v) {
  return v.replace(/\D/g, '')
}

export default function NovoPacienteModal({ onClose, onCriado }) {
  const [form, setForm] = useState(VAZIO)
  const [convenios, setConvenios] = useState([])
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState(null)

  useEffect(() => {
    fetchConvenios().then(setConvenios).catch(() => { /* select fica só com "Particular" se a lista falhar */ })
  }, [])

  const valido = form.nome.trim().length > 0
    && form.cpf.length === 11
    && form.dataNascimento
    && form.ddd.length === 2
    && form.numero.trim().length > 0

  function atualizar(campo, valor) {
    setForm((f) => ({ ...f, [campo]: valor }))
  }

  async function salvar() {
    if (!valido || salvando) return
    setSalvando(true)
    setErro(null)
    try {
      const paciente = await criarPaciente({
        nome: form.nome.trim(),
        cpf: form.cpf,
        dataNascimento: form.dataNascimento,
        ddd: form.ddd,
        numero: form.numero.trim(),
        convenio: form.convenioId ? { id: Number(form.convenioId) } : null,
      })
      onCriado(paciente)
    } catch (err) {
      setErro(err.message)
    } finally {
      setSalvando(false)
    }
  }

  return (
    <div className="pacientes-overlay" onClick={onClose}>
      <div className="pacientes-modal" onClick={(e) => e.stopPropagation()}>
        <div className="pacientes-modal-title">Novo paciente</div>
        <div className="pacientes-modal-subtitle">Cadastro básico — dados clínicos ficam na ficha do paciente</div>

        {erro && <div className="pacientes-modal-erro">{erro}</div>}

        <div className="pacientes-modal-campo">
          <label className="pacientes-label" htmlFor="np-nome">Nome completo</label>
          <input
            id="np-nome" className="pacientes-input" value={form.nome}
            onChange={(e) => atualizar('nome', e.target.value)} placeholder="Nome do paciente"
          />
        </div>

        <div className="pacientes-modal-linha">
          <div className="pacientes-modal-campo">
            <label className="pacientes-label" htmlFor="np-cpf">CPF</label>
            <input
              id="np-cpf" className="pacientes-input" value={form.cpf} maxLength={11}
              onChange={(e) => atualizar('cpf', apenasDigitos(e.target.value))} placeholder="Somente números"
            />
          </div>
          <div className="pacientes-modal-campo">
            <label className="pacientes-label" htmlFor="np-nasc">Nascimento</label>
            <input
              id="np-nasc" type="date" className="pacientes-input" value={form.dataNascimento}
              onChange={(e) => atualizar('dataNascimento', e.target.value)}
            />
          </div>
        </div>

        <div className="pacientes-modal-linha">
          <div className="pacientes-modal-campo" style={{ flex: '0 0 70px' }}>
            <label className="pacientes-label" htmlFor="np-ddd">DDD</label>
            <input
              id="np-ddd" className="pacientes-input" value={form.ddd} maxLength={2}
              onChange={(e) => atualizar('ddd', apenasDigitos(e.target.value))} placeholder="00"
            />
          </div>
          <div className="pacientes-modal-campo">
            <label className="pacientes-label" htmlFor="np-num">Telefone</label>
            <input
              id="np-num" className="pacientes-input" value={form.numero} maxLength={10}
              onChange={(e) => atualizar('numero', apenasDigitos(e.target.value))} placeholder="Número"
            />
          </div>
        </div>

        <div className="pacientes-modal-campo">
          <label className="pacientes-label" htmlFor="np-conv">Convênio</label>
          <select
            id="np-conv" className="pacientes-select" value={form.convenioId}
            onChange={(e) => atualizar('convenioId', e.target.value)}
          >
            <option value="">Particular</option>
            {convenios.map((c) => <option key={c.id} value={c.id}>{c.nome}</option>)}
          </select>
        </div>

        <div className="pacientes-modal-footer">
          <button type="button" className="pacientes-btn-ghost" onClick={onClose}>Cancelar</button>
          <button type="button" className="pacientes-btn-primario" onClick={salvar} disabled={!valido || salvando}>
            {salvando ? 'Salvando…' : 'Cadastrar paciente'}
          </button>
        </div>
      </div>
    </div>
  )
}
