import { useCallback, useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight, ClipboardList, RefreshCw, ShieldAlert } from 'lucide-react'
import { adminListAudit } from '../../services/agentApi'
import { ErrorBanner, Loading, EmptyState } from '../../components/agent-management/ui'
import '../../components/agent-management/agent-management.css'

export default function AdminAuditPage() {
  const [items, setItems] = useState([])
  const [totalCount, setTotalCount] = useState(0)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(15)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const loadAudit = useCallback(async () => {
    setLoading(true)
    try {
      const data = await adminListAudit(page, pageSize)
      setItems(data?.items || [])
      setTotalCount(data?.totalCount || 0)
      setError(null)
    } catch (err) {
      setError(err)
    } finally {
      setLoading(false)
    }
  }, [page, pageSize])

  useEffect(() => {
    loadAudit()
  }, [loadAudit])

  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize))

  const formatDate = (dateStr) => {
    if (!dateStr) return '—'
    try {
      return new Date(dateStr).toLocaleString('fr-FR', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      })
    } catch {
      return dateStr
    }
  }

  const getActionBadge = (action) => {
    const act = (action || '').toUpperCase()
    if (act.includes('DELETE')) {
      return (
        <span
          style={{
            background: '#fef2f2',
            color: '#dc2626',
            border: '1px solid #fecaca',
            padding: '3px 8px',
            borderRadius: 5,
            fontSize: 11,
            fontWeight: 700,
          }}
        >
          {action}
        </span>
      )
    }
    if (act.includes('ARCHIVE')) {
      return (
        <span
          style={{
            background: '#fffbeb',
            color: '#d97706',
            border: '1px solid #fef3c7',
            padding: '3px 8px',
            borderRadius: 5,
            fontSize: 11,
            fontWeight: 700,
          }}
        >
          {action}
        </span>
      )
    }
    return (
      <span
        style={{
          background: '#ede8ff',
          color: 'var(--purple)',
          border: '1px solid #d5cef7',
          padding: '3px 8px',
          borderRadius: 5,
          fontSize: 11,
          fontWeight: 700,
        }}
      >
        {action}
      </span>
    )
  }

  return (
    <div>
      <div className="m1-breadcrumb">
        Administration <span>/</span> Journal d'audit
      </div>

      <div className="m1-page-head">
        <div>
          <h1>Journal d'audit</h1>
          <p>Traçabilité des actions sensibles et interventions administratives sur la plateforme.</p>
        </div>
        <button type="button" className="m1-ghost" onClick={loadAudit} disabled={loading}>
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          Actualiser
        </button>
      </div>

      {error && <ErrorBanner error={error} retry={loadAudit} />}

      {loading ? (
        <Loading label="Chargement de l'audit..." />
      ) : items.length === 0 ? (
        <EmptyState
          text="Aucune entrée d'audit enregistrée."
          subtext="Les actions d'archivage, suppression administrative et modification de quota apparaîtront ici."
        />
      ) : (
        <>
          <div className="m1-table-wrapper">
            <table className="m1-table">
              <thead>
                <tr>
                  <th>Date & Heure</th>
                  <th>Acteur</th>
                  <th>Action</th>
                  <th>Cible</th>
                  <th>Motif</th>
                </tr>
              </thead>
              <tbody>
                {items.map((log) => (
                  <tr key={log.id}>
                    <td style={{ whiteSpace: 'nowrap', color: 'var(--muted)' }}>
                      {formatDate(log.timestamp || log.createdAt)}
                    </td>
                    <td>
                      <strong style={{ color: '#1b1b35' }}>{log.actor || 'Système'}</strong>
                    </td>
                    <td>{getActionBadge(log.action)}</td>
                    <td>
                      <code
                        style={{
                          fontSize: 12,
                          background: '#f8fafc',
                          border: '1px solid #e2e8f0',
                          padding: '2px 6px',
                          borderRadius: 4,
                        }}
                      >
                        {log.targetType ? `${log.targetType} #` : ''}
                        {log.targetId || '—'}
                      </code>
                    </td>
                    <td style={{ maxWidth: 320, color: '#374151' }}>
                      {log.reason || '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginTop: 18,
              fontSize: 13,
              color: 'var(--muted)',
              flexWrap: 'wrap',
              gap: 12,
            }}
          >
            <span>
              Total : <strong>{totalCount}</strong> événement(s) — Page {page + 1} sur {totalPages}
            </span>

            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <button
                type="button"
                className="m1-ghost-sm"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
              >
                <ChevronLeft size={14} /> Précédent
              </button>
              <button
                type="button"
                className="m1-ghost-sm"
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
              >
                Suivant <ChevronRight size={14} />
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
