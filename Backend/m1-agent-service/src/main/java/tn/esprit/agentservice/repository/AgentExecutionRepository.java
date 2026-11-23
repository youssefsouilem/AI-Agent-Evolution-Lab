package tn.esprit.agentservice.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.agentservice.entity.AgentExecution;
import tn.esprit.agentservice.entity.ExecutionStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface AgentExecutionRepository extends JpaRepository<AgentExecution, Long>,
        JpaSpecificationExecutor<AgentExecution> {
    interface StatsAggregate {
        Long getTotal();
        Long getCompleted();
        Long getFailed();
        Double getAvgDurationMs();
        Long getTotalTokensKnown();
        Long getUnknownTokens();
    }

    interface DailyStats {
        java.time.LocalDate getDayDate();
        Long getTotal();
        Long getFailed();
        Double getAvgDurationMs();
        Long getTokens();
    }

    interface UserActivity {
        String getOwnerId();
        LocalDateTime getLatest();
    }

    @Query(value = """
            SELECT requested_by AS ownerId, MAX(started_at) AS latest
            FROM agent_execution
            GROUP BY requested_by
            """, nativeQuery = true)
    List<UserActivity> latestActivityByUser();

    @Query(value = """
            SELECT COUNT(*) AS total,
                   SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed,
                   SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed,
                   AVG(duration_ms) AS avgDurationMs,
                   SUM(total_tokens) AS totalTokensKnown,
                   SUM(CASE WHEN total_tokens IS NULL THEN 1 ELSE 0 END) AS unknownTokens
            FROM agent_execution
            WHERE agent_id = :agentId AND started_at >= :since
            """, nativeQuery = true)
    StatsAggregate aggregateStats(@Param("agentId") Long agentId, @Param("since") LocalDateTime since);

    @Query(value = """
            SELECT CAST(started_at AS DATE) AS day_date,
                   COUNT(*) AS total,
                   SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed,
                   AVG(duration_ms) AS avgDurationMs,
                   SUM(total_tokens) AS tokens
            FROM agent_execution
            WHERE agent_id = :agentId AND started_at >= :since
            GROUP BY CAST(started_at AS DATE)
            ORDER BY CAST(started_at AS DATE)
            """, nativeQuery = true)
    List<DailyStats> dailyStats(@Param("agentId") Long agentId, @Param("since") LocalDateTime since);

    @Query(value = """
            SELECT duration_ms FROM agent_execution
            WHERE agent_id = :agentId AND started_at >= :since AND duration_ms IS NOT NULL
            ORDER BY duration_ms
            """, nativeQuery = true)
    List<Long> orderedDurations(@Param("agentId") Long agentId, @Param("since") LocalDateTime since,
                                org.springframework.data.domain.Pageable pageable);
    List<AgentExecution> findByAgentIdOrderByStartedAtDesc(Long agentId, Pageable pageable);
    List<AgentExecution> findByAgentIdAndStatusOrderByStartedAtDesc(
            Long agentId, ExecutionStatus status, Pageable pageable
    );
    List<AgentExecution> findByAgentIdAndVersionIdOrderByStartedAtDesc(
            Long agentId, Long versionId, Pageable pageable
    );
    List<AgentExecution> findByAgentIdAndStatusAndVersionIdOrderByStartedAtDesc(
            Long agentId, ExecutionStatus status, Long versionId, Pageable pageable
    );
    long countByAgentId(Long agentId);
    long countByAgentIdAndStartedAtGreaterThanEqualAndDurationMsIsNotNull(Long agentId, LocalDateTime since);
    long countByAgentIdAndStatus(Long agentId, ExecutionStatus status);
    long countByAgentIdAndVersionId(Long agentId, Long versionId);
    long countByAgentIdAndStatusAndVersionId(Long agentId, ExecutionStatus status, Long versionId);
    List<AgentExecution> findByRequestedByOrderByStartedAtDesc(String requestedBy, Pageable pageable);
    List<AgentExecution> findByAgentIdInOrderByStartedAtDesc(List<Long> agentIds, Pageable pageable);
    long countByRequestedBy(String requestedBy);
    long countByAgentIdIn(List<Long> agentIds);
    List<AgentExecution> findByStatusAndStartedAtBefore(ExecutionStatus status, LocalDateTime before);
    List<AgentExecution> findByAgentId(Long agentId);
}
