package tn.esprit.agentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.agentservice.entity.AdminAudit;

public interface AdminAuditRepository extends JpaRepository<AdminAudit, Long> { }
