import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import MainLayout from './layouts/MainLayout'
import DashboardPage from './pages/anomaly-monitoring/DashboardPage'
import ChecksPage from './pages/anomaly-monitoring/ChecksPage'
import CheckDetailPage from './pages/anomaly-monitoring/CheckDetailPage'
import AlertsPage from './pages/anomaly-monitoring/AlertsPage'
import ModelsPage from './pages/anomaly-monitoring/ModelsPage'
import AgentsListPage from './pages/agent-management/AgentsListPage'
import NewAgentPage from './pages/agent-management/NewAgentPage'
import AgentEditorPage from './pages/agent-management/AgentEditorPage'
import AgentTestsPage from './pages/agent-management/AgentTestsPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<MainLayout />}>
          <Route path="/monitoring" element={<DashboardPage />} />
          <Route path="/monitoring/checks" element={<ChecksPage />} />
          <Route path="/monitoring/checks/:id" element={<CheckDetailPage />} />
          <Route path="/monitoring/alerts" element={<AlertsPage />} />
          <Route path="/monitoring/models" element={<ModelsPage />} />
          <Route path="/agents" element={<AgentsListPage />} />
          <Route path="/agents/new" element={<NewAgentPage />} />
          <Route path="/agents/:id" element={<AgentEditorPage />} />
          <Route path="/agent-tests" element={<AgentTestsPage />} />
          <Route path="*" element={<Navigate to="/monitoring" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
