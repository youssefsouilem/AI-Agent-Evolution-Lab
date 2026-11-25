import { useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ArrowLeft, Download, Play, RefreshCw } from 'lucide-react';
import { useMissions } from '../../context/MissionContext';
import WorkflowCanvas from './WorkflowCanvas';
import ExecutionTimeline from './ExecutionTimeline';
import ExecutionDiagnostic from './ExecutionDiagnostic';
import { downloadJson } from '../../utils/workflow';
import {
  activeExecutionStatuses,
  executionDuration
} from '../../utils/execution';

export default function ExecutionDetail() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const {
    missions,
    executions,
    loading,
    startExecution,
    resumeExecution,
    refreshExecution,
    evaluateExecution,
    updateExecution,
    executionError
  } = useMissions();
  const [tab, setTab] = useState('run');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const mission = missions.find((item) => String(item.id) === id);
  const records = executions.filter((item) => String(item.missionId) === id);
  const runId = params.get('run');
  const current = runId
    ? records.find((item) => String(item.id) === runId)
    : records[0];
  const ordered = useMemo(
    () =>
      [...(mission?.nodes || [])].sort(
        (a, b) => (a.data.order || 0) - (b.data.order || 0)
      ),
    [mission]
  );
  const active = records.some((item) =>
    activeExecutionStatuses.includes(item.status)
  );
  const status = current?.status || 'PENDING';
  const simulated = current?.metricsSource === 'SIMULATED';
  const duration =
    current?.durationMs ??
    executionDuration(current?.startedAt, new Date().toISOString());

  const perform = async (action) => {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError('');
    try {
      await action();
    } catch (err) {
      setError(err.message);
    } finally {
      lock.current = false;
      setBusy(false);
    }
  };
  const start = () =>
    perform(async () => {
      // Execute the saved workflow. Never replace it implicitly after prior runs.
      if (mission.workflowNeedsSave)
        throw new Error('Enregistrez le workflow dans Studio avant de lancer.');
      const execution = await startExecution(mission);
      navigate(`/executions/${id}?run=${execution.id}`, { replace: true });
    });

  if (loading) return <div className="page">Chargement de la mission…</div>;
  if (!mission) return <div className="page">Mission introuvable.</div>;
  return (
    <div className="studio-page">
      <div className="studio-header">
        <div className="studio-title">
          <button
            className="icon-button"
            aria-label="Retour aux exécutions"
            onClick={() => navigate('/executions')}
          >
            <ArrowLeft size={18} />
          </button>
          <span className="studio-icon">
            <Play size={19} />
          </span>
          <div>
            <div className="studio-breadcrumb">Missions / Exécutions</div>
            <strong>{mission.name}</strong>
          </div>
        </div>
        <div className="studio-actions">
          <button
            className="btn btn-outline"
            disabled={!current || busy}
            onClick={() => perform(() => refreshExecution(current.id, mission))}
          >
            <RefreshCw size={16} />
            Actualiser
          </button>
          <button
            className="btn btn-outline"
            disabled={!current}
            onClick={() =>
              downloadJson(`execution-${current.id}.json`, current)
            }
          >
            <Download size={16} />
            Journal JSON
          </button>
          <button
            className="btn btn-primary"
            disabled={busy || active || !!mission.workflowError}
            onClick={start}
          >
            <Play size={16} />
            {busy ? 'Envoi…' : 'Lancer l’exécution'}
          </button>
        </div>
      </div>
      <div className="studio-tabs">
        {['canvas', 'run'].map((name) => (
          <button
            key={name}
            className={tab === name ? 'tab-active' : ''}
            onClick={() => setTab(name)}
          >
            {name === 'run' ? 'Run' : 'Canvas'}
          </button>
        ))}
      </div>
      {(error || executionError || mission.workflowError) && (
        <div className="m3-panel" role="alert">
          {error || executionError || mission.workflowError}
        </div>
      )}
      {tab === 'canvas' ? (
        <div className="execution-canvas">
          <WorkflowCanvas
            nodes={mission.nodes}
            edges={mission.edges}
            readOnly
            activeNodeId={current?.activeNodeId}
          />
        </div>
      ) : (
        <div className="run-page">
          <div className="run-summary">
            <div>
              <span className="eyebrow">
                {simulated
                  ? 'EXÉCUTION SERVEUR · AGENTS SIMULÉS'
                  : 'SUIVI DE L’EXÉCUTION'}
              </span>
              <h2>{mission.name}</h2>
              <p>
                {simulated
                  ? 'Le moteur utilise des réponses simulées, sans appel à un modèle IA réel.'
                  : 'Le workflow enregistré est exécuté par le backend. Le suivi s’actualise automatiquement.'}
              </p>
              {current && <small>ID : {current.id}</small>}
            </div>
            <span className={`status-pill ${status.toLowerCase()}`}>
              {current ? status : 'Non lancée'}
            </span>
          </div>
          {runId && !current && (
            <p role="status">
              Chargement de l’exécution {runId}… Vérifiez l’identifiant si elle
              n’apparaît pas.
            </p>
          )}
          {current?.errorMessage && (
            <div className="m3-panel" role="alert">
              {current.errorMessage}
            </div>
          )}
          {status === 'RECOVERY_REQUIRED' && (
            <p role="alert">
              La communication avec le moteur est incertaine. Le suivi recherche
              son état sans relancer les agents.
            </p>
          )}
          {status === 'PAUSED' && (
            <section className="run-box">
              <h3>Validation requise</h3>
              <p>Le moteur attend votre décision.</p>
              <button
                className="btn btn-primary"
                disabled={busy}
                onClick={() =>
                  perform(() => resumeExecution(current.id, true, mission))
                }
              >
                Approuver et continuer
              </button>{' '}
              <button
                className="btn btn-outline"
                disabled={busy}
                onClick={() =>
                  perform(() => resumeExecution(current.id, false, mission))
                }
              >
                Refuser
              </button>
            </section>
          )}
          <div
            className="m3-run-progress"
            role="progressbar"
            aria-label="Étapes terminées"
            aria-valuenow={current?.progress || 0}
            aria-valuemin={0}
            aria-valuemax={100}
          >
            <div style={{ width: `${current?.progress || 0}%` }} />
          </div>
          <div className="execution-metric-cards">
            <div>
              <span>Durée {current?.finishedAt ? 'totale' : 'écoulée'}</span>
              <strong>
                {duration == null
                  ? '—'
                  : `${(duration / 1000).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} s`}
              </strong>
            </div>
            <div>
              <span>{simulated ? 'Tokens simulés' : 'Tokens utilisés'}</span>
              <strong>
                {current?.tokensUsed == null
                  ? '—'
                  : current.tokensUsed.toLocaleString('fr-FR')}
              </strong>
            </div>
            <div>
              <span>Appels / tentatives d’agents</span>
              <strong>{current?.attempts ?? '—'}</strong>
            </div>
            <div>
              <span>Étapes traitées</span>
              <strong>
                {current?.steps.filter((step) =>
                  ['COMPLETED', 'SKIPPED'].includes(step.status)
                ).length || 0}{' '}
                / {current?.stepCount || ordered.length}
              </strong>
            </div>
          </div>
          <div className="run-columns">
            <section className="run-box">
              <h3>Suivi des étapes</h3>
              <ExecutionTimeline
                nodes={ordered}
                steps={current?.steps || []}
                activeId={current?.activeNodeId}
                status={status}
              />
            </section>
            <div className="execution-result-column">
              {current?.status === 'COMPLETED' && current?.metricsSource === 'MEASURED' && <section className="run-box">
                <h3>Validation serveur M6</h3>
                <p>Publication : {current.publicationStatus || 'PENDING_MONITORING'}</p>
                {current.monitoringError && <p role="alert">{current.monitoringError}</p>}
                <button className="btn btn-outline" disabled={busy} onClick={() => perform(() => evaluateExecution(current.id, mission))}>Vérifier avec M6</button>
              </section>}
              <ExecutionDiagnostic
                key={current?.id || 'not-started'}
                execution={current}
                onDiagnostic={(diagnostic, failure) =>
                  current &&
                  updateExecution(current.id, {
                    monitoring: diagnostic,
                    monitoringError: failure
                  })
                }
              />
              <section className="run-box">
                <h3>Résultat publié</h3>
                <p style={{ whiteSpace: 'pre-wrap' }}>
                  {current?.finalResult ||
                    (status === 'COMPLETED'
                      ? 'Exécution terminée. Aucun résultat final publié par le serveur.'
                      : 'Le résultat apparaîtra après publication par le serveur.')}
                </p>
                {current?.candidateResult && current?.publicationStatus !== "BLOCKED" && (
                  <details>
                    <summary>
                      Résultat candidat — non publié
                      {simulated ? ' · simulé' : ''}
                    </summary>
                    <pre
                      style={{
                        whiteSpace: 'pre-wrap',
                        overflowWrap: 'anywhere'
                      }}
                    >
                      {current.candidateResult}
                    </pre>
                  </details>
                )}
                <div className="run-log">
                  <strong>
                    Sorties intermédiaires{simulated ? ' simulées' : ''}
                  </strong>
                  {(current?.steps || []).map((step) => (
                    <div key={step.nodeId}>
                      <strong>{step.name}</strong>
                      <p style={{ whiteSpace: 'pre-wrap' }}>
                        {step.errorMessage ||
                          step.result ||
                          'Aucune sortie disponible.'}
                      </p>
                    </div>
                  ))}
                </div>
                <details>
                  <summary>Historique des tentatives ({current?.attemptHistory?.length || 0})</summary>
                  {(current?.attemptHistory || []).map(attempt => <div key={attempt.id} className="run-log">
                    <strong>{attempt.name} — tentative {attempt.attemptNumber} · {attempt.status}</strong>
                    <small>Version : {attempt.agentVersionId} · Tokens : {attempt.tokensUsed ?? '—'}</small>
                    <pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{attempt.errorMessage || attempt.output || 'Aucune sortie'}</pre>
                  </div>)}
                </details>
                <button
                  className="btn btn-outline"
                  onClick={() => navigate(`/studio/${id}`)}
                >
                  Revenir au workflow
                </button>
              </section>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
