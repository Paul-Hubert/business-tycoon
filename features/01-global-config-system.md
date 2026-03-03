# Feature 1.1 — Global Config System

## Overview

A single server-side JSON configuration file containing all tuning parameters. Hot-reloadable without server restart. Every other system reads from this config rather than using hardcoded values.

Currently, values are scattered across multiple Java files:
- `User.java:19` — `startingMoney = 100000`
- `ResourceProduction.java:68` — `getProductionCost()` returns hardcoded `10000`
- `SimulationServlet.java:19-20` — `SIMULATION_INTERVAL = 1000`, `PRICE_INTERVAL_MINUTES = 15`
- `Market.java:166-168` — hardcoded prices for bread (350), phone (89900), car (3500000)

---

## What Needs to Be Built

### New Files
1. **`src/config/GameConfig.java`** — Singleton that loads, caches, and exposes all config values. Thread-safe reads, hot-reloadable.
2. **`src/config/ConfigWatcher.java`** — File watcher (Java WatchService) that detects changes to config file and triggers reload.
3. **`config/game-config.json`** — The actual config file (deployed to classpath or a known path).

### New REST Endpoint
- `GET /api/config` — Returns the full config as JSON (public, no auth required). AI agents and UI both read from this.

---

## Existing Code Changes

| File | Change |
|------|--------|
| `User.java` | Replace `startingMoney = 100000` with `GameConfig.get().getStartingMoney()` |
| `ResourceProduction.java` | Replace `getProductionCost()` hardcoded return with config lookup by resource tier |
| `SimulationServlet.java` | Replace `SIMULATION_INTERVAL` and `PRICE_INTERVAL_MINUTES` with config reads |
| `Market.java` | Replace `updatePrice()` hardcoded values with config base prices |

---

## Data Model

### Config File Format (`game-config.json`)

```json
{
  "economy": {
    "startingMoney": 100000,
    "marketFeePercent": 2.0,
    "luxuryTaxPercent": 3.0,
    "idleCostMultiplier": 0.3,
    "downsizeRefundPercent": 0.4
  },
  "simulation": {
    "tickIntervalMs": 1000,
    "priceUpdateIntervalMinutes": 15,
    "decayIntervalTicks": 60
  },
  "facilities": {
    "buildCosts": {
      "RAW": 10000,
      "INTERMEDIATE": 30000,
      "ADVANCED_INTERMEDIATE": 80000,
      "CONSUMER": 200000
    },
    "operatingCosts": {
      "RAW": 200,
      "INTERMEDIATE": 500,
      "ADVANCED_INTERMEDIATE": 1200,
      "CONSUMER": 3000
    }
  },
  "demand": {
    "perlinOctaves": 6,
    "demandMultiplierMin": 0.7,
    "demandMultiplierMax": 1.4,
    "cyclePeriods": {
      "BREAD": 300, "CANNED_FOOD": 300,
      "CLOTHING": 600, "BICYCLE": 600,
      "FURNITURE": 900,
      "PHONE": 1200, "LAPTOP": 1200,
      "JEWELRY": 1800,
      "CAR": 2700
    },
    "basePrices": {
      "BREAD": 350, "CANNED_FOOD": 500,
      "CLOTHING": 12000, "BICYCLE": 5000,
      "FURNITURE": 50000,
      "PHONE": 89900, "LAPTOP": 89900,
      "JEWELRY": 250000,
      "CAR": 3500000
    }
  },
  "decay": {
    "rates": {
      "WHEAT": 0.01, "BREAD": 0.02, "CANNED_FOOD": 0.002,
      "PETROL": 0.005, "RUBBER": 0.003, "COTTON": 0.005
    }
  },
  "ai": {
    "enabled": true,
    "decisionIntervalMs": 7000,
    "corporations": ["AgriCorp", "TechVentures", "IronWorks", "LuxuryCraft"]
  }
}
```

All monetary values stored as BIGINT cents (e.g., `$100.00` = `10000`).

---

## Cross-System Dependencies

Every other system is a **consumer** of GameConfig. This is the most depended-upon module:

| Consumer System | Config Values Read |
|-----------------|-------------------|
| **Tick Engine** (04) | `simulation.tickIntervalMs`, tick ordering |
| **Facility Management** (06) | `facilities.buildCosts`, `facilities.operatingCosts`, `economy.downsizeRefundPercent` |
| **Operating Costs** (07) | `facilities.operatingCosts`, `economy.idleCostMultiplier` |
| **Dynamic Pricing** (09) | `demand.*` (all Perlin params, base prices, cycle periods) |
| **Saturation Penalty** (10) | Saturation curve params |
| **Market Fee** (16) | `economy.marketFeePercent` |
| **Luxury Tax** (17) | `economy.luxuryTaxPercent` |
| **Resource Decay** (18) | `decay.rates` |
| **AI Corporations** (20) | `ai.*` |
| **User/Auth** | `economy.startingMoney` |
| **REST API** (03) | Serves `GET /api/config` |

**No system writes to config** — it's read-only at runtime (admin edits the file).

---

## Scalable Architecture

### Singleton with Volatile Reference

```java
public class GameConfig {
    private static volatile GameConfig instance;
    private final JsonObject root;  // immutable after construction

    public static GameConfig get() {
        if (instance == null) reload();
        return instance;
    }

    public static synchronized void reload() {
        instance = new GameConfig(loadFromFile());
    }
}
```

- **Thread safety**: `volatile` reference ensures visibility. The config object itself is immutable — a reload creates a new instance (copy-on-write pattern).
- **Hot reload**: `ConfigWatcher` uses `java.nio.file.WatchService` on the config file's directory. On `ENTRY_MODIFY`, calls `GameConfig.reload()`.
- **No system depends on the file format**: Systems call typed getters like `getStartingMoney()`, `getBuildCost(ResourceTier)`, not raw JSON paths. If the config source changes (DB, env vars), only GameConfig internals change.

### Interface for Testability

```java
public interface ConfigProvider {
    long getStartingMoney();
    long getBuildCost(ResourceTier tier);
    long getOperatingCost(ResourceTier tier);
    double getMarketFeePercent();
    // ... etc
}
```

Systems depend on `ConfigProvider` interface, not `GameConfig` class directly. Enables test doubles.

---

## Key Implementation Details

- **JSON library**: Use `org.json` (already available — `json-20210307.jar` in `WEB-INF/lib/`). Lightweight, no additional dependency.
- **File location**: `WEB-INF/config/game-config.json` (inside webapp, editable without rebuild). Alternatively, external path configurable via system property.
- **Hot reload latency**: WatchService polling interval is OS-dependent (2-10 seconds on most systems). Acceptable for tuning — not for real-time changes.
- **Defaults**: If config file missing or a key missing, fall back to hardcoded defaults. Log warnings. Never crash.
- **Validation**: On reload, validate ranges (no negative costs, fee percent 0-100, etc.). Reject invalid config, keep previous.
- **Startup**: `SimulationServlet.contextInitialized()` calls `GameConfig.reload()` before starting tick engine.

---

## API Contract

### `GET /api/config`

**Auth**: None (public)

**Response** `200 OK`:
```json
{
  "startingMoney": 100000,
  "marketFeePercent": 2.0,
  "luxuryTaxPercent": 3.0,
  "resources": {
    "WHEAT": { "tier": "RAW", "perishable": true, "decayRate": 0.01 },
    "STEEL": { "tier": "INTERMEDIATE", "perishable": false },
    "CAR": { "tier": "CONSUMER", "perishable": false, "basePrice": 3500000, "demandCycleSec": 2700 }
  },
  "facilityBuildCosts": { "RAW": 10000, "INTERMEDIATE": 30000, "ADVANCED_INTERMEDIATE": 80000, "CONSUMER": 200000 },
  "facilityOperatingCosts": { "RAW": 200, "INTERMEDIATE": 500, "ADVANCED_INTERMEDIATE": 1200, "CONSUMER": 3000 },
  "tickIntervalMs": 1000
}
```

The response is a **flattened, UI-friendly** subset of the full config — not a raw dump of the JSON file. The API shapes data for consumers.
