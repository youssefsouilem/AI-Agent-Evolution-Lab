
import { useMemo } from 'react';
import { useMissions } from '../../../context/MissionContext';
import StatsCard from '../../../components/mission-management/StatsCard';
import StatusBadge from '../../../components/mission-management/StatusBadge';

export default function AdminDashboard() {
  const { missions, executions } = useMissions();
  const completed = executions.filter((e) => e.status === 'COMPLETED').length;
  const failed = executions.filter((e) => e.status === 'FAILED').length;
  const active = executions.filter((e) => e.status === 'RUNNING').length;
  const avg = executions.length
    ? Math.round(
        executions.reduce((s, e) => s + (e.durationMs || 0), 0) /
          executions.length
      )
    : 0;
  const byMission = useMemo(
    () =>
      missions
        .map((m) => ({
          name: m.name,
          count: executions.filter((e) => e.missionId === m.id).length
        }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 5),
    [missions, executions]
  );
  const max = Math.max(1, ...byMission.map((x) => x.count));
  return (
    <div className="page">
      <div className="page-heading">
        <div>
          <div className="breadcrumb">M3 / Administration · démo</div>
          <h1>Vue d'ensemble</h1>
          <p>
            Statistiques locales calculées à partir des missions et exécutions
            simulées.
          </p>
        </div>
      </div>
      <div className="m3-admin-stats">
        {[
          ['Missions', missions.length],
          ['Exécutions', executions.length],
          ['En cours', active],
          ['Réussies', completed],
          ['Échouées', failed],
          [
            'Taux de réussite',
            executions.length
              ? Math.round((completed / executions.length) * 100) + ' %'
              : '—'
          ],
          ['Durée moyenne', avg + ' ms'],
          [
            'Tentatives moyennes',
            executions.length
              ? (
                  executions.reduce((v, e) => v + (e.attempts || 1), 0) /
                  executions.length
                ).toFixed(1)
              : '—'
          ]
        ].map(([label, value]) => (
          <StatsCard key={label} label={label} value={value} />
        ))}
      </div>
      <div className="m3-admin-panels">
        <section className="m3-panel">
          <h2>Missions les plus exécutées</h2>
          {byMission.map((m) => (
            <div className="m3-chart-row" key={m.name}>
              <span>{m.name}</span>
              <div>
                <i style={{ width: `${(m.count / max) * 100}%` }} />
              </div>
              <strong>{m.count}</strong>
            </div>
          ))}
          {!byMission.length && <p className="muted">Aucune mission.</p>}
        </section>
        <section className="m3-panel">
          <h2>Répartition des statuts</h2>
          {['COMPLETED', 'FAILED', 'RUNNING', 'PENDING'].map((s) => (
            <div className="m3-status-chart" key={s}>
              <StatusBadge status={s} />
              <strong>{executions.filter((e) => e.status === s).length}</strong>
            </div>
          ))}
          <h3>Modes de création</h3>
          <p>
            Manuel :{missions.filter((m) => m.mode === 'manual').length}·
            Automatique (simulation) :
            {missions.filter((m) => m.mode === 'automatic').length}
          </p>
        </section>
      </div>
      <section className="m3-panel">
        <h2>Activité récente</h2>
        {executions.slice(0, 6).map((e) => (
          <div key={e.id} className="m3-admin-row">
            <span>
              {e.missionName}
              <small>{new Date(e.executedAt).toLocaleString('fr-FR')}</small>
            </span>
            <StatusBadge status={e.status} />
          </div>
        ))}
        {!executions.length && (
          <p className="muted">Aucune exécution enregistrée.</p>
        )}
      </section>
    </div>
  );
}
