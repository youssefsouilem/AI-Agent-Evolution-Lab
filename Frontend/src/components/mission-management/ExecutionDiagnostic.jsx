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
  MINOR: "Ã€ vÃ©rifier",
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
    ? "ContrÃ´le indisponible"
    : loading
      ? "ContrÃ´le en cours"
      : diagnostic
        ? diagnostic.simulated
          ? "Diagnostic simulÃ©"
          : diagnostic.blocking
            ? "Diffusion bloquÃ©e"
            : severity === "MINOR"
              ? "VÃ©rification recommandÃ©e"
              : "Aucun blocage signalÃ©"
        : "Non Ã©valuÃ©";
  return (
    <section
      className={`run-box execution-diagnostic ${tone}`}
      aria-label="Diagnostic de lâ€™exÃ©cution"
    >
      <div className="execution-diagnostic-heading">
        <h3>
          <ShieldCheck size={19} /> Diagnostic de lâ€™exÃ©cution
        </h3>
        <span className={`diagnostic-badge ${tone}`}>
          {failure
            ? "Indisponible"
            : loading
              ? "En cours"
              : diagnostic
                ? severityLabels[severity] || severity
                : "Non Ã©valuÃ©"}
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
                  ? "Exemple de test : cette rÃ©ponse ne constitue pas un contrÃ´le rÃ©el."
                  : diagnostic.blocking
                    ? "Une anomalie bloquante a Ã©tÃ© signalÃ©e. Le rÃ©sultat ne doit pas Ãªtre diffusÃ©."
                    : "Le diagnostic a Ã©tÃ© reÃ§u du service Monitoring."
                : !execution
                  ? "Lancez une exÃ©cution pour suivre son contrÃ´le."
                  : !terminal
                    ? "Le contrÃ´le sera disponible aprÃ¨s lâ€™exÃ©cution des Ã©tapes."
                    : execution.metricsSource !== "MEASURED"
                      ? execution.metricsSource === "SIMULATED"
                        ? "Cette exÃ©cution utilise des agents simulÃ©s. Ses mÃ©triques ne sont pas envoyÃ©es au service Monitoring."
                        : "Les mÃ©triques mesurÃ©es ne sont pas encore disponibles."
                      : "Aucun diagnostic reÃ§u pour cette exÃ©cution.")}
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
            <dt>Blocage{diagnostic.simulated ? " simulÃ©" : ""}</dt>
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
            ? "VÃ©rificationâ€¦"
            : failure
              ? "RÃ©essayer le contrÃ´le"
              : "Actualiser le diagnostic"}
        </button>
      )}
      <p className="diagnostic-note">
        Une anomalie ne signifie pas automatiquement que le contenu est faux.
      </p>
    </section>
  );
}
