package com.dm.bean;

public class Material {
    private String batchNo;
    private String orderNo;
    private String orderLineItem;
    private double stockWeight;
    private double stockWidth;
    private double stockLength;
    private String location;

    // Getters and Setters
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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public String toString() {
        return "Material [batchNo=" + batchNo + ", orderNo=" + orderNo + 
               ", weight=" + stockWeight + ", location=" + location + "]";
    }
}