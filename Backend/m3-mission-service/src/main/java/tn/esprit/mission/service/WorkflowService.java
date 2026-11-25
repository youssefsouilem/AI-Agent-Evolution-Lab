package tn.esprit.mission.service;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.mission.dto.*;
import tn.esprit.mission.entity.*;
import tn.esprit.mission.enums.NodeType;
import tn.esprit.mission.repository.*;

@Service
public class WorkflowService {

    private final MissionService missionService;
    private final MissionStepRepository steps;
    private final WorkflowEdgeRepository edges;
    private final MissionExecutionRepository executions;
    private final OrchestrationSettingsService settingsService;

    public WorkflowService(
        MissionService missionService,
        MissionStepRepository steps,
        WorkflowEdgeRepository edges,
        MissionExecutionRepository executions,
        OrchestrationSettingsService settingsService
    ) {
        this.missionService = missionService;
        this.steps = steps;
        this.edges = edges;
        this.executions = executions;
        this.settingsService = settingsService;
    }

    @Transactional(readOnly = true)
    public WorkflowResponse get(Long id) {
        missionService.get(id);
        return new WorkflowResponse(
            steps.findByMissionIdOrderByStepOrderAsc(id),
            edges.findByMissionId(id)
        );
    }

    @Transactional
    public WorkflowResponse replace(Long id, WorkflowRequest request) {
        missionService.getForUpdate(id);
        if (
            !executions.findByMissionIdOrderByIdDesc(id).isEmpty()
        ) throw new IllegalArgumentException(
            "Workflow verrouille apres execution : creez une copie de la mission."
        );
        int maxAttempts = settingsService.get().maxRetries();
        for (var step : request.steps()) {
            if (
                step.nodeType() == NodeType.AGENT &&
                step.maxRetries() != null &&
                step.maxRetries() > maxAttempts
            ) {
                throw new IllegalArgumentException(
                    "« " + step.name() + " » : choisissez entre 1 et " +
                    maxAttempts + " tentatives (limite administrateur)."
                );
            }
        }
        var positions = request
            .steps()
            .stream()
            .map(StepRequest::stepOrder)
            .collect(Collectors.toList());
        if (
            new HashSet<>(positions).size() != positions.size()
        ) throw new IllegalArgumentException(
            "Les ordres des etapes doivent etre uniques."
        );
        for (var step : request.steps()) {
            if (
                step.nodeType() == NodeType.AGENT &&
                (step.agentId() == null || step.agentId().isBlank())
            ) throw new IllegalArgumentException(
                "Une etape AGENT doit avoir un agentId."
            );
        }
        for (var edge : request.edges()) {
            if (
                !positions.stream().anyMatch(position -> position.longValue() == edge.sourceStepOrder()) ||
                !positions.stream().anyMatch(position -> position.longValue() == edge.targetStepOrder())
            ) throw new IllegalArgumentException(
                "Une connexion pointe vers une etape inexistante."
            );
        }
        edges.deleteByMissionId(id);
        steps.deleteByMissionId(id);
        Map<Long, Long> ids = new HashMap<>();
        for (var req : request.steps()) {
            var step = new MissionStep();
            step.setMissionId(id);
            step.setName(req.name());
            step.setStepOrder(req.stepOrder());
            step.setNodeType(req.nodeType());
            step.setAgentId(req.agentId());
            step.setAgentVersionId(req.agentVersionId());
            step.setInstruction(req.instruction());
            step.setInputMapping(req.inputMapping());
            step.setExpectedOutput(req.expectedOutput());
            step.setPositionX(req.positionX());
            step.setPositionY(req.positionY());
            step.setMaxRetries(req.maxRetries());
            step.setConditionVariable(req.conditionVariable());
            step.setConditionOperator(req.conditionOperator());
            step.setConditionExpected(req.conditionExpected());
            step.setConditionSampleValue(req.conditionSampleValue());
            step.setAgentRole(req.agentRole());
            ids.put(req.stepOrder().longValue(), steps.save(step).getId());
        }
        for (var req : request.edges()) {
            var edge = new WorkflowEdge();
            edge.setMissionId(id);
            edge.setSourceStepId(ids.get(req.sourceStepOrder()));
            edge.setTargetStepId(ids.get(req.targetStepOrder()));
            edge.setConditionLabel(req.conditionLabel());
            edge.setCorrection(req.correction());
            edges.save(edge);
        }
        return get(id);
    }
}
