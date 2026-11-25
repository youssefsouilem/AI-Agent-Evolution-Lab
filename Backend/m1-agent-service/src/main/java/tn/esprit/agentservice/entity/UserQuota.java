package tn.esprit.agentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_quota")
@Getter
@Setter
@NoArgsConstructor
public class UserQuota {
    @Id
    @Column(name = "owner_id", nullable = false, length = 255)
    private String ownerId;
    private Integer maxAgents;
    private String updatedBy;
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
