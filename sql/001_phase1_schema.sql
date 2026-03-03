-- ==========================================================
-- Trade Empire — Phase 1 Schema Migration
-- Run against an existing database to add Phase 1 tables.
-- All statements use IF NOT EXISTS for idempotency.
--
-- Usage (Docker):
--   docker compose exec mariadb mysql -u user -ppassword db < sql/001_phase1_schema.sql
-- ==========================================================

-- Players (REST API users, separate from legacy 'users' table)
CREATE TABLE IF NOT EXISTS players (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    cash DECIMAL(15, 2) NOT NULL DEFAULT 1000.00,
    net_worth DECIMAL(15, 2) NOT NULL DEFAULT 1000.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_ai BOOLEAN DEFAULT FALSE,
    ai_strategy VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Auth Tokens (session tokens for REST API)
CREATE TABLE IF NOT EXISTS auth_tokens (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    token VARCHAR(64) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    INDEX idx_token (token),
    INDEX idx_player (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Facilities (production buildings)
CREATE TABLE IF NOT EXISTS facilities (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    resource_name VARCHAR(100) NOT NULL,
    state ENUM('active', 'idle', 'destroyed') DEFAULT 'active',
    production_capacity INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    INDEX idx_player (player_id),
    INDEX idx_resource (resource_name),
    INDEX idx_state (state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Inventory (per-player resource storage)
CREATE TABLE IF NOT EXISTS inventory (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    resource_name VARCHAR(100) NOT NULL,
    quantity DECIMAL(15, 2) NOT NULL DEFAULT 0,
    last_decay_tick INT NOT NULL DEFAULT 0,
    UNIQUE KEY unique_player_resource (player_id, resource_name),
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    INDEX idx_player (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Market Orders (buy/sell offers)
CREATE TABLE IF NOT EXISTS market_orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    resource_name VARCHAR(100) NOT NULL,
    side ENUM('buy', 'sell') NOT NULL,
    price DECIMAL(15, 2) NOT NULL,
    quantity INT NOT NULL,
    quantity_filled INT NOT NULL DEFAULT 0,
    keep_reserve INT,        -- sell only: keep this much in stock, sell the rest
    target_quantity INT,     -- buy only: buy up to this total quantity
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    INDEX idx_resource_price (resource_name, price),
    INDEX idx_player (player_id),
    INDEX idx_side (side)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Price History (for charts and analytics)
CREATE TABLE IF NOT EXISTS price_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    resource_name VARCHAR(100) NOT NULL,
    tick_number INT NOT NULL,
    buy_price DECIMAL(15, 2),
    sell_price DECIMAL(15, 2),
    volume_traded INT NOT NULL DEFAULT 0,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_resource_tick (resource_name, tick_number),
    INDEX idx_resource (resource_name),
    INDEX idx_tick (tick_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Shops (retail locations for consumer goods)
CREATE TABLE IF NOT EXISTS shops (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    shop_name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    INDEX idx_player (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Shop Inventory (what's stocked in each shop)
CREATE TABLE IF NOT EXISTS shop_inventory (
    id INT AUTO_INCREMENT PRIMARY KEY,
    shop_id INT NOT NULL,
    resource_name VARCHAR(100) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    set_price DECIMAL(15, 2),   -- NULL means match market price
    UNIQUE KEY unique_shop_resource (shop_id, resource_name),
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Shop Sales (historical sales data)
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Chat Messages (player-to-player)
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Game State (singleton: tracks global tick count)
CREATE TABLE IF NOT EXISTS game_state (
    id INT PRIMARY KEY DEFAULT 1,
    current_tick INT NOT NULL DEFAULT 0,
    last_tick_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    server_status ENUM('running', 'paused', 'stopped') DEFAULT 'running',
    CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Central Bank (accumulates market fees and luxury taxes)
CREATE TABLE IF NOT EXISTS central_bank (
    id INT PRIMARY KEY DEFAULT 1,
    market_fee_reserve DECIMAL(15, 2) NOT NULL DEFAULT 0,
    luxury_tax_reserve DECIMAL(15, 2) NOT NULL DEFAULT 0,
    CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Initialize singleton rows
INSERT IGNORE INTO game_state (id, current_tick, server_status) VALUES (1, 0, 'running');
INSERT IGNORE INTO central_bank (id, market_fee_reserve, luxury_tax_reserve) VALUES (1, 0.00, 0.00);
