package com.dm.planning.advanced;

import com.dm.bean.*;
import com.dm.planning.Optimization4TruckService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-Objective Evolutionary Algorithm for Truck Planning
 * Optimizes for:
 * 1. Minimize total cost
 * 2. Maximize weight utilization
 * 3. Minimize number of trucks
 */
public class MOEATruckOptimizer implements Optimization4TruckService<Integer> {

    // Algorithm parameters
    private int populationSize = 200;
    private int maxGenerations = 500;
    private double crossoverRate = 0.8;
    private double mutationRate = 0.15;
    private int tournamentSize = 5;
    private double eliteRate = 0.1;

    // Truck class with enhanced features
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

        double getCostEfficiency() {
            return totalWeight / perDayCost;
        }

        boolean isValid() {
            return totalWeight <= maxWeight + 0.01 &&
                    coils.size() <= maxCoils &&
                    coils.size() > 0;
        }

        // Get recommended truck type for a given weight
        static int getRecommendedTruckType(double weight) {
            if (weight <= 19000) return 1;      // 2-axle
            if (weight <= 28500) return 2;      // 3-axle
            if (weight <= 36000) return 3;      // 4-axle
            if (weight <= 47500) return 4;      // 5-axle
            return 5;                            // 6-axle
        }

        @Override
        public String toString() {
            return String.format("    Truck %d (Type %d: %s): %d coils, %.2f kg, %.1f%% utilization, Cost: Rs.%.0f",
                    id, truckTypeId, truckTypeName, coils.size(), totalWeight, getUtilization(), perDayCost);
        }
    }

    // Chromosome representation for genetic algorithm
    private static class Chromosome {
        int[] truckAssignments;  // truck type for each material (1-based truck type ID)
        int[] truckGroupings;    // which truck each material belongs to
        double fitness;
        double totalCost;
        double avgUtilization;
        int truckCount;
        boolean isValid = true;

        Chromosome(int materialCount) {
            truckAssignments = new int[materialCount];
            truckGroupings = new int[materialCount];
        }

        Chromosome copy() {
            Chromosome copy = new Chromosome(truckAssignments.length);
            System.arraycopy(this.truckAssignments, 0, copy.truckAssignments, 0, truckAssignments.length);
            System.arraycopy(this.truckGroupings, 0, copy.truckGroupings, 0, truckGroupings.length);
            copy.fitness = this.fitness;
            copy.totalCost = this.totalCost;
            copy.avgUtilization = this.avgUtilization;
            copy.truckCount = this.truckCount;
            copy.isValid = this.isValid;
            return copy;
        }
    }

    // Pareto front solution
    private static class ParetoSolution {
        Chromosome solution;
        double cost;
        double utilization;
        int truckCount;

        ParetoSolution(Chromosome solution, double cost, double utilization, int truckCount) {
            this.solution = solution;
            this.cost = cost;
            this.utilization = utilization;
            this.truckCount = truckCount;
        }

        boolean dominates(ParetoSolution other) {
            return (this.cost <= other.cost &&
                    this.utilization >= other.utilization &&
                    this.truckCount <= other.truckCount) &&
                    (this.cost < other.cost ||
                            this.utilization > other.utilization ||
                            this.truckCount < other.truckCount);
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
            System.out.println("\nProcessing Plan for Group " +
                    (planDetail.getParameterDetail() != null && !planDetail.getParameterDetail().isEmpty() ?
                            planDetail.getParameterDetail().get(0).getGroupId() : "Unknown"));

            LocationPlan plan = optimizeLocationWithMOEA(planDetail);

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

    private LocationPlan optimizeLocationWithMOEA(Plan4TruckDetail planDetail) {
        LocationPlan bestPlan = new LocationPlan();

        // Get constraints and materials
        PlanParameter4TruckDetail param = planDetail.getParameterDetail().get(0);
        bestPlan.location = getLocationFromMaterials(planDetail.getOrderCoilMappingDetails());

        List<TruckConstraintDetail> truckTypes = planDetail.getTruckConstraintDetails();
        double roadMaxWeight = param.getRoadMaxWeight();

        // Print truck types for debugging
        System.out.println("\nAvailable Truck Types:");
        for (TruckConstraintDetail truck : truckTypes) {
            System.out.println("  Type " + truck.getTruckTypeId() + ": " + truck.getTruckTypeName() +
                    ", Weight: " + truck.getTruckLoadWeight() + " kg, Coils: " + truck.getTruckLoadNoMax() +
                    ", Cost: Rs." + truck.getPerDayCost());
        }

        List<Material> materials = extractMaterials(planDetail.getOrderCoilMappingDetails());
        System.out.println("Total materials to process: " + materials.size());

        // Sort materials by weight (descending) for better packing
        materials.sort((a, b) -> Double.compare(b.getStockWeight(), a.getStockWeight()));

        // Validate materials against road constraint
        validateMaterialsAgainstRoadConstraint(materials, roadMaxWeight);

        // Run enhanced optimization that processes ALL materials
        List<Truck> trucks = enhancedOptimization(materials, truckTypes);

        // Add to plan
        for (Truck truck : trucks) {
            bestPlan.addTruck(truck);
        }

        // Verify all materials are processed
        Set<Material> processedMaterials = new HashSet<>();
        for (Truck truck : trucks) {
            processedMaterials.addAll(truck.coils);
        }

        if (processedMaterials.size() != materials.size()) {
            System.out.println("WARNING: Only " + processedMaterials.size() + " out of " +
                    materials.size() + " materials were processed!");

            // Find unprocessed materials
            Set<Material> unprocessed = new HashSet<>(materials);
            unprocessed.removeAll(processedMaterials);
            System.out.println("Unprocessed materials:");
            for (Material m : unprocessed) {
                System.out.println("  " + m.getBatchNo() + " - " + m.getStockWeight() + " kg");
            }
        }

        // Print location summary
        printLocationSummary(bestPlan);

        return bestPlan;
    }

    /**
     * Enhanced optimization that properly processes ALL materials
     */
    private List<Truck> enhancedOptimization(List<Material> materials,
                                             List<TruckConstraintDetail> truckTypes) {
        List<Truck> trucks = new ArrayList<>();
        List<Material> remainingMaterials = new ArrayList<>(materials);

        // Create a map of truck types by ID for easy access
        Map<Integer, TruckConstraintDetail> truckTypeMap = new HashMap<>();
        for (TruckConstraintDetail truck : truckTypes) {
            truckTypeMap.put(truck.getTruckTypeId(), truck);
        }

        int truckCounter = 1;
        int maxIterations = 1000; // Safety limit
        int iterations = 0;

        while (!remainingMaterials.isEmpty() && iterations < maxIterations) {
            iterations++;

            // Get the current material (heaviest remaining)
            Material currentMaterial = remainingMaterials.get(0);

            // Find the most suitable truck type for this material
            TruckConstraintDetail bestTruckType = findBestTruckType(currentMaterial, remainingMaterials, truckTypes);

            if (bestTruckType == null) {
                System.out.println("ERROR: No suitable truck for material " + currentMaterial.getBatchNo() +
                        " weighing " + currentMaterial.getStockWeight() + " kg");
                remainingMaterials.remove(0); // Skip this material
                continue;
            }

            // Create new truck
            Truck truck = new Truck(truckCounter++, bestTruckType);

            // Try to load as many materials as possible onto this truck
            Iterator<Material> iterator = remainingMaterials.iterator();
            while (iterator.hasNext()) {
                Material material = iterator.next();

                // Check if this truck can carry this material
                if (truck.canAdd(material)) {
                    truck.add(material);
                    iterator.remove();
                    System.out.println("  Loaded material " + material.getBatchNo() +
                            " (" + material.getStockWeight() + " kg) onto Truck " + truck.id);
                }
            }

            // Only add valid trucks
            if (truck.isValid()) {
                trucks.add(truck);
                System.out.println("Created Truck " + truck.id + ": " + truck.coils.size() +
                        " coils, " + truck.totalWeight + " kg, " +
                        String.format("%.1f", truck.getUtilization()) + "% utilization");
            }
        }

        if (!remainingMaterials.isEmpty()) {
            System.out.println("WARNING: " + remainingMaterials.size() + " materials could not be processed!");
        }

        // Post-processing: Try to merge underutilized trucks
        trucks = optimizeTruckUtilization(trucks, truckTypeMap);

        // Post-processing: Optimize truck types for single-coil trucks
        trucks = optimizeTruckTypes(trucks, truckTypeMap);

        return trucks;
    }

    /**
     * Find the best truck type for a given material considering future materials
     */
    private TruckConstraintDetail findBestTruckType(Material material,
                                                    List<Material> allMaterials,
                                                    List<TruckConstraintDetail> truckTypes) {
        double materialWeight = material.getStockWeight();

        // Filter trucks that can carry this material
        List<TruckConstraintDetail> suitableTrucks = new ArrayList<>();
        for (TruckConstraintDetail truck : truckTypes) {
            if (truck.getTruckLoadWeight() >= materialWeight - 0.01) {
                suitableTrucks.add(truck);
            }
        }

        if (suitableTrucks.isEmpty()) {
            return null;
        }

        // Calculate average weight of remaining materials (excluding current)
        double avgRemainingWeight = 0;
        if (allMaterials.size() > 1) {
            avgRemainingWeight = allMaterials.subList(1, allMaterials.size()).stream()
                    .mapToDouble(Material::getStockWeight)
                    .average()
                    .orElse(0);
        }

        // Score each truck type
        Map<TruckConstraintDetail, Double> scores = new HashMap<>();

        for (TruckConstraintDetail truck : suitableTrucks) {
            double score = 0;

            // Factor 1: Capacity utilization for current material
            double utilization = materialWeight / truck.getTruckLoadWeight();
            score += utilization * 30; // 30% weight

            // Factor 2: Cost efficiency (prefer cheaper trucks)
            double costEfficiency = truck.getTruckLoadWeight() / truck.getPerDayCost();
            double maxCostEfficiency = suitableTrucks.stream()
                    .mapToDouble(t -> t.getTruckLoadWeight() / t.getPerDayCost())
                    .max()
                    .orElse(1);
            score += (costEfficiency / maxCostEfficiency) * 30; // 30% weight

            // Factor 3: Remaining capacity for other materials
            double remainingCapacity = truck.getTruckLoadWeight() - materialWeight;
            if (remainingCapacity >= avgRemainingWeight * 0.8) {
                score += 20; // Can fit another average material
            } else if (remainingCapacity >= avgRemainingWeight * 0.5) {
                score += 10; // Can fit a smaller material
            }

            // Factor 4: Coil capacity
            double coilUtilization = 1.0 / truck.getTruckLoadNoMax();
            score += (1 - coilUtilization) * 20; // Prefer trucks that can take multiple coils

            // Factor 5: For 30000 kg materials, strongly prefer Type 3
            if (Math.abs(materialWeight - 30000) < 1000 && truck.getTruckTypeId() == 3) {
                score += 50; // Huge bonus for using optimal truck for 30,000 kg
            }

            scores.put(truck, score);
        }

        // Return the truck with highest score
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(suitableTrucks.get(0));
    }

    /**
     * Optimize truck utilization by merging underutilized trucks
     */
    private List<Truck> optimizeTruckUtilization(List<Truck> trucks,
                                                 Map<Integer, TruckConstraintDetail> truckTypeMap) {
        boolean improved;
        int maxIterations = 50;
        int iteration = 0;

        do {
            improved = false;
            iteration++;

            // Sort trucks by utilization (ascending)
            trucks.sort((a, b) -> Double.compare(a.getUtilization(), b.getUtilization()));

            for (int i = 0; i < trucks.size() - 1; i++) {
                Truck underUtilized = trucks.get(i);

                // Skip if utilization is already good (> 70%)
                if (underUtilized.getUtilization() > 70) {
                    continue;
                }

                for (int j = trucks.size() - 1; j > i; j--) {
                    Truck wellUtilized = trucks.get(j);

                    // Try to move a coil from wellUtilized to underUtilized
                    List<Material> movableCoils = new ArrayList<>(wellUtilized.coils);
                    for (Material material : movableCoils) {
                        if (underUtilized.canAdd(material)) {
                            // Check if moving doesn't break the source truck
                            if (wellUtilized.coils.size() > 1 &&
                                    wellUtilized.totalWeight - material.getStockWeight() > 0) {

                                // Perform the move
                                wellUtilized.coils.remove(material);
                                wellUtilized.totalWeight -= material.getStockWeight();
                                underUtilized.add(material);

                                System.out.println("  Moved material " + material.getBatchNo() +
                                        " from Truck " + wellUtilized.id + " to Truck " + underUtilized.id);
                                improved = true;
                                break;
                            }
                        }
                    }

                    if (improved) break;
                }

                if (improved) break;
            }

        } while (improved && iteration < maxIterations);

        // Remove any empty trucks
        trucks.removeIf(t -> t.coils.isEmpty());

        return trucks;
    }

    /**
     * Optimize truck types for single-coil trucks
     */
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

                // If current truck type is not optimal and a more suitable type exists
                if (optimalTypeId != truck.truckTypeId) {
                    TruckConstraintDetail optimalTruck = truckTypeMap.get(optimalTypeId);
                    if (optimalTruck != null && optimalTruck.getTruckLoadWeight() >= weight - 0.01) {
                        // Replace with optimal truck type
                        Truck newTruck = new Truck(truck.id, optimalTruck);
                        newTruck.add(singleMaterial);
                        optimizedTrucks.add(newTruck);
                        System.out.println("  Optimized Truck " + truck.id + ": Changed from Type " +
                                truck.truckTypeId + " to Type " + optimalTypeId);
                        continue;
                    }
                }
            }
            optimizedTrucks.add(truck);
        }

        return optimizedTrucks;
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

    private List<Chromosome> initializePopulation(int materialCount,
                                                  List<TruckConstraintDetail> truckTypes) {
        List<Chromosome> population = new ArrayList<>();
        int numTruckTypes = truckTypes.size();

        for (int i = 0; i < populationSize; i++) {
            Chromosome chrom = new Chromosome(materialCount);

            if (i < populationSize / 3) {
                // Random initialization with 1-based truck type IDs
                for (int j = 0; j < materialCount; j++) {
                    chrom.truckAssignments[j] = 1 + (int) (Math.random() * numTruckTypes);
                    chrom.truckGroupings[j] = (int) (Math.random() * materialCount);
                }
            } else if (i < populationSize * 2 / 3) {
                // Greedy initialization
                chrom = greedyInitialization(materialCount, truckTypes);
            } else {
                // Load-balanced initialization
                chrom = loadBalancedInitialization(materialCount, truckTypes);
            }

            population.add(chrom);
        }

        return population;
    }

    private Chromosome greedyInitialization(int materialCount,
                                            List<TruckConstraintDetail> truckTypes) {
        Chromosome chrom = new Chromosome(materialCount);

        for (int i = 0; i < materialCount; i++) {
            // Use recommended truck type based on position (simulating weight)
            double estimatedWeight = 30000 - (i * 1000); // Decreasing weight
            chrom.truckAssignments[i] = Truck.getRecommendedTruckType(estimatedWeight);

            // Assign to truck group (approximately 3 coils per truck)
            int targetTrucks = Math.max(1, materialCount / 3);
            chrom.truckGroupings[i] = i % targetTrucks;
        }

        return chrom;
    }

    private Chromosome loadBalancedInitialization(int materialCount,
                                                  List<TruckConstraintDetail> truckTypes) {
        Chromosome chrom = new Chromosome(materialCount);
        int targetTrucks = Math.max(1, (int) Math.ceil(materialCount / 3.0)); // Aim for 3 coils per truck

        for (int i = 0; i < materialCount; i++) {
            // Distribute evenly across trucks
            int truckId = i % targetTrucks;
            chrom.truckGroupings[i] = truckId;

            // Select appropriate truck type based on position
            if (i < materialCount / 3) {
                chrom.truckAssignments[i] = 4; // 5-axle for heaviest
            } else if (i < materialCount * 2 / 3) {
                chrom.truckAssignments[i] = 3; // 4-axle for medium
            } else {
                chrom.truckAssignments[i] = 2; // 3-axle for lighter
            }
        }

        return chrom;
    }

    private void evaluateObjectives(Chromosome chrom, List<Material> materials,
                                    List<TruckConstraintDetail> truckTypes) {
        // Create a map for quick truck type lookup
        Map<Integer, TruckConstraintDetail> truckTypeMap = new HashMap<>();
        for (TruckConstraintDetail truck : truckTypes) {
            truckTypeMap.put(truck.getTruckTypeId(), truck);
        }

        Map<Integer, List<Integer>> truckGroups = new HashMap<>();

        // Group materials by truck
        for (int i = 0; i < materials.size(); i++) {
            int truckId = chrom.truckGroupings[i];
            truckGroups.computeIfAbsent(truckId, k -> new ArrayList<>()).add(i);
        }

        double totalCost = 0;
        double totalUtilization = 0;
        int validTrucks = 0;
        chrom.isValid = true;

        for (Map.Entry<Integer, List<Integer>> entry : truckGroups.entrySet()) {
            List<Integer> materialIndices = entry.getValue();

            // Get the most common truck type in this group
            Map<Integer, Long> typeCounts = materialIndices.stream()
                    .collect(Collectors.groupingBy(i -> chrom.truckAssignments[i], Collectors.counting()));

            int typeId = typeCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(chrom.truckAssignments[materialIndices.get(0)]);

            TruckConstraintDetail truckType = truckTypeMap.get(typeId);
            if (truckType == null) {
                chrom.isValid = false;
                continue;
            }

            double truckWeight = 0;
            for (int idx : materialIndices) {
                truckWeight += materials.get(idx).getStockWeight();
            }

            // Validate truck
            if (truckWeight <= truckType.getTruckLoadWeight() + 0.01 &&
                    materialIndices.size() <= truckType.getTruckLoadNoMax()) {
                totalCost += truckType.getPerDayCost();
                totalUtilization += (truckWeight / truckType.getTruckLoadWeight()) * 100;
                validTrucks++;
            } else {
                chrom.isValid = false;
            }
        }

        chrom.truckCount = validTrucks;
        chrom.totalCost = totalCost;
        chrom.avgUtilization = validTrucks > 0 ? totalUtilization / validTrucks : 0;

        // Multi-objective fitness
        double normalizedCost = totalCost / 1000000;
        double normalizedUtil = chrom.avgUtilization / 100;
        double normalizedTrucks = validTrucks / 50.0;

        // Heavily weight utilization and penalize invalid solutions
        if (chrom.isValid) {
            chrom.fitness = -0.2 * normalizedCost + 0.6 * normalizedUtil - 0.2 * normalizedTrucks;
        } else {
            chrom.fitness = -1000; // Heavy penalty for invalid solutions
        }
    }

    private void updateParetoFront(List<ParetoSolution> paretoFront, List<Chromosome> population) {
        List<ParetoSolution> candidates = new ArrayList<>();

        for (Chromosome chrom : population) {
            if (chrom.isValid) {
                candidates.add(new ParetoSolution(chrom, chrom.totalCost,
                        chrom.avgUtilization, chrom.truckCount));
            }
        }

        // Find non-dominated solutions
        for (ParetoSolution candidate : candidates) {
            boolean dominated = false;

            for (ParetoSolution existing : paretoFront) {
                if (existing.dominates(candidate)) {
                    dominated = true;
                    break;
                }
            }

            if (!dominated) {
                // Remove solutions dominated by this candidate
                paretoFront.removeIf(existing -> candidate.dominates(existing));
                paretoFront.add(candidate);
            }
        }

        // Keep only top solutions
        if (paretoFront.size() > 20) {
            paretoFront.sort((a, b) -> Double.compare(
                    b.cost + b.utilization * 1000 - b.truckCount * 10000,
                    a.cost + a.utilization * 1000 - a.truckCount * 10000));
            paretoFront.subList(20, paretoFront.size()).clear();
        }
    }

    private Chromosome tournamentSelect(List<Chromosome> population) {
        Chromosome best = null;
        for (int i = 0; i < tournamentSize; i++) {
            Chromosome candidate = population.get((int) (Math.random() * population.size()));
            if (best == null || candidate.fitness > best.fitness) {
                best = candidate;
            }
        }
        return best;
    }

    private Chromosome crossover(Chromosome parent1, Chromosome parent2) {
        Chromosome child = new Chromosome(parent1.truckAssignments.length);

        int crossoverPoint = (int) (Math.random() * parent1.truckAssignments.length);

        for (int i = 0; i < parent1.truckAssignments.length; i++) {
            if (i < crossoverPoint) {
                child.truckAssignments[i] = parent1.truckAssignments[i];
                child.truckGroupings[i] = parent1.truckGroupings[i];
            } else {
                child.truckAssignments[i] = parent2.truckAssignments[i];
                child.truckGroupings[i] = parent2.truckGroupings[i];
            }
        }

        return child;
    }

    private Chromosome mutate(Chromosome chrom, int numTruckTypes) {
        Chromosome mutated = chrom.copy();

        for (int i = 0; i < mutated.truckAssignments.length; i++) {
            if (Math.random() < mutationRate) {
                if (Math.random() < 0.5) {
                    // Mutate truck type (1-based)
                    mutated.truckAssignments[i] = 1 + (int) (Math.random() * numTruckTypes);
                } else {
                    // Mutate truck grouping
                    mutated.truckGroupings[i] = (int) (Math.random() * mutated.truckAssignments.length);
                }
            }
        }

        return mutated;
    }

    private ParetoSolution selectBestSolution(List<ParetoSolution> paretoFront) {
        if (paretoFront.isEmpty()) {
            return null;
        }

        // Weighted score: prioritize utilization and low cost
        return paretoFront.stream()
                .max((a, b) -> {
                    double scoreA = -0.3 * a.cost + 0.5 * a.utilization - 0.2 * a.truckCount * 10000;
                    double scoreB = -0.3 * b.cost + 0.5 * b.utilization - 0.2 * b.truckCount * 10000;
                    return Double.compare(scoreA, scoreB);
                })
                .orElse(paretoFront.get(0));
    }

    private List<Truck> decodeToTrucks(Chromosome chrom, List<Material> materials,
                                       List<TruckConstraintDetail> truckTypes) {
        if (!chrom.isValid) {
            return new ArrayList<>();
        }

        Map<Integer, TruckConstraintDetail> truckTypeMap = new HashMap<>();
        for (TruckConstraintDetail truck : truckTypes) {
            truckTypeMap.put(truck.getTruckTypeId(), truck);
        }

        Map<Integer, List<Integer>> truckGroups = new HashMap<>();

        for (int i = 0; i < materials.size(); i++) {
            int truckId = chrom.truckGroupings[i];
            truckGroups.computeIfAbsent(truckId, k -> new ArrayList<>()).add(i);
        }

        List<Truck> trucks = new ArrayList<>();
        int truckCounter = 1;

        for (Map.Entry<Integer, List<Integer>> entry : truckGroups.entrySet()) {
            List<Integer> indices = entry.getValue();

            // Determine truck type (use most common)
            Map<Integer, Long> typeCounts = indices.stream()
                    .collect(Collectors.groupingBy(i -> chrom.truckAssignments[i], Collectors.counting()));

            int typeId = typeCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(chrom.truckAssignments[indices.get(0)]);

            TruckConstraintDetail truckType = truckTypeMap.get(typeId);
            if (truckType == null) continue;

            Truck truck = new Truck(truckCounter++, truckType);

            for (int idx : indices) {
                truck.add(materials.get(idx));
            }

            // Only add valid trucks
            if (truck.isValid()) {
                trucks.add(truck);
            }
        }

        return trucks;
    }

    private void printLocationSummary(LocationPlan plan) {
        System.out.println("\n=== Location " + plan.location + " Optimization Summary ===");
        System.out.println("Total Weight: " + String.format("%.2f", plan.totalWeight) + " kg");
        System.out.println("Total Trucks: " + plan.trucks.size());
        System.out.println("Total Cost: Rs. " + String.format("%.0f", plan.totalCost));
        System.out.println("Average Utilization: " + String.format("%.2f", plan.getAverageUtilization()) + "%");
        System.out.println("Cost per Ton: Rs. " + String.format("%.2f", plan.getCostPerTon()));

        // Group trucks by type
        Map<Integer, List<Truck>> trucksByType = plan.trucks.stream()
                .collect(Collectors.groupingBy(t -> t.truckTypeId));

        for (Map.Entry<Integer, List<Truck>> entry : trucksByType.entrySet()) {
            int typeId = entry.getKey();
            List<Truck> typeTrucks = entry.getValue();
            String typeName = typeTrucks.get(0).truckTypeName;
            double typeCost = typeTrucks.stream().mapToDouble(t -> t.perDayCost).sum();
            double typeWeight = typeTrucks.stream().mapToDouble(t -> t.totalWeight).sum();

            System.out.println("  " + typeName + " (" + typeTrucks.size() + " trucks): " +
                    String.format("%.2f", typeWeight) + " kg, Rs. " + String.format("%.0f", typeCost));

            // Print individual trucks
            for (Truck truck : typeTrucks) {
                System.out.println(truck);
            }
        }
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
                String.format("%.2f", totalWeight / 1000) + " tons)");
        System.out.println("Total Trucks: " + totalTrucks);
        System.out.println("Total Cost: Rs. " + String.format("%.0f", totalCost));
        System.out.println("Average Cost per Ton: Rs. " + String.format("%.2f", totalCost / (totalWeight / 1000)));

        System.out.println("\nTruck Type Distribution:");
        for (Map.Entry<Integer, Integer> entry : trucksByType.entrySet()) {
            int typeId = entry.getKey();
            String typeName = getTruckTypeName(typeId);
            int count = entry.getValue();
            double typeWeight = weightByType.getOrDefault(typeId, 0.0);
            double typeCost = costByType.getOrDefault(typeId, 0.0);
            double typeCapacity = getTruckCapacity(typeId);
            double avgUtil = count > 0 ? (typeWeight / (count * typeCapacity)) * 100 : 0;

            System.out.println("  Type " + typeId + " (" + typeName + "): " + count + " trucks");
            System.out.println("    Total Weight: " + String.format("%.2f", typeWeight) + " kg");
            System.out.println("    Total Cost: Rs. " + String.format("%.0f", typeCost));
            System.out.println("    Avg Utilization: " + String.format("%.2f", avgUtil) + "%");
        }
        System.out.println("=======================================================");
    }

    private String getTruckTypeName(int typeId) {
        switch (typeId) {
            case 1:
                return "2-axle";
            case 2:
                return "3-axle";
            case 3:
                return "4-axle";
            case 4:
                return "5-axle";
            case 5:
                return "6-axle";
            default:
                return "Unknown";
        }
    }

    private double getTruckCapacity(int typeId) {
        switch (typeId) {
            case 1:
                return 19000;
            case 2:
                return 28500;
            case 3:
                return 36000;
            case 4:
                return 47500;
            case 5:
                return 49000;
            default:
                return 0;
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

    // LocationPlan inner class
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
    }
}