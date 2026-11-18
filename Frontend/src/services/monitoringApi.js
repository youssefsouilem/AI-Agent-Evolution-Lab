import axios from 'axios'

const client = axios.create({
  baseURL: import.meta.env.DEV ? '' : (import.meta.env.VITE_MONITORING_API_URL || 'http://localhost:8086'),
  headers: { 'Content-Type': 'application/json' },
})

client.interceptors.response.use(
  (response) => response,
  (error) => {
    const data = error.response?.data
    const fields = data && typeof data === 'object' ? data : {}
    const message = error.response
      ? Object.values(fields)[0] || error.response.statusText || 'Une erreur est survenue.'
      : 'Service indisponible'
    return Promise.reject({ status: error.response?.status, message, fields })
  },
)

const request = (method, url, data, config) => client({ method, url, data, ...config }).then((res) => res.data)
const crud = (path) => ({
  list: () => request('get', path),
  get: (id) => request('get', `${path}/${id}`),
  create: (data) => request('post', path, data),
  update: (id, data) => request('put', `${path}/${id}`, data),
  remove: (id) => request('delete', `${path}/${id}`),
})

export const anomalyChecksApi = {
  ...crud('/api/anomaly-checks'),
  detected: () => request('get', '/api/anomaly-checks/detected'),
  byAgent: (id) => request('get', `/api/anomaly-checks/agent/${id}`),
  byExecution: (id) => request('get', `/api/anomaly-checks/execution/${id}`),
  range: (from, to) => request('get', '/api/anomaly-checks/range', undefined, { params: { from, to } }),
  summary: () => request('get', '/api/anomaly-checks/summary'),
}

export const alertsApi = {
  ...crud('/api/alerts'),
  byStatus: (status) => request('get', '/api/alerts/status', undefined, { params: { status } }),
  acknowledge: (id) => request('post', `/api/alerts/${id}/acknowledge`),
  resolve: (id) => request('post', `/api/alerts/${id}/resolve`),
}

export const modelVersionsApi = {
  ...crud('/api/model-versions'),
  active: () => request('get', '/api/model-versions/active'),
  activate: (id) => request('post', `/api/model-versions/${id}/activate`),
  deactivate: (id) => request('post', `/api/model-versions/${id}/deactivate`),
}
