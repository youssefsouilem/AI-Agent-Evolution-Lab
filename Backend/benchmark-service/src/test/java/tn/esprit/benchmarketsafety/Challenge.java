package tn.esprit.benchmarketsafety;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 4000)
    private String prompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScoringType scoringType;

    @Column(length = 1000)
    private String expectedKeywords;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "split_type")
    private Split split;

    private int timeoutMs = 30000;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "benchmark_id")
    private Benchmark benchmark;
}