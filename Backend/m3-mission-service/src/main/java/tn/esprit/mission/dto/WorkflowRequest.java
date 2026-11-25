package tn.esprit.mission.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record WorkflowRequest(
@NotNull
    List<@NotNull @Valid StepRequest> steps,
@NotNull
    List<@NotNull @Valid EdgeRequest> edges
) {}
