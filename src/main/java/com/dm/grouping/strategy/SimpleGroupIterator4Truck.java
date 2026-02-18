package com.dm.grouping.strategy;

import com.dm.bean.*;
import com.dm.util.CollectionUtil;

import java.util.*;

public class SimpleGroupIterator4Truck implements GroupIterator4Truck {
    
    private final Iterator<ParameterGroup4Truck> paramGroupIterator;
    private final List<Material> materials;
    private final int planType;
    
    public SimpleGroupIterator4Truck(Set<ParameterGroup4Truck> paramGroups, 
                                      List<Material> materials, 
                                      int planType) {
        this.paramGroupIterator = paramGroups.iterator();
        this.materials = materials;
        this.planType = planType;
    }
    
    @Override
    public boolean hasNext() {
        return paramGroupIterator.hasNext();
    }
    
    @Override
    public List<Group4Truck> next() {
        ParameterGroup4Truck groupParam = paramGroupIterator.next();
        List<Group4Truck> groups = new ArrayList<>();
        
        List<Material> filteredMaterials = CollectionUtil.filter(materials, 
                new CollectionUtil.Filter<Material>() {
                    @Override
                    public boolean accept(Material material) {
                        return groupParam.getLocation().equals(material.getLocation());
                    }
                });
        
        groups.add(new SimpleGroup4Truck(filteredMaterials, groupParam));
        return groups;
    }
    
    private static class SimpleGroup4Truck implements Group4Truck {
        private final List<Material> materials;
        private final ParameterGroup4Truck groupParams;
        
        public SimpleGroup4Truck(List<Material> materials, ParameterGroup4Truck groupParams) {
            this.materials = materials;
            this.groupParams = groupParams;
        }
        
        @Override
        public List<Material> getMaterials() {
            return materials;
        }
        
        @Override
        public ParameterGroup4Truck getGroupParams() {
            return groupParams;
        }
        
        @Override
        public String toString() {
            return "Group [location=" + groupParams.getLocation() + 
                   ", materials=" + materials.size() + "]";
        }
    }
}