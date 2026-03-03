# Trade Empire — Phase 1 Implementation Guide

> **Foundation & Infrastructure**: Config System, Database Schema, Tick Engine, REST API

---

## Overview

Phase 1 builds the **invisible infrastructure** that everything else depends on. You won't see much visible gameplay, but a solid foundation prevents cascading bugs later.

**Estimated difficulty:** Medium (especially Tick Engine)
**Estimated duration:** 2–3 weeks for all 4 components
**Critical order:** Must complete 1.1 → 1.2 → 1.4 → 1.3 (config unlocks schema design, both needed before tick engine, API wraps everything)

---

## 1.1 Global Config System

### What It Does

A single server-side config file containing **all** tuning parameters:
- Starting cash amounts
- Facility build costs
- Operating costs per tick
- Market fees, luxury taxes
- Decay rates
- Demand cycle periods
- Perlin noise octaves

**Why it matters:** Instead of hardcoding `1000` for starting money in 15 different places, you have one source of truth. Change it in the config, restart the server (or hot-reload), and it applies everywhere.

### Implementation

#### 1. Create `config/GameConfig.properties`

```properties
# Economy
economy.starting_cash=1000
economy.market_fee_percent=2.0
economy.luxury_tax_percent=3.0

# Facilities - Build Costs
facility.build_cost.raw=100
facility.build_cost.intermediate=300
facility.build_cost.advanced_intermediate=800
facility.build_cost.consumer=2000

# Facilities - Operating Costs per Tick
facility.operating_cost.raw=2.0
facility.operating_cost.intermediate=5.0
facility.operating_cost.advanced_intermediate=12.0
facility.operating_cost.consumer=30.0

# Facility Auto-Idle Threshold
facility.bankruptcy_threshold=0.0

# Decay Rates (percent per 60 ticks)
decay.wheat=1.0
decay.bread=2.0
decay.canned_food=0.2
decay.petrol=0.5
decay.rubber=0.3
decay.cotton=0.5

# Consumer Good Demand Cycles (minutes)
demand.cycle_minutes.bread=5
demand.cycle_minutes.clothing=10
demand.cycle_minutes.furniture=15
demand.cycle_minutes.phone=20
demand.cycle_minutes.jewelry=30
demand.cycle_minutes.car=45

# Perlin Noise
perlin.base_octaves=6
perlin.demand_min_multiplier=0.7
perlin.demand_max_multiplier=1.4

# Simulation
simulation.ticks_per_second=4
simulation.thread_pool_size=4
```

#### 2. Create `ConfigManager.java`

```java
package com.tradeempire.config;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ConfigManager {
    private static ConfigManager instance;
    private Properties properties;
    private Path configPath;
    private long lastModified = 0;

    private ConfigManager() {
        this.configPath = Paths.get("config/GameConfig.properties");
        reload();
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager();
                }
            }
        }
        return instance;
    }

    public synchronized void reload() {
        try {
            if (!Files.exists(configPath)) {
                System.err.println("Config file not found: " + configPath.toAbsolutePath());
                properties = new Properties();
                return;
            }

            Properties newProps = new Properties();
            try (InputStream is = new FileInputStream(configPath.toFile())) {
                newProps.load(is);
            }
            this.properties = newProps;
            this.lastModified = Files.getLastModifiedTime(configPath).toMillis();
            System.out.println("[CONFIG] Reloaded from " + configPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[CONFIG] Error loading config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Check if config has been modified on disk (for hot-reload)
    public boolean hasChanged() {
        try {
            if (Files.exists(configPath)) {
                long currentModified = Files.getLastModifiedTime(configPath).toMillis();
                return currentModified > lastModified;
            }
        } catch (IOException ignored) {}
        return false;
    }

    // Typed getters
    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("[CONFIG] Invalid int for " + key + ": " + value);
            return defaultValue;
        }
    }

    public double getDouble(String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            System.err.println("[CONFIG] Invalid double for " + key + ": " + value);
            return defaultValue;
        }
    }

    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        return value.equalsIgnoreCase("true");
    }
}
```

#### 3. Usage in Other Code

```java
// In SimulationServlet or any service:
ConfigManager config = ConfigManager.getInstance();

int startingCash = config.getInt("economy.starting_cash", 1000);
double facilityRawCost = config.getDouble("facility.build_cost.raw", 100.0);
```

### Pitfalls & Foresight

**Pitfall 1: Hardcoded defaults scattered everywhere**
- ❌ Bad: `int cost = 100;`
- ✅ Good: `int cost = config.getInt("facility.build_cost.raw", 100);`
- Use config getters *everywhere*, even with defaults. Makes future tweaks trivial.

**Pitfall 2: No hot-reload**
- If you require a full server restart to change economy parameters, tuning the game becomes painfully slow.
- Build hot-reload into the config loader from day 1 (check `lastModified` periodically).

**Pitfall 3: Config grows unmaintained**
- Future you will add 50+ parameters. Keep them organized by section (economy, facilities, decay, etc.).
- Document what each value controls.

**Foresight for Phase 2 (Core Economy):**
- You'll need per-resource config: `demand.cycle_minutes.{resourceName}`, `decay.{resourceName}`, `facility.operating_cost.{tier}`.
- Design the naming scheme now to avoid refactoring later.
- Example: `resource.{name}.{property}` vs. `{property}.{name}` — pick one and stick with it.

**Foresight for Phase 3 (Market System):**
- Add AI decision thresholds: `ai.decision_interval_seconds`, `ai.price_threshold_percent`.
- Add market depth limits: `market.max_orders_per_player`, `market.max_offer_quantity`.

**Foresight for Phase 6 (AI Integration):**
- Add MCP rate limits: `api.rate_limit_per_minute`, `api.timeout_seconds`.
- Add system AI strategies: `ai.agricorp.strategy`, `ai.techventures.strategy`, etc.

---

## 1.2 Database Schema Redesign

### What It Does

Replaces the existing schema with tables supporting the full MVP game loop:
- Facilities (production infrastructure)
- Inventory (per-player resource storage)
- Market orders (buy/sell offers)
- Price history (for charts)
- Shops (consumer sales locations)
- Chat messages (player communication)

### Current State Assessment

Before you rewrite the schema, check what tables already exist:

```sql
SHOW TABLES;
DESCRIBE {table_name};
```

From your memory, you have `src/database/Provider.java`. Verify it's correctly pointing to `mariadb` in Docker, not `localhost`.

### New Schema

```sql
-- ==============================================
-- CORE TABLES (Required for all MVP)
-- ==============================================

-- Players (already exists, likely)
-- Ensure these columns:
CREATE TABLE IF NOT EXISTS players (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    cash DECIMAL(15, 2) NOT NULL DEFAULT 1000.00,
    net_worth DECIMAL(15, 2) NOT NULL DEFAULT 1000.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_ai BOOLEAN DEFAULT FALSE,
    ai_strategy VARCHAR(50) -- For System AI: 'agricorp', 'techventures', 'ironworks', 'luxurycraft'
);

-- Facilities (new)
-- Represents a production building for a specific resource
CREATE TABLE IF NOT EXISTS facilities (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    resource_name VARCHAR(100) NOT NULL,
    state ENUM('active', 'idle', 'destroyed') DEFAULT 'active',
    production_capacity INT NOT NULL, -- Units per tick (determined by resource)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    INDEX idx_player (player_id),
    INDEX idx_resource (resource_name)
);

-- Inventory (new)
-- Player resource storage
CREATE TABLE IF NOT EXISTS inventory (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    resource_name VARCHAR(100) NOT NULL,
    quantity DECIMAL(15, 2) NOT NULL DEFAULT 0,
    last_decay_tick INT NOT NULL DEFAULT 0, -- For applying decay only once per tick
    UNIQUE KEY unique_player_resource (player_id, resource_name),
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    INDEX idx_player (player_id)
);

-- Market Orders (new)
-- Buy/sell offers on the market
CREATE TABLE IF NOT EXISTS market_orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    resource_name VARCHAR(100) NOT NULL,
    side ENUM('buy', 'sell') NOT NULL,
    price DECIMAL(15, 2) NOT NULL,
    quantity INT NOT NULL,
    quantity_filled INT NOT NULL DEFAULT 0,

    -- Reserve (for sells) or Target (for buys)
    -- sell: keep_reserve = "keep 100 in stock, sell rest"
    -- buy: target_quantity = "buy up to 500 total stock"
    keep_reserve INT, -- Only for side='sell'
    target_quantity INT, -- Only for side='buy'

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    INDEX idx_resource_price (resource_name, price),
    INDEX idx_player (player_id),
    INDEX idx_side (side)
);

-- Price History (new)
-- For charts and analysis
CREATE TABLE IF NOT EXISTS price_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    resource_name VARCHAR(100) NOT NULL,
    tick_number INT NOT NULL,
    buy_price DECIMAL(15, 2), -- Last successful buy price
    sell_price DECIMAL(15, 2), -- Last successful sell price
    volume_traded INT NOT NULL DEFAULT 0,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_resource_tick (resource_name, tick_number),
    INDEX idx_resource (resource_name),
    INDEX idx_tick (tick_number)
);

-- Shops (new)
-- Retail locations for consumer goods
CREATE TABLE IF NOT EXISTS shops (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    shop_name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    INDEX idx_player (player_id)
);

-- Shop Inventory (new)
-- What's in each shop
CREATE TABLE IF NOT EXISTS shop_inventory (
    id INT AUTO_INCREMENT PRIMARY KEY,
    shop_id INT NOT NULL,
    resource_name VARCHAR(100) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    set_price DECIMAL(15, 2), -- Player-set price, or NULL to match market
    UNIQUE KEY unique_shop_resource (shop_id, resource_name),
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
);

-- Shop Sales (new)
-- Historical sales data for analytics
CREATE TABLE IF NOT EXISTS shop_sales (
    id INT AUTO_INCREMENT PRIMARY KEY,
    shop_id INT NOT NULL,
    resource_name VARCHAR(100) NOT NULL,
    quantity_sold INT NOT NULL,
    price_per_unit DECIMAL(15, 2) NOT NULL,
    tick_number INT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
    INDEX idx_shop (shop_id),
    INDEX idx_tick (tick_number)
);

-- Chat Messages (new)
CREATE TABLE IF NOT EXISTS chat_messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    from_player_id INT NOT NULL,
    to_player_id INT NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (from_player_id) REFERENCES players(id) ON DELETE CASCADE,
    FOREIGN KEY (to_player_id) REFERENCES players(id) ON DELETE CASCADE,
    INDEX idx_conversation (from_player_id, to_player_id),
    INDEX idx_to_player (to_player_id)
);

-- Game State (new)
-- Singleton table to track global tick count and other state
CREATE TABLE IF NOT EXISTS game_state (
    id INT PRIMARY KEY DEFAULT 1,
    current_tick INT NOT NULL DEFAULT 0,
    last_tick_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    server_status ENUM('running', 'paused', 'stopped') DEFAULT 'running',
    CHECK (id = 1) -- Enforce singleton
);

-- Central Bank (new)
-- Track accumulated fees and taxes
CREATE TABLE IF NOT EXISTS central_bank (
    id INT PRIMARY KEY DEFAULT 1,
    market_fee_reserve DECIMAL(15, 2) NOT NULL DEFAULT 0,
    luxury_tax_reserve DECIMAL(15, 2) NOT NULL DEFAULT 0,
    CHECK (id = 1)
);
```

### Schema Design Decisions Explained

**Why separate `inventory` and `shop_inventory`?**
- Players have a global inventory (raw materials, intermediates)
- Shops have separate inventory (only consumer goods for sale)
- A player's main inventory never sells to NPCs; only shops do
- This prevents "my production queue consumes my retail stock" bugs

**Why `keep_reserve` and `target_quantity` in the same table?**
- A sell order has `keep_reserve`: "Keep 100 Wheat in storage, sell rest at $2"
- A buy order has `target_quantity`: "Buy up to 500 Iron total at $3 max"
- They're conceptually different, but same table simplifies JOIN queries
- In code, you check: `if (order.side == 'sell') use keep_reserve; else use target_quantity`

**Why `quantity_filled` instead of just deleting completed orders?**
- Historical data: you can see "I sold 1000 units at this price"
- Prevents "ghost orders" that mysteriously disappear
- Simplifies the matching engine (just update `quantity_filled`)

**Why `game_state` singleton table?**
- Tick engine needs to know current tick number (for decay, price history, etc.)
- Storing it in a single-row table avoids race conditions
- Alternative: cache it in memory, but then you must persist on graceful shutdown

### Implementation Steps

1. **Backup existing database:**
   ```bash
   docker compose exec mariadb mysqldump -u user -ppassword db > backup.sql
   ```

2. **Create migration script** (`sql/001_phase1_schema.sql`):
   - Include the schema above
   - Add `IF NOT EXISTS` checks to make it idempotent
   - Document what each table does

3. **Run migration:**
   ```bash
   docker compose exec mariadb mysql -u user -ppassword db < sql/001_phase1_schema.sql
   ```

4. **Verify:**
   ```bash
   docker compose exec mariadb mysql -u user -ppassword db -e "SHOW TABLES;"
   ```

### Pitfalls & Foresight

**Pitfall 1: Forgetting indexes**
- ❌ Bad: `SELECT * FROM market_orders WHERE resource_name = 'Iron';` (full table scan)
- ✅ Good: Index on `resource_name` + frequently-used filter combinations
- Query 1000s of market orders per tick (during matching). Indexes are critical.

**Pitfall 2: `DECIMAL` vs `FLOAT` for money**
- ❌ Bad: `price FLOAT` — can introduce rounding errors
- ✅ Good: `price DECIMAL(15, 2)` — exact representation of cents
- Use `DECIMAL` for all financial data.

**Pitfall 3: Forgetting to handle NULL properly**
- Some columns are NULL by design (`keep_reserve` for buy orders, `target_quantity` for sell orders)
- Code must check `if (order.keep_reserve != null)` before using it
- Document which columns can be NULL

**Pitfall 4: Not tracking `last_decay_tick`**
- If you apply decay every query, the same resource decays multiple times
- Store `last_decay_tick` to ensure decay happens exactly once per tick
- Prevents "decay applied 5 times in one second" bugs

**Foresight for Phase 2 (Core Economy):**
- Facilities need to store input requirements (e.g., "Steel facility needs Iron + Coal")
- Add a `facility_recipes` table: `facility_id, input_resource, input_quantity_per_tick`
- Or store recipe info in-code (depends on how dynamic you want recipes to be)

**Foresight for Phase 4 (Consumer Sales):**
- Shop sales history is critical for demand forecasting
- `shop_sales` must track: `quantity_sold`, `price_per_unit`, `tick_number`
- You'll query this table frequently to calculate demand multipliers

**Foresight for Phase 6 (AI Integration):**
- System AI corporations are players (`is_ai = TRUE`)
- Add `ai_strategy` column to distinguish them
- This avoids special cases in the API — AI is just another player

---

## 1.4 Simulation Tick Engine Overhaul

### What It Does

A reliable, ordered, transactional loop that processes the entire economy once per "tick" (e.g., 1 tick = 1 second, 4 ticks per second = 250 ms intervals).

**Order of operations (per tick):**
1. Deduct operating costs from all active facilities
2. Run production (consume inputs, produce outputs)
3. Apply resource decay (spoilage)
4. Process shop sales (NPC customers buy goods)
5. Match market orders (execute buy/sell offers)
6. Auto-idle facilities if player can't afford operating costs

If any step fails, roll back and log the error without halting the tick.

### Why This Matters

Without a reliable tick engine:
- One facility's production bug halts the entire economy
- Decay applies inconsistently (sometimes twice, sometimes zero times)
- Market orders execute in random order (unfair to players)
- Impossible to debug economic issues (no consistent state)

### Implementation

#### 1. Create `TickEngine.java`

```java
package com.tradeempire.simulation;

import com.tradeempire.config.ConfigManager;
import com.tradeempire.database.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;

public class TickEngine {
    private static final Logger logger = LogManager.getLogger(TickEngine.class);
    private static TickEngine instance;

    private ScheduledExecutorService executor;
    private int ticksPerSecond;
    private volatile int currentTick = 0;
    private volatile boolean running = false;

    private TickEngine() {}

    public static TickEngine getInstance() {
        if (instance == null) {
            synchronized (TickEngine.class) {
                if (instance == null) {
                    instance = new TickEngine();
                }
            }
        }
        return instance;
    }

    public synchronized void start() {
        if (running) {
            logger.warn("TickEngine already running");
            return;
        }

        ConfigManager config = ConfigManager.getInstance();
        this.ticksPerSecond = config.getInt("simulation.ticks_per_second", 4);
        int poolSize = config.getInt("simulation.thread_pool_size", 4);

        executor = Executors.newScheduledThreadPool(poolSize);
        running = true;

        // Load initial tick from database
        loadTickFromDatabase();

        // Schedule tick execution
        long periodMs = 1000 / ticksPerSecond;
        executor.scheduleAtFixedRate(
            this::executeTick,
            0,
            periodMs,
            TimeUnit.MILLISECONDS
        );

        logger.info("TickEngine started: " + ticksPerSecond + " ticks/sec, pool size " + poolSize);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
            saveTickToDatabase();
            logger.info("TickEngine stopped gracefully");
        } catch (InterruptedException e) {
            logger.error("TickEngine shutdown interrupted", e);
            executor.shutdownNow();
        }
    }

    private void executeTick() {
        try {
            currentTick++;
            long startTime = System.currentTimeMillis();

            try (Connection conn = DatabaseProvider.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // Step 1: Deduct operating costs
                    deductOperatingCosts(conn);

                    // Step 2: Run production
                    runProduction(conn);

                    // Step 3: Apply decay
                    applyDecay(conn);

                    // Step 4: Process shop sales
                    processShopSales(conn);

                    // Step 5: Match market orders
                    matchMarketOrders(conn);

                    // Step 6: Auto-idle facilities
                    autoIdleFacilities(conn);

                    // Update game state
                    updateGameState(conn);

                    conn.commit();

                    long duration = System.currentTimeMillis() - startTime;
                    if (duration > 100) {
                        logger.warn("Slow tick #" + currentTick + ": " + duration + "ms");
                    }

                } catch (Exception e) {
                    conn.rollback();
                    logger.error("Tick #" + currentTick + " failed, rolled back", e);
                }
            }

        } catch (Exception e) {
            logger.error("TickEngine fatal error", e);
        }
    }

    private void deductOperatingCosts(Connection conn) throws Exception {
        // SELECT all active facilities
        // For each: fetch player's cash, subtract operating cost, update player
        // If player goes negative, flag for auto-idle (don't idle yet)

        String sql = """
            SELECT f.id, f.player_id, f.resource_name, p.cash,
                   CASE
                       WHEN r.tier = 'raw' THEN ?
                       WHEN r.tier = 'intermediate' THEN ?
                       WHEN r.tier = 'advanced_intermediate' THEN ?
                       WHEN r.tier = 'consumer' THEN ?
                   END AS operating_cost
            FROM facilities f
            JOIN players p ON f.player_id = p.id
            JOIN resources r ON f.resource_name = r.name
            WHERE f.state = 'active'
            """;

        // This is pseudocode. In reality, you'd:
        // 1. Load ConfigManager values for each tier
        // 2. Use PreparedStatement with proper parameterization
        // 3. Update player.cash in a single statement
        logger.debug("Step 1: Deducting operating costs...");
    }

    private void runProduction(Connection conn) throws Exception {
        // For each active facility:
        //   1. Check if inputs are available
        //   2. If yes: consume inputs, produce outputs
        //   3. If no: produce 0 units this tick (but still incur operating cost)
        logger.debug("Step 2: Running production...");
    }

    private void applyDecay(Connection conn) throws Exception {
        // For each perishable resource in each player's inventory:
        //   1. Calculate decay: current_qty * (1 - decay_rate)
        //   2. Update inventory.quantity
        //   3. Mark last_decay_tick = currentTick (prevent double-decay)
        logger.debug("Step 3: Applying decay...");
    }

    private void processShopSales(Connection conn) throws Exception {
        // For each shop:
        //   1. Calculate NPC demand (from demand forecast)
        //   2. Sell from shop inventory at set price
        //   3. Transfer revenue to player.cash (minus luxury tax)
        //   4. Log sale in shop_sales table
        logger.debug("Step 4: Processing shop sales...");
    }

    private void matchMarketOrders(Connection conn) throws Exception {
        // For each resource:
        //   1. Sort buy orders by price DESC, then by time ASC (price-time priority)
        //   2. Sort sell orders by price ASC, then by time ASC
        //   3. Match highest buyer with cheapest seller
        //   4. Deduct market fee (2%) from both
        //   5. Update inventory, filled quantities
        //   6. Log price and volume in price_history
        logger.debug("Step 5: Matching market orders...");
    }

    private void autoIdleFacilities(Connection conn) throws Exception {
        // For each player with cash < 0:
        //   1. Idle most expensive active facilities first
        //   2. Stop when player.cash > 0 (or reduce maintenance cost)
        //   3. Send notification (when we add that system)
        logger.debug("Step 6: Auto-idling facilities...");
    }

    private void updateGameState(Connection conn) throws Exception {
        // UPDATE game_state SET current_tick = ?, last_tick_timestamp = NOW()
        logger.debug("Updated game_state: tick #" + currentTick);
    }

    private void loadTickFromDatabase() {
        try (Connection conn = DatabaseProvider.getConnection()) {
            // SELECT current_tick FROM game_state
            // If not found, initialize
        } catch (Exception e) {
            logger.error("Error loading tick from database", e);
        }
    }

    private void saveTickToDatabase() {
        try (Connection conn = DatabaseProvider.getConnection()) {
            // UPDATE game_state SET current_tick = ?
        } catch (Exception e) {
            logger.error("Error saving tick to database", e);
        }
    }

    public int getCurrentTick() {
        return currentTick;
    }

    public boolean isRunning() {
        return running;
    }
}
```

#### 2. Register in `SimulationServlet.java`

```java
@WebListener
public class SimulationServlet implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[INIT] Starting Trade Empire simulation...");

        try {
            // Start tick engine (after config is loaded)
            TickEngine.getInstance().start();
            System.out.println("[INIT] Tick Engine started successfully");

        } catch (Exception e) {
            System.err.println("[INIT] Failed to start Tick Engine");
            e.printStackTrace();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("[SHUTDOWN] Stopping simulation...");
        TickEngine.getInstance().stop();
        System.out.println("[SHUTDOWN] Simulation stopped");
    }
}
```

### Pitfalls & Foresight

**Pitfall 1: No transaction isolation**
- ❌ Bad: Each tick step commits independently. One facility crashes mid-tick, leaving DB in half-updated state.
- ✅ Good: Wrap entire tick in a transaction. Rollback if anything fails.
- Ensures consistent state always.

**Pitfall 2: Ticks running concurrently**
- ❌ Bad: Tick #5 and Tick #6 execute simultaneously, stepping on each other's data.
- ✅ Good: Use `ScheduledExecutorService` with period-based scheduling (not overlapping).
- Or use a single-threaded executor to guarantee sequential ticks.

**Pitfall 3: Not handling slow steps**
- If production takes 5 seconds but ticks run every 250ms, ticks back up.
- Add timeout: if a step takes > 50ms, log a warning and skip it for diagnostics.
- Monitor which step is slow.

**Pitfall 4: Hard to debug**
- ❌ Bad: Console logging only. When something breaks at tick #10000, you can't replay it.
- ✅ Good: Log every step with tick number, allow "replay" for debugging.
- Consider storing tick snapshots periodically.

**Pitfall 5: `last_decay_tick` not tracked**
- If you don't check `last_decay_tick`, decay applies multiple times per tick.
- Implementation detail: only apply decay if `current_tick > last_decay_tick`

**Foresight for Phase 2 (Core Economy):**
- Production step needs access to recipes (Iron + Coal → Steel)
- Recipes can be:
  - **Hard-coded** (simpler, less flexible)
  - **In database** (more flexible for future expansions)
  - **In config file** (hot-reloadable)
- Decide now, impacts database design.

**Foresight for Phase 4 (Consumer Sales):**
- Shop sales need demand multiplier (from Perlin noise)
- Perlin noise generator should be created fresh each tick (or cached with awareness of tick progression)
- Test: does demand multiplier feel natural? Are cycles too fast/slow?

**Foresight for Phase 5 (Fees & Taxes):**
- Market fee and luxury tax revenue goes to `central_bank` table
- These are money sinks (prevent inflation)
- Admin might want to redistribute them later (e.g., "player bounties")
- Design accounting for this.

---

## 1.3 Unified REST API

### What It Does

Every game action is exposed as a REST endpoint. The UI (and future AI agents) call only the API, never the database directly.

This **guarantees parity**: if an AI agent can do something via the API, a human can too (and vice versa).

### Design Principles

1. **Stateless** — each request is independent (no session mutation)
2. **Authenticated** — endpoints validate player token
3. **Transactional** — each endpoint either fully succeeds or fully fails
4. **Documented** — clear error codes and response formats
5. **Versioned** — `/api/v1/...` allows future changes

### Core Endpoints

```
GET  /api/v1/state                  -> Full player state
GET  /api/v1/market/{resource}      -> Orderbook for a resource
GET  /api/v1/market/prices          -> Current prices for all resources
GET  /api/v1/market/history/{resource}  -> Price history
GET  /api/v1/leaderboard            -> Top players by net worth
GET  /api/v1/config                 -> Public config values

POST /api/v1/auth/signup            -> Create account
POST /api/v1/auth/login             -> Get session token
POST /api/v1/auth/logout            -> Invalidate token

POST /api/v1/production/build       -> Build a facility
POST /api/v1/production/idle        -> Idle a facility
POST /api/v1/production/activate    -> Reactivate a facility
POST /api/v1/production/downsize    -> Sell a facility

POST /api/v1/market/sell            -> Post a sell offer
POST /api/v1/market/buy             -> Post a buy offer
DELETE /api/v1/market/{offerId}     -> Cancel an offer

POST /api/v1/shop/stock             -> Stock a shop with goods
POST /api/v1/shop/price             -> Set shop price
GET  /api/v1/shop/{shopId}/sales    -> Shop sales history

POST /api/v1/chat/send              -> Send a message
GET  /api/v1/chat/messages          -> Fetch messages
```

### Implementation: Authentication

First, add a token system to the database:

```sql
CREATE TABLE IF NOT EXISTS auth_tokens (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    token VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    INDEX idx_token (token)
);
```

Create `AuthFilter.java`:

```java
package com.tradeempire.api;

import com.tradeempire.database.DatabaseProvider;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;

@WebFilter(urlPatterns = {"/api/v1/*"})
public class AuthFilter implements Filter {

    // Paths that don't require authentication
    private static final String[] PUBLIC_PATHS = {
        "/api/v1/auth/signup",
        "/api/v1/auth/login",
        "/api/v1/config"
    };

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // Check if path is public
        boolean isPublic = false;
        for (String publicPath : PUBLIC_PATHS) {
            if (path.equals(publicPath)) {
                isPublic = true;
                break;
            }
        }

        if (isPublic) {
            chain.doFilter(request, response);
            return;
        }

        // Verify token
        String token = extractToken(httpRequest);
        if (token == null || !isValidToken(token)) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\"error\": \"Unauthorized\"}");
            return;
        }

        // Attach player ID to request
        int playerId = getPlayerIdFromToken(token);
        httpRequest.setAttribute("playerId", playerId);

        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private boolean isValidToken(String token) {
        try (Connection conn = DatabaseProvider.getConnection()) {
            String sql = "SELECT 1 FROM auth_tokens WHERE token = ? AND expires_at > NOW()";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, token);
                return pstmt.executeQuery().next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private int getPlayerIdFromToken(String token) {
        try (Connection conn = DatabaseProvider.getConnection()) {
            String sql = "SELECT player_id FROM auth_tokens WHERE token = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, token);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("player_id");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public void init(FilterConfig config) {}

    @Override
    public void destroy() {}
}
```

### Implementation: Example Endpoint (`/api/v1/state`)

```java
package com.tradeempire.api;

import com.tradeempire.database.DatabaseProvider;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.JSONObject;

import java.sql.*;

@WebServlet("/api/v1/state")
public class StateServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws Exception {
        response.setContentType("application/json");

        Integer playerId = (Integer) request.getAttribute("playerId");
        if (playerId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Unauthorized\"}");
            return;
        }

        try (Connection conn = DatabaseProvider.getConnection()) {
            JSONObject state = new JSONObject();

            // Fetch player data
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT id, username, cash, net_worth FROM players WHERE id = ?")) {
                pstmt.setInt(1, playerId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        state.put("playerId", rs.getInt("id"));
                        state.put("username", rs.getString("username"));
                        state.put("cash", rs.getDouble("cash"));
                        state.put("net_worth", rs.getDouble("net_worth"));
                    }
                }
            }

            // Fetch inventory
            JSONObject inventory = new JSONObject();
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT resource_name, quantity FROM inventory WHERE player_id = ?")) {
                pstmt.setInt(1, playerId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        inventory.put(rs.getString("resource_name"), rs.getDouble("quantity"));
                    }
                }
            }
            state.put("inventory", inventory);

            // Fetch facilities
            // ... similar pattern
            // state.put("facilities", facilitiesArray);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(state.toString());

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
```

### Pitfalls & Foresight

**Pitfall 1: Missing error codes**
- ❌ Bad: `{"error": "Something went wrong"}`
- ✅ Good: `{"error": "insufficient_cash", "required": 500, "available": 100}`
- AI agents need specific error codes to make intelligent decisions.

**Pitfall 2: API doesn't validate business logic**
- ❌ Bad: Servlet just inserts into DB without checks (negative cash, missing inputs, etc.)
- ✅ Good: Service layer validates all rules before DB updates
- Example: `ProductionService.buildFacility(playerId, resource)` checks cash, resource validity, etc.

**Pitfall 3: Response format inconsistency**
- ❌ Bad: Some endpoints return `{data: ...}`, others return data directly
- ✅ Good: Pick a format and stick to it. Example: `{success: true, data: ..., error: null}`

**Pitfall 4: No rate limiting**
- ❌ Bad: Player spams `/api/v1/market/buy` 1000 times per second
- ✅ Good: Rate limit to 10 requests per second per player
- Future Phase 6 (AI Integration) will need this to prevent bot spam.

**Pitfall 5: Forgetting to update `net_worth` calculation**
- `net_worth = cash + (inventory value @ market prices)`
- Update this whenever cash or inventory changes
- Or calculate on-the-fly in the API (slower but simpler)

**Foresight for Phase 2 (Core Economy):**
- Add endpoints: `POST /api/v1/production/build`, `POST /api/v1/production/idle`, etc.
- Each endpoint should call a **Service** class that enforces rules
- Example: `ProductionService.buildFacility()` checks: cash available? resource exists? facility limit?

**Foresight for Phase 3 (Market System):**
- Market matching is complex. Consider a separate `MarketService` class
- API endpoint: `POST /api/v1/market/sell` → `MarketService.postSellOffer()` → updates DB + matches orders
- Matching logic should be testable in isolation

**Foresight for Phase 6 (AI Integration):**
- AI agents will call the same endpoints
- Design errors carefully so they can be programmatically detected
- Example: AI sees `insufficient_cash` error, it knows to save money before building

---

## Checklist: Phase 1 Complete

- [ ] **ConfigManager** loaded and hot-reloadable
- [ ] **Database schema** created and verified
- [ ] **TickEngine** running at correct tick rate
- [ ] **Auth system** (tokens + AuthFilter) working
- [ ] **REST API** for `/api/v1/state` and `/api/v1/config` tested
- [ ] Docker containers running without crashes
- [ ] Logs clean (no NPEs or SQLException)

---

## Testing Phase 1

```bash
# Start the server
./setup.sh

# Wait for Tomcat to start
sleep 10

# Check if running
curl http://localhost:8080/api/v1/config

# You should get the config values (no auth needed for /config)
# If 200 OK: Phase 1 foundation is solid!
```

---

## Next: Phase 2

Once Phase 1 is solid, you'll implement:
- Resource + Recipe system
- Facility management (build, idle, downsize)
- Operating costs deduction in the tick engine

See `IMPLEMENTATION_GUIDE_PHASE_2.md` (when ready)
