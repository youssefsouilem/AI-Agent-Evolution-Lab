import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ArrowLeft, Play, Save, Archive, Eye, Copy, Check } from 'lucide-react'
import { getAgent, updateAgent, listVersions, createVersion, activateVersion, executeAgent, listTools } from '../../services/agentApi'
import { Field, SelectField, TextareaField, Spinner, ExecutionResult } from '../../components/agent-management/ui'
import '../../components/agent-management/agent-management.css'

export default function AgentEditorPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const activeTab = searchParams.get('tab') || 'editor'

  const [agent, setAgent] = useState(null)
  const [versions, setVersions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  // Editor state
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState('DRAFT')

  // Configuration state
  const [configModel, setConfigModel] = useState('gpt-4o-mini')
  const [configPrompt, setConfigPrompt] = useState('')
  const [configChangeLog, setConfigChangeLog] = useState('')
  const [configParams, setConfigParams] = useState({})
  const [configTools, setConfigTools] = useState([])
  const [toolsCatalog, setToolsCatalog] = useState([])
  const [toolsUnavailable, setToolsUnavailable] = useState(false)
  const [manualToolIds, setManualToolIds] = useState('')
  const [configErrors, setConfigErrors] = useState({})

  // Test state
  const [testVersionId, setTestVersionId] = useState('')
  const [testInput, setTestInput] = useState('')
  const [testContext, setTestContext] = useState('')
  const [testRunning, setTestRunning] = useState(false)
  const [testResult, setTestResult] = useState(null)
  const [testError, setTestError] = useState(null)

  const loadAgent = useCallback(async () => {
    setLoading(true)
    try {
      const agentData = await getAgent(id)
      setAgent(agentData)
      setName(agentData.name)
      setDescription(agentData.description || '')
      setStatus(agentData.status)
      
      const versionsData = await listVersions(id)
      setVersions(versionsData)
      
      const activeVersion = versionsData.find(v => v.active)
      if (activeVersion) {
        setTestVersionId(activeVersion.id)
        setConfigModel(activeVersion.model || 'gpt-4o-mini')
        setConfigPrompt(activeVersion.systemPrompt || '')
        setConfigParams(activeVersion.configuration || {})
        setConfigTools(activeVersion.configuration?.allowedToolIds || [])
      }

      try {
        const tools = await listTools()
        setToolsCatalog(tools)
      } catch {
        setToolsUnavailable(true)
      }

      setError(null)
    } catch (e) {
      if (e.status === 404) {
        setError({ message: 'Agent introuvable', notFound: true })
      } else {
        setError(e)
      }
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => { loadAgent() }, [loadAgent])

  const handleSaveEditor = async () => {
    setSaving(true)
    try {
      await updateAgent(id, { name, description, status })
      await loadAgent()
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
    }
  }

  const handleActivateVersion = async (vid) => {
    try {
      await activateVersion(id, vid)
      await loadAgent()
    } catch (e) {
      setError(e)
    }
  }

  const handleDuplicateVersion = async (vid) => {
    try {
      const version = versions.find(v => v.id === vid)
      if (version) {
        setConfigModel(version.model || 'gpt-4o-mini')
        setConfigPrompt(version.systemPrompt || '')
        setConfigParams(version.configuration || {})
        setConfigTools(version.configuration?.allowedToolIds || [])
        setSearchParams({ tab: 'config' })
      }
    } catch (e) {
      setError(e)
    }
  }

  const handleCreateVersion = async (e) => {
    e.preventDefault()
    const newErrors = {}
    if (!configModel.trim()) newErrors.model = 'Le modèle est requis'
    if (!configPrompt.trim()) newErrors.prompt = 'Le prompt système est requis'
    setConfigErrors(newErrors)
    if (Object.keys(newErrors).length > 0) return

    setSaving(true)
    try {
      const configuration = {
        ...configParams,
        allowedToolIds: toolsUnavailable 
          ? manualToolIds.split(',').map(id => parseInt(id.trim())).filter(Boolean)
          : configTools
      }
      await createVersion(id, {
        model: configModel,
        systemPrompt: configPrompt,
        changeLog: configChangeLog,
        configuration,
      })
      await loadAgent()
      setConfigChangeLog('')
      setSearchParams({ tab: 'versions' })
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
    }
  }

  const handleTest = async () => {
    setTestRunning(true)
    setTestResult(null)
    setTestError(null)
    try {
      const result = await executeAgent(id, {
        versionId: testVersionId,
        input: testInput,
        context: testContext,
      })
      setTestResult(result)
    } catch (e) {
      setTestError(e.message)
    } finally {
      setTestRunning(false)
    }
  }

  const handleToolToggle = (toolId) => {
    setConfigTools(prev => 
      prev.includes(toolId) 
        ? prev.filter(id => id !== toolId)
        : [...prev, toolId]
    )
  }

  const handleParamChange = (name, value) => {
    setConfigParams(prev => ({ ...prev, [name]: value }))
  }

  if (loading) return <div className="m1-loading"><span /><span /><span /></div>
  if (error?.notFound) {
    return (
      <div className="m1-empty">
        <strong>Agent introuvable</strong>
        <button className="m1-ghost" onClick={() => navigate('/agents')}>Retour à la liste</button>
      </div>
    )
  }
  if (!agent) return null

  const formatDate = (dateStr) => {
    const date = new Date(dateStr)
    return date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' })
  }

  return (
    <div>
      <div className="m1-editor-bar">
        <button className="m1-back" onClick={() => navigate('/agents')}><ArrowLeft size={20} /></button>
        <div className="m1-icon"><Play size={20} /></div>
        <div className="m1-title">
          <h1>{agent.name}</h1>
          <small>Agent Studio / Éditeur</small>
        </div>
        <div className="m1-actions">
          <button className="m1-ghost" onClick={handleSaveEditor} disabled={saving}>
            <Save size={14} /> {saving ? '...' : 'Enregistrer'}
          </button>
          <button className="m1-ghost" onClick={() => updateAgent(id, { status: 'ARCHIVED' }).then(loadAgent)}>
            <Archive size={14} /> Archiver
          </button>
          <button className="m1-primary" onClick={() => setSearchParams({ tab: 'test' })}>
            <Play size={14} /> Tester
          </button>
        </div>
      </div>

      {error && <div className="m1-error"><strong>{error.message}</strong></div>}

      <div className="m1-tabs">
        <button className={`m1-tab ${activeTab === 'editor' ? 'active' : ''}`} onClick={() => setSearchParams({ tab: 'editor' })}>Éditeur</button>
        <button className={`m1-tab ${activeTab === 'versions' ? 'active' : ''}`} onClick={() => setSearchParams({ tab: 'versions' })}>Versions ({versions.length})</button>
        <button className={`m1-tab ${activeTab === 'config' ? 'active' : ''}`} onClick={() => setSearchParams({ tab: 'config' })}>Configuration</button>
        <button className={`m1-tab ${activeTab === 'test' ? 'active' : ''}`} onClick={() => setSearchParams({ tab: 'test' })}>Test</button>
      </div>

      {activeTab === 'editor' && (
        <div className="m1-tab-content active">
          <Field label="Nom" value={name} onChange={(e) => setName(e.target.value)} />
          <TextareaField label="Description" value={description} onChange={(e) => setDescription(e.target.value)} />
          <SelectField label="Statut" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="DRAFT">Brouillon</option>
            <option value="ACTIVE">Actif</option>
            <option value="ARCHIVED">Archivé</option>
          </SelectField>
        </div>
      )}

      {activeTab === 'versions' && (
        <div className="m1-tab-content active">
          <div className="m1-version-list">
            {versions.map(version => (
              <div key={version.id} className="m1-version-item">
                <div className="m1-v-icon"><Play size={18} /></div>
                <div className="m1-v-info">
                  <div className="m1-v-badges">
                    {version.active && <span className="m1-v-badge m1-v-badge-active">Active</span>}
                    {version.source === 'M5' && <span className="m1-v-badge m1-v-badge-m5">Créée par M5</span>}
                  </div>
                  <strong>{version.label}</strong>
                  <small>{version.model} · {formatDate(version.createdAt)}</small>
                  <small style={{ display: 'block', marginTop: '4px' }}>{version.changeLog || 'Aucun changement'}</small>
                </div>
                <div className="m1-v-actions">
                  {!version.active && (
                    <button className="m1-ghost" onClick={() => handleActivateVersion(version.id)}>
                      <Check size={14} /> Activer
                    </button>
                  )}
                  <button className="m1-ghost" onClick={() => navigate(`/agents/${id}?tab=config`)}><Eye size={14} /> Voir</button>
                  <button className="m1-ghost" onClick={() => handleDuplicateVersion(version.id)}><Copy size={14} /> Dupliquer</button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {activeTab === 'config' && (
        <div className="m1-tab-content active">
          <form onSubmit={handleCreateVersion}>
            <div className="m1-config-grid">
              <div className="m1-config-full">
                <Field label="Modèle" value={configModel} onChange={(e) => setConfigModel(e.target.value)} error={configErrors.model} />
              </div>
              <div className="m1-config-full">
                <TextareaField label="Prompt système" value={configPrompt} onChange={(e) => setConfigPrompt(e.target.value)} error={configErrors.prompt} />
              </div>
              <div className="m1-config-full">
                <Field label="Journal des changements" value={configChangeLog} onChange={(e) => setConfigChangeLog(e.target.value)} />
              </div>
              
              <div>
                <label className="m1-field">
                  <span>Température (Créativité)</span>
                  <div className="m1-slider-group">
                    <input type="range" min="0" max="2" step="0.1" value={configParams.temperature || 0.7} onChange={(e) => handleParamChange('temperature', parseFloat(e.target.value))} />
                    <input type="number" min="0" max="2" step="0.1" value={configParams.temperature || 0.7} onChange={(e) => handleParamChange('temperature', parseFloat(e.target.value))} />
                  </div>
                </label>
              </div>
              
              <div>
                <label className="m1-field">
                  <span>Max Tokens (Longueur max)</span>
                  <div className="m1-slider-group">
                    <input type="range" min="256" max="4096" step="64" value={configParams.maxTokens || 1024} onChange={(e) => handleParamChange('maxTokens', parseInt(e.target.value))} />
                    <input type="number" min="256" max="4096" value={configParams.maxTokens || 1024} onChange={(e) => handleParamChange('maxTokens', parseInt(e.target.value))} />
                  </div>
                </label>
              </div>
              
              <div>
                <label className="m1-field">
                  <span>Max Tours (Appels outils)</span>
                  <div className="m1-slider-group">
                    <input type="range" min="1" max="10" step="1" value={configParams.maxTurns || 3} onChange={(e) => handleParamChange('maxTurns', parseInt(e.target.value))} />
                    <input type="number" min="1" max="10" value={configParams.maxTurns || 3} onChange={(e) => handleParamChange('maxTurns', parseInt(e.target.value))} />
                  </div>
                </label>
              </div>
              
              <div>
                <label className="m1-field">
                  <span>Timeout (secondes)</span>
                  <div className="m1-slider-group">
                    <input type="range" min="5" max="120" step="5" value={configParams.timeoutSeconds || 30} onChange={(e) => handleParamChange('timeoutSeconds', parseInt(e.target.value))} />
                    <input type="number" min="5" max="120" value={configParams.timeoutSeconds || 30} onChange={(e) => handleParamChange('timeoutSeconds', parseInt(e.target.value))} />
                  </div>
                </label>
              </div>
              
              <div className="m1-config-full">
                <SelectField label="Style de réponse" value={configParams.style || 'concise'} onChange={(e) => handleParamChange('style', e.target.value)}>
                  <option value="concise">Concise</option>
                  <option value="detaille">Détaillé</option>
                </SelectField>
              </div>
              
              <div className="m1-config-full">
                <label className="m1-field">
                  <span>Outils autorisés</span>
                  {toolsUnavailable ? (
                    <>
                      <Field 
                        placeholder="IDs séparés par des virgules (ex: 1,2,3)" 
                        value={manualToolIds} 
                        onChange={(e) => setManualToolIds(e.target.value)} 
                      />
                      <div className="m1-warning">Catalogue des outils indisponible</div>
                    </>
                  ) : (
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                      {toolsCatalog.map(tool => (
                        <button
                          key={tool.id}
                          type="button"
                          className={`m1-chip ${configTools.includes(tool.id) ? 'selected' : ''}`}
                          onClick={() => handleToolToggle(tool.id)}
                        >
                          {tool.name}
                        </button>
                      ))}
                    </div>
                  )}
                </label>
              </div>
            </div>
            <div style={{ marginTop: '24px' }}>
              <button type="submit" className="m1-primary" disabled={saving}>
                {saving ? '...' : 'Enregistrer comme nouvelle version'}
              </button>
            </div>
          </form>
        </div>
      )}

      {activeTab === 'test' && (
        <div className="m1-tab-content active">
          <div className="m1-test-panel">
            <SelectField 
              label="Version" 
              value={testVersionId} 
              onChange={(e) => setTestVersionId(e.target.value)}
            >
              {versions.map(v => (
                <option key={v.id} value={v.id}>{v.label}</option>
              ))}
            </SelectField>
            <TextareaField 
              label="Votre demande" 
              placeholder="Entrez votre demande..." 
              value={testInput} 
              onChange={(e) => setTestInput(e.target.value)} 
            />
            <TextareaField 
              label="Contexte (optionnel)" 
              placeholder="Contexte additionnel..." 
              value={testContext} 
              onChange={(e) => setTestContext(e.target.value)} 
            />
            <button 
              className="m1-primary" 
              onClick={handleTest} 
              disabled={testRunning || !testInput.trim()}
              style={{ marginTop: '16px' }}
            >
              {testRunning ? <><Spinner size={16} /> Exécution...</> : <><Play size={14} /> Exécuter</>}
            </button>
            
            {(testResult || testError) && (
              <ExecutionResult result={testResult} error={testError} />
            )}
          </div>
        </div>
      )}
    </div>
  )
}
