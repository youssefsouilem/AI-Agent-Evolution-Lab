package tn.esprit.mission.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.mission.dto.AgentResponse;
import tn.esprit.mission.service.AgentCatalogService;

@RestController
@RequestMapping("/api/missions/agent-catalog")
public class AgentCatalogController {

    private final AgentCatalogService service;

    public AgentCatalogController(AgentCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public List<AgentResponse> getAgents() {
        return service.getAgents();
    }
}
