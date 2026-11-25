import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { clearCapabilitiesCache, getMe } from '../../services/agentApi'

/**
 * Authentication, Role and Quota Context for AI-Agent-Evolution-Lab.
 *
 * NOTE ON SECURITY:
 * Frontend roles and quota checks ONLY control what is displayed in the user interface
 * (affordances, progress bars, disabled buttons, admin routes).
 * The backend microservice M1 enforces all authorization, read-only mode, and quota limits
 * (returning 403 AGENT_QUOTA_EXCEEDED, 403 ADMIN_READ_ONLY, etc.).
 *
 * TODO (Keycloak integration in production):
 * In production with Keycloak, user identity and roles will be extracted from
 * the JWT token claim (realm_access.roles contains "admin" or "ROLE_ADMIN").
 * In dev / mock mode, identity is negotiated via GET /api/me and dev headers.
 */

const ROLE_STORAGE_KEY = 'evolution-lab-role'

const AuthContext = createContext({
  user: { id: 'demo-user', name: 'Utilisateur démo', role: 'USER' },
  role: 'USER',
  isAdmin: false,
  limits: { maxAgents: 5 },
  usage: { agents: 0 },
  capabilities: {},
  refreshProfile: () => {},
  setRole: () => {},
  toggleRole: () => {},
  logout: () => {},
  toast: null,
  clearToast: () => {},
  setToast: () => {},
})

export function AuthProvider({ children }) {
  const [role, setRoleState] = useState(() => {
    try {
      return localStorage.getItem(ROLE_STORAGE_KEY) || 'USER'
    } catch {
      return 'USER'
    }
  })
  const [profile, setProfile] = useState(null)
  const [toast, setToast] = useState(null)

  const isAdmin = role === 'ADMIN'

  const refreshProfile = useCallback(async () => {
    try {
      const data = await getMe()
      if (data) {
        setProfile(data)
        if (data.role && data.role !== role) {
          setRoleState(data.role)
          try {
            localStorage.setItem(ROLE_STORAGE_KEY, data.role)
          } catch {
            // ignore
          }
        }
      }
    } catch {
      // Fallback if GET /api/me is 404 or fails
      setProfile((prev) => ({
        ...prev,
        userId: isAdmin ? 'demo-admin' : 'demo-user',
        displayName: isAdmin ? 'Administrateur démo' : 'Utilisateur démo',
        role,
        limits: { maxAgents: isAdmin ? null : 5 },
        usage: prev?.usage || { agents: 0 },
        capabilities: {
          history: true,
          overrides: true,
          assist: true,
          llmCatalog: true,
          stats: true,
          quota: true,
        },
      }))
    }
  }, [role, isAdmin])

  useEffect(() => {
    refreshProfile()
  }, [refreshProfile])

  const setRole = (newRole) => {
    const validRole = newRole === 'ADMIN' ? 'ADMIN' : 'USER'
    setRoleState(validRole)
    try {
      localStorage.setItem(ROLE_STORAGE_KEY, validRole)
    } catch {
      // ignore
    }
    clearCapabilitiesCache()
    setToast(`Rôle changé en ${validRole === 'ADMIN' ? 'Administrateur' : 'Utilisateur'}`)
    setTimeout(() => {
      refreshProfile()
    }, 50)
  }

  const toggleRole = () => {
    setRole(role === 'ADMIN' ? 'USER' : 'ADMIN')
  }

  const logout = () => {
    try {
      localStorage.removeItem(ROLE_STORAGE_KEY)
    } catch {
      // ignore
    }
    setRoleState('USER')
    clearCapabilitiesCache()
    setToast('Déconnexion effectuée (session réinitialisée)')
  }

  const clearToast = () => setToast(null)

  const user = {
    id: profile?.userId || 'dev-user',
    name: profile?.displayName || (isAdmin ? 'Administrateur' : 'Utilisateur'),
    role,
  }

  const limits = profile?.limits ?? { maxAgents: isAdmin ? null : 5 }
  const usage = profile?.usage ?? { agents: 0 }
  const capabilities = profile?.capabilities ?? {
    history: true,
    overrides: true,
    assist: true,
    llmCatalog: true,
    stats: true,
    quota: true,
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        role,
        isAdmin,
        limits,
        usage,
        capabilities,
        refreshProfile,
        setRole,
        toggleRole,
        logout,
        toast,
        clearToast,
        setToast,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}

/**
 * Route guard that requires a specific role (e.g. ADMIN).
 * If the user does not have the required role, redirects to /agents
 * with an access denied message.
 */
export function RequireRole({ role = 'ADMIN', children }) {
  const { user } = useAuth()
  const location = useLocation()

  if (role === 'ADMIN' && user.role !== 'ADMIN') {
    return (
      <Navigate
        to="/agents"
        replace
        state={{
          flashMessage: 'Accès réservé aux administrateurs',
          from: location.pathname,
        }}
      />
    )
  }

  return children
}
