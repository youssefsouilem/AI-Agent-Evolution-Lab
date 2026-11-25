export class ApiError extends Error {
  constructor(status, message, errorCode = null, details = null) {
    super(message)
    this.status = status
    this.name = 'ApiError'
    this.errorCode = errorCode || details?.errorCode || null
    this.execution = details?.execution || null
    this.limit = details?.limit ?? null
    this.usage = details?.usage ?? null
    this.details = details
  }
}

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function getCurrentDevRole() {
  try {
    return localStorage.getItem('evolution-lab-role') || 'USER'
  } catch {
    return 'USER'
  }
}

let userQuotaOverrides = {
  'demo-user': null, // default 5
}

let auditLog = [
  {
    id: 1,
    occurredAt: '2026-11-20T10:15:00Z',
    actor: 'demo-admin',
    action: 'ARCHIVE_AGENT',
    targetType: 'AGENT',
    targetId: '4',
    reason: 'Archivage préventif suite à une refonte de la documentation.',
  },
  {
    id: 2,
    occurredAt: '2026-11-21T14:30:00Z',
    actor: 'demo-admin',
    action: 'SET_QUOTA',
    targetType: 'USER',
    targetId: 'demo-user',
    reason: 'Attribution du quota standard de 5 agents pour évaluation.',
  },
]

let agents = [
  {
    id: 1,
    name: 'Agent Support Client',
    description: 'Agent conversationnel pour assister les clients et traiter les tickets fréquents.',
    status: 'ACTIVE',
    ownerId: 'demo-user',
    createdAt: '2026-11-10T10:00:00Z',
    updatedAt: '2026-11-15T14:30:00Z',
  },
  {
    id: 2,
    name: 'Agent Analyse Données',
    description: 'Agent automatisé pour analyser les métriques de vente et générer des synthèses.',
    status: 'DRAFT',
    ownerId: 'demo-user',
    createdAt: '2026-11-12T09:15:00Z',
    updatedAt: '2026-11-16T11:20:00Z',
  },
  {
    id: 3,
    name: 'Agent Recrutement',
    description: 'Agent RH pour le tri des candidatures et la préparation des entretiens.',
    status: 'ACTIVE',
    ownerId: 'user-other',
    createdAt: '2026-11-08T16:45:00Z',
    updatedAt: '2026-11-14T09:00:00Z',
  },
  {
    id: 4,
    name: 'Agent Documentation',
    description: 'Agent pour maintenir et structurer la documentation technique des APIs.',
    status: 'ARCHIVED',
    ownerId: 'demo-user',
    createdAt: '2026-10-25T08:30:00Z',
    updatedAt: '2026-11-01T17:00:00Z',
  },
]

let versions = [
  {
    id: 1,
    agentId: 1,
    label: 'Version 1 (active)',
    versionNumber: 1,
    model: 'gpt-4o-mini',
    systemPrompt: 'Tu es un agent de support client bienveillant. Réponds de manière concise et professionnelle en français.',
    changeLog: 'Version initiale de démarrage',
    active: true,
    source: 'USER',
    createdAt: '2026-11-10T10:00:00Z',
    configuration: {
      temperature: 0.7,
      maxTokens: 1024,
      maxTurns: 3,
      timeoutSeconds: 30,
      style: 'concise',
      allowedToolIds: [1, 2],
    },
  },
  {
    id: 2,
    agentId: 1,
    label: 'Version 2',
    versionNumber: 2,
    model: 'gpt-4o-mini',
    systemPrompt: 'Tu es un agent de support expert. Fournis des explications approfondies avec étapes de résolution.',
    changeLog: 'Mode détaillé et ajout de la base de connaissances',
    active: false,
    source: 'USER',
    createdAt: '2026-11-15T14:30:00Z',
    configuration: {
      temperature: 0.8,
      maxTokens: 2048,
      maxTurns: 5,
      timeoutSeconds: 45,
      style: 'detaille',
      allowedToolIds: [1, 2, 3],
    },
  },
  {
    id: 3,
    agentId: 2,
    label: 'Version 1',
    versionNumber: 1,
    model: 'gpt-4o-mini',
    systemPrompt: 'Tu es un analyste de données spécialisé dans le reporting automatisé.',
    changeLog: 'Optimisation automatique par M5 (Optuna)',
    active: true,
    source: 'M5',
    studyId: 12,
    createdAt: '2026-11-12T09:15:00Z',
    configuration: {
      temperature: 0.3,
      maxTokens: 1536,
      maxTurns: 2,
      timeoutSeconds: 60,
      style: 'concise',
      allowedToolIds: [4],
    },
  },
  {
    id: 4,
    agentId: 2,
    label: 'Version 2',
    versionNumber: 2,
    model: 'gpt-4o',
    systemPrompt: 'Tu es un analyste financier et quantitatif.',
    changeLog: 'Passage sur GPT-4o pour calculs complexes',
    active: false,
    source: 'USER',
    createdAt: '2026-11-16T11:20:00Z',
    configuration: {
      temperature: 0.2,
      maxTokens: 3000,
      maxTurns: 4,
      timeoutSeconds: 60,
      style: 'detaille',
      allowedToolIds: [4],
    },
  },
  {
    id: 5,
    agentId: 3,
    label: 'Version 1 (active)',
    versionNumber: 1,
    model: 'gpt-4o',
    systemPrompt: 'Tu es un recruteur expert. Analyse les CV et évalue la correspondance technique.',
    changeLog: 'Version de production RH',
    active: true,
    source: 'USER',
    createdAt: '2026-11-08T16:45:00Z',
    configuration: {
      temperature: 0.6,
      maxTokens: 1280,
      maxTurns: 4,
      timeoutSeconds: 40,
      style: 'concise',
      allowedToolIds: [5, 6],
    },
  },
  {
    id: 6,
    agentId: 4,
    label: 'Version 1 (active)',
    versionNumber: 1,
    model: 'gpt-4o-mini',
    systemPrompt: 'Tu es un rédacteur technique spécialisé OpenAPI et Markdown.',
    changeLog: 'Création du générateur de doc',
    active: true,
    source: 'USER',
    createdAt: '2026-10-25T08:30:00Z',
    configuration: {
      temperature: 0.4,
      maxTokens: 2560,
      maxTurns: 3,
      timeoutSeconds: 90,
      style: 'concise',
      allowedToolIds: [8],
    },
  },
]

let executions = [
  {
    id: 101,
    agentId: 1,
    versionId: 1,
    versionNumber: 1,
    status: 'COMPLETED',
    input: 'Comment réinitialiser mon mot de passe oublié ?',
    inputPreview: 'Comment réinitialiser mon mot de passe oublié ?',
    context: JSON.stringify({ userId: 44, channel: 'chat-web' }),
    output: 'Pour réinitialiser votre mot de passe, rendez-vous sur la page de connexion, cliquez sur "Mot de passe oublié" et suivez les instructions envoyées par e-mail.',
    durationMs: 820,
    promptTokens: 48,
    completionTokens: 32,
    totalTokens: 80,
    toolCallCount: 1,
    hasOverrides: false,
    startedAt: '2026-11-18T14:20:00Z',
    finishedAt: '2026-11-18T14:20:01Z',
    requestedBy: 'demo-user',
    modelUsed: 'gpt-4o-mini',
  },
  {
    id: 102,
    agentId: 1,
    versionId: 1,
    versionNumber: 1,
    status: 'COMPLETED',
    input: 'Quel est le délai de livraison standard ?',
    inputPreview: 'Quel est le délai de livraison standard ?',
    context: null,
    output: 'Le délai de livraison standard est de 3 à 5 jours ouvrés en France métropolitaine.',
    durationMs: 650,
    promptTokens: null,
    completionTokens: null,
    totalTokens: null,
    toolCallCount: 0,
    hasOverrides: true,
    overrides: JSON.stringify({ temperature: 0.2 }),
    appliedSettings: JSON.stringify({ temperature: 0.2, maxTokens: 1024, style: 'concise' }),
    startedAt: '2026-11-18T16:45:00Z',
    finishedAt: '2026-11-18T16:45:01Z',
    requestedBy: 'demo-user',
    modelUsed: 'gpt-4o-mini',
  },
  {
    id: 103,
    agentId: 2,
    versionId: 3,
    versionNumber: 1,
    status: 'FAILED',
    input: 'Générer rapport financier Q3',
    inputPreview: 'Générer rapport financier Q3',
    context: JSON.stringify({ quarter: 'Q3-2026' }),
    output: null,
    errorCode: 'PROVIDER_RATE_LIMITED',
    errorMessage: 'Limite de débit atteinte auprès du fournisseur LLM.',
    durationMs: 1540,
    promptTokens: 120,
    completionTokens: 0,
    totalTokens: 120,
    toolCallCount: 0,
    hasOverrides: false,
    startedAt: '2026-11-19T09:10:00Z',
    finishedAt: '2026-11-19T09:10:02Z',
    requestedBy: 'demo-user',
    modelUsed: 'gpt-4o-mini',
  },
  {
    id: 104,
    agentId: 3,
    versionId: 5,
    versionNumber: 1,
    status: 'COMPLETED',
    input: 'Évaluer le profil Ingénieur IA Senior',
    inputPreview: 'Évaluer le profil Ingénieur IA Senior',
    context: null,
    output: 'Candidat solide : 6 ans d expérience Python, PyTorch, microservices Spring Boot. Recommandé pour entretien technique.',
    durationMs: 1420,
    promptTokens: 240,
    completionTokens: 60,
    totalTokens: 300,
    toolCallCount: 2,
    hasOverrides: false,
    startedAt: '2026-11-19T11:00:00Z',
    finishedAt: '2026-11-19T11:00:01Z',
    requestedBy: 'user-other',
    modelUsed: 'gpt-4o',
  },
]

let nextAgentId = 5
let nextVersionId = 7
let nextExecutionId = 105
let nextAuditId = 3

export const agentMock = {
  async getMe() {
    await delay(200)
    const role = getCurrentDevRole()
    const isAdmin = role === 'ADMIN'

    const userAgents = agents.filter((a) => a.ownerId === (isAdmin ? 'demo-admin' : 'demo-user'))
    const effectiveLimit = isAdmin ? null : userQuotaOverrides['demo-user'] ?? 5

    return {
      userId: isAdmin ? 'demo-admin' : 'demo-user',
      displayName: isAdmin ? 'Administrateur démo' : 'Utilisateur démo',
      role: isAdmin ? 'ADMIN' : 'USER',
      limits: {
        maxAgents: effectiveLimit,
      },
      usage: {
        agents: userAgents.length,
      },
      capabilities: {
        history: true,
        overrides: true,
        assist: true,
        llmCatalog: true,
        stats: true,
        quota: true,
      },
    }
  },

  async listAgents({ all = false } = {}) {
    await delay(350)
    const role = getCurrentDevRole()
    const isAdmin = role === 'ADMIN'

    if (all && isAdmin) {
      return agents.map((a) => ({ ...a }))
    }
    const currentOwner = isAdmin ? 'demo-admin' : 'demo-user'
    return agents.filter((a) => a.ownerId === currentOwner).map((a) => ({ ...a }))
  },

  async getAgent(id) {
    await delay(300)
    const role = getCurrentDevRole()
    const isAdmin = role === 'ADMIN'
    const currentOwner = isAdmin ? 'demo-admin' : 'demo-user'

    const agent = agents.find((a) => a.id === parseInt(id, 10))
    if (!agent) {
      throw new ApiError(404, 'Agent introuvable')
    }

    if (!isAdmin && agent.ownerId !== currentOwner) {
      throw new ApiError(404, 'Agent introuvable')
    }

    return { ...agent }
  },

  async createAgent(data) {
    await delay(400)
    const role = getCurrentDevRole()
    const isAdmin = role === 'ADMIN'
    const ownerId = isAdmin ? 'demo-admin' : 'demo-user'

    if (!data.name || !data.name.trim()) {
      throw new ApiError(400, 'Le nom de l agent est obligatoire')
    }

    if (!isAdmin) {
      const currentCount = agents.filter((a) => a.ownerId === ownerId).length
      const limit = userQuotaOverrides['demo-user'] ?? 5
      if (currentCount >= limit) {
        throw new ApiError(
          403,
          `Limite atteinte : quota d'agents dépassé (limite : ${limit}).`,
          'AGENT_QUOTA_EXCEEDED',
          { limit, usage: currentCount }
        )
      }
    }

    const newAgent = {
      id: nextAgentId++,
      name: data.name.trim(),
      description: data.description || '',
      status: data.status || 'DRAFT',
      ownerId,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
    agents.unshift(newAgent)
    return { ...newAgent }
  },

  async updateAgent(id, data) {
    await delay(350)
    const role = getCurrentDevRole()
    const isAdmin = role === 'ADMIN'
    const currentOwner = isAdmin ? 'demo-admin' : 'demo-user'

    const index = agents.findIndex((a) => a.id === parseInt(id, 10))
    if (index === -1) throw new ApiError(404, 'Agent introuvable')

    if (!isAdmin && agents[index].ownerId !== currentOwner) {
      throw new ApiError(404, 'Agent introuvable')
    }

    if (isAdmin && agents[index].ownerId !== currentOwner) {
      throw new ApiError(
        403,
        "Action interdite : vous consultez cet agent en lecture seule.",
        'ADMIN_READ_ONLY'
      )
    }

    agents[index] = {
      ...agents[index],
      ...data,
      updatedAt: new Date().toISOString(),
    }
    return { ...agents[index] }
  },

  async deleteAgent(id) {
    await delay(350)
    const role = getCurrentDevRole()
    const isAdmin = role === 'ADMIN'
    const currentOwner = isAdmin ? 'demo-admin' : 'demo-user'
    const numId = parseInt(id, 10)

    const index = agents.findIndex((a) => a.id === numId)
    if (index === -1) throw new ApiError(404, 'Agent introuvable')

    if (!isAdmin && agents[index].ownerId !== currentOwner) {
      throw new ApiError(404, 'Agent introuvable')
    }

    if (isAdmin && agents[index].ownerId !== currentOwner) {
      throw new ApiError(
        403,
        "Action interdite : vous consultez cet agent en lecture seule.",
        'ADMIN_READ_ONLY'
      )
    }

    agents.splice(index, 1)
    versions = versions.filter((v) => v.agentId !== numId)
    executions = executions.filter((e) => e.agentId !== numId)
  },

  async listVersions(agentId) {
    await delay(300)
    const numId = parseInt(agentId, 10)
    return versions.filter((v) => v.agentId === numId).map((v) => ({ ...v }))
  },

  async getVersion(agentId, vid) {
    await delay(300)
    const version = versions.find(
      (v) => v.id === parseInt(vid, 10) && v.agentId === parseInt(agentId, 10)
    )
    if (!version) throw new ApiError(404, 'Version introuvable')
    return { ...version }
  },

  async createVersion(agentId, data) {
    await delay(400)
    const numId = parseInt(agentId, 10)
    const agent = agents.find((a) => a.id === numId)
    if (!agent) throw new ApiError(404, 'Agent introuvable')

    const role = getCurrentDevRole()
    const isAdmin = role === 'ADMIN'
    if (isAdmin && agent.ownerId !== 'demo-admin') {
      throw new ApiError(
        403,
        "Action interdite : vous consultez cet agent en lecture seule.",
        'ADMIN_READ_ONLY'
      )
    }

    const agentVersions = versions.filter((v) => v.agentId === numId)
    const maxVersionNumber =
      agentVersions.length > 0 ? Math.max(...agentVersions.map((v) => v.versionNumber)) : 0

    const newVersion = {
      id: nextVersionId++,
      agentId: numId,
      label: `Version ${maxVersionNumber + 1}`,
      versionNumber: maxVersionNumber + 1,
      model: data.model || 'gpt-4o-mini',
      systemPrompt: data.systemPrompt || '',
      changeLog: data.changeLog || '',
      active: false,
      source: 'USER',
      createdAt: new Date().toISOString(),
      configuration: data.configuration || {
        temperature: 0.7,
        maxTokens: 1024,
        maxTurns: 3,
        timeoutSeconds: 30,
        style: 'concise',
        allowedToolIds: [],
      },
    }
    versions.unshift(newVersion)
    return { ...newVersion }
  },

  async activateVersion(agentId, vid) {
    await delay(300)
    const numId = parseInt(agentId, 10)
    const agent = agents.find((a) => a.id === numId)
    if (!agent) throw new ApiError(404, 'Agent introuvable')

    const role = getCurrentDevRole()
    const isAdmin = role === 'ADMIN'
    if (isAdmin && agent.ownerId !== 'demo-admin') {
      throw new ApiError(
        403,
        "Action interdite : vous consultez cet agent en lecture seule.",
        'ADMIN_READ_ONLY'
      )
    }

    const targetVid = parseInt(vid, 10)
    const agentVersions = versions.filter((v) => v.agentId === numId)
    const target = agentVersions.find((v) => v.id === targetVid)
    if (!target) throw new ApiError(404, 'Version introuvable')

    agentVersions.forEach((v) => {
      v.active = false
      v.label = `Version ${v.versionNumber}`
    })
    target.active = true
    target.label = `Version ${target.versionNumber} (active)`
    return { ...target }
  },

  async updateConfiguration(agentId, vid, config) {
    await delay(300)
    const version = versions.find(
      (v) => v.id === parseInt(vid, 10) && v.agentId === parseInt(agentId, 10)
    )
    if (!version) throw new ApiError(404, 'Version introuvable')
    version.configuration = { ...version.configuration, ...config }
    return { ...version }
  },

  async getTunableParameters(agentId, vid) {
    await delay(250)
    const version = versions.find(
      (v) => v.id === parseInt(vid, 10) && v.agentId === parseInt(agentId, 10)
    )
    const cfg = version?.configuration || {}
    return [
      {
        name: 'temperature',
        type: 'float',
        min: 0.0,
        max: 2.0,
        current: cfg.temperature ?? 0.7,
        description: 'Créativité',
      },
      {
        name: 'maxTokens',
        type: 'int',
        min: 256,
        max: 4096,
        current: cfg.maxTokens ?? 1024,
        description: 'Longueur maximale de la réponse',
      },
      {
        name: 'maxTurns',
        type: 'int',
        min: 1,
        max: 10,
        current: cfg.maxTurns ?? 3,
        description: "Nombre maximal d'appels d'outils",
      },
      {
        name: 'timeoutSeconds',
        type: 'int',
        min: 5,
        max: 120,
        current: cfg.timeoutSeconds ?? 30,
        description: "Délai maximal d'exécution (secondes)",
      },
      {
        name: 'style',
        type: 'categorical',
        choices: ['concise', 'detaille'],
        current: cfg.style || 'concise',
        description: 'Style de réponse',
      },
    ]
  },

  async executeAgent(agentId, { versionId, input, context, overrides }) {
    await delay(450)
    const numAgentId = parseInt(agentId, 10)
    const agent = agents.find((a) => a.id === numAgentId)
    if (!agent) throw new ApiError(404, 'Agent introuvable')

    const role = getCurrentDevRole()
    const isAdmin = role === 'ADMIN'
    if (isAdmin && agent.ownerId !== 'demo-admin') {
      throw new ApiError(
        403,
        "Action interdite : vous consultez cet agent en lecture seule.",
        'ADMIN_READ_ONLY'
      )
    }

    if (input === 'error-404') {
      throw new ApiError(404, 'Ressource introuvable')
    }
    if (input === 'error-422') {
      throw new ApiError(422, 'Données invalides', 'TOOL_LIMIT_EXCEEDED', {
        errorCode: 'TOOL_LIMIT_EXCEEDED',
        message: "Nombre maximal d'appels d'outils dépassé (limite atteinte)",
      })
    }
    if (input === 'error-502') {
      throw new ApiError(
        502,
        'Service indisponible (outils ou modèle de langage)',
        'PROVIDER_NOT_CONFIGURED',
        {
          errorCode: 'PROVIDER_NOT_CONFIGURED',
          message: 'Clé API du modèle LLM non configurée sur le serveur M1',
        }
      )
    }
    if (input === 'error-504') {
      throw new ApiError(504, 'Délai dépassé', 'TIMEOUT', {
        errorCode: 'TIMEOUT',
        message: "Le fournisseur LLM n'a pas répondu dans le délai imparti",
      })
    }

    const execId = nextExecutionId++
    const appliedSettings = {
      temperature: overrides?.temperature ?? 0.7,
      maxTokens: overrides?.maxTokens ?? 1024,
      style: overrides?.style ?? 'concise',
    }

    const executionRecord = {
      id: execId,
      agentId: numAgentId,
      versionId: versionId ? parseInt(versionId, 10) : 1,
      versionNumber: 1,
      status: 'COMPLETED',
      input,
      inputPreview: input.slice(0, 100),
      context: context ? JSON.stringify(context) : null,
      output: `[Mode Démo] Réponse générée avec succès pour l'agent "${agent.name}" : "Bonjour ! Comment puis-je vous assister aujourd'hui ?"`,
      durationMs: 780,
      promptTokens: 52,
      completionTokens: 38,
      totalTokens: 90,
      toolCallCount: 1,
      hasOverrides: Boolean(overrides && Object.keys(overrides).length > 0),
      overrides: overrides ? JSON.stringify(overrides) : null,
      appliedSettings: JSON.stringify(appliedSettings),
      startedAt: new Date(Date.now() - 780).toISOString(),
      finishedAt: new Date().toISOString(),
      requestedBy: agent.ownerId,
      modelUsed: 'gpt-4o-mini',
    }
    executions.unshift(executionRecord)

    return {
      executionId: execId,
      status: 'COMPLETED',
      output: executionRecord.output,
      tokensUsed: 90,
      promptTokens: 52,
      completionTokens: 38,
      durationMs: 780,
      versionId: executionRecord.versionId,
      toolCallCount: 1,
      appliedSettings,
      overrides: overrides || null,
    }
  },

  async listTools() {
    await delay(300)
    return [
      { id: 1, name: 'Recherche FAQ' },
      { id: 2, name: 'Création ticket' },
      { id: 3, name: 'Base de connaissances' },
      { id: 4, name: 'Analyse CSV' },
      { id: 5, name: 'Recherche candidat' },
      { id: 6, name: 'Évaluation profil' },
      { id: 7, name: 'Génération rapport' },
      { id: 8, name: 'Formatage Markdown' },
    ]
  },

  async listLlmModels() {
    await delay(300)
    return [
      {
        provider: 'openai',
        label: 'OpenAI',
        available: true,
        models: [
          { id: 'gpt-4o-mini', label: 'GPT-4o Mini', recommended: true },
          { id: 'gpt-4o', label: 'GPT-4o', recommended: false },
          { id: 'gpt-4-turbo', label: 'GPT-4 Turbo', recommended: false },
        ],
      },
      {
        provider: 'anthropic',
        label: 'Anthropic',
        available: false,
        reason: 'Clé ANTHROPIC_API_KEY absente sur le serveur.',
        models: [
          { id: 'claude-3-5-sonnet', label: 'Claude 3.5 Sonnet', recommended: true },
          { id: 'claude-3-haiku', label: 'Claude 3 Haiku', recommended: false },
        ],
      },
      {
        provider: 'google',
        label: 'Google Gemini',
        available: false,
        reason: 'Clé GEMINI_API_KEY absente sur le serveur.',
        models: [
          { id: 'gemini-1.5-pro', label: 'Gemini 1.5 Pro', recommended: false },
          { id: 'gemini-1.5-flash', label: 'Gemini 1.5 Flash', recommended: true },
        ],
      },
    ]
  },

  async assistAgent({ description, language: _language = 'fr', provider = 'openai', model = 'gpt-4o-mini' }) {
    await delay(600)
    if (!description || !description.trim()) {
      throw new ApiError(400, 'La description est obligatoire')
    }

    if (description.includes('error-429')) {
      throw new ApiError(429, 'Trop de demandes, réessayez plus tard.', 'RATE_LIMITED')
    }
    if (description.includes('error-502')) {
      throw new ApiError(502, 'Fournisseur absent ou sortie invalide.', 'ASSIST_INVALID_OUTPUT')
    }

    const trimmed = description.trim()
    const nameProposal =
      trimmed.length > 25
        ? 'Agent ' + trimmed.slice(0, 25).trim()
        : 'Agent ' + trimmed

    return {
      name: nameProposal,
      description: trimmed,
      systemPrompt: `Tu es un assistant IA spécialisé dans la mission suivante : "${trimmed}".\n\nDirectives :\n- Réponds de manière précise, claire et structurée.\n- Sois professionnel, courtois et synthétique.\n- Respecte scrupuleusement la langue française.`,
      provider,
      model,
      temperature: 0.7,
      maxTokens: 1024,
      maxTurns: 3,
      timeoutSeconds: 30,
      style: 'concise',
    }
  },

  async getAgentStats(agentId, days = 7) {
    await delay(300)
    const numId = parseInt(agentId, 10)
    const agentExecs = executions.filter((e) => e.agentId === numId)
    const total = agentExecs.length
    const completed = agentExecs.filter((e) => e.status === 'COMPLETED').length
    const failed = agentExecs.filter((e) => e.status === 'FAILED').length
    const successRate = total > 0 ? (completed / total) * 100 : 0
    const knownTokensExecs = agentExecs.filter((e) => e.totalTokens !== null && e.totalTokens !== undefined)
    const totalTokensKnown = knownTokensExecs.reduce((sum, e) => sum + e.totalTokens, 0)
    const executionsWithUnknownTokens = total - knownTokensExecs.length

    // Generate byDay breakdown
    const byDay = []
    for (let i = days - 1; i >= 0; i--) {
      const d = new Date()
      d.setDate(d.getDate() - i)
      const dateStr = d.toISOString().split('T')[0]
      const count = i === 0 ? total : Math.floor(Math.random() * 3)
      byDay.push({
        date: dateStr,
        total: count,
        failed: i === 1 ? 1 : 0,
        avgDurationMs: 750 + Math.floor(Math.random() * 200),
        tokens: count * 85,
      })
    }

    return {
      days,
      total: Math.max(total, 5),
      completed: Math.max(completed, 4),
      failed,
      successRate: total > 0 ? Math.round(successRate) : 92,
      avgDurationMs: 820,
      p95DurationMs: 1450,
      totalTokensKnown: totalTokensKnown > 0 ? totalTokensKnown : 450,
      executionsWithUnknownTokens,
      byDay,
    }
  },

  async adminListUsers() {
    await delay(300)
    return [
      {
        ownerId: 'demo-user',
        agentCount: agents.filter((a) => a.ownerId === 'demo-user').length,
        activeAgentCount: agents.filter(
          (a) => a.ownerId === 'demo-user' && a.status === 'ACTIVE'
        ).length,
        effectiveQuota: userQuotaOverrides['demo-user'] ?? 5,
        quotaIsOverride: userQuotaOverrides['demo-user'] !== null,
        lastActivityAt: '2026-11-23T20:45:00Z',
      },
      {
        ownerId: 'user-other',
        agentCount: agents.filter((a) => a.ownerId === 'user-other').length,
        activeAgentCount: agents.filter(
          (a) => a.ownerId === 'user-other' && a.status === 'ACTIVE'
        ).length,
        effectiveQuota: 5,
        quotaIsOverride: false,
        lastActivityAt: '2026-11-22T16:10:00Z',
      },
      {
        ownerId: 'demo-admin',
        agentCount: agents.filter((a) => a.ownerId === 'demo-admin').length,
        activeAgentCount: agents.filter(
          (a) => a.ownerId === 'demo-admin' && a.status === 'ACTIVE'
        ).length,
        effectiveQuota: null,
        quotaIsOverride: false,
        lastActivityAt: '2026-11-23T21:00:00Z',
      },
    ]
  },

  async adminSetQuota(ownerId, maxAgents) {
    await delay(300)
    userQuotaOverrides[ownerId] = maxAgents
    auditLog.unshift({
      id: nextAuditId++,
      occurredAt: new Date().toISOString(),
      actor: 'demo-admin',
      action: 'SET_QUOTA',
      targetType: 'USER',
      targetId: ownerId,
      reason:
        maxAgents === null
          ? 'Réinitialisation au quota par défaut.'
          : `Modification personnalisée du quota à ${maxAgents} agents.`,
    })
    return { ownerId, maxAgents }
  },

  async adminArchiveAgent(id, reason) {
    await delay(350)
    if (!reason || reason.trim().length < 5) {
      throw new ApiError(400, 'Une raison d au moins 5 caractères est obligatoire')
    }
    const numId = parseInt(id, 10)
    const agent = agents.find((a) => a.id === numId)
    if (!agent) throw new ApiError(404, 'Agent introuvable')

    agent.status = 'ARCHIVED'
    auditLog.unshift({
      id: nextAuditId++,
      occurredAt: new Date().toISOString(),
      actor: 'demo-admin',
      action: 'ARCHIVE_AGENT',
      targetType: 'AGENT',
      targetId: String(numId),
      reason: reason.trim(),
    })
  },

  async adminDeleteAgent(id, reason) {
    await delay(350)
    if (!reason || reason.trim().length < 5) {
      throw new ApiError(400, 'Une raison d au moins 5 caractères est obligatoire')
    }
    const numId = parseInt(id, 10)
    const index = agents.findIndex((a) => a.id === numId)
    if (index === -1) throw new ApiError(404, 'Agent introuvable')

    agents.splice(index, 1)
    versions = versions.filter((v) => v.agentId !== numId)
    executions = executions.filter((e) => e.agentId !== numId)

    auditLog.unshift({
      id: nextAuditId++,
      occurredAt: new Date().toISOString(),
      actor: 'demo-admin',
      action: 'DELETE_AGENT',
      targetType: 'AGENT',
      targetId: String(numId),
      reason: reason.trim(),
    })
  },

  async adminListAudit(page = 0, size = 20) {
    await delay(300)
    const start = page * size
    const paginated = auditLog.slice(start, start + size)
    return {
      items: paginated.map((item) => ({ ...item })),
      totalCount: auditLog.length,
    }
  },

  async getM1Health() {
    await delay(200)
    return { status: 'UP', raw: { status: 'UP', components: { db: { status: 'UP' } } } }
  },

  async listExecutions(agentId, { status, versionId, page = 0, size = 10 } = {}) {
    await delay(300)
    const numAgentId = parseInt(agentId, 10)
    let list = executions.filter((e) => e.agentId === numAgentId)
    if (status) {
      list = list.filter((e) => e.status === status)
    }
    if (versionId) {
      list = list.filter((e) => e.versionId === parseInt(versionId, 10))
    }
    const start = page * size
    const paginated = list.slice(start, start + size)
    return {
      items: paginated.map((e) => ({ ...e })),
      totalCount: list.length,
    }
  },

  async getExecution(agentId, executionId) {
    await delay(250)
    const exec = executions.find(
      (e) => e.id === parseInt(executionId, 10) && e.agentId === parseInt(agentId, 10)
    )
    if (!exec) throw new ApiError(404, 'Exécution introuvable')
    return { ...exec }
  },

  async listAllExecutions({ agentId, status, page = 0, size = 10, all = false } = {}) {
    await delay(300)
    let list = [...executions]
    const role = getCurrentDevRole()
    const isAdmin = role === 'ADMIN'

    if (!all || !isAdmin) {
      list = list.filter((e) => e.requestedBy === (isAdmin ? 'demo-admin' : 'demo-user'))
    }
    if (agentId) {
      list = list.filter((e) => e.agentId === parseInt(agentId, 10))
    }
    if (status) {
      list = list.filter((e) => e.status === status)
    }
    const start = page * size
    const paginated = list.slice(start, start + size)
    return {
      items: paginated.map((e) => ({ ...e })),
      totalCount: list.length,
    }
  },
}
