package simulation;

import config.ConfigManager;
import java.util.*;

/**
 * Static registry of all 32 game resources and their production recipes.
 * Resources are game data, not user data — defined in code, not the database.
 * This avoids a DB round-trip every tick and simplifies validation.
 */
public class ResourceRegistry {

    public enum Tier { RAW, INTERMEDIATE, ADVANCED_INTERMEDIATE, CONSUMER }

    public static class Resource {
        public final String name;
        public final Tier tier;
        public final double decayRatePerCycle; // 0 = no decay; 0.01 = 1% per 60 ticks
        public final double basePrice;          // Consumer goods only; 0 for non-consumer
        public final int demandCycleMinutes;    // Consumer goods only

        // Derived from config at call time (hot-reloadable)
        public double getBuildCost() {
            ConfigManager cfg = ConfigManager.getInstance();
            return switch (tier) {
                case RAW                   -> cfg.getDouble("facility.build_cost.raw",                  100.0);
                case INTERMEDIATE          -> cfg.getDouble("facility.build_cost.intermediate",          300.0);
                case ADVANCED_INTERMEDIATE -> cfg.getDouble("facility.build_cost.advanced_intermediate", 800.0);
                case CONSUMER              -> cfg.getDouble("facility.build_cost.consumer",             2000.0);
            };
        }

        public double getOperatingCostPerTick() {
            ConfigManager cfg = ConfigManager.getInstance();
            return switch (tier) {
                case RAW                   -> cfg.getDouble("facility.operating_cost.raw",                  2.0);
                case INTERMEDIATE          -> cfg.getDouble("facility.operating_cost.intermediate",          5.0);
                case ADVANCED_INTERMEDIATE -> cfg.getDouble("facility.operating_cost.advanced_intermediate", 12.0);
                case CONSUMER              -> cfg.getDouble("facility.operating_cost.consumer",             30.0);
            };
        }

        public final int defaultProductionPerTick;

        public Resource(String name, Tier tier, int defaultProductionPerTick,
                        double decayRatePerCycle, double basePrice, int demandCycleMinutes) {
            this.name = name;
            this.tier = tier;
            this.defaultProductionPerTick = defaultProductionPerTick;
            this.decayRatePerCycle = decayRatePerCycle;
            this.basePrice = basePrice;
            this.demandCycleMinutes = demandCycleMinutes;
        }

        public boolean isConsumerGood() {
            return tier == Tier.CONSUMER
                || name.equals("bread")
                || name.equals("canned_food");
        }

        public boolean isPerishable() {
            return decayRatePerCycle > 0;
        }

        /** Lowercase tier string for use with config keys. */
        public String tierKey() {
            return tier.name().toLowerCase();
        }
    }

    public static class Recipe {
        public final String outputResource;
        public final int outputQuantity;
        public final Map<String, Integer> inputs; // resource name -> qty consumed per output tick

        public Recipe(String outputResource, int outputQuantity, Map<String, Integer> inputs) {
            this.outputResource = outputResource;
            this.outputQuantity = outputQuantity;
            this.inputs = Collections.unmodifiableMap(inputs);
        }
    }

    // ── Static registry ─────────────────────────────────────────────────────

    private static final Map<String, Resource> RESOURCES = new LinkedHashMap<>();
    private static final Map<String, Recipe>   RECIPES   = new LinkedHashMap<>();

    static {
        // ── Raw resources (no inputs, produce at capacity unconditionally) ──
        r("wheat",   Tier.RAW,  10, 0.010, 0,     0);
        r("iron",    Tier.RAW,  8,  0.0,   0,     0);
        r("copper",  Tier.RAW,  6,  0.0,   0,     0);
        r("gold",    Tier.RAW,  2,  0.0,   0,     0);
        r("petrol",  Tier.RAW,  5,  0.005, 0,     0);
        r("cotton",  Tier.RAW,  8,  0.005, 0,     0);
        r("timber",  Tier.RAW,  10, 0.0,   0,     0);
        r("lithium", Tier.RAW,  3,  0.0,   0,     0);
        r("rubber",  Tier.RAW,  5,  0.003, 0,     0);
        r("silicon", Tier.RAW,  4,  0.0,   0,     0);
        r("bauxite", Tier.RAW,  7,  0.0,   0,     0);
        r("coal",    Tier.RAW,  9,  0.0,   0,     0);

        // ── Intermediate resources ──────────────────────────────────────────
        r("bread",          Tier.INTERMEDIATE,          5, 0.020, 4.0,  5);
        r("steel",          Tier.INTERMEDIATE,          4, 0.0,   0,    0);
        r("plastic",        Tier.INTERMEDIATE,          6, 0.0,   0,    0);
        r("circuit",        Tier.ADVANCED_INTERMEDIATE, 3, 0.0,   0,    0);
        r("fabric",         Tier.INTERMEDIATE,          5, 0.0,   0,    0);
        r("lumber",         Tier.INTERMEDIATE,          6, 0.0,   0,    0);
        r("glass",          Tier.INTERMEDIATE,          5, 0.0,   0,    0);
        r("aluminium",      Tier.INTERMEDIATE,          5, 0.0,   0,    0);
        r("battery",        Tier.ADVANCED_INTERMEDIATE, 3, 0.0,   0,    0);
        r("rubber_compound",Tier.INTERMEDIATE,          4, 0.003, 0,    0);
        r("canned_food",    Tier.INTERMEDIATE,          4, 0.002, 4.5,  5);

        // ── Consumer goods ──────────────────────────────────────────────────
        r("car",      Tier.CONSUMER, 1, 0.0, 35000, 45);
        r("phone",    Tier.CONSUMER, 2, 0.0, 899,   20);
        r("clothing", Tier.CONSUMER, 3, 0.0, 80,    10);
        r("furniture",Tier.CONSUMER, 2, 0.0, 500,   15);
        r("laptop",   Tier.CONSUMER, 2, 0.0, 1200,  20);
        r("bicycle",  Tier.CONSUMER, 2, 0.0, 150,   10);
        r("jewelry",  Tier.CONSUMER, 1, 0.0, 2500,  30);

        // ── Recipes ─────────────────────────────────────────────────────────
        // Raw resources have no recipes — they produce unconditionally.

        recipe("bread",          1, Map.of("wheat", 2));
        recipe("steel",          1, Map.of("iron", 2, "coal", 1));
        recipe("plastic",        2, Map.of("petrol", 2));
        recipe("circuit",        1, Map.of("silicon", 2, "copper", 1));
        recipe("fabric",         2, Map.of("cotton", 3));
        recipe("lumber",         2, Map.of("timber", 2));
        recipe("glass",          1, Map.of("silicon", 2, "coal", 1));
        recipe("aluminium",      2, Map.of("bauxite", 3));
        recipe("battery",        1, Map.of("lithium", 2, "aluminium", 1));
        recipe("rubber_compound",1, Map.of("rubber", 2, "petrol", 1));
        recipe("canned_food",    1, Map.of("wheat", 1));

        recipe("car",      1, Map.of("steel", 4, "rubber_compound", 2, "glass", 2, "circuit", 2, "battery", 1));
        recipe("phone",    1, Map.of("circuit", 1, "glass", 1, "battery", 1, "aluminium", 1));
        recipe("clothing", 1, Map.of("fabric", 2));
        recipe("furniture",1, Map.of("lumber", 3, "fabric", 1));
        recipe("laptop",   1, Map.of("circuit", 2, "aluminium", 1, "battery", 1, "plastic", 1));
        recipe("bicycle",  1, Map.of("steel", 2, "rubber_compound", 1));
        recipe("jewelry",  1, Map.of("gold", 2, "glass", 1));
    }

    private static void r(String name, Tier tier, int prod, double decay, double basePrice, int cycleMins) {
        RESOURCES.put(name, new Resource(name, tier, prod, decay, basePrice, cycleMins));
    }

    private static void recipe(String output, int qty, Map<String, Integer> inputs) {
        RECIPES.put(output, new Recipe(output, qty, new LinkedHashMap<>(inputs)));
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public static Resource get(String name)           { return RESOURCES.get(name); }
    public static Recipe   getRecipe(String name)     { return RECIPES.get(name); }
    public static Collection<Resource> allResources() { return RESOURCES.values(); }
    public static boolean  exists(String name)        { return RESOURCES.containsKey(name); }
    public static Map<String, Resource> allAsMap()    { return Collections.unmodifiableMap(RESOURCES); }
}
