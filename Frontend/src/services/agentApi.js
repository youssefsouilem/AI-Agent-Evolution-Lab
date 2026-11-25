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

function getToken() {
  // Placeholder for future Keycloak JWT token retrieval
  return null
}

function getDevHeaders() {
  if (import.meta.env.DEV && import.meta.env.VITE_USE_MOCK !== 'true') {
    try {
      const role = localStorage.getItem('evolution-lab-role') || 'USER'
      return {
        'X-Dev-User': 'dev-user',
        'X-Dev-Role': role === 'ADMIN' ? 'ADMIN' : 'USER',
      }
    } catch {
      return {
        'X-Dev-User': 'dev-user',
        'X-Dev-Role': 'USER',
      }
    }
  }
  return {}
}

function getErrorMessage(status, body) {
  if (body?.message) return body.message

  if (body?.errorCode === 'AGENT_QUOTA_EXCEEDED') {
    return `Limite atteinte : quota d'agents dépassé (limite : ${body.limit || 5}).`
  }
  if (body?.errorCode === 'ADMIN_READ_ONLY') {
    return "Action interdite : vous consultez cet agent en lecture seule."
  }
  if (body?.errorCode === 'RATE_LIMITED') {
    return 'Trop de demandes, réessayez plus tard.'
  }
  if (body?.errorCode === 'ASSIST_INVALID_OUTPUT') {
    return "La proposition générée par l'IA est invalide ou incomplète."
  }
  if (body?.errorCode === 'PROVIDER_NOT_CONFIGURED') {
    return "Le fournisseur du modèle n'est pas configuré sur le serveur."
  }

  const messages = {
    400: 'Données invalides',
    422: 'Données invalides',
    403: 'Accès refusé',
    404: 'Introuvable',
    409: 'Conflit',
    429: 'Trop de demandes, réessayez plus tard',
    502: 'Service indisponible (outils ou modèle de langage)',
    504: 'Délai dépassé',
  }
  return messages[status] || 'Une erreur est survenue'
}

async function request(url, options = {}) {
  const token = getToken()
  const devHeaders = getDevHeaders()

  const headers = {
    'Content-Type': 'application/json',
    ...(token && { Authorization: `Bearer ${token}` }),
    ...devHeaders,
    ...options.headers,
  }

  let response
  try {
    response = await fetch(url, {
      ...options,
      headers,
    })
  } catch {
    throw new ApiError(0, 'Impossible de joindre le serveur M1')
  }

  const text = await response.text()
  let body = null
  const contentType = response.headers.get('content-type') || ''
  if (text && text.trim().length > 0 && contentType.includes('application/json')) {
    try {
      body = JSON.parse(text)
    } catch {
      body = text
    }
  }

  if (!response.ok) {
    const errorCode = body?.errorCode || null
    throw new ApiError(response.status, getErrorMessage(response.status, body), errorCode, body)
  }

  return body
}

async function requestWithHeaders(url, options = {}) {
  const token = getToken()
  const devHeaders = getDevHeaders()

  const headers = {
    'Content-Type': 'application/json',
    ...(token && { Authorization: `Bearer ${token}` }),
    ...devHeaders,
    ...options.headers,
  }

  let response
  try {
    response = await fetch(url, {
      ...options,
      headers,
    })
  } catch {
    throw new ApiError(0, 'Impossible de joindre le serveur M1')
  }

  const text = await response.text()
  let body = null
  const contentType = response.headers.get('content-type') || ''
  if (text && text.trim().length > 0 && contentType.includes('application/json')) {
    try {
      body = JSON.parse(text)
    } catch {
      body = text
    }
  }

  if (!response.ok) {
    const errorCode = body?.errorCode || null
    throw new ApiError(response.status, getErrorMessage(response.status, body), errorCode, body)
  }

  return { body, headers: response.headers }
}

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

async function getMock() {
  const { agentMock } = await import('./mocks/agentMock.js')
  return agentMock
}

let cachedCapabilities = null

/**
 * Feature-detected capabilities of the M1 service.
 * Probes GET /api/me first; if 404, probes GET /api/executions?size=1.
 */
export async function getCapabilities() {
  if (cachedCapabilities !== null) {
    return cachedCapabilities
  }

  if (USE_MOCK) {
    cachedCapabilities = {
      history: true,
      overrides: true,
      assist: true,
      llmCatalog: true,
      stats: true,
      quota: true,
      executions: true,
      isMock: true,
    }
    return cachedCapabilities
  }

  try {
    const meRes = await fetch('/api/me', {
      headers: { 'Content-Type': 'application/json', ...getDevHeaders() },
    })
    if (meRes.status === 200) {
      const data = await meRes.json()
      cachedCapabilities = {
        ...data.capabilities,
        executions: data.capabilities?.history ?? true,
        isMock: false,
      }
      return cachedCapabilities
    }
  } catch {
    // continue to fallback probe
  }

  try {
    const response = await fetch('/api/executions?size=1', {
      headers: { 'Content-Type': 'application/json', ...getDevHeaders() },
    })
    if (response.status === 200) {
      cachedCapabilities = {
        history: true,
        overrides: true,
        assist: false,
        llmCatalog: false,
        stats: false,
        quota: false,
        executions: true,
        isMock: false,
      }
    } else {
      cachedCapabilities = {
        history: false,
        overrides: false,
        assist: false,
        llmCatalog: false,
        stats: false,
        quota: false,
        executions: false,
        isMock: false,
      }
    }
  } catch {
    cachedCapabilities = {
      history: false,
      overrides: false,
      assist: false,
      llmCatalog: false,
      stats: false,
      quota: false,
      executions: false,
      isMock: false,
    }
  }

  return cachedCapabilities
}

export function clearCapabilitiesCache() {
  cachedCapabilities = null
}

export async function getMe() {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.getMe()
  }
  return request('/api/me')
}

export async function listAgents({ all = false } = {}) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.listAgents({ all })
  }
  const query = all ? '?all=true' : ''
  return request(`/api/agents${query}`)
}

export async function getAgent(id) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.getAgent(id)
  }
  return request(`/api/agents/${id}`)
}

export async function createAgent(data) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.createAgent(data)
  }
  return request('/api/agents', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function updateAgent(id, data) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.updateAgent(id, data)
  }
  return request(`/api/agents/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export async function deleteAgent(id) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.deleteAgent(id)
  }
  return request(`/api/agents/${id}`, {
    method: 'DELETE',
  })
}

export async function listVersions(agentId) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.listVersions(agentId)
  }
  return request(`/api/agents/${agentId}/versions`)
}

export async function getVersion(agentId, vid) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.getVersion(agentId, vid)
  }
  return request(`/api/agents/${agentId}/versions/${vid}`)
}

export async function createVersion(agentId, data) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.createVersion(agentId, data)
  }
  return request(`/api/agents/${agentId}/versions`, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function activateVersion(agentId, vid) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.activateVersion(agentId, vid)
  }
  return request(`/api/agents/${agentId}/versions/${vid}/activate`, {
    method: 'PUT',
  })
}

export async function updateConfiguration(agentId, vid, config) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.updateConfiguration(agentId, vid, config)
  }
  return request(`/api/agents/${agentId}/versions/${vid}/configuration`, {
    method: 'PUT',
    body: JSON.stringify(config),
  })
}

export async function getTunableParameters(agentId, vid) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.getTunableParameters(agentId, vid)
  }
  return request(`/api/agents/${agentId}/versions/${vid}/tunable-parameters`)
}

export async function executeAgent(agentId, { versionId, input, context, overrides }) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.executeAgent(agentId, { versionId, input, context, overrides })
  }

  const payload = {
    input,
    ...(versionId ? { versionId: parseInt(versionId, 10) } : {}),
  }

  if (context !== undefined && context !== null && context !== '') {
    payload.context = typeof context === 'string' ? JSON.parse(context) : context
  }

  if (overrides && Object.keys(overrides).length > 0) {
    payload.overrides = overrides
  }

  return request(`/api/agents/${agentId}/execute`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function listTools() {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.listTools()
  }
  try {
    return await request('/api/tools')
  } catch {
    return []
  }
}

export async function listLlmModels() {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.listLlmModels()
  }
  return request('/api/llm/models')
}

export async function assistAgent({ description, language = 'fr' }) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.assistAgent({ description, language })
  }
  return request('/api/agents/assist', {
    method: 'POST',
    body: JSON.stringify({ description, language }),
  })
}

export async function getAgentStats(agentId, days = 7) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.getAgentStats(agentId, days)
  }
  return request(`/api/agents/${agentId}/stats?days=${days}`)
}

export async function adminListUsers() {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.adminListUsers()
  }
  return request('/api/admin/users')
}

export async function adminSetQuota(ownerId, maxAgents) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.adminSetQuota(ownerId, maxAgents)
  }
  return request(`/api/admin/users/${encodeURIComponent(ownerId)}/quota`, {
    method: 'PUT',
    body: JSON.stringify({ maxAgents }),
  })
}

export async function adminArchiveAgent(id, reason) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.adminArchiveAgent(id, reason)
  }
  return request(`/api/admin/agents/${id}/archive`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
}

export async function adminDeleteAgent(id, reason) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.adminDeleteAgent(id, reason)
  }
  return request(`/api/admin/agents/${id}?reason=${encodeURIComponent(reason)}`, {
    method: 'DELETE',
  })
}

export async function adminListAudit(page = 0, size = 20) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.adminListAudit(page, size)
  }
  const { body, headers } = await requestWithHeaders(`/api/admin/audit?page=${page}&size=${size}`)
  const totalCountHeader = headers.get('x-total-count')
  const totalCount =
    totalCountHeader !== null ? parseInt(totalCountHeader, 10) : Array.isArray(body) ? body.length : 0
  return {
    items: Array.isArray(body) ? body : [],
    totalCount: isNaN(totalCount) ? 0 : totalCount,
  }
}

export async function getM1Health() {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.getM1Health()
  }
  try {
    const response = await fetch('/m1-health')
    const text = await response.text()
    let data = null
    try {
      data = JSON.parse(text)
    } catch {
      // not JSON
    }
    if (data?.status === 'UP') {
      return { status: 'UP', raw: data }
    }
    if (data?.status === 'DOWN') {
      return { status: 'DOWN', raw: data }
    }
    return { status: response.ok ? 'UP' : 'DOWN', raw: data }
  } catch {
    return { status: 'UNREACHABLE' }
  }
}

export async function listExecutions(agentId, { status, versionId, page = 0, size = 10 } = {}) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.listExecutions(agentId, { status, versionId, page, size })
  }
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) params.append('status', status)
  if (versionId) params.append('versionId', String(versionId))

  const { body, headers } = await requestWithHeaders(
    `/api/agents/${agentId}/executions?${params.toString()}`
  )
  const totalCountHeader = headers.get('x-total-count')
  const totalCount =
    totalCountHeader !== null ? parseInt(totalCountHeader, 10) : Array.isArray(body) ? body.length : 0
  return {
    items: Array.isArray(body) ? body : [],
    totalCount: isNaN(totalCount) ? 0 : totalCount,
  }
}

export async function getExecution(agentId, executionId) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.getExecution(agentId, executionId)
  }
  return request(`/api/agents/${agentId}/executions/${executionId}`)
}

export async function listAllExecutions({ agentId, status, page = 0, size = 10, all = false } = {}) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.listAllExecutions({ agentId, status, page, size, all })
  }
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (agentId) params.append('agentId', String(agentId))
  if (status) params.append('status', status)
  if (all) params.append('all', 'true')

  const { body, headers } = await requestWithHeaders(`/api/executions?${params.toString()}`)
  const totalCountHeader = headers.get('x-total-count')
  const totalCount =
    totalCountHeader !== null ? parseInt(totalCountHeader, 10) : Array.isArray(body) ? body.length : 0
  return {
    items: Array.isArray(body) ? body : [],
    totalCount: isNaN(totalCount) ? 0 : totalCount,
  }
}
