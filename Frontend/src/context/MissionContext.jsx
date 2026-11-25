import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState
} from 'react';

import * as missionApi from '../api/missionApi';
import {
  activeExecutionStatuses,
  fromExecutionResponse
} from '../utils/execution';
import { fromWorkflowResponse } from '../utils/workflowApi';
import { toWorkflowRequest } from '../utils/workflowApi';

const Ctx = createContext(null);

const getLocalWorkflow = (id) => {
  try {
    return JSON.parse(localStorage.getItem(`m3-workflow-${id}`)) || {};
  } catch {
    return {};
  }
};

const toFrontend = (mission) => {
  const local = getLocalWorkflow(mission.id);

  return {
    ...mission,
    mode: mission.selectionMode === 'AUTOMATIC_AI' ? 'automatic' : 'manual',
    description: mission.objective,
    status: local.status || 'draft',
    nodes: local.nodes || [],
    edges: local.edges || [],
    versions: local.versions || [],
    chatHistory: local.chatHistory || [],
    workflowLoaded: false
  };
};

const toBackend = (mission) => ({
  name: mission.name?.trim() || 'Mission sans titre',
  objective: mission.description?.trim() || 'Mission à définir',
  selectionMode:
    mission.mode === 'AUTOMATIC_AI' || mission.mode === 'automatic'
      ? 'AUTOMATIC_AI'
      : 'MANUAL',
  maxAttempts: mission.maxAttempts || 3,
  ownerId: mission.ownerId || 'user1'
});

export function MissionProvider({ children }) {
  const [missions, setMissions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [executions, setExecutions] = useState([]);
  const [executionError, setExecutionError] = useState('');

  const refreshExecution = useCallback(async (executionId, mission) => {
    const details = await missionApi.getExecutionSteps(executionId);
    const execution = await missionApi.getExecution(executionId);
    const attempts = await missionApi.getExecutionAttempts(executionId);
    const item = fromExecutionResponse(execution, details, mission, attempts);
    setExecutions((previous) =>
      [item, ...previous.filter((run) => run.id !== item.id)].sort(
        (a, b) => Number(b.id) - Number(a.id)
      )
    );
    return item;
  }, []);

  useEffect(() => {
    let stopped = false;
    let timer;
    const terminalCache = new Map();
    const sync = async () => {
      try {
        const lists = await Promise.all(
          missions.map(async (mission) => {
            const records = await missionApi.getMissionExecutions(mission.id);
            return Promise.all(
              records.map(async (execution) => {
                if (terminalCache.has(String(execution.id)))
                  return fromExecutionResponse(
                    execution,
                    terminalCache.get(String(execution.id)).steps,
                    mission,
                    terminalCache.get(String(execution.id)).attemptHistory
                  );
                // GET refreshes the engine snapshot before retrieving step details.
                const current = activeExecutionStatuses.includes(
                  execution.status
                )
                  ? await missionApi.getExecution(execution.id)
                  : execution;
                const details = await missionApi.getExecutionSteps(
                  execution.id
                );
                const attempts = await missionApi.getExecutionAttempts(
                  execution.id
                );
                const item = fromExecutionResponse(
                  current,
                  details,
                  mission,
                  attempts
                );
                if (!activeExecutionStatuses.includes(current.status))
                  terminalCache.set(item.id, item);
                return item;
              })
            );
          })
        );
        if (!stopped) {
          setExecutions((previous) =>
            lists
              .flat()
              .map((item) => {
                const old = previous.find((run) => run.id === item.id);
                return {
                  ...item,
                  monitoring: old?.monitoring
                };
              })
              .sort((a, b) => Number(b.id) - Number(a.id))
          );
          setExecutionError('');
        }
      } catch (err) {
        if (!stopped) setExecutionError(`Suivi indisponible : ${err.message}`);
      } finally {
        if (!stopped) timer = setTimeout(sync, 2000);
      }
    };
    if (!loading) sync();
    return () => {
      stopped = true;
      clearTimeout(timer);
    };
  }, [missions, loading]);

  const startExecution = useCallback(async (mission) => {
    const execution = await missionApi.requestExecution(mission.id);
    const item = fromExecutionResponse(execution, [], mission);
    setExecutions((previous) => [
      item,
      ...previous.filter((run) => run.id !== item.id)
    ]);
    return item;
  }, []);

  const evaluateExecution = useCallback(
    async (executionId, mission) => {
      await missionApi.evaluateExecution(executionId);
      return refreshExecution(executionId, mission);
    },
    [refreshExecution]
  );

  const resumeExecution = useCallback(
    async (executionId, approved, mission) => {
      await missionApi.resumeExecution(executionId, approved);
      return refreshExecution(executionId, mission);
    },
    [refreshExecution]
  );

  const loadMissions = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await missionApi.getMissions();
      const hydrated = await Promise.all(
        data.map(async (item) => {
          const mission = toFrontend(item);
          try {
            const workflow = await missionApi.getWorkflow(item.id);
            return {
              ...mission,
              ...fromWorkflowResponse(workflow, getLocalWorkflow(item.id))
            };
          } catch (err) {
            return { ...mission, workflowError: err.message };
          }
        })
      );
      setMissions(hydrated);
    } catch (err) {
      setError(err.message);
      console.error('Chargement missions :', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadMissions();
  }, [loadMissions]);

  const createMission = useCallback(
    async ({ name, mode, description = '', initialWorkflow }) => {
      console.log('1. React : création demandée', name);

      try {
        const saved = await missionApi.createMission(
          toBackend({ name, mode, description })
        );

        console.log('2. Réponse Spring Boot :', saved);

        const mission = { ...toFrontend(saved), workflowLoaded: true };
        if (initialWorkflow) {
          Object.assign(mission, initialWorkflow);
          try {
            const persisted = await missionApi.saveWorkflow(
              saved.id,
              toWorkflowRequest(initialWorkflow.nodes, initialWorkflow.edges)
            );
            Object.assign(
              mission,
              fromWorkflowResponse(persisted, initialWorkflow)
            );
          } catch (err) {
            mission.workflowNeedsSave = true;
            mission.workflowNotice = `Mission créée, mais le modèle n’a pas été enregistré : ${err.message}. Cliquez sur Enregistrer pour réessayer.`;
          }
          localStorage.setItem(
            `m3-workflow-${saved.id}`,
            JSON.stringify({
              ...initialWorkflow,
              nodes: mission.nodes,
              edges: mission.edges
            })
          );
        }

        setMissions((previous) => [mission, ...previous]);

        return mission;
      } catch (err) {
        console.error('ERREUR API M3 :', err);

        throw err;
      }
    },
    []
  );

  const updateMission = useCallback(
    async (id, patch) => {
      const current = missions.find((m) => String(m.id) === String(id));

      if (!current) {
        throw new Error('Mission introuvable');
      }

      const updated = {
        ...current,
        ...patch,
        updatedAt: new Date().toISOString()
      };

      const workflowData = {
        nodes: updated.nodes,
        edges: updated.edges,
        versions: updated.versions,
        status: updated.status,
        chatHistory: updated.chatHistory
      };

      const businessFields = [
        'name',
        'description',
        'mode',
        'maxAttempts',
        'ownerId'
      ];

      const shouldUpdateBackend = businessFields.some((field) =>
        Object.prototype.hasOwnProperty.call(patch, field)
      );

      if (shouldUpdateBackend) {
        try {
          const saved = await missionApi.updateMission(id, toBackend(updated));

          const result = {
            ...updated,
            ...saved,
            mode: updated.mode,
            description: updated.description,
            ...workflowData
          };

          setMissions((previous) =>
            previous.map((m) => (String(m.id) === String(id) ? result : m))
          );

          localStorage.setItem(
            `m3-workflow-${id}`,
            JSON.stringify(workflowData)
          );
          return result;
        } catch (err) {
          throw err;
        }
      }

      setMissions((previous) =>
        previous.map((m) => (String(m.id) === String(id) ? updated : m))
      );

      localStorage.setItem(`m3-workflow-${id}`, JSON.stringify(workflowData));
      return updated;
    },
    [missions]
  );

  const deleteMission = useCallback(async (id) => {
    try {
      await missionApi.deleteMission(id);

      localStorage.removeItem(`m3-workflow-${id}`);

      setMissions((previous) =>
        previous.filter((m) => String(m.id) !== String(id))
      );
    } catch (err) {
      throw err;
    }
  }, []);

  const duplicateMission = useCallback(async (mission) => {
    try {
      const saved = await missionApi.duplicateMission(mission.id);
      const local = getLocalWorkflow(mission.id);
      const clone = {
        ...toFrontend(saved),
        ...fromWorkflowResponse(await missionApi.getWorkflow(saved.id), local)
      };
      localStorage.setItem(
        `m3-workflow-${saved.id}`,
        JSON.stringify({ ...local, nodes: clone.nodes, edges: clone.edges })
      );

      setMissions((previous) => [clone, ...previous]);

      return clone;
    } catch (err) {
      throw err;
    }
  }, []);

  const updateExecution = useCallback((id, patch) => {
    setExecutions((previous) =>
      previous.map((e) => (e.id === id ? { ...e, ...patch } : e))
    );
  }, []);

  return (
    <Ctx.Provider
      value={{
        missions,
        executions,
        loading,
        error,
        loadMissions,
        createMission,
        updateMission,
        deleteMission,
        duplicateMission,
        startExecution,
        resumeExecution,
        evaluateExecution,
        refreshExecution,
        executionError,
        updateExecution
      }}
    >
      {children}
    </Ctx.Provider>
  );
}

export function useMissions() {
  const context = useContext(Ctx);

  if (!context) {
    throw new Error('MissionProvider absent');
  }

  return context;
}
