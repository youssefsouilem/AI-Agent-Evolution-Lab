export class ApiError extends Error {
  constructor(status, message, details = null) {
    super(message)
    this.status = status
    this.name = 'ApiError'
    this.errorCode = details?.errorCode || null
    this.execution = details?.execution || null
    this.details = details
  }
}

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

let agents = [
  {
    id: 1,
    name: 'Agent Support Client',
    description: 'Agent conversationnel pour assister les clients et traiter les tickets fréquents.',
    status: 'ACTIVE',
    ownerId: 'user-alice',
    createdAt: '2026-11-10T10:00:00Z',
    updatedAt: '2026-11-15T14:30:00Z',
  },
  {
    id: 2,
    name: 'Agent Analyse Données',
    description: 'Agent automatisé pour analyser les métriques de vente et générer des synthèses.',
    status: 'DRAFT',
    ownerId: 'user-alice',
    createdAt: '2026-11-12T09:15:00Z',
    updatedAt: '2026-11-16T11:20:00Z',
  },
  {
    id: 3,
    name: 'Agent Recrutement',
    description: 'Agent RH pour le tri des candidatures et la préparation des entretiens.',
    status: 'ACTIVE',
    ownerId: 'user-bob',
    createdAt: '2026-11-08T16:45:00Z',
    updatedAt: '2026-11-14T09:00:00Z',
  },
  {
    id: 4,
    name: 'Agent Documentation',
    description: 'Agent pour maintenir et structurer la documentation technique des APIs.',
    status: 'ARCHIVED',
    ownerId: 'user-alice',
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
    systemPrompt: 'Tu es un agent de support client bienveillant. Réponds de manière concise et professionnelle.',
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
    agentId: 3,
    label: 'Version 2',
    versionNumber: 2,
    model: 'gpt-4o',
    systemPrompt: 'Tu es un consultant RH avec 10 ans d expérience dans la tech.',
    changeLog: 'Raffinement des critères d évaluation',
    active: false,
    source: 'USER',
    createdAt: '2026-11-14T09:00:00Z',
    configuration: {
      temperature: 0.7,
      maxTokens: 2048,
      maxTurns: 6,
      timeoutSeconds: 50,
      style: 'detaille',
      allowedToolIds: [5, 6, 7],
    },
  },
  {
    id: 7,
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
    requestedBy: 'user-alice',
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
    totalTokens: null, // Test case: tokens null should display "Indisponible"
    toolCallCount: 0,
    hasOverrides: true,
    overrides: JSON.stringify({ temperature: 0.2 }),
    appliedSettings: JSON.stringify({ temperature: 0.2, maxTokens: 1024, style: 'concise' }),
    startedAt: '2026-11-18T16:45:00Z',
    finishedAt: '2026-11-18T16:45:01Z',
    requestedBy: 'user-alice',
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
    requestedBy: 'user-alice',
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
    requestedBy: 'user-bob',
    modelUsed: 'gpt-4o',
  },
]

let nextAgentId = 5
let nextVersionId = 8
let nextExecutionId = 105

export const agentMock = {
  async listAgents({ all = false } = {}) {
    await delay(500)
    if (all) {
      return agents.map((a) => ({ ...a }))
    }
    // Normal user sees only alice's agents by default
    return agents.filter((a) => a.ownerId === 'user-alice').map((a) => ({ ...a }))
  },

  async getAgent(id) {
    await delay(500)
    const agent = agents.find((a) => a.id === parseInt(id, 10))
    if (!agent) throw new ApiError(404, 'Agent introuvable')
    return { ...agent }
  },

  async createAgent(data) {
    await delay(500)
    if (!data.name || !data.name.trim()) {
      throw new ApiError(400, 'Le nom de l agent est obligatoire')
    }
    const newAgent = {
      id: nextAgentId++,
      name: data.name.trim(),
      description: data.description || '',
      status: data.status || 'DRAFT',
      ownerId: 'user-alice',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
    agents.unshift(newAgent)
    return { ...newAgent }
  },

  async updateAgent(id, data) {
    await delay(500)
    const index = agents.findIndex((a) => a.id === parseInt(id, 10))
    if (index === -1) throw new ApiError(404, 'Agent introuvable')
    agents[index] = {
      ...agents[index],
      ...data,
      updatedAt: new Date().toISOString(),
    }
    return { ...agents[index] }
  },

  async deleteAgent(id) {
    await delay(500)
    const numId = parseInt(id, 10)
    const index = agents.findIndex((a) => a.id === numId)
    if (index === -1) throw new ApiError(404, 'Agent introuvable')
    agents.splice(index, 1)
    // Deleting an agent deletes its versions and execution history
    versions = versions.filter((v) => v.agentId !== numId)
    executions = executions.filter((e) => e.agentId !== numId)
  },

  async listVersions(agentId) {
    await delay(500)
    const numId = parseInt(agentId, 10)
    return versions.filter((v) => v.agentId === numId).map((v) => ({ ...v }))
  },

  async getVersion(agentId, vid) {
    await delay(500)
    const version = versions.find(
      (v) => v.id === parseInt(vid, 10) && v.agentId === parseInt(agentId, 10)
    )
    if (!version) throw new ApiError(404, 'Version introuvable')
    return { ...version }
  },

  async createVersion(agentId, data) {
    await delay(500)
    const numId = parseInt(agentId, 10)
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
    await delay(500)
    const numId = parseInt(agentId, 10)
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
    await delay(500)
    const version = versions.find(
      (v) => v.id === parseInt(vid, 10) && v.agentId === parseInt(agentId, 10)
    )
    if (!version) throw new ApiError(404, 'Version introuvable')
    version.configuration = { ...version.configuration, ...config }
    return { ...version }
  },

  async getTunableParameters(agentId, vid) {
    await delay(500)
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
    await delay(500)
    const numAgentId = parseInt(agentId, 10)
    const agent = agents.find((a) => a.id === numAgentId)
    if (!agent) throw new ApiError(404, 'Agent introuvable')

    // Error simulation triggers
    if (input === 'error-404') {
      throw new ApiError(404, 'Ressource introuvable')
    }
    if (input === 'error-422') {
      throw new ApiError(422, 'Données invalides', {
        errorCode: 'TOOL_LIMIT_EXCEEDED',
        message: "Nombre maximal d'appels d'outils dépassé (limite atteinte)",
      })
    }
    if (input === 'error-502') {
      throw new ApiError(502, 'Service indisponible (outils ou modèle de langage)', {
        errorCode: 'PROVIDER_NOT_CONFIGURED',
        message: 'Clé API du modèle LLM non configurée sur le serveur M1',
      })
    }
    if (input === 'error-504') {
      throw new ApiError(504, 'Délai dépassé', {
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
      output: `[Mode Démo] Réponse générée avec succès pour l'agent "${agent.name}" à partir de l'entrée : "${input}".`,
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
      requestedBy: 'user-alice',
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
    await delay(500)
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

  async getM1Health() {
    await delay(300)
    return { status: 'UP', raw: { status: 'UP', components: { db: { status: 'UP' } } } }
  },

  async listExecutions(agentId, { status, versionId, page = 0, size = 10 } = {}) {
    await delay(400)
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
    await delay(300)
    const exec = executions.find(
      (e) => e.id === parseInt(executionId, 10) && e.agentId === parseInt(agentId, 10)
    )
    if (!exec) throw new ApiError(404, 'Exécution introuvable')
    return { ...exec }
  },

  async listAllExecutions({ agentId, status, page = 0, size = 10, all = false } = {}) {
    await delay(400)
    let list = [...executions]
    if (!all) {
      list = list.filter((e) => e.requestedBy === 'user-alice')
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
