# Trade Empire — Phase 2 Implementation Guide

> **Core Economy**: Resources, Facilities, Market, Consumer Sales, Fees, Decay

---

## Overview

Phase 2 is where the game becomes playable. After Phase 1's invisible infrastructure, Phase 2 adds everything players actually interact with: building factories, trading resources, and watching prices move.

**Estimated difficulty:** High (Perlin noise demand, order matching engine)
**Estimated duration:** 3–5 weeks for all components
**Critical order within Phase 2:**

```
3.1 (Resources/Recipes) → 3.2 (Facility Management) → 3.4 (Operating Costs in tick engine)
                                                      ↓
7.1 (Order Matching) → 7.2/7.3 (Reserve/Target) → 7.4 (Price Charts)
                                                      ↓
4.3 (Perlin Demand) → 4.4 (Saturation) → 4.5 (Demand Forecast)
                                                      ↓
12.1/12.2 (Fees + Taxes) → 13.1 (Decay)
```

Resources must exist before facilities can be built; operating costs require tick engine from Phase 1; market requires resources; shops require consumer goods from resources.

---

## 3.1 Resource & Recipe System

### What It Does

Defines the 32 resources and their production recipes. Resources are the atoms of the economy — everything else is built on top of them.

**Resource Tiers:**

| Tier | Count | Examples |
|------|-------|---------|
| Raw | 12 | Wheat, Iron, Copper, Gold, Petrol, Cotton, Timber, Lithium, Rubber, Silicon, Bauxite, Coal |
| Intermediate | 11 | Bread, Steel, Plastic, Circuit, Fabric, Lumber, Glass, Aluminium, Battery, Rubber Compound, Canned Food |
| Consumer | 9 | Car, Phone, Clothing, Furniture, Laptop, Bicycle, Jewelry, Bread*, Canned Food* |

*(Bread and Canned Food are both intermediate goods and consumer goods — they can be sold directly to consumers.)

### Implementation

#### 1. Define Resources in Code (Not Database)

Resources are static game data, not user-generated data. They don't need to live in the database. Store them in code as an enum or static map. This makes the code simpler and avoids a database round-trip every tick.

```java
package com.tradeempire.simulation;

import java.util.*;

public class ResourceRegistry {

    public enum Tier { RAW, INTERMEDIATE, ADVANCED_INTERMEDIATE, CONSUMER }

    public static class Resource {
        public final String name;
        public final Tier tier;
        public final double buildCost;
        public final double operatingCostPerTick;
        public final int productionPerTick; // Units produced per tick when active
        public final double decayRatePerCycle; // 0 = no decay; 0.01 = 1% per 60 ticks
        public final double basePrice; // Consumer goods only; 0 for non-consumer
        public final int demandCycleMinutes; // Consumer goods only

        public Resource(String name, Tier tier, int productionPerTick, double decayRatePerCycle,
                        double basePrice, int demandCycleMinutes) {
            this.name = name;
            this.tier = tier;
            this.productionPerTick = productionPerTick;
            this.decayRatePerCycle = decayRatePerCycle;
            this.basePrice = basePrice;
            this.demandCycleMinutes = demandCycleMinutes;

            // Derive costs from tier
            switch (tier) {
                case RAW:                   buildCost = 100;  operatingCostPerTick = 2.0;  break;
                case INTERMEDIATE:          buildCost = 300;  operatingCostPerTick = 5.0;  break;
                case ADVANCED_INTERMEDIATE: buildCost = 800;  operatingCostPerTick = 12.0; break;
                case CONSUMER:              buildCost = 2000; operatingCostPerTick = 30.0; break;
                default:                    buildCost = 100;  operatingCostPerTick = 2.0;  break;
            }
        }

        public boolean isConsumerGood() {
            return tier == Tier.CONSUMER || name.equals("bread") || name.equals("canned_food");
        }

        public boolean isPerishable() {
            return decayRatePerCycle > 0;
        }
    }

    public static class Recipe {
        public final String outputResource;
        public final int outputQuantity;
        public final Map<String, Integer> inputs; // resource name -> qty consumed per tick

        public Recipe(String outputResource, int outputQuantity, Map<String, Integer> inputs) {
            this.outputResource = outputResource;
            this.outputQuantity = outputQuantity;
            this.inputs = inputs;
        }
    }

    // --- Static Registry ---

    private static final Map<String, Resource> RESOURCES = new LinkedHashMap<>();
    private static final Map<String, Recipe> RECIPES = new LinkedHashMap<>();

    static {
        // ---- Raw Resources ---- (no inputs, always produce at capacity)
        register(new Resource("wheat",   Tier.RAW, 10, 0.01,  0,  0));
        register(new Resource("iron",    Tier.RAW, 8,  0.0,   0,  0));
        register(new Resource("copper",  Tier.RAW, 6,  0.0,   0,  0));
        register(new Resource("gold",    Tier.RAW, 2,  0.0,   0,  0));
        register(new Resource("petrol",  Tier.RAW, 5,  0.005, 0,  0));
        register(new Resource("cotton",  Tier.RAW, 8,  0.005, 0,  0));
        register(new Resource("timber",  Tier.RAW, 10, 0.0,   0,  0));
        register(new Resource("lithium", Tier.RAW, 3,  0.0,   0,  0));
        register(new Resource("rubber",  Tier.RAW, 5,  0.003, 0,  0));
        register(new Resource("silicon", Tier.RAW, 4,  0.0,   0,  0));
        register(new Resource("bauxite", Tier.RAW, 7,  0.0,   0,  0));
        register(new Resource("coal",    Tier.RAW, 9,  0.0,   0,  0));

        // ---- Intermediate Resources ----
        register(new Resource("bread",          Tier.INTERMEDIATE,          5,  0.02,  4.0,  5));
        register(new Resource("steel",          Tier.INTERMEDIATE,          4,  0.0,   0,    0));
        register(new Resource("plastic",        Tier.INTERMEDIATE,          6,  0.0,   0,    0));
        register(new Resource("circuit",        Tier.ADVANCED_INTERMEDIATE, 3,  0.0,   0,    0));
        register(new Resource("fabric",         Tier.INTERMEDIATE,          5,  0.0,   0,    0));
        register(new Resource("lumber",         Tier.INTERMEDIATE,          6,  0.0,   0,    0));
        register(new Resource("glass",          Tier.INTERMEDIATE,          5,  0.0,   0,    0));
        register(new Resource("aluminium",      Tier.INTERMEDIATE,          5,  0.0,   0,    0));
        register(new Resource("battery",        Tier.ADVANCED_INTERMEDIATE, 3,  0.0,   0,    0));
        register(new Resource("rubber_compound",Tier.INTERMEDIATE,          4,  0.003, 0,    0));
        register(new Resource("canned_food",    Tier.INTERMEDIATE,          4,  0.002, 4.5,  5));

        // ---- Consumer Goods ----
        register(new Resource("car",      Tier.CONSUMER, 1, 0.0, 35000, 45));
        register(new Resource("phone",    Tier.CONSUMER, 2, 0.0, 899,   20));
        register(new Resource("clothing", Tier.CONSUMER, 3, 0.0, 80,    10));
        register(new Resource("furniture",Tier.CONSUMER, 2, 0.0, 500,   15));
        register(new Resource("laptop",   Tier.CONSUMER, 2, 0.0, 1200,  20));
        register(new Resource("bicycle",  Tier.CONSUMER, 2, 0.0, 150,   10));
        register(new Resource("jewelry",  Tier.CONSUMER, 1, 0.0, 2500,  30));

        // ---- Recipes ----
        // Raw resources have no inputs
        addRecipe("bread",          1, Map.of("wheat", 2));
        addRecipe("steel",          1, Map.of("iron", 2, "coal", 1));
        addRecipe("plastic",        2, Map.of("petrol", 2));
        addRecipe("circuit",        1, Map.of("silicon", 2, "copper", 1));
        addRecipe("fabric",         2, Map.of("cotton", 3));
        addRecipe("lumber",         2, Map.of("timber", 2));
        addRecipe("glass",          1, Map.of("silicon", 2, "coal", 1));
        addRecipe("aluminium",      2, Map.of("bauxite", 3));
        addRecipe("battery",        1, Map.of("lithium", 2, "aluminium", 1));
        addRecipe("rubber_compound",1, Map.of("rubber", 2, "petrol", 1));
        addRecipe("canned_food",    1, Map.of("wheat", 1));

        addRecipe("car",      1, Map.of("steel", 4, "rubber_compound", 2, "glass", 2, "circuit", 2, "battery", 1));
        addRecipe("phone",    1, Map.of("circuit", 1, "glass", 1, "battery", 1, "aluminium", 1));
        addRecipe("clothing", 1, Map.of("fabric", 2));
        addRecipe("furniture",1, Map.of("lumber", 3, "fabric", 1));
        addRecipe("laptop",   1, Map.of("circuit", 2, "aluminium", 1, "battery", 1, "plastic", 1));
        addRecipe("bicycle",  1, Map.of("steel", 2, "rubber_compound", 1));
        addRecipe("jewelry",  1, Map.of("gold", 2, "glass", 1));
    }

    private static void register(Resource r) { RESOURCES.put(r.name, r); }
    private static void addRecipe(String output, int qty, Map<String, Integer> inputs) {
        RECIPES.put(output, new Recipe(output, qty, inputs));
    }

    public static Resource get(String name) { return RESOURCES.get(name); }
    public static Recipe getRecipe(String name) { return RECIPES.get(name); }
    public static Collection<Resource> allResources() { return RESOURCES.values(); }
    public static boolean exists(String name) { return RESOURCES.containsKey(name); }
}
```

### Pitfalls & Foresight

**Pitfall 1: Recipes in the database**
- ❌ Bad: Recipes in a `recipes` table with joins on every production tick.
- ✅ Good: Static in-code. A recipe never changes at runtime — it changes when you ship a new version.
- Exception: if you want to let admins configure recipes live, add a config override layer on top.

**Pitfall 2: Tier costs hardcoded per resource**
- ❌ Bad: Hard-code `buildCost = 100` per resource.
- ✅ Good: Derive from `ResourceRegistry.Tier` + ConfigManager. One config change adjusts all raw resources.

**Pitfall 3: "Consumer good" ambiguity**
- Bread and Canned Food are both intermediate (used in recipes) AND consumer goods (sold in shops).
- Flag them with `isConsumerGood()` logic — don't rely purely on `Tier`.

**Foresight for Phase 3 (Market):**
- Market endpoints will accept `resource_name` strings. Validate against `ResourceRegistry.exists()`.
- Prevents: "I'm selling 100 units of 'unobtainium' at $9999" exploits.

**Foresight for Phase 6 (AI Integration):**
- Expose the full resource list via `/api/v1/config` so AI agents can read it without hardcoding.
- Expose recipes similarly — AI agents need to know what inputs Steel requires.

---

## 3.2 Facility Management + 3.4 Operating Costs

### What It Does

Players build facilities to produce resources. Each facility is a state machine (Active ↔ Idle). Active facilities produce output and cost money every tick. Idle facilities produce nothing but cost 30% maintenance. Downsize permanently removes and refunds 40%.

### Implementation: Tick Engine Step 1 & 2

Fill in the stubbed steps in `TickEngine.java` from Phase 1:

```java
private void deductOperatingCosts(Connection conn) throws Exception {
    ConfigManager config = ConfigManager.getInstance();
    double idleMultiplier = config.getDouble("facility.idle_cost_multiplier", 0.30);

    // Collect operating cost per player in a single pass
    String fetchSql = """
        SELECT f.id, f.player_id, f.state, r.tier
        FROM facilities f
        WHERE f.state IN ('active', 'idle')
        ORDER BY f.player_id
        """;

    Map<Integer, Double> totalCostByPlayer = new HashMap<>();
    Map<Integer, List<FacilityRef>> facilitiesByPlayer = new HashMap<>();

    try (PreparedStatement ps = conn.prepareStatement(fetchSql);
         ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            int facilityId = rs.getInt("id");
            int playerId = rs.getInt("player_id");
            String state = rs.getString("state");
            String tier = rs.getString("tier");

            double baseCost = getOperatingCost(tier);
            double actualCost = state.equals("idle") ? baseCost * idleMultiplier : baseCost;

            totalCostByPlayer.merge(playerId, actualCost, Double::sum);
            facilitiesByPlayer.computeIfAbsent(playerId, k -> new ArrayList<>())
                               .add(new FacilityRef(facilityId, actualCost));
        }
    }

    // Deduct in bulk per player
    String updateSql = "UPDATE players SET cash = cash - ? WHERE id = ?";
    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
        for (Map.Entry<Integer, Double> entry : totalCostByPlayer.entrySet()) {
            ps.setDouble(1, entry.getValue());
            ps.setInt(2, entry.getKey());
            ps.addBatch();
        }
        ps.executeBatch();
    }
}

private void runProduction(Connection conn) throws Exception {
    String fetchSql = """
        SELECT f.id, f.player_id, f.resource_name, f.production_capacity
        FROM facilities f
        WHERE f.state = 'active'
        """;

    try (PreparedStatement ps = conn.prepareStatement(fetchSql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {
            int facilityId = rs.getInt("id");
            int playerId = rs.getInt("player_id");
            String resourceName = rs.getString("resource_name");
            int capacity = rs.getInt("production_capacity");

            Recipe recipe = ResourceRegistry.getRecipe(resourceName);

            if (recipe == null) {
                // Raw resource — no inputs required, always produces at capacity
                addToInventory(conn, playerId, resourceName, capacity);
                continue;
            }

            // Check if all inputs are available
            int actualOutput = calculateActualOutput(conn, playerId, recipe, capacity);
            if (actualOutput <= 0) continue;

            // Consume inputs proportionally
            for (Map.Entry<String, Integer> input : recipe.inputs.entrySet()) {
                int consume = (int)(input.getValue() * ((double)actualOutput / recipe.outputQuantity));
                deductFromInventory(conn, playerId, input.getKey(), consume);
            }

            // Add output
            addToInventory(conn, playerId, resourceName, actualOutput);
        }
    }
}

private int calculateActualOutput(Connection conn, int playerId, Recipe recipe, int capacity) throws Exception {
    // For each input, calculate max production given current stock
    int maxOutput = capacity;

    for (Map.Entry<String, Integer> input : recipe.inputs.entrySet()) {
        double stock = getInventoryQty(conn, playerId, input.getKey());
        // How many ticks worth of this input do we have?
        int inputLimitedOutput = (int)(stock / input.getValue()) * recipe.outputQuantity;
        maxOutput = Math.min(maxOutput, inputLimitedOutput);
    }

    return maxOutput;
}

private double getInventoryQty(Connection conn, int playerId, String resourceName) throws Exception {
    String sql = "SELECT quantity FROM inventory WHERE player_id = ? AND resource_name = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, playerId);
        ps.setString(2, resourceName);
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble("quantity") : 0.0;
        }
    }
}

private void addToInventory(Connection conn, int playerId, String resourceName, double qty) throws Exception {
    String sql = """
        INSERT INTO inventory (player_id, resource_name, quantity)
        VALUES (?, ?, ?)
        ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)
        """;
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, playerId);
        ps.setString(2, resourceName);
        ps.setDouble(3, qty);
        ps.executeUpdate();
    }
}

private void deductFromInventory(Connection conn, int playerId, String resourceName, double qty) throws Exception {
    String sql = "UPDATE inventory SET quantity = GREATEST(0, quantity - ?) WHERE player_id = ? AND resource_name = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setDouble(1, qty);
        ps.setInt(2, playerId);
        ps.setString(3, resourceName);
        ps.executeUpdate();
    }
}

private double getOperatingCost(String tier) {
    ConfigManager config = ConfigManager.getInstance();
    return switch (tier) {
        case "raw"                   -> config.getDouble("facility.operating_cost.raw", 2.0);
        case "intermediate"          -> config.getDouble("facility.operating_cost.intermediate", 5.0);
        case "advanced_intermediate" -> config.getDouble("facility.operating_cost.advanced_intermediate", 12.0);
        case "consumer"              -> config.getDouble("facility.operating_cost.consumer", 30.0);
        default -> 2.0;
    };
}
```

### Implementation: API Endpoints

Create `ProductionServlet.java`:

```java
@WebServlet("/api/v1/production/*")
public class ProductionServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws Exception {
        response.setContentType("application/json");
        int playerId = (int) request.getAttribute("playerId");

        String action = request.getPathInfo(); // /build, /idle, /activate, /downsize

        JSONObject body = parseBody(request);
        JSONObject result = new JSONObject();

        try (Connection conn = DatabaseProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                switch (action) {
                    case "/build"    -> result = handleBuild(conn, playerId, body);
                    case "/idle"     -> result = handleIdle(conn, playerId, body);
                    case "/activate" -> result = handleActivate(conn, playerId, body);
                    case "/downsize" -> result = handleDownsize(conn, playerId, body);
                    default          -> throw new IllegalArgumentException("Unknown action: " + action);
                }
                conn.commit();
                response.getWriter().write(result.toJSONString());
            } catch (Exception e) {
                conn.rollback();
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JSONObject err = new JSONObject();
                err.put("error", e.getMessage());
                response.getWriter().write(err.toJSONString());
            }
        }
    }

    private JSONObject handleBuild(Connection conn, int playerId, JSONObject body) throws Exception {
        String resourceName = (String) body.get("resource");
        if (!ResourceRegistry.exists(resourceName)) {
            throw new IllegalArgumentException("unknown_resource");
        }

        Resource resource = ResourceRegistry.get(resourceName);
        double buildCost = resource.buildCost;

        // Check player cash
        double playerCash = getPlayerCash(conn, playerId);
        if (playerCash < buildCost) {
            throw new IllegalStateException("insufficient_cash");
        }

        // Deduct cash
        String deductSql = "UPDATE players SET cash = cash - ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(deductSql)) {
            ps.setDouble(1, buildCost);
            ps.setInt(2, playerId);
            ps.executeUpdate();
        }

        // Insert facility
        String insertSql = """
            INSERT INTO facilities (player_id, resource_name, state, production_capacity)
            VALUES (?, ?, 'active', ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, playerId);
            ps.setString(2, resourceName);
            ps.setInt(3, resource.productionPerTick);
            ps.executeUpdate();

            long facilityId;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                facilityId = keys.getLong(1);
            }

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("facilityId", facilityId);
            result.put("cost", buildCost);
            return result;
        }
    }

    private JSONObject handleDownsize(Connection conn, int playerId, JSONObject body) throws Exception {
        long facilityId = (long) body.get("facilityId");
        ConfigManager config = ConfigManager.getInstance();
        double refundRate = config.getDouble("facility.downsize_refund_rate", 0.40);

        // Verify ownership + get resource
        String sql = "SELECT resource_name, state FROM facilities WHERE id = ? AND player_id = ?";
        String resourceName;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, facilityId);
            ps.setInt(2, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("facility_not_found");
                resourceName = rs.getString("resource_name");
            }
        }

        Resource resource = ResourceRegistry.get(resourceName);
        double refund = resource.buildCost * refundRate;

        // Refund cash + mark as destroyed
        conn.prepareStatement("UPDATE players SET cash = cash + " + refund + " WHERE id = " + playerId).executeUpdate();
        conn.prepareStatement("UPDATE facilities SET state = 'destroyed' WHERE id = " + facilityId).executeUpdate();

        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("refund", refund);
        return result;
    }
}
```

### Pitfalls & Foresight

**Pitfall 1: Race condition on cash deduction**
- ❌ Bad: Read cash → check if enough → update cash. Two concurrent requests both read $200, both see "enough", both deduct.
- ✅ Good: `UPDATE players SET cash = cash - ? WHERE id = ? AND cash >= ?` — let the WHERE clause be the guard. Check rows affected = 1.

**Pitfall 2: Operating costs deducted individually per facility**
- ❌ Bad: 50 players × 20 facilities = 1000 individual UPDATE statements per tick.
- ✅ Good: Aggregate costs per player, then one UPDATE per player. 50 statements instead of 1000.

**Pitfall 3: Ignoring `GREATEST(0, quantity - ?)`**
- Without this, inventory can go negative. Use `GREATEST()` as a DB-level safeguard even if your application logic is correct.

**Foresight for Phase 3 (Market):**
- Operating costs create a cash drain that drives trading: players *must* sell to stay solvent.
- This is the economic pressure that makes the market interesting.
- Don't reduce costs too early in tuning — let players feel the squeeze.

---

## 7.1–7.4 Market System

### What It Does

A proper exchange: players post buy and sell orders; the system matches them by price-time priority. Price history is recorded for charts.

### Key Concepts

**Price-time priority:**
- Cheapest sell offers match first
- Highest buy bids match first
- Ties are broken by who posted earliest (time priority)
- This is the standard stock exchange mechanic

**Reserve (sell orders):** "Keep 100 Wheat in my inventory, sell everything above that at $2."
- Actual quantity for sale = `max(0, current_inventory - reserve_floor)`
- Recalculated every tick

**Target (buy orders):** "I want to hold 500 Iron total. Buy up to (500 - current_stock) at $3 max."
- Actual quantity to buy = `max(0, target - current_stock)`
- Recalculated every tick

### Implementation: Order Matching Engine

Fill in `TickEngine.matchMarketOrders()`:

```java
private void matchMarketOrders(Connection conn) throws Exception {
    // Get all unique resources with open orders
    List<String> activeResources;
    String resourcesSql = "SELECT DISTINCT resource_name FROM market_orders";
    try (PreparedStatement ps = conn.prepareStatement(resourcesSql);
         ResultSet rs = ps.executeQuery()) {
        activeResources = new ArrayList<>();
        while (rs.next()) activeResources.add(rs.getString("resource_name"));
    }

    for (String resource : activeResources) {
        matchResource(conn, resource);
    }
}

private void matchResource(Connection conn, String resourceName) throws Exception {
    // Fetch sell orders: cheapest first, oldest first on ties
    List<Order> sells = fetchOrders(conn, resourceName, "sell");
    // Fetch buy orders: highest price first, oldest first on ties
    List<Order> buys = fetchOrders(conn, resourceName, "buy");

    // Recalculate effective quantities (accounting for reserve/target)
    for (Order sell : sells) {
        if (sell.keepReserve > 0) {
            double currentStock = getInventoryQty(conn, sell.playerId, resourceName);
            sell.effectiveQty = (int) Math.max(0, currentStock - sell.keepReserve);
        } else {
            sell.effectiveQty = sell.quantity - sell.quantityFilled;
        }
    }

    for (Order buy : buys) {
        if (buy.targetQty > 0) {
            double currentStock = getInventoryQty(conn, buy.playerId, resourceName);
            buy.effectiveQty = (int) Math.max(0, buy.targetQty - currentStock);
        } else {
            buy.effectiveQty = buy.quantity - buy.quantityFilled;
        }
    }

    // Matching loop
    ConfigManager config = ConfigManager.getInstance();
    double marketFeeRate = config.getDouble("economy.market_fee_percent", 2.0) / 100.0;

    int si = 0, bi = 0;
    int totalVolume = 0;
    double lastPrice = 0;

    while (si < sells.size() && bi < buys.size()) {
        Order sell = sells.get(si);
        Order buy = buys.get(bi);

        // Skip empty orders
        if (sell.effectiveQty <= 0) { si++; continue; }
        if (buy.effectiveQty <= 0) { bi++; continue; }

        // Check if prices overlap (buyer willing to pay >= seller asking)
        if (buy.price < sell.price) break; // No more matches possible

        // Determine trade quantity and price
        int tradeQty = Math.min(sell.effectiveQty, buy.effectiveQty);
        double tradePrice = sell.price; // Execute at seller's ask (could also be midpoint)

        // Calculate fees (1% each side)
        double sellerRevenue = tradePrice * tradeQty * (1.0 - marketFeeRate / 2.0);
        double buyerCost = tradePrice * tradeQty * (1.0 + marketFeeRate / 2.0);
        double feeRevenue = tradePrice * tradeQty * marketFeeRate;

        // Execute trade: transfer inventory and cash
        deductFromInventory(conn, sell.playerId, resourceName, tradeQty);
        addToInventory(conn, buy.playerId, resourceName, tradeQty);

        updateCash(conn, sell.playerId, sellerRevenue);
        updateCash(conn, buy.playerId, -buyerCost);

        // Add fee to central bank
        addToCentralBank(conn, "market_fee_reserve", feeRevenue);

        // Update filled quantities
        sell.effectiveQty -= tradeQty;
        buy.effectiveQty -= tradeQty;
        sell.quantityFilled += tradeQty;
        buy.quantityFilled += tradeQty;

        updateOrderFilled(conn, sell.id, sell.quantityFilled);
        updateOrderFilled(conn, buy.id, buy.quantityFilled);

        totalVolume += tradeQty;
        lastPrice = tradePrice;

        // Advance exhausted orders
        if (sell.effectiveQty <= 0) si++;
        if (buy.effectiveQty <= 0) bi++;
    }

    // Record price history
    if (totalVolume > 0) {
        recordPriceHistory(conn, resourceName, lastPrice, lastPrice, totalVolume);
    }
}

private List<Order> fetchOrders(Connection conn, String resourceName, String side) throws Exception {
    String sql = side.equals("sell")
        ? "SELECT id, player_id, price, quantity, quantity_filled, keep_reserve FROM market_orders WHERE resource_name = ? AND side = 'sell' ORDER BY price ASC, created_at ASC"
        : "SELECT id, player_id, price, quantity, quantity_filled, target_quantity FROM market_orders WHERE resource_name = ? AND side = 'buy' ORDER BY price DESC, created_at ASC";

    List<Order> orders = new ArrayList<>();
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, resourceName);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Order o = new Order();
                o.id = rs.getInt("id");
                o.playerId = rs.getInt("player_id");
                o.price = rs.getDouble("price");
                o.quantity = rs.getInt("quantity");
                o.quantityFilled = rs.getInt("quantity_filled");
                o.keepReserve = side.equals("sell") ? rs.getInt("keep_reserve") : 0;
                o.targetQty = side.equals("buy") ? rs.getInt("target_quantity") : 0;
                o.effectiveQty = o.quantity - o.quantityFilled;
                orders.add(o);
            }
        }
    }
    return orders;
}

private void recordPriceHistory(Connection conn, String resource, double buyPrice, double sellPrice, int volume) throws Exception {
    String sql = """
        INSERT INTO price_history (resource_name, tick_number, buy_price, sell_price, volume_traded)
        VALUES (?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE buy_price = ?, sell_price = ?, volume_traded = volume_traded + ?
        """;
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, resource);
        ps.setInt(2, currentTick);
        ps.setDouble(3, buyPrice);
        ps.setDouble(4, sellPrice);
        ps.setInt(5, volume);
        ps.setDouble(6, buyPrice);
        ps.setDouble(7, sellPrice);
        ps.setInt(8, volume);
        ps.executeUpdate();
    }
}
```

### Pitfalls & Foresight

**Pitfall 1: Loading entire order book into memory**
- ❌ Bad: SELECT all market orders, load into Java, sort in Java.
- ✅ Good: Use ORDER BY in SQL, stream results. The database sorts much faster.
- For 10,000 orders across 32 resources, in-memory sorting is fine. For 1M+ orders, stream in chunks.

**Pitfall 2: Matching same player to themselves**
- If Alice has both a buy and sell order for the same resource, don't self-match.
- Add `AND sell.player_id != buy.player_id` to prevent it.

**Pitfall 3: Floating-point rounding errors in cash**
- `tradePrice * tradeQty * 0.99` introduces float imprecision at scale.
- Use `BigDecimal` for financial calculations, convert to `double` only for DB inserts.

**Pitfall 4: Not recording price history when no trades occur**
- The chart gaps look bad and break trend analysis.
- Insert a no-trade record each tick (or store "last known price" and repeat it).

**Foresight for Phase 4 (UI):**
- Price history chart needs clean tick-to-time conversion: `timestamp = server_start + tick * tick_period_ms`
- Store this in the API response, not just tick numbers. Clients can't reliably calculate time from ticks alone.

---

## 4.3–4.5 Consumer Pricing: Perlin Noise Demand

### What It Does

Consumer good prices are not fixed — they oscillate organically over time via Perlin noise. This creates boom/bust cycles that players can anticipate and exploit.

```
Effective Price Cap = Base Price × Demand Multiplier × Saturation Penalty
Demand Multiplier = Perlin noise → 0.7x to 1.4x
Saturation Penalty = decreases per unit sold in current cycle
```

### Why Perlin Noise (Not Random)

Pure random produces jagged, unpredictable prices. Perlin noise is **smooth** — adjacent values are correlated. This creates realistic "cycles" that rise and fall naturally.

Multi-octave Perlin adds variation at different scales: a slow broad trend (hourly) with smaller faster fluctuations layered on top.

### Implementation

#### 1. Add PerlinNoise to your project

Java doesn't have a built-in Perlin implementation. Use a simple 1D version:

```java
package com.tradeempire.simulation;

import java.util.Random;

/**
 * 1D Perlin noise generator for demand curves.
 * Each resource gets its own instance with a unique seed, so their cycles are independent.
 */
public class PerlinNoise {
    private final int[] permutation = new int[512];

    public PerlinNoise(long seed) {
        Random rng = new Random(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        // Shuffle
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
        }
        for (int i = 0; i < 512; i++) permutation[i] = p[i & 255];
    }

    private double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private double lerp(double t, double a, double b) { return a + t * (b - a); }
    private double grad(int hash, double x) {
        return (hash & 1) == 0 ? x : -x;
    }

    public double noise(double x) {
        int X = (int) Math.floor(x) & 255;
        x -= Math.floor(x);
        double u = fade(x);
        return lerp(u, grad(permutation[X], x), grad(permutation[X + 1], x - 1));
    }

    /**
     * Multi-octave noise: combines multiple frequencies.
     * @param x   Input value (time)
     * @param octaves Number of octave layers
     * @param persistence Amplitude drop-off per octave (0.5 = each octave is half as loud)
     * @param lacunarity Frequency increase per octave (2.0 = doubles each time)
     */
    public double octaveNoise(double x, int octaves, double persistence, double lacunarity) {
        double total = 0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double maxValue = 0;

        for (int i = 0; i < octaves; i++) {
            total += noise(x * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }

        return total / maxValue; // Normalize to [-1, 1]
    }
}
```

#### 2. DemandEngine — manages per-resource demand state

```java
package com.tradeempire.simulation;

import com.tradeempire.config.ConfigManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DemandEngine {
    private static DemandEngine instance;

    // Per-resource Perlin generators (seeded with hash of resource name so each is unique)
    private final Map<String, PerlinNoise> noiseGenerators = new ConcurrentHashMap<>();

    // Per-resource saturation: units sold this cycle
    private final Map<String, Integer> saturationCounters = new ConcurrentHashMap<>();

    // Per-resource cycle tick: tick when current cycle started
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
     * Get the current demand multiplier for a consumer good.
     * Returns 1.0 for non-consumer goods.
     */
    public double getDemandMultiplier(String resourceName, int currentTick) {
        ResourceRegistry.Resource resource = ResourceRegistry.get(resourceName);
        if (resource == null || !resource.isConsumerGood()) return 1.0;

        ConfigManager config = ConfigManager.getInstance();
        int octaves = config.getInt("perlin.base_octaves", 6);
        double minMultiplier = config.getDouble("perlin.demand_min_multiplier", 0.7);
        double maxMultiplier = config.getDouble("perlin.demand_max_multiplier", 1.4);

        // x input: slowly advance through noise field at pace of the resource's cycle
        int cycleMinutes = resource.demandCycleMinutes;
        double ticksPerMinute = config.getDouble("simulation.ticks_per_second", 4.0) * 60;
        double ticksPerCycle = cycleMinutes * ticksPerMinute;

        // Advance slowly — one full noise "period" per demand cycle
        double x = currentTick / ticksPerCycle;

        PerlinNoise noise = noiseGenerators.computeIfAbsent(
            resourceName,
            name -> new PerlinNoise(name.hashCode())
        );

        double raw = noise.octaveNoise(x, octaves, 0.5, 2.0); // raw in [-1, 1]
        double normalized = (raw + 1.0) / 2.0; // normalize to [0, 1]

        // Scale to [minMultiplier, maxMultiplier]
        return minMultiplier + normalized * (maxMultiplier - minMultiplier);
    }

    /**
     * Get saturation penalty (decreases price as more units sell per cycle).
     * Returns 1.0 at zero saturation, approaches 0.5 as saturation increases.
     */
    public double getSaturationPenalty(String resourceName) {
        int sold = saturationCounters.getOrDefault(resourceName, 0);
        // Penalty: each unit sold reduces price cap by 0.1%, minimum 0.5x
        double penalty = 1.0 - (sold * 0.001);
        return Math.max(0.5, penalty);
    }

    /**
     * Record that units were sold this cycle (update saturation).
     */
    public void recordSale(String resourceName, int quantity) {
        saturationCounters.merge(resourceName, quantity, Integer::sum);
    }

    /**
     * Reset saturation counters for a resource (called at cycle end).
     */
    public void resetCycle(String resourceName, int currentTick) {
        saturationCounters.put(resourceName, 0);
        cycleStartTicks.put(resourceName, currentTick);
    }

    /**
     * Check if a demand cycle has completed and reset if needed.
     */
    public void updateCycles(int currentTick) {
        ConfigManager config = ConfigManager.getInstance();
        double ticksPerSecond = config.getDouble("simulation.ticks_per_second", 4.0);

        for (ResourceRegistry.Resource resource : ResourceRegistry.allResources()) {
            if (!resource.isConsumerGood()) continue;

            int cycleStartTick = cycleStartTicks.getOrDefault(resource.name, 0);
            double ticksPerCycle = resource.demandCycleMinutes * 60 * ticksPerSecond;

            if (currentTick - cycleStartTick >= ticksPerCycle) {
                resetCycle(resource.name, currentTick);
            }
        }
    }

    /**
     * Effective price cap for a consumer good.
     */
    public double getEffectivePriceCap(String resourceName, int currentTick) {
        ResourceRegistry.Resource resource = ResourceRegistry.get(resourceName);
        if (resource == null || !resource.isConsumerGood()) return 0;

        double multiplier = getDemandMultiplier(resourceName, currentTick);
        double saturation = getSaturationPenalty(resourceName);
        return resource.basePrice * multiplier * saturation;
    }

    /**
     * Returns data visible to the player: current multiplier + trend direction.
     * NOT the future value — just enough to make an educated guess.
     */
    public Map<String, Object> getDemandForecast(String resourceName, int currentTick) {
        double current = getDemandMultiplier(resourceName, currentTick);
        double future = getDemandMultiplier(resourceName, currentTick + 10);
        String trend = future > current + 0.01 ? "rising"
                     : future < current - 0.01 ? "falling"
                     : "stable";

        Map<String, Object> forecast = new HashMap<>();
        forecast.put("multiplier", Math.round(current * 100.0) / 100.0);
        forecast.put("trend", trend);
        forecast.put("priceCap", getEffectivePriceCap(resourceName, currentTick));
        return forecast;
    }
}
```

#### 3. Shop Sales in Tick Engine (Step 4)

```java
private void processShopSales(Connection conn) throws Exception {
    DemandEngine demand = DemandEngine.getInstance();
    demand.updateCycles(currentTick);

    ConfigManager config = ConfigManager.getInstance();
    double luxuryTaxRate = config.getDouble("economy.luxury_tax_percent", 3.0) / 100.0;

    String shopSql = """
        SELECT si.shop_id, si.resource_name, si.quantity, si.set_price, s.player_id
        FROM shop_inventory si
        JOIN shops s ON si.shop_id = s.id
        WHERE si.quantity > 0
        """;

    try (PreparedStatement ps = conn.prepareStatement(shopSql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {
            int shopId = rs.getInt("shop_id");
            int playerId = rs.getInt("player_id");
            String resourceName = rs.getString("resource_name");
            int stockQty = rs.getInt("quantity");
            double setPrice = rs.getDouble("set_price");

            double priceCap = demand.getEffectivePriceCap(resourceName, currentTick);

            // Only sell if player's price is within the demand cap
            if (setPrice > priceCap) continue;

            // NPC purchases 1-5 units per tick (simplified demand model)
            int npcDemand = 1 + (int)(Math.random() * 4);
            int soldQty = Math.min(npcDemand, stockQty);
            if (soldQty <= 0) continue;

            double revenue = setPrice * soldQty;
            double tax = revenue * luxuryTaxRate;
            double netRevenue = revenue - tax;

            // Update shop inventory
            String updateStock = "UPDATE shop_inventory SET quantity = quantity - ? WHERE shop_id = ? AND resource_name = ?";
            try (PreparedStatement ups = conn.prepareStatement(updateStock)) {
                ups.setInt(1, soldQty);
                ups.setInt(2, shopId);
                ups.setString(3, resourceName);
                ups.executeUpdate();
            }

            // Credit player
            updateCash(conn, playerId, netRevenue);
            addToCentralBank(conn, "luxury_tax_reserve", tax);

            // Record sale
            String saleSql = "INSERT INTO shop_sales (shop_id, resource_name, quantity_sold, price_per_unit, tick_number) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement sps = conn.prepareStatement(saleSql)) {
                sps.setInt(1, shopId);
                sps.setString(2, resourceName);
                sps.setInt(3, soldQty);
                sps.setDouble(4, setPrice);
                sps.setInt(5, currentTick);
                sps.executeUpdate();
            }

            demand.recordSale(resourceName, soldQty);
        }
    }
}
```

### Pitfalls & Foresight

**Pitfall 1: Perlin seed collision**
- If two resources share a seed, they'll have identical demand curves.
- ✅ Use `resourceName.hashCode()` as seed — fast, deterministic, unique per resource name.

**Pitfall 2: Saturation counter never resets**
- If you forget to reset the saturation counter at cycle end, prices drop to 0.5x and stay there forever.
- ✅ Call `demand.updateCycles(currentTick)` at the start of every shop sales step.

**Pitfall 3: Demand visible data = exact future price**
- Don't expose `getDemandMultiplier(tick + future)` in the API.
- Only expose trend direction (rising/falling/stable). Players should have *intelligence*, not *omniscience*.

**Pitfall 4: NPC demand too high**
- If NPCs buy 100 units per tick, players can't keep shops stocked. Economy floods with cash.
- Start NPC demand low and tune up. This is what the config system is for.

---

## 13.1 Resource Decay

### Implementation

Fill in `TickEngine.applyDecay()`:

```java
private void applyDecay(Connection conn) throws Exception {
    // Decay applies once every 60 ticks per resource
    // Only perishable resources

    for (ResourceRegistry.Resource resource : ResourceRegistry.allResources()) {
        if (!resource.isPerishable()) continue;

        double decayRate = resource.decayRatePerCycle; // e.g., 0.01 = 1%

        // Apply: quantity = quantity * (1 - decayRate)
        // Only for inventory rows where last_decay_tick is more than 60 ticks ago
        String sql = """
            UPDATE inventory
            SET quantity = quantity * ?,
                last_decay_tick = ?
            WHERE resource_name = ?
              AND last_decay_tick <= ?
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, 1.0 - decayRate);
            ps.setInt(2, currentTick);
            ps.setString(3, resource.name);
            ps.setInt(4, currentTick - 60);
            ps.executeUpdate();
        }
    }
}
```

### Pitfalls

**Pitfall 1: Decay on shop inventory too**
- Bread in a shop should also decay.
- Apply the same logic to `shop_inventory`.

**Pitfall 2: Very small quantities never reaching zero**
- 0.01 × 0.98^100 never equals 0 exactly.
- Add: `DELETE FROM inventory WHERE quantity < 0.001` after decay to clean up negligible amounts.

---

## Phase 2 Checklist

- [ ] `ResourceRegistry` — all 32 resources + recipes defined and tested
- [ ] `TickEngine` Step 1: operating costs deducted correctly per player
- [ ] `TickEngine` Step 2: production runs, inputs consumed, outputs added
- [ ] `TickEngine` Step 3: decay applied correctly at 60-tick interval
- [ ] `TickEngine` Step 4: shop sales process with luxury tax
- [ ] `TickEngine` Step 5: market orders matched by price-time priority
- [ ] `TickEngine` Step 6: auto-idle when player cash < 0
- [ ] `ProductionServlet` — build, idle, activate, downsize all work
- [ ] `MarketServlet` — post sell/buy orders, cancel, view orderbook
- [ ] `DemandEngine` — Perlin noise demand confirmed with test printout
- [ ] Price history recorded in DB after trades
- [ ] Market fee and luxury tax flowing to `central_bank`

---

## Next: Phase 3

Once Phase 2 is stable, you'll build:
- The MCP Server wrapping the REST API
- System AI Corporations with distinct strategies

See `IMPLEMENTATION_GUIDE_PHASE_3.md`
