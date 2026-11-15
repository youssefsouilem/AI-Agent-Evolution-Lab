package tn.esprit.agentservice.mapper;

import org.springframework.stereotype.Component;
import tn.esprit.agentservice.dto.AgentResponse;
import tn.esprit.agentservice.entity.Agent;

@Component
public class AgentMapper {

    public AgentResponse toResponse(Agent agent) {
        return new AgentResponse(
                agent.getId(),
                agent.getName(),
                agent.getDescription(),
                agent.getOwnerId(),
                agent.getStatus(),
                agent.getCreatedAt(),
                agent.getUpdatedAt()
        );
    }
}
