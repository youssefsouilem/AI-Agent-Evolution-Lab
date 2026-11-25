package tn.esprit.mission.dto;

import java.util.List;
import tn.esprit.mission.entity.MissionStep;
import tn.esprit.mission.entity.WorkflowEdge;

public record WorkflowResponse(
    List<MissionStep> steps,
    List<WorkflowEdge> edges
) {}
