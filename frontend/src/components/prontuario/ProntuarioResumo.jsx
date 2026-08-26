import { Pencil } from 'lucide-react'

const TIPO_ALERGIA_LABEL = { MEDICAMENTOSA: 'Medicamentosa', ALIMENTAR: 'Alimentar', AMBIENTAL: 'Ambiental', OUTRA: 'Outra' }

function enderecoTxt(endereco) {
  if (!endereco) return null
  const linha1 = [endereco.logradouro, endereco.numero].filter(Boolean).join(', ') + (endereco.complemento ? ` · ${endereco.complemento}` : '')
  const linha2 = [endereco.bairro, endereco.cidade && endereco.uf ? `${endereco.cidade}/${endereco.uf}` : endereco.cidade].filter(Boolean).join(' · ')
  const cepTxt = endereco.cep ? `CEP ${endereco.cep.replace(/(\d{5})(\d{3})/, '$1-$2')}` : null
  return [linha1 || null, linha2 || null, cepTxt].filter(Boolean)
}

export default function ProntuarioResumo({ cadastro, alergias, comorbidades, medicamentos, onEditarCadastro }) {
  const endereco = enderecoTxt(cadastro?.endereco)

  return (
    <div className="prontuario-resumo-grid">
      <div className="prontuario-resumo-card">
        <div className="prontuario-resumo-titulo">
          Dados cadastrais
          <button type="button" className="prontuario-resumo-editar" onClick={onEditarCadastro} aria-label="Editar dados cadastrais">
            <Pencil size={13} strokeWidth={2} />
          </button>
        </div>
        <div className="prontuario-resumo-lista">
          <div>
            <div className="prontuario-resumo-item-nome">Telefone</div>
            <div className="prontuario-resumo-item-sub">{cadastro?.telefone || '—'}</div>
          </div>
          <div>
            <div className="prontuario-resumo-item-nome">E-mail</div>
            <div className="prontuario-resumo-item-sub">{cadastro?.email || '—'}</div>
          </div>
          <div>
            <div className="prontuario-resumo-item-nome">Convênio</div>
            <div className="prontuario-resumo-item-sub">{cadastro?.convenio || 'Particular'}</div>
          </div>
          <div>
            <div className="prontuario-resumo-item-nome">Endereço</div>
            {endereco ? endereco.map((linha, i) => <div key={i} className="prontuario-resumo-item-sub">{linha}</div>)
              : <div className="prontuario-resumo-item-sub">Não cadastrado</div>}
          </div>
        </div>
      </div>

      <div className="prontuario-resumo-card">
        <div className="prontuario-resumo-titulo">Alergias</div>
        {alergias.length === 0 ? (
          <div className="prontuario-resumo-vazio">Nenhuma alergia registrada.</div>
        ) : (
          <div className="prontuario-resumo-lista">
            {alergias.map((a, i) => (
              <div key={i}>
                <div className={`prontuario-resumo-item-nome prontuario-resumo-gravidade-${a.gravidade}`}>{a.substancia}</div>
                <div className="prontuario-resumo-item-sub">{TIPO_ALERGIA_LABEL[a.tipo] ?? a.tipo} · {a.gravidade.toLowerCase()}</div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="prontuario-resumo-card">
        <div className="prontuario-resumo-titulo">Comorbidades</div>
        {comorbidades.length === 0 ? (
          <div className="prontuario-resumo-vazio">Nenhuma comorbidade ativa.</div>
        ) : (
          <div className="prontuario-resumo-lista">
            {comorbidades.map((c, i) => (
              <div key={i}>
                <div className="prontuario-resumo-item-nome">{c.descricao}</div>
                {c.codigoCid && <div className="prontuario-resumo-item-sub">CID {c.codigoCid}</div>}
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="prontuario-resumo-card">
        <div className="prontuario-resumo-titulo">Medicamentos em uso contínuo</div>
        {medicamentos.length === 0 ? (
          <div className="prontuario-resumo-vazio">Nenhum medicamento em uso contínuo.</div>
        ) : (
          <div className="prontuario-resumo-lista">
            {medicamentos.map((m, i) => (
              <div key={i}>
                <div className="prontuario-resumo-item-nome">{m.medicamento}</div>
                <div className="prontuario-resumo-item-sub">{[m.dosagem, m.posologia].filter(Boolean).join(' · ') || '—'}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
