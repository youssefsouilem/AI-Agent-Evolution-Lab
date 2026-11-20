package tn.esprit.agentservice.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tn.esprit.agentservice.entity.AgentExecution;
import tn.esprit.agentservice.entity.ExecutionStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface AgentExecutionRepository extends JpaRepository<AgentExecution, Long>,
        JpaSpecificationExecutor<AgentExecution> {
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
