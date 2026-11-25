export const activeExecutionStatuses = [
  'PENDING',
  'RUNNING',
  'PAUSED',
  'RECOVERY_REQUIRED'
];

export function executionDuration(start, finish) {
  if (!start || !finish) return null;
  const duration = Date.parse(finish) - Date.parse(start);
  return Number.isFinite(duration) ? Math.max(0, duration) : null;
}

// Server IDs are numeric; React Flow uses the saved node ID "step-{id}".
export function fromExecutionResponse(execution, details = [], mission = {}, attempts = []) {
  const steps = details.map((detail) => {
    const nodeId = `step-${detail.missionStepId}`;
    const node = mission.nodes?.find((item) => item.id === nodeId);
    return {
      ...detail,
      nodeId,
      name: node?.data.label || `Étape ${detail.missionStepId}`,
      result: detail.output,
      durationMs: executionDuration(detail.startedAt, detail.finishedAt),
      attempts: detail.attemptNumber
    };
  });
  const completed = steps.filter((step) =>
    ['COMPLETED', 'SKIPPED'].includes(step.status)
  ).length;
  return {
    ...execution,
    id: String(execution.id),
    source: 'SERVER',
    attemptHistory: attempts.map((attempt) => ({ ...attempt, name: mission.nodes?.find((node) => node.id === `step-${attempt.missionStepId}`)?.data.label || `Étape ${attempt.missionStepId}` })),
    missionName: mission.name || `Mission ${execution.missionId}`,
    executedAt: execution.requestedAt || execution.startedAt,
    steps,
    stepCount: mission.nodes?.length || steps.length,
    progress: steps.length ? Math.round((completed / steps.length) * 100) : 0,
    durationMs: executionDuration(execution.startedAt, execution.finishedAt),
    tokensUsed: execution.totalTokensUsed,
    attempts: execution.attemptsUsed,
    result: execution.finalResult,
    activeNodeId:
      steps.find((step) => step.status === 'RUNNING')?.nodeId || null
  };
}
