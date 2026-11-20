package tn.esprit.agentservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TunableParameterDto(
        String name,
        String type,
        Number min,
        Number max,
        List<String> choices,
        Object current,
        String description
) {
}
