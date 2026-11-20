import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import {
  Archive,
  ArrowLeft,
  Bot,
  Check,
  Clock,
  Copy,
  Edit3,
  Eye,
  Play,
  Save,
  Sparkles,
} from 'lucide-react'
import {
  getAgent,
  updateAgent,
  listVersions,
  createVersion,
  activateVersion,
  executeAgent,
  listTools,
  getTunableParameters,
  getCapabilities,
  listExecutions,
} from '../../services/agentApi'
import { useAuth } from '../../components/common/AuthContext'
import {
  Field,
  SelectField,
  TextareaField,
  Spinner,
  ExecutionResult,
  AgentStatusBadge,
  ToolChips,
  TunableParametersControls,
  OverridesForm,
  ExecutionTable,
  ExecutionDrawer,
  ErrorBanner,
  Loading,
  EmptyState,
} from '../../components/agent-management/ui'
import '../../components/agent-management/agent-management.css'

const DEFAULT_CONFIG_PARAMS = [
  {
    name: 'temperature',
    type: 'float',
    min: 0.0,
    max: 2.0,
    current: 0.7,
    description: 'Créativité',
  },
  {
    name: 'maxTokens',
    type: 'int',
    min: 256,
    max: 4096,
    current: 1024,
    description: 'Longueur maximale de la réponse',
  },
  {
    name: 'maxTurns',
    type: 'int',
    min: 1,
    max: 10,
    current: 3,
    description: "Nombre maximal d'appels d'outils",
  },
  {
    name: 'timeoutSeconds',
    type: 'int',
    min: 5,
    max: 120,
    current: 30,
    description: "Délai maximal d'exécution (secondes)",
  },
  {
    name: 'style',
    type: 'categorical',
    choices: ['concise', 'detaille'],
    current: 'concise',
    description: 'Style de réponse',
  },
]

export default function AgentEditorPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { isAdmin } = useAuth()

  const activeTab = searchParams.get('tab') || 'editor'

  const [agent, setAgent] = useState(null)
  const [versions, setVersions] = useState([])
  const [capabilities, setCapabilities] = useState({ executions: false, overrides: false })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)
  const [renameModalOpen, setRenameModalOpen] = useState(false)
  const [renameValue, setRenameValue] = useState('')

  // Editor form state
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState('DRAFT')
  const [editorDirty, setEditorDirty] = useState(false)

  // Configuration form state (for creating a NEW version)
  const [configModel, setConfigModel] = useState('gpt-4o-mini')
  const [configPrompt, setConfigPrompt] = useState('')
  const [configChangeLog, setConfigChangeLog] = useState('')
  const [configParams, setConfigParams] = useState({
    temperature: 0.7,
    maxTokens: 1024,
    maxTurns: 3,
    timeoutSeconds: 30,
    style: 'concise',
  })
  const [tunableDefinitions, setTunableDefinitions] = useState(DEFAULT_CONFIG_PARAMS)
  const [configTools, setConfigTools] = useState([])
  const [toolsCatalog, setToolsCatalog] = useState([])
  const [toolsUnavailable, setToolsUnavailable] = useState(false)
  const [manualToolIds, setManualToolIds] = useState('')
  const [configErrors, setConfigErrors] = useState({})
  const [configDirty, setConfigDirty] = useState(false)

  // Test tab state
  const [testVersionId, setTestVersionId] = useState('')
  const [testInput, setTestInput] = useState('')
  const [testContext, setTestContext] = useState('')
  const [testContextError, setTestContextError] = useState(null)
  const [overridesOpen, setOverridesOpen] = useState(false)
  const [testOverrides, setTestOverrides] = useState({})
  const [testRunning, setTestRunning] = useState(false)
  const [testResult, setTestResult] = useState(null)
  const [testError, setTestError] = useState(null)

  // History tab state
  const [historyItems, setHistoryItems] = useState([])
  const [historyTotal, setHistoryTotal] = useState(0)
  const [historyPage, setHistoryPage] = useState(0)
  const [historyStatusFilter, setHistoryStatusFilter] = useState('')
  const [historyVersionFilter, setHistoryVersionFilter] = useState('')
  const [historyLoading, setHistoryLoading] = useState(false)
  const [selectedExecution, setSelectedExecution] = useState(null)

  // Unsaved changes alert
  useEffect(() => {
    const handleBeforeUnload = (e) => {
      if (editorDirty || configDirty) {
        e.preventDefault()
        e.returnValue = ''
      }
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [editorDirty, configDirty])

  // Load capabilities once
  useEffect(() => {
    getCapabilities().then(setCapabilities)
  }, [])

  // Load tools catalogue once
  useEffect(() => {
    listTools()
      .then((tools) => {
        if (!tools || tools.length === 0) {
          setToolsUnavailable(true)
        } else {
          setToolsCatalog(tools)
          setToolsUnavailable(false)
        }
      })
      .catch(() => {
        setToolsUnavailable(true)
      })
  }, [])

  const loadAgent = useCallback(async () => {
    setLoading(true)
    try {
      const agentData = await getAgent(id)
      setAgent(agentData)
      setName(agentData.name || '')
      setRenameValue(agentData.name || '')
      setDescription(agentData.description || '')
      setStatus(agentData.status || 'DRAFT')
      setEditorDirty(false)

      const versionsData = await listVersions(id)
      const sortedVersions = Array.isArray(versionsData)
        ? [...versionsData].sort((a, b) => b.versionNumber - a.versionNumber)
        : []
      setVersions(sortedVersions)

      const activeVersion = sortedVersions.find((v) => v.active) || sortedVersions[0]
      if (activeVersion) {
        setTestVersionId(String(activeVersion.id))
        // Fetch tunable parameters from backend for this version
        try {
          const tunableList = await getTunableParameters(id, activeVersion.id)
          if (Array.isArray(tunableList) && tunableList.length > 0) {
            setTunableDefinitions(tunableList)
          }
        } catch {
          // fallback to defaults
        }

        const cfg = activeVersion.configuration || {}
        setConfigModel(activeVersion.model || 'gpt-4o-mini')
        setConfigPrompt(activeVersion.systemPrompt || '')
        setConfigParams({
          temperature: cfg.temperature ?? 0.7,
          maxTokens: cfg.maxTokens ?? 1024,
          maxTurns: cfg.maxTurns ?? 3,
          timeoutSeconds: cfg.timeoutSeconds ?? 30,
          style: cfg.style || 'concise',
        })
        setConfigTools(Array.isArray(cfg.allowedToolIds) ? cfg.allowedToolIds : [])
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

  useEffect(() => {
    loadAgent()
  }, [loadAgent])

  // Load history when tab is 'history'
  const loadHistory = useCallback(async () => {
    if (!capabilities.executions) return
    setHistoryLoading(true)
    try {
      const res = await listExecutions(id, {
        page: historyPage,
        size: 10,
        status: historyStatusFilter || undefined,
        versionId: historyVersionFilter ? parseInt(historyVersionFilter, 10) : undefined,
      })
      setHistoryItems(res.items)
      setHistoryTotal(res.totalCount)
    } catch {
      setHistoryItems([])
      setHistoryTotal(0)
    } finally {
      setHistoryLoading(false)
    }
  }, [id, capabilities.executions, historyPage, historyStatusFilter, historyVersionFilter])

  useEffect(() => {
    if (activeTab === 'history') {
      loadHistory()
    }
  }, [activeTab, loadHistory])

  const setTab = (tab) => {
    setSearchParams({ tab })
  }

  // Handle Editor Save
  const handleSaveEditor = async () => {
    setSaving(true)
    try {
      const updated = await updateAgent(id, { name, description, status })
      setAgent(updated)
      setEditorDirty(false)
      setError(null)
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
    }
  }

  // Handle Rename
  const handleRenameConfirm = async () => {
    if (!renameValue.trim()) return
    setSaving(true)
    try {
      const updated = await updateAgent(id, {
        name: renameValue.trim(),
        description,
        status,
      })
      setAgent(updated)
      setName(updated.name)
      setRenameModalOpen(false)
      setError(null)
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
    }
  }

  // Handle Archive
  const handleArchive = async () => {
    setSaving(true)
    try {
      const updated = await updateAgent(id, {
        name,
        description,
        status: 'ARCHIVED',
      })
      setAgent(updated)
      setStatus('ARCHIVED')
      setError(null)
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
    }
  }

  // Handle Activate Version
  const handleActivateVersion = async (vid) => {
    try {
      await activateVersion(id, vid)
      await loadAgent()
    } catch (e) {
      setError(e)
    }
  }

  // Handle Duplicate / Pre-fill Version
  const handleDuplicateVersion = (v) => {
    const cfg = v.configuration || {}
    setConfigModel(v.model || 'gpt-4o-mini')
    setConfigPrompt(v.systemPrompt || '')
    setConfigChangeLog(`Basé sur ${v.label || `Version ${v.versionNumber}`}`)
    setConfigParams({
      temperature: cfg.temperature ?? 0.7,
      maxTokens: cfg.maxTokens ?? 1024,
      maxTurns: cfg.maxTurns ?? 3,
      timeoutSeconds: cfg.timeoutSeconds ?? 30,
      style: cfg.style || 'concise',
    })
    setConfigTools(Array.isArray(cfg.allowedToolIds) ? cfg.allowedToolIds : [])
    setConfigDirty(true)
    setTab('config')
  }

  // View Version (read-only inspect then can edit in config tab)
  const handleViewVersion = (v) => {
    handleDuplicateVersion(v)
  }

  // Create Version Submission
  const handleCreateVersion = async (e) => {
    e.preventDefault()
    const newErrors = {}

    if (!configModel.trim()) newErrors.model = 'Le modèle est obligatoire'
    if (!configPrompt.trim()) newErrors.prompt = 'Le prompt système est obligatoire'

    const temp = configParams.temperature
    if (temp === undefined || isNaN(temp) || temp < 0 || temp > 2) {
      newErrors.temperature = 'La créativité doit être comprise entre 0.0 et 2.0'
    }

    const tokens = configParams.maxTokens
    if (tokens === undefined || isNaN(tokens) || tokens < 256 || tokens > 4096) {
      newErrors.maxTokens = 'La longueur maximale doit être comprise entre 256 et 4096'
    }

    const turns = configParams.maxTurns
    if (turns === undefined || isNaN(turns) || turns < 1 || turns > 10) {
      newErrors.maxTurns = 'Le nombre maximal d appels d outils doit être entre 1 et 10'
    }

    const timeout = configParams.timeoutSeconds
    if (timeout === undefined || isNaN(timeout) || timeout < 1) {
      newErrors.timeoutSeconds = 'Le délai maximal doit être supérieur ou égal à 1 seconde'
    }

    setConfigErrors(newErrors)
    if (Object.keys(newErrors).length > 0) return

    setSaving(true)
    try {
      const allowedToolIds = toolsUnavailable
        ? manualToolIds
            .split(',')
            .map((s) => parseInt(s.trim(), 10))
            .filter((n) => !isNaN(n))
        : configTools

      const configuration = {
        temperature: Number(configParams.temperature),
        maxTokens: Number(configParams.maxTokens),
        maxTurns: Number(configParams.maxTurns),
        timeoutSeconds: Number(configParams.timeoutSeconds),
        style: configParams.style || 'concise',
        allowedToolIds: allowedToolIds || [],
      }

      await createVersion(id, {
        model: configModel.trim(),
        systemPrompt: configPrompt.trim(),
        changeLog: configChangeLog.trim() || 'Nouvelle version',
        configuration,
      })

      setConfigChangeLog('')
      setConfigDirty(false)
      await loadAgent()
      setTab('versions')
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  // Test execution
  const handleTestExecute = async () => {
    setTestContextError(null)
    setTestResult(null)
    setTestError(null)

    if (!testInput.trim()) return

    let parsedContext = undefined
    if (testContext.trim()) {
      try {
        parsedContext = JSON.parse(testContext)
        if (typeof parsedContext !== 'object' || Array.isArray(parsedContext) || parsedContext === null) {
          setTestContextError('Le contexte doit être un objet JSON valide (ex. {"key":"valeur"})')
          return
        }
      } catch {
        setTestContextError('Le contexte doit être un objet JSON valide')
        return
      }
    }

    // Prepare overrides only if capability is ON and user entered fields
    let overridesToSend = undefined
    if (capabilities.overrides && Object.keys(testOverrides).length > 0) {
      overridesToSend = testOverrides
    }

    setTestRunning(true)
    try {
      const result = await executeAgent(id, {
        versionId: testVersionId ? parseInt(testVersionId, 10) : undefined,
        input: testInput.trim(),
        context: parsedContext,
        overrides: overridesToSend,
      })
      setTestResult(result)
      if (result?.status === 'FAILED') {
        setTestError({ message: result.output || 'Exécution échouée' })
      }
      if (capabilities.executions && activeTab === 'history') {
        loadHistory()
      }
    } catch (e) {
      setTestError(e)
    } finally {
      setTestRunning(false)
    }
  }

  const handleToolToggle = (toolId) => {
    setConfigDirty(true)
    setConfigTools((prev) =>
      prev.includes(toolId) ? prev.filter((t) => t !== toolId) : [...prev, toolId]
    )
  }

  const handleConfigParamChange = (name, value) => {
    setConfigDirty(true)
    setConfigParams((prev) => ({ ...prev, [name]: value }))
  }

  const handleOverrideParamChange = (name, value) => {
    setTestOverrides((prev) => ({ ...prev, [name]: value }))
  }

  const formatDate = (dateStr) => {
    if (!dateStr) return '—'
    try {
      return new Date(dateStr).toLocaleDateString('fr-FR', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
      })
    } catch {
      return dateStr
    }
  }

  if (loading) {
    return <Loading label="Chargement de l'agent..." />
  }

  if (error?.notFound) {
    return (
      <div style={{ maxWidth: 520, margin: '80px auto', textAlign: 'center' }}>
        <EmptyState
          text="Agent introuvable"
          subtext="L'agent demandé n'existe pas ou vous n'avez pas les droits d'accès requis."
        />
        <div style={{ marginTop: 20 }}>
          <button type="button" className="m1-primary" onClick={() => navigate('/agents')}>
            <ArrowLeft size={16} /> Retour à la liste des agents
          </button>
        </div>
      </div>
    )
  }

  if (!agent) return null

  return (
    <div>
      <div className="m1-editor-bar">
        <button
          type="button"
          className="m1-back"
          onClick={() => navigate('/agents')}
          aria-label="Retour aux agents"
        >
          <ArrowLeft size={18} />
        </button>

        <div className="m1-icon">
          <Bot size={22} />
        </div>

        <div className="m1-title">
          <h1>{agent.name}</h1>
          <small>
            <span>Agent Studio / Éditeur</span>
            <AgentStatusBadge status={agent.status} />
            {isAdmin && (
              <span style={{ color: 'var(--purple)', fontWeight: 600 }}>
                Propriétaire : {agent.ownerId || 'Non assigné'}
              </span>
            )}
          </small>
        </div>

        <div className="m1-actions">
          <button
            type="button"
            className="m1-ghost"
            onClick={() => {
              setRenameValue(agent.name)
              setRenameModalOpen(true)
            }}
          >
            <Edit3 size={14} /> Renommer
          </button>
          <button
            type="button"
            className="m1-ghost"
            onClick={handleSaveEditor}
            disabled={saving || !editorDirty}
          >
            <Save size={14} /> {saving ? 'Enregistrement...' : 'Enregistrer'}
          </button>
          {agent.status !== 'ARCHIVED' && (
            <button type="button" className="m1-ghost" onClick={handleArchive} disabled={saving}>
              <Archive size={14} /> Archiver
            </button>
          )}
          <button type="button" className="m1-primary" onClick={() => setTab('test')}>
            <Play size={14} /> Tester
          </button>
        </div>
      </div>

      {error && !error.notFound && (
        <ErrorBanner error={error} retry={loadAgent} />
      )}

      {editorDirty && (
        <div className="m1-warning" style={{ marginBottom: 16 }}>
          <span>Des modifications ne sont pas encore enregistrées dans l onglet Éditeur.</span>
        </div>
      )}

      <div className="m1-tabs" role="tablist">
        <button
          type="button"
          className={`m1-tab ${activeTab === 'editor' ? 'active' : ''}`}
          onClick={() => setTab('editor')}
          role="tab"
          aria-selected={activeTab === 'editor'}
        >
          Éditeur
        </button>
        <button
          type="button"
          className={`m1-tab ${activeTab === 'versions' ? 'active' : ''}`}
          onClick={() => setTab('versions')}
          role="tab"
          aria-selected={activeTab === 'versions'}
        >
          Versions ({versions.length})
        </button>
        <button
          type="button"
          className={`m1-tab ${activeTab === 'config' ? 'active' : ''}`}
          onClick={() => setTab('config')}
          role="tab"
          aria-selected={activeTab === 'config'}
        >
          Configuration
        </button>
        <button
          type="button"
          className={`m1-tab ${activeTab === 'test' ? 'active' : ''}`}
          onClick={() => setTab('test')}
          role="tab"
          aria-selected={activeTab === 'test'}
        >
          Test
        </button>
        <button
          type="button"
          className={`m1-tab ${activeTab === 'history' ? 'active' : ''}`}
          onClick={() => setTab('history')}
          role="tab"
          aria-selected={activeTab === 'history'}
        >
          Historique
        </button>
      </div>

      {/* TAB 1: ÉDITEUR */}
      {activeTab === 'editor' && (
        <div className="m1-tab-card">
          <Field
            label="Nom de l agent"
            value={name}
            onChange={(e) => {
              setName(e.target.value)
              setEditorDirty(true)
            }}
          />
          <TextareaField
            label="Description"
            value={description}
            onChange={(e) => {
              setDescription(e.target.value)
              setEditorDirty(true)
            }}
          />
          <SelectField
            label="Statut"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value)
              setEditorDirty(true)
            }}
          >
            <option value="DRAFT">Brouillon</option>
            <option value="ACTIVE">Actif</option>
            <option value="ARCHIVED">Archivé</option>
          </SelectField>

          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 24 }}>
            <button
              type="button"
              className="m1-primary"
              onClick={handleSaveEditor}
              disabled={saving || !editorDirty}
            >
              <Save size={14} /> {saving ? 'Enregistrement...' : 'Enregistrer les modifications'}
            </button>
          </div>
        </div>
      )}

      {/* TAB 2: VERSIONS */}
      {activeTab === 'versions' && (
        <div>
          <div className="m1-version-list">
            {versions.map((version) => (
              <div key={version.id} className="m1-version-item">
                <div className="m1-v-icon">
                  <Play size={18} />
                </div>
                <div className="m1-v-info">
                  <div className="m1-v-badges">
                    {version.active && (
                      <span className="m1-v-badge m1-v-badge-active">Active</span>
                    )}
                    {version.source === 'M5' && (
                      <span className="m1-v-badge m1-v-badge-m5">
                        <Sparkles size={11} style={{ marginRight: 3 }} /> Créée par M5
                      </span>
                    )}
                  </div>
                  <strong>{version.label || `Version ${version.versionNumber}`}</strong>
                  <small>
                    {version.model || 'gpt-4o-mini'} · {formatDate(version.createdAt)}
                  </small>
                  <small style={{ display: 'block', marginTop: 4, color: 'var(--muted)' }}>
                    {version.changeLog || 'Aucun journal de changements'}
                  </small>
                </div>
                <div className="m1-v-actions">
                  {!version.active && (
                    <button
                      type="button"
                      className="m1-ghost-sm"
                      onClick={() => handleActivateVersion(version.id)}
                    >
                      <Check size={14} /> Activer
                    </button>
                  )}
                  <button
                    type="button"
                    className="m1-ghost-sm"
                    onClick={() => handleViewVersion(version)}
                  >
                    <Eye size={14} /> Voir
                  </button>
                  <button
                    type="button"
                    className="m1-ghost-sm"
                    onClick={() => handleDuplicateVersion(version)}
                  >
                    <Copy size={14} /> Dupliquer
                  </button>
                </div>
              </div>
            ))}
          </div>

          {versions.length === 0 && (
            <EmptyState
              text="Aucune version enregistrée"
              subtext="Rendez-vous dans l'onglet Configuration pour enregistrer votre première version."
            />
          )}
        </div>
      )}

      {/* TAB 3: CONFIGURATION (Creates a NEW version) */}
      {activeTab === 'config' && (
        <div className="m1-tab-card">
          <form onSubmit={handleCreateVersion}>
            <div className="m1-tunable-grid">
              <div className="m1-config-full">
                <Field
                  label="Modèle"
                  placeholder="ex. gpt-4o-mini"
                  value={configModel}
                  onChange={(e) => {
                    setConfigModel(e.target.value)
                    setConfigDirty(true)
                  }}
                  error={configErrors.model}
                />
              </div>

              <div className="m1-config-full">
                <TextareaField
                  label="Prompt système"
                  placeholder="Définissez les instructions système et directives pour ce modèle..."
                  value={configPrompt}
                  onChange={(e) => {
                    setConfigPrompt(e.target.value)
                    setConfigDirty(true)
                  }}
                  error={configErrors.prompt}
                  style={{ minHeight: 140 }}
                />
              </div>

              <div className="m1-config-full">
                <Field
                  label="Journal des changements"
                  placeholder="ex. Ajustement de la température et ajout des outils FAQ"
                  value={configChangeLog}
                  onChange={(e) => {
                    setConfigChangeLog(e.target.value)
                    setConfigDirty(true)
                  }}
                  helper="Décrivez les modifications apportées à cette nouvelle version."
                />
              </div>

              <div className="m1-config-full">
                <h3 style={{ margin: '16px 0 12px', fontSize: 16, color: '#1b1b35' }}>
                  Paramètres ajustables
                </h3>
              </div>

              <div className="m1-config-full">
                <TunableParametersControls
                  params={tunableDefinitions}
                  values={configParams}
                  onChange={handleConfigParamChange}
                />
              </div>

              <div className="m1-config-full" style={{ marginTop: 12 }}>
                <label className="m1-field">
                  <span>Outils autorisés (M2)</span>
                  {toolsUnavailable ? (
                    <>
                      <Field
                        placeholder="IDs numériques séparés par virgules (ex: 1, 2, 3)"
                        value={manualToolIds}
                        onChange={(e) => {
                          setManualToolIds(e.target.value)
                          setConfigDirty(true)
                        }}
                      />
                      <div className="m1-warning">
                        Catalogue des outils indisponible (microservice M2 non joignable).
                      </div>
                    </>
                  ) : (
                    <ToolChips
                      tools={toolsCatalog}
                      selectedIds={configTools}
                      onToggle={handleToolToggle}
                      unavailable={false}
                    />
                  )}
                </label>
              </div>
            </div>

            <div style={{ marginTop: 32, display: 'flex', justifyContent: 'flex-end' }}>
              <button type="submit" className="m1-primary" disabled={saving}>
                {saving ? (
                  <>
                    <Spinner size={16} /> Enregistrement...
                  </>
                ) : (
                  'Enregistrer comme nouvelle version'
                )}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* TAB 4: TEST */}
      {activeTab === 'test' && (
        <div className="m1-tab-card">
          <div style={{ maxWidth: 720 }}>
            <SelectField
              label="Version à tester"
              value={testVersionId}
              onChange={(e) => setTestVersionId(e.target.value)}
              helper="Par défaut la version active est sélectionnée."
            >
              {versions.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.label || `Version ${v.versionNumber}`} ({v.model || 'gpt-4o-mini'})
                </option>
              ))}
            </SelectField>

            <TextareaField
              label="Votre demande"
              placeholder="Saisissez la requête à envoyer à l agent..."
              value={testInput}
              onChange={(e) => setTestInput(e.target.value)}
              required
            />

            <TextareaField
              label="Contexte (JSON optionnel)"
              placeholder='Ex: {"client_id": 12, "priorite": "haute"}'
              value={testContext}
              onChange={(e) => {
                setTestContext(e.target.value)
                setTestContextError(null)
              }}
              error={testContextError}
              helper="Doit être un objet JSON valide (omettre si non nécessaire)."
            />

            {capabilities.overrides && (
              <OverridesForm
                open={overridesOpen}
                onToggle={() => setOverridesOpen(!overridesOpen)}
                overrides={testOverrides}
                onChange={handleOverrideParamChange}
                params={tunableDefinitions}
              />
            )}

            <button
              type="button"
              className="m1-primary"
              onClick={handleTestExecute}
              disabled={testRunning || !testInput.trim()}
              style={{ marginTop: 16 }}
            >
              {testRunning ? (
                <>
                  <Spinner size={16} /> Exécution en cours...
                </>
              ) : (
                <>
                  <Play size={14} /> Exécuter
                </>
              )}
            </button>

            {(testResult || testError) && (
              <div style={{ marginTop: 24 }}>
                <ExecutionResult result={testResult} error={testError} />
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 5: HISTORIQUE */}
      {activeTab === 'history' && (
        <div>
          {!capabilities.executions ? (
            <div className="m1-empty">
              <Clock size={36} />
              <strong>Fonctionnalité en attente</strong>
              <small>
                L historique des exécutions sera disponible dès que le serveur M1 sera mis à jour.
              </small>
            </div>
          ) : (
            <div>
              <div className="m1-filters">
                <select
                  className="m1-select"
                  value={historyStatusFilter}
                  onChange={(e) => setHistoryStatusFilter(e.target.value)}
                  aria-label="Filtrer par statut"
                >
                  <option value="">Tous les statuts</option>
                  <option value="COMPLETED">COMPLETED</option>
                  <option value="FAILED">FAILED</option>
                </select>

                <select
                  className="m1-select"
                  value={historyVersionFilter}
                  onChange={(e) => setHistoryVersionFilter(e.target.value)}
                  aria-label="Filtrer par version"
                >
                  <option value="">Toutes les versions</option>
                  {versions.map((v) => (
                    <option key={v.id} value={v.id}>
                      {v.label || `Version ${v.versionNumber}`}
                    </option>
                  ))}
                </select>

                <span className="m1-counter">{historyTotal} exécution(s)</span>
              </div>

              {historyLoading ? (
                <Loading label="Chargement de l historique..." />
              ) : historyItems.length > 0 ? (
                <>
                  <ExecutionTable
                    executions={historyItems}
                    onSelect={(exec) => setSelectedExecution(exec)}
                  />

                  {historyTotal > 10 && (
                    <div className="m1-pagination">
                      <button
                        type="button"
                        className="m1-ghost-sm"
                        disabled={historyPage === 0}
                        onClick={() => setHistoryPage((p) => Math.max(0, p - 1))}
                      >
                        Précédent
                      </button>
                      <span style={{ fontSize: 13, color: 'var(--muted)' }}>
                        Page {historyPage + 1} sur {Math.ceil(historyTotal / 10)}
                      </span>
                      <button
                        type="button"
                        className="m1-ghost-sm"
                        disabled={(historyPage + 1) * 10 >= historyTotal}
                        onClick={() => setHistoryPage((p) => p + 1)}
                      >
                        Suivant
                      </button>
                    </div>
                  )}
                </>
              ) : (
                <EmptyState
                  text="Aucune exécution enregistrée pour cet agent."
                  subtext="Rendez-vous dans l'onglet Test pour lancer une première requête."
                />
              )}
            </div>
          )}

          {selectedExecution && (
            <ExecutionDrawer
              execution={selectedExecution}
              onClose={() => setSelectedExecution(null)}
            />
          )}
        </div>
      )}

      {/* Rename dialog */}
      {renameModalOpen && (
        <div className="m1-modal-backdrop" onClick={() => setRenameModalOpen(false)}>
          <div
            className="m1-dialog"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
          >
            <h2>Renommer l agent</h2>
            <div style={{ margin: '18px 0' }}>
              <Field
                label="Nouveau nom"
                value={renameValue}
                onChange={(e) => setRenameValue(e.target.value)}
                autoFocus
              />
            </div>
            <div className="m1-modal-actions">
              <button
                type="button"
                className="m1-ghost"
                onClick={() => setRenameModalOpen(false)}
              >
                Annuler
              </button>
              <button
                type="button"
                className="m1-primary"
                onClick={handleRenameConfirm}
                disabled={!renameValue.trim() || saving}
              >
                {saving ? 'Renommage...' : 'Enregistrer'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
