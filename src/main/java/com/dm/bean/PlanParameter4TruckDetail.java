package com.dm.bean;

public class PlanParameter4TruckDetail {
    private int groupId;
    private String planId;
    private int scenarioType;
    private double roadMaxWeight;

    // Getters and Setters
    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public int getScenarioType() {
        return scenarioType;
    }

    public void setScenarioType(int scenarioType) {
        this.scenarioType = scenarioType;
    }

    public double getRoadMaxWeight() {
        return roadMaxWeight;
    }

    public void setRoadMaxWeight(double roadMaxWeight) {
        this.roadMaxWeight = roadMaxWeight;
    }
}