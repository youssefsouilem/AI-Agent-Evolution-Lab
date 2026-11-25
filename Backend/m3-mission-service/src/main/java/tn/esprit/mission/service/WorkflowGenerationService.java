package tn.esprit.mission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.esprit.mission.dto.*;
import tn.esprit.mission.entity.*;
import tn.esprit.mission.enums.NodeType;

@Service
public class WorkflowGenerationService {
    private final MissionService missions;
    private final AgentCatalogService catalogue;
    private final OrchestratorClient client;
    private final OrchestrationSettingsService settings;
    private final EngineWorkflowAdapter adapter;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final boolean enabled;
    private final String routerId;
    private final String routerVersion;
    public WorkflowGenerationService(MissionService missions, AgentCatalogService catalogue, OrchestratorClient client,
        OrchestrationSettingsService settings, EngineWorkflowAdapter adapter, ObjectMapper mapper, Validator validator,
        @Value("${m1.enabled:false}") boolean enabled, @Value("${ai.router-agent-id:}") String routerId,
        @Value("${ai.router-version-id:}") String routerVersion) {
        this.missions = missions; this.catalogue = catalogue; this.client = client; this.settings = settings;
        this.adapter = adapter; this.mapper = mapper; this.validator = validator;
        this.enabled = enabled; this.routerId = routerId; this.routerVersion = routerVersion;
    }
    public WorkflowRequest generate(Long missionId, String instruction) {
        var mission = missions.get(missionId);
        if (!enabled || !client.enabled() || routerId.isBlank() || routerVersion.isBlank()) {
            throw new IllegalArgumentException("Configurez M1_ENABLED, AI_ROUTER_AGENT_ID et AI_ROUTER_VERSION_ID pour la génération IA réelle.");
        }
        var agents = catalogue.getAgents();
        var allowed = new HashSet<String>();
        var publicCatalogue = new ArrayList<Map<String, Object>>();
        for (var agent : agents) {
            if (!"ACTIVE".equals(agent.status()) || agent.versions() == null) continue;
            var versions = agent.versions().stream().filter(version -> Boolean.TRUE.equals(version.active())).map(version -> version.id().toString()).toList();
            versions.forEach(version -> allowed.add(agent.id() + ":" + version));
            if (!versions.isEmpty()) publicCatalogue.add(Map.of("id", agent.id().toString(), "name", agent.name(), "versions", versions));
        }
        if (!allowed.contains(routerId + ":" + routerVersion)) throw new IllegalArgumentException("AI Router absent du catalogue actif M1.");
        var limits = settings.get();
        var response = client.generate(Map.of("mission_id", missionId, "instruction", instruction,
            "router_agent_id", routerId, "router_version_id", routerVersion, "catalogue", publicCatalogue, "max_steps", limits.maxSteps()));
        try {
            var request = mapper.treeToValue(response, WorkflowRequest.class);
            if (!validator.validate(request).isEmpty() || request.steps().isEmpty() || request.steps().size() > limits.maxSteps()) {
                throw new IllegalArgumentException("Plan AI Router invalide ou trop grand.");
            }
            var steps = new ArrayList<MissionStep>();
            for (var input : request.steps()) {
                if (input.nodeType() != NodeType.AGENT || !allowed.contains(input.agentId() + ":" + input.agentVersionId()) || input.instruction() == null || input.instruction().isBlank()) {
                    throw new IllegalArgumentException("Le plan référence un agent ou une version non active.");
                }
                var step = new MissionStep(); step.setId(input.stepOrder().longValue() + 1); step.setStepOrder(input.stepOrder());
                step.setNodeType(NodeType.AGENT); step.setAgentId(input.agentId()); step.setAgentVersionId(input.agentVersionId()); step.setInstruction(input.instruction());
                if (steps.stream().anyMatch(old -> old.getId().equals(step.getId()))) throw new IllegalArgumentException("Ordre d'étape dupliqué.");
                steps.add(step);
            }
            var edges = request.edges().stream().map(input -> {
                var edge = new WorkflowEdge(); edge.setSourceStepId(input.sourceStepOrder() + 1); edge.setTargetStepId(input.targetStepOrder() + 1); return edge;
            }).toList();
            adapter.convert(mission, 1L, steps, edges, limits);
            return request;
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalArgumentException("Réponse AI Router incompatible.");
        }
    }
}
