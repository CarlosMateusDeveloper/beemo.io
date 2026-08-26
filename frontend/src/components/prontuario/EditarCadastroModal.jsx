import { useEffect, useState } from 'react'
import { fetchPaciente, atualizarPaciente, fetchConvenios } from './api'

const UFS = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG',
  'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO',
]

function apenasDigitos(v) {
  return v.replace(/\D/g, '')
}

export default function EditarCadastroModal({ pacienteId, onClose, onSalvo }) {
  const [form, setForm] = useState(null)
  const [convenios, setConvenios] = useState([])
  const [carregando, setCarregando] = useState(true)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState(null)

  useEffect(() => {
    let cancelado = false
    fetchConvenios().then((lista) => { if (!cancelado) setConvenios(lista) }).catch(() => {})
    fetchPaciente(pacienteId)
      .then((p) => {
        if (cancelado) return
        setForm({
          nome: p.nome || '', cpf: p.cpf || '', dataNascimento: p.dataNascimento || '',
          ddd: p.ddd || '', numero: p.numero || '', convenioId: p.convenio?.id ? String(p.convenio.id) : '',
          email: p.email || '', cep: p.cep || '', logradouro: p.logradouro || '',
          numeroEndereco: p.numeroEndereco || '', complemento: p.complemento || '',
          bairro: p.bairro || '', cidade: p.cidade || '', uf: p.uf || '',
        })
      })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [pacienteId])

  function atualizar(campo, valor) {
    setForm((f) => ({ ...f, [campo]: valor }))
  }

  const valido = !!form && form.nome.trim().length > 0
    && form.cpf.length === 11 && form.dataNascimento
    && form.ddd.length === 2 && form.numero.trim().length > 0

  async function salvar() {
    if (!valido || salvando) return
    setSalvando(true)
    setErro(null)
    try {
      await atualizarPaciente(pacienteId, {
        nome: form.nome.trim(),
        cpf: form.cpf,
        dataNascimento: form.dataNascimento,
        ddd: form.ddd,
        numero: form.numero.trim(),
        convenio: form.convenioId ? { id: Number(form.convenioId) } : null,
        email: form.email.trim() || null,
        cep: form.cep || null,
        logradouro: form.logradouro.trim() || null,
        numeroEndereco: form.numeroEndereco.trim() || null,
        complemento: form.complemento.trim() || null,
        bairro: form.bairro.trim() || null,
        cidade: form.cidade.trim() || null,
        uf: form.uf || null,
      })
      onSalvo()
    } catch (err) {
      setErro(err.message)
    } finally {
      setSalvando(false)
    }
  }

  return (
    <div className="prontuario-overlay" onClick={onClose}>
      <div className="prontuario-modal" onClick={(e) => e.stopPropagation()}>
        <div className="prontuario-modal-title">Editar dados cadastrais</div>
        <div className="prontuario-modal-subtitle">Contato, endereço e convênio do paciente</div>

        {erro && <div className="prontuario-modal-erro">{erro}</div>}

        {carregando || !form ? (
          <div className="prontuario-skel prontuario-skel-linha" style={{ width: '100%', height: 200 }} />
        ) : (
          <>
            <div className="prontuario-modal-campo">
              <label className="prontuario-label" htmlFor="ec-nome">Nome completo</label>
              <input id="ec-nome" className="prontuario-input" value={form.nome} onChange={(e) => atualizar('nome', e.target.value)} />
            </div>

            <div className="prontuario-modal-linha">
              <div className="prontuario-modal-campo">
                <label className="prontuario-label" htmlFor="ec-cpf">CPF</label>
                <input
                  id="ec-cpf" className="prontuario-input" value={form.cpf} maxLength={11}
                  onChange={(e) => atualizar('cpf', apenasDigitos(e.target.value))}
                />
              </div>
              <div className="prontuario-modal-campo">
                <label className="prontuario-label" htmlFor="ec-nasc">Nascimento</label>
                <input
                  id="ec-nasc" type="date" className="prontuario-input" value={form.dataNascimento}
                  onChange={(e) => atualizar('dataNascimento', e.target.value)}
                />
              </div>
            </div>

            <div className="prontuario-modal-linha">
              <div className="prontuario-modal-campo" style={{ flex: '0 0 70px' }}>
                <label className="prontuario-label" htmlFor="ec-ddd">DDD</label>
                <input
                  id="ec-ddd" className="prontuario-input" value={form.ddd} maxLength={2}
                  onChange={(e) => atualizar('ddd', apenasDigitos(e.target.value))}
                />
              </div>
              <div className="prontuario-modal-campo">
                <label className="prontuario-label" htmlFor="ec-num">Telefone</label>
                <input
                  id="ec-num" className="prontuario-input" value={form.numero} maxLength={10}
                  onChange={(e) => atualizar('numero', apenasDigitos(e.target.value))}
                />
              </div>
            </div>

            <div className="prontuario-modal-campo">
              <label className="prontuario-label" htmlFor="ec-email">E-mail</label>
              <input
                id="ec-email" type="email" className="prontuario-input" value={form.email}
                onChange={(e) => atualizar('email', e.target.value)} placeholder="paciente@exemplo.com"
              />
            </div>

            <div className="prontuario-modal-campo">
              <label className="prontuario-label" htmlFor="ec-conv">Convênio</label>
              <select
                id="ec-conv" className="prontuario-select" value={form.convenioId}
                onChange={(e) => atualizar('convenioId', e.target.value)}
              >
                <option value="">Particular</option>
                {convenios.map((c) => <option key={c.id} value={c.id}>{c.nome}</option>)}
              </select>
            </div>

            <div className="prontuario-modal-linha">
              <div className="prontuario-modal-campo" style={{ flex: '0 0 110px' }}>
                <label className="prontuario-label" htmlFor="ec-cep">CEP</label>
                <input
                  id="ec-cep" className="prontuario-input" value={form.cep} maxLength={8}
                  onChange={(e) => atualizar('cep', apenasDigitos(e.target.value))} placeholder="Somente números"
                />
              </div>
              <div className="prontuario-modal-campo">
                <label className="prontuario-label" htmlFor="ec-log">Logradouro</label>
                <input
                  id="ec-log" className="prontuario-input" value={form.logradouro}
                  onChange={(e) => atualizar('logradouro', e.target.value)} placeholder="Rua, avenida…"
                />
              </div>
            </div>

            <div className="prontuario-modal-linha">
              <div className="prontuario-modal-campo" style={{ flex: '0 0 90px' }}>
                <label className="prontuario-label" htmlFor="ec-numend">Número</label>
                <input id="ec-numend" className="prontuario-input" value={form.numeroEndereco} onChange={(e) => atualizar('numeroEndereco', e.target.value)} />
              </div>
              <div className="prontuario-modal-campo">
                <label className="prontuario-label" htmlFor="ec-compl">Complemento</label>
                <input id="ec-compl" className="prontuario-input" value={form.complemento} onChange={(e) => atualizar('complemento', e.target.value)} />
              </div>
            </div>

            <div className="prontuario-modal-linha">
              <div className="prontuario-modal-campo">
                <label className="prontuario-label" htmlFor="ec-bairro">Bairro</label>
                <input id="ec-bairro" className="prontuario-input" value={form.bairro} onChange={(e) => atualizar('bairro', e.target.value)} />
              </div>
              <div className="prontuario-modal-campo">
                <label className="prontuario-label" htmlFor="ec-cidade">Cidade</label>
                <input id="ec-cidade" className="prontuario-input" value={form.cidade} onChange={(e) => atualizar('cidade', e.target.value)} />
              </div>
              <div className="prontuario-modal-campo" style={{ flex: '0 0 70px' }}>
                <label className="prontuario-label" htmlFor="ec-uf">UF</label>
                <select id="ec-uf" className="prontuario-select" value={form.uf} onChange={(e) => atualizar('uf', e.target.value)}>
                  <option value="">—</option>
                  {UFS.map((uf) => <option key={uf} value={uf}>{uf}</option>)}
                </select>
              </div>
            </div>
          </>
        )}

        <div className="prontuario-modal-footer">
          <button type="button" className="prontuario-btn-ghost" onClick={onClose}>Cancelar</button>
          <button type="button" className="prontuario-btn-primario" onClick={salvar} disabled={!valido || salvando || carregando}>
            {salvando ? 'Salvando…' : 'Salvar alterações'}
          </button>
        </div>
      </div>
    </div>
  )
}
