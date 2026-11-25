package tn.esprit.mission.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mission_event_outbox", uniqueConstraints = @UniqueConstraint(columnNames = {"executionId", "eventType"}))
public class MissionEventOutbox {
    @Id public String id;
    @Column(nullable = false) public Long executionId;
    @Column(nullable = false) public String eventType;
    @Column(columnDefinition = "TEXT", nullable = false) public String payload;
    public Instant createdAt;
    public Instant publishedAt;
    public Instant nextAttemptAt;
    public int attempts;
    public String lastError;
}
