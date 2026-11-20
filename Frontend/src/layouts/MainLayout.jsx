import { Activity, Bell, BrainCircuit, ChevronDown, Grid2X2, HelpCircle, Home, Menu, X } from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { useState } from 'react'
import './main-layout.css'

const links = [
  { to: '/monitoring', label: 'Tableau de bord', icon: Home, end: true },
  { to: '/monitoring/checks', label: 'Vérifications', icon: Activity },
  { to: '/monitoring/alerts', label: 'Alertes', icon: Bell },
  { to: '/monitoring/models', label: 'Modèles', icon: BrainCircuit },
]

const agentLinks = [
  { to: '/agents', label: 'Mes agents', icon: Bot, end: true },
  { to: '/agents/new', label: 'Nouvel agent', icon: Bot },
  { to: '/agent-tests', label: 'Tests', icon: TestTube },
]

export default function MainLayout() {
  const [open, setOpen] = useState(false)
  return (
    <div className="m6-root">
      <button className="m6-mobile-menu" onClick={() => setOpen(true)} aria-label="Ouvrir le menu"><Menu size={20} /></button>
      <aside className={`m6-sidebar ${open ? 'm6-sidebar-open' : ''}`}>
        <div className="m6-brand"><div className="m6-brand-icon"><Grid2X2 size={21} /></div><div><strong>Evolution Lab</strong><small>MONITORING</small></div></div>
        <div className="m6-workspace"><span className="m6-workspace-icon">E</span><strong>AI Agent Evolution Lab</strong><ChevronDown size={15} /></div>
        <div className="m6-side-label">SURVEILLANCE</div>
        <nav className="m6-nav">{links.map(({ to, label, icon: Icon, end }) => <NavLink key={to} to={to} end={end} onClick={() => setOpen(false)}><Icon size={18} /><span>{label}</span></NavLink>)}</nav>
        <div className="m6-side-label">AGENT STUDIO</div>
        <nav className="m6-nav">{agentLinks.map(({ to, label, icon: Icon, end }) => <NavLink key={to} to={to} end={end} onClick={() => setOpen(false)}><Icon size={18} /><span>{label}</span></NavLink>)}</nav>
        <div className="m6-sidebar-footer">{import.meta.env.VITE_USE_MOCK === 'true' && <div className="m6-demo"><HelpCircle size={16} /> Mode démonstration</div>}<div className="m6-divider" /><div className="m6-user"><div>M6</div><span><strong>Monitoring Manager</strong><small>Frontend React uniquement</small></span></div></div>
        {open && <button className="m6-close-menu" onClick={() => setOpen(false)} aria-label="Fermer le menu"><X size={20} /></button>}
      </aside>
      <main className="m6-main"><Outlet /></main>
    </div>
  )
}
