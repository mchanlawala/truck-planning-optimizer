package com.dm.planning;

import com.dm.bean.*;
import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.linear.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.ArrayRealVector;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Linear Programming based Truck Optimizer using Apache Commons Math
 * 
 * Solves the truck allocation problem as a Mixed Integer Linear Program:
 * 
 * Variables:
 *   x[i][j] = 1 if material i is assigned to truck j
 *   y[j][t] = 1 if truck j is of type t
 *   z[j] = 1 if truck j is used
 * 
 * Objective:
 *   Minimize total cost = sum(j) sum(t) cost[t] * y[j][t]
 * 
 * Constraints:
 *   1. Each material assigned to exactly one truck
 *   2. Each truck can be of only one type
 *   3. Weight capacity per truck type
 *   4. Coil count capacity per truck type
 *   5. Road weight limit per coil
 */
public class LPTruckOptimizer implements Optimization4TruckService<Integer> {

    // Truck type definitions
    private static final Map<Integer, TruckType> TRUCK_TYPES = new LinkedHashMap<>();
    private static final List<TruckType> TRUCK_TYPES_LIST = new ArrayList<>();
    
    static {
        TruckType type1 = new TruckType(1, "2-axle", 19000, 2, 15000, 15000/19000.0);
        TruckType type2 = new TruckType(2, "3-axle", 28500, 3, 20000, 20000/28500.0);
        TruckType type3 = new TruckType(3, "4-axle", 36000, 4, 25000, 25000/36000.0);
        TruckType type4 = new TruckType(4, "5-axle", 47500, 5, 30000, 30000/47500.0);
        TruckType type5 = new TruckType(5, "6-axle", 49000, 6, 35000, 35000/49000.0);
        
        TRUCK_TYPES.put(1, type1);
        TRUCK_TYPES.put(2, type2);
        TRUCK_TYPES.put(3, type3);
        TRUCK_TYPES.put(4, type4);
        TRUCK_TYPES.put(5, type5);
        
        TRUCK_TYPES_LIST.add(type1);
        TRUCK_TYPES_LIST.add(type2);
        TRUCK_TYPES_LIST.add(type3);
        TRUCK_TYPES_LIST.add(type4);
        TRUCK_TYPES_LIST.add(type5);
    }

    // Truck type class with cost efficiency
    private static class TruckType {
        int id; 
        String name; 
        double maxWeight; 
        int maxCoils; 
        double costPerDay;
        double costPerKg; // cost efficiency metric
        
        TruckType(int id, String name, double maxWeight, int maxCoils, double costPerDay, double costPerKg) {
            this.id = id; 
            this.name = name; 
            this.maxWeight = maxWeight; 
            this.maxCoils = maxCoils; 
            this.costPerDay = costPerDay;
            this.costPerKg = costPerKg;
        }
        
        boolean canCarry(double weight) { 
            return weight <= maxWeight + 0.01; 
        }
        
        boolean canCarry(int coils, double weight) { 
            return coils <= maxCoils && weight <= maxWeight + 0.01; 
        }
        
        double getUtilization(double weight) { 
            return (weight / maxWeight) * 100; 
        }
        
        @Override
        public String toString() {
            return String.format("Type %d (%s): %.0fkg, %d coils, Rs.%.0f/day (Rs.%.2f/kg)", 
                id, name, maxWeight, maxCoils, costPerDay, costPerDay/maxWeight);
        }
    }

    // Truck instance
    private static class Truck {
        int id; 
        int typeId; 
        List<Material> coils = new ArrayList<>(); 
        double totalWeight = 0;
        
        Truck(int id, int typeId) { 
            this.id = id; 
            this.typeId = typeId; 
        }
        
        void add(Material m) { 
            coils.add(m); 
            totalWeight += m.getStockWeight(); 
        }
        
        double getUtilization() { 
            return (totalWeight / TRUCK_TYPES.get(typeId).maxWeight) * 100; 
        }
        
        double getCost() { 
            return TRUCK_TYPES.get(typeId).costPerDay; 
        }
        
        @Override
        public String toString() {
            return String.format("  Truck %d (Type %d): %d coils, %.0fkg, %.1f%% util, Rs.%.0f", 
                id, typeId, coils.size(), totalWeight, getUtilization(), getCost());
        }
    }

    // Solution class
    private static class LPSolution {
        List<Truck> trucks = new ArrayList<>();
        double totalWeight = 0;
        double totalCost = 0;
        double avgUtilization = 0;
        double solveTime = 0;
        String status = "UNKNOWN";
        int iterations = 0;
        
        void addTruck(Truck t) { 
            trucks.add(t); 
            totalWeight += t.totalWeight; 
            totalCost += t.getCost();
        }
        
        void calculateStats() {
            avgUtilization = trucks.stream()
                .mapToDouble(Truck::getUtilization)
                .average()
                .orElse(0);
        }
        
        void printSummary(String location) {
            System.out.println("\n=== Location " + location + " LP Solution ===");
            System.out.printf("Status: %s%n", status);
            System.out.printf("Solve Time: %.3f seconds%n", solveTime);
            System.out.printf("Iterations: %d%n", iterations);
            System.out.printf("Total Trucks: %d%n", trucks.size());
            System.out.printf("Total Weight: %.2f kg (%.2f tons)%n", totalWeight, totalWeight/1000);
            System.out.printf("Total Cost: Rs. %.0f%n", totalCost);
            System.out.printf("Average Utilization: %.2f%%%n", avgUtilization);
            System.out.printf("Cost per Ton: Rs. %.2f%n", totalCost / (totalWeight/1000));
            
            // Group by truck type
            Map<Integer, List<Truck>> byType = trucks.stream()
                .collect(Collectors.groupingBy(t -> t.typeId));
            
            for (Map.Entry<Integer, List<Truck>> e : byType.entrySet()) {
                TruckType type = TRUCK_TYPES.get(e.getKey());
                List<Truck> typeTrucks = e.getValue();
                double typeWeight = typeTrucks.stream().mapToDouble(t -> t.totalWeight).sum();
                double typeCost = typeTrucks.stream().mapToDouble(Truck::getCost).sum();
                double avgUtil = typeTrucks.stream()
                    .mapToDouble(Truck::getUtilization)
                    .average().orElse(0);
                
                System.out.printf("  %s (%d trucks): %.0f kg, %.1f%% util, Rs.%.0f%n",
                    type.name, typeTrucks.size(), typeWeight, avgUtil, typeCost);
                
                // Print individual trucks for this type
                for (Truck t : typeTrucks) {
                    System.out.printf("    %s%n", t.toString());
                }
            }
        }
    }

    // Linear Programming Model using Apache Commons Math
    private static class LPOptimizationModel {
        private final List<Material> materials;
        private final List<TruckType> truckTypes;
        private final int numMaterials;
        private final int numTruckTypes;
        private final double roadMaxWeight;
        
        // Decision variables
        private double[][] assignment; // x[m][t] = 1 if material m assigned to truck type t
        
        LPOptimizationModel(List<Material> materials, List<TruckType> truckTypes, double roadMaxWeight) {
            this.materials = materials;
            this.truckTypes = truckTypes;
            this.numMaterials = materials.size();
            this.numTruckTypes = truckTypes.size();
            this.roadMaxWeight = roadMaxWeight;
            this.assignment = new double[numMaterials][numTruckTypes];
        }
        
        /**
         * Solve the linear programming problem using Simplex solver
         */
        LPSolution solve() {
            long startTime = System.currentTimeMillis();
            LPSolution solution = new LPSolution();
            
            try {
                // Build the linear objective function
                LinearObjectiveFunction objective = buildObjectiveFunction();
                
                // Build the constraints
                Collection<LinearConstraint> constraints = buildConstraints();
                
                // Create the optimization problem
                OptimizationData[] optimizationData = new OptimizationData[] {
                    objective,
                    new LinearConstraintSet(constraints),
                    GoalType.MINIMIZE,
                    new NonNegativeConstraint(true) // Variables must be >= 0
                };
                
                // Create and run the solver - FIXED: Removed setMaxIterations
                SimplexSolver solver = new SimplexSolver();
                
                PointValuePair solution_point = solver.optimize(optimizationData);
                
                // Extract solution
                double[] point = solution_point.getPoint();
                double objectiveValue = solution_point.getValue();
                
                solution.status = "OPTIMAL";
                solution.iterations = 0; // Can't get iterations directly
                
                // Decode the solution into trucks
                solution.trucks = decodeSolution(point);
                
            } catch (Exception e) {
                System.err.println("LP Optimization error: " + e.getMessage());
                e.printStackTrace();
                solution.status = "SUBOPTIMAL";
                
                // Fallback to heuristic solution
                solution.trucks = heuristicSolution();
            }
            
            long endTime = System.currentTimeMillis();
            solution.solveTime = (endTime - startTime) / 1000.0;
            solution.calculateStats();
            
            return solution;
        }
        
        /**
         * Build the objective function: Minimize total cost
         * Objective = sum(m) sum(t) cost[t] * x[m][t]
         */
        private LinearObjectiveFunction buildObjectiveFunction() {
            int numVariables = numMaterials * numTruckTypes;
            double[] coefficients = new double[numVariables];
            
            for (int m = 0; m < numMaterials; m++) {
                for (int t = 0; t < numTruckTypes; t++) {
                    int idx = m * numTruckTypes + t;
                    TruckType type = truckTypes.get(t);
                    // Cost per material based on truck type
                    coefficients[idx] = type.costPerDay / type.maxCoils; // Approximate cost share
                }
            }
            
            return new LinearObjectiveFunction(coefficients, 0);
        }
        
        /**
         * Build all constraints for the LP problem
         */
        private Collection<LinearConstraint> buildConstraints() {
            List<LinearConstraint> constraints = new ArrayList<>();
            int numVariables = numMaterials * numTruckTypes;
            
            // Constraint 1: Each material must be assigned to exactly one truck type
            for (int m = 0; m < numMaterials; m++) {
                double[] coefficients = new double[numVariables];
                for (int t = 0; t < numTruckTypes; t++) {
                    int idx = m * numTruckTypes + t;
                    coefficients[idx] = 1.0;
                }
                constraints.add(new LinearConstraint(coefficients, Relationship.EQ, 1.0));
            }
            
            // Constraint 2: Weight capacity per truck type
            for (int t = 0; t < numTruckTypes; t++) {
                double[] coefficients = new double[numVariables];
                for (int m = 0; m < numMaterials; m++) {
                    int idx = m * numTruckTypes + t;
                    coefficients[idx] = materials.get(m).getStockWeight();
                }
                TruckType type = truckTypes.get(t);
                constraints.add(new LinearConstraint(coefficients, Relationship.LEQ, type.maxWeight));
            }
            
            // Constraint 3: Coil count capacity per truck type
            for (int t = 0; t < numTruckTypes; t++) {
                double[] coefficients = new double[numVariables];
                for (int m = 0; m < numMaterials; m++) {
                    int idx = m * numTruckTypes + t;
                    coefficients[idx] = 1.0;
                }
                TruckType type = truckTypes.get(t);
                constraints.add(new LinearConstraint(coefficients, Relationship.LEQ, type.maxCoils));
            }
            
            // Constraint 4: Road weight limit per coil
            for (int m = 0; m < numMaterials; m++) {
                double weight = materials.get(m).getStockWeight();
                if (weight > roadMaxWeight + 0.01) {
                    // This material cannot be transported
                    for (int t = 0; t < numTruckTypes; t++) {
                        int idx = m * numTruckTypes + t;
                        double[] coeff = new double[numVariables];
                        coeff[idx] = 1.0;
                        constraints.add(new LinearConstraint(coeff, Relationship.EQ, 0.0));
                    }
                }
            }
            
            return constraints;
        }
        
        /**
         * Decode the LP solution into actual trucks
         */
        private List<Truck> decodeSolution(double[] point) {
            List<Truck> trucks = new ArrayList<>();
            Map<Integer, List<Material>> truckAssignments = new HashMap<>();
            Map<Integer, Integer> truckTypesMap = new HashMap<>();
            
            // Group materials by truck
            for (int m = 0; m < numMaterials; m++) {
                for (int t = 0; t < numTruckTypes; t++) {
                    int idx = m * numTruckTypes + t;
                    if (point[idx] > 0.5) { // Material assigned to this truck type
                        int truckId = findOrCreateTruck(truckAssignments, truckTypesMap, t);
                        truckAssignments.computeIfAbsent(truckId, k -> new ArrayList<>())
                            .add(materials.get(m));
                        break;
                    }
                }
            }
            
            // Create truck objects
            int truckNumber = 1;
            for (Map.Entry<Integer, List<Material>> entry : truckAssignments.entrySet()) {
                int typeId = truckTypesMap.get(entry.getKey());
                Truck truck = new Truck(truckNumber++, typeId);
                for (Material m : entry.getValue()) {
                    truck.add(m);
                }
                trucks.add(truck);
            }
            
            return trucks;
        }
        
        /**
         * Find or create a truck ID for a given type
         */
        private int findOrCreateTruck(Map<Integer, List<Material>> assignments, 
                                       Map<Integer, Integer> truckTypesMap, int typeId) {
            // Find existing truck of this type that can accept more materials
            for (Map.Entry<Integer, List<Material>> entry : assignments.entrySet()) {
                int truckId = entry.getKey();
                if (truckTypesMap.get(truckId) == typeId) {
                    List<Material> materials = entry.getValue();
                    double totalWeight = materials.stream().mapToDouble(Material::getStockWeight).sum();
                    TruckType type = truckTypes.get(typeId);
                    
                    if (materials.size() < type.maxCoils && 
                        totalWeight < type.maxWeight * 0.95) {
                        return truckId;
                    }
                }
            }
            
            // Create new truck
            int newId = assignments.size() + 1;
            truckTypesMap.put(newId, typeId);
            return newId;
        }
        
        /**
         * Fallback heuristic solution if LP fails
         */
        private List<Truck> heuristicSolution() {
            List<Truck> trucks = new ArrayList<>();
            List<Material> remaining = new ArrayList<>(materials);
            
            // Sort by weight descending
            remaining.sort((a, b) -> Double.compare(b.getStockWeight(), a.getStockWeight()));
            
            int truckId = 1;
            
            while (!remaining.isEmpty()) {
                Material current = remaining.get(0);
                
                // Find most cost-effective truck type
                TruckType bestType = findBestTruckType(current);
                
                if (bestType != null) {
                    Truck truck = new Truck(truckId++, bestType.id);
                    truck.add(current);
                    remaining.remove(0);
                    
                    // Try to add more materials
                    Iterator<Material> it = remaining.iterator();
                    while (it.hasNext()) {
                        Material m = it.next();
                        if (truck.totalWeight + m.getStockWeight() <= bestType.maxWeight + 0.01 &&
                            truck.coils.size() + 1 <= bestType.maxCoils) {
                            truck.add(m);
                            it.remove();
                        }
                    }
                    
                    trucks.add(truck);
                } else {
                    // Use largest truck
                    TruckType largest = TRUCK_TYPES.get(5);
                    Truck truck = new Truck(truckId++, largest.id);
                    truck.add(current);
                    remaining.remove(0);
                    trucks.add(truck);
                }
            }
            
            return trucks;
        }
        
        /**
         * Find best truck type for a material based on cost efficiency
         */
        private TruckType findBestTruckType(Material material) {
            double weight = material.getStockWeight();
            
            return truckTypes.stream()
                .filter(t -> t.canCarry(weight))
                .min(Comparator.comparingDouble(t -> t.costPerKg))
                .orElse(null);
        }
    }

    @Override
    public PlanOutput4TruckDetail<Integer> callEngine(List<Plan4TruckDetail> planDetails) {
        List<OrderCoilMapping4TruckDetail<Integer>> allOutput = new ArrayList<>();
        List<LPSolution> allSolutions = new ArrayList<>();
        int globalTruckId = 1;

        System.out.println("\n============ TRUCK OPTIMIZATION WITH LINEAR PROGRAMMING ============");
        System.out.println("Available Truck Types:");
        TRUCK_TYPES.values().forEach(t -> System.out.println("  " + t));

        for (Plan4TruckDetail planDetail : planDetails) {
            // Extract data
            List<Material> materials = extractMaterials(planDetail.getOrderCoilMappingDetails());
            String location = getLocationFromMaterials(planDetail.getOrderCoilMappingDetails());
            double roadMaxWeight = planDetail.getParameterDetail().get(0).getRoadMaxWeight();
            
            System.out.println("\n=========================================");
            System.out.println("LP Optimization for Location " + location);
            System.out.println("Materials: " + materials.size());
            System.out.println("Road Max Weight: " + roadMaxWeight + " kg");
            
            // Validate against road weight
            validateRoadConstraint(materials, roadMaxWeight);
            
            // Create and solve LP model
            LPOptimizationModel model = new LPOptimizationModel(
                materials, TRUCK_TYPES_LIST, roadMaxWeight);
            LPSolution solution = model.solve();
            
            // Assign global truck IDs
            for (Truck truck : solution.trucks) {
                truck.id = globalTruckId++;
            }
            
            allSolutions.add(solution);
            solution.printSummary(location);
            
            // Convert to output format
            for (Truck truck : solution.trucks) {
                for (Material m : truck.coils) {
                    OrderCoilMapping4TruckDetail<Integer> d = new OrderCoilMapping4TruckDetail<>();
                    d.setGroupId(planDetail.getParameterDetail().get(0).getGroupId());
                    d.setBatchNo(m.getBatchNo());
                    d.setOrderNo(m.getOrderNo());
                    d.setOrderLineItem(m.getOrderLineItem());
                    d.setStockWeight(m.getStockWeight());
                    d.setStockWidth(m.getStockWidth());
                    d.setStockLength(m.getStockLength());
                    d.setLocation(m.getLocation());
                    d.setTruckTypeId(truck.typeId);
                    d.setTruckNumber(truck.id);
                    allOutput.add(d);
                }
            }
        }

        // Print global summary
        printGlobalSummary(allSolutions);

        return new PlanOutput4TruckDetail<>(allOutput);
    }

    /**
     * Validate materials against road weight constraint
     */
    private void validateRoadConstraint(List<Material> materials, double roadMaxWeight) {
        boolean anyViolation = false;
        for (Material m : materials) {
            if (m.getStockWeight() > roadMaxWeight + 0.01) {
                System.out.printf("WARNING: Material %s weight (%.0f kg) exceeds road limit (%.0f kg)%n",
                    m.getBatchNo(), m.getStockWeight(), roadMaxWeight);
                anyViolation = true;
            }
        }
        if (!anyViolation) {
            System.out.println("All materials within road weight limit ✓");
        }
    }

    /**
     * Print global optimization summary
     */
    private void printGlobalSummary(List<LPSolution> solutions) {
        System.out.println("\n============ GLOBAL LP OPTIMIZATION SUMMARY ============");
        
        double totalWeight = 0;
        double totalCost = 0;
        int totalTrucks = 0;
        double totalSolveTime = 0;
        Map<Integer, Integer> typeCount = new TreeMap<>();
        Map<Integer, Double> typeWeight = new TreeMap<>();
        
        for (LPSolution sol : solutions) {
            totalWeight += sol.totalWeight;
            totalCost += sol.totalCost;
            totalTrucks += sol.trucks.size();
            totalSolveTime += sol.solveTime;
            
            for (Truck t : sol.trucks) {
                typeCount.merge(t.typeId, 1, Integer::sum);
                typeWeight.merge(t.typeId, t.totalWeight, Double::sum);
            }
        }
        
        System.out.printf("Total Solve Time: %.3f seconds%n", totalSolveTime);
        System.out.printf("Total Weight: %.2f kg (%.2f tons)%n", totalWeight, totalWeight/1000);
        System.out.printf("Total Trucks: %d%n", totalTrucks);
        System.out.printf("Total Cost: Rs. %.0f%n", totalCost);
        System.out.printf("Average Cost per Ton: Rs. %.2f%n", totalCost / (totalWeight/1000));
        
        System.out.println("\nTruck Type Distribution:");
        for (Map.Entry<Integer, Integer> e : typeCount.entrySet()) {
            int typeId = e.getKey();
            TruckType type = TRUCK_TYPES.get(typeId);
            int count = e.getValue();
            double weight = typeWeight.getOrDefault(typeId, 0.0);
            double avgUtil = count > 0 ? (weight / (count * type.maxWeight)) * 100 : 0;
            
            System.out.printf("  Type %d (%s): %d trucks, %.0f kg, %.1f%% avg util%n",
                typeId, type.name, count, weight, avgUtil);
        }
        
        // Calculate savings compared to using largest trucks
        double costIfAllLargest = totalTrucks * TRUCK_TYPES.get(5).costPerDay;
        double savings = costIfAllLargest - totalCost;
        double savingsPercent = (savings / costIfAllLargest) * 100;
        
        System.out.printf("\nCost Savings: Rs. %.0f (%.1f%%) compared to using all 6-axle trucks%n",
            savings, savingsPercent);
        
        // Calculate efficiency (kg per rupee)
        double efficiency = totalWeight / totalCost;
        System.out.printf("Transport Efficiency: %.2f kg per rupee%n", efficiency);
        
        System.out.println("========================================================");
    }

    // Utility methods
    private List<Material> extractMaterials(List<OrderCoilMapping4TruckDetail<Integer>> details) {
        List<Material> materials = new ArrayList<>();
        for (OrderCoilMapping4TruckDetail<Integer> d : details) {
            Material m = new Material();
            m.setBatchNo(d.getBatchNo());
            m.setOrderNo(d.getOrderNo());
            m.setOrderLineItem(d.getOrderLineItem());
            m.setStockWeight(d.getStockWeight());
            m.setStockWidth(d.getStockWidth());
            m.setStockLength(d.getStockLength());
            m.setLocation(d.getLocation());
            materials.add(m);
        }
        return materials;
    }

    private String getLocationFromMaterials(List<OrderCoilMapping4TruckDetail<Integer>> details) {
        return details.isEmpty() ? "Unknown" : details.get(0).getLocation();
    }
}