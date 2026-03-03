-- Phase 3.2: System AI Corporations

-- Add AI fields to players table (if not already present)
ALTER TABLE players ADD COLUMN IF NOT EXISTS is_ai BOOLEAN DEFAULT FALSE;
ALTER TABLE players ADD COLUMN IF NOT EXISTS ai_strategy VARCHAR(50);

-- Add API keys table for Phase 3.3
CREATE TABLE IF NOT EXISTS api_keys (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    key_name VARCHAR(100) NOT NULL DEFAULT 'Default',
    api_key VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_used TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    INDEX idx_player (player_id),
    INDEX idx_api_key (api_key)
);
