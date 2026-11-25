import { mockAgents } from '../data/mockAgents.js';

export const uuid = () =>
  globalThis.crypto?.randomUUID?.() ||
  `id-${Date.now()}-${Math.random().toString(36).slice(2)}`;
export const initialMissions = [
  {
    id: 'demo-1',
    name: 'Analyse de marché',
    description: 'Étude et synthèse de tendances (démonstration)',
    mode: 'manual',
    status: 'draft',
    updatedAt: '2026-11-14T15:15:00.000Z',
    nodes: [
      makeNode('research', 0, { x: 120, y: 150 }),
      makeNode('writer', 1, { x: 440, y: 150 })
    ],
    edges: [
      { id: 'e-demo', source: 'node-0', target: 'node-1', animated: true }
    ]
  },
  {
    id: 'demo-2',
    name: 'Vérification de livrables',
    description: 'Organisation d’un contrôle qualité (démonstration)',
    mode: 'automatic',
    status: 'draft',
    updatedAt: '2026-11-13T11:15:00.000Z',
    nodes: [],
    edges: []
  }
];
export function makeNode(
  agentId,
  index,
  position = { x: 100 + index * 300, y: 180 },
  catalogue = mockAgents
) {
  const agent = catalogue.find((a) => String(a.id) === String(agentId));
  if (!agent) throw new Error('Agent indisponible dans le catalogue.');
  const version = agent.versions?.find(v => v.active);
  if (agent.versions && (!version || agent.status !== 'ACTIVE')) throw new Error('Agent sans version active.');
  return {
    id: `node-${index}`,
    type: 'agentNode',
    position,
    data: {
      agentId: agent.id,
      agentVersionId: version?.id,
      label: agent.name,
      role: agent.role,
      instruction: `Effectuer l’étape ${index + 1} : ${agent.role.toLowerCase()}.`,
      order: index + 1
    }
  };
}
export function demoGenerateWorkflow(goal, catalogue = mockAgents) {
  const input = goal.toLowerCase();
  const chosen =
    input.includes('donn') || input.includes('csv') || input.includes('stat')
      ? ['planner', 'data', 'reviewer']
      : input.includes('article') ||
          input.includes('contenu') ||
          input.includes('rédig')
        ? ['planner', 'research', 'writer', 'reviewer']
        : ['planner', 'research', 'reviewer'];
  const nodes = chosen.map((id, i) =>
    makeNode(resolveAgentRole(id, catalogue).id, i, {
      x: 105 + (i % 2) * 350,
      y: 105 + Math.floor(i / 2) * 240
    }, catalogue)
  );
  const edges = nodes.slice(1).map((node, i) => ({
    id: uuid(),
    source: nodes[i].id,
    target: node.id,
    animated: true
  }));
  return { nodes, edges };
}
export function downloadJson(filename, value) {
  const blob = new Blob([JSON.stringify(value, null, 2)], {
    type: 'application/json'
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 500);
}
export const prettyDate = (iso) =>
  new Date(iso).toLocaleDateString('fr-FR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric'
  });

// Local template matching by name; this is not an M1 capability contract.
export function resolveAgentRole(role, catalogue) {
  const expressions = { planner: /planifi/i, research: /recherch/i, writer: /rapport|rédac|rédig|writer/i, reviewer: /vérifi|verifi|review/i, data: /données|data/i };
  const agent = catalogue.find(a => (a.id === role || expressions[role]?.test(a.name)) && (!a.versions || (a.status === 'ACTIVE' && a.versions.some(v => v.active))));
  if (!agent) throw new Error('Aucun agent disponible pour le rôle « ' + role + ' ». Choisissez les agents manuellement.');
  return agent;
}
