package tn.esprit.agentservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentStatus;
import tn.esprit.agentservice.event.AgentEventPublisher;
import tn.esprit.agentservice.exception.AgentNotFoundException;
import tn.esprit.agentservice.mapper.AgentMapper;
import tn.esprit.agentservice.repository.AgentRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceOwnershipTest {

    private AgentRepository repository;
    private AgentService service;

    @BeforeEach
    void setUp() {
        repository = mock(AgentRepository.class);
        service = new AgentService(repository, new AgentMapper(), mock(AgentEventPublisher.class));
    }

    @Test
    void listReturnsOnlyAgentsFromCurrentUserRepositoryQuery() {
        Agent ownAgent = Agent.builder().id(1L).name("Own").ownerId("user-1").status(AgentStatus.DRAFT).build();
        when(repository.findByOwnerId("user-1")).thenReturn(List.of(ownAgent));

        var result = service.list("user-1");

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());
        verify(repository).findByOwnerId("user-1");
    }

    @Test
    void getTreatsAnotherUsersAgentAsNotFound() {
        Agent otherAgent = Agent.builder().id(2L).name("Other").ownerId("user-2").build();
        when(repository.findById(2L)).thenReturn(Optional.of(otherAgent));

        assertThrows(AgentNotFoundException.class, () -> service.get(2L, "user-1"));
    }
}
