package tn.esprit.mission.dto;

import jakarta.validation.constraints.NotNull;

public record EdgeRequest(
@NotNull
    @jakarta.validation.constraints.Min(0)
    Long sourceStepOrder,
@NotNull
    @jakarta.validation.constraints.Min(0)
    Long targetStepOrder,
    @jakarta.validation.constraints.Size(max = 200) String conditionLabel,
    Boolean correction
) {
    public EdgeRequest(Long sourceStepOrder, Long targetStepOrder, String conditionLabel) {
        this(sourceStepOrder, targetStepOrder, conditionLabel, false);
    }
}
