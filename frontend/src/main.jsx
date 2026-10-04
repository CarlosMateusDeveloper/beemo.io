import React from 'react'
import ReactDOM from 'react-dom/client'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'
import './index.css'
import { ThemeProvider } from './theme/ThemeContext'
import { AuthProvider } from './auth/AuthContext'
import RotaProtegida from './auth/RotaProtegida'
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

// Configurando o roteador com os caminhos e seus respectivos componentes.
// Login fica fora do Layout (sem sidebar); as demais telas navegam pela
// sidebar, protegidas pela sessão.
const router = createBrowserRouter([
  {
    path: "/",
    element: <RotaProtegida><Layout /></RotaProtegida>,
    children: [
      { index: true, element: <Dashboard /> },
      { path: "pos-venda", element: <PosVenda /> },
      { path: "pos-venda/pacientes", element: <PosVendaPacientes /> },
      { path: "pos-venda/oportunidades", element: <Oportunidades /> },
      { path: "pos-venda/jornadas", element: <Jornadas /> },
      { path: "pos-venda/campanhas", element: <Campanhas /> },
      { path: "pos-venda/tarefas", element: <Tarefas /> },
      { path: "pos-venda/whatsapp", element: <Whatsapp /> },
      { path: "agenda", element: <Agenda /> },
      { path: "conta", element: <Conta /> },
      { path: "pacientes", element: <Pacientes /> },
      { path: "pacientes/:pacienteId", element: <ProntuarioDetalhe /> },
      { path: "prontuario", element: <Prontuario /> },
      { path: "prontuario/atendimento", element: <PaginaAtendimento /> },
      { path: "prontuario/:pacienteId", element: <ProntuarioDetalhe /> },
      { path: "whatsapp", element: <Whatsapp /> },
      { path: "medicos", element: <Medicos /> },
      { path: "caixa", element: <Caixa /> },
      { path: "caixa/dre", element: <CaixaDre /> },
      { path: "caixa/fluxo-consolidado", element: <CaixaFluxoConsolidado /> },
      { path: "caixa/despesas", element: <CaixaDespesas /> },
      { path: "convenios", element: <Convenios /> },
      { path: "convenios/glosas/:id", element: <GlosaDetalhePagina /> },
      { path: "convenios/auditoria/:id", element: <AuditoriaDetalhePagina /> },
      { path: "convenios/lotes/:id", element: <LoteDetalhePagina /> },
      { path: "convenios/:id", element: <ConvenioDetalhePagina /> },
    ],
  },
  {
    path: "/login",
    element: <Login />,
  },
  { path: '/clinicas', element: <Navigate to="/" replace /> },
  { path: '/minha-conta', element: <RotaProtegida><Conta /></RotaProtegida> },
  { path: '/login/magic', element: <MagicLink /> },
])

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <AuthProvider>
      <ThemeProvider>
        {/* Passamos o 'router' que configuramos acima como propriedade */}
        <RouterProvider router={router} />
      </ThemeProvider>
    </AuthProvider>
  </React.StrictMode>,
)
