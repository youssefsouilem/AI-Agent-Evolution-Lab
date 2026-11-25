package tn.esprit.mission.service;

import com.fasterxml.jackson.databind.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.mission.dto.EngineRequest;
import tn.esprit.mission.entity.MissionExecution;
import tn.esprit.mission.entity.MissionStepAttempt;
import tn.esprit.mission.enums.*;
import tn.esprit.mission.exception.ResourceNotFoundException;
import tn.esprit.mission.repository.*;

@Service
public class ExecutionStateStore {

  private final MissionExecutionRepository executions;
  private final MissionStepExecutionRepository details;
  private final MissionStepRepository steps;
  private final ObjectMapper mapper;
  private final MissionStepAttemptRepository attemptJournal;
  private final MissionEventPublisher events;

  public ExecutionStateStore(
    MissionExecutionRepository executions,
    MissionStepExecutionRepository details,
    MissionStepRepository steps,
    ObjectMapper mapper,
    MissionStepAttemptRepository attemptJournal,
    MissionEventPublisher events
  ) {
    this.executions = executions;
    this.details = details;
    this.steps = steps;
    this.mapper = mapper;
    this.attemptJournal = attemptJournal;
    this.events = events;
  }

  @Transactional(readOnly = true)
  public EngineRequest request(Long id) {
    try {
      return mapper.readValue(
        find(id).getOrchestratorRequest(),
        EngineRequest.class
      );
    } catch (Exception ex) {
      throw new IllegalArgumentException(
        "Définition d'exécution absente ou invalide.",
        ex
      );
    }
  }

  private MissionExecution find(Long id) {
    return executions
      .findById(id)
      .orElseThrow(() ->
        new ResourceNotFoundException("Exécution introuvable : " + id)
      );
  }

  private MissionExecution locked(Long id) {
    return executions
      .findByIdForUpdate(id)
      .orElseThrow(() ->
        new ResourceNotFoundException("Exécution introuvable : " + id)
      );
  }

  @Transactional
  public void begin(Long id) {
    var execution = locked(id);
    execution.setStatus(ExecutionStatus.RUNNING);
    if (execution.getStartedAt() == null) execution.setStartedAt(Instant.now());
    execution.setErrorMessage(null);
    execution.setMetricsSource("UNKNOWN");
    execution.setTotalTokensUsed(null);
  }

  @Transactional
  public void uncertain(Long id, String message) {
    var execution = locked(id);
    if (
      !List.of(
        ExecutionStatus.COMPLETED,
        ExecutionStatus.FAILED,
        ExecutionStatus.CANCELLED
      ).contains(execution.getStatus())
    ) {
      execution.setStatus(ExecutionStatus.RECOVERY_REQUIRED);
      execution.setErrorMessage(message);
    }
  }

  @Transactional
  public void apply(Long id, JsonNode snapshot) {
    var execution = locked(id);
    if (
      snapshot == null ||
      !id.toString().equals(snapshot.path("execution_id").asText()) ||
      snapshot.path("state").path("mission_id").asLong(-1) !=
        execution.getMissionId()
    ) {
      throw new IllegalArgumentException(
        "État Python absent ou associé à une autre exécution."
      );
    }
    ExecutionStatus status;
    try {
      status = ExecutionStatus.valueOf(snapshot.path("status").asText());
    } catch (Exception ex) {
      throw new IllegalArgumentException("Statut Python inconnu.", ex);
    }
    if (
      !List.of(
        ExecutionStatus.RUNNING,
        ExecutionStatus.PAUSED,
        ExecutionStatus.COMPLETED,
        ExecutionStatus.FAILED,
        ExecutionStatus.CANCELLED
      ).contains(status)
    ) {
      throw new IllegalArgumentException(
        "Transition Python non prise en charge."
      );
    }
    if (
      List.of(
        ExecutionStatus.COMPLETED,
        ExecutionStatus.FAILED,
        ExecutionStatus.CANCELLED
      ).contains(execution.getStatus())
    ) return;
    var state = snapshot.path("state");
    Set<String> skipped = new HashSet<>();
    state.path("skipped_steps").forEach(value -> skipped.add(value.asText()));
    var controls = state.path("control_results");
    var results = state.path("results");
    if (!results.isArray()) throw new IllegalArgumentException(
      "Liste des résultats Python absente."
    );
    Map<String, JsonNode> unique = new LinkedHashMap<>();
    for (var result : results) {
      String key =
        result.path("batch").asText("sequential") +
        ":" +
        result.path("stepId").asText() +
        ":" +
        result.path("attempt").asInt(1);
      unique.put(key, result);
    }
    Map<String, List<JsonNode>> perStep = new HashMap<>();
    long tokens = 0;
    boolean completeUsage = true;
    for (var result : unique.values()) {
      perStep
        .computeIfAbsent(result.path("stepId").asText(), key ->
          new ArrayList<>()
        )
        .add(result);
      var usage = result.path("output").get("tokensUsed");
      if (
        usage == null || !usage.isIntegralNumber() || usage.asLong() < 0
      ) completeUsage = false;
      else tokens = Math.addExact(tokens, usage.asLong());
    }
    var savedSteps = steps.findByMissionIdOrderByStepOrderAsc(
      execution.getMissionId()
    );
    for (String skippedId : skipped) {
      if (
        savedSteps
          .stream()
          .noneMatch(step -> step.getId().toString().equals(skippedId)) ||
        perStep.containsKey(skippedId)
      ) {
        throw new IllegalArgumentException(
          "Étape ignorée inconnue ou déjà exécutée."
        );
      }
    }
    var nodeTypes = new HashMap<Long, NodeType>();
    savedSteps.forEach(step -> nodeTypes.put(step.getId(), step.getNodeType()));
    for (var entry : perStep.entrySet()) {
      if (
        savedSteps
          .stream()
          .noneMatch(
            step ->
              step.getNodeType() == NodeType.AGENT &&
              step.getId().toString().equals(entry.getKey())
          )
      ) {
        throw new IllegalArgumentException(
          "Résultat associé à une étape inconnue."
        );
      }
      var lastOutput = entry
        .getValue()
        .get(entry.getValue().size() - 1)
        .path("output");
      if (
        status == ExecutionStatus.COMPLETED &&
        (!"SUCCESS".equals(lastOutput.path("status").asText()) ||
          !lastOutput.hasNonNull("output"))
      ) {
        throw new IllegalArgumentException(
          "Fin annoncée avec un résultat agent absent ou en échec."
        );
      }
    }
    for (var result : unique.values()) {
      Long stepId = Long.valueOf(result.path("stepId").asText());
      int attemptNumber = result.path("attempt").asInt(1);
      if (attemptNumber < 1) throw new IllegalArgumentException("Numéro de tentative invalide.");
      if (attemptJournal.existsByMissionExecutionIdAndMissionStepIdAndAttemptNumber(id, stepId, attemptNumber)) continue;
      var attempt = new MissionStepAttempt();
      var output = result.path("output");
      attempt.missionExecutionId = id;
      attempt.missionStepId = stepId;
      attempt.attemptNumber = attemptNumber;
      attempt.agentId = result.path("agentId").asText(null);
      attempt.agentVersionId = savedSteps.stream().filter(step -> step.getId().equals(stepId)).findFirst().orElseThrow().getAgentVersionId();
      attempt.status = "SUCCESS".equals(output.path("status").asText()) ? "COMPLETED" : "FAILED";
      attempt.output = output.path("output").asText(null);
      attempt.errorMessage = output.path("error").asText(null);
      if (output.hasNonNull("startedAt")) attempt.startedAt = Instant.parse(output.get("startedAt").asText());
      if (output.hasNonNull("finishedAt")) attempt.finishedAt = Instant.parse(output.get("finishedAt").asText());
      if (output.path("tokensUsed").isIntegralNumber() && output.get("tokensUsed").asLong() >= 0) attempt.tokensUsed = output.get("tokensUsed").asLong();
      attemptJournal.save(attempt);
    }
    boolean finished = List.of(
      ExecutionStatus.COMPLETED,
      ExecutionStatus.FAILED,
      ExecutionStatus.CANCELLED
    ).contains(status);
    if (
      status == ExecutionStatus.COMPLETED &&
      savedSteps
        .stream()
        .anyMatch(
          step ->
            step.getNodeType() == NodeType.AGENT &&
            !perStep.containsKey(step.getId().toString()) &&
            !skipped.contains(step.getId().toString())
        )
    ) {
      throw new IllegalArgumentException(
        "Le moteur annonce une fin sans résultat pour chaque agent."
      );
    }
    for (var detail : details.findByMissionExecutionId(id)) {
      String stepId = detail.getMissionStepId().toString();
      if (skipped.contains(stepId)) {
        detail.setStatus(ExecutionStatus.SKIPPED);
        detail.setAttemptNumber(0);
        detail.setTokensUsed(0L);
        detail.setOutput(null);
        continue;
      }
      if (controls.has(stepId)) {
        var control = controls.get(stepId);
        detail.setStatus(
          control.has("error")
            ? ExecutionStatus.FAILED
            : ExecutionStatus.COMPLETED
        );
        detail.setOutput(control.toString());
        detail.setErrorMessage(control.path("error").asText(null));
      }
      var attempts = perStep.get(stepId);
      if (attempts == null || attempts.isEmpty()) {
        if (controls.has(stepId)) continue;
        if (
          status == ExecutionStatus.COMPLETED &&
          nodeTypes.get(detail.getMissionStepId()) != NodeType.AGENT
        ) detail.setStatus(ExecutionStatus.COMPLETED);
        else if (finished) detail.setStatus(ExecutionStatus.CANCELLED);
        continue;
      }
      var output = attempts.get(attempts.size() - 1).path("output");
      boolean success = "SUCCESS".equals(output.path("status").asText());
      detail.setStatus(
        success ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED
      );
      detail.setAttemptNumber(attempts.size());
      detail.setOutput(success ? output.path("output").asText(null) : null);
      detail.setErrorMessage(
        success ? null : output.path("error").asText("Échec agent")
      );
      Long used = 0L;
      for (var attempt : attempts) {
        var value = attempt.path("output").get("tokensUsed");
        if (value == null || !value.isIntegralNumber() || value.asLong() < 0) {
          used = null;
          break;
        }
        used = Math.addExact(used, value.asLong());
      }
      detail.setTokensUsed(used);
      var first = attempts.get(0).path("output");
      if (first.hasNonNull("startedAt")) detail.setStartedAt(
        Instant.parse(first.get("startedAt").asText())
      );
      if (output.hasNonNull("finishedAt")) detail.setFinishedAt(
        Instant.parse(output.get("finishedAt").asText())
      );
    }
    execution.setStatus(status);
    execution.setOrchestratorState(snapshot.toString());
    execution.setErrorMessage(
      state.path("error").asText("").isBlank()
        ? null
        : state.get("error").asText()
    );
    execution.setAttemptsUsed(unique.size());
    execution.setTotalTokensUsed(completeUsage ? tokens : null);
    execution.setMetricsSource(
      snapshot.has("simulated")
        ? snapshot.get("simulated").asBoolean()
          ? "SIMULATED"
          : "MEASURED"
        : "UNKNOWN"
    );
    if (state.hasNonNull("started_at")) execution.setStartedAt(
      Instant.parse(state.get("started_at").asText())
    );
    if (finished) execution.setFinishedAt(
      state.hasNonNull("finished_at")
        ? Instant.parse(state.get("finished_at").asText())
        : Instant.now()
    );
    if (status == ExecutionStatus.COMPLETED) {
      var request = request(id);
      var last = request.stages().get(request.stages().size() - 1);
      var outputs = state.path("outputs");
      if (
        request
          .stages()
          .stream()
          .anyMatch(stage -> stage.parents() != null)
      ) {
        String candidate = null;
        for (var executed : state.path("executed_steps")) {
          if (outputs.path(executed.asText()).hasNonNull("output")) candidate =
            outputs.get(executed.asText()).get("output").asText();
        }
        execution.setCandidateResult(candidate);
      } else if ("parallel".equals(last.kind())) {
        var combined = mapper.createObjectNode();
        last
          .agents()
          .forEach(agent ->
            combined.set(agent.id(), outputs.path(agent.id()).path("output"))
          );
        execution.setCandidateResult(combined.toString());
      } else execution.setCandidateResult(
        outputs.path(last.id()).path("output").asText(null)
      );
      execution.setPublicationStatus("PENDING_MONITORING");
    }
    // finalResult is populated only after the future server-side publication decision.
    execution.setFinalResult(null);
    if (finished) events.enqueue(execution);
  }
}
