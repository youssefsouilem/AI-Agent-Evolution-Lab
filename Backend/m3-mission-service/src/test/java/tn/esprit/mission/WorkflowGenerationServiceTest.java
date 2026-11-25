package tn.esprit.mission;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;
import tn.esprit.mission.dto.*;
import tn.esprit.mission.entity.Mission;
import tn.esprit.mission.service.*;

class WorkflowGenerationServiceTest {

  @Test
  void validatesRouterPlanAndRejectsUnknownVersionsOrCycles() throws Exception {
    var missions = mock(MissionService.class);
    var catalogue = mock(AgentCatalogService.class);
    var client = mock(OrchestratorClient.class);
    var settings = mock(OrchestrationSettingsService.class);
    var mapper = new ObjectMapper();
    var mission = new Mission();
    mission.setId(1L);
    mission.setObjective("Voyage");
    when(missions.get(1L)).thenReturn(mission);
    when(client.enabled()).thenReturn(true);
    when(catalogue.getAgents()).thenReturn(
      List.of(
        new AgentResponse(
          1L,
          "Router",
          "",
          "ACTIVE",
          List.of(new AgentVersionResponse(11L, 1, "model", "", true, null))
        )
      )
    );
    when(settings.get()).thenReturn(
      new OrchestrationSettingsDto(3, 300, 30, 30, 2, true)
    );
    String plan = """
    {"steps":[{"name":"Planifier","stepOrder":0,"nodeType":"AGENT","agentId":"1","agentVersionId":"11","instruction":"Planifier le voyage"}],"edges":[]}
    """;
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var service = new WorkflowGenerationService(
        missions,
        catalogue,
        client,
        settings,
        new EngineWorkflowAdapter(),
        mapper,
        factory.getValidator(),
        true,
        "1",
        "11"
      );
      when(client.generate(any())).thenReturn(mapper.readTree(plan));
      assertEquals(
        "11",
        service.generate(1L, "Voyage").steps().get(0).agentVersionId()
      );
      when(client.generate(any())).thenReturn(
        mapper.readTree(
          plan.replace("\"agentVersionId\":\"11\"", "\"agentVersionId\":\"99\"")
        )
      );
      assertThrows(IllegalArgumentException.class, () ->
        service.generate(1L, "Voyage")
      );
      when(client.generate(any())).thenReturn(
        mapper.readTree(
          plan.replace(
            "\"edges\":[]",
            "\"edges\":[{\"sourceStepOrder\":0,\"targetStepOrder\":0}]"
          )
        )
      );
      assertThrows(IllegalArgumentException.class, () ->
        service.generate(1L, "Voyage")
      );
      var disabled = new WorkflowGenerationService(
        missions,
        catalogue,
        client,
        settings,
        new EngineWorkflowAdapter(),
        mapper,
        factory.getValidator(),
        false,
        "",
        ""
      );
      assertThrows(IllegalArgumentException.class, () ->
        disabled.generate(1L, "Voyage")
      );
    }
  }
}
