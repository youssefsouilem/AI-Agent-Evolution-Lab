package tn.esprit.mission;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import tn.esprit.mission.dto.OrchestrationSettingsDto;
import tn.esprit.mission.entity.Mission;
import tn.esprit.mission.entity.MissionStep;
import tn.esprit.mission.entity.WorkflowEdge;
import tn.esprit.mission.enums.NodeType;
import tn.esprit.mission.service.EngineWorkflowAdapter;

class ConditionalWorkflowTest {

  final EngineWorkflowAdapter adapter = new EngineWorkflowAdapter();
  final OrchestrationSettingsDto settings = new OrchestrationSettingsDto(
    3,
    300,
    30,
    30,
    2,
    true
  );

  @Test
  void conditionalPlanKeepsBranchLabelsAndCommonSuccessor() {
    var condition = step(2, NodeType.CONDITION);
    condition.setConditionVariable("score");
    condition.setConditionOperator(">=");
    condition.setConditionExpected(70.0);
    condition.setConditionSampleValue(80.0);
    var plan = adapter.convert(
      mission(),
      7L,
      List.of(
        step(1, NodeType.AGENT),
        condition,
        step(3, NodeType.AGENT),
        step(4, NodeType.AGENT),
        step(5, NodeType.AGENT)
      ),
      List.of(
        edge(1, 2, null),
        edge(2, 3, "Oui"),
        edge(2, 4, "Non"),
        edge(3, 5, null),
        edge(4, 5, null)
      ),
      settings
    );
    assertEquals("condition", plan.stages().get(1).kind());
    assertEquals(80.0, plan.stages().get(1).condition().sample());
    assertEquals("yes", plan.stages().get(2).parents().get(0).branch());
    assertEquals("no", plan.stages().get(3).parents().get(0).branch());
    assertEquals(2, plan.stages().get(4).parents().size());
  }

  @Test
  void sequentialAndParallelPlansRemainCompatible() {
    var steps = List.of(
      step(1, NodeType.AGENT),
      step(2, NodeType.AGENT),
      step(3, NodeType.AGENT)
    );
    var sequence = adapter.convert(
      mission(),
      7L,
      steps,
      List.of(edge(1, 2, null), edge(2, 3, null)),
      settings
    );
    assertEquals(
      List.of("agent", "agent", "agent"),
      sequence
        .stages()
        .stream()
        .map(stage -> stage.kind())
        .toList()
    );
    assertNull(sequence.stages().get(0).parents());
    var parallel = adapter.convert(
      mission(),
      8L,
      steps,
      List.of(edge(1, 2, null), edge(1, 3, null)),
      settings
    );
    assertEquals("parallel", parallel.stages().get(1).kind());
    assertEquals(2, parallel.stages().get(1).agents().size());
  }

  @Test
  void cyclesAndMissingConditionalBranchesAreRejected() {
    var steps = List.of(
      step(1, NodeType.AGENT),
      step(2, NodeType.AGENT),
      step(3, NodeType.AGENT)
    );
    assertThrows(IllegalArgumentException.class, () ->
      adapter.convert(
        mission(),
        7L,
        steps,
        List.of(edge(1, 2, null), edge(2, 3, null), edge(3, 2, null)),
        settings
      )
    );
    var condition = step(2, NodeType.CONDITION);
    assertThrows(IllegalArgumentException.class, () ->
      adapter.convert(
        mission(),
        7L,
        List.of(step(1, NodeType.AGENT), condition, step(3, NodeType.AGENT)),
        List.of(edge(1, 2, null), edge(2, 3, "yes")),
        settings
      )
    );
  }

  @Test
  void explicitCorrectionIsBoundedAndOrdinaryCycleStillFails() {
    var fixer = step(1, NodeType.AGENT);
    fixer.setMaxRetries(2);
    var reviewer = step(2, NodeType.AGENT);
    reviewer.setMaxRetries(2);
    var check = step(3, NodeType.CONDITION);
    check.setConditionVariable("valid");
    check.setConditionOperator("is_true");
    check.setConditionExpected(1.0);
    check.setConditionSampleValue(0.0);
    var back = edge(3, 1, "no");
    var steps = List.of(fixer, reviewer, check, step(4, NodeType.AGENT));
    var edges = List.of(edge(1, 2, null), edge(2, 3, null), edge(3, 4, "yes"), back);
    assertThrows(IllegalArgumentException.class, () -> adapter.convert(mission(), 9L, steps, edges, settings));
    back.setCorrection(true);
    var plan = adapter.convert(mission(), 9L, steps, edges, settings);
    assertEquals("1", plan.stages().get(2).condition().correction().target());
    assertEquals(2, plan.stages().get(2).condition().correction().maxAttempts());
    assertEquals(List.of("1", "2", "3"), plan.stages().get(2).condition().correction().steps());
    assertTrue(plan.stages().get(0).parents().isEmpty());
  }

  @Test
  void correctionCannotUseYesOrReturnToACondition() {
    var check = step(2, NodeType.CONDITION);
    check.setConditionOperator(">=");
    check.setConditionExpected(1.0);
    var wrong = edge(2, 1, "yes");
    wrong.setCorrection(true);
    assertThrows(IllegalArgumentException.class, () -> adapter.convert(mission(), 9L,
      List.of(step(1, NodeType.AGENT), check, step(3, NodeType.AGENT)),
      List.of(edge(1, 2, null), wrong, edge(2, 3, "no")), settings));
  }

  private Mission mission() {
    var mission = new Mission();
    mission.setId(1L);
    mission.setObjective("Test condition");
    return mission;
  }

  private MissionStep step(long id, NodeType type) {
    var step = new MissionStep();
    step.setId(id);
    step.setStepOrder((int) id);
    step.setNodeType(type);
    step.setAgentId("1");
    step.setAgentVersionId("11");
    step.setInstruction("Analyser");
    return step;
  }

  private WorkflowEdge edge(long source, long target, String branch) {
    var edge = new WorkflowEdge();
    edge.setSourceStepId(source);
    edge.setTargetStepId(target);
    edge.setConditionLabel(branch);
    return edge;
  }
}
