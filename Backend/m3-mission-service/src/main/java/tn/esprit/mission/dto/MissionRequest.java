package tn.esprit.mission.dto;

import jakarta.validation.constraints.*;
import tn.esprit.mission.enums.SelectionMode;

public record MissionRequest(
@NotBlank
    @Size(max = 160)
    String name,
@NotBlank
    @Size(max = 4000)
    String objective,
@NotNull
    SelectionMode selectionMode,
@NotNull
    @Min(1)
    @Max(20)
    Integer maxAttempts,
@Size(max = 100)
    String ownerId
) {}
