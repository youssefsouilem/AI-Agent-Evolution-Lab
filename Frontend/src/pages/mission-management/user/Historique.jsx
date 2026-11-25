import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowRight, Download, History, Search } from "lucide-react";
import { useMissions } from "../../../context/MissionContext";
import { downloadJson } from "../../../utils/workflow";

export default function Historique() {
  const { executions, missions, executionError } = useMissions();
  const navigate = useNavigate();
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState("all");
  const list = useMemo(
    () =>
      executions.filter(
        (e) =>
          (filter === "all" || e.status === filter) &&
          `${e.missionName} ${e.id}`.toLowerCase().includes(q.toLowerCase()),
      ),
    [executions, q, filter],
  );
  return (
    <div className="page">
      <div className="page-heading">
        <div>
          <div className="breadcrumb">M3 / Historique</div>
          <h1>Historique des exécutions</h1>
          <p>Exécutions et résultats enregistrés par le serveur.</p>
        </div>
      </div>
      <div className="toolbar m3-toolbar">
        <div className="search-box">
          <Search size={17} />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Rechercher par mission ou identifiant"
          />
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
          ].map((s) => (
            <option key={s}>{s}</option>
          ))}
        </select>
      </div>
      {executionError && <p role="alert">{executionError}</p>}
      <div className="m3-panel">
        <div className="m3-section-head">
          <h2>Historique serveur</h2>
          <span>
            {list.length}
            exécution(s)
          </span>
        </div>
        {list.map((e) => (
          <div className="m3-history-row" key={e.id}>
            <span className="history-icon">
              <History size={17} />
            </span>
            <span>
              <strong>{e.missionName}</strong>
              <small>
                {e.id.slice(0, 14)}·
                {new Date(e.executedAt).toLocaleString("fr-FR")}·
                {e.durationMs || 0}
                ms
              </small>
            </span>
            <span className={`status-pill ${e.status.toLowerCase()}`}>
              {e.status}
            </span>
            <button
              className="icon-button"
              title="Exporter JSON"
              onClick={() => downloadJson(`run-${e.id}.json`, e)}
            >
              <Download size={16} />
            </button>
            <button
              className="btn btn-outline"
              onClick={() => navigate(`/executions/${e.missionId}?run=${e.id}`)}
            >
              Détails
              <ArrowRight size={15} />
            </button>
            {missions.some((m) => String(m.id) === String(e.missionId)) && (
              <button
                className="btn btn-outline"
                onClick={() => navigate(`/executions/${e.missionId}`)}
              >
                Relancer
              </button>
            )}
          </div>
        ))}
        {!list.length && (
          <div className="m3-empty-small">Aucune exécution enregistrée.</div>
        )}
      </div>
    </div>
  );
}
