import { authorizedFetch } from './authToken.js';
const API_URL = (
  import.meta.env?.VITE_M3_API_URL || (import.meta.env.DEV ? '/m3-api' : 'http://localhost:8083')
).replace(/\/$/, '');
const SETTINGS_URL = `${API_URL}/api/orchestration-settings`;

export const defaultSettings = {
  maxRetries: 3,
  maxDuration: 300,
  maxStepDuration: 30,
  maxSteps: 30,
  maxConcurrent: 2,
  loopLimit: 3,
  allowConditions: true,
  requireValidation: true,
  failurePolicy: 'stop'
};
const KEY = 'm3-admin-settings-v1';
export const readSettings = () => {
  try {
    return {
      ...defaultSettings,
      ...JSON.parse(localStorage.getItem(KEY) || '{}')
    };
  } catch {
    return { ...defaultSettings };
  }
};
export const SETTINGS_CHANGED = 'm3-settings-changed';
let writeRevision = 0;
let pendingLoad;

function cacheSettings(value) {
  const settings = { ...defaultSettings, ...value };
  localStorage.setItem(KEY, JSON.stringify(settings));
  window.dispatchEvent(new Event(SETTINGS_CHANGED));
  return settings;
}

async function readSettingsResponse(response) {
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(
      body?.message || `Paramètres indisponibles : HTTP ${response.status}`
    );
  }
  const bounds = {
    maxRetries: [1, 10],
    maxDuration: [10, 3600],
    maxStepDuration: [1, 600],
    maxSteps: [1, 100],
    maxConcurrent: [1, 10]
  };
  if (
    !body ||
    typeof body.allowConditions !== 'boolean' ||
    Object.entries(bounds).some(
      ([key, [min, max]]) =>
        !Number.isInteger(body[key]) || body[key] < min || body[key] > max
    )
  ) {
    throw new Error('Réponse des paramètres invalide.');
  }
  return body;
}

export function loadSettings() {
  if (pendingLoad) return pendingLoad;
  const revision = writeRevision;
  const request = authorizedFetch(SETTINGS_URL)
    .then(readSettingsResponse)
    .then((value) =>
      revision === writeRevision ? cacheSettings(value) : readSettings()
    );
  pendingLoad = request;
  const clear = () => {
    if (pendingLoad === request) pendingLoad = null;
  };
  request.then(clear, clear);
  return request;
}

export async function saveSettings(value) {
  writeRevision++;
  pendingLoad = null;
  const payload = {
    maxRetries: value.maxRetries,
    maxDuration: value.maxDuration,
    maxStepDuration: value.maxStepDuration,
    maxSteps: value.maxSteps,
    maxConcurrent: value.maxConcurrent,
    allowConditions: value.allowConditions
  };
  const response = await authorizedFetch(SETTINGS_URL, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  return cacheSettings(await readSettingsResponse(response));
}

export const subscribeSettings = (listener) => {
  const onStorage = (event) => {
    if (event.key === KEY || event.key === null) listener();
  };
  window.addEventListener(SETTINGS_CHANGED, listener);
  window.addEventListener('storage', onStorage);
  return () => {
    window.removeEventListener(SETTINGS_CHANGED, listener);
    window.removeEventListener('storage', onStorage);
  };
};

// An omitted step value inherits the current administrative limit.
export const getStepMaxRetries = (data, settings = readSettings()) =>
  data.maxRetries ?? settings.maxRetries;

export const validateAttemptLimits = (nodes, settings = readSettings()) =>
  nodes
    .filter((node) => node.type === 'agentNode')
    .flatMap((node) => {
      const attempts = getStepMaxRetries(node.data, settings);
      return Number.isInteger(attempts) &&
        attempts >= 1 &&
        attempts <= settings.maxRetries
        ? []
        : [
            {
              nodeId: node.id,
              message: `« ${node.data.label} » : choisissez entre 1 et ${settings.maxRetries} tentatives (limite administrateur).`
            }
          ];
    });
export const missionApi = {
  mode: 'local-demo',
  futureEndpoint: '/api/m3/missions'
};
