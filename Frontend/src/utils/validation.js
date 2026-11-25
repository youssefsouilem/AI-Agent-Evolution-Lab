import { mockAgents } from "../data/mockAgents.js";
import { validateAttemptLimits } from "../services/missionApi.js";

export function validateOrchestration(nodes, edges, settings) {
  const issues = validateAttemptLimits(nodes, settings);
  if (nodes.length > settings.maxSteps)
    issues.push({
      nodeId: null,
      message: `Limite administrateur : ${settings.maxSteps} étapes maximum.`,
    });
  if (
    !settings.allowConditions &&
    nodes.some((node) => node.type === "conditionNode")
  ) {
    issues.push({
      nodeId: null,
      message: "Les conditions sont désactivées par l’administrateur.",
    });
  }
  return issues;
}

export function validateWorkflow(
  nodes = [],
  edges = [],
  catalogue = mockAgents,
  options = {},
) {
  const issues = [];
  const allEdges = edges;
  if (options.server) {
    edges = edges.filter(edge => !edge.data?.correction);
    for (const edge of allEdges.filter(edge => edge.data?.correction)) {
      if (edge.sourceHandle !== 'no' || nodes.find(node => node.id === edge.source)?.type !== 'conditionNode' || nodes.find(node => node.id === edge.target)?.type !== 'agentNode') {
        issues.push({ message: 'Un retour de correction doit relier Non à un agent précédent.' });
      }
    }
  }
  if (!nodes.length)
    return [
      {
        nodeId: null,
        message: "Ajoutez au moins un agent avant de lancer la simulation.",
      },
    ];
  const ids = new Set(nodes.map((n) => n.id));
  if (ids.size !== nodes.length)
    issues.push({
      nodeId: null,
      message: "Les identifiants des étapes doivent être uniques.",
    });
  if (!nodes.some((node) => node.type === "agentNode"))
    issues.push({ nodeId: null, message: "Ajoutez au moins une étape agent." });
  const roots = nodes.filter(
    (node) => !edges.some((edge) => edge.target === node.id),
  );
  if (roots.length > 1)
    issues.push({
      nodeId: null,
      message:
        "La simulation nécessite un seul début de workflow. Reliez les étapes à un début commun.",
    });
  for (const n of nodes) {
    if (
      !options.server &&
      n.type !== "conditionNode" &&
      edges.filter((edge) => edge.source === n.id).length > 1
    ) {
      issues.push({
        nodeId: n.id,
        message: `« ${n.data.label} » : les branches parallèles ne sont pas prises en charge par ce simulateur.`,
      });
    }
    if (
      n.type === "conditionNode" &&
      (!["=", "!=", ">", ">=", "<", "<=", "is_true", "is_false"].includes(n.data.operator) ||
        !Number.isFinite(n.data.expected) ||
        !Number.isFinite(n.data.sampleValue))
    ) {
      issues.push({
        nodeId: n.id,
        message: `Configurez les valeurs et l’opérateur de la condition « ${n.data.label} ».`,
      });
    }
    if (
      n.type === "agentNode" &&
      !catalogue.some(
        (a) =>
          String(a.id) === String(n.data.agentId) &&
          (!a.versions ||
            (a.status === "ACTIVE" &&
              a.versions.some(
                (v) =>
                  String(v.id) === String(n.data.agentVersionId) && v.active,
              ))),
      )
    )
      issues.push({
        nodeId: n.id,
        message: `Agent manquant pour « ${n.data.label} ».`,
      });
    if (n.type === "agentNode" && !n.data.instruction?.trim())
      issues.push({
        nodeId: n.id,
        message: `Instruction vide : ${n.data.label}.`,
      });
  }
  for (const n of nodes.filter((n) => n.type === "conditionNode")) {
    const outs = allEdges.filter((e) => e.source === n.id);
    if (
      !outs.some((e) => e.sourceHandle === "yes") ||
      !outs.some((e) => e.sourceHandle === "no")
    )
      issues.push({
        nodeId: n.id,
        message: `La condition « ${n.data.label} » doit posséder ses branches Oui et Non.`,
      });
  }
  for (const e of edges)
    if (!ids.has(e.source) || !ids.has(e.target))
      issues.push({
        nodeId: null,
        message: "Une connexion pointe vers un nœud absent.",
      });
  if (nodes.length > 1) {
    const incoming = Object.fromEntries(nodes.map((n) => [n.id, 0]));
    const outgoing = Object.fromEntries(nodes.map((n) => [n.id, []]));
    edges.forEach((e) => {
      if (ids.has(e.source) && ids.has(e.target)) {
        incoming[e.target]++;
        outgoing[e.source].push(e.target);
      }
    });
    const starts = nodes.filter((n) => incoming[n.id] === 0);
    const ends = nodes.filter((n) => outgoing[n.id].length === 0);
    if (!starts.length)
      issues.push({
        nodeId: null,
        message: "Aucun début de workflow identifiable.",
      });
    if (!ends.length)
      issues.push({
        nodeId: null,
        message: "Aucune fin de workflow identifiable.",
      });
    for (const n of nodes) {
      if (incoming[n.id] === 0 && outgoing[n.id].length === 0)
        issues.push({
          nodeId: n.id,
          message: `Étape isolée : ${n.data.label}.`,
        });
    }
    const indeg = { ...incoming };
    const queue = Object.keys(indeg).filter((k) => indeg[k] === 0);
    let traversed = 0;
    while (queue.length) {
      const id = queue.shift();
      traversed++;
      for (const dest of outgoing[id])
        if (--indeg[dest] === 0) queue.push(dest);
    }
    if (traversed !== nodes.length)
      issues.push({
        nodeId: null,
        message: "Cycle détecté : le workflow séquentiel doit être acyclique.",
      });
    const reachable = new Set();
    const visit = (id) => {
      if (reachable.has(id)) return;
      reachable.add(id);
      outgoing[id].forEach(visit);
    };
    starts.forEach((n) => visit(n.id));
    for (const n of nodes)
      if (!reachable.has(n.id))
        issues.push({
          nodeId: n.id,
          message: `Étape non accessible depuis le début : ${n.data.label}.`,
        });
  }
  return issues;
}
export function topologicalNodes(nodes, edges) {
  const next = [];
  const ids = new Set(nodes.map((n) => n.id));
  const degree = Object.fromEntries(nodes.map((n) => [n.id, 0]));
  edges.forEach((e) => {
    if (ids.has(e.target) && ids.has(e.source)) degree[e.target]++;
  });
  const queue = nodes.filter((n) => degree[n.id] === 0);
  while (queue.length) {
    const n = queue.shift();
    next.push(n);
    edges
      .filter((e) => e.source === n.id)
      .forEach((e) => {
        degree[e.target]--;
        if (degree[e.target] === 0)
          queue.push(nodes.find((n) => n.id === e.target));
      });
  }
  return next.length === nodes.length ? next : nodes;
}
export function evaluateDemoCondition(data) {
  const a = Number(data.sampleValue ?? 0);
  const b = Number(data.expected ?? 0);
  switch (data.operator) {
    case "=":
      return a === b;
    case "!=":
      return a !== b;
    case ">":
      return a > b;
    case ">=":
      return a >= b;
    case "<":
      return a < b;
    case "<=":
      return a <= b;
    default:
      return false;
  }
}
export function simulatedExecutionPath(nodes = [], edges = []) {
  if (!nodes.length) return [];
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const start =
    nodes.find((n) => n.type === "startNode") ||
    nodes.find((n) => !edges.some((e) => e.target === n.id)) ||
    nodes[0];
  const path = [];
  const visits = new Map();
  let current = start;
  while (current && path.length < 100) {
    const count = (visits.get(current.id) || 0) + 1;
    visits.set(current.id, count);
    if (count > 1) break;
    path.push(current);
    if (current.type === "endNode") break;
    const following = edges.filter((e) => e.source === current.id);
    let next;
    if (current.type === "conditionNode") {
      const branch = evaluateDemoCondition(current.data) ? "yes" : "no";
      next = following.find((e) => e.sourceHandle === branch);
    } else next = following[0];
    current = next ? byId.get(next.target) : null;
  }
  return path;
}
