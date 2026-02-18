package com.dm.bean;

public class TruckConstraintDetail {
    private int truckTypeId;
    private String truckTypeName;
    private double truckLoadWeight;
    private int truckLoadNoMax;
    private double perDayCost;

    public TruckConstraintDetail(int truckTypeId, String truckTypeName, 
                                  double truckLoadWeight, int truckLoadNoMax, 
                                  double perDayCost) {
        this.truckTypeId = truckTypeId;
        this.truckTypeName = truckTypeName;
        this.truckLoadWeight = truckLoadWeight;
        this.truckLoadNoMax = truckLoadNoMax;
        this.perDayCost = perDayCost;
    }

    // Getters and Setters
    public int getTruckTypeId() {
        return truckTypeId;
    }

    public void setTruckTypeId(int truckTypeId) {
        this.truckTypeId = truckTypeId;
    }

    public String getTruckTypeName() {
        return truckTypeName;
    }

    public void setTruckTypeName(String truckTypeName) {
        this.truckTypeName = truckTypeName;
    }

    public double getTruckLoadWeight() {
        return truckLoadWeight;
    }

    public void setTruckLoadWeight(double truckLoadWeight) {
        this.truckLoadWeight = truckLoadWeight;
    }

    public int getTruckLoadNoMax() {
        return truckLoadNoMax;
    }

    public void setTruckLoadNoMax(int truckLoadNoMax) {
        this.truckLoadNoMax = truckLoadNoMax;
    }

    public double getPerDayCost() {
        return perDayCost;
    }

    public void setPerDayCost(double perDayCost) {
        this.perDayCost = perDayCost;
    }

    @Override
    public String toString() {
        return "TruckConstraint [type=" + truckTypeName + ", weight=" + 
               truckLoadWeight + ", maxCoils=" + truckLoadNoMax + "]";
    }
}