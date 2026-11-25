import {
  ArrowRight,
  Bot,
  Workflow,
  MoreHorizontal,
  Search,
  FileText,
  Code2,
  ShieldCheck,
  MessagesSquare,
  ChartColumn
} from 'lucide-react';

import { prettyDate } from '../../utils/workflow';

import StatusBadge from './StatusBadge';

const themes = [
  { name: 'violet', Icon: ChartColumn },
  { name: 'blue', Icon: Search },
  { name: 'orange', Icon: FileText },
  { name: 'teal', Icon: Code2 },
  { name: 'pink', Icon: MessagesSquare },
  { name: 'green', Icon: ShieldCheck }
];

export default function MissionCard({ mission, latest, onOpen, onAction }) {
  const missionId = String(mission.id ?? '');

  const hash = [...missionId].reduce(
    (total, character) => total + character.charCodeAt(0),
    0
  );

  const theme = themes[hash % themes.length];

  const Icon = theme.Icon;

  return (
    <article className="m3-library-card">
      <div className={`m3-art ${theme.name}`}>
        <Icon size={37} strokeWidth={1.4} />

        <span className="m3-art-circles" />
      </div>

      <div className="m3-card-inner">
        <div className="m3-card-line">
          <span className="muted">
            {mission.mode === 'automatic' ? (
              <Bot size={14} />
            ) : (
              <Workflow size={14} />
            )}

            {mission.mode === 'automatic' ? 'Automatique · démo' : 'Manuel'}
          </span>

          <details className="m3-card-menu">
            <summary aria-label={`Actions pour ${mission.name}`}>
              <MoreHorizontal size={20} />
            </summary>

            <div className="m3-card-dropdown">
              {[
                ['open', 'Ouvrir'],
                ['rename', 'Renommer'],
                ['duplicate', 'Dupliquer'],
                ['edit', 'Modifier'],
                ['executions', 'Exécutions'],
                ['delete', 'Supprimer']
              ].map(([action, label]) => (
                <button
                  key={action}
                  type="button"
                  onClick={(event) => {
                    const details = event.currentTarget.closest('details');

                    if (details) {
                      details.open = false;
                    }

                    onAction(action, mission);
                  }}
                >
                  {label}
                </button>
              ))}
            </div>
          </details>
        </div>

        <h3>{mission.name}</h3>

        <p>
          {mission.description ||
            'Workflow personnalisable avec les agents existants M1.'}
        </p>

        <div className="m3-card-meta">
          <span>
            {(mission.nodes || []).filter((node) => node.data?.agentId).length}{' '}
            agent(s)
          </span>

          <span>{(mission.nodes || []).length} étapes</span>

          {latest && <StatusBadge status={latest.status} />}
        </div>

        <div className="m3-card-foot">
          <small>
            Modifiée{' '}
            {mission.updatedAt ? prettyDate(mission.updatedAt) : 'récemment'}
          </small>

          <button type="button" onClick={onOpen}>
            Ouvrir
            <ArrowRight size={15} />
          </button>
        </div>
      </div>
    </article>
  );
}
