import { Copy, Save, Trash2, X } from 'lucide-react';
import { useAgentCatalog } from '../../context/AgentCatalogContext';
import { useOrchestrationSettings } from '../../hooks/useOrchestrationSettings';
import { getStepMaxRetries, readSettings } from '../../services/missionApi';

export default function StepInspector({
  selectedNode,
  onUpdate,
  onRemove,
  onDuplicate,
  onClose
}) {
  const { agents: mockAgents, loading, error } = useAgentCatalog();
  const settings = useOrchestrationSettings();
  if (!selectedNode)
    return (
      <div className="settings-placeholder">
        <strong>Configuration des étapes</strong>
        <p>
          Sélectionnez un nœud pour modifier son rôle, ses instructions et ses
          paramètres.
        </p>
      </div>
    );
  const d = selectedNode.data;
  if (selectedNode.type !== 'agentNode')
    return (
      <div className="m3-inspector">
        <div className="panel-head">
          <strong>Configuration du nœud</strong>
          <button onClick={onClose}>✕</button>
        </div>
        <div className="m3-inspector-fields">
          <label className="m3-inspector-label">
            Nom
            <input
              className="field"
              value={d.label || ''}
              onChange={(e) =>
                onUpdate(selectedNode.id, { label: e.target.value })
              }
            />
          </label>
          {selectedNode.type === 'conditionNode' && (
            <>
              <label className="m3-inspector-label">
                Variable JSON (agents réels)
                <input
                  className="field"
                  value={d.variable || ''}
                  onChange={(e) =>
                    onUpdate(selectedNode.id, { variable: e.target.value })
                  }
                />
              </label>
              <label className="m3-inspector-label">
                Opérateur
                <select
                  className="field"
                  value={d.operator || '>='}
                  onChange={(e) => {
                    const operator = e.target.value;
                    onUpdate(
                      selectedNode.id,
                      ['is_true', 'is_false'].includes(operator)
                        ? {
                            operator,
                            expected: 1,
                            sampleValue: d.sampleValue === 0 ? 0 : 1
                          }
                        : { operator }
                    );
                  }}
                >
                  {['=', '!=', '>', '>=', '<', '<=', 'is_true', 'is_false'].map(
                    (op) => (
                      <option key={op}>{op}</option>
                    )
                  )}
                </select>
              </label>
              <label className="m3-inspector-label">
                Valeur attendue
                <input
                  className="field"
                  type="number"
                  step="any"
                  value={d.expected ?? 0.8}
                  onChange={(e) =>
                    onUpdate(selectedNode.id, {
                      expected: Number(e.target.value)
                    })
                  }
                />
              </label>
              <label className="m3-inspector-label">
                Valeur de test (agents simulés)
                <input
                  className="field"
                  type="number"
                  step="any"
                  value={d.sampleValue ?? 0.85}
                  onChange={(e) =>
                    onUpdate(selectedNode.id, {
                      sampleValue: Number(e.target.value)
                    })
                  }
                />
              </label>
              <small className="muted">
                LangGraph évalue cette comparaison. En mode simulé, il utilise
                la valeur de test. En mode réel, indiquez un champ JSON comme
                score ou step-42.score. Pour un booléen valid, choisissez
                is_true : la valeur de test est 1 (vrai) ou 0 (faux). Reliez Non
                à un agent précédent pour une correction limitée par le nombre
                maximal de tentatives.
              </small>
            </>
          )}
        </div>
        <div className="m3-inspector-buttons">
          <button
            className="btn btn-outline"
            onClick={() => onDuplicate(selectedNode.id)}
          >
            Dupliquer
          </button>
          <button
            className="btn btn-outline"
            onClick={() => onRemove(selectedNode.id)}
          >
            Supprimer
          </button>
          <button className="btn btn-primary" onClick={onClose}>
            Terminé
          </button>
        </div>
      </div>
    );
  const agent = mockAgents.find((a) => a.id === d.agentId);
  const field = (label, key, kind = 'input') => (
    <label className="m3-inspector-label" key={key}>
      {label}
      {kind === 'textarea' ? (
        <textarea
          className="field"
          value={d[key] || ''}
          onChange={(e) => onUpdate(selectedNode.id, { [key]: e.target.value })}
        />
      ) : (
        <input
          className="field"
          type={kind === 'number' ? 'number' : 'text'}
          min={kind === 'number' ? 1 : undefined}
          max={key === 'maxRetries' ? settings.maxRetries : undefined}
          value={
            key === 'maxRetries'
              ? getStepMaxRetries(d, settings)
              : (d[key] ?? '')
          }
          onChange={(e) =>
            onUpdate(selectedNode.id, {
              [key]:
                kind === 'number'
                  ? Math.max(
                      1,
                      Math.min(
                        readSettings().maxRetries,
                        Math.trunc(Number(e.target.value)) || 1
                      )
                    )
                  : e.target.value
            })
          }
        />
      )}
    </label>
  );
  return (
    <div className="m3-inspector">
      <div className="panel-head">
        <div>
          <strong>Configuration de l'étape</strong>
          <small>Catalogue fourni par M3</small>
        </div>
        <button className="icon-button" onClick={onClose}>
          <X size={17} />
        </button>
      </div>
      <div className="m3-inspector-fields">
        {field('Nom de l’étape', 'label')}
        <label className="m3-inspector-label">
          Agent M1
          <select
            className="field"
            value={d.agentId}
            onChange={(e) => {
              const a = mockAgents.find((v) => v.id === e.target.value);
              if (!a) return;
              onUpdate(selectedNode.id, {
                agentId: a.id,
                role: a.role,
                agentVersionId: a.versions.find((v) => v.active)?.id || ''
              });
            }}
          >
            <option value="">Choisir un agent</option>
            {!agent && d.agentId && (
              <option value={d.agentId}>Agent absent : {d.agentId}</option>
            )}
            {mockAgents
              .filter(
                (a) => a.status === 'ACTIVE' && a.versions.some((v) => v.active)
              )
              .map((a) => (
                <option value={a.id} key={a.id}>
                  {a.name}
                </option>
              ))}
          </select>
        </label>
        <div className="m3-inspector-quiet">
          {loading
            ? 'Chargement…'
            : error || agent?.name || 'Agent absent du catalogue'}
        </div>
        <label className="m3-inspector-label">
          Version de l’agent
          <select
            className="field"
            value={d.agentVersionId || ''}
            onChange={(e) =>
              onUpdate(selectedNode.id, { agentVersionId: e.target.value })
            }
          >
            <option value="">Choisir une version</option>
            {d.agentVersionId &&
              !agent?.versions.some(
                (v) => v.id === String(d.agentVersionId) && v.active
              ) && (
                <option value={d.agentVersionId}>
                  Version indisponible : {d.agentVersionId}
                </option>
              )}
            {agent?.versions
              .filter((v) => v.active)
              .map((v) => (
                <option key={v.id} value={v.id}>
                  Version {v.versionNumber} · {v.model}
                </option>
              ))}
          </select>
        </label>
        {field('Rôle de l’agent', 'role')}
        {field('Instruction de la tâche', 'instruction', 'textarea')}
        {field('Données d’entrée', 'inputMapping', 'textarea')}
        {field('Sortie attendue', 'expectedOutput', 'textarea')}
        {field('Nombre maximal de tentatives', 'maxRetries', 'number')}
        <small className="muted">
          Limite administrateur : {settings.maxRetries} tentatives.
        </small>
        {getStepMaxRetries(d, settings) > settings.maxRetries && (
          <p role="alert">
            Cette étape dépasse la nouvelle limite. Réduisez le nombre de
            tentatives avant d’enregistrer ou d’exécuter.
            <button
              className="btn btn-outline"
              onClick={() =>
                onUpdate(selectedNode.id, { maxRetries: settings.maxRetries })
              }
            >
              Appliquer la limite de {settings.maxRetries}
            </button>
          </p>
        )}
        <small className="muted">
          Les changements sont immédiatement appliqués au canvas. Enregistrez la
          mission pour les conserver.
        </small>
      </div>
      <div className="m3-inspector-buttons">
        <button
          className="btn btn-outline"
          onClick={() => onDuplicate?.(selectedNode.id)}
        >
          <Copy size={15} />
          Dupliquer
        </button>
        <button
          className="btn btn-outline"
          onClick={() => onRemove?.(selectedNode.id)}
        >
          <Trash2 size={15} />
          Supprimer
        </button>
        <button
          className="btn btn-primary"
          disabled={getStepMaxRetries(d, settings) > settings.maxRetries}
          onClick={onClose}
        >
          <Save size={15} />
          Terminé
        </button>
      </div>
    </div>
  );
}
