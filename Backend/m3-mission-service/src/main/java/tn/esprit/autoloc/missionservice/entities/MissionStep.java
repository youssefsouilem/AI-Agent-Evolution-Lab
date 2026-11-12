
package tn.esprit.autoloc.missionservice.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MissionStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer stepOrder;

    private String name;

    private Long agentVersionId;

    @ManyToOne
    @JoinColumn(name = "mission_id")
    private Mission mission;
}
