import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, Play } from 'lucide-react'
import { listAgents, listVersions } from '../../services/agentApi'
import { PageHeader, ErrorBanner, Loading, EmptyState } from '../../components/agent-management/ui'
import '../../components/agent-management/agent-management.css'

export default function AgentTestsPage() {
  const navigate = useNavigate()
  const [agentsWithVersions, setAgentsWithVersions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const agentsData = await listAgents()
      const list = Array.isArray(agentsData) ? agentsData : []

      const withCounts = await Promise.all(
        list.map(async (agent) => {
          try {
            const versions = await listVersions(agent.id)
            return {
              ...agent,
              versionCount: Array.isArray(versions) ? versions.length : 0,
            }
          } catch {
            return { ...agent, versionCount: 0 }
          }
        })
      )
      setAgentsWithVersions(withCounts)
      setError(null)
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  return (
    <div>
      <PageHeader
        crumb="Tests"
        title="Tests"
        subtitle="Exécutez et testez vos agents IA en temps réel."
        action="Nouvel agent"
        onAction={() => navigate('/agents/new')}
      />

      {error && <ErrorBanner error={error} retry={loadData} />}

      {loading ? (
        <Loading label="Chargement des agents pour tests..." />
      ) : (
        <div className="m1-version-list">
          {agentsWithVersions.map((agent) => (
            <div key={agent.id} className="m1-version-item">
              <div className="m1-v-icon">
                <Play size={18} />
              </div>
              <div className="m1-v-info">
                <strong>{agent.name}</strong>
                <small>
                  {agent.versionCount} version(s) configurée(s)
                  {agent.status === 'ACTIVE' ? ' · Prêt' : ` · ${agent.status}`}
                </small>
                {agent.description && (
                  <small style={{ display: 'block', marginTop: '4px', color: 'var(--muted)' }}>
                    {agent.description}
                  </small>
                )}
              </div>
              <Link className="m1-link-button" to={`/agents/${agent.id}?tab=test`}>
                Ouvrir <ArrowRight size={14} />
              </Link>
            </div>
          ))}

          {agentsWithVersions.length === 0 && (
            <EmptyState
              text="Aucun agent disponible pour les tests."
              subtext="Créez d abord un agent pour pouvoir tester son comportement."
            />
          )}
        </div>
      )}
    </div>
  )
}
