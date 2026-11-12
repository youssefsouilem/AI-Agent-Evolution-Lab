package tn.esprit.backend_m5.Entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "optimization_study")
@Getter
@Setter
@NoArgsConstructor
public class OptimizationStudy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Reference vers M1 (par API, pas de jointure SQL). */
    @Column(nullable = false)
    private Long agentId;

    /** Reference vers M1. */
    private Long baseAgentVersionId;

    /** Reference vers M4. */
    @Column(nullable = false)
    private Long benchmarkId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> searchSpace;

    @Column(nullable = false)
    private int maxTrials;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StudyStatus status = StudyStatus.PENDING;

    @Column(length = 1000)
    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime finishedAt;

    @OneToMany(mappedBy = "study", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("trialNumber ASC")
    private List<Trial> trials = new ArrayList<>();

    @OneToOne(mappedBy = "study", cascade = CascadeType.ALL, orphanRemoval = true)
    private BestConfiguration bestConfiguration;
}
