package simulation;

import config.ConfigManager;
import database.DB;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tick Engine — the heartbeat of the Trade Empire simulation.
 *
 * Runs at a configurable tick rate (default: 4 ticks/sec = 250 ms intervals).
 * Each tick executes 6 ordered steps, all wrapped in a single DB transaction:
 *
 *   1. Deduct operating costs from all active/idle facilities
 *   2. Run production (consume inputs, produce outputs)
 *   3. Apply resource decay (spoilage)
 *   4. Process shop sales (NPC customers buy consumer goods)
 *   5. Match market orders (price-time priority order book)
 *   6. Auto-idle facilities for players with negative cash
 */
public class TickEngine {

    private static volatile TickEngine instance;

    private ScheduledExecutorService executor;
    private final AtomicInteger currentTick = new AtomicInteger(0);
    private volatile boolean running = false;

    private static final int CONFIG_RELOAD_INTERVAL = 100;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    private TickEngine() {}

    public static TickEngine getInstance() {
        if (instance == null) {
            synchronized (TickEngine.class) {
                if (instance == null) instance = new TickEngine();
            }
        }
        return instance;
    }

    public synchronized void start() {
        if (running) {
            System.out.println("[TICK] Already running");
            return;
        }

        ConfigManager config = ConfigManager.getInstance();
        int ticksPerSecond = config.getInt("simulation.ticks_per_second", 4);
        int poolSize       = config.getInt("simulation.thread_pool_size", 4);

        loadTickFromDatabase();

        executor = Executors.newScheduledThreadPool(poolSize);
        running  = true;

        long periodMs = 1000L / ticksPerSecond;
        executor.scheduleAtFixedRate(this::executeTick, 0, periodMs, TimeUnit.MILLISECONDS);

        System.out.println("[TICK] Started: " + ticksPerSecond + " ticks/sec (" + periodMs + "ms period), pool=" + poolSize);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        saveTickToDatabase();
        System.out.println("[TICK] Stopped at tick #" + currentTick.get());
    }

    /**
     * Reset the tick count to 0.
     * Used primarily for testing when the database is cleared.
     * Can only be called when engine is not running.
     */
    public synchronized void reset() {
        if (running) {
            System.err.println("[TICK] Cannot reset while running");
            return;
        }
        currentTick.set(0);
        System.out.println("[TICK] Reset tick count to 0");
    }

    // ── Main tick loop ───────────────────────────────────────────────────────

    private void executeTick() {
        int tick  = currentTick.incrementAndGet();
        long start = System.currentTimeMillis();

        if (tick % CONFIG_RELOAD_INTERVAL == 0) {
            ConfigManager config = ConfigManager.getInstance();
            if (config.hasChanged()) config.reload();
        }

        try (Connection conn = DB.connect()) {
            conn.setAutoCommit(false);
            try {
                step1DeductOperatingCosts(conn, tick);
                step2RunProduction(conn, tick);
                step3ApplyDecay(conn, tick);
                step4ProcessShopSales(conn, tick);
                step5MatchMarketOrders(conn, tick);
                step6AutoIdleFacilities(conn, tick);
                updateGameState(conn, tick);
                conn.commit();

                long duration = System.currentTimeMillis() - start;
                if (duration > 100) {
                    System.err.println("[TICK] Slow tick #" + tick + ": " + duration + "ms");
                }
            } catch (Exception e) {
                conn.rollback();
                System.err.println("[TICK] Tick #" + tick + " rolled back: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("[TICK] DB connection error on tick #" + tick + ": " + e.getMessage());
        }
    }

    // ── Step 1: Deduct operating costs ──────────────────────────────────────

    private void step1DeductOperatingCosts(Connection conn, int tick) throws Exception {
        ConfigManager cfg           = ConfigManager.getInstance();
        double idleMultiplier       = cfg.getDouble("facility.idle_cost_multiplier", 0.30);

        // Aggregate cost per player in one pass
        Map<Integer, Double> costByPlayer = new HashMap<>();

        String sql = "SELECT player_id, resource_name, state FROM facilities WHERE state IN ('active','idle')";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int    playerId     = rs.getInt("player_id");
                String resourceName = rs.getString("resource_name");
                String state        = rs.getString("state");

                ResourceRegistry.Resource resource = ResourceRegistry.get(resourceName);
                if (resource == null) continue;

                double baseCost   = resource.getOperatingCostPerTick();
                double actualCost = state.equals("idle") ? baseCost * idleMultiplier : baseCost;

                costByPlayer.merge(playerId, actualCost, Double::sum);
            }
        }

        // Deduct in a batch — one UPDATE per player
        String updateSql = "UPDATE players SET cash = cash - ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            for (Map.Entry<Integer, Double> entry : costByPlayer.entrySet()) {
                ps.setDouble(1, entry.getValue());
                ps.setInt(2, entry.getKey());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ── Step 2: Run production ───────────────────────────────────────────────

    private void step2RunProduction(Connection conn, int tick) throws Exception {
        // ORDER BY id ensures deterministic processing order across runs
        String sql = "SELECT id, player_id, resource_name, production_capacity FROM facilities WHERE state = 'active' ORDER BY id ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int    playerId     = rs.getInt("player_id");
                String resourceName = rs.getString("resource_name");
                int    capacity     = rs.getInt("production_capacity");

                ResourceRegistry.Recipe recipe = ResourceRegistry.getRecipe(resourceName);

                if (recipe == null) {
                    // Raw resource — no inputs needed, always produces at capacity
                    addToInventory(conn, playerId, resourceName, capacity, tick);
                    continue;
                }

                // Check available inputs and calculate actual output
                int actualOutput = calculateActualOutput(conn, playerId, recipe, capacity);
                if (actualOutput <= 0) continue;

                // Consume inputs using INTEGER division (round down) to avoid fractional items
                for (Map.Entry<String, Integer> input : recipe.inputs.entrySet()) {
                    int consume = (input.getValue() * actualOutput) / recipe.outputQuantity;
                    deductFromInventory(conn, playerId, input.getKey(), consume);
                }

                // Add output
                addToInventory(conn, playerId, resourceName, actualOutput, tick);
            }
        }
    }

    private int calculateActualOutput(Connection conn, int playerId, ResourceRegistry.Recipe recipe, int capacity) throws Exception {
        int maxOutput = capacity;
        for (Map.Entry<String, Integer> input : recipe.inputs.entrySet()) {
            double stock           = getInventoryQty(conn, playerId, input.getKey());
            int inputLimitedOutput = (int) (stock / input.getValue()) * recipe.outputQuantity;
            maxOutput = Math.min(maxOutput, inputLimitedOutput);
        }
        return maxOutput;
    }

    // ── Step 3: Apply resource decay ─────────────────────────────────────────

    private void step3ApplyDecay(Connection conn, int tick) throws Exception {
        // Decay applied once every 60 ticks per resource, only for perishable goods
        for (ResourceRegistry.Resource resource : ResourceRegistry.allResources()) {
            if (!resource.isPerishable()) continue;

            double keepFactor = 1.0 - resource.decayRatePerCycle;

            // Decay player inventory — uses last_decay_tick to guard 60-tick interval per row
            String invSql = """
                UPDATE inventory
                SET quantity = quantity * ?,
                    last_decay_tick = ?
                WHERE resource_name = ?
                  AND last_decay_tick <= ?
                """;
            try (PreparedStatement ps = conn.prepareStatement(invSql)) {
                ps.setDouble(1, keepFactor);
                ps.setInt(2, tick);
                ps.setString(3, resource.name);
                ps.setInt(4, tick - 60);
                ps.executeUpdate();
            }

            // Decay shop inventory — no last_decay_tick column, so gate by tick interval
            if (tick % 60 == 0) {
                String shopSql = """
                    UPDATE shop_inventory
                    SET quantity = GREATEST(0, FLOOR(quantity * ?))
                    WHERE resource_name = ?
                    """;
                try (PreparedStatement ps = conn.prepareStatement(shopSql)) {
                    ps.setDouble(1, keepFactor);
                    ps.setString(2, resource.name);
                    ps.executeUpdate();
                }
            }
        }

        // Clean up negligible inventory quantities
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM inventory WHERE quantity < 0.001")) {
            ps.executeUpdate();
        }
    }

    // ── Step 4: Process shop sales ───────────────────────────────────────────

    private void step4ProcessShopSales(Connection conn, int tick) throws Exception {
        DemandEngine demand = DemandEngine.getInstance();
        demand.updateCycles(tick);

        ConfigManager cfg   = ConfigManager.getInstance();
        double luxuryTaxRate = cfg.getDouble("economy.luxury_tax_percent", 3.0) / 100.0;

        String shopSql = """
            SELECT si.shop_id, si.resource_name, si.quantity, si.set_price, s.player_id
            FROM shop_inventory si
            JOIN shops s ON si.shop_id = s.id
            WHERE si.quantity > 0 AND si.set_price IS NOT NULL
            """;

        try (PreparedStatement ps = conn.prepareStatement(shopSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int    shopId       = rs.getInt("shop_id");
                int    playerId     = rs.getInt("player_id");
                String resourceName = rs.getString("resource_name");
                int    stockQty     = rs.getInt("quantity");
                double setPrice     = rs.getDouble("set_price");

                ResourceRegistry.Resource resource = ResourceRegistry.get(resourceName);
                if (resource == null || !resource.isConsumerGood()) continue;

                double priceCap = demand.getEffectivePriceCap(resourceName, tick);
                if (setPrice > priceCap) continue; // Price too high for NPC demand

                // NPC buys 1–5 units per tick (tunable)
                int npcDemand = 1 + (int) (Math.random() * 4);
                int soldQty   = Math.min(npcDemand, stockQty);
                if (soldQty <= 0) continue;

                double revenue    = setPrice * soldQty;
                double tax        = revenue * luxuryTaxRate;
                double netRevenue = revenue - tax;

                // Deduct from shop
                String deductShop = "UPDATE shop_inventory SET quantity = quantity - ? WHERE shop_id = ? AND resource_name = ?";
                try (PreparedStatement upd = conn.prepareStatement(deductShop)) {
                    upd.setInt(1, soldQty);
                    upd.setInt(2, shopId);
                    upd.setString(3, resourceName);
                    upd.executeUpdate();
                }

                // Credit player (net of luxury tax)
                updateCash(conn, playerId, netRevenue);
                addToCentralBank(conn, "luxury_tax_reserve", tax);

                // Record sale
                String saleSql = """
                    INSERT INTO shop_sales (shop_id, resource_name, quantity_sold, price_per_unit, tick_number)
                    VALUES (?, ?, ?, ?, ?)
                    """;
                try (PreparedStatement sps = conn.prepareStatement(saleSql)) {
                    sps.setInt(1, shopId);
                    sps.setString(2, resourceName);
                    sps.setInt(3, soldQty);
                    sps.setDouble(4, setPrice);
                    sps.setInt(5, tick);
                    sps.executeUpdate();
                }

                demand.recordSale(resourceName, soldQty);
            }
        }
    }

    // ── Step 5: Match market orders ──────────────────────────────────────────

    private void step5MatchMarketOrders(Connection conn, int tick) throws Exception {
        // Get all resources with open orders, ordered deterministically
        List<String> activeResources = new ArrayList<>();
        String resSql = "SELECT DISTINCT resource_name FROM market_orders ORDER BY resource_name ASC";
        try (PreparedStatement ps = conn.prepareStatement(resSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) activeResources.add(rs.getString("resource_name"));
        }

        for (String resource : activeResources) {
            matchResource(conn, resource, tick);
        }
    }

    private void matchResource(Connection conn, String resourceName, int tick) throws Exception {
        List<Order> sells = fetchOrders(conn, resourceName, "sell");
        List<Order> buys  = fetchOrders(conn, resourceName, "buy");

        // Recalculate effective quantities (reserve / target)
        for (Order sell : sells) {
            if (sell.keepReserve > 0) {
                double stock    = getInventoryQty(conn, sell.playerId, resourceName);
                sell.effectiveQty = (int) Math.max(0, stock - sell.keepReserve);
            } else {
                sell.effectiveQty = sell.quantity - sell.quantityFilled;
            }
        }
        for (Order buy : buys) {
            if (buy.targetQty > 0) {
                double stock    = getInventoryQty(conn, buy.playerId, resourceName);
                buy.effectiveQty = (int) Math.max(0, buy.targetQty - stock);
            } else {
                buy.effectiveQty = buy.quantity - buy.quantityFilled;
            }
        }

        ConfigManager cfg     = ConfigManager.getInstance();
        double feeRate        = cfg.getDouble("economy.market_fee_percent", 2.0) / 100.0;
        double halfFee        = feeRate / 2.0;

        int    si = 0, bi = 0;
        int    totalVolume = 0;
        double lastPrice   = 0;

        while (si < sells.size() && bi < buys.size()) {
            Order sell = sells.get(si);
            Order buy  = buys.get(bi);

            if (sell.effectiveQty <= 0) { si++; continue; }
            if (buy.effectiveQty  <= 0) { bi++; continue; }
            if (buy.price < sell.price) break; // No price overlap

            // Prevent self-matching
            if (sell.playerId == buy.playerId) { bi++; continue; }

            int    tradeQty      = Math.min(sell.effectiveQty, buy.effectiveQty);
            double tradePrice    = sell.price; // Execute at seller's ask

            double sellerRevenue = tradePrice * tradeQty * (1.0 - halfFee);
            double buyerCost     = tradePrice * tradeQty * (1.0 + halfFee);
            double feeCollected  = tradePrice * tradeQty * feeRate;

            // Transfer inventory: seller → buyer
            deductFromInventory(conn, sell.playerId, resourceName, tradeQty);
            addToInventory(conn, buy.playerId, resourceName, tradeQty, tick);

            // Transfer cash
            updateCash(conn, sell.playerId,  sellerRevenue);
            updateCash(conn, buy.playerId,  -buyerCost);

            addToCentralBank(conn, "market_fee_reserve", feeCollected);

            // Update filled amounts
            sell.quantityFilled += tradeQty;
            buy.quantityFilled  += tradeQty;
            sell.effectiveQty   -= tradeQty;
            buy.effectiveQty    -= tradeQty;

            updateOrderFilled(conn, sell.id, sell.quantityFilled);
            updateOrderFilled(conn, buy.id,  buy.quantityFilled);

            totalVolume += tradeQty;
            lastPrice    = tradePrice;

            if (sell.effectiveQty <= 0) si++;
            if (buy.effectiveQty  <= 0) bi++;
        }

        if (totalVolume > 0) {
            recordPriceHistory(conn, resourceName, lastPrice, lastPrice, totalVolume, tick);
        }
    }

    private List<Order> fetchOrders(Connection conn, String resourceName, String side) throws Exception {
        String sql = side.equals("sell")
            ? "SELECT id, player_id, price, quantity, quantity_filled, keep_reserve FROM market_orders WHERE resource_name = ? AND side = 'sell' ORDER BY price ASC, created_at ASC, id ASC"
            : "SELECT id, player_id, price, quantity, quantity_filled, target_quantity FROM market_orders WHERE resource_name = ? AND side = 'buy' ORDER BY price DESC, created_at ASC, id ASC";

        List<Order> orders = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, resourceName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order o         = new Order();
                    o.id            = rs.getInt("id");
                    o.playerId      = rs.getInt("player_id");
                    o.price         = rs.getDouble("price");
                    o.quantity      = rs.getInt("quantity");
                    o.quantityFilled= rs.getInt("quantity_filled");
                    o.keepReserve   = side.equals("sell") ? rs.getInt("keep_reserve") : 0;
                    o.targetQty     = side.equals("buy")  ? rs.getInt("target_quantity") : 0;
                    o.effectiveQty  = o.quantity - o.quantityFilled;
                    orders.add(o);
                }
            }
        }
        return orders;
    }

    private void recordPriceHistory(Connection conn, String resourceName,
                                    double buyPrice, double sellPrice, int volume, int tick) throws Exception {
        String sql = """
            INSERT INTO price_history (resource_name, tick_number, buy_price, sell_price, volume_traded)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                buy_price     = VALUES(buy_price),
                sell_price    = VALUES(sell_price),
                volume_traded = volume_traded + VALUES(volume_traded)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, resourceName);
            ps.setInt(2, tick);
            ps.setDouble(3, buyPrice);
            ps.setDouble(4, sellPrice);
            ps.setInt(5, volume);
            ps.executeUpdate();
        }
    }

    // ── Step 6: Auto-idle bankrupt facilities ────────────────────────────────

    private void step6AutoIdleFacilities(Connection conn, int tick) throws Exception {
        double threshold = ConfigManager.getInstance().getDouble("facility.bankruptcy_threshold", 0.0);

        // Find players below threshold
        List<Integer> bankruptPlayers = new ArrayList<>();
        String playerSql = "SELECT id FROM players WHERE cash < ?";
        try (PreparedStatement ps = conn.prepareStatement(playerSql)) {
            ps.setDouble(1, threshold);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) bankruptPlayers.add(rs.getInt("id"));
            }
        }

        for (int playerId : bankruptPlayers) {
            // Idle one most-expensive active facility per tick (operating cost is tier-based)
            // Load all active facilities, pick the one with the highest operating cost
            String facSql = "SELECT id, resource_name FROM facilities WHERE player_id = ? AND state = 'active'";
            int    bestId   = -1;
            double bestCost = -1;

            try (PreparedStatement ps = conn.prepareStatement(facSql)) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String rName = rs.getString("resource_name");
                        ResourceRegistry.Resource res = ResourceRegistry.get(rName);
                        double cost = (res != null) ? res.getOperatingCostPerTick() : 0;
                        if (cost > bestCost) {
                            bestCost = cost;
                            bestId   = rs.getInt("id");
                        }
                    }
                }
            }

            if (bestId > 0) {
                String idleSql = "UPDATE facilities SET state = 'idle' WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(idleSql)) {
                    ps.setInt(1, bestId);
                    ps.executeUpdate();
                }
                System.out.println("[TICK] Auto-idled facility #" + bestId + " for bankrupt player #" + playerId);
            }
        }
    }

    // ── Shared helper methods ────────────────────────────────────────────────

    double getInventoryQty(Connection conn, int playerId, String resourceName) throws Exception {
        String sql = "SELECT quantity FROM inventory WHERE player_id = ? AND resource_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            ps.setString(2, resourceName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("quantity") : 0.0;
            }
        }
    }

    void addToInventory(Connection conn, int playerId, String resourceName, double qty, int tick) throws Exception {
        String sql = """
            INSERT INTO inventory (player_id, resource_name, quantity, last_decay_tick)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity),
                                  last_decay_tick = VALUES(last_decay_tick)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            ps.setString(2, resourceName);
            ps.setDouble(3, qty);
            ps.setInt(4, tick);
            ps.executeUpdate();
        }
    }

    void deductFromInventory(Connection conn, int playerId, String resourceName, double qty) throws Exception {
        String sql = "UPDATE inventory SET quantity = GREATEST(0, quantity - ?) WHERE player_id = ? AND resource_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, qty);
            ps.setInt(2, playerId);
            ps.setString(3, resourceName);
            ps.executeUpdate();
        }
    }

    void updateCash(Connection conn, int playerId, double delta) throws Exception {
        String sql = "UPDATE players SET cash = cash + ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, delta);
            ps.setInt(2, playerId);
            ps.executeUpdate();
        }
    }

    private void addToCentralBank(Connection conn, String column, double amount) throws Exception {
        // column is controlled by internal code only — safe to interpolate
        String sql = "UPDATE central_bank SET " + column + " = " + column + " + ? WHERE id = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.executeUpdate();
        }
    }

    private void updateOrderFilled(Connection conn, int orderId, int filledQty) throws Exception {
        String sql = "UPDATE market_orders SET quantity_filled = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, filledQty);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        }
    }

    // ── Inner types ──────────────────────────────────────────────────────────

    /** Used internally during market order matching. */
    private static class Order {
        int    id, playerId, quantity, quantityFilled, keepReserve, targetQty, effectiveQty;
        double price;
    }

    // ── DB persistence ───────────────────────────────────────────────────────

    private void updateGameState(Connection conn, int tick) throws Exception {
        String sql = "INSERT INTO game_state (id, current_tick, last_tick_timestamp) " +
                     "VALUES (1, ?, NOW()) " +
                     "ON DUPLICATE KEY UPDATE current_tick = ?, last_tick_timestamp = NOW()";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tick);
            ps.setInt(2, tick);
            ps.executeUpdate();
        }
    }

    private void loadTickFromDatabase() {
        try (Connection conn = DB.connect()) {
            String sql = "SELECT current_tick FROM game_state WHERE id = 1";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    currentTick.set(rs.getInt("current_tick"));
                    System.out.println("[TICK] Resumed from tick #" + currentTick.get());
                    return;
                }
            }
            currentTick.set(0);
            System.out.println("[TICK] No game_state found, starting from tick #0");
        } catch (Exception e) {
            System.err.println("[TICK] Could not load tick from DB (starting from 0): " + e.getMessage());
            currentTick.set(0);
        }
    }

    private void saveTickToDatabase() {
        try (Connection conn = DB.connect()) {
            String sql = "UPDATE game_state SET current_tick = ? WHERE id = 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, currentTick.get());
                ps.executeUpdate();
            }
            System.out.println("[TICK] Persisted tick #" + currentTick.get());
        } catch (Exception e) {
            System.err.println("[TICK] Could not save tick to DB: " + e.getMessage());
        }
    }

    // ── Public accessors ─────────────────────────────────────────────────────

    public int getCurrentTick() { return currentTick.get(); }
    public boolean isRunning()  { return running; }
}
