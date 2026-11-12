
package tn.esprit.autoloc.missionservice.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MissionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long agentId;

    private Long agentVersionId;

    private String role;

    @ManyToOne
    @JoinColumn(name = "mission_id")
    private Mission mission;
}
