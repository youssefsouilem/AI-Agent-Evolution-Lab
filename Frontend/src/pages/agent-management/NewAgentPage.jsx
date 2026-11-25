import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, Sparkles } from 'lucide-react'
import { createAgent } from '../../services/agentApi'
import { useAuth } from '../../components/common/AuthContext'
import { Field, SelectField, TextareaField, ErrorBanner, Spinner } from '../../components/agent-management/ui'
import '../../components/agent-management/agent-management.css'

export default function NewAgentPage() {
  const navigate = useNavigate()
  const { isAdmin, limits, usage, refreshProfile } = useAuth()

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState('DRAFT')
  const [errors, setErrors] = useState({})
  const [loading, setLoading] = useState(false)
  const [backendError, setBackendError] = useState(null)

  useEffect(() => {
    refreshProfile()
  }, [refreshProfile])

  const isQuotaReached =
    !isAdmin &&
    limits?.maxAgents !== null &&
    limits?.maxAgents !== undefined &&
    (usage?.agents ?? 0) >= limits.maxAgents

  const validate = () => {
    const newErrors = {}
    if (!name.trim()) {
      newErrors.name = "Le nom de l'agent est obligatoire"
    }
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (isQuotaReached) return
    setBackendError(null)

    if (!validate()) return

    setLoading(true)
    try {
      const agent = await createAgent({
        name: name.trim(),
        description: description.trim(),
        status,
      })
      refreshProfile()
      navigate(`/agents/${agent.id}?tab=config`)
    } catch (err) {
      setBackendError(err)
      refreshProfile()
    } finally {
      setLoading(false)
    }
  }

  const handleAssistCreate = async () => {
    if (isQuotaReached) return
    setBackendError(null)
    setLoading(true)
    try {
      const agent = await createAgent({
        name: name.trim() || 'Nouvel Agent IA',
        description: description.trim() || "Agent conçu avec l'assistant IA",
        status: 'DRAFT',
      })
      refreshProfile()
      navigate(`/agents/${agent.id}?tab=config&action=assist`)
    } catch (err) {
      setBackendError(err)
      refreshProfile()
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <div className="m1-breadcrumb">
        Agent Studio <span>/</span> <span style={{ cursor: 'pointer' }} onClick={() => navigate('/agents')}>Mes agents</span> <span>/</span> Nouvel agent
      </div>

      <div className="m1-center-card">
        <h1>Créer un agent</h1>
        <p className="subtitle">Configurez l'identité de base de votre nouvel agent IA.</p>

        {isQuotaReached && (
          <div className="m1-warning" style={{ marginBottom: 20 }}>
            <span>
              <strong>Limite atteinte :</strong> Vous avez atteint votre quota maximal de {limits.maxAgents} agents. Supprimez un agent existant ou contactez un administrateur pour augmenter votre quota.
            </span>
          </div>
        )}

        {backendError && <ErrorBanner error={backendError} />}

        <form onSubmit={handleSubmit}>
          <Field
            label="Nom"
            placeholder="Ex. Agent support"
            value={name}
            onChange={(e) => {
              setName(e.target.value)
              if (errors.name) setErrors((prev) => ({ ...prev, name: null }))
            }}
            error={errors.name}
            disabled={isQuotaReached}
            required
            autoFocus
          />

          <TextareaField
            label="Description"
            placeholder="Décrivez les objectifs, le rôle ou le contexte de votre agent..."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            disabled={isQuotaReached}
          />

          <SelectField
            label="Statut"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            disabled={isQuotaReached}
            helper="Un agent en brouillon peut être configuré et testé avant son activation."
          >
            <option value="DRAFT">Brouillon</option>
            <option value="ACTIVE">Actif</option>
            <option value="ARCHIVED">Archivé</option>
          </SelectField>

          <div
            style={{
              display: 'flex',
              gap: '12px',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginTop: '32px',
              flexWrap: 'wrap',
            }}
          >
            <button
              type="button"
              className="m1-ghost"
              onClick={handleAssistCreate}
              disabled={loading || isQuotaReached}
              title="Créer un brouillon et ouvrir directement l'assistant IA"
            >
              <Sparkles size={15} style={{ color: 'var(--purple)' }} />
              Construire avec l'IA
            </button>

            <div style={{ display: 'flex', gap: '10px' }}>
              <button
                type="button"
                className="m1-ghost"
                onClick={() => navigate('/agents')}
                disabled={loading}
              >
                <ArrowLeft size={14} /> Annuler
              </button>
              <button
                type="submit"
                className="m1-primary"
                disabled={loading || isQuotaReached}
                title={isQuotaReached ? 'Limite atteinte : supprimez un agent' : undefined}
              >
                {loading ? (
                  <>
                    <Spinner size={16} /> Création...
                  </>
                ) : (
                  'Continuer →'
                )}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  )
}
