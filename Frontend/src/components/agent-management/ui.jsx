import {
  AlertTriangle,
  ArrowRight,
  Bot,
  Check,
  ChevronDown,
  ChevronUp,
  Clock,
  Database,
  Edit3,
  Ellipsis,
  Loader2,
  Plus,
  RefreshCw,
  Search,
  Sliders,
  Trash2,
  X,
  Zap,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { useState } from 'react'

export function PageHeader({ crumb, title, subtitle, action, onAction }) {
  return (
    <>
      <div className="m1-breadcrumb">
        Agent Studio <span>/</span> {crumb}
      </div>
      <div className="m1-page-head">
        <div>
          <h1>{title}</h1>
          <p>{subtitle}</p>
        </div>
        {action && (
          <button className="m1-primary" onClick={onAction}>
            <Plus size={16} />
            {action}
          </button>
        )}
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
  return (
    <div className="m1-stats">
      {items.map((item) => (
        <StatCard key={item.label} {...item} />
      ))}
    </div>
  )
}

export function ErrorBanner({ error, retry }) {
  const message = error?.message || (typeof error === 'string' ? error : 'Une erreur est survenue.')
  const errorCode = error?.errorCode

  return (
    <div className="m1-error" role="alert">
      <AlertTriangle size={18} className="m1-error-icon" />
      <div style={{ flex: 1 }}>
        <strong>{message}</strong>
        {errorCode && <span className="m1-error-code-badge">{errorCode}</span>}
      </div>
      {retry && (
        <button type="button" onClick={retry}>
          <RefreshCw size={13} /> Réessayer
        </button>
      )}
    </div>
  )
}

export function EmptyState({ text = 'Aucun résultat trouvé.', subtext }) {
  return (
    <div className="m1-empty">
      <Database size={36} />
      <strong>{text}</strong>
      {subtext && <small>{subtext}</small>}
    </div>
  )
}

export function Loading({ label = 'Chargement en cours...' }) {
  return (
    <div className="m1-loading" aria-live="polite">
      <span />
      <span />
      <span />
      <span className="sr-only">{label}</span>
    </div>
  )
}

export function AgentStatusBadge({ status }) {
  const badges = {
    DRAFT: { className: 'm1-badge-draft', label: 'Brouillon' },
    ACTIVE: { className: 'm1-badge-active', label: 'Actif' },
    ARCHIVED: { className: 'm1-badge-archived', label: 'Archivé' },
  }
  const badge = badges[status] || badges.DRAFT
  return <span className={`m1-badge ${badge.className}`}>{badge.label}</span>
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
      <input
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
      />
    </label>
  )
}

export function CreateCard({ label = 'Créer un agent', onClick }) {
  return (
    <button type="button" className="m1-create-card" onClick={onClick}>
      <span>
        <Plus size={24} />
      </span>
      <strong>{label}</strong>
      <small>À partir de zéro</small>
    </button>
  )
}

export function ConfirmDialog({
  title,
  message = 'Cette action est irréversible.',
  confirmLabel = 'Confirmer',
  cancelLabel = 'Annuler',
  variant = 'danger',
  onCancel,
  onConfirm,
}) {
  return (
    <div className="m1-modal-backdrop" onClick={onCancel}>
      <div className="m1-dialog" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <h2>{title}</h2>
        <p>{message}</p>
        <div className="m1-modal-actions">
          <button type="button" className="m1-ghost" onClick={onCancel}>
            {cancelLabel}
          </button>
          <button
            type="button"
            className={variant === 'danger' ? 'm1-danger' : 'm1-primary'}
            onClick={onConfirm}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

export function Field({ label, error, helper, ...props }) {
  return (
    <label className="m1-field">
      <span>{label}</span>
      <input {...props} />
      {helper && <small className="m1-field-helper">{helper}</small>}
      {error && <span className="m1-field-error">{error}</span>}
    </label>
  )
}

export function SelectField({ label, children, error, helper, ...props }) {
  return (
    <label className="m1-field">
      <span>{label}</span>
      <select {...props}>{children}</select>
      {helper && <small className="m1-field-helper">{helper}</small>}
      {error && <span className="m1-field-error">{error}</span>}
    </label>
  )
}

export function TextareaField({ label, error, helper, ...props }) {
  return (
    <label className="m1-field">
      <span>{label}</span>
      <textarea {...props} />
      {helper && <small className="m1-field-helper">{helper}</small>}
      {error && <span className="m1-field-error">{error}</span>}
    </label>
  )
}

export function AgentCard({ agent, isAdmin, onEdit, onArchive, onDelete }) {
  const [menuOpen, setMenuOpen] = useState(false)

  const formatDate = (dateStr) => {
    if (!dateStr) return '—'
    try {
      const date = new Date(dateStr)
      return date.toLocaleDateString('fr-FR', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
      })
    } catch {
      return dateStr
    }
  }

  return (
    <div className="m1-agent-card">
      <div className="m1-card-top">
        <div className="m1-card-icon">
          <Bot size={20} />
        </div>
        <div className="m1-menu-container">
          <button
            type="button"
            className="m1-icon-button"
            onClick={() => setMenuOpen(!menuOpen)}
            aria-label="Actions de l'agent"
            aria-expanded={menuOpen}
          >
            <Ellipsis size={18} />
          </button>
          {menuOpen && (
            <div className="m1-menu-dropdown" onMouseLeave={() => setMenuOpen(false)}>
              <button
                type="button"
                onClick={() => {
                  onEdit(agent)
                  setMenuOpen(false)
                }}
              >
                <Edit3 size={14} /> Modifier
              </button>
              {agent.status !== 'ARCHIVED' && (
                <button
                  type="button"
                  onClick={() => {
                    onArchive(agent)
                    setMenuOpen(false)
                  }}
                >
                  Archiver
                </button>
              )}
              <button
                type="button"
                className="m1-danger-item"
                onClick={() => {
                  onDelete(agent)
                  setMenuOpen(false)
                }}
              >
                <Trash2 size={14} /> Supprimer
              </button>
            </div>
          )}
        </div>
      </div>

      <div className="m1-card-title">
        <strong>{agent.name}</strong>
        <AgentStatusBadge status={agent.status} />
      </div>

      {isAdmin && (
        <div className="m1-card-owner">
          <small>Propriétaire : <strong>{agent.ownerId || 'Non assigné'}</strong></small>
        </div>
      )}

      <p>{agent.description || 'Aucune description fournie.'}</p>

      <div className="m1-card-meta">
        <span>Créé le {formatDate(agent.createdAt)}</span>
      </div>

      <div className="m1-card-footer">
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <StatusDot status={agent.status} />
          <span style={{ fontSize: 12, color: 'var(--muted)' }}>
            {agent.status === 'DRAFT'
              ? 'Brouillon'
              : agent.status === 'ACTIVE'
              ? 'Actif'
              : 'Archivé'}
          </span>
        </div>
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
      {tools.map((tool) => {
        const isSelected = selectedIds.includes(tool.id)
        return (
          <button
            key={tool.id}
            type="button"
            className={`m1-chip ${isSelected ? 'selected' : ''}`}
            onClick={() => onToggle(tool.id)}
            aria-pressed={isSelected}
          >
            {isSelected && <Check size={12} style={{ marginRight: 4 }} />}
            {tool.name}
          </button>
        )
      })}
    </div>
  )
}

export function TunableParametersControls({ params, values, onChange }) {
  return (
    <div className="m1-tunable-grid">
      {params.map((param) => {
        const val = values[param.name] ?? param.current ?? 0

        if (param.type === 'categorical') {
          return (
            <div key={param.name} className="m1-config-full">
              <label className="m1-field">
                <span>{param.description || param.name}</span>
                <div className="m1-segmented-control">
                  {(param.choices || ['concise', 'detaille']).map((choice) => (
                    <button
                      key={choice}
                      type="button"
                      className={`m1-segment ${val === choice ? 'active' : ''}`}
                      onClick={() => onChange(param.name, choice)}
                    >
                      {choice === 'concise' ? 'Concise' : choice === 'detaille' ? 'Détaillé' : choice}
                    </button>
                  ))}
                </div>
              </label>
            </div>
          )
        }

        const min = param.min ?? (param.type === 'int' ? 1 : 0)
        const max = param.max ?? (param.type === 'int' ? 100 : 2)
        const step = param.type === 'int' ? 1 : 0.1

        return (
          <div key={param.name}>
            <label className="m1-field">
              <span>
                {param.description || param.name} ({val})
              </span>
              <div className="m1-slider-group">
                <input
                  type="range"
                  min={min}
                  max={max}
                  step={step}
                  value={val}
                  onChange={(e) =>
                    onChange(
                      param.name,
                      param.type === 'int' ? parseInt(e.target.value, 10) : parseFloat(e.target.value)
                    )
                  }
                />
                <input
                  type="number"
                  min={min}
                  max={max}
                  step={step}
                  value={val}
                  onChange={(e) =>
                    onChange(
                      param.name,
                      param.type === 'int' ? parseInt(e.target.value, 10) : parseFloat(e.target.value)
                    )
                  }
                />
              </div>
            </label>
          </div>
        )
      })}
    </div>
  )
}

export function OverridesForm({ open, onToggle, overrides, onChange, params }) {
  return (
    <div className="m1-overrides-card">
      <button
        type="button"
        className="m1-overrides-header"
        onClick={onToggle}
        aria-expanded={open}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Sliders size={16} />
          <strong>Paramètres temporaires (cette exécution uniquement)</strong>
        </div>
        {open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
      </button>

      {open && (
        <div className="m1-overrides-body">
          <small className="m1-overrides-note">
            Ne modifie pas la configuration enregistrée de la version.
          </small>
          <div style={{ marginTop: '16px' }}>
            <TunableParametersControls
              params={params}
              values={overrides}
              onChange={onChange}
            />
          </div>
        </div>
      )}
    </div>
  )
}

export function ExecutionResult({ result, error }) {
  if (error) {
    const errorObj = typeof error === 'object' ? error : { message: error }
    return (
      <div className="m1-test-result failed" role="alert">
        <div className="m1-test-result-header">
          <span className="m1-status m1-status-failed">ÉCHEC</span>
          {errorObj.errorCode && (
            <span className="m1-error-code-badge">{errorObj.errorCode}</span>
          )}
        </div>
        <p className="m1-error-message">{errorObj.message || 'Une erreur est survenue lors de l exécution.'}</p>
        {errorObj.execution && (
          <div className="m1-meta">
            {errorObj.execution.durationMs !== undefined && (
              <span>Durée : {errorObj.execution.durationMs} ms</span>
            )}
            {errorObj.execution.totalTokens !== undefined && (
              <span>Tokens : {errorObj.execution.totalTokens ?? 'Indisponible'}</span>
            )}
          </div>
        )}
      </div>
    )
  }

  // Contract rule: NEVER show success unless status === 'COMPLETED'
  if (!result || result.status !== 'COMPLETED') {
    if (result && result.status === 'FAILED') {
      return (
        <div className="m1-test-result failed" role="alert">
          <div className="m1-test-result-header">
            <span className="m1-status m1-status-failed">ÉCHEC</span>
          </div>
          <p className="m1-error-message">{result.output || 'Exécution échouée.'}</p>
        </div>
      )
    }
    return null
  }

  const tokensDisplay =
    result.tokensUsed !== null && result.tokensUsed !== undefined
      ? `${result.tokensUsed} tokens`
      : 'Indisponible'

  return (
    <div className="m1-test-result success">
      <div className="m1-test-result-header">
        <span className="m1-status m1-status-completed">
          <Check size={14} style={{ marginRight: 4 }} /> COMPLETED
        </span>
        {result.executionId && (
          <span className="m1-meta-tag">Exécution #{result.executionId}</span>
        )}
      </div>

      <pre className="m1-output-text">{result.output}</pre>

      <div className="m1-meta">
        <span>
          <Clock size={13} style={{ marginRight: 4 }} />
          Durée : {result.durationMs ?? 0} ms
        </span>
        <span>
          <Zap size={13} style={{ marginRight: 4 }} />
          Tokens utilisés : {tokensDisplay}
        </span>
        {result.toolCallCount !== undefined && result.toolCallCount > 0 && (
          <span>Appels d outils : {result.toolCallCount}</span>
        )}
      </div>

      {result.appliedSettings && (
        <div className="m1-applied-settings">
          <small>Paramètres appliqués :</small>
          <code>{JSON.stringify(result.appliedSettings, null, 2)}</code>
        </div>
      )}
    </div>
  )
}

export function ExecutionDrawer({ execution, onClose }) {
  if (!execution) return null

  const formatDate = (dateStr) => {
    if (!dateStr) return '—'
    try {
      return new Date(dateStr).toLocaleString('fr-FR')
    } catch {
      return dateStr
    }
  }

  const totalTokens =
    execution.totalTokens !== null && execution.totalTokens !== undefined
      ? execution.totalTokens
      : 'Indisponible'

  return (
    <div className="m1-modal-backdrop" onClick={onClose}>
      <aside
        className="m1-drawer"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Détail de l'exécution"
      >
        <div className="m1-drawer-header">
          <div>
            <h2>Exécution #{execution.id}</h2>
            <small>{formatDate(execution.startedAt)}</small>
          </div>
          <button type="button" className="m1-close-btn" onClick={onClose} aria-label="Fermer">
            <X size={20} />
          </button>
        </div>

        <div className="m1-drawer-content">
          <div className="m1-drawer-section">
            <span className="m1-drawer-label">Statut</span>
            <div>
              <span
                className={`m1-status ${
                  execution.status === 'COMPLETED'
                    ? 'm1-status-completed'
                    : 'm1-status-failed'
                }`}
              >
                {execution.status}
              </span>
              {execution.errorCode && (
                <span className="m1-error-code-badge" style={{ marginLeft: 8 }}>
                  {execution.errorCode}
                </span>
              )}
            </div>
          </div>

          <div className="m1-drawer-section">
            <span className="m1-drawer-label">Métriques</span>
            <div className="m1-metrics-grid">
              <div>
                <small>Durée</small>
                <strong>{execution.durationMs ?? 0} ms</strong>
              </div>
              <div>
                <small>Tokens totaux</small>
                <strong>{totalTokens}</strong>
              </div>
              <div>
                <small>Appels d outils</small>
                <strong>{execution.toolCallCount ?? 0}</strong>
              </div>
              <div>
                <small>Modèle</small>
                <strong>{execution.modelUsed || 'gpt-4o-mini'}</strong>
              </div>
            </div>
          </div>

          <div className="m1-drawer-section">
            <span className="m1-drawer-label">Demande (Input)</span>
            <pre className="m1-code-block">{execution.input}</pre>
          </div>

          {execution.context && (
            <div className="m1-drawer-section">
              <span className="m1-drawer-label">Contexte</span>
              <pre className="m1-code-block">{execution.context}</pre>
            </div>
          )}

          {execution.output && (
            <div className="m1-drawer-section">
              <span className="m1-drawer-label">Réponse (Output)</span>
              <pre className="m1-code-block">{execution.output}</pre>
            </div>
          )}

          {execution.errorMessage && (
            <div className="m1-drawer-section">
              <span className="m1-drawer-label">Message d erreur</span>
              <div className="m1-error-text">{execution.errorMessage}</div>
            </div>
          )}

          {execution.overrides && (
            <div className="m1-drawer-section">
              <span className="m1-drawer-label">Surcharges (Overrides)</span>
              <pre className="m1-code-block">{execution.overrides}</pre>
            </div>
          )}

          {execution.appliedSettings && (
            <div className="m1-drawer-section">
              <span className="m1-drawer-label">Configuration appliquée</span>
              <pre className="m1-code-block">{execution.appliedSettings}</pre>
            </div>
          )}
        </div>
      </aside>
    </div>
  )
}

export function ExecutionTable({ executions, onSelect }) {
  const formatDate = (dateStr) => {
    if (!dateStr) return '—'
    try {
      return new Date(dateStr).toLocaleString('fr-FR', {
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return dateStr
    }
  }

  return (
    <div className="m1-table-wrapper">
      <table className="m1-table">
        <thead>
          <tr>
            <th>Date</th>
            <th>Version</th>
            <th>Statut</th>
            <th>Entrée</th>
            <th>Durée</th>
            <th>Tokens</th>
            <th style={{ textAlign: 'right' }}>Action</th>
          </tr>
        </thead>
        <tbody>
          {executions.map((exec) => {
            const tokens =
              exec.totalTokens !== null && exec.totalTokens !== undefined
                ? exec.totalTokens
                : 'Indisponible'
            return (
              <tr key={exec.id}>
                <td>{formatDate(exec.startedAt)}</td>
                <td>
                  <span className="m1-v-tag">V{exec.versionNumber || exec.versionId}</span>
                </td>
                <td>
                  <span
                    className={`m1-status ${
                      exec.status === 'COMPLETED'
                        ? 'm1-status-completed'
                        : 'm1-status-failed'
                    }`}
                  >
                    {exec.status}
                  </span>
                </td>
                <td className="m1-td-preview">
                  {exec.inputPreview || exec.input || '—'}
                </td>
                <td>{exec.durationMs ?? 0} ms</td>
                <td>{tokens}</td>
                <td style={{ textAlign: 'right' }}>
                  <button
                    type="button"
                    className="m1-ghost-sm"
                    onClick={() => onSelect(exec)}
                  >
                    Détails →
                  </button>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

export function Spinner({ size = 16 }) {
  return <Loader2 size={size} className="animate-spin" />
}
