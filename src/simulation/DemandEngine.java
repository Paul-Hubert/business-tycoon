package simulation;

import config.ConfigManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages demand multipliers for consumer goods using Perlin noise.
 *
 * Each consumer good has an independent demand curve that oscillates organically
 * over its demand cycle. Players can see the current multiplier and trend direction
 * (rising/falling/stable) but NOT exact future values.
 *
 * Saturation: the more units sold in a cycle, the lower the effective price cap.
 * Cycles reset automatically based on each resource's demandCycleMinutes.
 */
public class DemandEngine {

    private static volatile DemandEngine instance;

    // Per-resource Perlin generators (seeded from resource name hash for uniqueness)
    private final Map<String, PerlinNoise> noiseGenerators = new ConcurrentHashMap<>();

    // Per-resource saturation: total units sold in the current demand cycle
    private final Map<String, Integer> saturationCounters = new ConcurrentHashMap<>();

    // Per-resource cycle start tick
    private final Map<String, Integer> cycleStartTicks = new ConcurrentHashMap<>();

    private DemandEngine() {}

    public static DemandEngine getInstance() {
        if (instance == null) {
            synchronized (DemandEngine.class) {
                if (instance == null) instance = new DemandEngine();
            }
        }
        return instance;
    }

    /**
     * Returns the Perlin-based demand multiplier for the current tick.
     * Range: [minMultiplier, maxMultiplier] from config (default 0.7–1.4).
     * Returns 1.0 for non-consumer goods.
     */
    public double getDemandMultiplier(String resourceName, int tick) {
        ResourceRegistry.Resource resource = ResourceRegistry.get(resourceName);
        if (resource == null || !resource.isConsumerGood()) return 1.0;

        ConfigManager cfg = ConfigManager.getInstance();
        int octaves          = cfg.getInt("perlin.base_octaves", 6);
        double minMultiplier = cfg.getDouble("perlin.demand_min_multiplier", 0.7);
        double maxMultiplier = cfg.getDouble("perlin.demand_max_multiplier", 1.4);

        double ticksPerSecond = cfg.getDouble("simulation.ticks_per_second", 4.0);
        double ticksPerCycle  = resource.demandCycleMinutes * 60.0 * ticksPerSecond;

        // Advance slowly through noise field — one full noise "period" per demand cycle
        double x = tick / ticksPerCycle;

        PerlinNoise noise = noiseGenerators.computeIfAbsent(
            resourceName,
            name -> new PerlinNoise((long) name.hashCode())
        );

        double raw        = noise.octaveNoise(x, octaves, 0.5, 2.0); // [-1, 1]
        double normalized = (raw + 1.0) / 2.0;                        // [0, 1]
        return minMultiplier + normalized * (maxMultiplier - minMultiplier);
    }

    /**
     * Saturation penalty: the more units sold in the current cycle, the lower the cap.
     * Returns 1.0 at zero sales, approaches 0.5 at high saturation.
     */
    public double getSaturationPenalty(String resourceName) {
        int sold    = saturationCounters.getOrDefault(resourceName, 0);
        double penalty = 1.0 - (sold * 0.001); // each unit sold reduces cap by 0.1%
        return Math.max(0.5, penalty);
    }

    /**
     * Effective price cap = basePrice × demandMultiplier × saturationPenalty.
     * Consumer goods sold above this cap get no NPC buyers.
     */
    public double getEffectivePriceCap(String resourceName, int tick) {
        ResourceRegistry.Resource resource = ResourceRegistry.get(resourceName);
        if (resource == null || !resource.isConsumerGood()) return 0;
        return resource.basePrice * getDemandMultiplier(resourceName, tick) * getSaturationPenalty(resourceName);
    }

    /**
     * Record that units were sold this cycle (used to increment saturation).
     */
    public void recordSale(String resourceName, int quantity) {
        saturationCounters.merge(resourceName, quantity, Integer::sum);
    }

    /**
     * Check and reset any demand cycles that have completed this tick.
     * Call once at the start of step 4 every tick.
     */
    public void updateCycles(int tick) {
        ConfigManager cfg = ConfigManager.getInstance();
        double ticksPerSecond = cfg.getDouble("simulation.ticks_per_second", 4.0);

        for (ResourceRegistry.Resource resource : ResourceRegistry.allResources()) {
            if (!resource.isConsumerGood()) continue;
            int    startTick     = cycleStartTicks.getOrDefault(resource.name, 0);
            double ticksPerCycle = resource.demandCycleMinutes * 60.0 * ticksPerSecond;
            if (tick - startTick >= ticksPerCycle) {
                saturationCounters.put(resource.name, 0);
                cycleStartTicks.put(resource.name, tick);
            }
        }
    }

    /**
     * Demand forecast exposed to players via API: current multiplier + trend direction.
     * Trend is computed by comparing now to 10 ticks ahead — enough for direction,
     * not enough for exact exploitation.
     */
    public Map<String, Object> getDemandForecast(String resourceName, int tick) {
        double current = getDemandMultiplier(resourceName, tick);
        double future  = getDemandMultiplier(resourceName, tick + 10);
        String trend   = future > current + 0.01 ? "rising"
                       : future < current - 0.01 ? "falling"
                       : "stable";

        double priceCap = getEffectivePriceCap(resourceName, tick);

        Map<String, Object> forecast = new LinkedHashMap<>();
        forecast.put("multiplier", Math.round(current * 100.0) / 100.0);
        forecast.put("trend", trend);
        forecast.put("price_cap", Math.round(priceCap * 100.0) / 100.0);
        return forecast;
    }
}
