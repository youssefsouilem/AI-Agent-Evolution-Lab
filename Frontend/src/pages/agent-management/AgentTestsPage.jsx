import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Play, ArrowRight } from 'lucide-react'
import { listAgents, listVersions } from '../../services/agentApi'
import { PageHeader, ErrorBanner, Loading, EmptyState } from '../../components/agent-management/ui'
import '../../components/agent-management/agent-management.css'

export default function AgentTestsPage() {
  const [agents, setAgents] = useState([])
  const [agentsWithVersions, setAgentsWithVersions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const agentsData = await listAgents()
      setAgents(agentsData)
      
      const agentsWithVersionCounts = await Promise.all(
        agentsData.map(async (agent) => {
          try {
            const versions = await listVersions(agent.id)
            return { ...agent, versionCount: versions.length }
          } catch {
            return { ...agent, versionCount: 0 }
          }
        })
      )
      setAgentsWithVersions(agentsWithVersionCounts)
      setError(null)
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadData() }, [loadData])

  if (loading) return <div><PageHeader crumb="Tests" title="Tests" subtitle="Exécutez et testez vos agents IA." /><Loading /></div>

  return (
    <div>
      <PageHeader crumb="Tests" title="Tests" subtitle="Exécutez et testez vos agents IA." />
      {error && <ErrorBanner error={error} retry={loadData} />}
      
      <div className="m1-version-list">
        {agentsWithVersions.map(agent => (
          <div key={agent.id} className="m1-version-item">
            <div className="m1-v-icon"><Play size={18} /></div>
            <div className="m1-v-info">
              <strong>{agent.name}</strong>
              <small>{agent.versionCount} version(s)</small>
              {agent.description && <small style={{ display: 'block', marginTop: '4px', color: 'var(--muted)' }}>{agent.description}</small>}
            </div>
            <Link className="m1-link-button" to={`/agents/${agent.id}?tab=test`}>
              Ouvrir <ArrowRight size={14} />
            </Link>
          </div>
        ))}
      </div>

      {agentsWithVersions.length === 0 && !loading && <EmptyState text="Aucun agent disponible pour les tests." />}
    </div>
  )
}
