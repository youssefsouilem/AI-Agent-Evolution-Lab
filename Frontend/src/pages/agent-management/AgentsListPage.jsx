import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { listAgents, deleteAgent, updateAgent } from '../../services/agentApi'
import { PageHeader, Stats, ErrorBanner, EmptyState, Loading, AgentCard, ConfirmDialog, SearchInput } from '../../components/agent-management/ui'
import '../../components/agent-management/agent-management.css'

export default function AgentsListPage() {
  const navigate = useNavigate()
  const [agents, setAgents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [deleteConfirm, setDeleteConfirm] = useState(null)
  const [archiveConfirm, setArchiveConfirm] = useState(null)

  const loadAgents = useCallback(async () => {
    setLoading(true)
    try {
      const data = await listAgents()
      setAgents(data)
      setError(null)
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadAgents() }, [loadAgents])

  const handleDelete = async () => {
    if (!deleteConfirm) return
    try {
      await deleteAgent(deleteConfirm.id)
      setAgents(agents.filter(a => a.id !== deleteConfirm.id))
      setDeleteConfirm(null)
    } catch (e) {
      setError(e)
    }
  }

  const handleArchive = async () => {
    if (!archiveConfirm) return
    try {
      await updateAgent(archiveConfirm.id, { status: 'ARCHIVED' })
      setAgents(agents.map(a => a.id === archiveConfirm.id ? { ...a, status: 'ARCHIVED' } : a))
      setArchiveConfirm(null)
    } catch (e) {
      setError(e)
    }
  }

  const filteredAgents = agents.filter(agent => {
    const matchesSearch = agent.name.toLowerCase().includes(search.toLowerCase()) ||
                         (agent.description || '').toLowerCase().includes(search.toLowerCase())
    const matchesStatus = !statusFilter || agent.status === statusFilter
    return matchesSearch && matchesStatus
  })

  const total = agents.length
  const active = agents.filter(a => a.status === 'ACTIVE').length
  const drafts = agents.filter(a => a.status === 'DRAFT').length

  if (loading) return <div><PageHeader crumb="Mes agents" title="Mes agents" subtitle="Créez, configurez et versionnez vos agents IA." action="+ Créer un agent" onAction={() => navigate('/agents/new')} /><Loading /></div>

  return (
    <div>
      <PageHeader crumb="Mes agents" title="Mes agents" subtitle="Créez, configurez et versionnez vos agents IA." action="+ Créer un agent" onAction={() => navigate('/agents/new')} />
      {error && <ErrorBanner error={error} retry={loadAgents} />}
      
      <Stats items={[
        { label: 'Total des agents', value: total, caption: 'Tous vos agents' },
        { label: 'Agents actifs', value: active, caption: 'Prêts à être exécutés' },
        { label: 'Brouillons', value: drafts, caption: 'En cours de configuration' },
      ]} />

      <div className="m1-filters">
        <SearchInput value={search} onChange={setSearch} placeholder="Rechercher un agent…" />
        <select className="m1-select" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">Tous les statuts</option>
          <option value="DRAFT">Brouillon</option>
          <option value="ACTIVE">Actif</option>
          <option value="ARCHIVED">Archivé</option>
        </select>
        <span className="m1-counter">{filteredAgents.length} agent(s)</span>
      </div>

      <div className="m1-card-grid">
        <button className="m1-create-card" onClick={() => navigate('/agents/new')}>
          <span>+</span>
          <strong>Créer un agent</strong>
          <small>À partir de zéro</small>
        </button>
        {filteredAgents.map(agent => (
          <AgentCard
            key={agent.id}
            agent={agent}
            onEdit={(a) => navigate(`/agents/${a.id}`)}
            onArchive={setArchiveConfirm}
            onDelete={setDeleteConfirm}
          />
        ))}
      </div>

      {filteredAgents.length === 0 && !loading && <EmptyState text="Aucun agent trouvé." />}

      {deleteConfirm && (
        <ConfirmDialog
          title={`Supprimer l'agent "${deleteConfirm.name}" ?`}
          onCancel={() => setDeleteConfirm(null)}
          onConfirm={handleDelete}
        />
      )}

      {archiveConfirm && (
        <ConfirmDialog
          title={`Archiver l'agent "${archiveConfirm.name}" ?`}
          onCancel={() => setArchiveConfirm(null)}
          onConfirm={handleArchive}
        />
      )}
    </div>
  )
}
