import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  listAgents,
  deleteAgent,
  updateAgent,
  adminArchiveAgent,
  adminDeleteAgent,
} from '../../services/agentApi'
import { useAuth } from '../../components/common/AuthContext'
import {
  PageHeader,
  Stats,
  ErrorBanner,
  EmptyState,
  Loading,
  AgentCard,
  ConfirmDialog,
  ReasonDialog,
  SearchInput,
  CreateCard,
} from '../../components/agent-management/ui'
import '../../components/agent-management/agent-management.css'

export default function AgentsListPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, isAdmin, limits, usage, refreshProfile } = useAuth()

  const [agents, setAgents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [showAllUsers, setShowAllUsers] = useState(false)
  const [deleteConfirm, setDeleteConfirm] = useState(null)
  const [archiveConfirm, setArchiveConfirm] = useState(null)
  const [flashMessage, setFlashMessage] = useState(location.state?.flashMessage || null)

  const isQuotaReached =
    !isAdmin &&
    limits?.maxAgents !== null &&
    limits?.maxAgents !== undefined &&
    (usage?.agents ?? agents.length) >= limits.maxAgents

  const loadAgents = useCallback(async () => {
    setLoading(true)
    try {
      const data = await listAgents({ all: isAdmin && showAllUsers })
      setAgents(Array.isArray(data) ? data : [])
      setError(null)
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }, [isAdmin, showAllUsers])

  useEffect(() => {
    loadAgents()
    refreshProfile()
  }, [loadAgents, refreshProfile])

  useEffect(() => {
    if (location.state?.flashMessage) {
      setFlashMessage(location.state.flashMessage)
      window.history.replaceState({}, document.title)
    }
  }, [location.state])

  const handleDelete = async (reason) => {
    if (!deleteConfirm) return
    try {
      if (isAdmin && (deleteConfirm.ownerId !== user?.id || reason)) {
        await adminDeleteAgent(deleteConfirm.id, reason || 'Suppression administrative')
      } else {
        await deleteAgent(deleteConfirm.id)
      }
      setAgents((prev) => prev.filter((a) => a.id !== deleteConfirm.id))
      setDeleteConfirm(null)
      refreshProfile()
    } catch (e) {
      setError(e)
    }
  }

  const handleArchive = async (reason) => {
    if (!archiveConfirm) return
    try {
      if (isAdmin && (archiveConfirm.ownerId !== user?.id || reason)) {
        await adminArchiveAgent(archiveConfirm.id, reason || 'Archivage administratif')
      } else {
        await updateAgent(archiveConfirm.id, {
          name: archiveConfirm.name,
          description: archiveConfirm.description,
          status: 'ARCHIVED',
        })
      }
      setAgents((prev) =>
        prev.map((a) => (a.id === archiveConfirm.id ? { ...a, status: 'ARCHIVED' } : a))
      )
      setArchiveConfirm(null)
      refreshProfile()
    } catch (e) {
      setError(e)
    }
  }

  const filteredAgents = agents.filter((agent) => {
    const q = search.toLowerCase()
    const matchesSearch =
      (agent.name || '').toLowerCase().includes(q) ||
      (agent.description || '').toLowerCase().includes(q)
    const matchesStatus = !statusFilter || agent.status === statusFilter
    return matchesSearch && matchesStatus
  })

  const total = agents.length
  const active = agents.filter((a) => a.status === 'ACTIVE').length
  const drafts = agents.filter((a) => a.status === 'DRAFT').length

  return (
    <div>
      <PageHeader
        crumb="Mes agents"
        title="Mes agents"
        subtitle="Créez, configurez et versionnez vos agents IA."
        action="Créer un agent"
        actionDisabled={isQuotaReached}
        actionTooltip={isQuotaReached ? 'Limite atteinte : supprimez un agent ou contactez un administrateur' : undefined}
        onAction={() => navigate('/agents/new')}
      />

      {isQuotaReached && (
        <div className="m1-warning" style={{ marginBottom: 20 }}>
          <span>
            <strong>Limite atteinte :</strong> Vous avez atteint votre quota de {limits.maxAgents} agents. Supprimez un agent existant ou contactez un administrateur pour augmenter votre limite.
          </span>
        </div>
      )}

      {flashMessage && (
        <div className="m1-warning" style={{ marginBottom: 20 }}>
          <span>{flashMessage}</span>
          <button
            type="button"
            className="m1-ghost-sm"
            style={{ marginLeft: 'auto' }}
            onClick={() => setFlashMessage(null)}
          >
            Fermer
          </button>
        </div>
      )}

      {error && <ErrorBanner error={error} retry={loadAgents} />}

      <Stats
        items={[
          {
            label: 'Quota agents',
            value: isAdmin ? 'Illimité' : `${usage?.agents ?? total} / ${limits?.maxAgents ?? 5}`,
            caption: isAdmin ? 'Rôle Administrateur' : isQuotaReached ? 'Quota maximal atteint' : 'Utilisation actuelle',
          },
          { label: 'Agents actifs', value: active, caption: 'Prêts à être exécutés' },
          { label: 'Brouillons', value: drafts, caption: 'En cours de configuration' },
        ]}
      />

      <div className="m1-filters">
        <SearchInput value={search} onChange={setSearch} placeholder="Rechercher un agent…" />

        <select
          className="m1-select"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          aria-label="Filtrer par statut"
        >
          <option value="">Tous les statuts</option>
          <option value="DRAFT">Brouillon</option>
          <option value="ACTIVE">Actif</option>
          <option value="ARCHIVED">Archivé</option>
        </select>

        {isAdmin && (
          <label className="m1-admin-toggle">
            <input
              type="checkbox"
              checked={showAllUsers}
              onChange={(e) => setShowAllUsers(e.target.checked)}
            />
            <span>Tous les utilisateurs (Admin)</span>
          </label>
        )}

        <span className="m1-counter">{filteredAgents.length} agent(s)</span>
      </div>

      {loading ? (
        <Loading label="Chargement des agents..." />
      ) : (
        <>
          <div className="m1-card-grid">
            <CreateCard
              label="Créer un agent"
              disabled={isQuotaReached}
              tooltip={isQuotaReached ? 'Limite atteinte : supprimez un agent ou contactez un administrateur' : undefined}
              onClick={() => navigate('/agents/new')}
            />

            {filteredAgents.map((agent) => (
              <AgentCard
                key={agent.id}
                agent={agent}
                isAdmin={isAdmin}
                isReadOnly={isAdmin && Boolean(agent.ownerId && agent.ownerId !== user?.id)}
                onEdit={(a) => navigate(`/agents/${a.id}`)}
                onArchive={setArchiveConfirm}
                onDelete={setDeleteConfirm}
              />
            ))}
          </div>

          {filteredAgents.length === 0 && (
            <div style={{ marginTop: 20 }}>
              <EmptyState
                text="Aucun agent trouvé."
                subtext="Créez votre premier agent ou ajustez vos critères de recherche."
              />
            </div>
          )}
        </>
      )}

      {deleteConfirm && (
        isAdmin && deleteConfirm.ownerId !== user?.id ? (
          <ReasonDialog
            title={`Suppression administrative de "${deleteConfirm.name}"`}
            message={`Cet agent appartient à "${deleteConfirm.ownerId}". Cette action est irréversible et sera enregistrée dans le journal d'audit.`}
            confirmLabel="Supprimer définitivement"
            onCancel={() => setDeleteConfirm(null)}
            onConfirm={handleDelete}
          />
        ) : (
          <ConfirmDialog
            title={`Supprimer l'agent "${deleteConfirm.name}" ?`}
            message="Cette action est irréversible et supprimera également toutes ses versions et son historique."
            confirmLabel="Supprimer"
            onCancel={() => setDeleteConfirm(null)}
            onConfirm={() => handleDelete()}
          />
        )
      )}

      {archiveConfirm && (
        isAdmin && archiveConfirm.ownerId !== user?.id ? (
          <ReasonDialog
            title={`Archivage administratif de "${archiveConfirm.name}"`}
            message={`Cet agent appartient à "${archiveConfirm.ownerId}". L'action sera consignée dans le journal d'audit.`}
            confirmLabel="Archiver l'agent"
            variant="primary"
            onCancel={() => setArchiveConfirm(null)}
            onConfirm={handleArchive}
          />
        ) : (
          <ConfirmDialog
            title={`Archiver l'agent "${archiveConfirm.name}" ?`}
            message="L'agent sera désactivé mais restera consultable dans la liste."
            confirmLabel="Archiver"
            variant="primary"
            onCancel={() => setArchiveConfirm(null)}
            onConfirm={() => handleArchive()}
          />
        )
      )}
    </div>
  )
}
