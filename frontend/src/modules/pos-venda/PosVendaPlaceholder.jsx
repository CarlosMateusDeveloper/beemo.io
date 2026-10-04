import './PosVenda.css'

export default function PosVendaPlaceholder({ title }) {
  return <main className="pos-venda-module pos-venda-placeholder" aria-labelledby="pos-venda-section-title">
    <div className="pv-placeholder-card">
      <p>Pós-atendimento e retenção</p>
      <h1 id="pos-venda-section-title">{title}</h1>
      <span>Esta área está pronta para receber os dados do módulo.</span>
    </div>
  </main>
}
