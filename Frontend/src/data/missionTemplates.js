import { makeNode, resolveAgentRole, uuid } from '../utils/workflow';

export const missionTemplates = [
  {
    id: 'market-analysis',
    name: 'Analyse de marché',
    description: 'Rechercher, analyser et vérifier les tendances du marché.',
    roles: ['planner', 'research', 'writer', 'reviewer']
  },
  {
    id: 'data-analysis',
    name: 'Analyse de données',
    description: 'Préparer et synthétiser des données pour en tirer des constats.',
    roles: ['planner', 'data', 'reviewer']
  },
  {
    id: 'content-creation',
    name: 'Création de contenu',
    description: 'Documenter un sujet et préparer un contenu relu.',
    roles: ['planner', 'research', 'writer', 'reviewer']
  }
];

export function createTemplateWorkflow(templateId, catalogue) {
  const template = missionTemplates.find((item) => item.id === templateId);
  if (!template) throw new Error('Modèle de mission introuvable.');

  const nodes = template.roles.map((role, index) => {
    const agent = resolveAgentRole(role, catalogue);
    return makeNode(agent.id, index, {
      x: 100 + index * 300,
      y: 170
    }, catalogue);
  });
  const edges = nodes.slice(1).map((node, index) => ({
    id: uuid(),
    source: nodes[index].id,
    target: node.id,
    animated: true
  }));

  return { nodes, edges };
}
