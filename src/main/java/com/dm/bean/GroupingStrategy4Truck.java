package com.dm.bean;

import java.util.List;

public interface GroupingStrategy4Truck {
    GroupIterator4Truck group(List<Material> materials, int planType);
}