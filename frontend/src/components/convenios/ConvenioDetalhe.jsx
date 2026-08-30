import { useEffect, useState } from 'react'
import { ArrowLeft } from 'lucide-react'
import {
  fetchConvenio, atualizarConvenio,
  fetchPlanos, criarPlano, atualizarPlano,
  fetchProcedimentos, criarProcedimento, atualizarProcedimento,
  fetchRegras, criarRegra, atualizarRegra,
  fetchDocumentos, criarDocumento, atualizarDocumento,
} from './api'
import { TIPOS_REGRA, severidadeMeta, SEVERIDADES, brl } from './conveniosData'
import SubRecursoTabela from './SubRecursoTabela'

export default function ConvenioDetalhe({ id, onVoltar, onAtualizado }) {
  const [convenio, setConvenio] = useState(null)
  const [planos, setPlanos] = useState([])
  const [procedimentos, setProcedimentos] = useState([])
  const [regras, setRegras] = useState([])
  const [documentos, setDocumentos] = useState([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)

  const [formGeral, setFormGeral] = useState(null)
  const [salvandoGeral, setSalvandoGeral] = useState(false)
  const [erroGeral, setErroGeral] = useState(null)

  useEffect(() => {
    let cancelado = false
    setCarregando(true)
    Promise.all([fetchConvenio(id), fetchPlanos(id), fetchProcedimentos(id), fetchRegras(id), fetchDocumentos(id)])
      .then(([c, p, proc, r, d]) => {
        if (cancelado) return
        setConvenio(c)
        setFormGeral({ nome: c.nome, registroAns: c.registroAns, contato: c.contato ?? '', observacoes: c.observacoes ?? '', ativo: c.ativo })
        setPlanos(p)
        setProcedimentos(proc)
        setRegras(r)
        setDocumentos(d)
        setErro(null)
      })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [id])

  async function salvarGeral() {
    if (salvandoGeral) return
    setSalvandoGeral(true)
    setErroGeral(null)
    try {
      const atualizado = await atualizarConvenio(id, formGeral)
      setConvenio(atualizado)
      onAtualizado?.()
    } catch (err) {
      setErroGeral(err.message)
    } finally {
      setSalvandoGeral(false)
    }
  }

  if (carregando) {
    return <div className="convenios-painel"><div className="convenios-vazio">Carregando…</div></div>
  }

  if (erro || !convenio) {
    return (
      <div className="convenios-painel">
        <div className="convenios-vazio">
          <div className="convenios-vazio-titulo">Não foi possível abrir este convênio</div>
          <div className="convenios-vazio-texto">{erro}</div>
        </div>
      </div>
    )
  }

  const opcoesPlanos = planos.map((p) => ({ value: String(p.id), label: p.nome }))
  const opcoesProcedimentos = procedimentos.map((p) => ({ value: String(p.id), label: `${p.codigo} — ${p.descricao}` }))

  return (
    <div className="convenios-detalhe">
      <button type="button" className="convenios-voltar" onClick={onVoltar}>
        <ArrowLeft size={15} strokeWidth={2} />Voltar para convênios
      </button>

      <div className="convenios-detalhe-titulo">
        <h2>{convenio.nome}</h2>
        <span className={`convenios-badge ${convenio.ativo ? 'st-ok' : 'st-neutro'}`}>{convenio.ativo ? 'Ativo' : 'Inativo'}</span>
      </div>

      <section className="convenios-sub">
        <div className="convenios-sub-head"><h3>Dados gerais</h3></div>
        {erroGeral && <div className="convenios-modal-erro">{erroGeral}</div>}
        <div className="convenios-sub-form-grid">
          <div className="convenios-modal-campo">
            <label className="convenios-label">Nome</label>
            <input className="convenios-input" value={formGeral.nome} onChange={(e) => setFormGeral((f) => ({ ...f, nome: e.target.value }))} />
          </div>
          <div className="convenios-modal-campo">
            <label className="convenios-label">Registro ANS</label>
            <input className="convenios-input" value={formGeral.registroAns} maxLength={6} onChange={(e) => setFormGeral((f) => ({ ...f, registroAns: e.target.value }))} />
          </div>
          <div className="convenios-modal-campo">
            <label className="convenios-label">Contato</label>
            <input className="convenios-input" value={formGeral.contato} onChange={(e) => setFormGeral((f) => ({ ...f, contato: e.target.value }))} />
          </div>
          <div className="convenios-modal-campo">
            <label className="convenios-form-check" style={{ marginTop: 22 }}>
              <input type="checkbox" checked={formGeral.ativo} onChange={(e) => setFormGeral((f) => ({ ...f, ativo: e.target.checked }))} />
              Convênio ativo
            </label>
          </div>
          <div className="convenios-modal-campo" style={{ gridColumn: '1 / -1' }}>
            <label className="convenios-label">Observações</label>
            <textarea className="convenios-textarea" rows={2} value={formGeral.observacoes} onChange={(e) => setFormGeral((f) => ({ ...f, observacoes: e.target.value }))} />
          </div>
        </div>
        <div className="convenios-modal-footer">
          <button type="button" className="convenios-btn-primario" onClick={salvarGeral} disabled={salvandoGeral || !formGeral.nome.trim()}>
            {salvandoGeral ? 'Salvando…' : 'Salvar dados gerais'}
          </button>
        </div>
      </section>

      <SubRecursoTabela
        titulo="Planos"
        vazioTitulo="Nenhum plano cadastrado"
        vazioTexto="Planos permitem negociar procedimentos específicos por variação do convênio."
        itens={planos}
        campos={[
          { key: 'nome', label: 'Nome', type: 'text', obrigatorio: true },
          { key: 'codigo', label: 'Código', type: 'text' },
          { key: 'ativo', label: 'Ativo', type: 'checkbox', default: true },
        ]}
        colunas={[
          { key: 'nome', label: 'Nome', render: (p) => p.nome },
          { key: 'codigo', label: 'Código', render: (p) => p.codigo || '—' },
          { key: 'ativo', label: 'Status', render: (p) => <span className={`convenios-badge ${p.ativo ? 'st-ok' : 'st-neutro'}`}>{p.ativo ? 'Ativo' : 'Inativo'}</span> },
        ]}
        onCriar={async (dados) => { await criarPlano(id, dados); setPlanos(await fetchPlanos(id)) }}
        onAtualizar={async (idPlano, dados) => { await atualizarPlano(id, idPlano, dados); setPlanos(await fetchPlanos(id)) }}
      />

      <SubRecursoTabela
        titulo="Procedimentos"
        vazioTitulo="Nenhum procedimento cadastrado"
        vazioTexto="Cadastre os procedimentos negociados com este convênio, seus valores e cobertura."
        itens={procedimentos}
        campos={[
          { key: 'codigo', label: 'Código', type: 'text', obrigatorio: true },
          { key: 'descricao', label: 'Descrição', type: 'text', obrigatorio: true },
          { key: 'valorNegociado', label: 'Valor negociado', type: 'number', step: '0.01' },
          { key: 'plano', label: 'Plano', type: 'ref-select', options: opcoesPlanos, placeholder: 'Todos os planos' },
          { key: 'cobertura', label: 'Coberto', type: 'checkbox', default: true },
          { key: 'exigeAutorizacao', label: 'Exige autorização', type: 'checkbox' },
        ]}
        colunas={[
          { key: 'codigo', label: 'Código', render: (p) => p.codigo },
          { key: 'descricao', label: 'Descrição', render: (p) => p.descricao },
          { key: 'valor', label: 'Valor', align: 'num', render: (p) => p.valorNegociado != null ? brl(p.valorNegociado) : '—' },
          { key: 'cobertura', label: 'Cobertura', render: (p) => <span className={`convenios-badge ${p.cobertura ? 'st-ok' : 'st-perdida'}`}>{p.cobertura ? 'Coberto' : 'Não coberto'}</span> },
          { key: 'autorizacao', label: 'Autorização', render: (p) => p.exigeAutorizacao ? 'Exige' : '—' },
        ]}
        onCriar={async (dados) => { await criarProcedimento(id, dados); setProcedimentos(await fetchProcedimentos(id)) }}
        onAtualizar={async (idProc, dados) => { await atualizarProcedimento(id, idProc, dados); setProcedimentos(await fetchProcedimentos(id)) }}
      />

      <SubRecursoTabela
        titulo="Regras de auditoria"
        vazioTitulo="Nenhuma regra cadastrada"
        vazioTexto="Regras de auditoria bloqueiam ou sinalizam atendimentos com risco de glosa antes do faturamento."
        itens={regras}
        campos={[
          { key: 'tipo', label: 'Tipo', type: 'select', options: TIPOS_REGRA.map((t) => ({ value: t.valor, label: t.rotulo })), obrigatorio: true },
          { key: 'severidade', label: 'Severidade', type: 'select', options: SEVERIDADES.map((s) => ({ value: s.valor, label: s.rotulo })), obrigatorio: true },
          { key: 'descricao', label: 'Descrição', type: 'text', obrigatorio: true },
          { key: 'procedimento', label: 'Procedimento (opcional)', type: 'ref-select', options: opcoesProcedimentos, placeholder: 'Regra geral do convênio' },
          { key: 'ativo', label: 'Ativa', type: 'checkbox', default: true },
        ]}
        colunas={[
          { key: 'tipo', label: 'Tipo', render: (r) => TIPOS_REGRA.find((t) => t.valor === r.tipo)?.rotulo ?? r.tipo },
          { key: 'severidade', label: 'Severidade', render: (r) => <span className={`convenios-badge ${severidadeMeta(r.severidade).cls}`}>{severidadeMeta(r.severidade).rotulo}</span> },
          { key: 'descricao', label: 'Descrição', render: (r) => r.descricao },
          { key: 'ativo', label: 'Status', render: (r) => <span className={`convenios-badge ${r.ativo ? 'st-ok' : 'st-neutro'}`}>{r.ativo ? 'Ativa' : 'Inativa'}</span> },
        ]}
        onCriar={async (dados) => { await criarRegra(id, dados); setRegras(await fetchRegras(id)) }}
        onAtualizar={async (idRegra, dados) => { await atualizarRegra(id, idRegra, dados); setRegras(await fetchRegras(id)) }}
      />

      <SubRecursoTabela
        titulo="Documentos obrigatórios"
        vazioTitulo="Nenhum documento cadastrado"
        vazioTexto="Documentos exigidos pelo convênio para liberar o faturamento de um atendimento."
        itens={documentos}
        campos={[
          { key: 'nomeDocumento', label: 'Nome do documento', type: 'text', obrigatorio: true },
          { key: 'procedimento', label: 'Procedimento (opcional)', type: 'ref-select', options: opcoesProcedimentos, placeholder: 'Todos os procedimentos' },
          { key: 'obrigatorio', label: 'Obrigatório', type: 'checkbox', default: true },
        ]}
        colunas={[
          { key: 'nomeDocumento', label: 'Documento', render: (d) => d.nomeDocumento },
          { key: 'obrigatorio', label: 'Status', render: (d) => <span className={`convenios-badge ${d.obrigatorio ? 'st-warn' : 'st-neutro'}`}>{d.obrigatorio ? 'Obrigatório' : 'Opcional'}</span> },
        ]}
        onCriar={async (dados) => { await criarDocumento(id, dados); setDocumentos(await fetchDocumentos(id)) }}
        onAtualizar={async (idDoc, dados) => { await atualizarDocumento(id, idDoc, dados); setDocumentos(await fetchDocumentos(id)) }}
      />
    </div>
  )
}
