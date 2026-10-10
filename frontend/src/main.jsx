import React from 'react'
import ReactDOM from 'react-dom/client'
import { createBrowserRouter, Navigate, Outlet, RouterProvider } from 'react-router-dom'
import ClinisisLanding from '../../landing-page/src/ClinisisLanding.jsx'
import './index.css'
import { ThemeProvider } from './theme/ThemeContext'
import { AuthProvider } from './auth/AuthContext'
import RotaProtegida from './auth/RotaProtegida'
import RotaAutorizada from './auth/RotaAutorizada'
import MagicLink from './components/login/MagicLink'
import Conta from './auth/Conta'

// Importando as páginas que criamos
import Layout from './components/layout/Layout'
import PosVenda from './modules/pos-venda/PosVenda'
import Oportunidades from './modules/pos-venda/Oportunidades'
import PosVendaPacientes from './modules/pos-venda/Pacientes'
import Tarefas from './modules/pos-venda/Tarefas'
import Jornadas from './modules/pos-venda/Jornadas'
import Campanhas from './modules/pos-venda/Campanhas'
import { Dashboard } from './components/dashboard/dashboard'
import Login from './components/login/login'
import Agenda from './pages/Agenda'
import { Pacientes } from './components/pacientes/pacientes'
import { Prontuario } from './components/prontuario/Prontuario'
import { ProntuarioDetalhe } from './components/prontuario/ProntuarioDetalhe'
import { PaginaAtendimento } from './components/prontuario/PaginaAtendimento'
import { Whatsapp } from './components/whatsapp/whatsapp'
import { Medicos } from './components/medicos/medicos'
import { Caixa } from './components/caixa/caixa'
import { CaixaDre } from './components/caixa/CaixaDre'
import { CaixaFluxoConsolidado } from './components/caixa/CaixaFluxoConsolidado'
import { CaixaDespesas } from './components/caixa/CaixaDespesas'
import { Convenios } from './components/convenios/convenios'
import ConvenioDetalhePagina from './components/convenios/ConvenioDetalhePagina'
import GlosaDetalhePagina from './components/convenios/GlosaDetalhePagina'
import AuditoriaDetalhePagina from './components/convenios/AuditoriaDetalhePagina'
import LoteDetalhePagina from './components/convenios/LoteDetalhePagina'

// A página inicial é pública, independentemente da sessão. Apenas as rotas
// do sistema carregam autenticação e tema; o dashboard continua protegido.
const systemRoutes = [
  {
    element: <RotaProtegida><Layout /></RotaProtegida>,
    children: [
      { path: "dashboard", element: <RotaAutorizada permission={['dashboard.operacional.visualizar', 'dashboard.financeiro.visualizar']}><Dashboard /></RotaAutorizada> },
      { path: "pos-venda", element: <RotaAutorizada permission="oportunidade.visualizar"><PosVenda /></RotaAutorizada> },
      { path: "pos-venda/pacientes", element: <RotaAutorizada permission="oportunidade.visualizar"><PosVendaPacientes /></RotaAutorizada> },
      { path: "pos-venda/oportunidades", element: <RotaAutorizada permission="oportunidade.visualizar"><Oportunidades /></RotaAutorizada> },
      { path: "pos-venda/jornadas", element: <RotaAutorizada permission={['jornada.executar', 'jornada.configurar']}><Jornadas /></RotaAutorizada> },
      { path: "pos-venda/campanhas", element: <RotaAutorizada permission={['campanha.executar', 'campanha.configurar']}><Campanhas /></RotaAutorizada> },
      { path: "pos-venda/tarefas", element: <RotaAutorizada permission="tarefa.visualizar"><Tarefas /></RotaAutorizada> },
      { path: "pos-venda/whatsapp", element: <RotaAutorizada permission="whatsapp.visualizar"><Whatsapp /></RotaAutorizada> },
      { path: "agenda", element: <RotaAutorizada permission="agenda.visualizar"><Agenda /></RotaAutorizada> },
      { path: "conta", element: <Conta /> },
      { path: "pacientes", element: <RotaAutorizada permission="paciente.visualizar"><Pacientes /></RotaAutorizada> },
      { path: "pacientes/:pacienteId", element: <RotaAutorizada permission="prontuario.visualizar"><ProntuarioDetalhe /></RotaAutorizada> },
      { path: "prontuario", element: <RotaAutorizada permission="prontuario.visualizar"><Prontuario /></RotaAutorizada> },
      { path: "prontuario/atendimento", element: <RotaAutorizada permission="prontuario.editar"><PaginaAtendimento /></RotaAutorizada> },
      { path: "prontuario/:pacienteId", element: <RotaAutorizada permission="prontuario.visualizar"><ProntuarioDetalhe /></RotaAutorizada> },
      { path: "whatsapp", element: <RotaAutorizada permission="whatsapp.visualizar"><Whatsapp /></RotaAutorizada> },
      { path: "medicos", element: <RotaAutorizada permission="medico.visualizar"><Medicos /></RotaAutorizada> },
      { path: "caixa", element: <RotaAutorizada permission="caixa.visualizar"><Caixa /></RotaAutorizada> },
      { path: "caixa/dre", element: <RotaAutorizada permission="financeiro.dre.visualizar"><CaixaDre /></RotaAutorizada> },
      { path: "caixa/fluxo-consolidado", element: <RotaAutorizada permission="financeiro.fluxo.visualizar"><CaixaFluxoConsolidado /></RotaAutorizada> },
      { path: "caixa/despesas", element: <RotaAutorizada permission="despesa.visualizar"><CaixaDespesas /></RotaAutorizada> },
      { path: "convenios", element: <RotaAutorizada permission="convenio.gerenciar"><Convenios /></RotaAutorizada> },
      { path: "convenios/glosas/:id", element: <RotaAutorizada permission="glosa.visualizar"><GlosaDetalhePagina /></RotaAutorizada> },
      { path: "convenios/auditoria/:id", element: <RotaAutorizada permission="convenio.gerenciar"><AuditoriaDetalhePagina /></RotaAutorizada> },
      { path: "convenios/lotes/:id", element: <RotaAutorizada permission="convenio.gerenciar"><LoteDetalhePagina /></RotaAutorizada> },
      { path: "convenios/:id", element: <RotaAutorizada permission="convenio.gerenciar"><ConvenioDetalhePagina /></RotaAutorizada> },
    ],
  },
  {
    path: "/login",
    element: <Login />,
  },
  { path: '/clinicas', element: <Navigate to="/dashboard" replace /> },
  { path: '/minha-conta', element: <RotaProtegida><Conta /></RotaProtegida> },
  { path: '/login/magic', element: <MagicLink /> },
]

const router = createBrowserRouter([
  { path: '/', element: <ClinisisLanding loginUrl="/login" /> },
  {
    element: <AuthProvider><ThemeProvider><Outlet /></ThemeProvider></AuthProvider>,
    children: systemRoutes,
  },
])

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>,
)
