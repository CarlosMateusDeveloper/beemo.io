import { Construction } from 'lucide-react'

export default function ConveniosPlaceholder({ titulo, texto }) {
  return (
    <div className="convenios-painel">
      <div className="convenios-vazio">
        <span className="convenios-vazio-tile"><Construction size={20} strokeWidth={1.6} /></span>
        <div className="convenios-vazio-titulo">{titulo}</div>
        <div className="convenios-vazio-texto">{texto}</div>
      </div>
    </div>
  )
}
