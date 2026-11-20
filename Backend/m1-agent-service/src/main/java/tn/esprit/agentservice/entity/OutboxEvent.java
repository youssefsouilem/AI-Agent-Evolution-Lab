package tn.esprit.agentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String eventType;
    @Column(nullable = false)
    private String routingKey;
    @Column(nullable = false, columnDefinition = "CLOB")
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    @Column(length = 500)
    private String lastError;
    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    @PrePersist
    void beforeInsert() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = OutboxStatus.PENDING;
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (nextAttemptAt == null) nextAttemptAt = createdAt;
    }
}
