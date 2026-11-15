package tn.esprit.agentservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.agentservice.dto.AgentRequest;
import tn.esprit.agentservice.dto.AgentResponse;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentStatus;
import tn.esprit.agentservice.event.AgentEvent;
import tn.esprit.agentservice.event.AgentEventPublisher;
import tn.esprit.agentservice.event.AgentEventType;
import tn.esprit.agentservice.exception.AgentAccessDeniedException;
import tn.esprit.agentservice.exception.AgentNotFoundException;
import tn.esprit.agentservice.mapper.AgentMapper;
import tn.esprit.agentservice.repository.AgentRepository;

import java.util.Collection;
import java.util.Map;

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
    public Page<AgentResponse> list(Pageable pageable) {
        return agentRepository.findAll(pageable).map(agentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public AgentResponse get(Long id) {
        return agentMapper.toResponse(requireAgent(id));
    }

    public AgentResponse update(Long id, AgentRequest request, Authentication authentication) {
        Agent agent = requireAgent(id);
        assertCanManage(agent, authentication);
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

    public void delete(Long id, Authentication authentication) {
        Agent agent = requireAgent(id);
        assertCanManage(agent, authentication);
        agentRepository.delete(agent);
    }

    @Transactional(readOnly = true)
    public Agent requireAgent(Long id) {
        return agentRepository.findById(id).orElseThrow(() -> new AgentNotFoundException(id));
    }

    public Agent requireAgentForUpdate(Long id) {
        return agentRepository.findByIdForUpdate(id).orElseThrow(() -> new AgentNotFoundException(id));
    }

    public void assertCanManage(Agent agent, Authentication authentication) {
        if (isAdmin(authentication)) {
            return;
        }
        if (authentication == null || !agent.getOwnerId().equals(authentication.getName())) {
            throw new AgentAccessDeniedException();
        }
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        if (authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            return true;
        }
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        if (containsAdmin(jwt.getClaims().get("roles"))) {
            return true;
        }
        Object realmAccess = jwt.getClaims().get("realm_access");
        return realmAccess instanceof Map<?, ?> claims && containsAdmin(claims.get("roles"));
    }

    private boolean containsAdmin(Object roles) {
        return roles instanceof Collection<?> values
                && values.stream().anyMatch(role -> "ADMIN".equals(role) || "ROLE_ADMIN".equals(role));
    }
}
