package tn.esprit.agentservice.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "agent_configurations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    private double temperature = 0.7;

    @Column(nullable = true)
    private Integer maxTokens;

    @Column(nullable = true)
    private String style;

    @Builder.Default
    private int maxTurns = 3;

    @Builder.Default
    private int timeoutSeconds = 30;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "agent_configuration_tools",
            joinColumns = @JoinColumn(name = "configuration_id")
    )
    @Column(name = "tool_id")
    @Builder.Default
    private List<Long> allowedToolIds = new ArrayList<>();
}
