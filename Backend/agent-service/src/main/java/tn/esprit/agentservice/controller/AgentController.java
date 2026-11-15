package tn.esprit.agentservice.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.agentservice.dto.AgentRequest;
import tn.esprit.agentservice.dto.AgentResponse;
import tn.esprit.agentservice.dto.ExecuteRequest;
import tn.esprit.agentservice.dto.ExecuteResponse;
import tn.esprit.agentservice.service.AgentExecutionService;
import tn.esprit.agentservice.service.AgentService;

import java.net.URI;

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
            @AuthenticationPrincipal Jwt jwt
    ) {
        AgentResponse response = agentService.create(request, jwt.getSubject());
        return ResponseEntity.created(URI.create("/api/agents/" + response.id())).body(response);
    }

    @GetMapping
    public Page<AgentResponse> list(Pageable pageable) {
        return agentService.list(pageable);
    }

    @GetMapping("/{id}")
    public AgentResponse get(@PathVariable Long id) {
        return agentService.get(id);
    }

    @PutMapping("/{id}")
    public AgentResponse update(
            @PathVariable Long id,
            @Valid @RequestBody AgentRequest request,
            Authentication authentication
    ) {
        return agentService.update(id, request, authentication);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        agentService.delete(id, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/execute")
    public ExecuteResponse execute(
            @PathVariable Long id,
            @Valid @RequestBody ExecuteRequest request,
            Authentication authentication
    ) {
        return executionService.execute(id, request, authentication);
    }
}
