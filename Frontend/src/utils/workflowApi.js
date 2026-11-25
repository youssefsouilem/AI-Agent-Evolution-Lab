import { readSettings } from '../services/missionApi.js';
import { mockAgents } from '../data/mockAgents.js';
const nodeTypes = {
  agentNode: 'AGENT',
  conditionNode: 'CONDITION',
  startNode: 'START',
  endNode: 'END'
};

// Exclude React Flow's transient selection and measurement state from JSON and dirty checks.
export function workflowDocument(nodes, edges) {
  return {
    nodes: nodes.map(({ id, type, position, data }) => ({
      id,
      type,
      position,
      data
    })),
    edges: edges.map(({ selected, ...edge }) => edge)
  };
}

export function toWorkflowRequest(nodes, edges, settings = readSettings()) {
  const orders = new Map(nodes.map((node, index) => [node.id, index]));
  return {
    steps: nodes.map((node, stepOrder) => {
      if (!nodeTypes[node.type]) throw new Error('Type de nœud non reconnu.');
      const input = node.data.inputMapping ?? node.data.input;
      return {
        name: node.data.label?.trim() || `Étape ${stepOrder + 1}`,
        stepOrder,
        nodeType: nodeTypes[node.type],
        agentId: node.data.agentId || null,
        agentVersionId: node.data.agentVersionId || null,
        instruction: node.data.instruction || null,
        inputMapping:
          input == null || input === ''
            ? null
            : typeof input === 'string'
              ? input
              : JSON.stringify(input),
        expectedOutput: node.data.expectedOutput || null,
        positionX: node.position.x,
        positionY: node.position.y,
        maxRetries:
          node.type === 'agentNode'
            ? (node.data.maxRetries ?? settings.maxRetries)
            : null,
        conditionVariable:
          node.type === 'conditionNode' ? (node.data.variable ?? null) : null,
        conditionOperator:
          node.type === 'conditionNode' ? (node.data.operator ?? null) : null,
        conditionExpected:
          node.type === 'conditionNode' ? (node.data.expected ?? null) : null,
        conditionSampleValue:
          node.type === 'conditionNode'
            ? (node.data.sampleValue ?? null)
            : null,
        agentRole:
          node.data.role ||
          mockAgents.find((agent) => agent.id === node.data.agentId)?.role ||
          null
      };
    }),
    edges: edges.map((edge) => {
      if (!orders.has(edge.source) || !orders.has(edge.target)) {
        throw new Error('Une connexion référence une étape inexistante.');
      }
      return {
        correction: edge.data?.correction === true,
        sourceStepOrder: orders.get(edge.source),
        targetStepOrder: orders.get(edge.target),
        conditionLabel:
          edge.sourceHandle === 'yes' || edge.sourceHandle === 'no'
            ? edge.sourceHandle
            : edge.data?.conditionLabel || edge.label || null
      };
    })
  };
}

export function fromWorkflowResponse(response, local = {}) {
  if (!Array.isArray(response?.steps) || !Array.isArray(response?.edges)) {
    throw new Error('Réponse du workflow invalide.');
  }
  const steps = [...response.steps].sort((a, b) => a.stepOrder - b.stepOrder);
  const ids = new Map(
    steps.map((step) => [String(step.id), `step-${step.id}`])
  );
  const types = Object.fromEntries(
    Object.entries(nodeTypes).map(([key, value]) => [value, key])
  );
  const nodes = steps.map((step, index) => {
    if (!types[step.nodeType]) throw new Error('Type de nœud serveur inconnu.');
    return {
      id: ids.get(String(step.id)),
      type: types[step.nodeType],
      position: {
        x: step.positionX ?? 100 + (index % 3) * 340,
        y: step.positionY ?? 100 + Math.floor(index / 3) * 220
      },
      data: {
        label: step.name,
        order: index + 1,
        agentId: step.agentId,
        agentVersionId: step.agentVersionId,
        instruction: step.instruction || '',
        inputMapping: step.inputMapping || '',
        expectedOutput: step.expectedOutput || '',
        maxRetries: step.maxRetries ?? undefined,
        role:
          step.agentRole ??
          mockAgents.find((agent) => agent.id === step.agentId)?.role ??
          '',
        variable: step.conditionVariable ?? undefined,
        operator: step.conditionOperator ?? undefined,
        expected: step.conditionExpected ?? undefined,
        sampleValue: step.conditionSampleValue ?? undefined
      }
    };
  });
  const edges = response.edges.map((edge) => {
    const source = ids.get(String(edge.sourceStepId));
    const target = ids.get(String(edge.targetStepId));
    if (!source || !target)
      throw new Error('Connexion serveur sans étape correspondante.');
    const label = edge.conditionLabel;
    const branch = ['yes', 'oui'].includes(label?.toLowerCase())
      ? 'yes'
      : ['no', 'non'].includes(label?.toLowerCase())
        ? 'no'
        : undefined;
    return {
      id: `edge-${edge.id}`,
      source,
      target,
      animated: true,
      ...(branch ? { sourceHandle: branch } : {}),
      ...(label ? { label: edge.correction ? 'Non · correction' : label, data: { conditionLabel: label, correction: edge.correction === true } } : {})
    };
  });
  return {
    nodes,
    edges,
    versions: local.versions || [],
    chatHistory: local.chatHistory || [],
    workflowLoaded: true,
    workflowError: null,
    workflowNotice: steps.some(
      (step) =>
        step.nodeType === 'CONDITION' &&
        (step.conditionOperator == null ||
          step.conditionExpected == null ||
          step.conditionSampleValue == null)
    )
      ? 'Ancienne condition sans configuration complète : configurez-la et enregistrez la mission.'
      : ''
  };
}

export function assertWorkflowDocument(value) {
  if (
    !value ||
    !Array.isArray(value.nodes) ||
    !Array.isArray(value.edges) ||
    value.nodes.length > 100 ||
    value.edges.length > 200
  ) {
    throw new Error(
      'Le JSON doit contenir nodes (100 maximum) et edges (200 maximum).'
    );
  }
  const ids = new Set();
  for (const node of value.nodes) {
    if (
      !node ||
      typeof node.id !== 'string' ||
      !node.id ||
      ids.has(node.id) ||
      !nodeTypes[node.type] ||
      !Number.isFinite(node.position?.x) ||
      !Number.isFinite(node.position?.y) ||
      !node.data ||
      Array.isArray(node.data) ||
      typeof node.data !== 'object' ||
      typeof node.data.label !== 'string' ||
      [
        'instruction',
        'input',
        'inputMapping',
        'expectedOutput',
        'agentId'
      ].some(
        (key) => node.data[key] != null && typeof node.data[key] !== 'string'
      )
    ) {
      throw new Error(
        'Chaque nœud doit avoir un identifiant unique, un type, une position et des données valides.'
      );
    }
    ids.add(node.id);
  }
  const edgeIds = new Set();
  for (const edge of value.edges) {
    if (
      !edge ||
      typeof edge.id !== 'string' ||
      !edge.id ||
      edgeIds.has(edge.id) ||
      !ids.has(edge.source) ||
      !ids.has(edge.target) ||
      edge.source === edge.target ||
      (edge.sourceHandle != null && !['yes', 'no'].includes(edge.sourceHandle))
    ) {
      throw new Error(
        'Une connexion est invalide, dupliquée ou pointe vers un nœud absent.'
      );
    }
    edgeIds.add(edge.id);
  }
  return { nodes: value.nodes, edges: value.edges };
}
