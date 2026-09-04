import React from 'react'
import ReactDOM from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import './index.css'
import { ThemeProvider } from './theme/ThemeContext'
import { AuthProvider } from './auth/AuthContext'
// RotaProtegida desligada a pedido explícito — ver SecurityConfig.java pro
// mesmo motivo no backend. Pra reativar: importa de volta e envolve o
// Layout com <RotaProtegida /> como antes.

// Importando as páginas que criamos
import Layout from './components/layout/Layout'
import { Dashboard } from './components/dashboard/dashboard'
import Login from './components/login/login'
import Agenda from './pages/Agenda'
import { Pacientes } from './components/pacientes/pacientes'
import { Prontuario } from './components/prontuario/Prontuario'
import { ProntuarioDetalhe } from './components/prontuario/ProntuarioDetalhe'
import { PaginaAtendimento } from './components/prontuario/PaginaAtendimento'
import { Retorno } from './components/retorno/retorno'
import { Whatsapp } from './components/whatsapp/whatsapp'
import { Medicos } from './components/medicos/medicos'
import { Caixa } from './components/caixa/caixa'
import { Convenios } from './components/convenios/convenios'
import ConvenioDetalhePagina from './components/convenios/ConvenioDetalhePagina'
import GlosaDetalhePagina from './components/convenios/GlosaDetalhePagina'

// Configurando o roteador com os caminhos e seus respectivos componentes.
// Login fica fora do Layout (sem sidebar); as demais telas navegam pela
// sidebar. Sem RotaProtegida por ora — nenhuma rota exige sessão (ver nota
// acima sobre autenticação desligada).
const router = createBrowserRouter([
  {
    path: "/",
    element: <Layout />,
    children: [
      { index: true, element: <Dashboard /> },
      { path: "agenda", element: <Agenda /> },
      { path: "pacientes", element: <Pacientes /> },
      { path: "pacientes/:pacienteId", element: <ProntuarioDetalhe /> },
      { path: "prontuario", element: <Prontuario /> },
      { path: "prontuario/atendimento", element: <PaginaAtendimento /> },
      { path: "prontuario/:pacienteId", element: <ProntuarioDetalhe /> },
      { path: "retorno", element: <Retorno /> },
      { path: "whatsapp", element: <Whatsapp /> },
      { path: "medicos", element: <Medicos /> },
      { path: "caixa", element: <Caixa /> },
      { path: "convenios", element: <Convenios /> },
      { path: "convenios/glosas/:id", element: <GlosaDetalhePagina /> },
      { path: "convenios/:id", element: <ConvenioDetalhePagina /> },
    ],
  },
  {
    path: "/login",
    element: <Login />,
  },
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
