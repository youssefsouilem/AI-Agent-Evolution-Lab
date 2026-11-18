package tn.esprit.agentservice.service;

import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentConfiguration;
import tn.esprit.agentservice.entity.AgentStatus;
import tn.esprit.agentservice.entity.AgentVersion;
import tn.esprit.agentservice.event.AgentEventPublisher;
import tn.esprit.agentservice.exception.AgentVersionNotFoundException;
import tn.esprit.agentservice.mapper.AgentVersionMapper;
import tn.esprit.agentservice.repository.AgentVersionRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentVersionServiceTest {

    private Agent agent;
    private AgentService agentService;
    private AgentVersionRepository versionRepository;
    private AgentVersionMapper mapper;
    private AgentVersionService versionService;

    @BeforeEach
    void setUp() {
        agent = Agent.builder().id(12L).name("Agent support").ownerId("user-1").status(AgentStatus.DRAFT).build();
        agentService = mock(AgentService.class);
        versionRepository = mock(AgentVersionRepository.class);
        mapper = new AgentVersionMapper();
        versionService = new AgentVersionService(
                agentService,
                versionRepository,
                mapper,
                new TunableParameterCatalog(),
                mock(AgentEventPublisher.class),
                mock(Validator.class)
        );
        when(agentService.loadOwnedAgentForUpdate(12L, "user-1")).thenReturn(agent);
        when(versionRepository.findMaxVersionNumberByAgentId(12L)).thenReturn(Optional.of(3));
    }

    @Test
    void createsM5VersionWithCopiedToolsAndWithoutActivatingIt() {
        AgentConfiguration originalConfiguration = AgentConfiguration.builder()
                .temperature(0.7)
                .maxTokens(512)
                .style("concise")
                .maxTurns(4)
                .timeoutSeconds(30)
                .allowedToolIds(List.of(7L, 9L))
                .build();
        AgentVersion baseVersion = AgentVersion.builder()
                .id(3L)
                .agent(agent)
                .versionNumber(3)
                .model("gpt-4o-mini")
                .systemPrompt("Base prompt")
                .active(true)
                .configuration(originalConfiguration)
                .build();
        when(versionRepository.findByIdAndAgentId(3L, 12L)).thenReturn(Optional.of(baseVersion));
        AtomicReference<AgentVersion> savedVersion = new AtomicReference<>();
        when(versionRepository.save(any(AgentVersion.class))).thenAnswer(invocation -> {
            AgentVersion version = invocation.getArgument(0);
            version.setId(4L);
            savedVersion.set(version);
            return version;
        });

        var response = versionService.create(
                12L,
                new tn.esprit.agentservice.dto.VersionRequest(
                        null,
                        null,
                        null,
                        null,
                        3L,
                        "M5",
                        4L,
                        java.util.Map.of("temperature", 0.3, "maxTokens", 1024, "style", "detaille")
                ),
                "user-1"
        );

        AgentVersion created = savedVersion.get();
        assertEquals(4L, response.id());
        assertEquals(4, created.getVersionNumber());
        assertFalse(created.isActive());
        assertEquals("M5", created.getSource());
        assertEquals(4L, created.getStudyId());
        assertEquals("Créée par M5 (étude 4)", created.getChangeLog());
        assertEquals("gpt-4o-mini", created.getModel());
        assertEquals("Base prompt", created.getSystemPrompt());
        assertEquals(0.3, created.getConfiguration().getTemperature());
        assertEquals(1024, created.getConfiguration().getMaxTokens());
        assertEquals("detaille", created.getConfiguration().getStyle());
        assertEquals(List.of(7L, 9L), created.getConfiguration().getAllowedToolIds());
        assertNotSame(originalConfiguration, created.getConfiguration());
        assertEquals(512, originalConfiguration.getMaxTokens());
        assertEquals(List.of(7L, 9L), originalConfiguration.getAllowedToolIds());
        assertEquals(3L, baseVersion.getVersionNumber());
    }

    @Test
    void rejectsBasedOnVersionFromDifferentAgent() {
        when(versionRepository.findByIdAndAgentId(3L, 12L)).thenReturn(Optional.empty());

        assertThrows(AgentVersionNotFoundException.class, () -> versionService.create(
                12L,
                new tn.esprit.agentservice.dto.VersionRequest(
                        null, null, null, null, 3L, "M5", 4L, java.util.Map.of()
                ),
                "user-1"
        ));
        verify(versionRepository, never()).save(any(AgentVersion.class));
    }
}
