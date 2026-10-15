import { useEffect, useState } from 'react'
import { CheckCircle2, Copy, KeyRound } from 'lucide-react'
import { fetchWhatsappConfiguracao, salvarWhatsappConfiguracao } from './api'
import { serviceBase } from '../../lib/apiClient'

export default function WhatsappConfiguracao() {
  const [config, setConfig] = useState(null)
  const [form, setForm] = useState({ phoneNumberId: '', businessAccountId: '', displayPhoneNumber: '', accessToken: '', appSecret: '', verifyToken: '', active: true })
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  useEffect(() => { fetchWhatsappConfiguracao().then((data) => { setConfig(data); setForm((old) => ({ ...old, phoneNumberId: data.phoneNumberId || '', businessAccountId: data.businessAccountId || '', displayPhoneNumber: data.displayPhoneNumber || '', active: data.active ?? true })) }).catch((reason) => setError(reason.message)) }, [])
  const webhook = config?.webhookPath ? `${serviceBase(import.meta.env.VITE_CHATBOT_API_URL, 8082)}${config.webhookPath}` : ''
  async function save(event) {
    event.preventDefault(); setBusy(true); setError(''); setMessage('')
    try {
      const result = await salvarWhatsappConfiguracao(form)
      setConfig(result); setMessage('Configuração salva. Os segredos ficaram criptografados.')
      setForm((old) => ({ ...old, accessToken: '', appSecret: '', verifyToken: '' }))
    } catch (reason) { setError(reason.message) } finally { setBusy(false) }
  }
  return <section className="whatsapp-config">
    <header><div><span className="whatsapp-config-icon"><KeyRound size={18} /></span><h2>WhatsApp Business</h2><p>Conecte o número oficial da clínica. Os segredos nunca voltam para o navegador.</p></div></header>
    <form onSubmit={save}>
      <label>ID do número<input required value={form.phoneNumberId} onChange={(e) => setForm({ ...form, phoneNumberId: e.target.value })} /></label>
      <label>ID da conta empresarial<input value={form.businessAccountId} onChange={(e) => setForm({ ...form, businessAccountId: e.target.value })} /></label>
      <label>Número exibido<input placeholder="+55 85 99999-9999" value={form.displayPhoneNumber} onChange={(e) => setForm({ ...form, displayPhoneNumber: e.target.value })} /></label>
      <label>Token de acesso<input type="password" placeholder={config?.accessTokenConfigured ? 'Configurado — deixe vazio para manter' : ''} value={form.accessToken} onChange={(e) => setForm({ ...form, accessToken: e.target.value })} /></label>
      <label>App Secret<input type="password" placeholder={config?.appSecretConfigured ? 'Configurado — deixe vazio para manter' : ''} value={form.appSecret} onChange={(e) => setForm({ ...form, appSecret: e.target.value })} /></label>
      <label>Token de verificação<input type="password" placeholder={config?.configured ? 'Deixe vazio para manter' : 'Crie um valor difícil de adivinhar'} value={form.verifyToken} onChange={(e) => setForm({ ...form, verifyToken: e.target.value })} /></label>
      <label className="whatsapp-config-check"><input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Integração ativa</label>
      {webhook && <label>URL do webhook<div className="whatsapp-config-webhook"><code>{webhook}</code><button type="button" onClick={() => navigator.clipboard.writeText(webhook)} aria-label="Copiar URL"><Copy size={15} /></button></div></label>}
      {error && <p className="whatsapp-config-error" role="alert">{error}</p>}
      {message && <p className="whatsapp-config-success"><CheckCircle2 size={15} /> {message}</p>}
      <button className="whatsapp-primary" disabled={busy}>{busy ? 'Salvando…' : 'Salvar configuração'}</button>
    </form>
  </section>
}
