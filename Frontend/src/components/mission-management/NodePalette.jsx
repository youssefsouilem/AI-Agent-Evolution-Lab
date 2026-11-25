import { GitBranch, Flag, Play, RotateCcw } from 'lucide-react';
import { useOrchestrationSettings } from '../../hooks/useOrchestrationSettings';

export default function NodePalette({ onAdd }) {
  const settings = useOrchestrationSettings();
  return (
    <div className="m3-node-palette">
      <p className="muted">LOGIQUE DE WORKFLOW</p>
      {[
        ['startNode', 'Début', Play],
        ['conditionNode', 'Condition If / Else', GitBranch],
        ['endNode', 'Sortie finale', Flag]
      ].map(([id, label, Icon]) => (
        <button
          key={id}
          type="button"
          disabled={id === 'conditionNode' && !settings.allowConditions}
          title={
            id === 'conditionNode' && !settings.allowConditions
              ? 'Désactivé par l’administrateur'
              : label
          }
          onClick={() => onAdd(id)}
        >
          <Icon size={16} />
          {label}
        </button>
      ))}
      <small>
        Les conditions évaluent seulement une valeur fictive, jamais le résultat
        d'une IA.
      </small>
    </div>
  );
}
