class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
    this.name = 'ApiError'
  }
}

const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms))

let agents = [
  {
    id: 1,
    name: 'Agent Support Client',
    description: 'Agent dédié au support client pour répondre aux questions fréquentes.',
    status: 'ACTIVE',
    createdAt: '2026-11-10T10:00:00Z',
    updatedAt: '2026-11-15T14:30:00Z',
  },
  {
    id: 2,
    name: 'Agent Analyse Données',
    description: 'Agent pour analyser les données de ventes et générer des rapports.',
    status: 'DRAFT',
    createdAt: '2026-11-12T09:15:00Z',
    updatedAt: '2026-11-16T11:20:00Z',
  },
  {
    id: 3,
    name: 'Agent Recrutement',
    description: 'Agent pour aider au processus de recrutement et présélection des candidats.',
    status: 'ACTIVE',
    createdAt: '2026-11-08T16:45:00Z',
    updatedAt: '2026-11-14T09:00:00Z',
  },
  {
    id: 4,
    name: 'Agent Documentation',
    description: 'Agent pour générer et maintenir la documentation technique.',
    status: 'ARCHIVED',
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
    systemPrompt: 'Tu es un agent de support client amical et professionnel. Réponds aux questions avec précision et empathie.',
    changeLog: 'Version initiale',
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
    systemPrompt: 'Tu es un agent de support client expert. Fournis des réponses détaillées avec des exemples quand nécessaire.',
    changeLog: 'Amélioration du prompt pour plus de détails',
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
    systemPrompt: 'Tu es un analyste de données. Analyse les données fournies et génère des insights.',
    changeLog: 'Version initiale',
    active: false,
    source: 'M5',
    createdAt: '2026-11-12T09:15:00Z',
    configuration: {
      temperature: 0.5,
      maxTokens: 1536,
      maxTurns: 2,
      timeoutSeconds: 60,
      style: 'concise',
      allowedToolIds: [4],
    },
  },
  {
    id: 4,
    agentId: 3,
    label: 'Version 1 (active)',
    versionNumber: 1,
    model: 'gpt-4o',
    systemPrompt: 'Tu es un spécialiste du recrutement. Évalue les profils candidats avec objectivité.',
    changeLog: 'Version initiale',
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
    id: 5,
    agentId: 3,
    label: 'Version 2',
    versionNumber: 2,
    model: 'gpt-4o',
    systemPrompt: 'Tu es un expert RH avec 10 ans d\'expérience. Fournis des évaluations détaillées.',
    changeLog: 'Amélioration du prompt',
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
    id: 6,
    agentId: 4,
    label: 'Version 1 (active)',
    versionNumber: 1,
    model: 'gpt-4o-mini',
    systemPrompt: 'Tu es un rédacteur technique. Génère une documentation claire et structurée.',
    changeLog: 'Version initiale',
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

let nextAgentId = 5
let nextVersionId = 7

export const agentMock = {
  async listAgents() {
    await delay(500)
    return [...agents]
  },

  async getAgent(id) {
    await delay(500)
    const agent = agents.find(a => a.id === parseInt(id))
    if (!agent) throw new ApiError(404, 'Agent introuvable')
    return { ...agent }
  },

  async createAgent(data) {
    await delay(500)
    const newAgent = {
      id: nextAgentId++,
      name: data.name,
      description: data.description || '',
      status: data.status || 'DRAFT',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
    agents.push(newAgent)
    return { ...newAgent }
  },

  async updateAgent(id, data) {
    await delay(500)
    const index = agents.findIndex(a => a.id === parseInt(id))
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
    const index = agents.findIndex(a => a.id === parseInt(id))
    if (index === -1) throw new ApiError(404, 'Agent introuvable')
    agents.splice(index, 1)
  },

  async listVersions(agentId) {
    await delay(500)
    return versions.filter(v => v.agentId === parseInt(agentId)).map(v => ({ ...v }))
  },

  async getVersion(agentId, vid) {
    await delay(500)
    const version = versions.find(v => v.id === parseInt(vid) && v.agentId === parseInt(agentId))
    if (!version) throw new ApiError(404, 'Version introuvable')
    return { ...version }
  },

  async createVersion(agentId, data) {
    await delay(500)
    const agentVersions = versions.filter(v => v.agentId === parseInt(agentId))
    const maxVersionNumber = agentVersions.length > 0 ? Math.max(...agentVersions.map(v => v.versionNumber)) : 0
    
    const newVersion = {
      id: nextVersionId++,
      agentId: parseInt(agentId),
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
    versions.push(newVersion)
    return { ...newVersion }
  },

  async activateVersion(agentId, vid) {
    await delay(500)
    const agentVersions = versions.filter(v => v.agentId === parseInt(agentId))
    const version = agentVersions.find(v => v.id === parseInt(vid))
    if (!version) throw new ApiError(404, 'Version introuvable')
    
    agentVersions.forEach(v => v.active = false)
    version.active = true
    version.label = version.label.replace(' (active)', '') + ' (active)'
    return { ...version }
  },

  async updateConfiguration(agentId, vid, config) {
    await delay(500)
    const version = versions.find(v => v.id === parseInt(vid) && v.agentId === parseInt(agentId))
    if (!version) throw new ApiError(404, 'Version introuvable')
    version.configuration = { ...version.configuration, ...config }
    return { ...version }
  },

  async getTunableParameters(agentId, vid) {
    await delay(500)
    const version = versions.find(v => v.id === parseInt(vid) && v.agentId === parseInt(agentId))
    if (!version) throw new ApiError(404, 'Version introuvable')
    
    return [
      { name: 'temperature', type: 'float', min: 0.0, max: 2.0, current: version.configuration.temperature || 0.7, description: 'Créativité' },
      { name: 'maxTokens', type: 'int', min: 256, max: 4096, current: version.configuration.maxTokens || 1024, description: 'Longueur maximale de la réponse' },
      { name: 'maxTurns', type: 'int', min: 1, max: 10, current: version.configuration.maxTurns || 3, description: 'Nombre maximal d\'appels d\'outils' },
      { name: 'timeoutSeconds', type: 'int', min: 5, max: 120, current: version.configuration.timeoutSeconds || 30, description: 'Délai maximal d\'exécution (secondes)' },
      { name: 'style', type: 'categorical', choices: ['concise', 'detaille'], current: version.configuration.style || 'concise', description: 'Style de réponse' },
    ]
  },

  async executeAgent(agentId, { versionId, input, context }) {
    await delay(500)
    const agent = agents.find(a => a.id === parseInt(agentId))
    if (!agent) throw new ApiError(404, 'Agent introuvable')
    
    if (input === 'error-404') {
      throw new ApiError(404, 'Ressource introuvable')
    }
    if (input === 'error-422') {
      throw new ApiError(422, 'Données invalides')
    }
    if (input === 'error-504') {
      throw new ApiError(504, 'Délai dépassé')
    }
    
    return {
      status: 'COMPLETED',
      output: `Réponse simulée pour l'agent "${agent.name}" avec l'entrée: "${input}". ${context ? `Contexte: ${context}` : ''}`,
      tokensUsed: 150,
      durationMs: 1200,
      versionId: versionId || 1,
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
}
