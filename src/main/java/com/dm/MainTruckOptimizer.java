package com.dm;

import com.dm.bean.*;
import com.dm.grouping.strategy.SimpleGroupingStrategy4Truck;
import com.dm.planning.HybridTruckOptimizer;
import com.dm.planning.JeneticsTruckOptimizer;
import com.dm.planning.Optimization4TruckService;
import com.dm.planning.advanced.MOEATruckOptimizer;
import com.dm.util.CollectionUtil;
import com.dm.util.ExcelReader;
import com.dm.util.ExcelWriter;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

public class MainTruckOptimizer {

    public static void main(String[] args) {
        System.out.println("Starting Truck Planning Optimizer...");
        System.out.println("Working directory: " + System.getProperty("user.dir"));

        GroupingStrategy4Truck strategy = new SimpleGroupingStrategy4Truck();
        final String basePath = System.getProperty("user.dir") + "/excel/";
        System.out.println("Excel path: " + basePath);

        ExcelReader excelReader = new ExcelReader();

        // Read input data
        final LinkedHashMap<Integer, LinkedHashMap<String, Object>> planningData = excelReader
                .getDataFromFile(basePath + "TruckProjectionPlanner_Input.xlsx", "Planning Parameter");

        final LinkedHashMap<Integer, LinkedHashMap<String, Object>> roadConstraintData = excelReader
                .getDataFromFile(basePath + "TruckProjectionPlanner_Input.xlsx", "Rode Constraint");

        final LinkedHashMap<Integer, LinkedHashMap<String, Object>> truckConstraintData = excelReader
                .getDataFromFile(basePath + "TruckProjectionPlanner_Input.xlsx", "Truck Constraint");

        final LinkedHashMap<Integer, LinkedHashMap<String, Object>> materialData = excelReader
                .getDataFromFile(basePath + "TruckProjectionPlanner_Input.xlsx", "Material");

        List<Material> materials = getMaterialData(materialData);

        // Group materials by location
        GroupIterator4Truck iterator = strategy.group(materials,
                (int) Double.parseDouble(planningData.get(2).get("SCENARIO_TYPE").toString()));

        // Use the truck optimization engine
        Optimization4TruckService service = new JeneticsTruckOptimizer();

        // Track all outputs
        List<PlanOutput4TruckDetail<Integer>> allPlanOutputDetails = new LinkedList<>();

        // Track group IDs sequentially
        final int[] globalGroupId = {1};

        CollectionUtil.each(iterator, new CollectionUtil.IIterate<List<Group4Truck>>() {
            @Override
            public void process(List<Group4Truck> groups) {
                List<Plan4TruckDetail> planParameters = new LinkedList<>();

                // Process each group within the current location
                for (Group4Truck group : groups) {
                    System.out.println("\nProcessing group " + globalGroupId[0] + ": " + group);

                    // Get road constraint
                    double roadMaxWeight = Double.parseDouble(
                            roadConstraintData.get(2).get("ROAD_MAX_WEIGHT").toString());

                    // Create plan parameter
                    PlanParameter4TruckDetail paramDetail = new PlanParameter4TruckDetail();
                    paramDetail.setGroupId(globalGroupId[0]);
                    paramDetail.setPlanId(planningData.get(2).get("PLAN_ID").toString());
                    paramDetail.setScenarioType(
                            (int) Double.parseDouble(planningData.get(2).get("SCENARIO_TYPE").toString()));
                    paramDetail.setRoadMaxWeight(roadMaxWeight);

                    List<PlanParameter4TruckDetail> paramDetails = new LinkedList<>();
                    paramDetails.add(paramDetail);

                    // Create truck constraints
                    List<TruckConstraintDetail> truckConstraints = getTruckConstraints(truckConstraintData);

                    // Create plan detail
                    Plan4TruckDetail planDetail = new Plan4TruckDetail(
                            planningData.get(2).get("PLAN_ID").toString(),
                            (int) Double.parseDouble(planningData.get(2).get("SCENARIO_TYPE").toString()));

                    planDetail.setParameterDetail(paramDetails);
                    planDetail.setTruckConstraintDetails(truckConstraints);
                    planDetail.setOrderCoilMappingDetails(
                            getMaterialMappingDetail(globalGroupId[0], group.getMaterials()));

                    planParameters.add(planDetail);

                    globalGroupId[0]++;
                }

                // Call the optimization engine for this location group
                PlanOutput4TruckDetail<Integer> outputDetail = service.callEngine(planParameters);
                allPlanOutputDetails.add(outputDetail);
            }
        });

        // Renumber trucks sequentially across all groups
        renumberTrucksSequentially(allPlanOutputDetails);

        // Write all outputs to Excel
        ExcelWriter excelWriter = new ExcelWriter();
        excelWriter.writeTruckOutputPlan(basePath + "TruckProjectionPlanner_Output.xlsx", allPlanOutputDetails);
        System.out.println("\nOutput written successfully!");
    }

    private static void renumberTrucksSequentially(List<PlanOutput4TruckDetail<Integer>> allOutputs) {
        int nextTruckNumber = 1;

        for (PlanOutput4TruckDetail<Integer> output : allOutputs) {
            // Get unique truck numbers in this output
            List<Integer> uniqueTruckNumbers = output.getOrderCoilMappingDetails().stream()
                    .map(OrderCoilMapping4TruckDetail::getTruckNumber)
                    .distinct()
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());

            // Create mapping from old to new truck numbers
            java.util.Map<Integer, Integer> truckMapping = new java.util.HashMap<>();
            for (Integer oldNumber : uniqueTruckNumbers) {
                truckMapping.put(oldNumber, nextTruckNumber++);
            }

            // Update truck numbers
            for (OrderCoilMapping4TruckDetail<Integer> detail : output.getOrderCoilMappingDetails()) {
                Integer newNumber = truckMapping.get(detail.getTruckNumber());
                if (newNumber != null) {
                    detail.setTruckNumber(newNumber);
                }
            }
        }
    }

    private static List<OrderCoilMapping4TruckDetail<Integer>> getMaterialMappingDetail(
            int groupId, List<Material> materials) {
        List<OrderCoilMapping4TruckDetail<Integer>> orderCoilMappingDetails = new LinkedList<>();
        for (Material material : materials) {
            OrderCoilMapping4TruckDetail<Integer> orderCoilDetail = new OrderCoilMapping4TruckDetail<>();
            orderCoilDetail.setBatchNo(material.getBatchNo());
            orderCoilDetail.setOrderNo(material.getOrderNo());
            orderCoilDetail.setOrderLineItem(material.getOrderLineItem());
            orderCoilDetail.setStockWeight(material.getStockWeight());
            orderCoilDetail.setStockWidth(material.getStockWidth());
            orderCoilDetail.setStockLength(material.getStockLength());
            orderCoilDetail.setLocation(material.getLocation());
            orderCoilDetail.setGroupId(groupId);
            orderCoilMappingDetails.add(orderCoilDetail);
        }
        return orderCoilMappingDetails;
    }

    private static List<TruckConstraintDetail> getTruckConstraints(
            LinkedHashMap<Integer, LinkedHashMap<String, Object>> truckConstraintData) {
        List<TruckConstraintDetail> constraints = new LinkedList<>();
        for (int key : truckConstraintData.keySet()) {
            LinkedHashMap<String, Object> data = truckConstraintData.get(key);
            constraints.add(new TruckConstraintDetail(
                    (int) Double.parseDouble(data.get("TRUCK_TYPE_ID").toString()),
                    data.get("TRUCK_TYPE_NAME").toString(),
                    Double.parseDouble(data.get("TRUCK_LOAD_WEIGHT").toString()),
                    (int) Double.parseDouble(data.get("TRUCK_LOAD_NO_MAX").toString()),
                    Double.parseDouble(data.get("PER_DAY_COST").toString())));
        }
        return constraints;
    }

    private static List<Material> getMaterialData(
            LinkedHashMap<Integer, LinkedHashMap<String, Object>> materialData) {
        List<Material> materials = new LinkedList<>();
        for (Integer cnt : materialData.keySet()) {
            try {
                LinkedHashMap<String, Object> data = materialData.get(cnt);
                Material material = new Material();
                material.setBatchNo(data.get("BATCH_NUMBER").toString());
                material.setOrderNo(data.get("ORDER_NUMBER").toString());
                material.setOrderLineItem(data.get("ORDER_LINE_NUMBER").toString());
                material.setStockWeight(Double.parseDouble(data.get("STOCK_WEIGHT").toString()));
                material.setStockWidth(Double.parseDouble(data.get("STOCK_WIDTH").toString()));
                material.setStockLength(Double.parseDouble(data.get("STOCK_LENGTH").toString()));
                material.setLocation(data.get("LOCATION").toString());
                materials.add(material);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return materials;
    }
}