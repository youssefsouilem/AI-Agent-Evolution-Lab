import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import MainLayout from './layouts/MainLayout'
import { AuthProvider, RequireRole } from './components/common/AuthContext'
import HomePage from './pages/HomePage'
import DashboardPage from './pages/anomaly-monitoring/DashboardPage'
import ChecksPage from './pages/anomaly-monitoring/ChecksPage'
import CheckDetailPage from './pages/anomaly-monitoring/CheckDetailPage'
import AlertsPage from './pages/anomaly-monitoring/AlertsPage'
import ModelsPage from './pages/anomaly-monitoring/ModelsPage'
import AgentsListPage from './pages/agent-management/AgentsListPage'
import NewAgentPage from './pages/agent-management/NewAgentPage'
import AgentEditorPage from './pages/agent-management/AgentEditorPage'
import AgentTestsPage from './pages/agent-management/AgentTestsPage'
import AgentHistoryPage from './pages/agent-management/AgentHistoryPage'
import AdminServicesPage from './pages/agent-management/AdminServicesPage'
import LlmModelsPage from './pages/agent-management/LlmModelsPage'
import AdminUsersPage from './pages/agent-management/AdminUsersPage'
import AdminAuditPage from './pages/agent-management/AdminAuditPage'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<MainLayout />}>
            {/* Plateforme / Accueil */}
            <Route path="/" element={<HomePage />} />

            {/* Monitoring M6 (teammate routes preserved) */}
            <Route path="/monitoring" element={<DashboardPage />} />
            <Route path="/monitoring/checks" element={<ChecksPage />} />
            <Route path="/monitoring/checks/:id" element={<CheckDetailPage />} />
            <Route path="/monitoring/alerts" element={<AlertsPage />} />
            <Route path="/monitoring/models" element={<ModelsPage />} />

            {/* Agent Management M1 */}
            <Route path="/agents" element={<AgentsListPage />} />
            <Route path="/agents/new" element={<NewAgentPage />} />
            <Route path="/agents/:id" element={<AgentEditorPage />} />
            <Route path="/agent-models" element={<LlmModelsPage />} />
            <Route path="/agent-tests" element={<AgentTestsPage />} />
            <Route path="/agent-history" element={<AgentHistoryPage />} />

            {/* Administration (Admin only) */}
            <Route
              path="/admin/services"
              element={
                <RequireRole role="ADMIN">
                  <AdminServicesPage />
                </RequireRole>
              }
            />
            <Route
              path="/admin/users"
              element={
                <RequireRole role="ADMIN">
                  <AdminUsersPage />
                </RequireRole>
              }
            />
            <Route
              path="/admin/audit"
              element={
                <RequireRole role="ADMIN">
                  <AdminAuditPage />
                </RequireRole>
              }
            />

            {/* Catch-all redirect to /agents */}
            <Route path="*" element={<Navigate to="/agents" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
