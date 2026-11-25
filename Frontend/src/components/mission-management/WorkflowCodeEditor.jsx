import { useState } from 'react';
import {
  assertWorkflowDocument,
  workflowDocument
} from '../../utils/workflowApi';

export default function WorkflowCodeEditor({ nodes, edges, onApply }) {
  const current = JSON.stringify(workflowDocument(nodes, edges), null, 2);
  const [draft, setDraft] = useState(current);
  const [baseline, setBaseline] = useState(current);
  const [error, setError] = useState('');
  const [feedback, setFeedback] = useState('');
  const changedCanvas = current !== baseline;
  const reset = () => {
    setDraft(current);
    setBaseline(current);
    setError('');
    setFeedback('JSON actualisé depuis le canvas.');
  };
  return (
    <section className="m3-code-editor">
      <h2>Définition du workflow</h2>
      <p>
        Modifiez le JSON, puis appliquez-le au canvas. Aucun code n’est exécuté.
      </p>
      {changedCanvas && (
        <p role="status">
          Le canvas a changé. Rechargez son JSON avant d’appliquer vos
          modifications.
        </p>
      )}
      <label htmlFor="workflow-code">Nœuds et connexions</label>
      <textarea
        id="workflow-code"
        spellCheck={false}
        value={draft}
        onChange={(event) => {
          setDraft(event.target.value);
          setError('');
          setFeedback('');
        }}
      />
      {error && (
        <p role="alert" className="m3-error">
          {error}
        </p>
      )}
      {feedback && <p role="status">{feedback}</p>}
      <div className="m3-modal-actions">
        <button className="btn btn-outline" onClick={reset}>
          Recharger depuis le canvas
        </button>
        <button
          className="btn btn-primary"
          disabled={changedCanvas}
          onClick={() => {
            try {
              const graph = assertWorkflowDocument(JSON.parse(draft));
              onApply(graph);
              const applied = JSON.stringify(graph, null, 2);
              setBaseline(applied);
              setDraft(applied);
              setError('');
              setFeedback(
                'JSON appliqué. Enregistrez pour conserver le workflow.'
              );
            } catch (err) {
              setError(err.message);
            }
          }}
        >
          Appliquer au canvas
        </button>
      </div>
    </section>
  );
}
