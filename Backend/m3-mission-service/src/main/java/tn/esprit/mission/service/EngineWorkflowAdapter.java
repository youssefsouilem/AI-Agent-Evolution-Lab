package tn.esprit.mission.service;

import java.util.*;
import org.springframework.stereotype.Component;
import tn.esprit.mission.dto.EngineRequest;
import tn.esprit.mission.dto.OrchestrationSettingsDto;
import tn.esprit.mission.entity.*;
import tn.esprit.mission.enums.NodeType;

@Component
public class EngineWorkflowAdapter {

  public EngineRequest convert(
    Mission mission,
    Long executionId,
    List<MissionStep> steps,
    List<WorkflowEdge> edges,
    OrchestrationSettingsDto settings
  ) {
    Map<Long, MissionStep> byId = new HashMap<>();
    Map<Long, List<Long>> outgoing = new HashMap<>();
    Map<Long, Integer> incoming = new HashMap<>();
    for (var step : steps) {
      byId.put(step.getId(), step);
      outgoing.put(step.getId(), new ArrayList<>());
      incoming.put(step.getId(), 0);
    }
    Set<String> unique = new HashSet<>();
    for (var edge : edges) {
      Long source = edge.getSourceStepId(),
        target = edge.getTargetStepId();
      if (
        !byId.containsKey(source) ||
        !byId.containsKey(target) ||
        source.equals(target) ||
        !unique.add(source + ":" + target)
      ) throw new IllegalArgumentException("Connexion invalide ou dupliquée.");
      if (edge.isCorrection()) continue;
      outgoing.get(source).add(target);
      incoming.put(target, incoming.get(target) + 1);
    }
    List<Long> ready = incoming
      .keySet()
      .stream()
      .filter(id -> incoming.get(id) == 0)
      .toList();
    if (ready.size() != 1) throw new IllegalArgumentException(
      "Le workflow doit avoir un seul début commun."
    );
    for (var step : steps) {
      if (
        (step.getNodeType() == NodeType.START &&
          incoming.get(step.getId()) != 0) ||
        (step.getNodeType() == NodeType.END &&
          !outgoing.get(step.getId()).isEmpty())
      ) {
        throw new IllegalArgumentException(
          "Les nœuds Début et Fin doivent être aux extrémités."
        );
      }
    }
    boolean conditional = steps
      .stream()
      .anyMatch(step -> step.getNodeType() == NodeType.CONDITION);
    for (var step : steps) {
      if (step.getNodeType() != NodeType.CONDITION) continue;
      if (!settings.allowConditions()) throw new IllegalArgumentException(
        "Conditions désactivées par l'administrateur."
      );
      var branches = edges
        .stream()
        .filter(edge -> edge.getSourceStepId().equals(step.getId()))
        .toList();
      if (
        branches.size() != 2 ||
        branches
          .stream()
          .map(edge -> branch(edge.getConditionLabel()))
          .distinct()
          .count() != 2 ||
        branches
          .stream()
          .anyMatch(edge -> branch(edge.getConditionLabel()) == null)
      ) {
        throw new IllegalArgumentException(
          "Chaque condition nécessite exactement une branche Oui et une branche Non."
        );
      }
      if (
        !Set.of("=", "!=", ">", ">=", "<", "<=", "is_true", "is_false").contains(
          Objects.toString(step.getConditionOperator(), "")
        ) ||
        step.getConditionExpected() == null ||
        !Double.isFinite(step.getConditionExpected()) ||
        (step.getConditionSampleValue() != null &&
          !Double.isFinite(step.getConditionSampleValue()))
      ) {
        throw new IllegalArgumentException(
          "Opérateur ou seuil de condition invalide."
        );
      }
    }
    Map<Long, EngineRequest.Correction> corrections = new HashMap<>();
    Set<Long> correctionBody = new HashSet<>();
    for (var edge : edges) {
      if (!edge.isCorrection()) continue;
      var source = byId.get(edge.getSourceStepId());
      var target = byId.get(edge.getTargetStepId());
      if (source.getNodeType() != NodeType.CONDITION || target.getNodeType() != NodeType.AGENT || !"no".equals(branch(edge.getConditionLabel()))) {
        throw new IllegalArgumentException("Un retour de correction doit relier Non d'une condition à un agent précédent.");
      }
      List<String> body = new ArrayList<>();
      Set<Long> seen = new HashSet<>();
      Long cursor = target.getId();
      while (true) {
        if (!seen.add(cursor) || !correctionBody.add(cursor)) throw new IllegalArgumentException("Boucles de correction imbriquées ou chevauchantes refusées.");
        body.add(cursor.toString());
        if (cursor.equals(source.getId())) break;
        if (byId.get(cursor).getNodeType() != NodeType.AGENT || outgoing.get(cursor).size() != 1 ||
            !cursor.equals(target.getId()) && incoming.get(cursor) != 1) {
          throw new IllegalArgumentException("Le segment de correction doit être une chaîne d'agents jusqu'à la condition.");
        }
        cursor = outgoing.get(cursor).get(0);
      }
      if (incoming.get(source.getId()) != 1) throw new IllegalArgumentException("Une condition de correction doit avoir un seul prédécesseur.");
      int limit = Math.min(settings.maxRetries(), target.getMaxRetries() == null ? settings.maxRetries() : target.getMaxRetries());
      for (String member : body) {
        var step = byId.get(Long.valueOf(member));
        if (step.getNodeType() == NodeType.AGENT && step.getMaxRetries() != null) limit = Math.min(limit, step.getMaxRetries());
      }
      limit = Math.min(limit, mission.getMaxAttempts() == null ? settings.maxRetries() : mission.getMaxAttempts());
      corrections.put(source.getId(), new EngineRequest.Correction(target.getId().toString(), limit, body));
    }
    List<EngineRequest.Stage> stages = new ArrayList<>();
    int visited = 0;
    while (!ready.isEmpty()) {
      var level = ready
        .stream()
        .map(byId::get)
        .sorted(Comparator.comparing(MissionStep::getStepOrder))
        .toList();
      List<EngineRequest.Stage> agents = new ArrayList<>();
      List<Long> next = new ArrayList<>();
      for (var step : level) {
        visited++;
        if (conditional) {
          var parents = edges
            .stream()
            .filter(edge -> !edge.isCorrection() && edge.getTargetStepId().equals(step.getId()))
            .map(edge ->
              new EngineRequest.Parent(
                edge.getSourceStepId().toString(),
                byId.get(edge.getSourceStepId()).getNodeType() ==
                  NodeType.CONDITION
                  ? branch(edge.getConditionLabel())
                  : null
              )
            )
            .toList();
          var condition =
            step.getNodeType() == NodeType.CONDITION
              ? new EngineRequest.Condition(
                  step.getConditionVariable(),
                  step.getConditionOperator(),
                  step.getConditionExpected(),
                  step.getConditionSampleValue(),
                  corrections.get(step.getId())
                )
              : null;
          stages.add(
            new EngineRequest.Stage(
              step.getId().toString(),
              step.getNodeType() == NodeType.AGENT
                ? "agent"
                : step.getNodeType() == NodeType.CONDITION
                  ? "condition"
                  : "control",
              step.getAgentId(),
              step.getAgentVersionId(),
              step.getInstruction(),
              step.getInputMapping(),
              step.getExpectedOutput(),
              step.getMaxRetries() == null
                ? settings.maxRetries()
                : step.getMaxRetries(),
              null,
              parents,
              condition
            )
          );
        } else if (step.getNodeType() == NodeType.AGENT) {
          agents.add(
            new EngineRequest.Stage(
              step.getId().toString(),
              "agent",
              step.getAgentId(),
              step.getAgentVersionId(),
              step.getInstruction(),
              step.getInputMapping(),
              step.getExpectedOutput(),
              step.getMaxRetries() == null
                ? settings.maxRetries()
                : step.getMaxRetries(),
              null
            )
          );
        }
        for (Long target : outgoing.get(step.getId())) {
          incoming.put(target, incoming.get(target) - 1);
          if (incoming.get(target) == 0) next.add(target);
        }
      }
      if (agents.size() == 1) stages.add(agents.get(0));
      else if (!agents.isEmpty()) stages.add(
        new EngineRequest.Stage(
          "parallel-" + stages.size(),
          "parallel",
          null,
          null,
          null,
          null,
          null,
          settings.maxRetries(),
          agents
        )
      );
      ready = next;
    }
    if (visited != steps.size()) throw new IllegalArgumentException(
      "Workflow cyclique ou déconnecté : exécution refusée."
    );
    if (
      steps.stream().noneMatch(step -> step.getNodeType() == NodeType.AGENT)
    ) throw new IllegalArgumentException("Ajoutez au moins un agent.");
    return new EngineRequest(
      mission.getId(),
      executionId.toString(),
      mission.getObjective(),
      settings.maxDuration(),
      settings.maxStepDuration(),
      Math.min(32, settings.maxConcurrent()),
      stages
    );
  }

  private String branch(String label) {
    if (label == null) return null;
    return switch (label.trim().toLowerCase(Locale.ROOT)) {
      case "yes", "oui" -> "yes";
      case "no", "non" -> "no";
      default -> null;
    };
  }
}
