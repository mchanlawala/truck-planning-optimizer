package com.dm.bean;

import java.util.List;

public class PlanOutput4TruckDetail<T> {
    private List<OrderCoilMapping4TruckDetail<T>> orderCoilMappingDetails;

    public PlanOutput4TruckDetail(List<OrderCoilMapping4TruckDetail<T>> orderCoilMappingDetails) {
        this.orderCoilMappingDetails = orderCoilMappingDetails;
    }

    public List<OrderCoilMapping4TruckDetail<T>> getOrderCoilMappingDetails() {
        return orderCoilMappingDetails;
    }
}