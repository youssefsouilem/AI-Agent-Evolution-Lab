package tn.esprit.agentservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.agentservice.dto.AgentRequest;
import tn.esprit.agentservice.dto.AgentResponse;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentStatus;
import tn.esprit.agentservice.event.AgentEvent;
import tn.esprit.agentservice.event.AgentEventPublisher;
import tn.esprit.agentservice.event.AgentEventType;
import tn.esprit.agentservice.exception.AgentNotFoundException;
import tn.esprit.agentservice.mapper.AgentMapper;
import tn.esprit.agentservice.repository.AgentRepository;
import tn.esprit.agentservice.repository.AgentExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Service
@Transactional
public class AgentService {

    private final AgentRepository agentRepository;
    private final AgentMapper agentMapper;
    private final AgentEventPublisher eventPublisher;
    private final AgentAccessPolicy accessPolicy;
    private final UserQuotaService quotaService;
    private AgentExecutionRepository executionRepository;

    public AgentService(
            AgentRepository agentRepository,
            AgentMapper agentMapper,
            AgentEventPublisher eventPublisher
    ) {
        this(agentRepository, agentMapper, eventPublisher, new AgentAccessPolicy(), null);
    }

    @Autowired
    public AgentService(
            AgentRepository agentRepository,
            AgentMapper agentMapper,
            AgentEventPublisher eventPublisher,
            AgentAccessPolicy accessPolicy,
            UserQuotaService quotaService
    ) {
        this.agentRepository = agentRepository;
        this.agentMapper = agentMapper;
        this.eventPublisher = eventPublisher;
        this.accessPolicy = accessPolicy;
        this.quotaService = quotaService;
    }

    @Autowired
    public void setExecutionRepository(AgentExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    public AgentResponse create(AgentRequest request, String ownerId) {
        return create(request, new CurrentUser(ownerId, false));
    }

    public AgentResponse create(AgentRequest request, CurrentUser user) {
        String ownerId = user.id();
        if (!user.admin() && quotaService != null) {
            quotaService.checkCreation(ownerId);
        }
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
                null,
                user.id(),
                user.role(),
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
    public List<AgentResponse> list(CurrentUser user, boolean all) {
        return list(user, all, null);
    }

    @Transactional(readOnly = true)
    public List<AgentResponse> list(CurrentUser user, boolean all, String ownerId) {
        List<Agent> visible = all && user.admin()
                ? agentRepository.findAll()
                : agentRepository.findByOwnerId(user.id());
        if (all && user.admin() && ownerId != null && !ownerId.isBlank()) {
            visible = visible.stream().filter(agent -> ownerId.equals(agent.getOwnerId())).toList();
        }
        return visible.stream().map(agentMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AgentResponse get(Long id, String ownerId) {
        return agentMapper.toResponse(loadOwnedAgent(id, ownerId));
    }

    @Transactional(readOnly = true)
    public AgentResponse get(Long id, CurrentUser user) {
        return agentMapper.toResponse(loadOwnedAgent(id, user));
    }

    public AgentResponse update(Long id, AgentRequest request, String ownerId) {
        return updateAgent(loadOwnedAgent(id, ownerId), request, new CurrentUser(ownerId, false));
    }

    public AgentResponse update(Long id, AgentRequest request, CurrentUser user) {
        return updateAgent(loadOwnedAgentForUpdate(id, user), request, user);
    }

    private AgentResponse updateAgent(Agent agent, AgentRequest request, CurrentUser actor) {
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
                null,
                actor.id(),
                actor.role(),
                null
        ));
        return agentMapper.toResponse(savedAgent);
    }

    public void delete(Long id, String ownerId) {
        Agent agent = loadOwnedAgent(id, ownerId);
        deleteAgent(agent, new CurrentUser(ownerId, false), null);
    }

    public void delete(Long id, CurrentUser user) {
        Agent agent = loadOwnedAgentForUpdate(id, user);
        deleteAgent(agent, user, null);
    }

    public Agent findForAdmin(Long id) {
        return agentRepository.findById(id).orElseThrow(() -> new AgentNotFoundException(id));
    }

    public void deleteForAdmin(Agent agent, CurrentUser actor, String reason) {
        if (!actor.admin()) {
            throw new org.springframework.security.access.AccessDeniedException("Rôle administrateur requis.");
        }
        deleteAgent(agent, actor, reason);
    }

    public void archiveForAdmin(Agent agent, CurrentUser actor, String reason) {
        if (!actor.admin()) {
            throw new org.springframework.security.access.AccessDeniedException("Rôle administrateur requis.");
        }
        agent.setStatus(AgentStatus.ARCHIVED);
        Agent archived = agentRepository.save(agent);
        eventPublisher.publish(AgentEvent.create(AgentEventType.AGENT_UPDATED, "agent-service",
                archived.getId(), null, actor.id(), actor.role(), reason));
    }

    private void deleteAgent(Agent agent, CurrentUser actor, String reason) {
        if (executionRepository != null) {
            executionRepository.deleteAll(executionRepository.findByAgentId(agent.getId()));
        }
        eventPublisher.publish(AgentEvent.create(
                AgentEventType.AGENT_DELETED, "agent-service", agent.getId(), null,
                actor.id(), actor.role(), reason));
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

    @Transactional(readOnly = true)
    public Agent loadOwnedAgent(Long id, CurrentUser currentUser) {
        Agent agent = agentRepository.findById(id).orElseThrow(() -> new AgentNotFoundException(id));
        accessPolicy.requireRead(agent, currentUser);
        return agent;
    }

    @Transactional
    public Agent loadOwnedAgentForUpdate(Long id, CurrentUser currentUser) {
        Agent agent = agentRepository.findByIdForUpdate(id).orElseThrow(() -> new AgentNotFoundException(id));
        accessPolicy.requireWrite(agent, currentUser);
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
