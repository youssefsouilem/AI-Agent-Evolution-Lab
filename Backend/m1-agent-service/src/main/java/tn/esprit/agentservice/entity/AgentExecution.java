package tn.esprit.agentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_execution")
@Getter
@Setter
@NoArgsConstructor
public class AgentExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long agentId;
    @Column(nullable = false)
    private Long versionId;
    @Column(nullable = false)
    private int versionNumber;
    @Column(nullable = false)
    private String requestedBy;
    private String requestedByRole;
    @Column(nullable = false, length = 20000)
    private String input;
    @Column(columnDefinition = "CLOB")
    private String context;
    @Column(columnDefinition = "CLOB")
    private String overrides;
    @Column(nullable = false, columnDefinition = "CLOB")
    private String appliedSettings;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;
    private String errorCode;
    @Column(length = 1000)
    private String errorMessage;
    @Column(columnDefinition = "CLOB", length = 100000)
    private String output;
    @Column(nullable = false)
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    @Column(nullable = false)
    private int toolCallCount;
    @Column(columnDefinition = "CLOB")
    private String toolCalls;
    private String modelUsed;
    private String provider;

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ExecutionStatus.RUNNING;
        }
    }
}
