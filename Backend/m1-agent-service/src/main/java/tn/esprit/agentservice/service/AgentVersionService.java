package tn.esprit.agentservice.service;

import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.agentservice.dto.ConfigurationDto;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.dto.TunableParameterDto;
import tn.esprit.agentservice.dto.VersionRequest;
import tn.esprit.agentservice.dto.VersionResponse;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentConfiguration;
import tn.esprit.agentservice.entity.AgentVersion;
import tn.esprit.agentservice.event.AgentEvent;
import tn.esprit.agentservice.event.AgentEventPublisher;
import tn.esprit.agentservice.event.AgentEventType;
import tn.esprit.agentservice.exception.AgentVersionNotFoundException;
import tn.esprit.agentservice.exception.InvalidConfigurationException;
import tn.esprit.agentservice.mapper.AgentVersionMapper;
import tn.esprit.agentservice.repository.AgentVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class AgentVersionService {

    private final AgentService agentService;
    private final AgentVersionRepository versionRepository;
    private final AgentVersionMapper versionMapper;
    private final TunableParameterCatalog parameterCatalog;
    private final AgentEventPublisher eventPublisher;
    private final Validator validator;
    private final AgentAccessPolicy accessPolicy;
    private final ModelCatalogService modelCatalog;

    public AgentVersionService(
            AgentService agentService,
            AgentVersionRepository versionRepository,
            AgentVersionMapper versionMapper,
            TunableParameterCatalog parameterCatalog,
            AgentEventPublisher eventPublisher,
            Validator validator
    ) {
        this(agentService, versionRepository, versionMapper, parameterCatalog, eventPublisher, validator,
                new AgentAccessPolicy(), null);
    }

    @Autowired
    public AgentVersionService(
            AgentService agentService,
            AgentVersionRepository versionRepository,
            AgentVersionMapper versionMapper,
            TunableParameterCatalog parameterCatalog,
            AgentEventPublisher eventPublisher,
            Validator validator,
            AgentAccessPolicy accessPolicy,
            ModelCatalogService modelCatalog
    ) {
        this.agentService = agentService;
        this.versionRepository = versionRepository;
        this.versionMapper = versionMapper;
        this.parameterCatalog = parameterCatalog;
        this.eventPublisher = eventPublisher;
        this.validator = validator;
        this.accessPolicy = accessPolicy;
        this.modelCatalog = modelCatalog;
    }

    public VersionResponse create(Long agentId, VersionRequest request, String ownerId) {
        return create(agentId, request, new CurrentUser(ownerId, false));
    }

    public VersionResponse create(Long agentId, VersionRequest request, CurrentUser user) {
        Agent agent = agentService.loadOwnedAgentForUpdate(agentId, user);
        accessPolicy.requireWrite(agent, user);
        return createVersion(agent, request, user);
    }

    private VersionResponse createVersion(Agent agent, VersionRequest request, CurrentUser actor) {
        Long agentId = agent.getId();
        int nextVersion = versionRepository.findMaxVersionNumberByAgentId(agentId).orElse(0) + 1;
        AgentVersion version = request.basedOnVersionId() == null
                ? createUiVersion(agent, request, nextVersion)
                : createM5Version(agent, request, nextVersion);
        if (modelCatalog != null) {
            version.setProvider(modelCatalog.resolveProvider(version.getProvider(), version.getModel()));
        }
        AgentVersion savedVersion = versionRepository.save(version);
        publishUpdatedEvent(agentId, savedVersion.getId(), actor);
        return versionMapper.toResponse(savedVersion);
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> list(Long agentId, String ownerId) {
        return list(agentId, new CurrentUser(ownerId, false));
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> list(Long agentId, CurrentUser user) {
        Agent agent = agentService.loadOwnedAgent(agentId, user);
        accessPolicy.requireRead(agent, user);
        return versionRepository.findByAgentId(agentId).stream()
                .map(versionMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VersionResponse get(Long agentId, Long versionId, String ownerId) {
        return get(agentId, versionId, new CurrentUser(ownerId, false));
    }

    @Transactional(readOnly = true)
    public VersionResponse get(Long agentId, Long versionId, CurrentUser user) {
        Agent agent = agentService.loadOwnedAgent(agentId, user);
        accessPolicy.requireRead(agent, user);
        return versionMapper.toResponse(requireVersion(agentId, versionId));
    }

    @Transactional(readOnly = true)
    public List<TunableParameterDto> tunableParameters(Long agentId, Long versionId, String ownerId) {
        return tunableParameters(agentId, versionId, new CurrentUser(ownerId, false));
    }

    @Transactional(readOnly = true)
    public List<TunableParameterDto> tunableParameters(Long agentId, Long versionId, CurrentUser user) {
        Agent agent = agentService.loadOwnedAgent(agentId, user);
        accessPolicy.requireRead(agent, user);
        AgentVersion version = requireVersion(agentId, versionId);
        return parameterCatalog.describe(version.getConfiguration());
    }

    public VersionResponse activate(Long agentId, Long versionId, String ownerId) {
        return activate(agentId, versionId, new CurrentUser(ownerId, false));
    }

    public VersionResponse activate(Long agentId, Long versionId, CurrentUser user) {
        Agent agent = agentService.loadOwnedAgentForUpdate(agentId, user);
        accessPolicy.requireWrite(agent, user);
        AgentVersion selectedVersion = requireVersion(agentId, versionId);
        List<AgentVersion> versions = versionRepository.findByAgentId(agentId);
        versions.forEach(version -> version.setActive(version.getId().equals(versionId)));
        publishUpdatedEvent(agentId, selectedVersion.getId(), user);
        return versionMapper.toResponse(selectedVersion);
    }

    public VersionResponse updateConfiguration(
            Long agentId,
            Long versionId,
            ConfigurationDto configuration,
            String ownerId
    ) {
        return updateConfiguration(agentId, versionId, configuration, new CurrentUser(ownerId, false));
    }

    public VersionResponse updateConfiguration(
            Long agentId,
            Long versionId,
            ConfigurationDto configuration,
            CurrentUser user
    ) {
        Agent agent = agentService.loadOwnedAgentForUpdate(agentId, user);
        accessPolicy.requireWrite(agent, user);
        AgentVersion version = requireVersion(agentId, versionId);
        if (configuration == null) {
            throw new InvalidConfigurationException("La configuration est obligatoire.");
        }
        var violations = validator.validate(configuration);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new InvalidConfigurationException(message);
        }
        AgentConfiguration updatedConfiguration = versionMapper.toEntity(configuration);
        version.setConfiguration(updatedConfiguration);
        AgentVersion saved = versionRepository.save(version);
        publishUpdatedEvent(agentId, saved.getId(), user);
        return versionMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AgentVersion requireVersion(Long agentId, Long versionId) {
        return versionRepository.findByIdAndAgentId(versionId, agentId)
                .orElseThrow(() -> new AgentVersionNotFoundException(versionId, agentId));
    }

    private AgentVersion createUiVersion(Agent agent, VersionRequest request, int versionNumber) {
        var violations = validator.validate(request.configuration());
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new InvalidConfigurationException(message);
        }
        return AgentVersion.builder()
                .agent(agent)
                .versionNumber(versionNumber)
                .provider(request.provider())
                .model(request.model())
                .systemPrompt(request.systemPrompt())
                .changeLog(request.changeLog())
                .source(request.source() == null ? "USER" : request.source())
                .studyId(request.studyId())
                .active(false)
                .configuration(versionMapper.toEntity(request.configuration()))
                .build();
    }

    private AgentVersion createM5Version(Agent agent, VersionRequest request, int versionNumber) {
        AgentVersion baseVersion = requireVersion(agent.getId(), request.basedOnVersionId());
        AgentConfiguration configuration = versionMapper.copyConfiguration(baseVersion.getConfiguration());
        if (configuration == null) {
            throw new InvalidConfigurationException("La configuration de la version de référence est absente.");
        }
        Map<String, Object> params = request.params();
        parameterCatalog.applyParams(configuration, params);
        String changeLog = request.changeLog();
        if (changeLog == null || changeLog.isBlank()) {
            changeLog = "Créée par M5 (étude " + request.studyId() + ")";
        }
        return AgentVersion.builder()
                .agent(agent)
                .versionNumber(versionNumber)
                .provider(baseVersion.getProvider())
                .model(baseVersion.getModel())
                .systemPrompt(baseVersion.getSystemPrompt())
                .changeLog(changeLog)
                .source(request.source() == null ? "USER" : request.source())
                .studyId(request.studyId())
                .active(false)
                .configuration(configuration)
                .build();
    }

    private void publishUpdatedEvent(Long agentId, Long versionId, CurrentUser actor) {
        eventPublisher.publish(AgentEvent.create(
                AgentEventType.AGENT_UPDATED, "agent-service", agentId, versionId,
                actor.id(), actor.role(), null));
    }
}
