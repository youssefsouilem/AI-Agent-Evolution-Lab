package tn.esprit.agentservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.dto.ConfigurationDto;
import tn.esprit.agentservice.dto.TunableParameterDto;
import tn.esprit.agentservice.dto.VersionRequest;
import tn.esprit.agentservice.dto.VersionResponse;
import tn.esprit.agentservice.service.AgentVersionService;

import java.util.List;

@RestController
@RequestMapping("/api/agents/{id}/versions")
public class AgentVersionController {

    private final AgentVersionService versionService;

    public AgentVersionController(AgentVersionService versionService) {
        this.versionService = versionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VersionResponse create(
            @PathVariable Long id,
            @Valid @RequestBody VersionRequest request,
            Authentication authentication
    ) {
        return versionService.create(id, request, CurrentUser.from(authentication).id());
    }

    @GetMapping
    public List<VersionResponse> list(@PathVariable Long id, Authentication authentication) {
        return versionService.list(id, CurrentUser.from(authentication).id());
    }

    @GetMapping("/{vid}")
    public VersionResponse get(
            @PathVariable Long id,
            @PathVariable Long vid,
            Authentication authentication
    ) {
        return versionService.get(id, vid, CurrentUser.from(authentication).id());
    }

    @GetMapping("/{vid}/tunable-parameters")
    public List<TunableParameterDto> tunableParameters(
            @PathVariable Long id,
            @PathVariable Long vid,
            Authentication authentication
    ) {
        return versionService.tunableParameters(id, vid, CurrentUser.from(authentication).id());
    }

    @PutMapping("/{vid}/activate")
    public VersionResponse activate(
            @PathVariable Long id,
            @PathVariable Long vid,
            Authentication authentication
    ) {
        return versionService.activate(id, vid, CurrentUser.from(authentication).id());
    }

    @PutMapping("/{vid}/configuration")
    public VersionResponse updateConfiguration(
            @PathVariable Long id,
            @PathVariable Long vid,
            @RequestBody ConfigurationDto configuration,
            Authentication authentication
    ) {
        return versionService.updateConfiguration(id, vid, configuration, CurrentUser.from(authentication).id());
    }
}
