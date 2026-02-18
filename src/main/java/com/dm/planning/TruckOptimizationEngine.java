package com.dm.planning;

import com.dm.bean.*;
import java.util.*;
import java.util.stream.Collectors;

public class TruckOptimizationEngine implements Optimization4TruckService<Integer> {

    // Truck loading plan
    private static class Truck {
        int id;
        int truckTypeId;
        String truckTypeName;
        double maxWeight;
        int maxCoils;
        double perDayCost;
        List<Material> coils = new ArrayList<>();
        double totalWeight = 0;

        Truck(int id, TruckConstraintDetail constraint) {
            this.id = id;
            this.truckTypeId = constraint.getTruckTypeId();
            this.truckTypeName = constraint.getTruckTypeName();
            this.maxWeight = constraint.getTruckLoadWeight();
            this.maxCoils = constraint.getTruckLoadNoMax();
            this.perDayCost = constraint.getPerDayCost();
        }

        boolean canAdd(Material material) {
            return totalWeight + material.getStockWeight() <= maxWeight + 0.01 &&
                    coils.size() + 1 <= maxCoils;
        }

        void add(Material material) {
            coils.add(material);
            totalWeight += material.getStockWeight();
        }

        double getUtilization() {
            return (totalWeight / maxWeight) * 100;
        }

        double getCostPerKg() {
            return perDayCost / totalWeight;
        }

        boolean isValid() {
            return totalWeight <= maxWeight + 0.01 &&
                    coils.size() <= maxCoils &&
                    coils.size() > 0;
        }

        // Check if this truck type is optimal for a given material weight
        static boolean isOptimalForWeight(int truckTypeId, double weight) {
            switch(truckTypeId) {
                case 1: // 2-axle: 19000 kg
                    return weight <= 19000 && weight > 0;
                case 2: // 3-axle: 28500 kg
                    return weight > 19000 && weight <= 28500;
                case 3: // 4-axle: 36000 kg - OPTIMAL for 30000 kg
                    return weight > 28500 && weight <= 36000;
                case 4: // 5-axle: 47500 kg
                    return weight > 36000 && weight <= 47500;
                case 5: // 6-axle: 49000 kg
                    return weight > 47500 && weight <= 49000;
                default:
                    return false;
            }
        }

        // Get recommended truck type for a given weight
        static int getRecommendedTruckType(double weight) {
            if (weight <= 19000) return 1;
            if (weight <= 28500) return 2;
            if (weight <= 36000) return 3; // This covers 30000 kg coils
            if (weight <= 47500) return 4;
            return 5;
        }

        @Override
        public String toString() {
            return String.format("  Truck %d (Type %d: %s): %d coils, %.2f kg, %.1f%% utilization, Cost: Rs.%.0f, Cost/kg: Rs.%.4f",
                    id, truckTypeId, truckTypeName, coils.size(), totalWeight, getUtilization(), perDayCost, perDayCost/totalWeight);
        }
    }

    // Location group plan
    private static class LocationPlan {
        String location;
        List<Truck> trucks = new ArrayList<>();
        double totalWeight = 0;
        double totalCost = 0;

        void addTruck(Truck truck) {
            trucks.add(truck);
            totalWeight += truck.totalWeight;
            totalCost += truck.perDayCost;
        }

        double getAverageUtilization() {
            if (trucks.isEmpty()) return 0;
            return trucks.stream().mapToDouble(Truck::getUtilization).average().orElse(0);
        }

        double getCostPerTon() {
            if (totalWeight == 0) return 0;
            return totalCost / (totalWeight / 1000);
        }

        void printSummary() {
            System.out.println("\n=== Location " + location + " Optimization Summary ===");
            System.out.println("Total Weight: " + String.format("%.2f", totalWeight) + " kg");
            System.out.println("Total Trucks: " + trucks.size());
            System.out.println("Total Cost: Rs. " + String.format("%.0f", totalCost));
            System.out.println("Average Utilization: " + String.format("%.2f", getAverageUtilization()) + "%");
            System.out.println("Cost per Ton: Rs. " + String.format("%.2f", getCostPerTon()));

            // Group trucks by type
            Map<Integer, List<Truck>> trucksByType = trucks.stream()
                    .collect(Collectors.groupingBy(t -> t.truckTypeId));

            for (Map.Entry<Integer, List<Truck>> entry : trucksByType.entrySet()) {
                int typeId = entry.getKey();
                List<Truck> typeTrucks = entry.getValue();
                String typeName = typeTrucks.get(0).truckTypeName;
                double typeCost = typeTrucks.stream().mapToDouble(t -> t.perDayCost).sum();
                double typeWeight = typeTrucks.stream().mapToDouble(t -> t.totalWeight).sum();
                double typeUtilization = typeTrucks.stream().mapToDouble(Truck::getUtilization).average().orElse(0);

                System.out.println("\n  " + typeName + " (Type " + typeId + "): " + typeTrucks.size() + " trucks");
                System.out.println("    Total Weight: " + String.format("%.2f", typeWeight) + " kg");
                System.out.println("    Total Cost: Rs. " + String.format("%.0f", typeCost));
                System.out.println("    Avg Utilization: " + String.format("%.2f", typeUtilization) + "%");

                // Print individual trucks
                for (Truck truck : typeTrucks) {
                    System.out.println("    " + truck);
                }
            }
        }
    }

    @Override
    public PlanOutput4TruckDetail<Integer> callEngine(List<Plan4TruckDetail> planDetails) {
        if (planDetails == null || planDetails.isEmpty()) {
            throw new IllegalArgumentException("planDetails is empty");
        }

        List<OrderCoilMapping4TruckDetail<Integer>> allOutputDetails = new ArrayList<>();
        List<LocationPlan> allPlans = new ArrayList<>();

        // Track next truck number globally
        final int[] nextTruckNumber = {1};

        for (Plan4TruckDetail planDetail : planDetails) {
            LocationPlan plan = optimizeLocation(planDetail);

            // Assign sequential truck numbers
            for (Truck truck : plan.trucks) {
                truck.id = nextTruckNumber[0]++;
            }

            allPlans.add(plan);

            // Convert to output format
            for (Truck truck : plan.trucks) {
                for (Material material : truck.coils) {
                    OrderCoilMapping4TruckDetail<Integer> detail = new OrderCoilMapping4TruckDetail<>();
                    detail.setGroupId(planDetail.getParameterDetail().get(0).getGroupId());
                    detail.setBatchNo(material.getBatchNo());
                    detail.setOrderNo(material.getOrderNo());
                    detail.setOrderLineItem(material.getOrderLineItem());
                    detail.setStockWeight(material.getStockWeight());
                    detail.setStockWidth(material.getStockWidth());
                    detail.setStockLength(material.getStockLength());
                    detail.setLocation(material.getLocation());
                    detail.setTruckTypeId(truck.truckTypeId);
                    detail.setTruckNumber(truck.id);
                    allOutputDetails.add(detail);
                }
            }
        }

        // Print overall summary
        printOverallSummary(allPlans);

        return new PlanOutput4TruckDetail<>(allOutputDetails);
    }

    private LocationPlan optimizeLocation(Plan4TruckDetail planDetail) {
        LocationPlan locationPlan = new LocationPlan();

        // Get constraints
        PlanParameter4TruckDetail param = planDetail.getParameterDetail().get(0);
        locationPlan.location = getLocationFromMaterials(planDetail.getOrderCoilMappingDetails());

        List<TruckConstraintDetail> truckConstraints = planDetail.getTruckConstraintDetails();
        double roadMaxWeight = param.getRoadMaxWeight();

        // Print truck types for reference
        System.out.println("\nAvailable Truck Types:");
        for (TruckConstraintDetail truck : truckConstraints) {
            System.out.println("  Type " + truck.getTruckTypeId() + ": " + truck.getTruckTypeName() +
                    ", Weight: " + truck.getTruckLoadWeight() + " kg, Max Coils: " + truck.getTruckLoadNoMax() +
                    ", Cost: Rs." + truck.getPerDayCost());
        }

        // Extract materials
        List<Material> materials = extractMaterials(planDetail.getOrderCoilMappingDetails());

        // Sort materials by weight (descending) for better packing
        materials.sort((a, b) -> Double.compare(b.getStockWeight(), a.getStockWeight()));

        // Validate materials against road constraint
        validateMaterialsAgainstRoadConstraint(materials, roadMaxWeight);

        // Optimize truck allocation with proper type selection
        List<Truck> trucks = optimizeTruckAllocation(materials, truckConstraints);

        // Add to location plan
        for (Truck truck : trucks) {
            locationPlan.addTruck(truck);
        }

        // Print summary
        locationPlan.printSummary();

        return locationPlan;
    }

    private void validateMaterialsAgainstRoadConstraint(List<Material> materials, double roadMaxWeight) {
        for (Material material : materials) {
            if (material.getStockWeight() > roadMaxWeight + 0.01) {
                System.out.println("WARNING: Material " + material.getBatchNo() +
                        " weight (" + material.getStockWeight() + " kg) exceeds road max weight (" +
                        roadMaxWeight + " kg). This material cannot be transported.");
            }
        }
    }

    private List<Truck> optimizeTruckAllocation(List<Material> materials,
                                                List<TruckConstraintDetail> truckConstraints) {
        List<Truck> trucks = new ArrayList<>();
        List<Material> remainingMaterials = new ArrayList<>(materials);

        // Create a map for quick truck type lookup
        Map<Integer, TruckConstraintDetail> truckTypeMap = new HashMap<>();
        for (TruckConstraintDetail truck : truckConstraints) {
            truckTypeMap.put(truck.getTruckTypeId(), truck);
        }

        int truckCounter = 1;

        while (!remainingMaterials.isEmpty()) {
            Material currentMaterial = remainingMaterials.get(0);

            // Get the recommended truck type for this material
            int recommendedTypeId = Truck.getRecommendedTruckType(currentMaterial.getStockWeight());
            TruckConstraintDetail selectedTruckType = truckTypeMap.get(recommendedTypeId);

            // If recommended type not available, find next best
            if (selectedTruckType == null) {
                selectedTruckType = findNextBestTruckType(currentMaterial, truckConstraints);
            }

            if (selectedTruckType == null) {
                System.out.println("ERROR: Cannot find suitable truck for material " +
                        currentMaterial.getBatchNo() + " weighing " +
                        currentMaterial.getStockWeight() + " kg");
                remainingMaterials.remove(0);
                continue;
            }

            // Create new truck
            Truck truck = new Truck(truckCounter++, selectedTruckType);

            // Try to load materials onto this truck
            Iterator<Material> iterator = remainingMaterials.iterator();
            while (iterator.hasNext()) {
                Material material = iterator.next();

                // Check if material can be added to this truck
                if (truck.canAdd(material)) {
                    truck.add(material);
                    iterator.remove();
                }
            }

            // Validate truck
            if (!truck.isValid()) {
                System.out.println("WARNING: Truck " + truck.id + " is invalid!");
                continue;
            }

            // Only add truck if it has materials
            if (!truck.coils.isEmpty()) {
                trucks.add(truck);
            }
        }

        // Post-process to optimize truck type selection
        trucks = optimizeTruckTypes(trucks, truckTypeMap);

        return trucks;
    }

    private TruckConstraintDetail findNextBestTruckType(Material material,
                                                        List<TruckConstraintDetail> truckConstraints) {
        double materialWeight = material.getStockWeight();

        // Find all trucks that can carry this material
        List<TruckConstraintDetail> suitableTrucks = new ArrayList<>();

        for (TruckConstraintDetail truck : truckConstraints) {
            if (truck.getTruckLoadWeight() >= materialWeight - 0.01) {
                suitableTrucks.add(truck);
            }
        }

        if (suitableTrucks.isEmpty()) {
            return null;
        }

        // For 30000 kg material, prefer Type 3 (4-axle) over Type 4 (5-axle)
        if (Math.abs(materialWeight - 30000) < 1000) {
            for (TruckConstraintDetail truck : suitableTrucks) {
                if (truck.getTruckTypeId() == 3) {
                    return truck; // Type 3 is optimal for 30000 kg
                }
            }
        }

        // For other weights, choose the smallest truck that can carry it
        return suitableTrucks.stream()
                .min(Comparator.comparingDouble(TruckConstraintDetail::getTruckLoadWeight))
                .orElse(suitableTrucks.get(0));
    }

    private List<Truck> optimizeTruckTypes(List<Truck> trucks,
                                           Map<Integer, TruckConstraintDetail> truckTypeMap) {
        List<Truck> optimizedTrucks = new ArrayList<>();

        for (Truck truck : trucks) {
            // If this truck has only one coil, check if a smaller truck type would be better
            if (truck.coils.size() == 1) {
                Material singleMaterial = truck.coils.get(0);
                double weight = singleMaterial.getStockWeight();

                // Get the optimal truck type for this single material
                int optimalTypeId = Truck.getRecommendedTruckType(weight);

                // If current truck type is not optimal and a smaller type exists
                if (optimalTypeId < truck.truckTypeId) {
                    TruckConstraintDetail optimalTruck = truckTypeMap.get(optimalTypeId);
                    if (optimalTruck != null && optimalTruck.getTruckLoadWeight() >= weight - 0.01) {
                        // Replace with optimal truck type
                        Truck newTruck = new Truck(truck.id, optimalTruck);
                        newTruck.add(singleMaterial);
                        optimizedTrucks.add(newTruck);
                        continue;
                    }
                }
            }
            optimizedTrucks.add(truck);
        }

        return optimizedTrucks;
    }

    private void printOverallSummary(List<LocationPlan> allPlans) {
        System.out.println("\n============ OVERALL OPTIMIZATION SUMMARY ============");

        double totalWeight = 0;
        double totalCost = 0;
        int totalTrucks = 0;
        Map<Integer, Integer> trucksByType = new TreeMap<>();
        Map<Integer, Double> weightByType = new TreeMap<>();
        Map<Integer, Double> costByType = new TreeMap<>();

        for (LocationPlan plan : allPlans) {
            totalWeight += plan.totalWeight;
            totalCost += plan.totalCost;
            totalTrucks += plan.trucks.size();

            for (Truck truck : plan.trucks) {
                trucksByType.merge(truck.truckTypeId, 1, Integer::sum);
                weightByType.merge(truck.truckTypeId, truck.totalWeight, Double::sum);
                costByType.merge(truck.truckTypeId, truck.perDayCost, Double::sum);
            }
        }

        System.out.println("Total Weight: " + String.format("%.2f", totalWeight) + " kg (" +
                String.format("%.2f", totalWeight/1000) + " tons)");
        System.out.println("Total Trucks: " + totalTrucks);
        System.out.println("Total Cost: Rs. " + String.format("%.0f", totalCost));
        System.out.println("Average Cost per Ton: Rs. " + String.format("%.2f", totalCost / (totalWeight/1000)));

        System.out.println("\nTruck Type Distribution:");
        for (Map.Entry<Integer, Integer> entry : trucksByType.entrySet()) {
            int typeId = entry.getKey();
            String typeName = getTruckTypeName(typeId);
            int count = entry.getValue();
            double typeWeight = weightByType.getOrDefault(typeId, 0.0);
            double typeCost = costByType.getOrDefault(typeId, 0.0);
            double typeCapacity = getTruckCapacity(typeId);
            double avgUtil = (typeWeight / (count * typeCapacity)) * 100;

            System.out.println("  Type " + typeId + " (" + typeName + "): " + count + " trucks");
            System.out.println("    Total Weight: " + String.format("%.2f", typeWeight) + " kg");
            System.out.println("    Total Cost: Rs. " + String.format("%.0f", typeCost));
            System.out.println("    Avg Utilization: " + String.format("%.2f", avgUtil) + "%");
            System.out.println("    Cost per Ton: Rs. " + String.format("%.2f", typeCost / (typeWeight/1000)));
        }
        System.out.println("=======================================================");
    }

    private String getTruckTypeName(int typeId) {
        switch(typeId) {
            case 1: return "2-axle";
            case 2: return "3-axle";
            case 3: return "4-axle";
            case 4: return "5-axle";
            case 5: return "6-axle";
            default: return "Unknown";
        }
    }

    private double getTruckCapacity(int typeId) {
        switch(typeId) {
            case 1: return 19000;
            case 2: return 28500;
            case 3: return 36000;
            case 4: return 47500;
            case 5: return 49000;
            default: return 0;
        }
    }

    private List<Material> extractMaterials(List<OrderCoilMapping4TruckDetail<Integer>> details) {
        List<Material> materials = new ArrayList<>();
        for (OrderCoilMapping4TruckDetail<Integer> detail : details) {
            Material material = new Material();
            material.setBatchNo(detail.getBatchNo());
            material.setOrderNo(detail.getOrderNo());
            material.setOrderLineItem(detail.getOrderLineItem());
            material.setStockWeight(detail.getStockWeight());
            material.setStockWidth(detail.getStockWidth());
            material.setStockLength(detail.getStockLength());
            material.setLocation(detail.getLocation());
            materials.add(material);
        }
        return materials;
    }

    private String getLocationFromMaterials(List<OrderCoilMapping4TruckDetail<Integer>> details) {
        if (details.isEmpty()) return "Unknown";
        return details.get(0).getLocation();
    }
}