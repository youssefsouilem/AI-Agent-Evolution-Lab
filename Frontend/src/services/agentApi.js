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

function getToken() {
  // Placeholder for future Keycloak JWT token retrieval
  return null
}

function getErrorMessage(status, body) {
  if (body?.message) return body.message
  const messages = {
    400: 'Données invalides',
    422: 'Données invalides',
    403: 'Accès refusé',
    404: 'Introuvable',
    409: 'Conflit',
    502: 'Service indisponible (outils ou modèle de langage)',
    504: 'Délai dépassé',
  }
  return messages[status] || 'Une erreur est survenue'
}

async function request(url, options = {}) {
  const token = getToken()
  const headers = {
    'Content-Type': 'application/json',
    ...(token && { Authorization: `Bearer ${token}` }),
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
    throw new ApiError(response.status, getErrorMessage(response.status, body), body)
  }

  return body
}

async function requestWithHeaders(url, options = {}) {
  const token = getToken()
  const headers = {
    'Content-Type': 'application/json',
    ...(token && { Authorization: `Bearer ${token}` }),
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
    throw new ApiError(response.status, getErrorMessage(response.status, body), body)
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
 * Probes GET /api/executions?size=1 once at startup and caches the result.
 * 200 -> executions & overrides are supported.
 * 404, 405, 501 -> not supported.
 * In mock mode, all capabilities are always enabled.
 */
export async function getCapabilities() {
  if (cachedCapabilities !== null) {
    return cachedCapabilities
  }

  if (USE_MOCK) {
    cachedCapabilities = { executions: true, overrides: true, isMock: true }
    return cachedCapabilities
  }

  try {
    const response = await fetch('/api/executions?size=1', {
      headers: { 'Content-Type': 'application/json' },
    })
    if (response.status === 200) {
      cachedCapabilities = { executions: true, overrides: true, isMock: false }
    } else {
      cachedCapabilities = { executions: false, overrides: false, isMock: false }
    }
  } catch {
    cachedCapabilities = { executions: false, overrides: false, isMock: false }
  }

  return cachedCapabilities
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

  const { body, headers } = await requestWithHeaders(`/api/agents/${agentId}/executions?${params.toString()}`)
  const totalCountHeader = headers.get('x-total-count')
  const totalCount = totalCountHeader !== null ? parseInt(totalCountHeader, 10) : (Array.isArray(body) ? body.length : 0)
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
  const totalCount = totalCountHeader !== null ? parseInt(totalCountHeader, 10) : (Array.isArray(body) ? body.length : 0)
  return {
    items: Array.isArray(body) ? body : [],
    totalCount: isNaN(totalCount) ? 0 : totalCount,
  }
}
