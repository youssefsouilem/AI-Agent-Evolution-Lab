import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { createAgent } from '../../services/agentApi'
import { Field, SelectField, TextareaField, ErrorBanner, Spinner } from '../../components/agent-management/ui'
import '../../components/agent-management/agent-management.css'

export default function NewAgentPage() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState('DRAFT')
  const [errors, setErrors] = useState({})
  const [loading, setLoading] = useState(false)
  const [backendError, setBackendError] = useState(null)

  const validate = () => {
    const newErrors = {}
    if (!name.trim()) {
      newErrors.name = 'Le nom de l agent est obligatoire'
    }
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setBackendError(null)

    if (!validate()) return

    setLoading(true)
    try {
      const agent = await createAgent({
        name: name.trim(),
        description: description.trim(),
        status,
      })
      navigate(`/agents/${agent.id}?tab=config`)
    } catch (err) {
      setBackendError(err)
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
        <p className="subtitle">Configurez l identité de base de votre nouvel agent IA.</p>

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
            required
            autoFocus
          />

          <TextareaField
            label="Description"
            placeholder="Décrivez les objectifs, le rôle ou le contexte de votre agent..."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />

          <SelectField
            label="Statut"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
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
              justifyContent: 'flex-end',
              marginTop: '32px',
            }}
          >
            <button
              type="button"
              className="m1-ghost"
              onClick={() => navigate('/agents')}
              disabled={loading}
            >
              <ArrowLeft size={14} /> Annuler
            </button>
            <button type="submit" className="m1-primary" disabled={loading}>
              {loading ? (
                <>
                  <Spinner size={16} /> Création...
                </>
              ) : (
                'Continuer →'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
