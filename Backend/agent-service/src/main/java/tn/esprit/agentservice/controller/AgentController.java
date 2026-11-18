package tn.esprit.agentservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.dto.AgentRequest;
import tn.esprit.agentservice.dto.AgentResponse;
import tn.esprit.agentservice.dto.ExecuteRequest;
import tn.esprit.agentservice.dto.ExecuteResponse;
import tn.esprit.agentservice.service.AgentExecutionService;
import tn.esprit.agentservice.service.AgentService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService agentService;
    private final AgentExecutionService executionService;

    public AgentController(AgentService agentService, AgentExecutionService executionService) {
        this.agentService = agentService;
        this.executionService = executionService;
    }

    @PostMapping
    public ResponseEntity<AgentResponse> create(
            @Valid @RequestBody AgentRequest request,
            Authentication authentication
    ) {
        AgentResponse response = agentService.create(request, CurrentUser.from(authentication).id());
        return ResponseEntity.created(URI.create("/api/agents/" + response.id())).body(response);
    }

    @GetMapping
    public List<AgentResponse> list(Authentication authentication) {
        return agentService.list(CurrentUser.from(authentication).id());
    }

    @GetMapping("/{id}")
    public AgentResponse get(@PathVariable Long id, Authentication authentication) {
        return agentService.get(id, CurrentUser.from(authentication).id());
    }

    @PutMapping("/{id}")
    public AgentResponse update(
            @PathVariable Long id,
            @Valid @RequestBody AgentRequest request,
            Authentication authentication
    ) {
        return agentService.update(id, request, CurrentUser.from(authentication).id());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        agentService.delete(id, CurrentUser.from(authentication).id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/execute")
    public ExecuteResponse execute(
            @PathVariable Long id,
            @Valid @RequestBody ExecuteRequest request,
            Authentication authentication
    ) {
        return executionService.execute(id, request, CurrentUser.from(authentication).id());
    }
}
