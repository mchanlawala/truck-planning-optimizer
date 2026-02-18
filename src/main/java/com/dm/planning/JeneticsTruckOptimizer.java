package com.dm.planning;

import com.dm.bean.*;
import io.jenetics.*;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.engine.Limits;
import io.jenetics.util.Factory;
import io.jenetics.util.ISeq;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Jenetics-based Genetic Algorithm for Truck Planning Optimization
 * 
 * Chromosome Structure: Each gene represents a truck assignment for a material
 * - IntegerGene: 0-99 (truck group ID)
 * - Each material assigned to a truck group
 * - Separate chromosome for truck type per group
 */
public class JeneticsTruckOptimizer implements Optimization4TruckService<Integer> {

    // Truck type definitions
    private static final Map<Integer, TruckType> TRUCK_TYPES = new LinkedHashMap<>();
    
    static {
        TRUCK_TYPES.put(1, new TruckType(1, "2-axle", 19000, 2, 15000));
        TRUCK_TYPES.put(2, new TruckType(2, "3-axle", 28500, 3, 20000));
        TRUCK_TYPES.put(3, new TruckType(3, "4-axle", 36000, 4, 25000));
        TRUCK_TYPES.put(4, new TruckType(4, "5-axle", 47500, 5, 30000));
        TRUCK_TYPES.put(5, new TruckType(5, "6-axle", 49000, 6, 35000));
    }

    // Truck type class
    private static class TruckType {
        int id; String name; double maxWeight; int maxCoils; double costPerDay; double costPerKg;
        TruckType(int id, String name, double maxWeight, int maxCoils, double costPerDay) {
            this.id = id; this.name = name; this.maxWeight = maxWeight; 
            this.maxCoils = maxCoils; this.costPerDay = costPerDay;
            this.costPerKg = costPerDay / maxWeight;
        }
        boolean canCarry(double weight) { return weight <= maxWeight + 0.01; }
        boolean canCarry(int coils, double weight) { return coils <= maxCoils && weight <= maxWeight + 0.01; }
        double getUtilization(double weight) { return (weight / maxWeight) * 100; }
    }

    // Truck instance
    private static class Truck {
        int id; int typeId; List<Material> coils = new ArrayList<>(); double totalWeight = 0;
        Truck(int id, int typeId) { this.id = id; this.typeId = typeId; }
        void add(Material m) { coils.add(m); totalWeight += m.getStockWeight(); }
        double getUtilization() { return (totalWeight / TRUCK_TYPES.get(typeId).maxWeight) * 100; }
        double getCost() { return TRUCK_TYPES.get(typeId).costPerDay; }
    }

    // Solution class
    public static class TruckSolution {
        List<Truck> trucks = new ArrayList<>();
        double totalCost; double totalWeight; double avgUtilization;
        
        void addTruck(Truck t) { 
            trucks.add(t); 
            totalWeight += t.totalWeight; 
            totalCost += t.getCost();
        }
        
        void calculateStats() {
            avgUtilization = trucks.stream().mapToDouble(Truck::getUtilization).average().orElse(0);
        }
    }

    // Problem data
    private List<Material> materials;
    private List<TruckType> truckTypes;
    private double roadMaxWeight;

    @Override
    public PlanOutput4TruckDetail<Integer> callEngine(List<Plan4TruckDetail> planDetails) {
        List<OrderCoilMapping4TruckDetail<Integer>> allOutputDetails = new ArrayList<>();
        List<TruckSolution> allSolutions = new ArrayList<>();
        int globalTruckId = 1;

        for (Plan4TruckDetail planDetail : planDetails) {
            // Initialize problem data
            this.materials = extractMaterials(planDetail.getOrderCoilMappingDetails());
            this.truckTypes = new ArrayList<>(TRUCK_TYPES.values());
            this.roadMaxWeight = planDetail.getParameterDetail().get(0).getRoadMaxWeight();

            System.out.println("\n=== Optimizing Location " + 
                getLocationFromMaterials(planDetail.getOrderCoilMappingDetails()) + " ===");
            System.out.println("Materials: " + materials.size());

            // Run Jenetics optimization
            TruckSolution solution = optimizeWithJenetics();
            
            // Assign global truck IDs
            for (Truck truck : solution.trucks) {
                truck.id = globalTruckId++;
            }
            
            allSolutions.add(solution);

            // Convert to output format
            for (Truck truck : solution.trucks) {
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
                    detail.setTruckTypeId(truck.typeId);
                    detail.setTruckNumber(truck.id);
                    allOutputDetails.add(detail);
                }
            }
        }

        // Print global summary
        printGlobalSummary(allSolutions);

        return new PlanOutput4TruckDetail<>(allOutputDetails);
    }

    /**
     * Jenetics Genetic Algorithm Optimization
     */
    private TruckSolution optimizeWithJenetics() {
        int materialCount = materials.size();
        int maxTrucks = materialCount; // Upper bound
        
        // Create genotype factory: each gene is truck assignment (0 to maxTrucks-1)
        Factory<Genotype<IntegerGene>> gtf = Genotype.of(
            IntegerChromosome.of(0, maxTrucks - 1, materialCount)
        );

        // Configure the genetic algorithm engine
        Engine<IntegerGene, Double> engine = Engine
            .builder(this::fitness, gtf)
            .populationSize(500)
            .optimize(Optimize.MAXIMUM)
            .alterers(
                new Mutator<>(0.15),
                new SinglePointCrossover<>(0.7)
            )
            .selector(new TournamentSelector<>(5))
            .build();

        // Evolution statistics
        EvolutionStatistics<Double, ?> statistics = EvolutionStatistics.ofNumber();

        // Run evolution
        Phenotype<IntegerGene, Double> best = engine.stream()
            .limit(Limits.bySteadyFitness(50))
            .limit(500)
            .peek(statistics)
            .collect(EvolutionResult.toBestPhenotype());

        System.out.println("\nJenetics Evolution Statistics:");
        System.out.println(statistics);

        // Decode best solution
        TruckSolution solution = decodeToTrucks(best.genotype());
        solution.calculateStats();

        // Print solution summary
        printSolutionSummary(solution);

        return solution;
    }

    /**
     * Fitness function: Maximize weight utilization, minimize cost
     */
    private double fitness(Genotype<IntegerGene> gt) {
        Map<Integer, List<Integer>> truckGroups = new HashMap<>();
        Map<Integer, Set<Integer>> truckTypes = new HashMap<>();
        
        // Group materials by truck ID
        for (int i = 0; i < materials.size(); i++) {
            int truckId = gt.get(0).get(i).intValue();
            truckGroups.computeIfAbsent(truckId, k -> new ArrayList<>()).add(i);
        }

        double totalCost = 0;
        double totalUtilization = 0;
        int validTrucks = 0;
        double penalty = 0;

        // Evaluate each truck group
        for (Map.Entry<Integer, List<Integer>> entry : truckGroups.entrySet()) {
            List<Integer> indices = entry.getValue();
            
            // Calculate total weight for this truck
            double truckWeight = indices.stream()
                .mapToDouble(i -> materials.get(i).getStockWeight())
                .sum();
            int coilCount = indices.size();

            // Find best truck type for this load
            TruckType bestType = findBestTruckType(truckWeight, coilCount);
            
            if (bestType != null) {
                totalCost += bestType.costPerDay;
                totalUtilization += (truckWeight / bestType.maxWeight);
                validTrucks++;
            } else {
                // Penalty for impossible loads
                penalty += 10000 * (truckWeight / 1000);
            }
        }

        // Avoid division by zero
        if (validTrucks == 0) return -penalty;

        // Calculate fitness components
        double avgUtilization = (totalUtilization / validTrucks) * 100;
        double avgCostPerTon = totalCost / (totalUtilization * 1000); // Cost per ton
        
        // Normalized scores (0-1)
        double utilizationScore = avgUtilization / 100;
        double costScore = 1 - (avgCostPerTon / 1000); // Lower cost is better
        double truckCountScore = 1 - (validTrucks / (double) materials.size());
        
        // Weighted fitness (maximize)
        double fitness = 0.4 * utilizationScore + 0.4 * costScore + 0.2 * truckCountScore;
        
        // Apply penalty
        fitness -= penalty / 1000000;

        return fitness;
    }

    /**
     * Find best truck type for a given load (Dynamic Programming style selection)
     */
    private TruckType findBestTruckType(double weight, int coilCount) {
        List<TruckType> suitable = truckTypes.stream()
            .filter(t -> t.canCarry(coilCount, weight))
            .collect(Collectors.toList());

        if (suitable.isEmpty()) return null;

        // Score each truck type
        return suitable.stream()
            .max((t1, t2) -> {
                double score1 = scoreTruckType(t1, weight, coilCount);
                double score2 = scoreTruckType(t2, weight, coilCount);
                return Double.compare(score1, score2);
            })
            .orElse(suitable.get(0));
    }

    /**
     * Score a truck type for a specific load
     */
    private double scoreTruckType(TruckType type, double weight, int coilCount) {
        // Utilization score (higher is better)
        double utilizationScore = weight / type.maxWeight;
        
        // Cost efficiency score (lower cost per kg is better)
        double costEfficiency = 1 / type.costPerKg;
        
        // Capacity fit score (closer to max weight is better)
        double fitScore = 1 - (Math.abs(type.maxWeight - weight) / type.maxWeight);
        
        // Special bonus for optimal truck types
        double optimalBonus = 0;
        if (type.id == 3 && weight > 28000 && weight < 32000) {
            optimalBonus = 0.3; // 4-axle bonus for 30,000 kg
        }
        
        // Weighted combination
        return 0.4 * utilizationScore + 0.3 * costEfficiency + 0.2 * fitScore + optimalBonus;
    }

    /**
     * Decode genotype to truck objects
     */
    private TruckSolution decodeToTrucks(Genotype<IntegerGene> gt) {
        Map<Integer, List<Integer>> truckGroups = new HashMap<>();
        
        // Group by truck ID
        for (int i = 0; i < materials.size(); i++) {
            int truckId = gt.get(0).get(i).intValue();
            truckGroups.computeIfAbsent(truckId, k -> new ArrayList<>()).add(i);
        }

        TruckSolution solution = new TruckSolution();
        int truckNumber = 1;

        // Create trucks for each group
        for (Map.Entry<Integer, List<Integer>> entry : truckGroups.entrySet()) {
            List<Integer> indices = entry.getValue();
            
            // Calculate load
            double weight = indices.stream()
                .mapToDouble(i -> materials.get(i).getStockWeight())
                .sum();
            int coilCount = indices.size();

            // Find best truck type
            TruckType bestType = findBestTruckType(weight, coilCount);
            
            if (bestType != null) {
                Truck truck = new Truck(truckNumber++, bestType.id);
                for (int idx : indices) {
                    truck.add(materials.get(idx));
                }
                solution.addTruck(truck);
            }
        }

        return solution;
    }

    /**
     * Print solution summary
     */
    private void printSolutionSummary(TruckSolution solution) {
        System.out.println("\n--- Jenetics Solution Summary ---");
        System.out.printf("Total Trucks: %d%n", solution.trucks.size());
        System.out.printf("Total Weight: %.2f kg (%.2f tons)%n", 
            solution.totalWeight, solution.totalWeight/1000);
        System.out.printf("Total Cost: Rs. %.0f%n", solution.totalCost);
        System.out.printf("Average Utilization: %.2f%%%n", solution.avgUtilization);
        System.out.printf("Cost per Ton: Rs. %.2f%n", 
            solution.totalCost / (solution.totalWeight/1000));
        
        // Group by truck type
        Map<Integer, List<Truck>> byType = solution.trucks.stream()
            .collect(Collectors.groupingBy(t -> t.typeId));
        
        for (Map.Entry<Integer, List<Truck>> entry : byType.entrySet()) {
            int typeId = entry.getKey();
            List<Truck> trucks = entry.getValue();
            TruckType type = TRUCK_TYPES.get(typeId);
            
            double typeWeight = trucks.stream().mapToDouble(t -> t.totalWeight).sum();
            double typeCost = trucks.stream().mapToDouble(Truck::getCost).sum();
            double avgUtil = trucks.stream().mapToDouble(Truck::getUtilization).average().orElse(0);
            
            System.out.printf("  %s (%d trucks): %.0f kg, %.1f%% util, Rs.%.0f%n",
                type.name, trucks.size(), typeWeight, avgUtil, typeCost);
        }
    }

    /**
     * Print global summary
     */
    private void printGlobalSummary(List<TruckSolution> solutions) {
        System.out.println("\n============ GLOBAL OPTIMIZATION SUMMARY ============");
        
        double totalWeight = 0;
        double totalCost = 0;
        int totalTrucks = 0;
        Map<Integer, Integer> typeCount = new TreeMap<>();
        
        for (TruckSolution sol : solutions) {
            totalWeight += sol.totalWeight;
            totalCost += sol.totalCost;
            totalTrucks += sol.trucks.size();
            
            for (Truck t : sol.trucks) {
                typeCount.merge(t.typeId, 1, Integer::sum);
            }
        }
        
        System.out.printf("Total Weight: %.2f kg (%.2f tons)%n", totalWeight, totalWeight/1000);
        System.out.println("Total Trucks: " + totalTrucks);
        System.out.printf("Total Cost: Rs. %.0f%n", totalCost);
        System.out.printf("Avg Cost per Ton: Rs. %.2f%n", totalCost / (totalWeight/1000));
        
        System.out.println("\nTruck Type Distribution:");
        for (Map.Entry<Integer, Integer> entry : typeCount.entrySet()) {
            TruckType type = TRUCK_TYPES.get(entry.getKey());
            System.out.printf("  Type %d (%s): %d trucks%n", 
                entry.getKey(), type.name, entry.getValue());
        }
        System.out.println("=====================================================");
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