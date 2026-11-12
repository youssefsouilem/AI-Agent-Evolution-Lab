package tn.esprit.backend_m5.Entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "trial")
@Getter
@Setter
@NoArgsConstructor
public class Trial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_id", nullable = false)
    private OptimizationStudy study;

    @Column(nullable = false)
    private int trialNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> params;

    /** Reference vers le run M4 qui a produit le score. */
    private Long benchmarkRunId;

    private Double score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrialStatus status = TrialStatus.RUNNING;

    @Column(length = 1000)
    private String errorMessage;

    private LocalDateTime startedAt = LocalDateTime.now();

    private LocalDateTime finishedAt;
}
