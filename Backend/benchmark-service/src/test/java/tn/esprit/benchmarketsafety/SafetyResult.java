package tn.esprit.benchmarketsafety;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class SafetyResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Référence logique vers M1 (AgentVersion.id), pas une clé étrangère
    @Column(nullable = false)
    private Long agentVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SafetyVerdict verdict;

    @Column(length = 2000)
    private String observedToolCalls;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "safety_test_case_id")
    private SafetyTestCase safetyTestCase;
}