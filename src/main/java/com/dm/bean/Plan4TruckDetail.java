package com.dm.bean;

import java.util.List;

public class Plan4TruckDetail {
    private String planId;
    private int scenarioType;
    private List<PlanParameter4TruckDetail> parameterDetail;
    private List<TruckConstraintDetail> truckConstraintDetails;
    private List<OrderCoilMapping4TruckDetail<Integer>> orderCoilMappingDetails;

    public Plan4TruckDetail(String planId, int scenarioType) {
        this.planId = planId;
        this.scenarioType = scenarioType;
    }

    // Getters and Setters
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

    public List<PlanParameter4TruckDetail> getParameterDetail() {
        return parameterDetail;
    }

    public void setParameterDetail(List<PlanParameter4TruckDetail> parameterDetail) {
        this.parameterDetail = parameterDetail;
    }

    public List<TruckConstraintDetail> getTruckConstraintDetails() {
        return truckConstraintDetails;
    }

    public void setTruckConstraintDetails(List<TruckConstraintDetail> truckConstraintDetails) {
        this.truckConstraintDetails = truckConstraintDetails;
    }

    public List<OrderCoilMapping4TruckDetail<Integer>> getOrderCoilMappingDetails() {
        return orderCoilMappingDetails;
    }

    public void setOrderCoilMappingDetails(List<OrderCoilMapping4TruckDetail<Integer>> orderCoilMappingDetails) {
        this.orderCoilMappingDetails = orderCoilMappingDetails;
    }
}