import { useNavigate, useParams } from 'react-router-dom';
import { useMissions } from '../../../context/MissionContext';
import WorkflowCanvas from '../../../components/mission-management/WorkflowCanvas';

export default function InspectionMission() {
  const { id } = useParams();
  const nav = useNavigate();
  const { missions, loading, error, loadMissions } = useMissions();
  const mission = missions.find((m) => String(m.id) === String(id));
  if (loading)
    return (
      <div className="page">
        <p role="status">Chargement de la mission…</p>
      </div>
    );
  if (error || mission?.workflowError)
    return (
      <div className="page">
        <h1>Inspection indisponible</h1>
        <p role="alert">{error || mission.workflowError}</p>
        <button className="btn btn-primary" onClick={loadMissions}>
          Réessayer
        </button>
        <button
          className="btn btn-outline"
          onClick={() => nav('/admin/missions')}
        >
          Retour
        </button>
      </div>
    );
  if (!mission)
    return (
      <div className="page">
        <p>Mission introuvable.</p>
        <button
          className="btn btn-outline"
          onClick={() => nav('/admin/missions')}
        >
          Retour
        </button>
      </div>
    );
  return (
    <div className="page">
      <div className="page-heading">
        <div>
          <div className="breadcrumb">Administration / Lecture seule</div>
          <h1>{mission.name}</h1>
          <p>Consultation du workflow simulé. Modification désactivée.</p>
        </div>
        <button
          className="btn btn-outline"
          onClick={() => nav('/admin/missions')}
        >
          Retour
        </button>
      </div>
      <div className="m3-inspection-canvas">
        <WorkflowCanvas
          nodes={mission.nodes || []}
          edges={mission.edges || []}
          readOnly
        />
      </div>
    </div>
  );
}
