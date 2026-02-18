package com.dm.planning;

import com.dm.bean.Plan4TruckDetail;
import com.dm.bean.PlanOutput4TruckDetail;
import java.util.List;

public interface Optimization4TruckService<T> {
    PlanOutput4TruckDetail<T> callEngine(List<Plan4TruckDetail> planDetails);
}