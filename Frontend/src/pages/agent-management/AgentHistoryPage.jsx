import { useCallback, useEffect, useState } from 'react'
import { Clock } from 'lucide-react'
import { listAllExecutions, listAgents, getCapabilities } from '../../services/agentApi'
import {
  PageHeader,
  ErrorBanner,
  Loading,
  EmptyState,
  ExecutionTable,
  ExecutionDrawer,
} from '../../components/agent-management/ui'
import '../../components/agent-management/agent-management.css'

export default function AgentHistoryPage() {
  const [capabilities, setCapabilities] = useState({ executions: false, overrides: false })
  const [agents, setAgents] = useState([])
  const [items, setItems] = useState([])
  const [totalCount, setTotalCount] = useState(0)
  const [page, setPage] = useState(0)
  const [agentFilter, setAgentFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [selectedExecution, setSelectedExecution] = useState(null)

  useEffect(() => {
    getCapabilities().then(setCapabilities)
    listAgents()
      .then((data) => setAgents(Array.isArray(data) ? data : []))
      .catch(() => setAgents([]))
  }, [])

  const loadExecutions = useCallback(async () => {
    if (!capabilities.executions) {
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      const res = await listAllExecutions({
        page,
        size: 10,
        agentId: agentFilter ? parseInt(agentFilter, 10) : undefined,
        status: statusFilter || undefined,
      })
      setItems(res.items || [])
      setTotalCount(res.totalCount || 0)
      setError(null)
    } catch (e) {
      setError(e)
      setItems([])
      setTotalCount(0)
    } finally {
      setLoading(false)
    }
  }, [capabilities.executions, page, agentFilter, statusFilter])

  useEffect(() => {
    loadExecutions()
  }, [loadExecutions])

  return (
    <div>
      <PageHeader
        crumb="Historique"
        title="Historique des exécutions"
        subtitle="Consultez et analysez les requêtes traitées par l ensemble de vos agents IA."
      />

      {error && <ErrorBanner error={error} retry={loadExecutions} />}

      {!capabilities.executions ? (
        <div className="m1-empty" style={{ margin: '30px 0' }}>
          <Clock size={40} style={{ marginBottom: 12, color: 'var(--muted)' }} />
          <strong>Fonctionnalité en attente de mise à jour serveur</strong>
          <small>
            L historique des exécutions sera disponible dès que le serveur M1 sera mis à jour.
          </small>
        </div>
      ) : (
        <div>
          <div className="m1-filters">
            <select
              className="m1-select"
              value={agentFilter}
              onChange={(e) => {
                setAgentFilter(e.target.value)
                setPage(0)
              }}
              aria-label="Filtrer par agent"
            >
              <option value="">Tous les agents</option>
              {agents.map((agent) => (
                <option key={agent.id} value={agent.id}>
                  {agent.name}
                </option>
              ))}
            </select>

            <select
              className="m1-select"
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value)
                setPage(0)
              }}
              aria-label="Filtrer par statut"
            >
              <option value="">Tous les statuts</option>
              <option value="COMPLETED">COMPLETED</option>
              <option value="FAILED">FAILED</option>
            </select>

            <span className="m1-counter">{totalCount} exécution(s) trouvée(s)</span>
          </div>

          {loading ? (
            <Loading label="Chargement de l historique global..." />
          ) : items.length > 0 ? (
            <>
              <ExecutionTable executions={items} onSelect={setSelectedExecution} />

              {totalCount > 10 && (
                <div className="m1-pagination">
                  <button
                    type="button"
                    className="m1-ghost-sm"
                    disabled={page === 0}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                  >
                    Précédent
                  </button>
                  <span style={{ fontSize: 13, color: 'var(--muted)' }}>
                    Page {page + 1} sur {Math.ceil(totalCount / 10)}
                  </span>
                  <button
                    type="button"
                    className="m1-ghost-sm"
                    disabled={(page + 1) * 10 >= totalCount}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Suivant
                  </button>
                </div>
              )}
            </>
          ) : (
            <EmptyState
              text="Aucune exécution trouvée."
              subtext="Lancez des tests pour voir apparaître vos exécutions ici."
            />
          )}

          {selectedExecution && (
            <ExecutionDrawer
              execution={selectedExecution}
              onClose={() => setSelectedExecution(null)}
            />
          )}
        </div>
      )}
    </div>
  )
}
