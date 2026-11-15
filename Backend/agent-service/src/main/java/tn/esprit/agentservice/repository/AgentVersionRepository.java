package tn.esprit.agentservice.repository;

import tn.esprit.agentservice.entity.AgentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentVersionRepository extends JpaRepository<AgentVersion, Long> {

    List<AgentVersion> findByAgentId(Long agentId);

    Optional<AgentVersion> findByIdAndAgentId(Long id, Long agentId);

    Optional<AgentVersion> findFirstByAgentIdAndActiveTrue(Long agentId);

    @Query("SELECT MAX(version.versionNumber) FROM AgentVersion version WHERE version.agent.id = :agentId")
    Optional<Integer> findMaxVersionNumberByAgentId(@Param("agentId") Long agentId);
}
