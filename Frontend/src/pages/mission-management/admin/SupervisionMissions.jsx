import { useMemo, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useMissions } from '../../../context/MissionContext';
import StatusBadge from '../../../components/mission-management/StatusBadge';

export default function SupervisionMissions() {
  const { missions, executions } = useMissions();
  const navigate = useNavigate();
  const location = useLocation();
  const [tab, setTab] = useState(
    location.pathname.endsWith('/executions') ? 'executions' : 'missions'
  );
  const [query, setQuery] = useState('');
  const ms = useMemo(
    () =>
      missions.filter((m) =>
        m.name.toLowerCase().includes(query.toLowerCase())
      ),
    [missions, query]
  );
  const runs = executions.filter((e) =>
    e.missionName?.toLowerCase().includes(query.toLowerCase())
  );
  return (
    <div className="page">
      <div className="page-heading">
        <div>
          <div className="breadcrumb">M3 / Administration</div>
          <h1>Supervision globale</h1>
          <p>
            Inspection en lecture seule des missions et exécutions de
            démonstration.
          </p>
        </div>
      </div>
      <div className="m3-tabs-inline">
        <button
          className={tab === 'missions' ? 'active' : ''}
          onClick={() => setTab('missions')}
        >
          Toutes les missions
        </button>
        <button
          className={tab === 'executions' ? 'active' : ''}
          onClick={() => setTab('executions')}
        >
          Supervision des exécutions
        </button>
      </div>
      <input
        className="field m3-admin-search"
        placeholder="Rechercher une mission..."
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      <div className="m3-panel m3-table-scroll">
        <table className="m3-table">
          <thead>
            <tr>
              {(tab === 'missions'
                ? [
                    'Mission',
                    'Propriétaire fictif',
                    'Mode',
                    'Étapes',
                    'Dernière exécution',
                    'Actions'
                  ]
                : ['Exécution', 'Mission', 'Date', 'Durée', 'Statut', 'Actions']
              ).map((h) => (
                <th key={h}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {tab === 'missions'
              ? ms.map((m) => {
                  const last = executions.find((e) => e.missionId === m.id);
                  return (
                    <tr key={m.id}>
                      <td>
                        <strong>{m.name}</strong>
                      </td>
                      <td>Utilisateur Démo</td>
                      <td>
                        {m.mode === 'automatic' ? 'IA simulée' : 'Manuel'}
                      </td>
                      <td>{m.nodes?.length || 0}</td>
                      <td>
                        {last ? <StatusBadge status={last.status} /> : 'Aucune'}
                      </td>
                      <td>
                        <button
                          className="btn btn-outline"
                          onClick={() => navigate(`/admin/missions/${m.id}`)}
                        >
                          Inspecter
                        </button>
                      </td>
                    </tr>
                  );
                })
              : runs.map((e) => (
                  <tr key={e.id}>
                    <td>{e.id.slice(0, 10)}…</td>
                    <td>{e.missionName}</td>
                    <td>{new Date(e.executedAt).toLocaleString('fr-FR')}</td>
                    <td>
                      {e.durationMs || 0}
                      ms
                    </td>
                    <td>
                      <StatusBadge status={e.status} />
                    </td>
                    <td>
                      <button
                        className="btn btn-outline"
                        onClick={() =>
                          navigate(`/executions/${e.missionId}?run=${e.id}`)
                        }
                      >
                        Détails
                      </button>
                    </td>
                  </tr>
                ))}
          </tbody>
        </table>
        {!(tab === 'missions' ? ms : runs).length && (
          <p className="muted">Aucun élément trouvé.</p>
        )}
      </div>
    </div>
  );
}
