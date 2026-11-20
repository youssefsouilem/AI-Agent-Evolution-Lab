import { createContext, useContext, useEffect, useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'

/**
 * Authentication and Role Context for AI-Agent-Evolution-Lab.
 *
 * NOTE ON SECURITY:
 * The frontend role here ONLY changes what is displayed in the user interface
 * (e.g. admin navigation entries, ownerId display, supervision page).
 * The real security and authorization are strictly enforced by the backend
 * microservices (e.g. Spring Boot OAuth2 resource server / Keycloak JWT validation).
 *
 * TODO (Keycloak integration):
 * Later, the role must be extracted from the Keycloak JWT token claim:
 *   const roles = tokenParsed?.realm_access?.roles || []
 *   const isAdmin = roles.includes('admin') || roles.includes('ROLE_ADMIN')
 */

const ROLE_STORAGE_KEY = 'evolution-lab-role'

const AuthContext = createContext({
  user: { name: 'Utilisateur démo', role: 'USER' },
  role: 'USER',
  isAdmin: false,
  setRole: () => {},
  toggleRole: () => {},
  logout: () => {},
  toast: null,
  clearToast: () => {},
})

export function AuthProvider({ children }) {
  const [role, setRoleState] = useState(() => {
    try {
      return localStorage.getItem(ROLE_STORAGE_KEY) || 'USER'
    } catch {
      return 'USER'
    }
  })
  const [toast, setToast] = useState(null)

  useEffect(() => {
    try {
      localStorage.setItem(ROLE_STORAGE_KEY, role)
    } catch {
      // ignore storage errors
    }
  }, [role])

  const setRole = (newRole) => {
    const validRole = newRole === 'ADMIN' ? 'ADMIN' : 'USER'
    setRoleState(validRole)
    setToast(`Rôle changé en ${validRole === 'ADMIN' ? 'Administrateur' : 'Utilisateur'}`)
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
    setToast('Déconnexion effectuée (session réinitialisée)')
  }

  const clearToast = () => setToast(null)

  const isAdmin = role === 'ADMIN'
  const user = {
    name: isAdmin ? 'Administrateur démo' : 'Utilisateur démo',
    role,
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        role,
        isAdmin,
        setRole,
        toggleRole,
        logout,
        toast,
        clearToast,
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
