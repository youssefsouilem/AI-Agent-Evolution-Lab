package tn.esprit.mission.dto;

import java.util.List;

public record AgentResponse(
        Long id,
        String name,
        String description,
        String status,
        List<AgentVersionResponse> versions
) {
}