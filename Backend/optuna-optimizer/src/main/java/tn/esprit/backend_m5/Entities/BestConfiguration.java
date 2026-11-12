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
@Table(name = "best_configuration")
@Getter
@Setter
@NoArgsConstructor
public class BestConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_id", nullable = false, unique = true)
    private OptimizationStudy study;

    /** Essai gagnant. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trial_id", nullable = false)
    private Trial trial;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> params;

    @Column(nullable = false)
    private Double score;

    @Column(nullable = false)
    private boolean appliedToM1 = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
