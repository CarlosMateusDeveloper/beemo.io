import { useEffect, useState } from 'react'
import { AlertTriangle, CheckCircle2, ClipboardList, Search, Users } from 'lucide-react'
import { apiRequest } from '../lib/apiClient'
import './SaasPainel.css'

export default function SaasPainel() {
  const [data,setData]=useState({itens:[],total:0,fontes:{}}),[actions,setActions]=useState([]),[search,setSearch]=useState(''),[error,setError]=useState('')
  async function load(query=search) { try { const [clinics,tasks]=await Promise.all([apiRequest(`/api/saas/clinicas?busca=${encodeURIComponent(query)}`),apiRequest('/api/saas/acoes')]);setData(clinics);setActions(tasks);setError('') } catch(e) { setError(e.message) } }
  useEffect(() => { let active=true; Promise.all([apiRequest('/api/saas/clinicas?busca='),apiRequest('/api/saas/acoes')]).then(([clinics,tasks])=>{if(active){setData(clinics);setActions(tasks)}}).catch(e=>{if(active)setError(e.message)}); return()=>{active=false} }, [])
  return <main className="saas-page"><header><div><span>Operação interna</span><h1>Saúde das clínicas</h1><p>Indicadores agregados, sem conteúdo clínico ou conversas.</p></div><div className="saas-total"><Users size={18}/>{data.total} clínicas</div></header>
    <form className="saas-search" onSubmit={e=>{e.preventDefault();load()}}><Search size={17}/><input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Buscar clínica"/><button>Buscar</button></form>
    {error&&<p className="saas-error">{error}</p>}
    <section className="saas-grid"><div className="saas-card"><h2>Carteira</h2>{data.itens.map(item=><article className="saas-clinic" key={item.id_clinica}><div><strong>{item.nome}</strong><small>Onboarding: {item.onboarding_status || 'sem dados'} · Uso: {item.acoes_30d ?? 'sem dados'} ações/30d</small></div><div className="saas-risks">{item.riscos.length ? item.riscos.map(r=><span key={r.codigo}><AlertTriangle size={13}/>{r.motivo}</span>) : <span className="ok"><CheckCircle2 size={13}/>Sem sinal ativo</span>}</div></article>)}{!data.itens.length&&<p>Nenhuma clínica encontrada.</p>}</div><div className="saas-card"><h2><ClipboardList size={17}/>Próximas ações</h2>{actions.filter(a=>a.status==='ABERTA').map(a=><article className="saas-action" key={a.id_acao}><strong>{a.titulo}</strong><small>{a.clinica} · {a.tipo}</small><button onClick={async()=>{await apiRequest(`/api/saas/acoes/${a.id_acao}/concluir`,{method:'POST'});load()}}>Concluir</button></article>)}{!actions.some(a=>a.status==='ABERTA')&&<p>Nenhuma ação pendente.</p>}</div></section>
    <footer>Fontes: uso por auditoria agregada; onboarding por projeção. Plano, cobrança e consumo aparecerão quando as issues #31 e #32 forem implementadas.</footer>
  </main>
}
