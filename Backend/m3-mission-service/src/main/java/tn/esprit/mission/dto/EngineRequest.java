package tn.esprit.mission.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record EngineRequest(
  @JsonProperty("mission_id") Long missionId,
  @JsonProperty("execution_id") String executionId,
  String instruction,
  @JsonProperty("max_duration") int maxDuration,
  @JsonProperty("max_step_duration") int maxStepDuration,
  @JsonProperty("max_parallel") int maxParallel,
  List<Stage> stages
) {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Stage(
    String id,
    String kind,
    String agentId,
    String agentVersionId,
    String instruction,
    String inputMapping,
    String expectedOutput,
    @JsonProperty("max_attempts") int maxAttempts,
    List<Stage> agents,
    List<Parent> parents,
    Condition condition
  ) {
    public Stage(
      String id,
      String kind,
      String agentId,
      String agentVersionId,
      String instruction,
      String inputMapping,
      String expectedOutput,
      int maxAttempts,
      List<Stage> agents
    ) {
      this(
        id,
        kind,
        agentId,
        agentVersionId,
        instruction,
        inputMapping,
        expectedOutput,
        maxAttempts,
        agents,
        null,
        null
      );
    }
  }

  public record Parent(String id, String branch) {}

  public record Condition(
    String variable,
    String operator,
    Double expected,
    Double sample,
    Correction correction
  ) {
    public Condition(String variable, String operator, Double expected, Double sample) {
      this(variable, operator, expected, sample, null);
    }
  }

  public record Correction(String target, int maxAttempts, List<String> steps) {}
}
