import { useState } from 'react'
import { Pencil, Plus, X } from 'lucide-react'

function valorInicialForm(campos, item) {
  const form = {}
  for (const campo of campos) {
    if (campo.type === 'ref-select') {
      form[campo.key] = item?.[campo.key]?.id != null ? String(item[campo.key].id) : ''
    } else if (campo.type === 'checkbox') {
      form[campo.key] = item ? Boolean(item[campo.key]) : (campo.default ?? false)
    } else {
      form[campo.key] = item?.[campo.key] ?? campo.default ?? ''
    }
  }
  return form
}

function montarDados(campos, form) {
  const dados = {}
  for (const campo of campos) {
    const bruto = form[campo.key]
    if (campo.type === 'ref-select') {
      dados[campo.key] = bruto ? { id: Number(bruto) } : null
    } else if (campo.type === 'number') {
      dados[campo.key] = bruto === '' || bruto == null ? null : Number(bruto)
    } else if (campo.type === 'checkbox') {
      dados[campo.key] = Boolean(bruto)
    } else if (typeof bruto === 'string') {
      dados[campo.key] = bruto.trim() || null
    } else {
      dados[campo.key] = bruto
    }
  }
  return dados
}

function Campo({ campo, valor, onChange }) {
  if (campo.type === 'checkbox') {
    return (
      <label className="convenios-form-check">
        <input type="checkbox" checked={valor} onChange={(e) => onChange(e.target.checked)} />
        {campo.label}
      </label>
    )
  }
  if (campo.type === 'select' || campo.type === 'ref-select') {
    return (
      <select className="convenios-input" value={valor} onChange={(e) => onChange(e.target.value)}>
        <option value="">{campo.placeholder ?? 'Selecione'}</option>
        {campo.options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    )
  }
  return (
    <input
      type={campo.type === 'number' ? 'number' : 'text'}
      className="convenios-input" value={valor} step={campo.step}
      placeholder={campo.placeholder} onChange={(e) => onChange(e.target.value)}
    />
  )
}

export default function SubRecursoTabela({
  titulo, vazioTitulo, vazioTexto, itens, campos, colunas, onCriar, onAtualizar, idField = 'id',
}) {
  const [modo, setModo] = useState('lista') // 'lista' | 'criando' | id do item em edição
  const [form, setForm] = useState({})
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState(null)

  const emEdicaoId = modo !== 'lista' && modo !== 'criando' ? modo : null
  const campoObrigatorioVazio = campos.some((c) => c.obrigatorio && !String(form[c.key] ?? '').trim())

  function abrirCriacao() {
    setForm(valorInicialForm(campos, null))
    setErro(null)
    setModo('criando')
  }

  function abrirEdicao(item) {
    setForm(valorInicialForm(campos, item))
    setErro(null)
    setModo(item[idField])
  }

  function cancelar() {
    setModo('lista')
    setErro(null)
  }

  async function salvar() {
    if (campoObrigatorioVazio || salvando) return
    setSalvando(true)
    setErro(null)
    try {
      const dados = montarDados(campos, form)
      if (modo === 'criando') await onCriar(dados)
      else await onAtualizar(emEdicaoId, dados)
      setModo('lista')
    } catch (err) {
      setErro(err.message)
    } finally {
      setSalvando(false)
    }
  }

  return (
    <div className="convenios-sub">
      <div className="convenios-sub-head">
        <h3>{titulo}</h3>
        {modo === 'lista' && (
          <button type="button" className="convenios-btn-ghost sm" onClick={abrirCriacao}>
            <Plus size={13} strokeWidth={2} />Adicionar
          </button>
        )}
      </div>

      {itens.length === 0 && modo === 'lista' ? (
        <div className="convenios-sub-vazio">
          <strong>{vazioTitulo}</strong>
          <span>{vazioTexto}</span>
        </div>
      ) : (
        <div className="convenios-tabela-scroll">
          <table className="convenios-tabela convenios-tabela-sub">
            <thead>
              <tr>
                {colunas.map((c) => <th key={c.key} className={c.align === 'num' ? 'num' : undefined}>{c.label}</th>)}
                <th style={{ width: 40 }} />
              </tr>
            </thead>
            <tbody>
              {itens.map((item) => (
                <tr key={item[idField]} onClick={() => modo === 'lista' && abrirEdicao(item)}>
                  {colunas.map((c) => (
                    <td key={c.key} className={c.align === 'num' ? 'num' : undefined}>{c.render(item)}</td>
                  ))}
                  <td><Pencil size={13} strokeWidth={2} className="convenios-sub-editicon" /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modo !== 'lista' && (
        <div className="convenios-sub-form">
          <div className="convenios-sub-form-head">
            <span>{modo === 'criando' ? 'Novo registro' : 'Editar registro'}</span>
            <button type="button" className="convenios-bclear" onClick={cancelar}><X size={15} strokeWidth={2} /></button>
          </div>
          {erro && <div className="convenios-modal-erro">{erro}</div>}
          <div className="convenios-sub-form-grid">
            {campos.map((campo) => (
              <div key={campo.key} className="convenios-modal-campo">
                {campo.type !== 'checkbox' && <label className="convenios-label">{campo.label}</label>}
                <Campo campo={campo} valor={form[campo.key]} onChange={(v) => setForm((f) => ({ ...f, [campo.key]: v }))} />
              </div>
            ))}
          </div>
          <div className="convenios-modal-footer">
            <button type="button" className="convenios-btn-ghost" onClick={cancelar}>Cancelar</button>
            <button type="button" className="convenios-btn-primario" onClick={salvar} disabled={campoObrigatorioVazio || salvando}>
              {salvando ? 'Salvando…' : 'Salvar'}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
