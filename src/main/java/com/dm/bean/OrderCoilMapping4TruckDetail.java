package com.dm.bean;

public class OrderCoilMapping4TruckDetail<T> {
    private int groupId;
    private T coilIndex;
    private T orderIndex;
    private double stockWeight;
    private double stockWidth;
    private double stockLength;
    private String batchNo;
    private String orderNo;
    private String orderLineItem;
    private String location;
    private int truckTypeId;
    private int truckNumber;

    // Getters and Setters
    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public T getCoilIndex() {
        return coilIndex;
    }

    public void setCoilIndex(T coilIndex) {
        this.coilIndex = coilIndex;
    }

    public T getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(T orderIndex) {
        this.orderIndex = orderIndex;
    }

    public double getStockWeight() {
        return stockWeight;
    }

    public void setStockWeight(double stockWeight) {
        this.stockWeight = stockWeight;
    }

    public double getStockWidth() {
        return stockWidth;
    }

    public void setStockWidth(double stockWidth) {
        this.stockWidth = stockWidth;
    }

    public double getStockLength() {
        return stockLength;
    }

    public void setStockLength(double stockLength) {
        this.stockLength = stockLength;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getOrderLineItem() {
        return orderLineItem;
    }

    public void setOrderLineItem(String orderLineItem) {
        this.orderLineItem = orderLineItem;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getTruckTypeId() {
        return truckTypeId;
    }

    public void setTruckTypeId(int truckTypeId) {
        this.truckTypeId = truckTypeId;
    }

    public int getTruckNumber() {
        return truckNumber;
    }

    public void setTruckNumber(int truckNumber) {
        this.truckNumber = truckNumber;
    }
}