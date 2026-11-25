import { useEffect, useRef, useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  Clock3,
  RefreshCw,
  ShieldCheck,
} from "lucide-react";
import { checkExecutionMetrics } from "../../services/missionMonitoringApi";

const severityLabels = {
  NORMAL: "Normal",
  MINOR: "À vérifier",
  CRITICAL: "Critique",
};

export default function ExecutionDiagnostic({ execution, onDiagnostic }) {
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const request = useRef(null);
  useEffect(() => () => request.current?.abort(), []);
  let savedDiagnostic;
  try { savedDiagnostic = execution?.monitoringDiagnostic ? JSON.parse(execution.monitoringDiagnostic) : null; } catch { savedDiagnostic = null; }
  const diagnostic = savedDiagnostic || result || execution?.monitoring;
  const failure = error || execution?.monitoringError;
  const terminal = ["COMPLETED", "FAILED"].includes(execution?.status);
  const canCheck =
    execution?.source !== 'SERVER' && terminal &&
    execution?.metricsSource === "MEASURED" &&
    Number.isSafeInteger(Number(execution.id)) &&
    Number(execution.id) > 0 &&
    [execution.durationMs, execution.tokensUsed, execution.attempts].every(
      (value) => Number.isSafeInteger(value) && value >= 0,
    );
  const check = async () => {
    if (!canCheck || loading) return;
    const controller = new AbortController();
    request.current = controller;
    setLoading(true);
    setError("");
    try {
      const value = await checkExecutionMetrics(
        {
          executionId: Number(execution.id),
          durationMs: execution.durationMs,
          tokensUsed: execution.tokensUsed,
          attempts: execution.attempts,
          status: execution.status,
        },
        controller.signal,
      );
      if (!controller.signal.aborted) {
        setResult(value);
        onDiagnostic?.(value, null);
      }
    } catch (err) {
      if (!controller.signal.aborted) {
        setResult(null);
        setError(err.message);
        onDiagnostic?.(null, err.message);
      }
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  };
  const severity = diagnostic?.severity;
  const tone = failure
    ? "unavailable"
    : loading
      ? "waiting"
      : diagnostic
        ? severity?.toLowerCase()
        : "waiting";
  const title = failure
    ? "Contrôle indisponible"
    : loading
      ? "Contrôle en cours"
      : diagnostic
        ? diagnostic.simulated
          ? "Diagnostic simulé"
          : diagnostic.blocking
            ? "Diffusion bloquée"
            : severity === "MINOR"
              ? "Vérification recommandée"
              : "Aucun blocage signalé"
        : "Non évalué";
  return (
    <section
      className={`run-box execution-diagnostic ${tone}`}
      aria-label="Diagnostic de l’exécution"
    >
      <div className="execution-diagnostic-heading">
        <h3>
          <ShieldCheck size={19} /> Diagnostic de l’exécution
        </h3>
        <span className={`diagnostic-badge ${tone}`}>
          {failure
            ? "Indisponible"
            : loading
              ? "En cours"
              : diagnostic
                ? severityLabels[severity] || severity
                : "Non évalué"}
        </span>
      </div>
      <div
        className={`diagnostic-message ${tone}`}
        role={failure ? "alert" : "status"}
      >
        {failure || diagnostic?.blocking ? (
          <AlertTriangle size={21} />
        ) : diagnostic ? (
          <CheckCircle2 size={21} />
        ) : (
          <Clock3 size={21} />
        )}
        <div>
          <strong>{title}</strong>
          <p>
            {failure ||
              (diagnostic
                ? diagnostic.simulated
                  ? "Exemple de test : cette réponse ne constitue pas un contrôle réel."
                  : diagnostic.blocking
                    ? "Une anomalie bloquante a été signalée. Le résultat ne doit pas être diffusé."
                    : "Le diagnostic a été reçu du service Monitoring."
                : !execution
                  ? "Lancez une exécution pour suivre son contrôle."
                  : !terminal
                    ? "Le contrôle sera disponible après l’exécution des étapes."
                    : execution.metricsSource !== "MEASURED"
                      ? execution.metricsSource === "SIMULATED"
                        ? "Cette exécution utilise des agents simulés. Ses métriques ne sont pas envoyées au service Monitoring."
                        : "Les métriques mesurées ne sont pas encore disponibles."
                      : "Aucun diagnostic reçu pour cette exécution.")}
          </p>
        </div>
      </div>
      {diagnostic && !failure && (
        <dl className="diagnostic-details">
          <div>
            <dt>Motif</dt>
            <dd>{diagnostic.reason || "Aucun motif fourni"}</dd>
          </div>
          <div>
            <dt>Blocage{diagnostic.simulated ? " simulé" : ""}</dt>
            <dd>{diagnostic.blocking ? "Oui" : "Non"}</dd>
          </div>
          <div>
            <dt>Source</dt>
            <dd>{diagnostic.simulated ? "Simulation" : "Monitoring"}</dd>
          </div>
        </dl>
      )}
      {canCheck && (
        <button className="btn btn-outline" disabled={loading} onClick={check}>
          <RefreshCw size={15} />
          {loading
            ? "Vérification…"
            : failure
              ? "Réessayer le contrôle"
              : "Actualiser le diagnostic"}
        </button>
      )}
      <p className="diagnostic-note">
        Une anomalie ne signifie pas automatiquement que le contenu est faux.
      </p>
    </section>
  );
}
