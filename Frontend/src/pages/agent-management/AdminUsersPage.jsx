import { useCallback, useEffect, useState } from 'react'
import { Edit2, RefreshCw, RotateCcw, Shield, Users } from 'lucide-react'
import { adminListUsers, adminSetQuota } from '../../services/agentApi'
import { useAuth } from '../../components/common/AuthContext'
import { ErrorBanner, Loading, EmptyState, Spinner } from '../../components/agent-management/ui'
import '../../components/agent-management/agent-management.css'

export default function AdminUsersPage() {
  const { refreshProfile } = useAuth()
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // Edit quota modal state
  const [selectedUser, setSelectedUser] = useState(null)
  const [quotaInput, setQuotaInput] = useState(5)
  const [useDefaultQuota, setUseDefaultQuota] = useState(false)
  const [saving, setSaving] = useState(false)
  const [modalError, setModalError] = useState(null)

  const loadUsers = useCallback(async () => {
    setLoading(true)
    try {
      const data = await adminListUsers()
      setUsers(Array.isArray(data) ? data : [])
      setError(null)
    } catch (err) {
      setError(err)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadUsers()
  }, [loadUsers])

  const openQuotaModal = (u) => {
    setSelectedUser(u)
    setUseDefaultQuota(!u.quotaIsOverride)
    setQuotaInput(u.effectiveQuota ?? 5)
    setModalError(null)
  }

  const handleSaveQuota = async (e) => {
    e.preventDefault()
    if (!selectedUser) return

    setSaving(true)
    setModalError(null)
    try {
      const maxAgents = useDefaultQuota ? null : parseInt(quotaInput, 10)
      await adminSetQuota(selectedUser.ownerId, maxAgents)
      setSelectedUser(null)
      await loadUsers()
      refreshProfile()
    } catch (err) {
      setModalError(err)
    } finally {
      setSaving(false)
    }
  }

  const formatDate = (dateStr) => {
    if (!dateStr) return '—'
    try {
      return new Date(dateStr).toLocaleString('fr-FR', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return dateStr
    }
  }

  return (
    <div>
      <div className="m1-breadcrumb">
        Administration <span>/</span> Utilisateurs & quotas
      </div>

      <div className="m1-page-head">
        <div>
          <h1>Utilisateurs & quotas</h1>
          <p>Supervisez l'utilisation de la plateforme et configurez les quotas par utilisateur.</p>
        </div>
        <button type="button" className="m1-ghost" onClick={loadUsers} disabled={loading}>
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          Actualiser
        </button>
      </div>

      {error && <ErrorBanner error={error} retry={loadUsers} />}

      {loading ? (
        <Loading label="Chargement des utilisateurs..." />
      ) : users.length === 0 ? (
        <EmptyState text="Aucun utilisateur répertorié." />
      ) : (
        <div className="m1-table-wrapper">
          <table className="m1-table">
            <thead>
              <tr>
                <th>Propriétaire</th>
                <th>Agents créés</th>
                <th>Agents actifs</th>
                <th>Quota effectif</th>
                <th>Dernière activité</th>
                <th style={{ textAlign: 'right' }}>Action</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.ownerId}>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <div
                        style={{
                          width: 28,
                          height: 28,
                          borderRadius: 6,
                          background: 'var(--lavender)',
                          color: 'var(--purple)',
                          display: 'grid',
                          placeItems: 'center',
                          fontWeight: 700,
                          fontSize: 12,
                        }}
                      >
                        {u.ownerId.charAt(0).toUpperCase()}
                      </div>
                      <strong style={{ color: '#1b1b35' }}>{u.ownerId}</strong>
                    </div>
                  </td>
                  <td>{u.agentCount ?? 0}</td>
                  <td>{u.activeAgentCount ?? 0}</td>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span>{u.effectiveQuota !== null ? `${u.effectiveQuota} agents` : 'Illimité'}</span>
                      {u.quotaIsOverride && (
                        <span
                          style={{
                            background: '#ede8ff',
                            color: 'var(--purple)',
                            border: '1px solid #d5cef7',
                            padding: '2px 6px',
                            borderRadius: 4,
                            fontSize: 10,
                            fontWeight: 700,
                            textTransform: 'uppercase',
                          }}
                        >
                          Personnalisé
                        </span>
                      )}
                    </div>
                  </td>
                  <td>{formatDate(u.lastActivityAt)}</td>
                  <td style={{ textAlign: 'right' }}>
                    <button
                      type="button"
                      className="m1-ghost-sm"
                      onClick={() => openQuotaModal(u)}
                    >
                      <Edit2 size={13} /> Modifier le quota
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {selectedUser && (
        <div className="m1-modal-backdrop" onClick={() => setSelectedUser(null)}>
          <div className="m1-dialog" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
            <h2>Modifier le quota de "{selectedUser.ownerId}"</h2>
            <p>
              Ajustez le nombre maximal d'agents que cet utilisateur est autorisé à créer sur la plateforme.
            </p>

            {modalError && <ErrorBanner error={modalError} />}

            <form onSubmit={handleSaveQuota}>
              <div style={{ marginBottom: 16 }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', marginBottom: 14 }}>
                  <input
                    type="checkbox"
                    checked={useDefaultQuota}
                    onChange={(e) => {
                      setUseDefaultQuota(e.target.checked)
                      if (e.target.checked) {
                        setQuotaInput(5)
                      }
                    }}
                  />
                  <span style={{ fontSize: 13, color: '#1b1b35', fontWeight: 500 }}>
                    Utiliser le quota par défaut de la plateforme (5 agents)
                  </span>
                </label>

                {!useDefaultQuota && (
                  <label className="m1-field">
                    <span>Nombre maximal d'agents (1 à 100)</span>
                    <input
                      type="number"
                      min={1}
                      max={100}
                      value={quotaInput}
                      onChange={(e) => setQuotaInput(parseInt(e.target.value, 10) || 1)}
                      required
                    />
                  </label>
                )}
              </div>

              <div className="m1-modal-actions">
                <button
                  type="button"
                  className="m1-ghost"
                  onClick={() => setSelectedUser(null)}
                  disabled={saving}
                >
                  Annuler
                </button>
                <button type="submit" className="m1-primary" disabled={saving}>
                  {saving ? (
                    <>
                      <Spinner size={14} /> Enregistrement...
                    </>
                  ) : (
                    'Enregistrer le quota'
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
