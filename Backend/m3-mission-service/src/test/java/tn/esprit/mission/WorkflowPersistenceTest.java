package tn.esprit.mission;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import tn.esprit.mission.dto.*;
import tn.esprit.mission.enums.NodeType;
import tn.esprit.mission.enums.SelectionMode;
import tn.esprit.mission.service.MissionService;
import tn.esprit.mission.service.WorkflowService;
import tn.esprit.mission.service.OrchestrationSettingsService;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({ MissionService.class, WorkflowService.class, OrchestrationSettingsService.class })
class WorkflowPersistenceTest {

    @Autowired
    MissionService missions;

    @Autowired
    WorkflowService workflows;

    @Autowired
    EntityManager entityManager;

    @Autowired
    OrchestrationSettingsService settings;

    @Test
    void configurationSurvivesDatabaseReloadAndDuplication() {
        settings.save(new OrchestrationSettingsDto(4, 300, 30, 30, 2, true));
        var mission = missions.create(
            new MissionRequest(
                "Persistance",
                "Test",
                SelectionMode.MANUAL,
                4,
                "test"
            )
        );
        var agent = new StepRequest(
            "Recherche",
            0,
            NodeType.AGENT,
            "research",
            "v1",
            "Analyser",
            "documents",
            "synthese",
            -125.5,
            220.0,
            4,
            null,
            null,
            null,
            null,
            "Analyste"
        );
        var condition = new StepRequest(
            "Qualite",
            1,
            NodeType.CONDITION,
            null,
            null,
            null,
            null,
            null,
            420.0,
            0.0,
            null,
            "score",
            ">=",
            0.0,
            0.85,
            null
        );
        workflows.replace(
            mission.getId(),
            new WorkflowRequest(
                List.of(agent, condition),
                List.of(new EdgeRequest(0L, 1L, "yes"))
            )
        );
        entityManager.flush();
        entityManager.clear();
        var reloaded = workflows.get(mission.getId());
        assertEquals(-125.5, reloaded.steps().get(0).getPositionX());
        assertEquals(220.0, reloaded.steps().get(0).getPositionY());
        assertEquals(4, reloaded.steps().get(0).getMaxRetries());
        assertEquals("Analyste", reloaded.steps().get(0).getAgentRole());
        assertEquals("score", reloaded.steps().get(1).getConditionVariable());
        assertEquals(">=", reloaded.steps().get(1).getConditionOperator());
        assertEquals(0.0, reloaded.steps().get(1).getConditionExpected());
        assertEquals(0.85, reloaded.steps().get(1).getConditionSampleValue());
        assertEquals("yes", reloaded.edges().get(0).getConditionLabel());
        var copy = missions.duplicate(mission.getId());
        entityManager.flush();
        entityManager.clear();
        var cloned = workflows.get(copy.getId());
        assertEquals(4, cloned.steps().get(0).getMaxRetries());
        assertEquals(-125.5, cloned.steps().get(0).getPositionX());
        assertEquals("score", cloned.steps().get(1).getConditionVariable());
        assertEquals(0.85, cloned.steps().get(1).getConditionSampleValue());
        assertNotEquals(
            reloaded.steps().get(0).getId(),
            cloned.steps().get(0).getId()
        );
        assertEquals(
            cloned.steps().get(0).getId(),
            cloned.edges().get(0).getSourceStepId()
        );
    }

    @Test
    void oldRequestsRemainAcceptedAndNewValuesAreValidated() throws Exception {
        var mapper = new ObjectMapper();
        var old = mapper.readValue(
            "{\"name\":\"Ancienne etape\",\"stepOrder\":0,\"nodeType\":\"AGENT\",\"agentId\":\"research\"}",
            StepRequest.class
        );
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(old).isEmpty());
            assertNull(old.positionX());
            var invalid = new StepRequest(
                "Test",
                0,
                NodeType.CONDITION,
                null,
                null,
                null,
                null,
                null,
                Double.NaN,
                0.0,
                0,
                "score",
                "INVALID",
                0.0,
                1.0,
                null
            );
            assertEquals(3, validator.validate(invalid).size());
        }
    }
}
