import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import './Layout.css'

export default function Layout() {
  return (
    <div className="app-layout">
      <Sidebar />
      <div className="app-content">
        <Outlet />
      </div>
    </div>
  )
}
