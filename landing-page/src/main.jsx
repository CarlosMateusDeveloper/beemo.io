import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import ClinisisLanding from './ClinisisLanding.jsx'
import './standalone.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <ClinisisLanding />
  </StrictMode>,
)
