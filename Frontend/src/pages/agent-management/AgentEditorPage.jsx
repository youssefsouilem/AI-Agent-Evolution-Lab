import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import {
  Activity,
  AlertTriangle,
  ArrowLeft,
  Bot,
  Check,
  ChevronDown,
  Clock,
  Code2,
  Copy,
  Cpu,
  Database,
  Edit3,
  Ellipsis,
  ExternalLink,
  Eye,
  HelpCircle,
  History,
  Layers,
  Lock,
  Moon,
  Play,
  Plus,
  RefreshCw,
  RotateCcw,
  Save,
  Send,
  Sliders,
  Sparkles,
  Sun,
  Trash2,
  X,
  Zap,
} from 'lucide-react'
import {
  getAgent,
  updateAgent,
  deleteAgent,
  listVersions,
  createVersion,
  activateVersion,
  executeAgent,
  listTools,
  getTunableParameters,
  getCapabilities,
  listExecutions,
  listLlmModels,
  assistAgent,
  getAgentStats,
  adminArchiveAgent,
  adminDeleteAgent,
} from '../../services/agentApi'
import { useAuth } from '../../components/common/AuthContext'
import {
  Field,
  SelectField,
  TextareaField,
  Spinner,
  AgentStatusBadge,
  ToolChips,
  TunableParametersControls,
  OverridesForm,
  ExecutionTable,
  ExecutionDrawer,
  ErrorBanner,
  Loading,
  EmptyState,
  ConfirmDialog,
  ReasonDialog,
} from '../../components/agent-management/ui'
import '../../components/agent-management/agent-management.css'
import './agent-editor.css'

const DEFAULT_CONFIG_PARAMS = [
  { name: 'temperature', type: 'float', min: 0.0, max: 2.0, current: 0.7, description: 'Créativité' },
  { name: 'maxTokens', type: 'int', min: 256, max: 4096, current: 1024, description: 'Longueur max. (tokens)' },
  { name: 'maxTurns', type: 'int', min: 1, max: 10, current: 3, description: 'Nombre max. d’appels d’outils' },
  { name: 'timeoutSeconds', type: 'int', min: 5, max: 120, current: 30, description: 'Délai d’exécution (s)' },
  { name: 'style', type: 'categorical', choices: ['concise', 'detaille'], current: 'concise', description: 'Style de réponse' },
]

export default function AgentEditorPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { user, isAdmin, refreshProfile } = useAuth()

  // Rail active tab: 'config' | 'endpoint' | 'logs' | 'monitoring'
  const activeRailTab = searchParams.get('tab') || 'config'
  const setRailTab = (tab) => {
    setSearchParams((prev) => {
      const p = new URLSearchParams(prev)
      p.set('tab', tab)
      return p
    })
  }

  // Right panel active tab: 'BUILD' | 'PREVIEW'
  const [rightTab, setRightTab] = useState(() => (searchParams.get('action') === 'assist' ? 'BUILD' : 'BUILD'))

  // Scoped theme
  const [theme, setTheme] = useState('light')

  // Core Data
  const [agent, setAgent] = useState(null)
  const [versions, setVersions] = useState([])
  const [llmProviders, setLlmProviders] = useState([])
  const [capabilities, setCapabilities] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)
  const [feedback, setFeedback] = useState(null)

  // Agent identity state
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState('DRAFT')
  const [renameModalOpen, setRenameModalOpen] = useState(false)
  const [renameInput, setRenameInput] = useState('')

  // Configuration state for version creation / draft
  const [configModel, setConfigModel] = useState('gpt-4o-mini')
  const [configPrompt, setConfigPrompt] = useState('')
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
  const [toolsModalOpen, setToolsModalOpen] = useState(false)
  const [manualToolInput, setManualToolInput] = useState('')
  const [newVersionModalOpen, setNewVersionModalOpen] = useState(false)
  const [newVersionChangelog, setNewVersionChangelog] = useState('')

  // Popover & Dropdown states
  const [modelDropdownOpen, setModelDropdownOpen] = useState(false)
  const [modelSearch, setModelSearch] = useState('')
  const [popoverOpen, setPopoverOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)

  // Admin and action dialogs
  const [deleteConfirm, setDeleteConfirm] = useState(false)
  const [archiveConfirm, setArchiveConfirm] = useState(false)

  // Right Tab 1: Assistant (CONSTRUIRE) state
  const [assistInput, setAssistInput] = useState('')
  const [assistLoading, setAssistLoading] = useState(false)
  const [assistMessages, setAssistMessages] = useState([
    {
      role: 'assistant',
      text: "Bonjour ! Décrivez-moi le rôle ou la tâche de cet agent, et je vous proposerai un nom, des instructions système et les paramètres optimaux.",
    },
  ])
  const [pendingProposal, setPendingProposal] = useState(null)
  const [assistError, setAssistError] = useState(null)

  // Right Tab 2: Test (APERÇU) state
  const [testVersionId, setTestVersionId] = useState('')
  const [testInput, setTestInput] = useState('')
  const [testContext] = useState('')
  const [overridesOpen, setOverridesOpen] = useState(false)
  const [testOverrides, setTestOverrides] = useState({})
  const [testRunning, setTestRunning] = useState(false)
  const [chatMessages, setChatMessages] = useState([])
  const [testError, setTestError] = useState(null)

  // Zone 2 - Logs (Journaux) state
  const [logs, setLogs] = useState([])
  const [logsTotal, setLogsTotal] = useState(0)
  const [logsLoading, setLogsLoading] = useState(false)
  const [logsStatusFilter, setLogsStatusFilter] = useState('')
  const [logsVersionFilter, setLogsVersionFilter] = useState('')
  const [selectedExecution, setSelectedExecution] = useState(null)

  // Zone 2 - Monitoring state
  const [statsDays, setStatsDays] = useState(7)
  const [statsData, setStatsData] = useState(null)
  const [statsLoading, setStatsLoading] = useState(false)

  // Copied curl state
  const [curlCopied, setCurlCopied] = useState(false)

  // Admin Read-only status
  const isReadOnly = useMemo(() => {
    return Boolean(isAdmin && agent?.ownerId && agent.ownerId !== user?.id)
  }, [isAdmin, agent?.ownerId, user?.id])

  // Load capabilities & LLM models on mount
  useEffect(() => {
    getCapabilities().then(setCapabilities).catch(() => {})
    listLlmModels()
      .then((data) => setLlmProviders(Array.isArray(data) ? data : []))
      .catch(() => {})
  }, [])

  // Load tools catalogue
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

  // Load Agent and versions
  const loadAgent = useCallback(async () => {
    setLoading(true)
    try {
      const agentData = await getAgent(id)
      setAgent(agentData)
      setName(agentData.name || '')
      setRenameInput(agentData.name || '')
      setDescription(agentData.description || '')
      setStatus(agentData.status || 'DRAFT')

      const versionsData = await listVersions(id)
      const sortedVersions = Array.isArray(versionsData)
        ? [...versionsData].sort((a, b) => b.versionNumber - a.versionNumber)
        : []
      setVersions(sortedVersions)

      const activeVer = sortedVersions.find((v) => v.active) || sortedVersions[0]
      if (activeVer) {
        setTestVersionId(String(activeVer.id))
        const cfg = activeVer.configuration || {}
        setConfigModel(activeVer.model || 'gpt-4o-mini')
        setConfigPrompt(activeVer.systemPrompt || '')
        setConfigParams({
          temperature: cfg.temperature ?? 0.7,
          maxTokens: cfg.maxTokens ?? 1024,
          maxTurns: cfg.maxTurns ?? 3,
          timeoutSeconds: cfg.timeoutSeconds ?? 30,
          style: cfg.style || 'concise',
        })
        setConfigTools(Array.isArray(cfg.allowedToolIds) ? cfg.allowedToolIds : [])

        try {
          const tunableList = await getTunableParameters(id, activeVer.id)
          if (Array.isArray(tunableList) && tunableList.length > 0) {
            setTunableDefinitions(tunableList)
          }
        } catch {
          // ignore
        }
      }

      setError(null)
    } catch (err) {
      setError(err)
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    loadAgent()
  }, [loadAgent])

  // Load Logs
  const loadLogs = useCallback(async () => {
    setLogsLoading(true)
    try {
      const res = await listExecutions(id, {
        status: logsStatusFilter || undefined,
        versionId: logsVersionFilter || undefined,
        page: 0,
        size: 50,
      })
      setLogs(res.items || [])
      setLogsTotal(res.totalCount || 0)
    } catch {
      // ignore
    } finally {
      setLogsLoading(false)
    }
  }, [id, logsStatusFilter, logsVersionFilter])

  useEffect(() => {
    if (activeRailTab === 'logs') {
      loadLogs()
    }
  }, [activeRailTab, loadLogs])

  // Load Monitoring Stats
  const loadMonitoringStats = useCallback(async () => {
    setStatsLoading(true)
    try {
      const data = await getAgentStats(id, statsDays)
      setStatsData(data)
    } catch {
      // ignore
    } finally {
      setStatsLoading(false)
    }
  }, [id, statsDays])

  useEffect(() => {
    if (activeRailTab === 'monitoring') {
      loadMonitoringStats()
    }
  }, [activeRailTab, loadMonitoringStats])

  // Save basic Agent identity
  const handleSaveIdentity = async (newName, newDesc, newStatus) => {
    if (isReadOnly) return
    setSaving(true)
    try {
      const updated = await updateAgent(id, {
        name: newName ?? name,
        description: newDesc ?? description,
        status: newStatus ?? status,
      })
      setAgent(updated)
      setName(updated.name)
      setDescription(updated.description)
      setStatus(updated.status)
      setFeedback('Agent mis à jour.')
      setTimeout(() => setFeedback(null), 3000)
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  // Create immutable version
  const handleCreateVersion = async (e) => {
    e.preventDefault()
    if (isReadOnly) return
    setSaving(true)
    try {
      const newVer = await createVersion(id, {
        model: configModel,
        systemPrompt: configPrompt,
        configuration: {
          ...configParams,
          allowedToolIds: configTools,
        },
        changeLog: newVersionChangelog.trim() || undefined,
      })
      setNewVersionModalOpen(false)
      setNewVersionChangelog('')
      setFeedback(`Version V${newVer.versionNumber} créée et activée avec succès.`)
      setTimeout(() => setFeedback(null), 3000)
      await loadAgent()
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  // Activate Version
  const handleActivateVersion = async (versionId) => {
    if (isReadOnly) return
    try {
      await activateVersion(id, versionId)
      setFeedback('Version activée.')
      setTimeout(() => setFeedback(null), 3000)
      await loadAgent()
    } catch (err) {
      setError(err)
    }
  }

  // Duplicate Version into editor configuration
  const handleDuplicateVersionToConfig = (ver) => {
    setConfigModel(ver.model || 'gpt-4o-mini')
    setConfigPrompt(ver.systemPrompt || '')
    const cfg = ver.configuration || {}
    setConfigParams({
      temperature: cfg.temperature ?? 0.7,
      maxTokens: cfg.maxTokens ?? 1024,
      maxTurns: cfg.maxTurns ?? 3,
      timeoutSeconds: cfg.timeoutSeconds ?? 30,
      style: cfg.style || 'concise',
    })
    setConfigTools(Array.isArray(cfg.allowedToolIds) ? cfg.allowedToolIds : [])
    setFeedback(`Configuration de V${ver.versionNumber} chargée dans l'éditeur.`)
    setTimeout(() => setFeedback(null), 3000)
  }

  // Duplication of entire agent
  const handleDuplicateAgent = async () => {
    try {
      setFeedback('Duplication en cours...')
      navigate('/agents/new', { state: { initialName: `${name} (Copie)`, initialDesc: description } })
    } catch (err) {
      setError(err)
    }
  }

  // Export JSON
  const handleExportJson = () => {
    const exportData = {
      agent,
      versions,
      exportedAt: new Date().toISOString(),
    }
    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `agent-${agent.name.toLowerCase().replace(/[^a-z0-9]/g, '-')}.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  // Archive Agent
  const handleArchive = async (reason) => {
    try {
      if (isReadOnly) {
        await adminArchiveAgent(id, reason || "Archivage administratif")
      } else {
        await updateAgent(id, { name, description, status: 'ARCHIVED' })
      }
      setArchiveConfirm(false)
      navigate('/agents', { state: { flashMessage: `Agent "${name}" archivé.` } })
    } catch (err) {
      setError(err)
    }
  }

  // Delete Agent
  const handleDelete = async (reason) => {
    try {
      if (isReadOnly) {
        await adminDeleteAgent(id, reason || "Suppression administrative")
      } else {
        await deleteAgent(id)
      }
      setDeleteConfirm(false)
      refreshProfile()
      navigate('/agents', { state: { flashMessage: `Agent "${name}" supprimé.` } })
    } catch (err) {
      setError(err)
    }
  }

  // Conversational Assistant (CONSTRUIRE)
  const handleSendAssist = async (e) => {
    e.preventDefault()
    if (!assistInput.trim() || assistLoading || isReadOnly) return

    const userText = assistInput.trim()
    setAssistInput('')
    setAssistMessages((prev) => [...prev, { role: 'user', text: userText }])
    setAssistLoading(true)
    setAssistError(null)

    try {
      const proposal = await assistAgent({ description: userText, language: 'fr' })
      setPendingProposal(proposal)
      setAssistMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          text: `Voici ma proposition pour votre agent "${proposal.name}" :`,
          proposal,
        },
      ])
    } catch (err) {
      setAssistError(err)
      setAssistMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          text: `Désolé, une erreur est survenue lors de l'assistance : ${err.message || 'Impossible de générer la proposition.'}`,
        },
      ])
    } finally {
      setAssistLoading(false)
    }
  }

  // Apply proposal to form
  const handleApplyProposal = (proposal) => {
    if (!proposal || isReadOnly) return
    if (proposal.name) {
      setName(proposal.name)
      setRenameInput(proposal.name)
    }
    if (proposal.description) setDescription(proposal.description)
    if (proposal.systemPrompt) setConfigPrompt(proposal.systemPrompt)
    if (proposal.model) setConfigModel(proposal.model)
    setConfigParams((prev) => ({
      ...prev,
      temperature: proposal.temperature ?? prev.temperature,
      maxTokens: proposal.maxTokens ?? prev.maxTokens,
      maxTurns: proposal.maxTurns ?? prev.maxTurns,
      timeoutSeconds: proposal.timeoutSeconds ?? prev.timeoutSeconds,
      style: proposal.style ?? prev.style,
    }))
    setFeedback('Proposition appliquée aux champs de configuration.')
    setTimeout(() => setFeedback(null), 3000)
  }

  // Interactive Test (APERÇU)
  const handleExecuteTest = async (e) => {
    e.preventDefault()
    if (!testInput.trim() || testRunning || isReadOnly) return

    const inputMsg = testInput.trim()
    setTestInput('')
    setTestError(null)

    const userTurn = { id: Date.now(), role: 'user', content: inputMsg }
    setChatMessages((prev) => [...prev, userTurn])
    setTestRunning(true)

    let parsedContext = {}
    if (testContext.trim()) {
      try {
        parsedContext = JSON.parse(testContext)
      } catch {
        parsedContext = { raw: testContext }
      }
    }

    try {
      const res = await executeAgent({
        agentId: id,
        versionId: testVersionId ? parseInt(testVersionId, 10) : undefined,
        input: inputMsg,
        context: parsedContext,
        overrides: Object.keys(testOverrides).length > 0 ? testOverrides : undefined,
      })

      const botTurn = {
        id: Date.now() + 1,
        role: 'assistant',
        content: res.output,
        status: res.status,
        durationMs: res.durationMs,
        tokensUsed: res.tokensUsed,
        toolCallCount: res.toolCallCount,
      }
      setChatMessages((prev) => [...prev, botTurn])
    } catch (err) {
      setTestError(err)
      const errTurn = {
        id: Date.now() + 1,
        role: 'assistant',
        content: err.message || "Erreur d'exécution",
        status: 'FAILED',
        errorCode: err.errorCode,
        durationMs: err.execution?.durationMs,
        tokensUsed: err.execution?.totalTokens,
      }
      setChatMessages((prev) => [...prev, errTurn])
    } finally {
      setTestRunning(false)
    }
  }

  // Flattened available models
  const allModels = useMemo(() => {
    const list = []
    llmProviders.forEach((prov) => {
      ;(prov.models || []).forEach((m) => {
        list.push({ ...m, provider: prov.label || prov.provider, available: prov.available })
      })
    })
    return list
  }, [llmProviders])

  const filteredModels = useMemo(() => {
    const q = modelSearch.toLowerCase()
    return allModels.filter(
      (m) => m.id.toLowerCase().includes(q) || (m.label && m.label.toLowerCase().includes(q))
    )
  }, [allModels, modelSearch])

  // Copy Curl command
  const curlCommand = `curl -X POST "http://localhost:8081/api/agents/${id}/execute" \\
  -H "Content-Type: application/json" \\
  -d '{
    "input": "Bonjour, comment peux-tu m\\'aider ?",
    "context": {}
  }'`

  const handleCopyCurl = () => {
    navigator.clipboard.writeText(curlCommand)
    setCurlCopied(true)
    setTimeout(() => setCurlCopied(false), 2000)
  }

  if (loading) {
    return (
      <div style={{ padding: 40 }}>
        <Loading label="Chargement de l'agent..." />
      </div>
    )
  }

  if (error && !agent) {
    return (
      <div style={{ padding: 30 }}>
        <ErrorBanner error={error} retry={loadAgent} />
        <button type="button" className="m1-ghost" onClick={() => navigate('/agents')} style={{ marginTop: 14 }}>
          <ArrowLeft size={14} /> Retour à la liste
        </button>
      </div>
    )
  }

  return (
    <div className={`m1-builder-root ${theme === 'dark' ? 'm1-editor-dark' : 'm1-editor-light'}`}>
      {/* Admin Read-only Banner */}
      {isReadOnly && (
        <div className="m1-readonly-banner">
          <div>
            <Lock size={15} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            <strong>Mode lecture seule :</strong> Vous visualisez l'agent de l'utilisateur "{agent.ownerId}". Les modifications sont verrouillées.
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              type="button"
              className="m1-ghost-sm"
              style={{ color: '#b45309', borderColor: '#fde68a' }}
              onClick={() => setArchiveConfirm(true)}
            >
              Archiver (Admin)
            </button>
            <button
              type="button"
              className="m1-ghost-sm"
              style={{ color: '#dc2626', borderColor: '#fecaca' }}
              onClick={() => setDeleteConfirm(true)}
            >
              Supprimer (Admin)
            </button>
          </div>
        </div>
      )}

      {/* Global Feedback notification */}
      {feedback && (
        <div
          style={{
            position: 'fixed',
            bottom: 24,
            right: 24,
            background: 'var(--purple)',
            color: 'white',
            padding: '10px 18px',
            borderRadius: 8,
            boxShadow: '0 8px 24px rgba(0,0,0,0.2)',
            zIndex: 99,
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            fontSize: 13,
            fontWeight: 600,
          }}
        >
          <Check size={16} /> {feedback}
        </div>
      )}

      <div className="m1-builder-container">
        {/* ==================================================================== */}
        {/* ZONE 1: LEFT RAIL (~220px)                                           */}
        {/* ==================================================================== */}
        <aside className="m1-rail">
          {/* Back link */}
          <button type="button" className="m1-rail-back" onClick={() => navigate('/agents')}>
            <ArrowLeft size={14} /> Mes agents
          </button>

          {/* Agent Header & Menu */}
          <div className="m1-rail-header">
            <div className="m1-rail-avatar">
              <Bot size={20} />
            </div>
            <div className="m1-rail-agent-name" title={name}>
              <strong onClick={() => !isReadOnly && setRenameModalOpen(true)}>{name}</strong>
              <small>{agent.ownerId || 'Mon agent'}</small>
            </div>
            <div style={{ position: 'relative' }}>
              <button
                type="button"
                className="m1-icon-button"
                onClick={() => setMenuOpen(!menuOpen)}
                aria-label="Actions de l'agent"
              >
                <Ellipsis size={18} />
              </button>

              {menuOpen && (
                <div className="m1-menu-dropdown" onMouseLeave={() => setMenuOpen(false)}>
                  {!isReadOnly && (
                    <button
                      type="button"
                      onClick={() => {
                        setRenameModalOpen(true)
                        setMenuOpen(false)
                      }}
                    >
                      <Edit3 size={14} /> Renommer
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => {
                      handleDuplicateAgent()
                      setMenuOpen(false)
                    }}
                  >
                    <Copy size={14} /> Dupliquer
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      handleExportJson()
                      setMenuOpen(false)
                    }}
                  >
                    <ExternalLink size={14} /> Exporter en JSON
                  </button>
                  {agent.status !== 'ARCHIVED' && (
                    <button
                      type="button"
                      onClick={() => {
                        setArchiveConfirm(true)
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
                      setDeleteConfirm(true)
                      setMenuOpen(false)
                    }}
                  >
                    <Trash2 size={14} /> Supprimer
                  </button>
                </div>
              )}
            </div>
          </div>

          {/* Navigation rail items */}
          <nav className="m1-rail-nav">
            <button
              type="button"
              className={`m1-rail-btn ${activeRailTab === 'config' ? 'active' : ''}`}
              onClick={() => setRailTab('config')}
            >
              <Sliders size={16} />
              <span>Configurer</span>
            </button>

            <button
              type="button"
              className={`m1-rail-btn ${activeRailTab === 'endpoint' ? 'active' : ''}`}
              onClick={() => setRailTab('endpoint')}
            >
              <Code2 size={16} />
              <span>Point d'accès</span>
            </button>

            <button
              type="button"
              className={`m1-rail-btn ${activeRailTab === 'logs' ? 'active' : ''}`}
              onClick={() => setRailTab('logs')}
            >
              <History size={16} />
              <span>Journaux</span>
            </button>

            <button
              type="button"
              className={`m1-rail-btn ${activeRailTab === 'monitoring' ? 'active' : ''}`}
              onClick={() => setRailTab('monitoring')}
            >
              <Activity size={16} />
              <span>Monitoring</span>
            </button>
          </nav>

          {/* Rail footer with theme switcher */}
          <div className="m1-rail-footer">
            <div className="m1-theme-toggle">
              <button
                type="button"
                className={`m1-theme-btn ${theme === 'light' ? 'active' : ''}`}
                onClick={() => setTheme('light')}
              >
                <Sun size={12} /> Clair
              </button>
              <button
                type="button"
                className={`m1-theme-btn ${theme === 'dark' ? 'active' : ''}`}
                onClick={() => setTheme('dark')}
              >
                <Moon size={12} /> Sombre
              </button>
            </div>

            <div style={{ fontSize: 11, color: 'var(--muted)', textAlign: 'center' }}>
              {versions.length} version(s) • {agent.status}
            </div>
          </div>
        </aside>

        {/* ==================================================================== */}
        {/* ZONE 2: CENTER PANEL                                                 */}
        {/* ==================================================================== */}
        <section className="m1-center">
          {/* Top Sticky Bar */}
          <header className="m1-top-bar">
            <div className="m1-model-bar">
              {/* Model Selector Dropdown Button */}
              <button
                type="button"
                className="m1-model-btn"
                onClick={() => setModelDropdownOpen(!modelDropdownOpen)}
                disabled={isReadOnly}
              >
                <Cpu size={16} style={{ color: 'var(--purple)' }} />
                <span>{configModel}</span>
                <ChevronDown size={14} />
              </button>

              {/* Model Dropdown Menu */}
              {modelDropdownOpen && (
                <div className="m1-model-dropdown" onMouseLeave={() => setModelDropdownOpen(false)}>
                  <input
                    type="search"
                    placeholder="Filtrer les modèles..."
                    className="m1-model-search"
                    value={modelSearch}
                    onChange={(e) => setModelSearch(e.target.value)}
                    autoFocus
                  />
                  <div style={{ maxHeight: 220, overflowY: 'auto' }}>
                    {filteredModels.length === 0 ? (
                      <div style={{ padding: 10, fontSize: 12, color: 'var(--muted)' }}>
                        Aucun modèle trouvé
                      </div>
                    ) : (
                      filteredModels.map((m) => (
                        <button
                          key={m.id}
                          type="button"
                          className={`m1-model-item ${configModel === m.id ? 'selected' : ''}`}
                          onClick={() => {
                            setConfigModel(m.id)
                            setModelDropdownOpen(false)
                          }}
                        >
                          <div>
                            <strong>{m.label || m.id}</strong>
                            <div style={{ fontSize: 10, color: 'var(--muted)' }}>{m.provider}</div>
                          </div>
                          {m.recommended && (
                            <span
                              style={{
                                background: '#ede8ff',
                                color: 'var(--purple)',
                                padding: '2px 5px',
                                borderRadius: 4,
                                fontSize: 10,
                                fontWeight: 700,
                              }}
                            >
                              ★
                            </span>
                          )}
                        </button>
                      ))
                    )}
                  </div>
                </div>
              )}

              {/* Parameters Popover Toggle */}
              <button
                type="button"
                className={`m1-popover-btn ${popoverOpen ? 'active' : ''}`}
                onClick={() => setPopoverOpen(!popoverOpen)}
                title="Paramètres d'inférence (température, tokens...)"
              >
                <Sliders size={16} />
              </button>

              {/* Popover */}
              {popoverOpen && (
                <div className="m1-popover" onMouseLeave={() => setPopoverOpen(false)}>
                  <div className="m1-popover-title">
                    <span>Paramètres du modèle</span>
                    <button
                      type="button"
                      className="m1-icon-button"
                      onClick={() => setPopoverOpen(false)}
                    >
                      <X size={14} />
                    </button>
                  </div>
                  <TunableParametersControls
                    params={tunableDefinitions}
                    values={configParams}
                    onChange={(paramName, val) =>
                      !isReadOnly && setConfigParams((prev) => ({ ...prev, [paramName]: val }))
                    }
                  />
                </div>
              )}
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <AgentStatusBadge status={agent.status} />

              {!isReadOnly && (
                <button
                  type="button"
                  className="m1-primary"
                  onClick={() => setNewVersionModalOpen(true)}
                  disabled={saving}
                >
                  <Save size={14} />
                  Enregistrer une version
                </button>
              )}
            </div>
          </header>

          {/* Center Dynamic Content */}
          <div className="m1-center-content">
            {/* ---------------------------------------------------- */}
            {/* TAB: CONFIGURER                                      */}
            {/* ---------------------------------------------------- */}
            {activeRailTab === 'config' && (
              <>
                {/* Description Card */}
                <div className="m1-instructions-card">
                  <div className="m1-instructions-header">
                    <strong>Description de l'agent</strong>
                    <button
                      type="button"
                      className="m1-ghost-sm"
                      onClick={() => handleSaveIdentity(name, description, status)}
                      disabled={isReadOnly || saving}
                    >
                      Enregistrer
                    </button>
                  </div>
                  <textarea
                    className="m1-instructions-textarea"
                    style={{ minHeight: 70 }}
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="Description du rôle ou de l'objectif de l'agent..."
                    disabled={isReadOnly}
                  />
                </div>

                {/* Instructions / System Prompt */}
                <div className="m1-instructions-card">
                  <div className="m1-instructions-header">
                    <strong>Instructions système (System Prompt)</strong>
                    <span className="m1-char-count">{configPrompt.length} / 8 000 caractères</span>
                  </div>
                  <textarea
                    className="m1-instructions-textarea"
                    placeholder="Vous êtes un assistant IA expert. Définissez ici les consignes, règles et contraintes que l'agent doit scrupuleusement suivre..."
                    value={configPrompt}
                    onChange={(e) => setConfigPrompt(e.target.value)}
                    disabled={isReadOnly}
                  />
                </div>

                {/* Tools Section */}
                <div className="m1-instructions-card">
                  <div className="m1-instructions-header">
                    <div>
                      <strong>Outils MCP autorisés</strong>
                      <div style={{ fontSize: 12, color: 'var(--muted)' }}>
                        Outils externes appelables par l'agent lors de ses exécutions.
                      </div>
                    </div>
                    {!isReadOnly && (
                      <button
                        type="button"
                        className="m1-ghost-sm"
                        onClick={() => setToolsModalOpen(true)}
                      >
                        <Plus size={13} /> Gérer les outils ({configTools.length})
                      </button>
                    )}
                  </div>

                  {configTools.length === 0 ? (
                    <div style={{ fontSize: 13, color: 'var(--muted)', fontStyle: 'italic', padding: '8px 0' }}>
                      Aucun outil configuré. L'agent répondra en texte seul sans appel d'outil externe.
                    </div>
                  ) : (
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 8 }}>
                      {configTools.map((tId) => (
                        <span
                          key={tId}
                          style={{
                            background: 'var(--lavender)',
                            color: 'var(--purple)',
                            padding: '4px 10px',
                            borderRadius: 16,
                            fontSize: 12,
                            fontWeight: 600,
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 6,
                          }}
                        >
                          {tId}
                          {!isReadOnly && (
                            <button
                              type="button"
                              onClick={() => setConfigTools((prev) => prev.filter((id) => id !== tId))}
                              style={{ border: 0, background: 'none', cursor: 'pointer', color: 'var(--purple)' }}
                            >
                              <X size={12} />
                            </button>
                          )}
                        </span>
                      ))}
                    </div>
                  )}
                </div>

                {/* Immutable Versions List */}
                <div className="m1-instructions-card">
                  <div className="m1-instructions-header">
                    <div>
                      <strong>Historique des versions ({versions.length})</strong>
                      <div style={{ fontSize: 12, color: 'var(--muted)' }}>
                        Chaque version enregistrée est immuable et peut être activée à tout moment.
                      </div>
                    </div>
                    {!isReadOnly && (
                      <button
                        type="button"
                        className="m1-ghost-sm"
                        onClick={() => setNewVersionModalOpen(true)}
                      >
                        <Plus size={13} /> Nouvelle version
                      </button>
                    )}
                  </div>

                  <div className="m1-version-list" style={{ marginTop: 12 }}>
                    {versions.map((ver) => (
                      <div key={ver.id} className="m1-version-item">
                        <div className="m1-v-icon">
                          <Bot size={18} />
                        </div>
                        <div className="m1-v-info">
                          <div className="m1-v-badges">
                            {ver.active && (
                              <span className="m1-v-badge m1-v-badge-active">
                                <Check size={10} style={{ marginRight: 3 }} /> Active
                              </span>
                            )}
                            {ver.optimizedByM5 && (
                              <span className="m1-v-badge m1-v-badge-m5">
                                <Sparkles size={10} style={{ marginRight: 3 }} /> Optimisé M5
                              </span>
                            )}
                          </div>
                          <strong>
                            Version V{ver.versionNumber} ({ver.model || 'gpt-4o-mini'})
                          </strong>
                          <small>
                            Créée le {ver.createdAt ? new Date(ver.createdAt).toLocaleDateString('fr-FR') : '—'}
                            {ver.changeLog ? ` • ${ver.changeLog}` : ''}
                          </small>
                        </div>

                        <div className="m1-v-actions">
                          <button
                            type="button"
                            className="m1-ghost-sm"
                            onClick={() => handleDuplicateVersionToConfig(ver)}
                            title="Charger les paramètres de cette version dans l'éditeur"
                          >
                            <Copy size={12} /> Charger
                          </button>

                          {!ver.active && !isReadOnly && (
                            <button
                              type="button"
                              className="m1-primary"
                              style={{ padding: '5px 10px', fontSize: 12 }}
                              onClick={() => handleActivateVersion(ver.id)}
                            >
                              Activer
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </>
            )}

            {/* ---------------------------------------------------- */}
            {/* TAB: POINT D'ACCÈS                                   */}
            {/* ---------------------------------------------------- */}
            {activeRailTab === 'endpoint' && (
              <div className="m1-instructions-card">
                <div className="m1-instructions-header">
                  <strong>Intégration API REST</strong>
                  <button type="button" className="m1-ghost-sm" onClick={handleCopyCurl}>
                    {curlCopied ? <Check size={13} style={{ color: '#059669' }} /> : <Copy size={13} />}
                    {curlCopied ? 'Copié !' : 'Copier cURL'}
                  </button>
                </div>

                <p style={{ fontSize: 13, color: 'var(--muted)', marginTop: 4 }}>
                  Invoquez cet agent par programmation depuis vos applications ou scripts de test via l'API REST.
                </p>

                <div style={{ marginTop: 16 }}>
                  <span className="m1-drawer-label">Commande cURL</span>
                  <pre className="m1-code-block">{curlCommand}</pre>
                </div>

                <div style={{ marginTop: 20 }}>
                  <span className="m1-drawer-label">Exemple de corps de réponse (200 OK)</span>
                  <pre className="m1-code-block">
{`{
  "executionId": 42,
  "status": "COMPLETED",
  "output": "Voici le résultat de l'exécution de l'agent...",
  "durationMs": 340,
  "tokensUsed": 185,
  "toolCallCount": 0,
  "appliedSettings": {
    "temperature": 0.7,
    "maxTokens": 1024,
    "style": "concise"
  }
}`}
                  </pre>
                </div>

                <div style={{ marginTop: 20 }}>
                  <span className="m1-drawer-label">Codes de statut HTTP</span>
                  <table className="m1-table" style={{ marginTop: 8 }}>
                    <thead>
                      <tr>
                        <th>Code</th>
                        <th>Signification</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td><code>200 OK</code></td>
                        <td>Exécution effectuée avec succès (statut COMPLETED ou FAILED dans le corps).</td>
                      </tr>
                      <tr>
                        <td><code>400 Bad Request</code></td>
                        <td>Entrée (input) manquante ou invalide.</td>
                      </tr>
                      <tr>
                        <td><code>404 Not Found</code></td>
                        <td>Agent ou version spécifiée introuvable.</td>
                      </tr>
                      <tr>
                        <td><code>422 Unprocessable</code></td>
                        <td>Le contexte fourni n'est pas un objet JSON valide.</td>
                      </tr>
                      <tr>
                        <td><code>502 Bad Gateway</code></td>
                        <td>Erreur du fournisseur de modèle de langage ou du service d'outils.</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {/* ---------------------------------------------------- */}
            {/* TAB: JOURNAUX (LOGS)                                 */}
            {/* ---------------------------------------------------- */}
            {activeRailTab === 'logs' && (
              <div>
                <div style={{ display: 'flex', gap: 12, marginBottom: 16, alignItems: 'center' }}>
                  <select
                    className="m1-select"
                    value={logsStatusFilter}
                    onChange={(e) => setLogsStatusFilter(e.target.value)}
                  >
                    <option value="">Tous les statuts</option>
                    <option value="COMPLETED">COMPLETED (Succès)</option>
                    <option value="FAILED">FAILED (Échec)</option>
                  </select>

                  <select
                    className="m1-select"
                    value={logsVersionFilter}
                    onChange={(e) => setLogsVersionFilter(e.target.value)}
                  >
                    <option value="">Toutes les versions</option>
                    {versions.map((v) => (
                      <option key={v.id} value={v.id}>
                        V{v.versionNumber}
                      </option>
                    ))}
                  </select>

                  <button
                    type="button"
                    className="m1-ghost"
                    onClick={loadLogs}
                    style={{ marginLeft: 'auto' }}
                  >
                    <RefreshCw size={14} className={logsLoading ? 'animate-spin' : ''} />
                    Actualiser ({logsTotal})
                  </button>
                </div>

                {logsLoading ? (
                  <Loading label="Chargement des journaux..." />
                ) : logs.length === 0 ? (
                  <EmptyState
                    text="Aucun journal d'exécution pour cet agent."
                    subtext="Lancez un test depuis le panneau Aperçu à droite pour générer des exécutions."
                  />
                ) : (
                  <ExecutionTable executions={logs} onSelect={setSelectedExecution} />
                )}
              </div>
            )}

            {/* ---------------------------------------------------- */}
            {/* TAB: MONITORING                                      */}
            {/* ---------------------------------------------------- */}
            {activeRailTab === 'monitoring' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
                {/* Period selection */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div className="m1-segmented-control">
                    <button
                      type="button"
                      className={`m1-segment ${statsDays === 7 ? 'active' : ''}`}
                      onClick={() => setStatsDays(7)}
                    >
                      7 derniers jours
                    </button>
                    <button
                      type="button"
                      className={`m1-segment ${statsDays === 30 ? 'active' : ''}`}
                      onClick={() => setStatsDays(30)}
                    >
                      30 derniers jours
                    </button>
                  </div>

                  <button type="button" className="m1-ghost" onClick={loadMonitoringStats}>
                    <RefreshCw size={13} className={statsLoading ? 'animate-spin' : ''} /> Actualiser
                  </button>
                </div>

                {/* Metrics Cards */}
                <div className="m1-stats">
                  <div className="m1-stat">
                    <span>Exécutions totales</span>
                    <strong>{statsData?.total ?? 0}</strong>
                    <small>{statsDays} derniers jours</small>
                  </div>
                  <div className="m1-stat">
                    <span>Taux de réussite</span>
                    <strong>{statsData?.successRate ? `${Math.round(statsData.successRate)}%` : '—'}</strong>
                    <small>{statsData?.completed ?? 0} réussies / {statsData?.failed ?? 0} échouées</small>
                  </div>
                  <div className="m1-stat">
                    <span>Durée moyenne</span>
                    <strong>{statsData?.avgDurationMs ? `${Math.round(statsData.avgDurationMs)} ms` : '—'}</strong>
                    <small>P95 : {statsData?.p95DurationMs ? `${Math.round(statsData.p95DurationMs)} ms` : '—'}</small>
                  </div>
                </div>

                {/* SVG Daily Chart */}
                <div className="m1-chart-container">
                  <div className="m1-chart-header">
                    <strong>Activité journalière des exécutions</strong>
                    <small style={{ color: 'var(--muted)', fontSize: 12 }}>
                      <span style={{ color: 'var(--purple)', fontWeight: 700 }}>■</span> Succès &nbsp;
                      <span style={{ color: '#dc2626', fontWeight: 700 }}>■</span> Échecs
                    </small>
                  </div>

                  {statsData?.byDay && statsData.byDay.length > 0 ? (
                    <div style={{ width: '100%', height: 180, display: 'flex', alignItems: 'flex-end', gap: 12, paddingBottom: 20 }}>
                      {statsData.byDay.map((day) => {
                        const totalDay = day.total || 0
                        const failedDay = day.failed || 0
                        const completedDay = Math.max(0, totalDay - failedDay)
                        const maxH = Math.max(1, ...statsData.byDay.map((d) => d.total || 0))
                        const compH = (completedDay / maxH) * 120
                        const failH = (failedDay / maxH) * 120

                        return (
                          <div
                            key={day.date}
                            style={{
                              flex: 1,
                              display: 'flex',
                              flexDirection: 'column',
                              alignItems: 'center',
                              gap: 4,
                            }}
                            title={`${day.date}: ${totalDay} exec (${completedDay} succès, ${failedDay} échecs)`}
                          >
                            <div style={{ display: 'flex', alignItems: 'flex-end', gap: 2, height: 130 }}>
                              <div
                                style={{
                                  width: 14,
                                  height: Math.max(4, compH),
                                  background: 'var(--purple)',
                                  borderRadius: '3px 3px 0 0',
                                }}
                              />
                              {failedDay > 0 && (
                                <div
                                  style={{
                                    width: 14,
                                    height: Math.max(4, failH),
                                    background: '#dc2626',
                                    borderRadius: '3px 3px 0 0',
                                  }}
                                />
                              )}
                            </div>
                            <span style={{ fontSize: 10, color: 'var(--muted)' }}>
                              {day.date.slice(5)}
                            </span>
                          </div>
                        )
                      })}
                    </div>
                  ) : (
                    <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--muted)', fontSize: 13 }}>
                      Aucune donnée d'exécution enregistrée sur cette période.
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </section>

        {/* ==================================================================== */}
        {/* ZONE 3: RIGHT PANEL (CONSTRUIRE / APERÇU)                            */}
        {/* ==================================================================== */}
        <aside className="m1-right">
          {/* Header Dual Tabs */}
          <div className="m1-right-tabs">
            <button
              type="button"
              className={`m1-right-tab ${rightTab === 'BUILD' ? 'active' : ''}`}
              onClick={() => setRightTab('BUILD')}
            >
              <Sparkles size={13} style={{ verticalAlign: 'middle', marginRight: 5 }} />
              CONSTRUIRE
            </button>
            <button
              type="button"
              className={`m1-right-tab ${rightTab === 'PREVIEW' ? 'active' : ''}`}
              onClick={() => setRightTab('PREVIEW')}
            >
              <Play size={13} style={{ verticalAlign: 'middle', marginRight: 5 }} />
              APERÇU
            </button>
          </div>

          <div className="m1-right-body">
            {/* ---------------------------------------------------- */}
            {/* TAB: CONSTRUIRE (Assistant Agent)                    */}
            {/* ---------------------------------------------------- */}
            {rightTab === 'BUILD' && (
              <div className="m1-assist-container">
                <div className="m1-assist-feed">
                  {assistMessages.map((msg, i) => (
                    <div
                      key={i}
                      className={`m1-assist-bubble ${msg.role === 'user' ? 'm1-assist-user' : 'm1-assist-bot'}`}
                    >
                      <div>{msg.text}</div>

                      {msg.proposal && (
                        <div className="m1-proposal-card">
                          <div className="m1-proposal-header">
                            <Sparkles size={14} /> Proposition générée par l'IA
                          </div>
                          <div className="m1-proposal-field">
                            <strong>Nom suggéré :</strong> {msg.proposal.name}
                          </div>
                          <div className="m1-proposal-field">
                            <strong>Description :</strong> {msg.proposal.description}
                          </div>
                          <div className="m1-proposal-field">
                            <strong>Modèle recommandé :</strong> {msg.proposal.model || 'gpt-4o-mini'}
                          </div>
                          <div className="m1-proposal-field">
                            <strong>Instructions :</strong>
                            <p style={{ margin: '4px 0', fontSize: 11, fontStyle: 'italic', maxHeight: 80, overflowY: 'auto' }}>
                              {msg.proposal.systemPrompt}
                            </p>
                          </div>

                          {!isReadOnly && (
                            <button
                              type="button"
                              className="m1-primary"
                              style={{ width: '100%', marginTop: 8, fontSize: 12, padding: '7px 12px' }}
                              onClick={() => handleApplyProposal(msg.proposal)}
                            >
                              Appliquer à la configuration
                            </button>
                          )}
                        </div>
                      )}
                    </div>
                  ))}

                  {assistLoading && (
                    <div className="m1-assist-bubble m1-assist-bot" style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                      <Spinner size={14} />
                      <span style={{ fontSize: 12 }}>Génération de la proposition...</span>
                    </div>
                  )}
                </div>

                <form onSubmit={handleSendAssist} className="m1-assist-input-bar">
                  <input
                    type="text"
                    className="m1-assist-input"
                    placeholder={isReadOnly ? 'Mode lecture seule' : 'Décrivez ce que cet agent doit faire...'}
                    value={assistInput}
                    onChange={(e) => setAssistInput(e.target.value)}
                    disabled={assistLoading || isReadOnly}
                  />
                  <button
                    type="submit"
                    className="m1-primary"
                    disabled={!assistInput.trim() || assistLoading || isReadOnly}
                    style={{ padding: '8px 12px' }}
                  >
                    <Send size={14} />
                  </button>
                </form>
              </div>
            )}

            {/* ---------------------------------------------------- */}
            {/* TAB: APERÇU (Test interactif)                        */}
            {/* ---------------------------------------------------- */}
            {rightTab === 'PREVIEW' && (
              <div className="m1-preview-container">
                <div className="m1-preview-controls">
                  <select
                    className="m1-select"
                    style={{ padding: '6px 8px', fontSize: 12 }}
                    value={testVersionId}
                    onChange={(e) => setTestVersionId(e.target.value)}
                  >
                    {versions.map((v) => (
                      <option key={v.id} value={v.id}>
                        Version V{v.versionNumber} {v.active ? '(Active)' : ''}
                      </option>
                    ))}
                  </select>

                  <button
                    type="button"
                    className="m1-ghost-sm"
                    onClick={() => setChatMessages([])}
                    title="Effacer la conversation"
                  >
                    <RotateCcw size={12} />
                  </button>
                </div>

                {/* Overrides form collapsible */}
                <OverridesForm
                  open={overridesOpen}
                  onToggle={() => setOverridesOpen(!overridesOpen)}
                  overrides={testOverrides}
                  onChange={(k, v) => setTestOverrides((prev) => ({ ...prev, [k]: v }))}
                  params={tunableDefinitions}
                />

                {/* Chat feed */}
                <div className="m1-preview-feed">
                  {testError && (
                    <div style={{ marginBottom: 10 }}>
                      <ErrorBanner error={testError} />
                    </div>
                  )}
                  {chatMessages.length === 0 ? (
                    <div style={{ textAlign: 'center', padding: '30px 10px', color: 'var(--muted)', fontSize: 12 }}>
                      Testez votre agent en direct. Saisissez une demande ci-dessous.
                    </div>
                  ) : (
                    chatMessages.map((msg) => (
                      <div key={msg.id} className={`m1-chat-msg ${msg.role}`}>
                        <div className="m1-bubble">{msg.content}</div>
                        {msg.role === 'assistant' && (
                          <div className="m1-chat-meta">
                            <span
                              className={`m1-status ${msg.status === 'COMPLETED' ? 'm1-status-completed' : 'm1-status-failed'}`}
                            >
                              {msg.status}
                            </span>
                            {msg.durationMs !== undefined && <span>{msg.durationMs} ms</span>}
                            <span>
                              Tokens : {msg.tokensUsed !== null && msg.tokensUsed !== undefined ? msg.tokensUsed : 'Indisponible'}
                            </span>
                          </div>
                        )}
                      </div>
                    ))
                  )}

                  {testRunning && (
                    <div className="m1-chat-msg assistant">
                      <div className="m1-bubble" style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                        <Spinner size={14} /> Exécution en cours...
                      </div>
                    </div>
                  )}
                </div>

                <form onSubmit={handleExecuteTest} className="m1-assist-input-bar">
                  <input
                    type="text"
                    className="m1-assist-input"
                    placeholder="Saisissez un message de test..."
                    value={testInput}
                    onChange={(e) => setTestInput(e.target.value)}
                    disabled={testRunning || isReadOnly}
                  />
                  <button
                    type="submit"
                    className="m1-primary"
                    disabled={!testInput.trim() || testRunning || isReadOnly}
                    style={{ padding: '8px 12px' }}
                  >
                    <Send size={14} />
                  </button>
                </form>
              </div>
            )}
          </div>
        </aside>
      </div>

      {/* ==================================================================== */}
      {/* MODALS                                                               */}
      {/* ==================================================================== */}

      {/* Rename Modal */}
      {renameModalOpen && (
        <div className="m1-modal-backdrop" onClick={() => setRenameModalOpen(false)}>
          <div className="m1-dialog" onClick={(e) => e.stopPropagation()}>
            <h2>Renommer l'agent</h2>
            <form
              onSubmit={(e) => {
                e.preventDefault()
                if (renameInput.trim()) {
                  handleSaveIdentity(renameInput.trim(), description, status)
                  setRenameModalOpen(false)
                }
              }}
            >
              <Field
                label="Nom de l'agent"
                value={renameInput}
                onChange={(e) => setRenameInput(e.target.value)}
                required
                autoFocus
              />
              <div className="m1-modal-actions">
                <button type="button" className="m1-ghost" onClick={() => setRenameModalOpen(false)}>
                  Annuler
                </button>
                <button type="submit" className="m1-primary">
                  Renommer
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* New Version Modal */}
      {newVersionModalOpen && (
        <div className="m1-modal-backdrop" onClick={() => setNewVersionModalOpen(false)}>
          <div className="m1-dialog" onClick={(e) => e.stopPropagation()}>
            <h2>Enregistrer une nouvelle version</h2>
            <p>
              Cette version scellera la configuration courante (modèle <strong>{configModel}</strong>, instructions et paramètres).
            </p>
            <form onSubmit={handleCreateVersion}>
              <TextareaField
                label="Notes de version (changelog optionnel)"
                placeholder="Ex. Ajustement de la température et ajout des consignes de concision..."
                value={newVersionChangelog}
                onChange={(e) => setNewVersionChangelog(e.target.value)}
                autoFocus
              />
              <div className="m1-modal-actions">
                <button type="button" className="m1-ghost" onClick={() => setNewVersionModalOpen(false)} disabled={saving}>
                  Annuler
                </button>
                <button type="submit" className="m1-primary" disabled={saving}>
                  {saving ? <><Spinner size={14} /> Création...</> : 'Créer et activer la version'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Tools Catalog Modal */}
      {toolsModalOpen && (
        <div className="m1-modal-backdrop" onClick={() => setToolsModalOpen(false)}>
          <div className="m1-dialog" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 500 }}>
            <h2>Gérer les outils autorisés</h2>
            <p>Sélectionnez les outils MCP que cet agent peut appeler lors de son raisonnement.</p>

            {toolsUnavailable ? (
              <div style={{ marginBottom: 16 }}>
                <div className="m1-warning" style={{ marginBottom: 12 }}>
                  Le catalogue des outils (M2) est actuellement inaccessible. Vous pouvez saisir les identifiants d'outils manuellement.
                </div>
                <Field
                  label="Ajouter un outil par son identifiant"
                  placeholder="Ex. search_web, execute_sql..."
                  value={manualToolInput}
                  onChange={(e) => setManualToolInput(e.target.value)}
                />
                <button
                  type="button"
                  className="m1-ghost-sm"
                  onClick={() => {
                    if (manualToolInput.trim() && !configTools.includes(manualToolInput.trim())) {
                      setConfigTools((prev) => [...prev, manualToolInput.trim()])
                      setManualToolInput('')
                    }
                  }}
                >
                  <Plus size={12} /> Ajouter
                </button>
              </div>
            ) : (
              <div style={{ maxHeight: 240, overflowY: 'auto', marginBottom: 16 }}>
                {toolsCatalog.map((tool) => {
                  const isChecked = configTools.includes(tool.id)
                  return (
                    <label
                      key={tool.id}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        padding: '8px 10px',
                        borderBottom: '1px solid var(--border)',
                        cursor: 'pointer',
                      }}
                    >
                      <input
                        type="checkbox"
                        checked={isChecked}
                        onChange={(e) => {
                          if (e.target.checked) {
                            setConfigTools((prev) => [...prev, tool.id])
                          } else {
                            setConfigTools((prev) => prev.filter((id) => id !== tool.id))
                          }
                        }}
                      />
                      <div>
                        <strong style={{ fontSize: 13, color: '#1b1b35' }}>{tool.name}</strong>
                        <div style={{ fontSize: 11, color: 'var(--muted)' }}>
                          {tool.description || tool.id}
                        </div>
                      </div>
                    </label>
                  )
                })}
              </div>
            )}

            <div className="m1-modal-actions">
              <button type="button" className="m1-primary" onClick={() => setToolsModalOpen(false)}>
                Terminer ({configTools.length} sélectionnés)
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Execution Detail Drawer */}
      {selectedExecution && (
        <ExecutionDrawer execution={selectedExecution} onClose={() => setSelectedExecution(null)} />
      )}

      {/* Delete Confirmation */}
      {deleteConfirm && (
        isReadOnly ? (
          <ReasonDialog
            title={`Suppression administrative de "${name}"`}
            message={`Cet agent appartient à "${agent.ownerId}". Cette suppression est irréversible et sera enregistrée dans le journal d'audit.`}
            confirmLabel="Supprimer définitivement"
            onCancel={() => setDeleteConfirm(false)}
            onConfirm={handleDelete}
          />
        ) : (
          <ConfirmDialog
            title={`Supprimer l'agent "${name}" ?`}
            message="Cette action est irréversible et supprimera toutes les versions et l'historique d'exécution."
            confirmLabel="Supprimer"
            onCancel={() => setDeleteConfirm(false)}
            onConfirm={() => handleDelete()}
          />
        )
      )}

      {/* Archive Confirmation */}
      {archiveConfirm && (
        isReadOnly ? (
          <ReasonDialog
            title={`Archivage administratif de "${name}"`}
            message={`Cet agent appartient à "${agent.ownerId}". L'action sera consignée dans le journal d'audit.`}
            confirmLabel="Archiver l'agent"
            variant="primary"
            onCancel={() => setArchiveConfirm(false)}
            onConfirm={handleArchive}
          />
        ) : (
          <ConfirmDialog
            title={`Archiver l'agent "${name}" ?`}
            message="L'agent sera désactivé mais restera consultable."
            confirmLabel="Archiver"
            variant="primary"
            onCancel={() => setArchiveConfirm(false)}
            onConfirm={() => handleArchive()}
          />
        )
      )}
    </div>
  )
}
