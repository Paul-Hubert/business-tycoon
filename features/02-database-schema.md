# Feature 1.2 — Database Schema Redesign

## Overview

The current schema has 3 tables (`users`, `production`, `offers`) supporting a basic prototype. The MVP requires 9+ tables to support facilities with state machines, separate inventory, market orders with reserve/target, price history, shops, chat, and API keys.

### Current Schema (to be migrated)
- `users` (id, user, pass, money)
- `production` (user_id, resource, count, production, research_cost, research) — no PK, mixes inventory + facility count
- `offers` (id, user_id, resource, buy, price, quantity) — no reserve/target, no timestamps

---

## New Tables

### 1. `facilities` — Replaces `production.production` count

```sql
CREATE TABLE facilities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resource INT NOT NULL,
    state ENUM('ACTIVE', 'IDLE') NOT NULL DEFAULT 'ACTIVE',
    build_cost BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_facilities_user (user_id),
    INDEX idx_facilities_user_resource (user_id, resource)
);
```

### 2. `inventory` — Replaces `production.count`

```sql
CREATE TABLE inventory (
    user_id BIGINT NOT NULL,
    resource INT NOT NULL,
    quantity BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, resource),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### 3. `market_orders` — Replaces `offers` with reserve/target support

```sql
CREATE TABLE market_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resource INT NOT NULL,
    order_type ENUM('BUY', 'SELL') NOT NULL,
    price BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    reserve BIGINT DEFAULT NULL,       -- SELL only: keep at least N
    target BIGINT DEFAULT NULL,        -- BUY only: reach N total
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_orders_resource_type (resource, order_type, price),
    INDEX idx_orders_user (user_id)
);
```

### 4. `trades` — Trade execution history

```sql
CREATE TABLE trades (
    id BIGINT NOT NULL AUTO_INCREMENT,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    resource INT NOT NULL,
    price BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    buyer_fee BIGINT NOT NULL DEFAULT 0,
    seller_fee BIGINT NOT NULL DEFAULT 0,
    executed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (buyer_id) REFERENCES users(id),
    FOREIGN KEY (seller_id) REFERENCES users(id),
    INDEX idx_trades_resource_time (resource, executed_at),
    INDEX idx_trades_buyer (buyer_id, executed_at),
    INDEX idx_trades_seller (seller_id, executed_at)
);
```

### 5. `market_prices` — Current market price per resource

```sql
CREATE TABLE market_prices (
    resource INT NOT NULL,
    last_trade_price BIGINT NOT NULL DEFAULT 0,
    last_buy_price BIGINT NOT NULL DEFAULT 0,
    last_sell_price BIGINT NOT NULL DEFAULT 0,
    volume_today BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (resource)
);
```

### 6. `shops` — Player-owned retail shops

```sql
CREATE TABLE shops (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resource INT NOT NULL,
    stock BIGINT NOT NULL DEFAULT 0,
    price BIGINT NOT NULL DEFAULT 0,
    total_sold BIGINT NOT NULL DEFAULT 0,
    total_revenue BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_shops_user (user_id)
);
```

### 7. `chat_messages` — Player-to-player messaging

```sql
CREATE TABLE chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_chat_receiver_time (receiver_id, created_at),
    INDEX idx_chat_conversation (sender_id, receiver_id, created_at)
);
```

### 8. `api_keys` — For MCP/AI agent authentication

```sql
CREATE TABLE api_keys (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    key_hash BINARY(32) NOT NULL,
    key_prefix VARCHAR(12) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME DEFAULT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_apikeys_prefix (key_prefix),
    INDEX idx_apikeys_user (user_id)
);
```

---

## Modified Tables

### `users` — Add columns

```sql
ALTER TABLE users
    ADD COLUMN is_ai BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN net_worth BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD UNIQUE INDEX idx_users_username (user);
```

- `is_ai`: Distinguishes AI corporations from human players. AI badge in UI.
- `net_worth`: Cached calculation (money + inventory value at market prices). Updated periodically by tick engine. Used by leaderboard.
- `created_at`: Account creation time.

### `production` — DROP (replaced by `facilities` + `inventory`)

```sql
DROP TABLE IF EXISTS production;
```

### `offers` — DROP (replaced by `market_orders`)

```sql
DROP TABLE IF EXISTS offers;
```

---

## Migration Strategy

```sql
-- 1. Add new columns to users
ALTER TABLE users ADD COLUMN is_ai BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN net_worth BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 2. Create new tables
-- (all CREATE TABLE statements above)

-- 3. Migrate production data → inventory + facilities
INSERT INTO inventory (user_id, resource, quantity)
    SELECT user_id, resource, count FROM production WHERE count > 0;

-- Each "production" unit becomes a facility record
-- production.production was a count; create N facility rows per entry
-- (This requires a stored procedure or application-level migration)

-- 4. Migrate offers → market_orders
INSERT INTO market_orders (user_id, resource, order_type, price, quantity, created_at)
    SELECT user_id, resource,
        CASE WHEN buy THEN 'BUY' ELSE 'SELL' END,
        price, quantity, CURRENT_TIMESTAMP
    FROM offers;

-- 5. Seed market_prices with defaults
INSERT INTO market_prices (resource, last_trade_price) VALUES
    (0, 0), (1, 350), (2, 0), (3, 0), (4, 0), (5, 0),
    (6, 0), (7, 0), (8, 0), (9, 3500000), (10, 89900);

-- 6. Drop old tables
DROP TABLE production;
DROP TABLE offers;
```

**Note**: The `production.production` → facilities migration is lossy. If a player had `production=5` for wheat, we create 5 ACTIVE facility rows. Research data (`research`, `research_cost`) is dropped (MVP removes research mechanic).

---

## Cross-System Dependencies

| Table | Written By | Read By |
|-------|-----------|---------|
| `users` | Auth/signup, Operating Costs (money deduct), Market (trade settlement), Shop sales | All systems (user lookup), Leaderboard, API |
| `facilities` | Facility Management (build/idle/activate/downsize) | Tick Engine (operating costs, production), Production Display, API |
| `inventory` | Production (tick output), Market (trade settlement), Decay (spoilage), Shop (stocking) | Market (reserve/target calc), Production (input check), API |
| `market_orders` | API (place/cancel orders) | Matching Engine (order book) |
| `trades` | Matching Engine (after execution) | Price History API, Player trade log |
| `market_prices` | Matching Engine (update after trade) | Leaderboard (net worth), Market UI, Demand Forecast |
| `shops` | API (stock/price), Shop Sales tick phase | Shop Sales, API |
| `chat_messages` | Chat API (send) | Chat API (receive), MCP |
| `api_keys` | API (generate key) | Auth Filter (validate key) |

---

## Scalable Architecture

### Indexing Strategy
- **Composite indexes** on frequent query patterns: `(user_id, resource)` for inventory/facilities, `(resource, order_type, price)` for order matching, `(resource, executed_at)` for price history.
- **Covering indexes** where possible to avoid table lookups.

### Foreign Key Design
- All user-referencing tables cascade on delete (player account deletion cleans up everything).
- `trades` does NOT cascade (historical record preserved even if accounts deleted — use SET NULL if needed).

### Normalization Decisions
- `inventory` is normalized (one row per user per resource). No sparse columns.
- `market_prices` is denormalized (caches computed values). Acceptable because it's updated frequently by the matching engine and read even more frequently by the UI/API.
- `users.net_worth` is denormalized (cached calculation). Recomputed every N ticks. Avoids expensive JOIN for leaderboard.

### Resource Storage
- Resources stored as `INT` mapping to Java enum ordinal. **Risk**: adding/reordering enum values breaks existing data. **Mitigation**: assign explicit IDs in the Resource enum (`WHEAT(0)`, `BREAD(1)`, etc.) and never reorder. Add new resources at the end with next available ID.

---

## Key Implementation Details

- **Money as BIGINT cents**: All monetary values in cents. `$100.00` = `10000`. Avoids floating-point precision issues. `Money.java` already handles this format.
- **Resource as INT**: Maps to `Resource.getID()` (currently ordinal). When expanding from 11 to 32 resources, must assign stable explicit IDs.
- **Timestamps**: Use `DATETIME` (not `TIMESTAMP` which has 2038 limit on 32-bit). MariaDB 11 handles both well.
- **Facility state as ENUM**: MySQL/MariaDB ENUM is stored as 1-2 bytes internally. Fast comparison, type-safe.
- **Boolean fields**: MariaDB `BOOLEAN` is `TINYINT(1)`. Use 0/1.
- **Text encoding**: Schema uses `CHARACTER SET utf8` (from existing setup). Consider `utf8mb4` for full Unicode support in chat messages.
