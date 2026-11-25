package tn.esprit.mission.service;

import java.util.List;
import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;
import tn.esprit.mission.repository.WorkflowEdgeRepository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.mission.entity.MissionExecution;
import tn.esprit.mission.entity.MissionStepExecution;
import tn.esprit.mission.enums.NodeType;
import tn.esprit.mission.exception.ExecutionConflictException;
import tn.esprit.mission.enums.ExecutionStatus;
import tn.esprit.mission.exception.ResourceNotFoundException;
import tn.esprit.mission.repository.MissionExecutionRepository;
import tn.esprit.mission.repository.MissionStepRepository;
import tn.esprit.mission.repository.MissionStepExecutionRepository;

@Service
public class MissionExecutionService {

    private final MissionExecutionRepository executions;
    private final MissionService missions;
    private final OrchestrationBridge bridge;
    private final EngineWorkflowAdapter adapter;
    private final WorkflowEdgeRepository edges;
    private final ObjectMapper mapper;
    private final MissionStepRepository steps;
    private final tn.esprit.mission.repository.MissionStepAttemptRepository attemptJournal;
    private final MissionStepExecutionRepository stepExecutions;
    private final OrchestrationSettingsService settingsService;

    public MissionExecutionService(
        MissionExecutionRepository executions,
        MissionService missions,
        MissionStepRepository steps,
        MissionStepExecutionRepository stepExecutions,
        OrchestrationSettingsService settingsService,
        OrchestrationBridge bridge, EngineWorkflowAdapter adapter,
        WorkflowEdgeRepository edges, ObjectMapper mapper,
        tn.esprit.mission.repository.MissionStepAttemptRepository attemptJournal
    ) {
        this.executions = executions;
        this.missions = missions;
        this.steps = steps;
        this.stepExecutions = stepExecutions;
        this.settingsService = settingsService;
        this.attemptJournal = attemptJournal;
        this.bridge = bridge; this.adapter = adapter; this.edges = edges; this.mapper = mapper;
    }

    @Transactional
    public MissionExecution create(Long missionId) {
        // Lock the mission until commit to serialize duplicate launches and workflow edits.
        var mission = missions.getForUpdate(missionId);
        if (executions.existsByMissionIdAndStatusIn(
            missionId, List.of(ExecutionStatus.PENDING, ExecutionStatus.RUNNING, ExecutionStatus.PAUSED, ExecutionStatus.RECOVERY_REQUIRED)
        )) {
            throw new ExecutionConflictException("Une exécution est déjà en attente ou en cours pour cette mission.");
        }

        var workflowSteps = steps.findByMissionIdOrderByStepOrderAsc(missionId);
        var settings = settingsService.get();
        if (workflowSteps.stream().noneMatch(step -> step.getNodeType() == NodeType.AGENT)) {
            throw new IllegalArgumentException("Ajoutez et enregistrez au moins une étape agent avant de demander une exécution.");
        }
        if (workflowSteps.size() > settings.maxSteps()) {
            throw new IllegalArgumentException("Limite administrateur : " + settings.maxSteps() + " étapes maximum.");
        }
        for (var step : workflowSteps) {
            if (step.getNodeType() == NodeType.CONDITION && !settings.allowConditions()) {
                throw new IllegalArgumentException("Les conditions sont désactivées par l'administrateur.");
            }
            if (step.getNodeType() != NodeType.AGENT) continue;
            if (step.getAgentId() == null || step.getAgentId().isBlank() ||
                step.getAgentVersionId() == null || step.getAgentVersionId().isBlank()) {
                throw new IllegalArgumentException("Choisissez un agent et sa version pour « " + step.getName() + " ».");
            }
            if (step.getInstruction() == null || step.getInstruction().isBlank()) {
                throw new IllegalArgumentException("Instruction manquante pour « " + step.getName() + " ».");
            }
            if (step.getMaxRetries() != null &&
                (step.getMaxRetries() < 1 || step.getMaxRetries() > settings.maxRetries())) {
                throw new IllegalArgumentException("« " + step.getName() + " » : choisissez entre 1 et " + settings.maxRetries() + " tentatives (limite administrateur).");
            }
        }

        if (bridge.enabled()) {
            // Validate before creating any execution or changing the existing workflow.
            adapter.convert(mission, 1L, workflowSteps, edges.findByMissionId(missionId), settings);
        }
        var execution = new MissionExecution();
        execution.setMissionId(missionId);
        execution.setStatus(ExecutionStatus.PENDING);
        execution.setRequestedAt(Instant.now());
        execution.setAttemptsUsed(0);
        execution.setTotalTokensUsed(0L);
        execution = executions.save(execution);

        for (var step : workflowSteps) {
            var detail = new MissionStepExecution();
            detail.setMissionExecutionId(execution.getId());
            detail.setMissionStepId(step.getId());
            detail.setStatus(ExecutionStatus.PENDING);
            detail.setAttemptNumber(0);
            detail.setTokensUsed(0L);
            stepExecutions.save(detail);
        }
        if (bridge.enabled()) {
            try {
                execution.setOrchestratorRequest(mapper.writeValueAsString(
                    adapter.convert(mission, execution.getId(), workflowSteps, edges.findByMissionId(missionId), settings)));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("Impossible de sérialiser le workflow.", ex);
            }
            Long executionId = execution.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { bridge.launch(executionId); }
            });
        }
        return execution;
    }

    public List<tn.esprit.mission.entity.MissionStepAttempt> attempts(Long executionId) {
        get(executionId);
        return attemptJournal.findByMissionExecutionIdOrderByIdAsc(executionId);
    }

    public List<MissionStepExecution> steps(Long executionId) {
        get(executionId);
        return stepExecutions.findByMissionExecutionId(executionId).stream()
            .sorted(java.util.Comparator.comparing(MissionStepExecution::getId))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<MissionExecution> list(Long missionId) {
        missions.get(missionId);
        return executions.findByMissionIdOrderByIdDesc(missionId);
    }

    public MissionExecution get(Long executionId) {
        var existing = executions.findById(executionId).orElseThrow(() -> new ResourceNotFoundException("Exécution introuvable : " + executionId));
        missions.get(existing.getMissionId());
        bridge.refresh(executionId);
        return executions
            .findById(executionId)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Execution introuvable : " + executionId
                )
            );
    }
    @Transactional
    public MissionExecution resume(Long executionId, boolean approved) {
        if (!bridge.enabled()) throw new IllegalArgumentException("Le moteur Python est désactivé.");
        var execution = executions.findByIdForUpdate(executionId)
            .orElseThrow(() -> new ResourceNotFoundException("Exécution introuvable : " + executionId));
        missions.get(execution.getMissionId());
        if (execution.getStatus() != ExecutionStatus.PAUSED) {
            throw new ExecutionConflictException("Cette exécution n'est pas en pause.");
        }
        execution.setStatus(ExecutionStatus.RUNNING);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { bridge.resume(executionId, approved); }
        });
        return execution;
    }
}
