import { useEffect, useState } from 'react';
import ConfirmDialog from '../../../components/common/ConfirmDialog';
import {
  defaultSettings,
  loadSettings,
  readSettings,
  saveSettings
} from '../../../services/missionApi';

const fields = [
  [
    'maxRetries',
    'Nombre maximal de tentatives',
    1,
    10,
    'Nombre total de passages autorisés par agent, premier passage compris.'
  ],
  [
    'maxDuration',
    'Durée maximale d’une mission (s)',
    10,
    3600,
    'Durée maximale autorisée pour une mission.'
  ],
  [
    'maxStepDuration',
    'Durée maximale d’une étape (s)',
    1,
    600,
    'Durée maximale autorisée pour un appel agent.'
  ],
  [
    'maxSteps',
    'Nombre maximal d’étapes',
    1,
    100,
    'Bloque les workflows trop grands.'
  ],
  [
    'maxConcurrent',
    'Agents en parallèle',
    1,
    10,
    'Plafond d’agents exécutés en parallèle dans un groupe.'
  ]
];
export default function ParametresOrchestration() {
  const [data, setData] = useState(readSettings);
  const [pending, setPending] = useState(null);
  const [feedback, setFeedback] = useState('');
  const [savedData, setSavedData] = useState(readSettings);
  const [loading, setLoading] = useState(true);
  const [ready, setReady] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setReady(false);
    setError('');
    loadSettings()
      .then((settings) => {
        if (!active) return;
        setData(settings);
        setSavedData(settings);
        setReady(true);
      })
      .catch((err) => {
        if (active) setError(err.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [reload]);
  const dirty = JSON.stringify(data) !== JSON.stringify(savedData);
  const change = (key, value) => setData((v) => ({ ...v, [key]: value }));
  const save = async (value = data) => {
    if (!ready || saving) return;
    setSaving(true);
    setError('');
    setFeedback('');
    try {
      const settings = await saveSettings(value);
      setData(settings);
      setSavedData(settings);
      setPending(null);
      setFeedback('Paramètres enregistrés dans le backend.');
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <div className="page">
      <div className="page-heading">
        <div>
          <div className="breadcrumb">M3 / Administration</div>
          <h1>Paramètres d'orchestration</h1>
          <p>
            Les paramètres sont enregistrés dans le backend et partagés entre
            les utilisateurs.
          </p>
        </div>
      </div>
      {loading && <p role="status">Chargement des paramètres…</p>}
      {error && (
        <div role="alert">
          <p>{error}</p>
          {!ready && (
            <button onClick={() => setReload((v) => v + 1)}>Réessayer</button>
          )}
        </div>
      )}
      {feedback && (
        <p role="status" className="m3-toast" onClick={() => setFeedback('')}>
          {feedback}×
        </p>
      )}
      <div className="m3-panel m3-settings-grid">
        {fields.map(([key, label, min, max, hint]) => (
          <label className="m3-setting" key={key}>
            <strong>{label}</strong>
            <small>{hint}</small>
            <input
              className="field"
              type="number"
              disabled={!ready || saving}
              min={min}
              max={max}
              value={data[key]}
              onChange={(e) =>
                change(
                  key,
                  Math.max(
                    min,
                    Math.min(max, Math.trunc(Number(e.target.value)) || min)
                  )
                )
              }
            />
          </label>
        ))}
        <label className="m3-setting">
          <strong>Branches conditionnelles</strong>
          <small>Autoriser les nœuds If / Else exécutés par le moteur.</small>
          <input
            type="checkbox"
            checked={data.allowConditions}
            disabled={!ready || saving}
            onChange={(e) => change('allowConditions', e.target.checked)}
          />
        </label>
        <label className="m3-setting">
          <strong>Validation avant Run</strong>
          <small>
            Le backend valide obligatoirement le workflow avant son exécution.
          </small>
          <input
            type="checkbox"
            disabled
            checked
            onChange={(e) => change('requireValidation', e.target.checked)}
          />
        </label>
        <label className="m3-setting">
          <strong>Gestion des échecs</strong>
          <small>
            Un appel agent en échec arrête la mission. Une correction ne reprend
            que par une connexion de retour explicitement configurée et bornée.
          </small>
          <select
            className="field"
            disabled
            value="stop"
            onChange={(e) => change('failurePolicy', e.target.value)}
          >
            <option value="stop">Arrêter la mission</option>
            <option value="continue">Continuer les étapes possibles</option>
          </select>
        </label>
      </div>
      <div className="m3-setting-actions">
        <p role="status">
          {dirty
            ? `Modifications non enregistrées. Limite active : ${savedData.maxRetries} tentatives.`
            : `Limite active : ${savedData.maxRetries} tentatives.`}
        </p>
        <button
          className="btn btn-outline"
          disabled={!ready || saving}
          onClick={() => setPending('reset')}
        >
          Restaurer par défaut
        </button>
        <button
          className="btn btn-primary"
          disabled={!ready || saving || !dirty}
          onClick={() => save()}
        >
          {saving ? 'Enregistrement…' : 'Enregistrer les paramètres'}
        </button>
      </div>
      <ConfirmDialog
        open={!!pending}
        danger={pending === 'reset'}
        title={
          pending === 'reset'
            ? 'Restaurer les valeurs par défaut ?'
            : 'Enregistrer les paramètres ?'
        }
        message="Cette action remplacera les paramètres enregistrés dans le backend."
        confirmLabel="Confirmer"
        onCancel={() => {
          if (!saving) setPending(null);
        }}
        onConfirm={() => save(defaultSettings)}
      />
    </div>
  );
}
