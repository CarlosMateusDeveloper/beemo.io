import React from 'react'
import ReactDOM from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'

// Importando as páginas que criamos
import Layout from './components/layout/Layout'
import { Dashboard } from './components/dashboard/dashboard'
import Login from './components/login/login'
import Agenda from './pages/Agenda'
import { Pacientes } from './components/pacientes/pacientes'
import { Whatsapp } from './components/whatsapp/whatsapp'
import { Medicos } from './components/medicos/medicos'
import { Exames } from './components/exames/exames'
import { Financeiro } from './components/financeiro/financeiro'
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
      { path: "exames", element: <Exames /> },
      { path: "financeiro", element: <Financeiro /> },
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
    {/* Passamos o 'router' que configuramos acima como propriedade */}
    <RouterProvider router={router} />
  </React.StrictMode>,
)
