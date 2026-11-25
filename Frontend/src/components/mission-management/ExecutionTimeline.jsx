import { CheckCircle2, Circle, LoaderCircle, XCircle } from 'lucide-react';

export default function ExecutionTimeline({
  nodes = [],
  steps = [],
  activeId = null,
  status = 'PENDING'
}) {
  return (
    <div className="timeline">
      {nodes.map((node, index) => {
        const step = steps.find((s) => s.nodeId === node.id);
        const current = activeId === node.id && status === 'RUNNING';
        const state = step?.status || (current ? 'RUNNING' : 'PENDING');
        return (
          <div
            className={`timeline-step ${current ? 'm3-step-active' : ''}`}
            key={node.id}
          >
            <span className={`timeline-marker ${state.toLowerCase()}`}>
              {state === 'COMPLETED' ? (
                <CheckCircle2 size={19} />
              ) : state === 'FAILED' ? (
                <XCircle size={19} />
              ) : current ? (
                <LoaderCircle size={19} />
              ) : (
                <Circle size={19} />
              )}
            </span>
            <div>
              <strong>{node.data.label}</strong>
              <small>
                Étape
                {index + 1}·
                {state === 'SKIPPED' ? 'Ignorée (branche non choisie)' : state}·
                {step?.durationMs ?? '—'}
                ms
              </small>
              <p>
                {node.data.instruction ||
                  (node.type === 'agentNode'
                    ? 'Instruction manquante'
                    : 'Nœud de contrôle')}
              </p>
              {step?.attempts != null && (
                <small>
                  Tentatives : {step.attempts} · Tokens :{' '}
                  {step.tokensUsed ?? '—'}
                </small>
              )}
              {step?.errorMessage && <p role="alert">{step.errorMessage}</p>}
            </div>
          </div>
        );
      })}
      {!nodes.length && <p className="muted">Aucune étape configurée.</p>}
    </div>
  );
}
