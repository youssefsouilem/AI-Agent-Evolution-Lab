import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createAgent } from '../../services/agentApi'
import { Field, SelectField, TextareaField } from '../../components/agent-management/ui'
import '../../components/agent-management/agent-management.css'

export default function NewAgentPage() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState('DRAFT')
  const [errors, setErrors] = useState({})
  const [loading, setLoading] = useState(false)

  const validate = () => {
    const newErrors = {}
    if (!name.trim()) newErrors.name = 'Le nom est requis'
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!validate()) return

    setLoading(true)
    try {
      const agent = await createAgent({ name, description, status })
      navigate(`/agents/${agent.id}?tab=config`)
    } catch (err) {
      setErrors({ submit: err.message })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="m1-center-card">
      <h1>Créer un agent</h1>
      <form onSubmit={handleSubmit}>
        <Field
          label="Nom"
          placeholder="Ex. Agent support"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={errors.name}
        />
        <TextareaField
          label="Description"
          placeholder="Décrivez le rôle de votre agent..."
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <SelectField
          label="Statut"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
        >
          <option value="DRAFT">Brouillon</option>
          <option value="ACTIVE">Actif</option>
          <option value="ARCHIVED">Archivé</option>
        </SelectField>
        {errors.submit && <div className="m1-error"><strong>{errors.submit}</strong></div>}
        <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end', marginTop: '24px' }}>
          <button type="button" className="m1-ghost" onClick={() => navigate('/agents')}>Annuler</button>
          <button type="submit" className="m1-primary" disabled={loading}>
            {loading ? 'Création...' : 'Continuer →'}
          </button>
        </div>
      </form>
    </div>
  )
}
