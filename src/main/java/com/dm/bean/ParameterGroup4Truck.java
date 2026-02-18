package com.dm.bean;

public class ParameterGroup4Truck {
    private String location;

    public ParameterGroup4Truck(String location) {
        this.location = location;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public int hashCode() {
        return 7 * (location != null ? location.hashCode() : 0);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ParameterGroup4Truck)) return false;
        ParameterGroup4Truck other = (ParameterGroup4Truck) obj;
        return location != null ? location.equals(other.location) : other.location == null;
    }

    @Override
    public String toString() {
        return "ParameterGroup [location=" + location + "]";
    }
}