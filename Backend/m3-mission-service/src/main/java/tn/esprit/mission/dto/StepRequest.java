package tn.esprit.mission.dto;

import jakarta.validation.constraints.*;
import tn.esprit.mission.enums.NodeType;

public record StepRequest(
    @NotBlank @Size(max = 160) String name,
    @NotNull @Min(0) Integer stepOrder,
    @NotNull NodeType nodeType,
    @Size(max = 100) String agentId,
    @Size(max = 100) String agentVersionId,
    @Size(max = 4000) String instruction,
    String inputMapping,
    @Size(max = 1000) String expectedOutput,
    Double positionX,
    Double positionY,
    @Min(1) @Max(20) Integer maxRetries,
    @Size(max = 100) String conditionVariable,
    @Pattern(regexp = "=|!=|>|>=|<|<=|is_true|is_false") String conditionOperator,
    Double conditionExpected,
    Double conditionSampleValue,
    @Size(max = 255) String agentRole
) {
    @AssertTrue(
        message = "Les coordonnées et valeurs de condition doivent être des nombres finis"
    )
    public boolean isNumericConfigurationValid() {
        return (
            finite(positionX) &&
            finite(positionY) &&
            finite(conditionExpected) &&
            finite(conditionSampleValue)
        );
    }

    private static boolean finite(Double value) {
        return value == null || Double.isFinite(value);
    }
}
