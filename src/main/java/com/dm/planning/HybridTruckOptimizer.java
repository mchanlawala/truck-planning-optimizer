package com.dm.planning;

import com.dm.bean.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid Multi-Objective Truck Optimizer
 * 
 * Optimization Goals:
 * 1. Maximize weight utilization per truck (>85% target)
 * 2. Minimize total cost (select most cost-effective axle type)
 * 3. Minimize number of trucks
 * 4. Balance load across truck types
 * 
 * Algorithm: Hybrid (Bin Packing + DP + GA + Local Search)
 */
public class HybridTruckOptimizer implements Optimization4TruckService<Integer> {

    // ==================== CONFIGURATION ====================
    
    // Truck type definitions (from your matrix)
    private static final Map<Integer, TruckType> TRUCK_TYPES = new LinkedHashMap<>();
    
    static {
        TRUCK_TYPES.put(1, new TruckType(1, "2-axle", 19000, 2, 15000));
        TRUCK_TYPES.put(2, new TruckType(2, "3-axle", 28500, 3, 20000));
        TRUCK_TYPES.put(3, new TruckType(3, "4-axle", 36000, 4, 25000)); // Optimal for 30,000 kg
        TRUCK_TYPES.put(4, new TruckType(4, "5-axle", 47500, 5, 30000));
        TRUCK_TYPES.put(5, new TruckType(5, "6-axle", 49000, 6, 35000));
    }
    
    // Optimization parameters
    private static final double TARGET_UTILIZATION = 85.0; // Target utilization percentage
    private static final double MAX_ACCEPTABLE_COST_PER_TON = 1000.0; // Max Rs. per ton
    private static final int POPULATION_SIZE = 200;
    private static final int MAX_GENERATIONS = 100;
    private static final double MUTATION_RATE = 0.1;
    private static final double CROSSOVER_RATE = 0.8;
    
    // ==================== DATA STRUCTURES ====================
    
    /**
     * Truck type definition with cost analysis
     */
    private static class TruckType {
        int id;
        String name;
        double maxWeight;
        int maxCoils;
        double costPerDay;
        double costPerKg; // Calculated
        
        TruckType(int id, String name, double maxWeight, int maxCoils, double costPerDay) {
            this.id = id;
            this.name = name;
            this.maxWeight = maxWeight;
            this.maxCoils = maxCoils;
            this.costPerDay = costPerDay;
            this.costPerKg = costPerDay / maxWeight;
        }
        
        double getUtilization(double weight) {
            return (weight / maxWeight) * 100;
        }
        
        boolean canCarry(double weight) {
            return weight <= maxWeight + 0.01;
        }
        
        boolean canCarry(List<Material> materials) {
            double totalWeight = materials.stream().mapToDouble(Material::getStockWeight).sum();
            return totalWeight <= maxWeight + 0.01 && materials.size() <= maxCoils;
        }
        
        @Override
        public String toString() {
            return String.format("Type %d (%s): %.0f kg, %d coils, Rs.%.0f/day (Rs.%.4f/kg)", 
                    id, name, maxWeight, maxCoils, costPerDay, costPerKg);
        }
    }
    
    /**
     * Truck instance with loaded materials
     */
    private static class Truck {
        int id;
        TruckType type;
        List<Material> coils = new ArrayList<>();
        double totalWeight = 0;
        
        Truck(int id, TruckType type) {
            this.id = id;
            this.type = type;
        }
        
        boolean canAdd(Material material) {
            return totalWeight + material.getStockWeight() <= type.maxWeight + 0.01 &&
                   coils.size() + 1 <= type.maxCoils;
        }
        
        void add(Material material) {
            coils.add(material);
            totalWeight += material.getStockWeight();
        }
        
        double getUtilization() {
            return (totalWeight / type.maxWeight) * 100;
        }
        
        double getCostPerTon() {
            return type.costPerDay / (totalWeight / 1000);
        }
        
        boolean isOptimal() {
            return getUtilization() >= TARGET_UTILIZATION;
        }
        
        boolean canAcceptMore() {
            return coils.size() < type.maxCoils && totalWeight < type.maxWeight * 0.95;
        }
        
        @Override
        public String toString() {
            return String.format("Truck %d (Type %d): %d coils, %.0f kg, %.1f%% util, Rs.%.2f/ton", 
                    id, type.id, coils.size(), totalWeight, getUtilization(), getCostPerTon());
        }
    }
    
    /**
     * Location plan with all trucks
     */
    private static class LocationPlan {
        String location;
        List<Truck> trucks = new ArrayList<>();
        double totalWeight = 0;
        double totalCost = 0;
        Map<Integer, Integer> truckTypeCount = new HashMap<>();
        Map<Integer, Double> truckTypeWeight = new HashMap<>();
        
        void addTruck(Truck truck) {
            trucks.add(truck);
            totalWeight += truck.totalWeight;
            totalCost += truck.type.costPerDay;
            truckTypeCount.merge(truck.type.id, 1, Integer::sum);
            truckTypeWeight.merge(truck.type.id, truck.totalWeight, Double::sum);
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
            System.out.printf("Total Weight: %.2f kg (%.2f tons)%n", totalWeight, totalWeight/1000);
            System.out.println("Total Trucks: " + trucks.size());
            System.out.printf("Total Cost: Rs. %.0f%n", totalCost);
            System.out.printf("Average Utilization: %.2f%%%n", getAverageUtilization());
            System.out.printf("Cost per Ton: Rs. %.2f%n", getCostPerTon());
            
            // Print by truck type
            System.out.println("\n  Truck Type Distribution:");
            for (Map.Entry<Integer, Integer> entry : truckTypeCount.entrySet()) {
                int typeId = entry.getKey();
                TruckType type = TRUCK_TYPES.get(typeId);
                int count = entry.getValue();
                double weight = truckTypeWeight.getOrDefault(typeId, 0.0);
                double avgUtil = count > 0 ? (weight / (count * type.maxWeight)) * 100 : 0;
                
                System.out.printf("    %s (%d trucks): %.0f kg, %.1f%% avg util, Rs.%.0f total%n",
                        type.name, count, weight, avgUtil, count * type.costPerDay);
            }
        }
    }
    
    // ==================== MAIN OPTIMIZATION ENGINE ====================

    @Override
    public PlanOutput4TruckDetail<Integer> callEngine(List<Plan4TruckDetail> planDetails) {
        List<OrderCoilMapping4TruckDetail<Integer>> allOutputDetails = new ArrayList<>();
        List<LocationPlan> allPlans = new ArrayList<>();
        int nextTruckId = 1;
        
        System.out.println("\n============ TRUCK OPTIMIZATION ENGINE ============");
        System.out.println("Available Truck Types:");
        TRUCK_TYPES.values().forEach(t -> System.out.println("  " + t));
        
        for (Plan4TruckDetail planDetail : planDetails) {
            LocationPlan plan = optimizeLocation(planDetail);
            
            // Assign sequential truck IDs
            for (Truck truck : plan.trucks) {
                truck.id = nextTruckId++;
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
                    detail.setTruckTypeId(truck.type.id);
                    detail.setTruckNumber(truck.id);
                    allOutputDetails.add(detail);
                }
            }
        }
        
        // Print global optimization results
        printGlobalOptimizationSummary(allPlans);
        
        return new PlanOutput4TruckDetail<>(allOutputDetails);
    }

    // ==================== LOCATION OPTIMIZATION ====================

    private LocationPlan optimizeLocation(Plan4TruckDetail planDetail) {
        LocationPlan plan = new LocationPlan();
        
        // Get location and materials
        plan.location = getLocationFromMaterials(planDetail.getOrderCoilMappingDetails());
        List<Material> materials = extractMaterials(planDetail.getOrderCoilMappingDetails());
        
        System.out.println("\n--- Optimizing Location " + plan.location + " ---");
        System.out.println("Total materials: " + materials.size());
        
        // Analyze material distribution
        analyzeMaterialDistribution(materials);
        
        // Step 1: Group materials by optimal truck type using Dynamic Programming
        List<List<Material>> optimalGroups = groupMaterialsByDP(materials);
        System.out.println("Optimal groups formed: " + optimalGroups.size());
        
        // Step 2: Assign trucks using Best-Fit Decreasing algorithm
        List<Truck> initialTrucks = assignTrucksWithBFD(optimalGroups);
        System.out.println("Initial trucks assigned: " + initialTrucks.size());
        
        // Step 3: Apply Genetic Algorithm for global optimization
        List<Truck> optimizedTrucks = optimizeWithGA(initialTrucks, materials);
        
        // Step 4: Apply Local Search for fine-tuning
        optimizedTrucks = localSearchOptimization(optimizedTrucks);
        
        // Add to plan
        for (Truck truck : optimizedTrucks) {
            plan.addTruck(truck);
        }
        
        // Print location summary
        plan.printSummary();
        
        return plan;
    }

    // ==================== STEP 1: MATERIAL ANALYSIS ====================

    private void analyzeMaterialDistribution(List<Material> materials) {
        System.out.println("\n  Material Weight Distribution:");
        
        // Group by weight ranges
        Map<String, Integer> weightRanges = new LinkedHashMap<>();
        weightRanges.put("0-19000 kg (2-axle)", 0);
        weightRanges.put("19001-28500 kg (3-axle)", 0);
        weightRanges.put("28501-36000 kg (4-axle)", 0);
        weightRanges.put("36001-47500 kg (5-axle)", 0);
        weightRanges.put("47501-49000 kg (6-axle)", 0);
        
        for (Material m : materials) {
            double w = m.getStockWeight();
            if (w <= 19000) weightRanges.put("0-19000 kg (2-axle)", weightRanges.get("0-19000 kg (2-axle)") + 1);
            else if (w <= 28500) weightRanges.put("19001-28500 kg (3-axle)", weightRanges.get("19001-28500 kg (3-axle)") + 1);
            else if (w <= 36000) weightRanges.put("28501-36000 kg (4-axle)", weightRanges.get("28501-36000 kg (4-axle)") + 1);
            else if (w <= 47500) weightRanges.put("36001-47500 kg (5-axle)", weightRanges.get("36001-47500 kg (5-axle)") + 1);
            else weightRanges.put("47501-49000 kg (6-axle)", weightRanges.get("47501-49000 kg (6-axle)") + 1);
        }
        
        for (Map.Entry<String, Integer> entry : weightRanges.entrySet()) {
            if (entry.getValue() > 0) {
                System.out.printf("    %s: %d coils%n", entry.getKey(), entry.getValue());
            }
        }
    }

    // ==================== STEP 2: DYNAMIC PROGRAMMING GROUPING ====================

    /**
     * Use Dynamic Programming to find optimal groupings of materials
     * that maximize utilization for each truck type
     */
    private List<List<Material>> groupMaterialsByDP(List<Material> materials) {
        List<List<Material>> optimalGroups = new ArrayList<>();
        List<Material> remaining = new ArrayList<>(materials);
        
        // Sort by weight descending for better packing
        remaining.sort((a, b) -> Double.compare(b.getStockWeight(), a.getStockWeight()));
        
        while (!remaining.isEmpty()) {
            Material first = remaining.get(0);
            
            // Find best truck type for this material
            TruckType bestType = findBestTruckTypeForMaterial(first);
            
            // Find optimal combination of materials for this truck type
            List<Material> bestCombination = findOptimalCombination(remaining, bestType);
            
            if (!bestCombination.isEmpty()) {
                optimalGroups.add(bestCombination);
                remaining.removeAll(bestCombination);
            } else {
                // If no optimal combination found, just take the first material
                optimalGroups.add(Arrays.asList(first));
                remaining.remove(0);
            }
        }
        
        return optimalGroups;
    }

    /**
     * Find the most cost-effective truck type for a given material
     */
    private TruckType findBestTruckTypeForMaterial(Material material) {
        double weight = material.getStockWeight();
        
        // For 30,000 kg, strongly prefer Type 3 (4-axle)
        if (Math.abs(weight - 30000) < 1000) {
            return TRUCK_TYPES.get(3); // 4-axle is optimal
        }
        
        // For other weights, find the smallest truck that can carry it
        TruckType best = null;
        for (TruckType type : TRUCK_TYPES.values()) {
            if (type.canCarry(weight)) {
                if (best == null || type.maxWeight < best.maxWeight) {
                    best = type;
                }
            }
        }
        return best;
    }

    /**
     * DP algorithm to find optimal combination of materials for a truck type
     */
    private List<Material> findOptimalCombination(List<Material> available, TruckType truckType) {
        int n = Math.min(available.size(), 20); // Limit search for performance
        List<Material> candidates = available.subList(0, n);
        
        // DP array: dp[w][c] = maximum value (utilization) achievable with weight w and c coils
        int maxWeight = (int) truckType.maxWeight;
        int maxCoils = truckType.maxCoils;
        
        double[][] dp = new double[maxWeight + 1][maxCoils + 1];
        boolean[][][] selected = new boolean[maxWeight + 1][maxCoils + 1][n];
        
        // Initialize
        for (int w = 0; w <= maxWeight; w++) {
            for (int c = 0; c <= maxCoils; c++) {
                dp[w][c] = -1;
            }
        }
        dp[0][0] = 0;
        
        // DP iteration
        for (int i = 0; i < n; i++) {
            Material m = candidates.get(i);
            int weight = (int) m.getStockWeight();
            
            for (int w = maxWeight; w >= weight; w--) {
                for (int c = maxCoils; c >= 1; c--) {
                    if (dp[w - weight][c - 1] >= 0) {
                        double newUtil = dp[w - weight][c - 1] + m.getStockWeight();
                        if (newUtil > dp[w][c]) {
                            dp[w][c] = newUtil;
                            // Mark selection
                            for (int k = 0; k < n; k++) {
                                selected[w][c][k] = selected[w - weight][c - 1][k];
                            }
                            selected[w][c][i] = true;
                        }
                    }
                }
            }
        }
        
        // Find best combination (maximize utilization)
        double bestUtil = 0;
        int bestW = 0, bestC = 0;
        for (int w = 0; w <= maxWeight; w++) {
            for (int c = 1; c <= maxCoils; c++) {
                if (dp[w][c] > bestUtil) {
                    bestUtil = dp[w][c];
                    bestW = w;
                    bestC = c;
                }
            }
        }
        
        // Reconstruct selected materials
        List<Material> result = new ArrayList<>();
        if (bestW > 0 && bestC > 0) {
            for (int i = 0; i < n; i++) {
                if (selected[bestW][bestC][i]) {
                    result.add(candidates.get(i));
                }
            }
        } else if (!candidates.isEmpty()) {
            // If no combination found, take the first material
            result.add(candidates.get(0));
        }
        
        return result;
    }

    // ==================== STEP 3: BEST-FIT DECREASING TRUCK ASSIGNMENT ====================

    /**
     * Assign trucks to material groups using Best-Fit Decreasing algorithm
     */
    private List<Truck> assignTrucksWithBFD(List<List<Material>> groups) {
        List<Truck> trucks = new ArrayList<>();
        int truckId = 1;
        
        for (List<Material> group : groups) {
            double totalWeight = group.stream().mapToDouble(Material::getStockWeight).sum();
            
            // Find best truck type for this group
            TruckType bestType = findBestTruckTypeForGroup(group, totalWeight);
            
            if (bestType != null) {
                Truck truck = new Truck(truckId++, bestType);
                for (Material m : group) {
                    truck.add(m);
                }
                trucks.add(truck);
            }
        }
        
        return trucks;
    }

    /**
     * Find the most cost-effective truck type for a group of materials
     */
    private TruckType findBestTruckTypeForGroup(List<Material> group, double totalWeight) {
        List<TruckType> suitable = new ArrayList<>();
        
        for (TruckType type : TRUCK_TYPES.values()) {
            if (type.canCarry(group)) {
                suitable.add(type);
            }
        }
        
        if (suitable.isEmpty()) return null;
        
        // Score each suitable truck type
        return suitable.stream()
                .max((t1, t2) -> {
                    double score1 = calculateTruckScore(t1, totalWeight, group.size());
                    double score2 = calculateTruckScore(t2, totalWeight, group.size());
                    return Double.compare(score1, score2);
                })
                .orElse(suitable.get(0));
    }

    /**
     * Calculate score for a truck type (higher is better)
     * Factors: utilization, cost efficiency, capacity fit
     */
    private double calculateTruckScore(TruckType type, double weight, int coilCount) {
        double utilization = weight / type.maxWeight;
        double costEfficiency = type.costPerKg;
        double capacityFit = 1.0 - (Math.abs(type.maxWeight - weight) / type.maxWeight);
        
        // Weights for different factors
        double utilizationWeight = 0.4;
        double costWeight = 0.3;
        double fitWeight = 0.3;
        
        // Special bonus for optimal truck types
        double optimalBonus = 0;
        if (type.id == 3 && weight > 28000 && weight < 32000) {
            optimalBonus = 0.2; // 4-axle bonus for 30,000 kg
        }
        
        return (utilization * utilizationWeight) + 
               ((1 / costEfficiency) * costWeight) + 
               (capacityFit * fitWeight) + 
               optimalBonus;
    }

    // ==================== STEP 4: GENETIC ALGORITHM OPTIMIZATION ====================

    /**
     * Chromosome representation for GA
     */
    private static class Chromosome {
        int[] truckTypeAssignments;  // Truck type for each material group
        double fitness;
        double totalCost;
        double avgUtilization;
        int truckCount;
        
        Chromosome(int numGroups) {
            truckTypeAssignments = new int[numGroups];
        }
        
        Chromosome copy() {
            Chromosome copy = new Chromosome(truckTypeAssignments.length);
            System.arraycopy(this.truckTypeAssignments, 0, copy.truckTypeAssignments, 0, truckTypeAssignments.length);
            copy.fitness = this.fitness;
            copy.totalCost = this.totalCost;
            copy.avgUtilization = this.avgUtilization;
            copy.truckCount = this.truckCount;
            return copy;
        }
    }

    /**
     * Genetic Algorithm for global optimization
     */
    private List<Truck> optimizeWithGA(List<Truck> initialTrucks, List<Material> allMaterials) {
        if (initialTrucks.isEmpty()) return initialTrucks;
        
        // Group materials by truck for GA representation
        List<List<Material>> groups = new ArrayList<>();
        for (Truck truck : initialTrucks) {
            groups.add(new ArrayList<>(truck.coils));
        }
        
        // Initialize population
        List<Chromosome> population = initializeGAPopulation(groups);
        
        Chromosome bestChromosome = null;
        double bestFitness = Double.NEGATIVE_INFINITY;
        
        for (int generation = 0; generation < MAX_GENERATIONS; generation++) {
            // Evaluate fitness
            for (Chromosome chrom : population) {
                evaluateGAFitness(chrom, groups, allMaterials);
            }
            
            // Find best
            Chromosome generationBest = population.stream()
                    .max(Comparator.comparingDouble(c -> c.fitness))
                    .orElse(null);
            
            if (generationBest != null && generationBest.fitness > bestFitness) {
                bestFitness = generationBest.fitness;
                bestChromosome = generationBest.copy();
            }
            
            // Create next generation
            List<Chromosome> nextGen = new ArrayList<>();
            
            // Elitism - keep top 10%
            population.sort((a, b) -> Double.compare(b.fitness, a.fitness));
            int eliteCount = POPULATION_SIZE / 10;
            nextGen.addAll(population.subList(0, eliteCount));
            
            // Fill rest with crossover and mutation
            while (nextGen.size() < POPULATION_SIZE) {
                Chromosome parent1 = tournamentSelect(population);
                Chromosome parent2 = tournamentSelect(population);
                
                if (Math.random() < CROSSOVER_RATE) {
                    Chromosome child = crossover(parent1, parent2);
                    child = mutate(child);
                    nextGen.add(child);
                } else {
                    nextGen.add(parent1.copy());
                }
            }
            
            population = nextGen;
        }
        
        // Decode best chromosome to trucks
        return decodeChromosomeToTrucks(bestChromosome, groups);
    }

    private List<Chromosome> initializeGAPopulation(List<List<Material>> groups) {
        List<Chromosome> population = new ArrayList<>();
        int numGroups = groups.size();
        
        for (int i = 0; i < POPULATION_SIZE; i++) {
            Chromosome chrom = new Chromosome(numGroups);
            
            for (int j = 0; j < numGroups; j++) {
                double groupWeight = groups.get(j).stream().mapToDouble(Material::getStockWeight).sum();
                int coilCount = groups.get(j).size();
                
                // Find suitable truck types
                List<Integer> suitableTypes = new ArrayList<>();
                for (TruckType type : TRUCK_TYPES.values()) {
                    if (type.maxWeight >= groupWeight - 0.01 && type.maxCoils >= coilCount) {
                        suitableTypes.add(type.id);
                    }
                }
                
                if (!suitableTypes.isEmpty()) {
                    // Random selection from suitable types
                    chrom.truckTypeAssignments[j] = suitableTypes.get(
                            (int) (Math.random() * suitableTypes.size()));
                } else {
                    // Default to largest type
                    chrom.truckTypeAssignments[j] = 5;
                }
            }
            
            population.add(chrom);
        }
        
        return population;
    }

    private void evaluateGAFitness(Chromosome chrom, List<List<Material>> groups, List<Material> allMaterials) {
        double totalCost = 0;
        double totalUtilization = 0;
        int validGroups = 0;
        
        for (int i = 0; i < groups.size(); i++) {
            int typeId = chrom.truckTypeAssignments[i];
            TruckType type = TRUCK_TYPES.get(typeId);
            
            if (type == null) continue;
            
            double groupWeight = groups.get(i).stream().mapToDouble(Material::getStockWeight).sum();
            int coilCount = groups.get(i).size();
            
            if (type.canCarry(groupWeight) && coilCount <= type.maxCoils) {
                totalCost += type.costPerDay;
                totalUtilization += (groupWeight / type.maxWeight);
                validGroups++;
            }
        }
        
        chrom.truckCount = validGroups;
        chrom.totalCost = totalCost;
        chrom.avgUtilization = validGroups > 0 ? (totalUtilization / validGroups) * 100 : 0;
        
        // Fitness function: maximize utilization, minimize cost
        double utilizationScore = chrom.avgUtilization / 100;
        double costScore = 1 - (totalCost / (validGroups * 35000)); // Normalize cost
        double truckCountScore = 1 - (validGroups / (double) groups.size());
        
        chrom.fitness = 0.5 * utilizationScore + 0.3 * costScore + 0.2 * truckCountScore;
    }

    private Chromosome tournamentSelect(List<Chromosome> population) {
        Chromosome best = null;
        for (int i = 0; i < 5; i++) {
            Chromosome candidate = population.get((int) (Math.random() * population.size()));
            if (best == null || candidate.fitness > best.fitness) {
                best = candidate;
            }
        }
        return best;
    }

    private Chromosome crossover(Chromosome p1, Chromosome p2) {
        Chromosome child = new Chromosome(p1.truckTypeAssignments.length);
        int point = (int) (Math.random() * p1.truckTypeAssignments.length);
        
        for (int i = 0; i < p1.truckTypeAssignments.length; i++) {
            if (i < point) {
                child.truckTypeAssignments[i] = p1.truckTypeAssignments[i];
            } else {
                child.truckTypeAssignments[i] = p2.truckTypeAssignments[i];
            }
        }
        return child;
    }

    private Chromosome mutate(Chromosome chrom) {
        Chromosome mutated = chrom.copy();
        
        for (int i = 0; i < mutated.truckTypeAssignments.length; i++) {
            if (Math.random() < MUTATION_RATE) {
                // Mutate to adjacent truck type (1-5)
                int current = mutated.truckTypeAssignments[i];
                int delta = (int) (Math.random() * 3) - 1; // -1, 0, or 1
                int newType = Math.max(1, Math.min(5, current + delta));
                mutated.truckTypeAssignments[i] = newType;
            }
        }
        
        return mutated;
    }

    private List<Truck> decodeChromosomeToTrucks(Chromosome chrom, List<List<Material>> groups) {
        List<Truck> trucks = new ArrayList<>();
        int truckId = 1;
        
        for (int i = 0; i < groups.size(); i++) {
            int typeId = chrom.truckTypeAssignments[i];
            TruckType type = TRUCK_TYPES.get(typeId);
            
            if (type != null) {
                Truck truck = new Truck(truckId++, type);
                for (Material m : groups.get(i)) {
                    truck.add(m);
                }
                trucks.add(truck);
            }
        }
        
        return trucks;
    }

    // ==================== STEP 5: LOCAL SEARCH OPTIMIZATION ====================

    /**
     * Fine-tune the solution with local search
     */
    private List<Truck> localSearchOptimization(List<Truck> trucks) {
        boolean improved;
        int iterations = 0;
        int maxIterations = 100;
        
        do {
            improved = false;
            iterations++;
            
            // Try to merge underutilized trucks
            improved |= mergeUnderutilizedTrucks(trucks);
            
            // Try to upgrade truck types for better utilization
            improved |= upgradeTruckTypes(trucks);
            
            // Try to rebalance loads
            improved |= rebalanceTruckLoads(trucks);
            
        } while (improved && iterations < maxIterations);
        
        return trucks;
    }

    /**
     * Merge underutilized trucks (<70% utilization)
     */
    private boolean mergeUnderutilizedTrucks(List<Truck> trucks) {
        boolean merged = false;
        
        // Find underutilized trucks
        List<Truck> underutilized = trucks.stream()
                .filter(t -> t.getUtilization() < 70 && t.coils.size() < t.type.maxCoils)
                .sorted(Comparator.comparingDouble(Truck::getUtilization))
                .collect(Collectors.toList());
        
        for (Truck target : underutilized) {
            for (Truck source : trucks) {
                if (target == source) continue;
                if (target.type.id != source.type.id) continue; // Same type only
                
                // Try to move coils from source to target
                List<Material> movable = new ArrayList<>(source.coils);
                for (Material m : movable) {
                    if (target.canAdd(m) && source.coils.size() > 1) {
                        source.coils.remove(m);
                        source.totalWeight -= m.getStockWeight();
                        target.add(m);
                        merged = true;
                        break;
                    }
                }
            }
        }
        
        // Remove empty trucks
        trucks.removeIf(t -> t.coils.isEmpty());
        
        return merged;
    }

    /**
     * Upgrade truck types for better cost efficiency
     */
    private boolean upgradeTruckTypes(List<Truck> trucks) {
        boolean upgraded = false;
        
        for (Truck truck : trucks) {
            if (truck.coils.size() == 1) {
                Material m = truck.coils.get(0);
                double weight = m.getStockWeight();
                
                // Find optimal truck type for this single coil
                int optimalType = findOptimalTruckTypeForWeight(weight);
                
                if (optimalType != truck.type.id) {
                    TruckType newType = TRUCK_TYPES.get(optimalType);
                    if (newType != null && newType.canCarry(weight)) {
                        // Upgrade to more cost-effective type
                        truck.type = newType;
                        upgraded = true;
                    }
                }
            }
        }
        
        return upgraded;
    }

    /**
     * Find optimal truck type for a given weight (minimizes cost per kg)
     */
    private int findOptimalTruckTypeForWeight(double weight) {
        TruckType best = null;
        double bestCostPerKg = Double.MAX_VALUE;
        
        for (TruckType type : TRUCK_TYPES.values()) {
            if (type.canCarry(weight)) {
                double costPerKg = type.costPerDay / weight;
                if (costPerKg < bestCostPerKg) {
                    bestCostPerKg = costPerKg;
                    best = type;
                }
            }
        }
        
        return best != null ? best.id : 5; // Default to largest
    }

    /**
     * Rebalance loads across trucks of same type
     */
    private boolean rebalanceTruckLoads(List<Truck> trucks) {
        boolean rebalanced = false;
        
        // Group trucks by type
        Map<Integer, List<Truck>> byType = trucks.stream()
                .collect(Collectors.groupingBy(t -> t.type.id));
        
        for (Map.Entry<Integer, List<Truck>> entry : byType.entrySet()) {
            List<Truck> typeTrucks = entry.getValue();
            if (typeTrucks.size() < 2) continue;
            
            // Calculate average weight for this type
            double avgWeight = typeTrucks.stream()
                    .mapToDouble(t -> t.totalWeight)
                    .average()
                    .orElse(0);
            
            // Find trucks below and above average
            List<Truck> belowAvg = typeTrucks.stream()
                    .filter(t -> t.totalWeight < avgWeight && t.canAcceptMore())
                    .collect(Collectors.toList());
            
            List<Truck> aboveAvg = typeTrucks.stream()
                    .filter(t -> t.totalWeight > avgWeight)
                    .collect(Collectors.toList());
            
            // Move coils from above avg to below avg
            for (Truck target : belowAvg) {
                for (Truck source : aboveAvg) {
                    List<Material> movable = new ArrayList<>(source.coils);
                    for (Material m : movable) {
                        if (target.canAdd(m) && source.coils.size() > 1) {
                            source.coils.remove(m);
                            source.totalWeight -= m.getStockWeight();
                            target.add(m);
                            rebalanced = true;
                            break;
                        }
                    }
                    if (!target.canAcceptMore()) break;
                }
            }
        }
        
        return rebalanced;
    }

    // ==================== UTILITY METHODS ====================

    private void printGlobalOptimizationSummary(List<LocationPlan> allPlans) {
        System.out.println("\n============ GLOBAL OPTIMIZATION SUMMARY ============");
        
        double totalWeight = 0;
        double totalCost = 0;
        int totalTrucks = 0;
        Map<Integer, Integer> typeCount = new TreeMap<>();
        Map<Integer, Double> typeWeight = new TreeMap<>();
        
        for (LocationPlan plan : allPlans) {
            totalWeight += plan.totalWeight;
            totalCost += plan.totalCost;
            totalTrucks += plan.trucks.size();
            
            for (Truck truck : plan.trucks) {
                typeCount.merge(truck.type.id, 1, Integer::sum);
                typeWeight.merge(truck.type.id, truck.totalWeight, Double::sum);
            }
        }
        
        System.out.printf("Total Weight: %.2f kg (%.2f tons)%n", totalWeight, totalWeight/1000);
        System.out.println("Total Trucks: " + totalTrucks);
        System.out.printf("Total Cost: Rs. %.0f%n", totalCost);
        System.out.printf("Average Cost per Ton: Rs. %.2f%n", totalCost / (totalWeight/1000));
        
        System.out.println("\nTruck Type Distribution:");
        for (Map.Entry<Integer, Integer> entry : typeCount.entrySet()) {
            int typeId = entry.getKey();
            TruckType type = TRUCK_TYPES.get(typeId);
            int count = entry.getValue();
            double weight = typeWeight.getOrDefault(typeId, 0.0);
            double avgUtil = count > 0 ? (weight / (count * type.maxWeight)) * 100 : 0;
            
            System.out.printf("  Type %d (%s): %d trucks, %.0f kg, %.1f%% avg util%n",
                    typeId, type.name, count, weight, avgUtil);
        }
        System.out.println("=====================================================");
    }

    private List<Material> extractMaterials(List<OrderCoilMapping4TruckDetail<Integer>> details) {
        List<Material> materials = new ArrayList<>();
        for (OrderCoilMapping4TruckDetail<Integer> detail : details) {
            Material m = new Material();
            m.setBatchNo(detail.getBatchNo());
            m.setOrderNo(detail.getOrderNo());
            m.setOrderLineItem(detail.getOrderLineItem());
            m.setStockWeight(detail.getStockWeight());
            m.setStockWidth(detail.getStockWidth());
            m.setStockLength(detail.getStockLength());
            m.setLocation(detail.getLocation());
            materials.add(m);
        }
        return materials;
    }

    private String getLocationFromMaterials(List<OrderCoilMapping4TruckDetail<Integer>> details) {
        if (details.isEmpty()) return "Unknown";
        return details.get(0).getLocation();
    }
}