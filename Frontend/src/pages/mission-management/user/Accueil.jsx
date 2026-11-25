import { useNavigate } from "react-router-dom";
import {
  ArrowRight,
  Bot,
  Plus,
  Workflow,
  PlayCircle,
  Clock3,
} from "lucide-react";
import { useMissions } from "../../../context/MissionContext";
import { prettyDate } from "../../../utils/workflow";

export default function Accueil() {
  const { missions, executions } = useMissions();
  const navigate = useNavigate();
  return (
    <div className="page">
      <div className="page-heading">
        <div>
          <div className="breadcrumb">Evolution Lab / M3</div>
          <h1>Vue d'ensemble</h1>
          <p>
            Concevez et supervisez vos missions multi-agents — environnement de
            démonstration local.
          </p>
        </div>
        <button
          className="btn btn-primary"
          onClick={() => navigate("/missions/new")}
        >
          <Plus size={16} />
          Créer une mission
        </button>
      </div>
      <div className="stats">
        <div>
          <span>Missions</span>
          <strong>{missions.length}</strong>
          <small>Workflows enregistrés</small>
        </div>
        <div>
          <span>Agents configurés</span>
          <strong>{missions.reduce((v, m) => v + m.nodes.length, 0)}</strong>
          <small>Étapes dans les missions</small>
        </div>
        <div>
          <span>Exécutions</span>
          <strong>{executions.length}</strong>
          <small>Exécutions du serveur</small>
        </div>
      </div>
      <div className="m3-overview">
        <section className="m3-panel">
          <div className="m3-section-head">
            <h2>Dernières missions</h2>
            <button
              className="text-action"
              onClick={() => navigate("/missions")}
            >
              Tout voir
              <ArrowRight size={15} />
            </button>
          </div>
          {missions
            .slice()
            .sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt))
            .slice(0, 5)
            .map((m) => (
              <button
                className="m3-list-row"
                key={m.id}
                onClick={() => navigate(`/studio/${m.id}`)}
              >
                <span className="history-icon">
                  {m.mode === "manual" ? (
                    <Workflow size={18} />
                  ) : (
                    <Bot size={18} />
                  )}
                </span>
                <span>
                  <strong>{m.name}</strong>
                  <small>
                    {m.nodes.length}
                    étapes ·{prettyDate(m.updatedAt)}
                  </small>
                </span>
                <ArrowRight size={16} />
              </button>
            ))}
          {!missions.length && (
            <p className="muted">Aucune mission enregistrée.</p>
          )}
        </section>
        <section className="m3-panel">
          <div className="m3-section-head">
            <h2>Exécutions récentes</h2>
            <button
              className="text-action"
              onClick={() => navigate("/executions")}
            >
              Tout voir
              <ArrowRight size={15} />
            </button>
          </div>
          {executions.slice(0, 5).map((e) => (
            <button
              className="m3-list-row"
              key={e.id}
              onClick={() => navigate(`/executions/${e.missionId}?run=${e.id}`)}
            >
              <span className="history-icon">
                <PlayCircle size={18} />
              </span>
              <span>
                <strong>{e.missionName}</strong>
                <small>
                  {e.status.toUpperCase()}·{prettyDate(e.executedAt)}
                </small>
              </span>
              <ArrowRight size={16} />
            </button>
          ))}
          {!executions.length && (
            <div className="m3-empty-small">
              <Clock3 size={22} />
              <p>Les exécutions simulées apparaîtront ici.</p>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
