export const SEVERITIES = ['LOW', 'INFO', 'MEDIUM', 'WARNING', 'HIGH', 'CRITICAL']
export const ALERT_STATUSES = ['OPEN', 'NEW', 'ACKNOWLEDGED', 'RESOLVED']
export const EXECUTION_STATUSES = ['SUCCESS', 'FAILED', 'RUNNING', 'TIMEOUT']
export const badgeClass = (value) => {
  const normalized = String(value || '').toUpperCase()
  if (['LOW', 'INFO'].includes(normalized)) return 'm6-badge m6-badge-blue'
  if (['MEDIUM', 'WARNING'].includes(normalized)) return 'm6-badge m6-badge-amber'
  if (normalized === 'HIGH') return 'm6-badge m6-badge-orange'
  if (normalized === 'CRITICAL') return 'm6-badge m6-badge-red'
  if (['OPEN', 'NEW'].includes(normalized)) return 'm6-badge m6-badge-red'
  if (normalized === 'ACKNOWLEDGED') return 'm6-badge m6-badge-amber'
  if (normalized === 'RESOLVED') return 'm6-badge m6-badge-green'
  return 'm6-badge'
}
export const label = (value) => String(value || '').replaceAll('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())
export const formatDate = (value) => value ? new Date(value).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' }) : '—'
export const shortDate = (value) => value ? new Date(value).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'
