class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
    this.name = 'ApiError'
  }
}

function getToken() {
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

  const response = await fetch(url, {
    ...options,
    headers,
  })

  const contentType = response.headers.get('content-type')
  let body = null
  if (contentType?.includes('application/json')) {
    body = await response.json()
  }

  if (!response.ok) {
    throw new ApiError(response.status, getErrorMessage(response.status, body))
  }

  return body
}

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

async function getMock() {
  const { agentMock } = await import('./mocks/agentMock.js')
  return agentMock
}

export async function listAgents() {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.listAgents()
  }
  return request('/api/agents')
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

export async function executeAgent(agentId, { versionId, input, context }) {
  if (USE_MOCK) {
    const mock = await getMock()
    return mock.executeAgent(agentId, { versionId, input, context })
  }
  return request(`/api/agents/${agentId}/execute`, {
    method: 'POST',
    body: JSON.stringify({ versionId, input, context }),
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
