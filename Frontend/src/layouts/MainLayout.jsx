import {
  Activity,
  BarChart2,
  Bot,
  ChevronDown,
  ClipboardList,
  Compass,
  Cpu,
  Grid2X2,
  HelpCircle,
  Home,
  Lock,
  LogOut,
  Menu,
  Server,
  Sparkles,
  UserCheck,
  Users,
  X,
} from 'lucide-react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { useAuth } from '../components/common/AuthContext'
import './main-layout.css'

export default function MainLayout() {
  const [mobileOpen, setMobileOpen] = useState(false)
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const location = useLocation()
  const { user, isAdmin, limits, usage, toggleRole, logout, toast, clearToast } = useAuth()

  // Expandable group states
  const isAgentRoute =
    location.pathname.startsWith('/agents') ||
    location.pathname.startsWith('/agent-models') ||
    location.pathname.startsWith('/agent-tests') ||
    location.pathname.startsWith('/agent-history')

  const isMissionRoute = location.pathname.startsWith('/missions')
  const isMonitoringRoute = location.pathname.startsWith('/monitoring')

  const [agentsExpanded, setAgentsExpanded] = useState(true)
  const [missionsExpanded, setMissionsExpanded] = useState(false)
  const [monitoringExpanded, setMonitoringExpanded] = useState(false)

  // Auto-expand active group
  useEffect(() => {
    if (isAgentRoute) setAgentsExpanded(true)
    if (isMissionRoute) setMissionsExpanded(true)
    if (isMonitoringRoute) setMonitoringExpanded(true)
  }, [isAgentRoute, isMissionRoute, isMonitoringRoute])

  const isMock = import.meta.env.VITE_USE_MOCK === 'true'

  return (
    <div className="m6-root">
      {/* Mobile hamburger button */}
      <button
        type="button"
        className="m6-mobile-menu"
        onClick={() => setMobileOpen(true)}
        aria-label="Ouvrir le menu"
      >
        <Menu size={20} />
      </button>

      {/* Main Sidebar */}
      <aside className={`m6-sidebar ${mobileOpen ? 'm6-sidebar-open' : ''}`}>
        {/* Brand Logo Header */}
        <div className="m6-brand">
          <div className="m6-brand-icon">
            <Grid2X2 size={21} />
          </div>
          <div>
            <strong>Evolution Lab</strong>
            <small>AI AGENT EVOLUTION LAB</small>
          </div>
        </div>

        {/* Workspace pill */}
        <div className="m6-workspace">
          <span className="m6-workspace-icon">E</span>
          <strong>AI Agent Evolution Lab</strong>
          <ChevronDown size={15} />
        </div>

        {/* Scrollable Navigation container */}
        <div className="m6-nav-scroll">
          <div className="m6-side-label">PLATEFORME</div>

          <nav className="m6-nav">
            {/* 1. Accueil */}
            <NavLink
              to="/"
              end
              onClick={() => setMobileOpen(false)}
              className={({ isActive }) => (isActive ? 'active' : '')}
            >
              <Home size={18} />
              <span>Accueil</span>
            </NavLink>

            {/* 2. Agents (M1) - Expandable */}
            <div className="m6-nav-group">
              <button
                type="button"
                className={`m6-group-header ${isAgentRoute ? 'group-active' : ''}`}
                onClick={() => setAgentsExpanded(!agentsExpanded)}
                aria-expanded={agentsExpanded}
              >
                <Bot size={18} />
                <span>Agents</span>
                <ChevronDown
                  size={14}
                  className={`m6-chevron ${agentsExpanded ? 'expanded' : ''}`}
                />
              </button>

              {agentsExpanded && (
                <div className="m6-sub-nav">
                  <NavLink
                    to="/agents"
                    end
                    onClick={() => setMobileOpen(false)}
                    className={({ isActive }) => (isActive ? 'active' : '')}
                  >
                    <span>Mes agents</span>
                  </NavLink>
                  <NavLink
                    to="/agents/new"
                    onClick={() => setMobileOpen(false)}
                    className={({ isActive }) => (isActive ? 'active' : '')}
                  >
                    <span>Nouvel agent</span>
                  </NavLink>
                  <NavLink
                    to="/agent-models"
                    onClick={() => setMobileOpen(false)}
                    className={({ isActive }) => (isActive ? 'active' : '')}
                  >
                    <span>Modèles LLM</span>
                  </NavLink>
                  <NavLink
                    to="/agent-tests"
                    onClick={() => setMobileOpen(false)}
                    className={({ isActive }) => (isActive ? 'active' : '')}
                  >
                    <span>Tests</span>
                  </NavLink>
                  <NavLink
                    to="/agent-history"
                    onClick={() => setMobileOpen(false)}
                    className={({ isActive }) => (isActive ? 'active' : '')}
                  >
                    <span>Historique</span>
                  </NavLink>

                  {!isAdmin && limits?.maxAgents && (
                    <div className="m6-quota-widget" title={`Quota: ${usage?.agents ?? 0} sur ${limits.maxAgents} agents utilisés`}>
                      <div className="m6-quota-header">
                        <span>Quota agents</span>
                        <strong>{usage?.agents ?? 0}/{limits.maxAgents}</strong>
                      </div>
                      <div className="m6-quota-bar">
                        <div
                          className={`m6-quota-fill ${(usage?.agents ?? 0) >= limits.maxAgents ? 'exceeded' : ''}`}
                          style={{
                            width: `${Math.min(100, Math.round(((usage?.agents ?? 0) / limits.maxAgents) * 100))}%`,
                          }}
                        />
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* 3. Sécurité MCP (M2) - Disabled */}
            <div className="m6-nav-item-disabled" title="Module M2 en cours de développement">
              <div className="m6-nav-item-content">
                <Lock size={18} />
                <span>Sécurité MCP</span>
              </div>
              <span className="m6-pill-badge">Bientôt</span>
            </div>

            {/* 4. Missions (M3) - Expandable */}
            <div className="m6-nav-group">
              <button
                type="button"
                className={`m6-group-header ${isMissionRoute ? 'group-active' : ''}`}
                onClick={() => setMissionsExpanded(!missionsExpanded)}
                aria-expanded={missionsExpanded}
              >
                <Compass size={18} />
                <span>Missions</span>
                <ChevronDown
                  size={14}
                  className={`m6-chevron ${missionsExpanded ? 'expanded' : ''}`}
                />
              </button>

              {missionsExpanded && (
                <div className="m6-sub-nav">
                  <div className="m6-sub-item-disabled">
                    <span>Accueil</span>
                    <span className="m6-pill-badge">Bientôt</span>
                  </div>
                  <div className="m6-sub-item-disabled">
                    <span>Mes missions</span>
                    <span className="m6-pill-badge">Bientôt</span>
                  </div>
                  <div className="m6-sub-item-disabled">
                    <span>Mission Studio</span>
                    <span className="m6-pill-badge">Bientôt</span>
                  </div>
                  <div className="m6-sub-item-disabled">
                    <span>Exécutions</span>
                    <span className="m6-pill-badge">Bientôt</span>
                  </div>
                  <div className="m6-sub-item-disabled">
                    <span>Historique</span>
                    <span className="m6-pill-badge">Bientôt</span>
                  </div>
                </div>
              )}
            </div>

            {/* 5. Benchmark & Safety (M4) - Disabled */}
            <div className="m6-nav-item-disabled" title="Module M4 en cours de développement">
              <div className="m6-nav-item-content">
                <BarChart2 size={18} />
                <span>Benchmark & Safety</span>
              </div>
              <span className="m6-pill-badge">Bientôt</span>
            </div>

            {/* 6. Optimisation (M5) - Disabled */}
            <div className="m6-nav-item-disabled" title="Module M5 en cours de développement">
              <div className="m6-nav-item-content">
                <Sparkles size={18} />
                <span>Optimisation</span>
              </div>
              <span className="m6-pill-badge">Bientôt</span>
            </div>

            {/* 7. Monitoring (M6) - Expandable / Working */}
            <div className="m6-nav-group">
              <button
                type="button"
                className={`m6-group-header ${isMonitoringRoute ? 'group-active' : ''}`}
                onClick={() => setMonitoringExpanded(!monitoringExpanded)}
                aria-expanded={monitoringExpanded}
              >
                <Activity size={18} />
                <span>Monitoring</span>
                <ChevronDown
                  size={14}
                  className={`m6-chevron ${monitoringExpanded ? 'expanded' : ''}`}
                />
              </button>

              {monitoringExpanded && (
                <div className="m6-sub-nav">
                  <NavLink
                    to="/monitoring"
                    end
                    onClick={() => setMobileOpen(false)}
                    className={({ isActive }) => (isActive ? 'active' : '')}
                  >
                    <span>Tableau de bord</span>
                  </NavLink>
                  <NavLink
                    to="/monitoring/checks"
                    onClick={() => setMobileOpen(false)}
                    className={({ isActive }) => (isActive ? 'active' : '')}
                  >
                    <span>Vérifications</span>
                  </NavLink>
                  <NavLink
                    to="/monitoring/alerts"
                    onClick={() => setMobileOpen(false)}
                    className={({ isActive }) => (isActive ? 'active' : '')}
                  >
                    <span>Alertes</span>
                  </NavLink>
                  <NavLink
                    to="/monitoring/models"
                    onClick={() => setMobileOpen(false)}
                    className={({ isActive }) => (isActive ? 'active' : '')}
                  >
                    <span>Modèles</span>
                  </NavLink>
                </div>
              )}
            </div>
          </nav>

          {/* Administration section - ADMIN ONLY */}
          {isAdmin && (
            <>
              <div className="m6-side-label" style={{ marginTop: 24 }}>
                ADMINISTRATION
              </div>
              <nav className="m6-nav">
                <NavLink
                  to="/admin/services"
                  onClick={() => setMobileOpen(false)}
                  className={({ isActive }) => (isActive ? 'active' : '')}
                >
                  <Server size={18} />
                  <span>Supervision des services</span>
                </NavLink>

                <NavLink
                  to="/admin/users"
                  onClick={() => setMobileOpen(false)}
                  className={({ isActive }) => (isActive ? 'active' : '')}
                >
                  <Users size={18} />
                  <span>Utilisateurs & quotas</span>
                </NavLink>

                <NavLink
                  to="/admin/audit"
                  onClick={() => setMobileOpen(false)}
                  className={({ isActive }) => (isActive ? 'active' : '')}
                >
                  <ClipboardList size={18} />
                  <span>Journal d'audit</span>
                </NavLink>
              </nav>
            </>
          )}
        </div>

        {/* Sidebar Footer */}
        <div className="m6-sidebar-footer">
          {isMock && (
            <div className="m6-demo">
              <HelpCircle size={15} />
              <span>Mode démonstration</span>
            </div>
          )}

          <div className="m6-divider" />

          {/* User Block with interactive Role Switcher dropdown */}
          <div className="m6-user-container">
            <button
              type="button"
              className="m6-user-button"
              onClick={() => setUserMenuOpen(!userMenuOpen)}
              aria-label="Menu utilisateur"
              aria-expanded={userMenuOpen}
            >
              <div className="m6-user-avatar">
                {isAdmin ? 'A' : 'U'}
              </div>
              <div className="m6-user-details">
                <strong>{user.name}</strong>
                <small>{isAdmin ? 'Administrateur' : 'Utilisateur'}</small>
              </div>
              <ChevronDown
                size={14}
                className={`m6-chevron ${userMenuOpen ? 'expanded' : ''}`}
                style={{ marginLeft: 'auto' }}
              />
            </button>

            {userMenuOpen && (
              <div className="m6-user-menu" onMouseLeave={() => setUserMenuOpen(false)}>
                <div className="m6-user-menu-header">
                  <strong>{user.name}</strong>
                  <span className="m6-user-role-badge">
                    {isAdmin ? 'Administrateur' : 'Utilisateur'}
                  </span>
                </div>

                <div className="m6-user-menu-divider" />

                <button
                  type="button"
                  className="m6-user-menu-item"
                  onClick={() => {
                    toggleRole()
                    setUserMenuOpen(false)
                  }}
                >
                  <UserCheck size={14} />
                  <span>
                    Changer de rôle (démo) → {isAdmin ? 'Utilisateur' : 'Administrateur'}
                  </span>
                </button>

                <button
                  type="button"
                  className="m6-user-menu-item"
                  onClick={() => {
                    logout()
                    setUserMenuOpen(false)
                  }}
                >
                  <LogOut size={14} />
                  <span>Se déconnecter</span>
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Mobile close button */}
        {mobileOpen && (
          <button
            type="button"
            className="m6-close-menu"
            onClick={() => setMobileOpen(false)}
            aria-label="Fermer le menu"
          >
            <X size={20} />
          </button>
        )}
      </aside>

      {/* Main page content */}
      <main className="m6-main">
        {toast && (
          <div className="m6-toast" role="status">
            <span>{toast}</span>
            <button type="button" onClick={clearToast} aria-label="Fermer notification">
              <X size={14} />
            </button>
          </div>
        )}
        <Outlet />
      </main>
    </div>
  )
}
