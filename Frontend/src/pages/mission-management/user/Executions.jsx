import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { ArrowRight, PlayCircle } from "lucide-react";
import { useMissions } from "../../../context/MissionContext";

export default function Executions() {
  const { missions, executions, executionError } = useMissions();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const missionId = params.get("mission");
  const [filter, setFilter] = useState("all");
  const records = executions.filter(
    (e) =>
      (filter === "all" || e.status === filter) &&
      (!missionId || String(e.missionId) === missionId),
  );
  return (
    <div className="page">
      <div className="page-heading">
        <div>
          <div className="breadcrumb">M3 / Exécutions</div>
          <h1>Centre d'exécutions</h1>
          <p>
            Une exécution possède son identifiant, sa progression et sa
            timeline. Les données proviennent du backend.
          </p>
        </div>
        <select
          className="select"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
        >
          <option value="all">Tous les statuts</option>
          {[
            "PENDING",
            "RUNNING",
            "PAUSED",
            "RECOVERY_REQUIRED",
            "COMPLETED",
            "FAILED",
            "CANCELLED",
          ].map((v) => (
            <option key={v} value={v}>
              {v}
            </option>
          ))}
        </select>
      </div>
      {executionError && <p role="alert">{executionError}</p>}
      <div className="m3-panel">
        <div className="m3-section-head">
          <h2>Exécutions enregistrées</h2>
          <span className="muted">
            {records.length}
            résultats
          </span>
        </div>
        {records.map((e) => (
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
                {e.id.slice(0, 13)}·
                {new Date(e.executedAt).toLocaleString("fr-FR")}·
                {e.durationMs || 0}
                ms
              </small>
            </span>
            <span className={`status-pill ${e.status.toLowerCase()}`}>
              {e.status}
            </span>
            <span className="m3-progress">
              <span style={{ width: `${e.progress || 0}%` }} />
            </span>
            <ArrowRight size={16} />
          </button>
        ))}
        {!records.length && (
          <div className="m3-empty-small">
            Aucune exécution enregistrée pour ce filtre.
          </div>
        )}
      </div>
      <div className="m3-panel">
        <div className="m3-section-head">
          <h2>Démarrer une exécution</h2>
        </div>
        {missions.map((m) => (
          <button
            key={m.id}
            className="m3-list-row"
            onClick={() => navigate(`/executions/${m.id}`)}
          >
            <span className="history-icon">
              <PlayCircle size={18} />
            </span>
            <span>
              <strong>{m.name}</strong>
              <small>
                {m.nodes.length}
                étapes disponibles
              </small>
            </span>
            <span className="text-action">
              Ouvrir
              <ArrowRight size={14} />
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}
