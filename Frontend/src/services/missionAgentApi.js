import { authorizedFetch } from './authToken.js';

const API_URL =
  import.meta.env?.VITE_M3_API_URL ||
  (import.meta.env.DEV ? '/m3-api' : 'http://localhost:8083');
const CATALOG_URL = `${API_URL.replace(/\/$/, '')}/api/missions/agent-catalog`;

export async function getAgents(signal) {
  const response = await authorizedFetch(CATALOG_URL, { signal });
  const raw = await response.text();
  let agents;

  try {
    agents = raw ? JSON.parse(raw) : null;
  } catch {
    throw new Error('Réponse JSON invalide du catalogue d’agents.');
  }

  if (!response.ok) {
    throw new Error(
      agents?.message ||
        `Catalogue d’agents indisponible : HTTP ${response.status}`
    );
  }
  if (
    !Array.isArray(agents) ||
    agents.some(
      (agent) =>
        agent?.id == null ||
        typeof agent.name !== 'string' ||
        !Array.isArray(agent.versions)
    )
  ) {
    throw new Error('Réponse du catalogue d’agents invalide.');
  }

  return agents;
}
