import { useNavigate, useParams } from 'react-router-dom'
import ConvenioDetalhe from './ConvenioDetalhe'
import './convenios.css'

export default function ConvenioDetalhePagina() {
  const { id } = useParams()
  const navigate = useNavigate()

  return (
    <div className="convenios-page">
      <ConvenioDetalhe id={id} onVoltar={() => navigate('/convenios')} onAtualizado={() => {}} />
    </div>
  )
}
