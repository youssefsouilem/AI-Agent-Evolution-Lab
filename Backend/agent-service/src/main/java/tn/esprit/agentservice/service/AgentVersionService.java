package tn.esprit.agentservice.service;

import jakarta.validation.Validator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.agentservice.dto.ConfigurationDto;
import tn.esprit.agentservice.dto.VersionRequest;
import tn.esprit.agentservice.dto.VersionResponse;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentConfiguration;
import tn.esprit.agentservice.entity.AgentVersion;
import tn.esprit.agentservice.exception.AgentVersionNotFoundException;
import tn.esprit.agentservice.exception.InvalidAgentConfigurationException;
import tn.esprit.agentservice.mapper.AgentVersionMapper;
import tn.esprit.agentservice.repository.AgentVersionRepository;

import java.util.List;

@Service
@Transactional
public class AgentVersionService {

    private final AgentService agentService;
    private final AgentVersionRepository versionRepository;
    private final AgentVersionMapper versionMapper;
    private final Validator validator;

    public AgentVersionService(
            AgentService agentService,
            AgentVersionRepository versionRepository,
            AgentVersionMapper versionMapper,
            Validator validator
    ) {
        this.agentService = agentService;
        this.versionRepository = versionRepository;
        this.versionMapper = versionMapper;
        this.validator = validator;
    }

    public VersionResponse create(Long agentId, VersionRequest request, Authentication authentication) {
        Agent agent = agentService.requireAgentForUpdate(agentId);
        agentService.assertCanManage(agent, authentication);
        int nextVersion = versionRepository.findMaxVersionNumberByAgentId(agentId).orElse(0) + 1;
        AgentVersion version = AgentVersion.builder()
                .agent(agent)
                .versionNumber(nextVersion)
                .model(request.model())
                .systemPrompt(request.systemPrompt())
                .changeLog(request.changeLog())
                .active(false)
                .configuration(versionMapper.toEntity(request.configuration()))
                .build();
        return versionMapper.toResponse(versionRepository.save(version));
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> list(Long agentId) {
        agentService.requireAgent(agentId);
        return versionRepository.findByAgentId(agentId).stream()
                .map(versionMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VersionResponse get(Long agentId, Long versionId) {
        return versionMapper.toResponse(requireVersion(agentId, versionId));
    }

    public VersionResponse activate(Long agentId, Long versionId, Authentication authentication) {
        Agent agent = agentService.requireAgentForUpdate(agentId);
        agentService.assertCanManage(agent, authentication);
        AgentVersion selectedVersion = requireVersion(agentId, versionId);
        List<AgentVersion> versions = versionRepository.findByAgentId(agentId);
        versions.forEach(version -> version.setActive(version.getId().equals(versionId)));
        return versionMapper.toResponse(selectedVersion);
    }

    public VersionResponse updateConfiguration(
            Long agentId,
            Long versionId,
            ConfigurationDto configuration,
            Authentication authentication
    ) {
        Agent agent = agentService.requireAgentForUpdate(agentId);
        agentService.assertCanManage(agent, authentication);
        AgentVersion version = requireVersion(agentId, versionId);
        if (configuration == null) {
            throw new InvalidAgentConfigurationException("Configuration is required.");
        }
        var violations = validator.validate(configuration);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(", "));
            throw new InvalidAgentConfigurationException(message);
        }
        AgentConfiguration updatedConfiguration = versionMapper.toEntity(configuration);
        version.setConfiguration(updatedConfiguration);
        return versionMapper.toResponse(versionRepository.save(version));
    }

    @Transactional(readOnly = true)
    public AgentVersion requireVersion(Long agentId, Long versionId) {
        return versionRepository.findByIdAndAgentId(versionId, agentId)
                .orElseThrow(() -> new AgentVersionNotFoundException(versionId, agentId));
    }
}
