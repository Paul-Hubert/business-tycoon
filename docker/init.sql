-- ==========================================================
-- Trade Empire — Auto-generated database schema
-- Runs once on first container startup.
-- ==========================================================

USE db;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user VARCHAR(128) NOT NULL,
    pass BINARY(64) NOT NULL,
    money BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS production (
    user_id BIGINT NOT NULL,
    resource INT NOT NULL,
    count BIGINT NOT NULL,
    production BIGINT NOT NULL,
    research_cost BIGINT NOT NULL,
    research BIGINT NOT NULL,
    FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS offers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resource INT NOT NULL,
    buy BOOLEAN NOT NULL,
    price BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
