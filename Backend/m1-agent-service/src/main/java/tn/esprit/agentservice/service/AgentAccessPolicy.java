package tn.esprit.agentservice.service;

import org.springframework.stereotype.Component;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.exception.AgentNotFoundException;
import tn.esprit.agentservice.exception.ForbiddenOperationException;

import java.util.Objects;

@Component
public class AgentAccessPolicy {
    public void requireRead(Agent agent, CurrentUser user) {
        if (!user.admin() && !Objects.equals(agent.getOwnerId(), user.id())) {
            throw new AgentNotFoundException(agent.getId());
        }
    }

    public void requireWrite(Agent agent, CurrentUser user) {
        if (!Objects.equals(agent.getOwnerId(), user.id())) {
            if (user.admin()) {
                throw new ForbiddenOperationException(
                        "Un administrateur ne peut pas modifier l'agent d'un autre utilisateur.",
                        "ADMIN_READ_ONLY"
                );
            }
            throw new AgentNotFoundException(agent.getId());
        }
    }
}
