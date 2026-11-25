import MissionModule from './layouts/MissionModule'
import Accueil from './pages/mission-management/user/Accueil'
import MesMissions from './pages/mission-management/user/MesMissions'
import NouvelleMission from './pages/mission-management/user/NouvelleMission'
import MissionStudio from './pages/mission-management/user/MissionStudio'
import Executions from './pages/mission-management/user/Executions'
import Historique from './pages/mission-management/user/Historique'
import ExecutionDetail from './components/mission-management/ExecutionDetail'
import AdminDashboard from './pages/mission-management/admin/AdminDashboard'
import SupervisionMissions from './pages/mission-management/admin/SupervisionMissions'
import ParametresOrchestration from './pages/mission-management/admin/ParametresOrchestration'
import AutorisationsM3 from './pages/mission-management/admin/AutorisationsM3'
import InspectionMission from './pages/mission-management/admin/InspectionMission'
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

            <Route element={<MissionModule />}>
              <Route path="/missions/overview" element={<Accueil />} />
              <Route path="/missions" element={<MesMissions />} />
              <Route path="/missions/new" element={<NouvelleMission />} />
              <Route path="/studio" element={<MissionStudio />} />
              <Route path="/studio/:id" element={<MissionStudio />} />
              <Route path="/executions" element={<Executions />} />
              <Route path="/executions/:id" element={<ExecutionDetail />} />
              <Route path="/history" element={<Historique />} />
              <Route path="/admin" element={<AdminDashboard />} />
              <Route path="/admin/missions" element={<SupervisionMissions />} />
              <Route path="/admin/missions/:id" element={<InspectionMission />} />
              <Route path="/admin/executions" element={<SupervisionMissions key="executions" />} />
              <Route path="/admin/settings" element={<ParametresOrchestration />} />
              <Route path="/admin/access" element={<AutorisationsM3 />} />
            </Route>

            {/* Catch-all redirect to /agents */}
            <Route path="*" element={<Navigate to="/agents" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
