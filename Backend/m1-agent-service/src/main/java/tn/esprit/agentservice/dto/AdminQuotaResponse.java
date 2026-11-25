package tn.esprit.agentservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminQuotaResponse(String ownerId, Integer maxAgents) { }
