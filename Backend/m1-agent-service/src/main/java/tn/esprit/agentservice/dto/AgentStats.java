package tn.esprit.agentservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AgentStats(
        int days,
        long total,
        long completed,
        long failed,
        Double successRate,
        Long avgDurationMs,
        Long p95DurationMs,
        Long totalTokensKnown,
        long executionsWithUnknownTokens,
        List<Day> byDay
) {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Day(LocalDate date, long total, long failed, Long avgDurationMs, Long tokens) { }
}
