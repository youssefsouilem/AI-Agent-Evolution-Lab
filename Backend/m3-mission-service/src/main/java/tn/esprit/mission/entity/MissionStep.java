package tn.esprit.mission.entity;

import jakarta.persistence.*;
import java.time.Instant;
import tn.esprit.mission.enums.NodeType;

@Entity
@Table(name = "mission_steps")
public class MissionStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long missionId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private Integer stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NodeType nodeType;

    @Column(length = 100)
    private String agentId;

    @Column(length = 100)
    private String agentVersionId;

    @Column(length = 4000)
    private String instruction;

    @Column(columnDefinition = "LONGTEXT")
    private String inputMapping;

    @Column(length = 1000)
    private String expectedOutput;

    private Double positionX;
    private Double positionY;
    private Integer maxRetries;
    private String conditionVariable;
    private String conditionOperator;
    private Double conditionExpected;
    private Double conditionSampleValue;
    private String agentRole;

    public MissionStep() {}

    public Double getPositionX() {
        return positionX;
    }

    public void setPositionX(Double positionX) {
        this.positionX = positionX;
    }

    public Double getPositionY() {
        return positionY;
    }

    public void setPositionY(Double positionY) {
        this.positionY = positionY;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getConditionVariable() {
        return conditionVariable;
    }

    public void setConditionVariable(String conditionVariable) {
        this.conditionVariable = conditionVariable;
    }

    public String getConditionOperator() {
        return conditionOperator;
    }

    public void setConditionOperator(String conditionOperator) {
        this.conditionOperator = conditionOperator;
    }

    public Double getConditionExpected() {
        return conditionExpected;
    }

    public void setConditionExpected(Double conditionExpected) {
        this.conditionExpected = conditionExpected;
    }

    public Double getConditionSampleValue() {
        return conditionSampleValue;
    }

    public void setConditionSampleValue(Double conditionSampleValue) {
        this.conditionSampleValue = conditionSampleValue;
    }

    public String getAgentRole() {
        return agentRole;
    }

    public void setAgentRole(String agentRole) {
        this.agentRole = agentRole;
    }

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(Integer stepOrder) {
        this.stepOrder = stepOrder;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
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

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public String getInputMapping() {
        return inputMapping;
    }

    public void setInputMapping(String inputMapping) {
        this.inputMapping = inputMapping;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }
}
