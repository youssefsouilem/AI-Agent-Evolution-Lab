import { saveWorkflow, generateWorkflow } from '../../api/missionApi';
import {
  fromWorkflowResponse,
  toWorkflowRequest,
  assertWorkflowDocument,
  workflowDocument
} from '../../utils/workflowApi';
import WorkflowCodeEditor from './WorkflowCodeEditor';
import {
  missionTemplates,
  createTemplateWorkflow
} from '../../data/missionTemplates';
import { useEffect, useRef, useState } from 'react';

import { useNavigate, useParams } from 'react-router-dom';

import {
  ArrowLeft,
  Check,
  Download,
  FileUp,
  Play,
  Redo2,
  Save,
  Undo2,
  Workflow,
  Sparkles
} from 'lucide-react';

import { useMissions } from '../../context/MissionContext';

import { useAgentCatalog } from '../../context/AgentCatalogContext';

import AgentSelector from './AgentSelector';
import WorkflowCanvas from './WorkflowCanvas';
import StepInspector from './StepInspector';

import {
  demoGenerateWorkflow,
  resolveAgentRole,
  downloadJson,
  makeNode,
  uuid
} from '../../utils/workflow';

import {
  validateWorkflow,
  validateOrchestration
} from '../../utils/validation';

import { useWorkflowHistory } from '../../hooks/useWorkflowHistory';

import ConfirmDialog from '../common/ConfirmDialog';
import RenameMissionDialog from './RenameMissionDialog';
import Modal from '../common/Modal';

import {
  loadSettings,
  readSettings,
  validateAttemptLimits
} from '../../services/missionApi';

export default function MissionEditor({ mode }) {
  const {
    agents: mockAgents,
    loading: agentsLoading,
    error: agentsError
  } = useAgentCatalog();
  const { id } = useParams();

  const navigate = useNavigate();
  const generationLock = useRef(false);
  const [generating, setGenerating] = useState(false);

  const { missions, updateMission, loading } = useMissions();

  const mission = missions.find((m) => String(m.id) === String(id));

  const { graph, commit, undo, redo, replace, canUndo, canRedo } =
    useWorkflowHistory(mission?.nodes || [], mission?.edges || []);

  const { nodes, edges } = graph;

  const [selectedId, setSelectedId] = useState(null);

  const [saved, setSaved] = useState(true);
  const [saving, setSaving] = useState(false);
  const saveLock = useRef(false);
  const [view, setView] = useState('canvas');
  const savedGraph = useRef(
    JSON.stringify(workflowDocument(graph.nodes, graph.edges))
  );
  const dirty =
    JSON.stringify(workflowDocument(graph.nodes, graph.edges)) !==
    savedGraph.current;

  const [messages, setMessages] = useState(mission?.chatHistory || []);

  const [draft, setDraft] = useState('');

  const [pending, setPending] = useState(null);

  const [started, setStarted] = useState(Boolean(mission?.nodes?.length));

  const [issues, setIssues] = useState([]);

  const [tab, setTab] = useState('canvas');

  const [confirm, setConfirm] = useState(null);

  const [renameOpen, setRenameOpen] = useState(false);

  const [errorMessage, setErrorMessage] = useState('');

  const upload = useRef(null);

  const initializedMission = useRef(null);

  // Initialiser le canvas lorsqu'une mission est chargée

  useEffect(() => {
    if (!mission || !mission.workflowLoaded) {
      return;
    }

    if (initializedMission.current === String(mission.id)) {
      return;
    }

    initializedMission.current = String(mission.id);

    replace({
      nodes: mission.nodes || [],
      edges: mission.edges || []
    });

    savedGraph.current = mission.workflowNeedsSave
      ? ''
      : JSON.stringify(
          workflowDocument(mission.nodes || [], mission.edges || [])
        );
    setStarted(Boolean(mission.nodes?.length));

    setMessages(mission.chatHistory || []);

    setSelectedId(null);

    setIssues([]);
  }, [mission, replace]);

  useEffect(() => {
    const warn = (event) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  // Modifier les noeuds

  const setNodes = (updater) => {
    commit((g) => ({
      ...g,
      nodes: typeof updater === 'function' ? updater(g.nodes) : updater
    }));

    setSaved(false);
  };

  // Modifier les connexions

  const setEdges = (updater) => {
    commit((g) => ({
      ...g,
      edges: typeof updater === 'function' ? updater(g.edges) : updater
    }));

    setSaved(false);
  };

  // Selectionner un noeud

  const selectNode = (nodeId) => {
    setSelectedId(nodeId);
  };

  // Ajouter un agent

  const addAgent = (agentId) => {
    const node = {
      ...makeNode(
        agentId,
        nodes.length,
        {
          x: 95 + nodes.length * 260,
          y: 150 + (nodes.length % 3) * 75
        },
        mockAgents
      ),

      id: `node-${uuid()}`
    };

    setNodes((previous) => [...previous, node]);

    setSelectedId(node.id);
  };

  // Ajouter un noeud logique

  const addLogic = (type) => {
    const nodeId = `node-${uuid()}`;

    const labels = {
      startNode: 'Début',
      endNode: 'Sortie finale',
      conditionNode: 'Condition If / Else'
    };

    const node = {
      id: nodeId,

      type,

      position: {
        x: 120 + nodes.length * 225,
        y: 130 + (nodes.length % 3) * 100
      },

      data: {
        label: labels[type],
        variable: 'score',
        operator: '>=',
        expected: 0.8,
        sampleValue: 0.85
      }
    };

    setNodes((previous) => [...previous, node]);

    setSelectedId(nodeId);
  };

  // Modifier une etape

  const updateStep = (nodeId, patch) => {
    setNodes((previous) =>
      previous.map((node) =>
        node.id === nodeId
          ? {
              ...node,
              data: {
                ...node.data,
                ...patch
              }
            }
          : node
      )
    );
  };

  // Supprimer une etape

  const removeStep = (nodeId) => {
    commit((g) => ({
      nodes: g.nodes.filter((node) => node.id !== nodeId),

      edges: g.edges.filter(
        (edge) => edge.source !== nodeId && edge.target !== nodeId
      )
    }));

    setSelectedId(null);

    setSaved(false);
  };

  // Dupliquer une etape

  const duplicateStep = (nodeId) => {
    const old = nodes.find((node) => node.id === nodeId);

    if (!old) {
      return;
    }

    const node = {
      ...structuredClone(old),

      id: `node-${uuid()}`,

      position: {
        x: old.position.x + 75,
        y: old.position.y + 110
      },

      data: {
        ...old.data,
        label: `${old.data.label} (copie)`
      }
    };

    setNodes((previous) => [...previous, node]);

    setSelectedId(node.id);
  };

  // Enregistrer le workflow local

  const save = async () => {
    setSaved(false);
    setErrorMessage('');
    if (saveLock.current) return false;
    saveLock.current = true;
    setSaving(true);
    try {
      const settings = await loadSettings();
      const attemptIssues = validateOrchestration(nodes, edges, settings);
      if (attemptIssues.length) {
        setIssues(attemptIssues);
        setTab('canvas');
        return false;
      }
      const workflow = toWorkflowRequest(nodes, edges, settings);

      const persisted = await saveWorkflow(id, workflow);
      const confirmed = fromWorkflowResponse(persisted, {
        chatHistory: messages
      });

      const versions = [
        ...(mission.versions || []),
        {
          id: uuid(),
          date: new Date().toISOString(),
          nodes: structuredClone(confirmed.nodes),
          edges: structuredClone(confirmed.edges)
        }
      ].slice(-12);

      await updateMission(id, {
        nodes: confirmed.nodes,
        edges: confirmed.edges,
        versions,
        chatHistory: messages,
        workflowNeedsSave: false,
        workflowNotice: ''
      });

      replace({ nodes: confirmed.nodes, edges: confirmed.edges });
      setSelectedId(null);
      savedGraph.current = JSON.stringify(
        workflowDocument(confirmed.nodes, confirmed.edges)
      );
      setSaved(true);

      return true;
    } catch (error) {
      console.error('Erreur sauvegarde workflow :', error);

      setErrorMessage(`Impossible d'enregistrer : ${error.message}`);

      return false;
    } finally {
      saveLock.current = false;
      setSaving(false);
    }
  };

  // Préparer le workflow enregistré pour une exécution serveur

  const run = async () => {
    let settings;
    try {
      settings = await loadSettings();
    } catch (error) {
      setErrorMessage(
        `Impossible de vérifier les paramètres : ${error.message}`
      );
      return;
    }

    const errors = [
      ...validateWorkflow(nodes, edges, mockAgents, { server: true }),
      ...validateOrchestration(nodes, edges, settings)
    ];

    setIssues(errors);

    if (errors.length > 0) {
      setTab('canvas');

      return;
    }

    const success = !dirty || (await save());

    if (success) {
      navigate(`/executions/${id}`);
    }
  };

  // Envoyer une commande au simulateur

  const request = async (text) => {
    if (generationLock.current) return;
    if (agentsLoading || agentsError) {
      setErrorMessage(agentsError || 'Chargement du catalogue…');
      return;
    }
    setErrorMessage('');
    try {
      const trimmed = text.trim();

      if (!trimmed) {
        return;
      }

      setDraft('');

      setStarted(true);

      setMessages((previous) => [
        ...previous,
        {
          role: 'user',
          text: trimmed
        }
      ]);

      if (mode !== 'manual') {
        generationLock.current = true;
        setGenerating(true);
        try {
          const plan = await generateWorkflow(id, trimmed);
          const graph = fromWorkflowResponse({
            steps: plan.steps.map((step) => ({
              ...step,
              id: step.stepOrder + 1
            })),
            edges: plan.edges.map((edge, index) => ({
              ...edge,
              id: index + 1,
              sourceStepId: edge.sourceStepOrder + 1,
              targetStepId: edge.targetStepOrder + 1
            }))
          });
          setPending({
            kind: 'generate',
            label: 'Appliquer la proposition AI Router validée par M3',
            payload: graph
          });
        } finally {
          generationLock.current = false;
          setGenerating(false);
        }
        return;
      }
      let change = null;

      const low = trimmed.toLowerCase();

      if (!nodes.length) {
        change = {
          kind: 'generate',

          label:
            'Proposer un workflow séquentiel de démonstration à partir de mots-clés (sans IA)',

          payload: demoGenerateWorkflow(trimmed, mockAgents)
        };
      } else if (/ajout|ins[eè]re|cr[eé]e/.test(low)) {
        const agentId = /v[eé]rifi|contr[oô]le|qualit[eé]/.test(low)
          ? 'reviewer'
          : /rapport|r[eé]dig|conclus/.test(low)
            ? 'writer'
            : /recherch/.test(low)
              ? 'research'
              : null;

        if (agentId) {
          const agent = resolveAgentRole(agentId, mockAgents);

          change = {
            kind: 'add',

            label: `Ajouter une étape « ${agent?.name || agentId} »`,

            agentId: agent.id
          };
        }
      } else if (/supprim|retir/.test(low) && /recherch/.test(low)) {
        const target = nodes.find((node) =>
          /recherch/i.test(node.data?.role || '')
        );

        if (target) {
          change = {
            kind: 'remove',

            label: `Supprimer l'étape « ${target.data.label} » et ses connexions`,

            nodeId: target.id
          };
        }
      }

      setPending(change);

      if (!change) {
        setMessages((previous) => [
          ...previous,

          {
            role: 'assistant',
            text: 'Commande non reconnue par la simulation. Exemples : « Ajoute un agent vérificateur », « Supprime l’étape de recherche », « Ajoute un rapport final ». Aucune IA réelle n’est connectée.'
          }
        ]);
      }
    } catch (error) {
      setDraft(text);
      setMessages((previous) => [
        ...previous,
        { role: 'assistant', text: error.message }
      ]);
      setErrorMessage(error.message);
    }
  };

  const chooseTemplate = (template) => {
    try {
      setStarted(true);
      setDraft((previous) =>
        previous.trim()
          ? previous
          : `Créer une mission : ${template.name.toLowerCase()}.`
      );
      setPending({
        kind: 'generate',
        label: `Utiliser le modèle « ${template.name} » avec les agents de démonstration`,
        payload: createTemplateWorkflow(template.id, mockAgents)
      });
    } catch (error) {
      setErrorMessage(error.message);
    }
  };

  const templateChoices = (
    <div className="m3-chat-templates">
      <span>Partir d’un modèle de mission</span>
      <div>
        {missionTemplates.map((template) => (
          <button
            type="button"
            key={template.id}
            disabled={!!pending}
            onClick={() => chooseTemplate(template)}
          >
            <strong>{template.name}</strong>
            <small>{template.description}</small>
          </button>
        ))}
      </div>
      <small>
        Agents de démonstration · Confirmez la proposition, puis personnalisez
        le canvas.
      </small>
    </div>
  );

  // Appliquer une proposition simulee

  const applyPending = () => {
    if (!pending) {
      return;
    }

    if (pending.kind === 'generate') {
      commit(pending.payload);
    } else if (pending.kind === 'add') {
      const node = {
        ...makeNode(
          pending.agentId,
          nodes.length,
          {
            x: 120 + nodes.length * 290,
            y: 155
          },
          mockAgents
        ),

        id: `node-${uuid()}`
      };

      commit((g) => ({
        ...g,

        nodes: [...g.nodes, node],

        edges: g.nodes.length
          ? [
              ...g.edges,
              {
                id: uuid(),
                source: g.nodes[g.nodes.length - 1].id,
                target: node.id
              }
            ]
          : g.edges
      }));
    } else if (pending.kind === 'remove') {
      removeStep(pending.nodeId);
    }

    setMessages((previous) => [
      ...previous,

      {
        role: 'assistant',
        text: `Modification simulée confirmée : ${pending.label}. Vérifiez le canvas avant toute exécution.`
      }
    ]);

    setPending(null);

    setSaved(false);
  };

  // Importer un workflow JSON

  const importJson = async (event) => {
    const file = event.target.files?.[0];

    event.target.value = '';

    if (!file) {
      return;
    }

    try {
      const obj = JSON.parse(await file.text());

      assertWorkflowDocument(obj);

      setConfirm({
        title: 'Importer un workflow ?',

        message: 'Le canvas actuel sera remplacé par le contenu JSON validé.',

        action: () => {
          commit({
            nodes: obj.nodes,
            edges: obj.edges
          });

          setSaved(false);

          setSelectedId(null);

          setIssues([]);
        }
      });
    } catch (error) {
      setErrorMessage(`Import refusé : ${error.message}`);
    }
  };

  // Restaurer une version

  const restore = (version) => {
    setConfirm({
      title: 'Restaurer cette version ?',

      message: 'Le canvas actuel sera remplacé par cette version locale.',

      action: () => {
        commit({
          nodes: version.nodes,
          edges: version.edges
        });

        setSaved(false);

        setIssues([]);
      }
    });
  };

  // Affichage pendant chargement

  if (loading) {
    return (
      <div className="page">
        <h2>Chargement de la mission...</h2>
      </div>
    );
  }

  if (mission?.workflowError)
    return (
      <div className="page">
        <h2>Workflow indisponible</h2>
        <p role="alert">{mission.workflowError}</p>
        <button
          className="btn btn-outline"
          onClick={() => window.location.reload()}
        >
          Réessayer
        </button>
      </div>
    );

  // Mission inexistante

  if (!mission) {
    return (
      <div className="page">
        <h2>Mission introuvable</h2>

        <button
          className="btn btn-primary"
          onClick={() => navigate('/missions')}
        >
          Retour
        </button>
      </div>
    );
  }

  // Editeur visuel

  const editor = (
    <div
      className={`studio-layout m3-editor-layout ${
        mode === 'automatic' ? 'm3-editor-automatic' : ''
      }`}
    >
      {mode === 'manual' && (
        <AgentSelector onAdd={addAgent} onAddLogic={addLogic} />
      )}

      <WorkflowCanvas
        nodes={nodes}
        edges={edges}
        setNodes={setNodes}
        setEdges={setEdges}
        onSelectNode={selectNode}
        onReset={() => {
          setConfirm({
            title: 'Réinitialiser le canvas ?',

            message:
              'Les étapes et connexions seront retirées de votre éditeur. Pensez à enregistrer si nécessaire.',

            action: () => {
              commit({
                nodes: [],
                edges: []
              });

              setSelectedId(null);

              setSaved(false);
            }
          });
        }}
      />

      {selectedId && (
        <aside className="settings-panel">
          <StepInspector
            selectedNode={nodes.find((node) => node.id === selectedId)}
            onUpdate={updateStep}
            onRemove={removeStep}
            onDuplicate={duplicateStep}
            onClose={() => setSelectedId(null)}
          />
        </aside>
      )}
    </div>
  );

  return (
    <div className="studio-page">
      <div className="studio-header">
        <div className="studio-title">
          <button className="icon-button" onClick={() => navigate('/missions')}>
            <ArrowLeft size={19} />
          </button>

          <span className="studio-icon">
            {mode === 'manual' ? (
              <Workflow size={19} />
            ) : (
              <Sparkles size={19} />
            )}
          </span>

          <div>
            <div className="studio-breadcrumb">
              Mission Studio /
              {mode === 'manual'
                ? ' Flux de travail'
                : ' Automatique IA · démo'}
            </div>

            <strong>{mission.name}</strong>
          </div>
        </div>

        <div className="studio-actions">
          <button
            className="btn btn-outline"
            title="Renommer"
            onClick={() => setRenameOpen(true)}
          >
            Renommer
          </button>

          <button
            className="btn btn-outline"
            onClick={undo}
            disabled={!canUndo || saving}
          >
            <Undo2 size={16} />
            Annuler
          </button>

          <button
            className="btn btn-outline"
            onClick={redo}
            disabled={!canRedo || saving}
          >
            <Redo2 size={16} />
            Rétablir
          </button>

          <button
            className="btn btn-outline"
            onClick={() => upload.current?.click()}
          >
            <FileUp size={16} />
            Importer
          </button>

          <input
            ref={upload}
            type="file"
            accept="application/json,.json"
            hidden
            onChange={importJson}
          />

          <button
            className="btn btn-outline"
            onClick={() => {
              downloadJson(`${mission.name.replaceAll(' ', '-')}.json`, {
                name: mission.name,
                mode,
                nodes,
                edges
              });
            }}
          >
            <Download size={16} />
            JSON
          </button>

          <button className="btn btn-outline" onClick={save} disabled={saving}>
            {!dirty ? <Check size={16} /> : <Save size={16} />}
            {saving
              ? 'Enregistrement…'
              : dirty
                ? 'Enregistrer les modifications'
                : 'Enregistrer'}
          </button>

          <button className="btn btn-primary" onClick={run} disabled={saving}>
            <Play size={16} />
            Run
          </button>
        </div>
      </div>

      <div className="studio-tabs">
        <button
          className={tab === 'canvas' ? 'tab-active' : ''}
          onClick={() => setTab('canvas')}
        >
          Canvas
        </button>

        <button onClick={run}>Run</button>

        <button
          className={tab === 'versions' ? 'tab-active' : ''}
          onClick={() => setTab('versions')}
        >
          Versions ({(mission.versions || []).length})
        </button>
      </div>

      <p className="muted m3-storage-notice">
        Workflow et configuration des étapes enregistrés sur le serveur. Les
        versions et le chat restent dans ce navigateur.
      </p>
      {mission.workflowNotice && (
        <p role="status" className="m3-storage-notice">
          {mission.workflowNotice}
        </p>
      )}
      {tab === 'canvas' && (
        <div className="studio-tabs" aria-label="Vue du workflow">
          {['canvas', 'code', 'split'].map((option) => (
            <button
              key={option}
              className={view === option ? 'tab-active' : ''}
              onClick={() => setView(option)}
            >
              {option === 'canvas'
                ? 'Canvas'
                : option === 'code'
                  ? 'Code JSON'
                  : 'Canvas + Code'}
            </button>
          ))}
        </div>
      )}
      {issues.length > 0 && (
        <div className="m3-errors">
          <strong>Validation impossible : {issues.length} problème(s)</strong>

          {issues.map((issue, index) => (
            <button
              key={index}
              onClick={() => {
                setTab('canvas');

                setSelectedId(issue.nodeId);
              }}
            >
              {issue.message}
            </button>
          ))}
        </div>
      )}

      {tab === 'versions' ? (
        <div className="page">
          <h2>Versions enregistrées localement</h2>

          {(mission.versions || [])
            .slice()
            .reverse()
            .map((version) => (
              <div className="m3-list-row" key={version.id}>
                <span>
                  {new Date(version.date).toLocaleString('fr-FR')}
                  {' · '}
                  {version.nodes.length} étapes
                </span>

                <button
                  className="btn btn-outline"
                  onClick={() => restore(version)}
                >
                  Restaurer
                </button>
              </div>
            ))}

          {!(mission.versions || []).length && (
            <p className="muted">
              Enregistrez votre mission pour créer une version locale.
            </p>
          )}
        </div>
      ) : view !== 'canvas' ? (
        <div className={view === 'split' ? 'm3-code-split' : 'm3-code-only'}>
          {view === 'split' && editor}
          <WorkflowCodeEditor
            nodes={nodes}
            edges={edges}
            onApply={(next) => {
              commit(next);
              setSaved(false);
              setSelectedId(null);
              setIssues([]);
              setStarted(true);
            }}
          />
        </div>
      ) : mode === 'manual' ? (
        editor
      ) : !started ? (
        <div className="m3-chat-intro">
          <div className="m3-chat-intro-inner">
            <span className="m3-chat-spark">
              <Sparkles size={26} />
            </span>

            <h1>Que souhaitez-vous accomplir aujourd'hui ?</h1>

            <p>
              Décrivez votre objectif. L’AI Router configuré proposera un
              workflow avec les agents actifs de M1, à valider avant
              enregistrement.
            </p>

            <form
              onSubmit={(event) => {
                event.preventDefault();

                request(draft);
              }}
            >
              <textarea
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                placeholder="Décrivez votre mission..."
                rows={5}
              />

              <button
                className="btn btn-primary"
                disabled={!draft.trim() || generating}
                type="submit"
              >
                Générer une proposition
              </button>
            </form>

            <small>
              Génération IA disponible lorsque M1 et l’AI Router sont configurés
            </small>
            {templateChoices}
          </div>
        </div>
      ) : (
        <div className="m3-automatic-split">
          <section className="m3-studio-chat">
            <div className="m3-studio-chat-header">
              <strong>Studio Chat</strong>

              <small>AI Router · proposition validée avant application</small>
            </div>

            <div className="m3-studio-chat-messages">
              {messages.map((message, index) => (
                <div key={index} className={`m3-chat-bubble ${message.role}`}>
                  <small>
                    {message.role === 'user' ? 'Vous' : 'Simulateur'}
                  </small>

                  <p>{message.text}</p>
                </div>
              ))}

              {generating && <p role="status">Génération en cours…</p>}

            </div>

              {pending && (
                <div className="m3-change-preview">
                  <strong>Aperçu du changement</strong>

                  <p>{pending.label}</p>

                  <div>
                    <button className="btn btn-primary" onClick={applyPending}>
                      Confirmer
                    </button>

                    <button
                      className="btn btn-outline"
                      onClick={() => {
                        setPending(null);

                        setMessages((previous) => [
                          ...previous,

                          {
                            role: 'assistant',
                            text: 'Modification annulée.'
                          }
                        ]);
                      }}
                    >
                      Annuler
                    </button>
                  </div>
                </div>
              )}

            <form
              className="m3-studio-chat-input"
              onSubmit={(event) => {
                event.preventDefault();

                request(draft);
              }}
            >
              <textarea
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                placeholder="Ex. Ajoute un agent vérificateur..."
                rows={3}
              />

              <button
                className="btn btn-primary"
                disabled={!draft.trim() || !!pending || generating}
                type="submit"
              >
                Envoyer
              </button>
            </form>
            {templateChoices}
          </section>

          {editor}
        </div>
      )}

      <RenameMissionDialog
        mission={renameOpen ? mission : null}
        onClose={() => setRenameOpen(false)}
        onSave={async (name) => {
          try {
            await updateMission(id, { name });

            setRenameOpen(false);
          } catch (error) {
            setErrorMessage(`Impossible de renommer : ${error.message}`);
          }
        }}
      />

      <ConfirmDialog
        open={!!confirm}
        title={confirm?.title}
        message={confirm?.message}
        confirmLabel="Confirmer"
        danger={false}
        onCancel={() => setConfirm(null)}
        onConfirm={() => {
          confirm?.action();

          setConfirm(null);
        }}
      />

      <Modal
        open={!!errorMessage}
        title="Information"
        onClose={() => setErrorMessage('')}
      >
        <p className="m3-error">{errorMessage}</p>

        <button className="btn btn-outline" onClick={() => setErrorMessage('')}>
          Fermer
        </button>
      </Modal>
    </div>
  );
}
