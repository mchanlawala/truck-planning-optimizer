package com.dm.grouping.strategy;

import com.dm.bean.*;
import com.dm.util.CollectionUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SimpleGroupingStrategy4Truck implements GroupingStrategy4Truck {

    @Override
    public GroupIterator4Truck group(List<Material> materials, int planType) {
        Set<ParameterGroup4Truck> paramGroup = new HashSet<>();
        
        List<ParameterGroup4Truck> groups = CollectionUtil.collect(materials, 
                new CollectionUtil.IList<Material, ParameterGroup4Truck>() {
                    @Override
                    public ParameterGroup4Truck collect(Material material) {
                        return new ParameterGroup4Truck(material.getLocation());
                    }
                });
        paramGroup.addAll(groups);
        
        return new SimpleGroupIterator4Truck(paramGroup, materials, planType);
    }
}