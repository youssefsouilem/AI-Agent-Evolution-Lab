import { authorizedFetch } from '../services/authToken.js';
const API_URL = import.meta.env?.VITE_API_URL || 'http://localhost:8083';

async function request(path, options = {}) {
  const response = await authorizedFetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers
    }
  });

  if (!response.ok) {
    const raw = await response.text();
    let message = raw;
    try {
      const body = JSON.parse(raw);
      message = body.message || body.detail || body.error || raw;
    } catch {
      /* Non-JSON error response. */
    }
    const error = new Error(`Erreur ${response.status}: ${message}`);
    error.status = response.status;
    throw error;
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

export async function getMissions() {
  const missions = [];
  let page = 0;
  let result;
  do {
    result = await request(`/api/missions?page=${page}&size=100`);
    if (!Array.isArray(result.content))
      throw new Error('Réponse de la liste des missions invalide.');
    missions.push(...result.content);
    page++;
  } while (page < (result.totalPages || 1));
  return missions;
}

export async function createMission(mission) {
  return request('/api/missions', {
    method: 'POST',
    body: JSON.stringify(mission)
  });
}

export async function updateMission(id, mission) {
  return request(`/api/missions/${id}`, {
    method: 'PUT',
    body: JSON.stringify(mission)
  });
}

export async function deleteMission(id) {
  return request(`/api/missions/${id}`, {
    method: 'DELETE'
  });
}

export async function duplicateMission(id) {
  return request(`/api/missions/${id}/duplicate`, {
    method: 'POST'
  });
}

export async function getWorkflow(missionId) {
  return request(`/api/missions/${missionId}/workflow`);
}

export async function saveWorkflow(missionId, workflow) {
  return request(`/api/missions/${missionId}/workflow`, {
    method: 'PUT',
    body: JSON.stringify(workflow)
  });
}

// Register the execution; Spring Boot dispatches it to the orchestration engine.
export async function requestExecution(missionId) {
  return request(`/api/missions/${missionId}/execute`, { method: 'POST' });
}

export async function getExecution(executionId) {
  return request(`/api/executions/${executionId}`);
}

export async function getExecutionSteps(executionId) {
  return request(`/api/executions/${executionId}/steps`);
}

export function getMissionExecutions(missionId) {
  return request(`/api/missions/${missionId}/executions`);
}

export function resumeExecution(executionId, approved) {
  return request(`/api/executions/${executionId}/resume`, {
    method: 'POST',
    body: JSON.stringify({ approved })
  });
}

export function getExecutionAttempts(executionId) {
  return request(`/api/executions/${executionId}/attempts`);
}

export async function generateWorkflow(missionId, instruction) {
  try {
    return await request(`/api/missions/${missionId}/generate`, {
      method: 'POST',
      body: JSON.stringify({ instruction }),
      signal: AbortSignal.timeout(90000)
    });
  } catch (error) {
    if ([404, 405].includes(error.status))
      throw new Error(
        'La génération IA est indisponible sur le backend actuellement démarré. Redémarrez sa dernière version et configurez M1 et l’AI Router. Vous pouvez utiliser un modèle de mission en attendant.'
      );
    if (error.name === 'TimeoutError')
      throw new Error(
        'Le délai de génération IA est dépassé. Votre demande est conservée ; vérifiez le moteur et M1 avant de réessayer.'
      );
    throw error;
  }
}
export function evaluateExecution(executionId) {
  return request(`/api/executions/${executionId}/evaluate`, { method: 'POST' });
}
