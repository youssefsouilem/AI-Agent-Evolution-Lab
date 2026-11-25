import { createContext, useContext, useEffect, useState } from 'react';
import { getAgents } from '../services/missionAgentApi';
const Context = createContext(null);
export function AgentCatalogProvider({ children }) {
  const [agents, setAgents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(''); setAgents([]);
    getAgents(controller.signal).then(setAgents).catch(err => {
      if (!controller.signal.aborted) setError(err.message);
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [revision]);
  return <Context.Provider value={{ agents, loading, error, retry: () => setRevision(v => v + 1) }}>{children}</Context.Provider>;
}
export const useAgentCatalog = () => useContext(Context);
