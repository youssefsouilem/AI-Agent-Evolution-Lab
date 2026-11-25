package tn.esprit.mission.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mission_participants")
public class MissionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long missionId;

    @Column(nullable = false, length = 100)
    private String agentId;

    @Column(length = 100)
    private String agentVersionId;

    @Column(nullable = false, length = 100)
    private String role;

    public MissionParticipant() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMissionId() {
        return missionId;
    }

    public void setMissionId(Long missionId) {
        this.missionId = missionId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getAgentVersionId() {
        return agentVersionId;
    }

    public void setAgentVersionId(String agentVersionId) {
        this.agentVersionId = agentVersionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
