import { authorizedFetch } from './authToken.js';

const API_URL =
  import.meta.env?.VITE_M3_API_URL ||
  (import.meta.env.DEV ? '/m3-api' : 'http://localhost:8083');
const CHECK_URL = `${API_URL.replace(/\/$/, '')}/api/monitoring/check`;

export async function checkExecutionMetrics(metrics, signal) {
  const response = await authorizedFetch(CHECK_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(metrics),
    signal
  });
  const raw = await response.text();
  let result;

  try {
    result = raw ? JSON.parse(raw) : null;
  } catch {
    throw new Error('Réponse JSON invalide du service Monitoring.');
  }

  if (!response.ok) {
    throw new Error(
      result?.message || `Contrôle Monitoring indisponible : HTTP ${response.status}`
    );
  }
  if (
    !result ||
    typeof result.severity !== 'string' ||
    typeof result.blocking !== 'boolean'
  ) {
    throw new Error('Réponse du service Monitoring invalide.');
  }

  return result;
}
