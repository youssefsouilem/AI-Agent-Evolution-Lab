import { useEffect, useState } from 'react';
import {
  loadSettings,
  readSettings,
  subscribeSettings
} from '../services/missionApi';

export function useOrchestrationSettings() {
  const [settings, setSettings] = useState(readSettings);
  useEffect(() => {
    const refresh = () => setSettings(readSettings());
    const unsubscribe = subscribeSettings(refresh);
    refresh();
    const load = () =>
      loadSettings().catch((error) => {
        console.error('Chargement des paramètres :', error.message);
      });
    load();
    window.addEventListener('focus', load);
    return () => {
      unsubscribe();
      window.removeEventListener('focus', load);
    };
  }, []);
  return settings;
}
