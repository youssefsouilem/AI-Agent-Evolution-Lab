package tn.esprit.agentservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdminQuotaRequest(@Min(1) @Max(100) Integer maxAgents) { }
