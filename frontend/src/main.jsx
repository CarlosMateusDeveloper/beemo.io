import React from 'react'
import ReactDOM from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import './index.css'
import { ThemeProvider } from './theme/ThemeContext'
import { AuthProvider } from './auth/AuthContext'
import RotaProtegida from './auth/RotaProtegida'

// Importando as páginas que criamos
import Layout from './components/layout/Layout'
import { Dashboard } from './components/dashboard/dashboard'
import Login from './components/login/login'
import Agenda from './pages/Agenda'
import { Pacientes } from './components/pacientes/pacientes'
import { Prontuario } from './components/prontuario/Prontuario'
import { ProntuarioDetalhe } from './components/prontuario/ProntuarioDetalhe'
import { Whatsapp } from './components/whatsapp/whatsapp'
import { Medicos } from './components/medicos/medicos'
import { Caixa } from './components/caixa/caixa'
import { Convenios } from './components/convenios/convenios'

// Configurando o roteador com os caminhos e seus respectivos componentes.
// Login fica fora da RotaProtegida (sem sidebar, sem exigir sessão); as
// demais telas exigem usuário autenticado (RotaProtegida) e navegam pela
// sidebar (Layout).
const router = createBrowserRouter([
  {
    path: "/",
    element: <RotaProtegida />,
    children: [
      {
        element: <Layout />,
        children: [
          { index: true, element: <Dashboard /> },
          { path: "agenda", element: <Agenda /> },
          { path: "pacientes", element: <Pacientes /> },
          { path: "pacientes/:pacienteId", element: <ProntuarioDetalhe /> },
          { path: "prontuario", element: <Prontuario /> },
          { path: "prontuario/:pacienteId", element: <ProntuarioDetalhe /> },
          { path: "whatsapp", element: <Whatsapp /> },
          { path: "medicos", element: <Medicos /> },
          { path: "caixa", element: <Caixa /> },
          { path: "convenios", element: <Convenios /> },
        ],
      },
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
