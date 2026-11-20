import { ArrowRight, Database, Edit3, Ellipsis, Loader2, Plus, Bot, Search, Trash2, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useState } from 'react'

export function PageHeader({ crumb, title, subtitle, action, onAction }) {
  return (
    <>
      <div className="m1-breadcrumb">Agent Studio <span>/</span> {crumb}</div>
      <div className="m1-page-head">
        <div>
          <h1>{title}</h1>
          <p>{subtitle}</p>
        </div>
        {action && <button className="m1-primary" onClick={onAction}><Plus size={16} />{action}</button>}
      </div>
    </>
  )
}

export function StatCard({ label, value, caption }) {
  return (
    <div className="m1-stat">
      <span>{label}</span>
      <strong>{value ?? '—'}</strong>
      {caption && <small>{caption}</small>}
    </div>
  )
}

export function Stats({ items }) {
  return <div className="m1-stats">{items.map((item) => <StatCard key={item.label} {...item} />)}</div>
}

export function ErrorBanner({ error, retry }) {
  return (
    <div className="m1-error">
      <strong>{error?.message || 'Une erreur est survenue.'}</strong>
      <button onClick={retry}>Réessayer</button>
    </div>
  )
}

export function EmptyState({ text = 'Aucun résultat trouvé.' }) {
  return (
    <div className="m1-empty">
      <Database size={32} />
      <strong>{text}</strong>
    </div>
  )
}

export function Loading() {
  return <div className="m1-loading"><span /><span /><span /></div>
}

export function AgentStatusBadge({ status }) {
  const badges = {
    DRAFT: { class: 'm1-badge-draft', label: 'Brouillon' },
    ACTIVE: { class: 'm1-badge-active', label: 'Actif' },
    ARCHIVED: { class: 'm1-badge-archived', label: 'Archivé' },
  }
  const badge = badges[status] || badges.DRAFT
  return <span className={`m1-badge ${badge.class}`}>{badge.label}</span>
}

export function StatusDot({ status }) {
  const dots = {
    DRAFT: 'm1-dot-draft',
    ACTIVE: 'm1-dot-active',
    ARCHIVED: 'm1-dot-archived',
  }
  return <span className={`m1-dot ${dots[status] || ''}`} />
}

export function SearchInput({ value, onChange, placeholder = 'Rechercher un agent…' }) {
  return (
    <label className="m1-search">
      <Search size={16} />
      <input value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} />
    </label>
  )
}

export function CreateCard({ label, onClick }) {
  return (
    <button className="m1-create-card" onClick={onClick}>
      <span><Plus size={24} /></span>
      <strong>{label}</strong>
      <small>À partir de zéro</small>
    </button>
  )
}

export function ConfirmDialog({ title, onCancel, onConfirm }) {
  return (
    <div className="m1-modal-backdrop">
      <div className="m1-dialog">
        <h2>{title}</h2>
        <p>Cette action est irréversible.</p>
        <div className="m1-modal-actions">
          <button className="m1-ghost" onClick={onCancel}>Annuler</button>
          <button className="m1-danger" onClick={onConfirm}>Supprimer</button>
        </div>
      </div>
    </div>
  )
}

export function Field({ label, error, ...props }) {
  return (
    <label className="m1-field">
      <span>{label}</span>
      <input {...props} />
      {error && <span className="m1-field-error">{error}</span>}
    </label>
  )
}

export function SelectField({ label, children, error, ...props }) {
  return (
    <label className="m1-field">
      <span>{label}</span>
      <select {...props}>{children}</select>
      {error && <span className="m1-field-error">{error}</span>}
    </label>
  )
}

export function TextareaField({ label, error, ...props }) {
  return (
    <label className="m1-field">
      <span>{label}</span>
      <textarea {...props} />
      {error && <span className="m1-field-error">{error}</span>}
    </label>
  )
}

export function AgentCard({ agent, onEdit, onArchive, onDelete }) {
  const [menuOpen, setMenuOpen] = useState(false)
  const formatDate = (dateStr) => {
    const date = new Date(dateStr)
    return date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' })
  }

  return (
    <div className="m1-agent-card">
      <div className="m1-card-top">
        <div className="m1-card-icon"><Bot size={19} /></div>
        <button className="m1-icon-button" onClick={() => setMenuOpen(!menuOpen)} aria-label="Menu">
          <Ellipsis size={19} />
        </button>
        {menuOpen && (
          <div className="m1-menu-dropdown">
            <button onClick={() => { onEdit(agent); setMenuOpen(false); }}>
              <Edit3 size={14} /> Modifier
            </button>
            <button onClick={() => { onArchive(agent); setMenuOpen(false); }}>
              Archiver
            </button>
            <button className="m1-danger-item" onClick={() => { onDelete(agent); setMenuOpen(false); }}>
              <Trash2 size={14} /> Supprimer
            </button>
          </div>
        )}
      </div>
      <div className="m1-card-title">
        <strong>{agent.name}</strong>
        <AgentStatusBadge status={agent.status} />
      </div>
      <p>{agent.description || 'Aucune description'}</p>
      <div className="m1-card-meta">
        <span>Créé le {formatDate(agent.createdAt)}</span>
      </div>
      <div className="m1-card-footer">
        <StatusDot status={agent.status} />
        <span style={{ fontSize: 12, color: 'var(--muted)' }}>
          {agent.status === 'DRAFT' ? 'Brouillon' : agent.status === 'ACTIVE' ? 'Actif' : 'Archivé'}
        </span>
        <Link className="m1-link-button" to={`/agents/${agent.id}`}>
          Ouvrir <ArrowRight size={14} />
        </Link>
      </div>
    </div>
  )
}

export function ToolChips({ tools, selectedIds, onToggle, unavailable }) {
  if (unavailable) {
    return null
  }
  return (
    <div className="m1-chips">
      {tools.map((tool) => (
        <button
          key={tool.id}
          type="button"
          className={`m1-chip ${selectedIds.includes(tool.id) ? 'selected' : ''}`}
          onClick={() => onToggle(tool.id)}
        >
          {tool.name}
        </button>
      ))}
    </div>
  )
}

export function ExecutionResult({ result, error }) {
  if (error) {
    return (
      <div className="m1-test-result">
        <div className="m1-status m1-status-failed">Échec</div>
        <pre>{error}</pre>
      </div>
    )
  }

  if (!result || result.status !== 'COMPLETED') {
    return null
  }

  return (
    <div className="m1-test-result">
      <div className="m1-status m1-status-completed">Succès</div>
      <pre>{result.output}</pre>
      <div className="m1-meta">
        Tokens utilisés: {result.tokensUsed} · Durée: {result.durationMs}ms
      </div>
    </div>
  )
}

export function Spinner({ size = 16 }) {
  return <Loader2 size={size} className="animate-spin" />
}
