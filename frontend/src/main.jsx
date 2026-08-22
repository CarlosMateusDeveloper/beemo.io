import React from 'react'
import ReactDOM from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import './index.css'
import { ThemeProvider } from './theme/ThemeContext'

// Importando as páginas que criamos
import Layout from './components/layout/Layout'
import { Dashboard } from './components/dashboard/dashboard'
import Login from './components/login/login'
import Agenda from './pages/Agenda'
import { Pacientes } from './components/pacientes/pacientes'
import { Whatsapp } from './components/whatsapp/whatsapp'
import { Medicos } from './components/medicos/medicos'
import { Caixa } from './components/caixa/caixa'
import { Convenios } from './components/convenios/convenios'

// Configurando o roteador com os caminhos e seus respectivos componentes.
// Login fica fora do Layout (sem sidebar); as demais telas navegam pela sidebar.
const router = createBrowserRouter([
  {
    path: "/",
    element: <Layout />,
    children: [
      { index: true, element: <Dashboard /> },
      { path: "agenda", element: <Agenda /> },
      { path: "pacientes", element: <Pacientes /> },
      { path: "whatsapp", element: <Whatsapp /> },
      { path: "medicos", element: <Medicos /> },
      { path: "caixa", element: <Caixa /> },
      { path: "convenios", element: <Convenios /> },
    ],
  },
  {
    path: "/login",
    element: <Login />,
  },
])

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ThemeProvider>
      {/* Passamos o 'router' que configuramos acima como propriedade */}
      <RouterProvider router={router} />
    </ThemeProvider>
  </React.StrictMode>,
)
