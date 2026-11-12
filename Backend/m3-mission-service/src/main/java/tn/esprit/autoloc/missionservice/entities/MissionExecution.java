
package tn.esprit.autoloc.missionservice.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MissionExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ExecutionStatus status;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String finalResult;

    private Long totalTokensUsed;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @ManyToOne
    @JoinColumn(name = "mission_id")
    private Mission mission;
}
