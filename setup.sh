#!/usr/bin/env bash
# ============================================================
#  Trade Empire — One-Command Setup
# ============================================================
#
#  REQUIREMENTS (only two):
#    1. Docker Engine   — https://docs.docker.com/engine/install/
#    2. Docker Compose  — ships with Docker Desktop, or:
#                         https://docs.docker.com/compose/install/
#
#  USAGE:
#    chmod +x setup.sh
#    ./setup.sh              # build and start (default)
#    ./setup.sh  --stop      # stop containers, keep data
#    ./setup.sh  --reset     # stop containers, wipe database
#    ./setup.sh  --logs      # tail live logs
#    ./setup.sh  --status    # show container health
#
# ============================================================

set -euo pipefail

# ── Configuration ──────────────────────────────────────────
# Change these if you want different credentials or ports.

DB_NAME="db"
DB_USER="user"
DB_PASS="password"
DB_ROOT_PASS="rootpassword"
HOST_PORT="8080"                       # port exposed on your machine
DB_HOST="mariadb"                      # Docker service name (do not change
                                       # unless you also rename the compose service)

# ── Resolve project root (directory this script lives in) ──

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ── Colors ─────────────────────────────────────────────────

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'   # No Color

info()  { printf "${CYAN}[INFO]${NC}  %s\n" "$*"; }
ok()    { printf "${GREEN}[OK]${NC}    %s\n" "$*"; }
warn()  { printf "${YELLOW}[WARN]${NC}  %s\n" "$*"; }
fail()  { printf "${RED}[FAIL]${NC}  %s\n" "$*"; exit 1; }

# ── Load ANTHROPIC_API_KEY from api_key.txt ────────────────

ANTHROPIC_API_KEY=""
if [ -f "api_key.txt" ]; then
  ANTHROPIC_API_KEY=$(cat api_key.txt | tr -d '\n\r')
  ok "Loaded ANTHROPIC_API_KEY from api_key.txt"
else
  warn "api_key.txt not found — AI corporations will not work without ANTHROPIC_API_KEY"
fi

# ── Handle flags ───────────────────────────────────────────

case "${1:-}" in
  --stop)
    info "Stopping containers (data preserved)…"
    docker compose down
    ok "Stopped."
    exit 0
    ;;
  --reset)
    warn "Stopping containers and DELETING all database data…"
    docker compose down -v
    ok "Wiped."
    exit 0
    ;;
  --logs)
    docker compose logs -f
    exit 0
    ;;
  --status)
    docker compose ps
    exit 0
    ;;
  --help|-h)
    head -20 "$0" | tail -14
    exit 0
    ;;
esac

# ============================================================
#  Step 1 — Check requirements
# ============================================================
# We only need Docker (with Compose).  Nothing else.

info "Checking requirements…"

if ! command -v docker &>/dev/null; then
  fail "Docker is not installed.  Install it: https://docs.docker.com/engine/install/"
fi

# "docker compose" (v2 plugin) vs "docker-compose" (standalone v1)
if docker compose version &>/dev/null; then
  COMPOSE="docker compose"
elif command -v docker-compose &>/dev/null; then
  COMPOSE="docker-compose"
else
  fail "Docker Compose not found.  Install it: https://docs.docker.com/compose/install/"
fi

ok "Docker $(docker --version | grep -oP '\d+\.\d+\.\d+')"
ok "Compose $(${COMPOSE} version --short 2>/dev/null || ${COMPOSE} version)"

# ============================================================
#  Step 2 — Patch Provider.java for Docker networking
# ============================================================
# Inside Docker, containers talk by service name, not localhost.
# We swap "localhost" → the DB_HOST variable ("mariadb") in the
# JDBC URL so Tomcat can reach the MariaDB container.

PROVIDER="src/database/Provider.java"

if [ ! -f "$PROVIDER" ]; then
  fail "Cannot find $PROVIDER — run this script from the project root."
fi

if grep -q "mariadb://localhost" "$PROVIDER"; then
  info "Patching $PROVIDER: localhost → $DB_HOST"
  # Use a temp file so this works on both macOS and Linux sed
  sed "s|mariadb://localhost|mariadb://${DB_HOST}|g" "$PROVIDER" > "${PROVIDER}.tmp"
  mv "${PROVIDER}.tmp" "$PROVIDER"
  ok "Provider.java patched."
elif grep -q "mariadb://${DB_HOST}" "$PROVIDER"; then
  ok "Provider.java already points to $DB_HOST — no patch needed."
else
  warn "Provider.java has an unexpected CONNECTION_URL. Verify it manually."
fi

# ============================================================
#  Step 3 — Generate docker/init.sql
# ============================================================
# MariaDB auto-runs .sql files in /docker-entrypoint-initdb.d/
# on first boot (when the data volume is empty).  We generate
# the schema here so there is a single source of truth.

info "Generating docker/init.sql…"
mkdir -p docker

cat > docker/init.sql <<'EOSQL'
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

-- ==========================================================
-- Phase 1: New tables (REST API, Tick Engine, Market)
-- ==========================================================

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

CREATE TABLE IF NOT EXISTS market_orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    resource_name VARCHAR(100) NOT NULL,
    side ENUM('buy', 'sell') NOT NULL,
    price DECIMAL(15, 2) NOT NULL,
    quantity INT NOT NULL,
    quantity_filled INT NOT NULL DEFAULT 0,
    keep_reserve INT,
    target_quantity INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    INDEX idx_resource_price (resource_name, price),
    INDEX idx_player (player_id),
    INDEX idx_side (side)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

CREATE TABLE IF NOT EXISTS shops (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    shop_name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    INDEX idx_player (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shop_inventory (
    id INT AUTO_INCREMENT PRIMARY KEY,
    shop_id INT NOT NULL,
    resource_name VARCHAR(100) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    set_price DECIMAL(15, 2),
    UNIQUE KEY unique_shop_resource (shop_id, resource_name),
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

CREATE TABLE IF NOT EXISTS game_state (
    id INT PRIMARY KEY DEFAULT 1,
    current_tick INT NOT NULL DEFAULT 0,
    last_tick_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    server_status ENUM('running', 'paused', 'stopped') DEFAULT 'running',
    CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS central_bank (
    id INT PRIMARY KEY DEFAULT 1,
    market_fee_reserve DECIMAL(15, 2) NOT NULL DEFAULT 0,
    luxury_tax_reserve DECIMAL(15, 2) NOT NULL DEFAULT 0,
    CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO game_state (id, current_tick, server_status) VALUES (1, 0, 'running');
INSERT IGNORE INTO central_bank (id, market_fee_reserve, luxury_tax_reserve) VALUES (1, 0.00, 0.00);
EOSQL

ok "docker/init.sql"

# ============================================================
#  Step 4 — Generate Dockerfile (multi-stage build)
# ============================================================
# Stage 1 compiles Java 18 sources against the Jakarta Servlet
#   API and the bundled JARs (MariaDB driver, json-simple).
# Stage 2 copies the compiled classes into an official Tomcat
#   10 image and deploys as the ROOT webapp.

info "Generating Dockerfile…"

cat > Dockerfile <<'EOFILE'
# ── Stage 1: Compile Java ──────────────────────────────────
FROM eclipse-temurin:18-jdk AS builder
WORKDIR /build

COPY src/ src/
COPY WebContent/WEB-INF/lib/ lib/

# Jakarta Servlet API — compile-time only (Tomcat ships its own)
ADD https://repo1.maven.org/maven2/jakarta/servlet/jakarta.servlet-api/5.0.0/jakarta.servlet-api-5.0.0.jar lib/jakarta.servlet-api-5.0.0.jar

RUN mkdir -p classes && \
    find src -name '*.java' > sources.txt && \
    javac -cp "lib/*" -d classes -source 17 -target 17 @sources.txt

# ── Stage 2: Tomcat runtime ────────────────────────────────
FROM tomcat:10.0

RUN rm -rf /usr/local/tomcat/webapps/*
RUN mkdir -p /usr/local/tomcat/webapps/ROOT/WEB-INF/classes \
             /usr/local/tomcat/webapps/ROOT/WEB-INF/lib

COPY WebContent/                        /usr/local/tomcat/webapps/ROOT/
COPY --from=builder /build/classes/     /usr/local/tomcat/webapps/ROOT/WEB-INF/classes/
COPY WebContent/WEB-INF/lib/            /usr/local/tomcat/webapps/ROOT/WEB-INF/lib/
COPY config/                            /usr/local/tomcat/config/

EXPOSE 8080
CMD ["catalina.sh", "run"]
EOFILE

ok "Dockerfile"

# ============================================================
#  Step 5 — Generate docker-compose.yml
# ============================================================
# Two services:
#   mariadb  — database, internal only, persistent volume,
#              health-checked so Tomcat waits for it.
#   tomcat   — built from our Dockerfile, port-mapped to host.

info "Generating docker-compose.yml…"

cat > docker-compose.yml <<EOYML
services:

  mariadb:
    image: mariadb:11
    container_name: tycoon-db
    restart: unless-stopped
    environment:
      MARIADB_ROOT_PASSWORD: ${DB_ROOT_PASS}
      MARIADB_DATABASE:      ${DB_NAME}
      MARIADB_USER:          ${DB_USER}
      MARIADB_PASSWORD:      ${DB_PASS}
    ports:
      - "3306:3306"
    volumes:
      - db_data:/var/lib/mysql
      - ./docker/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
    networks:
      - tycoon-net
    healthcheck:
      test: ["CMD", "healthcheck.sh", "--connect", "--innodb_initialized"]
      interval: 5s
      timeout: 5s
      retries: 10

  tomcat:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: tycoon-app
    restart: unless-stopped
    environment:
      DB_HOST: mariadb
      DB_NAME: ${DB_NAME}
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}
    ports:
      - "${HOST_PORT}:8080"
    depends_on:
      mariadb:
        condition: service_healthy
    networks:
      - tycoon-net

volumes:
  db_data:

networks:
  tycoon-net:
    driver: bridge
EOYML

ok "docker-compose.yml"

# ============================================================
#  Step 6 — Generate .dockerignore
# ============================================================
# Keeps the build context small so `docker build` is fast.

info "Generating .dockerignore…"

cat > .dockerignore <<'EOIGN'
.git
.gitignore
.settings
.classpath
.project
.metadata
bin/
tomcat-config/
sql/
docker/
*.md
EOIGN

ok ".dockerignore"

# ============================================================
#  Step 7 — Build & start
# ============================================================

echo ""
info "Building images and starting containers…"
echo ""

# Export ANTHROPIC_API_KEY so docker compose can use it
export ANTHROPIC_API_KEY
${COMPOSE} up --build -d

# ============================================================
#  Step 8 — Wait for Tomcat to finish starting
# ============================================================
# Poll the Tomcat container logs for the "Server startup" line.
# Times out after 90 seconds.

info "Waiting for Tomcat to start…"

TIMEOUT=90
ELAPSED=0
while [ $ELAPSED -lt $TIMEOUT ]; do
  if docker logs tycoon-app 2>&1 | grep -q "Server startup in"; then
    break
  fi
  sleep 2
  ELAPSED=$((ELAPSED + 2))
  printf "."
done
echo ""

if [ $ELAPSED -ge $TIMEOUT ]; then
  warn "Tomcat did not report startup within ${TIMEOUT}s.  Check logs:"
  echo "  ${COMPOSE} logs -f tomcat"
  exit 1
fi

# ============================================================
#  Step 9 — Verify
# ============================================================

echo ""
printf "${BOLD}============================================${NC}\n"
printf "${BOLD}  Trade Empire is running!${NC}\n"
printf "${BOLD}============================================${NC}\n"
echo ""
ok "Tomcat:   http://localhost:${HOST_PORT}/"
ok "MariaDB:  internal on tycoon-net:3306"
echo ""

${COMPOSE} ps

echo ""
printf "${CYAN}Useful commands:${NC}\n"
echo "  ./setup.sh --logs      Tail live logs"
echo "  ./setup.sh --status    Container health"
echo "  ./setup.sh --stop      Stop (keep data)"
echo "  ./setup.sh --reset     Stop + wipe database"
echo ""
echo "  ${COMPOSE} exec mariadb mysql -u ${DB_USER} -p${DB_PASS} ${DB_NAME}"
echo "                          ↑ Interactive DB shell"
echo ""
