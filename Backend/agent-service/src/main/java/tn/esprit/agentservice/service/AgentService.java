package tn.esprit.agentservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.agentservice.dto.AgentRequest;
import tn.esprit.agentservice.dto.AgentResponse;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentStatus;
import tn.esprit.agentservice.event.AgentEvent;
import tn.esprit.agentservice.event.AgentEventPublisher;
import tn.esprit.agentservice.event.AgentEventType;
import tn.esprit.agentservice.exception.AgentNotFoundException;
import tn.esprit.agentservice.mapper.AgentMapper;
import tn.esprit.agentservice.repository.AgentRepository;

import java.util.List;

@Service
@Transactional
public class AgentService {

    private final AgentRepository agentRepository;
    private final AgentMapper agentMapper;
    private final AgentEventPublisher eventPublisher;

    public AgentService(
            AgentRepository agentRepository,
            AgentMapper agentMapper,
            AgentEventPublisher eventPublisher
    ) {
        this.agentRepository = agentRepository;
        this.agentMapper = agentMapper;
        this.eventPublisher = eventPublisher;
    }

    public AgentResponse create(AgentRequest request, String ownerId) {
        Agent agent = Agent.builder()
                .name(request.name())
                .description(request.description())
                .ownerId(ownerId)
                .status(request.status() == null ? AgentStatus.DRAFT : request.status())
                .build();
        Agent savedAgent = agentRepository.save(agent);
        eventPublisher.publish(AgentEvent.create(
                AgentEventType.AGENT_CREATED,
                "agent-service",
                savedAgent.getId(),
                null
        ));
        return agentMapper.toResponse(savedAgent);
    }

    @Transactional(readOnly = true)
    public List<AgentResponse> list(String ownerId) {
        return agentRepository.findByOwnerId(ownerId).stream()
                .map(agentMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentResponse get(Long id, String ownerId) {
        return agentMapper.toResponse(loadOwnedAgent(id, ownerId));
    }

    public AgentResponse update(Long id, AgentRequest request, String ownerId) {
        Agent agent = loadOwnedAgent(id, ownerId);
        agent.setName(request.name());
        agent.setDescription(request.description());
        if (request.status() != null) {
            agent.setStatus(request.status());
        }
        Agent savedAgent = agentRepository.save(agent);
        eventPublisher.publish(AgentEvent.create(
                AgentEventType.AGENT_UPDATED,
                "agent-service",
                savedAgent.getId(),
                null
        ));
        return agentMapper.toResponse(savedAgent);
    }

    public void delete(Long id, String ownerId) {
        Agent agent = loadOwnedAgent(id, ownerId);
        agentRepository.delete(agent);
    }

    @Transactional(readOnly = true)
    public Agent loadOwnedAgent(Long id, String ownerId) {
        Agent agent = agentRepository.findById(id).orElseThrow(() -> new AgentNotFoundException(id));
        if (!java.util.Objects.equals(agent.getOwnerId(), ownerId)) {
            throw new AgentNotFoundException(id);
        }
        return agent;
    }

    public Agent loadOwnedAgentForUpdate(Long id, String ownerId) {
        loadOwnedAgent(id, ownerId);
        Agent agent = agentRepository.findByIdForUpdate(id).orElseThrow(() -> new AgentNotFoundException(id));
        if (!java.util.Objects.equals(agent.getOwnerId(), ownerId)) {
            throw new AgentNotFoundException(id);
        }
        return agent;
    }
}
