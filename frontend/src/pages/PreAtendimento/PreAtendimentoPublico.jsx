import { useEffect, useMemo, useState } from 'react'
import { CheckCircle2, FileText, Lock, ShieldCheck, Upload } from 'lucide-react'
import { carregarFormulario, consultarLink, enviarAnexo, enviarFormulario, salvarRascunho, verificarIdentidade } from './api'
import './preAtendimento.css'

const TOKEN_KEY = 'clinisis-pre-atendimento-token'
const SESSION_KEY = 'clinisis-pre-atendimento-session'

function readToken() {
  const params = new URLSearchParams(window.location.hash.slice(1))
  const value = params.get('token') || sessionStorage.getItem(TOKEN_KEY) || ''
  if (value) sessionStorage.setItem(TOKEN_KEY, value)
  if (params.has('token')) history.replaceState(null, '', window.location.pathname)
  return value
}

export default function PreAtendimentoPublico() {
  const token = useMemo(() => readToken(), [])
  const sessionKey = `${SESSION_KEY}:${token}`
  const [session, setSession] = useState(() => sessionStorage.getItem(sessionKey) || '')
  const [clinic, setClinic] = useState('Sua clínica')
  const [step, setStep] = useState(session ? 'form' : 'verify')
  const [identity, setIdentity] = useState({ dataNascimento: '', cpfUltimos4: '' })
  const [data, setData] = useState(null)
  const [registration, setRegistration] = useState({})
  const [answers, setAnswers] = useState({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(() => token ? '' : 'Link de pré-atendimento inválido.')
  const [notice, setNotice] = useState('')
  const granted = data?.escopo || []
  const canEdit = granted.some(scope => ['cadastro', 'formulario', 'documentos'].includes(scope))

  useEffect(() => {
    if (!token) return
    consultarLink(token).then(result => setClinic(result.clinica)).catch(err => setError(err.message))
  }, [token])

  useEffect(() => {
    if (!session || step !== 'form') return
    carregarFormulario(token, session).then(result => {
      setData(result)
      setRegistration(result.escopo.includes('cadastro') ? {
        nome: result.nome || '', email: result.email || '', ddd: result.ddd || '', numero: result.numero || '',
        cep: result.cep || '', logradouro: result.logradouro || '', numeroEndereco: result.numeroEndereco || '',
        complemento: result.complemento || '', bairro: result.bairro || '', cidade: result.cidade || '', uf: result.uf || '',
        ...(result.dadosPropostos || {}),
      } : {})
      setAnswers(result.respostas || {})
      if (result.status === 'enviado' || result.status === 'aprovado') setStep('done')
    }).catch(err => { sessionStorage.removeItem(sessionKey); setSession(''); setStep('verify'); setError(err.message) })
  }, [session, sessionKey, step, token])

  async function verify(event) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const result = await verificarIdentidade(token, identity)
      sessionStorage.setItem(sessionKey, result.sessao); setSession(result.sessao); setStep('form')
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  async function save(showNotice = true) {
    setBusy(true); setError('')
    try {
      const result = await salvarRascunho(token, session, {
        versao: data.versao,
        dadosPropostos: granted.includes('cadastro') ? registration : {},
        respostas: granted.includes('formulario') ? answers : {},
      })
      setData(result); if (showNotice) setNotice('Rascunho salvo com segurança.')
      return result
    } catch (err) { setError(err.message); throw err } finally { setBusy(false) }
  }

  async function submit(event) {
    event.preventDefault(); setNotice('')
    try {
      const version = granted.some(scope => ['cadastro', 'formulario'].includes(scope)) ? (await save(false)).versao : data.versao
      await enviarFormulario(token, session, version)
      setStep('done')
    } catch (err) { setError(err.message) }
  }

  async function upload(event) {
    const file = event.target.files?.[0]; if (!file) return
    setBusy(true); setError('')
    try {
      await enviarAnexo(token, session, file)
      const fresh = await carregarFormulario(token, session); setData(fresh); setNotice('Documento anexado.')
    } catch (err) { setError(err.message) } finally { setBusy(false); event.target.value = '' }
  }

  return <main className="pre-public-shell">
    <div className="pre-public-top"><ShieldCheck size={22} /><strong>Clinisis</strong><span>{clinic}</span></div>
    <section className="pre-public-card">
      {step === 'verify' && <form onSubmit={verify}>
        <div className="pre-icon"><Lock /></div>
        <p className="pre-eyebrow">Acesso protegido</p><h1>Confirme que é você</h1>
        <p className="pre-lead">Antes de mostrar seus dados, precisamos confirmar duas informações.</p>
        <label>Data de nascimento<input type="date" required value={identity.dataNascimento} onChange={e => setIdentity({ ...identity, dataNascimento: e.target.value })} /></label>
        <label>4 últimos dígitos do CPF<input inputMode="numeric" pattern="[0-9]{4}" maxLength="4" required placeholder="0000" value={identity.cpfUltimos4} onChange={e => setIdentity({ ...identity, cpfUltimos4: e.target.value.replace(/\D/g, '') })} /></label>
        {error && <div className="pre-alert error">{error}</div>}
        <button className="pre-primary" disabled={busy}>{busy ? 'Confirmando…' : 'Continuar'}</button>
        <p className="pre-privacy">O link não dá acesso ao seu prontuário e expira automaticamente.</p>
      </form>}

      {step === 'form' && data && <form onSubmit={submit}>
        {granted.includes('formulario') && <p className="pre-eyebrow">{data.formulario} · versão {data.formularioVersao}</p>}<h1>Prepare sua consulta</h1>
        <p className="pre-lead">Confira o cadastro e responda às perguntas. Alterações serão revisadas pela clínica.</p>
        <div className="pre-appointment"><strong>{data.profissional}</strong><span>{new Date(`${data.dataConsulta}T12:00:00`).toLocaleDateString('pt-BR')} · {String(data.horaConsulta).slice(0, 5)}</span></div>
        {granted.includes('cadastro') && <><h2>Seus dados</h2>
        <div className="pre-grid">
          {[
            ['nome','Nome completo'],['email','E-mail'],['ddd','DDD'],['numero','Telefone'],['cep','CEP'],['logradouro','Logradouro'],
            ['numeroEndereco','Número'],['complemento','Complemento'],['bairro','Bairro'],['cidade','Cidade'],['uf','UF'],
          ].map(([key,label]) => <label key={key} className={key === 'nome' || key === 'logradouro' ? 'wide' : ''}>{label}<input value={registration[key] || ''} onChange={e => setRegistration({ ...registration, [key]: e.target.value })} /></label>)}
        </div></>}
        {granted.includes('formulario') && <><h2>Informações para a equipe</h2>
        {(data.campos || []).filter(field => !['telefone','endereco'].includes(field.chave)).map(field => <label key={field.chave}>{field.rotulo}{field.obrigatorio && ' *'}
          <textarea required={field.obrigatorio} rows={field.tipo === 'textarea' ? 4 : 2} value={answers[field.chave] || ''} onChange={e => setAnswers({ ...answers, [field.chave]: e.target.value })} />
        </label>)}</>}
        {granted.includes('documentos') && <><h2>Documentos</h2>
        <label className="pre-upload"><Upload size={20} /><span><strong>Adicionar PDF, JPG ou PNG</strong><small>Máximo de 8 MB por arquivo</small></span><input type="file" accept="application/pdf,image/jpeg,image/png" onChange={upload} disabled={busy} /></label>
        {(data.anexos || []).map(file => <div className="pre-file" key={file.id}><FileText size={17} />{file.nome}<span>{Math.ceil(file.tamanho / 1024)} KB</span></div>)}</>}
        {granted.includes('orientacoes') && <><h2>Orientações da clínica</h2>
        <div className="pre-guidance">{(data.orientacoes || []).map(item => <article key={item.titulo}><strong>{item.titulo}</strong><p>{item.texto}</p></article>)}</div></>}
        {error && <div className="pre-alert error">{error}</div>}{notice && <div className="pre-alert success">{notice}</div>}
        {canEdit && <div className="pre-actions">{granted.some(scope => ['cadastro', 'formulario'].includes(scope)) && <button type="button" className="pre-secondary" onClick={() => save().catch(() => {})} disabled={busy}>Salvar rascunho</button>}<button className="pre-primary" disabled={busy}>{busy ? 'Salvando…' : 'Enviar para a clínica'}</button></div>}
      </form>}

      {step === 'done' && <div className="pre-done"><CheckCircle2 /><p className="pre-eyebrow">Enviado com sucesso</p><h1>Pré-atendimento concluído</h1><p>A equipe da {clinic} fará a revisão. Se houver alguma pendência, ela entrará em contato.</p></div>}
    </section>
  </main>
}
