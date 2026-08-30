import { useState } from 'react'
import { criarConvenio } from './api'

const VAZIO = { nome: '', registroAns: '', contato: '', observacoes: '' }

export default function NovoConvenioModal({ onClose, onCriado }) {
  const [form, setForm] = useState(VAZIO)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState(null)

  const valido = form.nome.trim().length > 0 && form.registroAns.trim().length === 6

  function atualizar(campo, valor) {
    setForm((f) => ({ ...f, [campo]: valor }))
  }

  async function salvar() {
    if (!valido || salvando) return
    setSalvando(true)
    setErro(null)
    try {
      const convenio = await criarConvenio({
        nome: form.nome.trim(),
        registroAns: form.registroAns.trim(),
        contato: form.contato.trim() || null,
        observacoes: form.observacoes.trim() || null,
        ativo: true,
      })
      onCriado(convenio)
    } catch (err) {
      setErro(err.message)
    } finally {
      setSalvando(false)
    }
  }

  return (
    <div className="convenios-overlay" onClick={onClose}>
      <div className="convenios-modal" onClick={(e) => e.stopPropagation()}>
        <div className="convenios-modal-title">Novo convênio</div>
        <div className="convenios-modal-subtitle">Cadastro básico — planos, procedimentos e regras entram depois na ficha do convênio</div>

        {erro && <div className="convenios-modal-erro">{erro}</div>}

        <div className="convenios-modal-campo">
          <label className="convenios-label" htmlFor="nc-nome">Nome</label>
          <input
            id="nc-nome" className="convenios-input" value={form.nome}
            onChange={(e) => atualizar('nome', e.target.value)} placeholder="Nome do convênio"
          />
        </div>

        <div className="convenios-modal-campo">
          <label className="convenios-label" htmlFor="nc-ans">Registro ANS</label>
          <input
            id="nc-ans" className="convenios-input" value={form.registroAns} maxLength={6}
            onChange={(e) => atualizar('registroAns', e.target.value)} placeholder="6 caracteres"
          />
        </div>

        <div className="convenios-modal-campo">
          <label className="convenios-label" htmlFor="nc-contato">Contato</label>
          <input
            id="nc-contato" className="convenios-input" value={form.contato}
            onChange={(e) => atualizar('contato', e.target.value)} placeholder="Telefone, e-mail ou nome do representante"
          />
        </div>

        <div className="convenios-modal-campo">
          <label className="convenios-label" htmlFor="nc-obs">Observações</label>
          <textarea
            id="nc-obs" className="convenios-textarea" value={form.observacoes} rows={3}
            onChange={(e) => atualizar('observacoes', e.target.value)} placeholder="Opcional"
          />
        </div>

        <div className="convenios-modal-footer">
          <button type="button" className="convenios-btn-ghost" onClick={onClose}>Cancelar</button>
          <button type="button" className="convenios-btn-primario" onClick={salvar} disabled={!valido || salvando}>
            {salvando ? 'Salvando…' : 'Cadastrar convênio'}
          </button>
        </div>
      </div>
    </div>
  )
}
