
package tn.esprit.autoloc.missionservice.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(length = 2000)
    private String objective;

    @Enumerated(EnumType.STRING)
    private SelectionMode selectionMode;

    private Integer maxAttempts;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "mission")
    private List<MissionStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "mission")
    private List<MissionParticipant> participants = new ArrayList<>();

    @OneToMany(mappedBy = "mission")
    private List<MissionExecution> executions = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
