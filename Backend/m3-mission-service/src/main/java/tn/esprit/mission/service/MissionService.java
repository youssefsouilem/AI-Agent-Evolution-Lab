package tn.esprit.mission.service;

import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.mission.dto.MissionRequest;
import tn.esprit.mission.entity.Mission;
import tn.esprit.mission.enums.ExecutionStatus;
import tn.esprit.mission.exception.ResourceNotFoundException;
import tn.esprit.mission.repository.*;

@Service
public class MissionService {

    private final MissionRepository missions;
    private final MissionStepRepository steps;
    private final WorkflowEdgeRepository edges;
    private final MissionParticipantRepository participants;
    private final MissionExecutionRepository executions;

    public MissionService(
        MissionRepository missions,
        MissionStepRepository steps,
        WorkflowEdgeRepository edges,
        MissionParticipantRepository participants,
        MissionExecutionRepository executions
    ) {
        this.missions = missions;
        this.steps = steps;
        this.edges = edges;
        this.participants = participants;
        this.executions = executions;
    }

    @Transactional
    public Mission create(MissionRequest request) {
        Mission mission = new Mission();
        apply(mission, request);
        return missions.save(mission);
    }

    @Transactional(readOnly = true)
    public Page<Mission> list(String search, Pageable pageable) {
        if (MissionAccess.subject() != null && !MissionAccess.admin()) {
            return missions.findByOwnerIdAndNameContainingIgnoreCase(MissionAccess.subject(), search == null ? "" : search, pageable);
        }
        return search == null || search.isBlank()
            ? missions.findAll(pageable)
            : missions.findByNameContainingIgnoreCase(search, pageable);
    }

    @Transactional(readOnly = true)
    public Mission get(Long id) {
        var mission = missions
            .findById(id)
            .orElseThrow(() ->
                new ResourceNotFoundException("Mission introuvable : " + id)
            );
        MissionAccess.check(mission);
        return mission;
    }

    @Transactional
    public Mission update(Long id, MissionRequest request) {
        Mission mission = get(id);
        apply(mission, request);
        return missions.save(mission);
    }

    @Transactional
    public Mission getForUpdate(Long id) {
        var mission = missions.findByIdForUpdate(id).orElseThrow(() ->
            new ResourceNotFoundException("Mission introuvable : " + id)
        );
        MissionAccess.check(mission);
        return mission;
    }

    @Transactional
    public void delete(Long id) {
        getForUpdate(id);
        if (
            !executions.findByMissionIdOrderByIdDesc(id).isEmpty()
        ) throw new IllegalArgumentException(
            "Cette mission a des executions : suppression refusee pour conserver l'historique."
        );
        edges.deleteByMissionId(id);
        steps.deleteByMissionId(id);
        participants.deleteByMissionId(id);
        missions.deleteById(id);
    }

    @Transactional
    public Mission duplicate(Long id) {
        Mission original = get(id);
        String suffix = " (copie)";
        String name = original.getName();
        if (name.length() > 160 - suffix.length()) {
            name = name.substring(0, 160 - suffix.length());
        }
        MissionRequest copy = new MissionRequest(
            name + suffix,
            original.getObjective(),
            original.getSelectionMode(),
            original.getMaxAttempts(),
            original.getOwnerId()
        );
        Mission result = create(copy);
        var oldSteps = steps.findByMissionIdOrderByStepOrderAsc(id);
        Map<Long, Long> idMap = new HashMap<>();
        for (var step : oldSteps) {
            var next = new tn.esprit.mission.entity.MissionStep();
            next.setMissionId(result.getId());
            next.setName(step.getName());
            next.setStepOrder(step.getStepOrder());
            next.setNodeType(step.getNodeType());
            next.setAgentId(step.getAgentId());
            next.setAgentVersionId(step.getAgentVersionId());
            next.setInstruction(step.getInstruction());
            next.setInputMapping(step.getInputMapping());
            next.setExpectedOutput(step.getExpectedOutput());
            next.setPositionX(step.getPositionX());
            next.setPositionY(step.getPositionY());
            next.setMaxRetries(step.getMaxRetries());
            next.setConditionVariable(step.getConditionVariable());
            next.setConditionOperator(step.getConditionOperator());
            next.setConditionExpected(step.getConditionExpected());
            next.setConditionSampleValue(step.getConditionSampleValue());
            next.setAgentRole(step.getAgentRole());
            next = steps.save(next);
            idMap.put(step.getId(), next.getId());
        }
        for (var edge : edges.findByMissionId(id)) {
            var next = new tn.esprit.mission.entity.WorkflowEdge();
            next.setMissionId(result.getId());
            next.setSourceStepId(idMap.get(edge.getSourceStepId()));
            next.setTargetStepId(idMap.get(edge.getTargetStepId()));
            next.setConditionLabel(edge.getConditionLabel());
            next.setCorrection(edge.isCorrection());
            edges.save(next);
        }
        for (var participant : participants.findByMissionId(id)) {
            var next = new tn.esprit.mission.entity.MissionParticipant();
            next.setMissionId(result.getId());
            next.setAgentId(participant.getAgentId());
            next.setAgentVersionId(participant.getAgentVersionId());
            next.setRole(participant.getRole());
            participants.save(next);
        }
        return result;
    }

    private void apply(Mission m, MissionRequest r) {
        m.setName(r.name());
        m.setObjective(r.objective());
        m.setSelectionMode(r.selectionMode());
        m.setMaxAttempts(r.maxAttempts());
        if (MissionAccess.subject() == null) m.setOwnerId(r.ownerId());
        else if (m.getOwnerId() == null) m.setOwnerId(MissionAccess.subject());
    }
}
