import { useParams, useNavigate } from 'react-router-dom';
import { useMissions } from '../../../context/MissionContext';
import MissionEditor from '../../../components/mission-management/MissionEditor';
import { Workflow, ArrowRight } from 'lucide-react';

export default function MissionStudio() {
  const { id } = useParams();
  const nav = useNavigate();

  const { missions, loading, error } = useMissions();

  const mission = missions.find((m) => String(m.id) === String(id));

  if (!id) {
    return (
      <div className="page">
        <div className="page-heading">
          <div>
            <div className="breadcrumb">M3 / Espace de conception</div>

            <h1>Mission Studio</h1>

            <p>Créez et modifiez vos workflows dans l'éditeur visuel.</p>
          </div>
        </div>

        <div className="m3-studio-landing">
          <Workflow size={38} />

          <h2>Votre espace de création</h2>

          <p>
            Choisissez une mission récente pour ouvrir directement le canvas ou
            créez un workflow.
          </p>

          <button
            className="btn btn-primary"
            onClick={() => nav('/missions/new')}
          >
            Créer un workflow
          </button>
        </div>

        <div className="m3-panel">
          <h2>Workflows récents</h2>

          {loading ? (
            <p>Chargement des missions...</p>
          ) : (
            missions.slice(0, 5).map((m) => (
              <button
                className="m3-list-row"
                key={m.id}
                onClick={() => nav(`/studio/${m.id}`)}
              >
                <strong>{m.name}</strong>

                <span>
                  {m.mode === 'automatic'
                    ? 'Automatique IA · simulation'
                    : 'Manuel'}
                </span>

                <ArrowRight size={16} />
              </button>
            ))
          )}
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="page">
        <p>Chargement de la mission...</p>
      </div>
    );
  }

  if (error && !mission) {
    return (
      <div className="page">
        <h2>Erreur de chargement</h2>

        <p>{error}</p>

        <button className="btn btn-primary" onClick={() => nav('/missions')}>
          Mes missions
        </button>
      </div>
    );
  }

  if (!mission) {
    return (
      <div className="page">
        <h2>Mission introuvable</h2>

        <p>La mission demandée n'existe pas ou n'a pas été chargée.</p>

        <button className="btn btn-primary" onClick={() => nav('/missions')}>
          Mes missions
        </button>
      </div>
    );
  }

  return (
    <MissionEditor
      key={mission.id}
      mode={mission.mode === 'automatic' ? 'automatic' : 'manual'}
    />
  );
}
