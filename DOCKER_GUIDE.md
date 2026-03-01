# Docker Deployment Guide

This guide walks you through containerizing Trade Empire with **Docker Compose**, running Apache Tomcat 10 and MariaDB side-by-side with zero manual setup.

---

## Table of Contents

- [Quick Start](#quick-start)
- [What Gets Created](#what-gets-created)
- [File Overview](#file-overview)
- [Step-by-Step Explanation](#step-by-step-explanation)
  - [1. Dockerfile (Tomcat + App)](#1-dockerfile-tomcat--app)
  - [2. docker-compose.yml](#2-docker-composeyml)
  - [3. Database Init Script](#3-database-init-script)
  - [4. Provider.java Change](#4-providerjava-change)
- [Build & Run](#build--run)
- [Verify It Works](#verify-it-works)
- [Common Operations](#common-operations)
- [Environment Variables](#environment-variables)
- [Troubleshooting](#troubleshooting)
- [Production Hardening](#production-hardening)

---

## Quick Start

If you just want to get it running:

```bash
# 1. Make sure Docker and Docker Compose are installed
docker --version
docker compose version

# 2. From the project root:
docker compose up --build -d

# 3. Open in browser:
#    http://localhost:8080/

# 4. Stop:
docker compose down

# 5. Stop and wipe database:
docker compose down -v
```

That's it. The first build takes ~2 minutes (downloading base images + compiling Java). Subsequent starts are nearly instant.

---

## What Gets Created

```
┌─────────────────────────────────────────────────────────────┐
│  Docker Network: tycoon-net                                 │
│                                                             │
│  ┌─────────────────────┐    ┌──────────────────────────┐   │
│  │  mariadb             │    │  tomcat                   │   │
│  │  (MariaDB 11)        │◄───│  (Tomcat 10 + JDK 18)    │   │
│  │                      │    │                           │   │
│  │  Port: 3306 internal │    │  Port: 8080 → host:8080  │   │
│  │  Volume: db_data     │    │                           │   │
│  └─────────────────────┘    └──────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

| Container | Image               | Exposed Port | Purpose                          |
|-----------|---------------------|--------------|----------------------------------|
| `mariadb` | `mariadb:11`        | 3306 (internal only) | Database server            |
| `tomcat`  | Custom (see Dockerfile) | 8080 → host | Tomcat 10 + compiled app     |

---

## File Overview

You need to create 3 files in the project root:

```
business-tycoon/
├── Dockerfile              # Builds the Tomcat + app image
├── docker-compose.yml      # Orchestrates Tomcat + MariaDB
├── docker/
│   └── init.sql            # Database initialization (runs once)
└── ... (existing project files)
```

---

## Step-by-Step Explanation

### 1. Dockerfile (Tomcat + App)

The Dockerfile uses a **multi-stage build**:

1. **Stage 1 (`builder`)**: Uses a JDK image to compile all `.java` files into `.class` files
2. **Stage 2 (`runtime`)**: Uses the official Tomcat 10 image, copies in the compiled classes and web content

This means you don't need Java or any build tools on the host machine — Docker handles everything.

Create `Dockerfile` in the project root:

```dockerfile
# ============================================================
# Stage 1: Compile Java sources
# ============================================================
FROM eclipse-temurin:18-jdk AS builder

WORKDIR /build

# Copy source code and libraries
COPY src/ src/
COPY WebContent/WEB-INF/lib/ lib/

# Tomcat 10 Jakarta Servlet API (needed for compilation)
# We download the Servlet API jar for compile-time only
ADD https://repo1.maven.org/maven2/jakarta/servlet/jakarta.servlet-api/5.0.0/jakarta.servlet-api-5.0.0.jar lib/jakarta.servlet-api-5.0.0.jar

# Compile all Java files
RUN mkdir -p classes && \
    find src -name '*.java' > sources.txt && \
    javac \
      -cp "lib/*" \
      -d classes \
      -source 18 \
      -target 18 \
      @sources.txt

# ============================================================
# Stage 2: Package into Tomcat
# ============================================================
FROM tomcat:10.0-jdk18-temurin

# Remove default Tomcat webapps
RUN rm -rf /usr/local/tomcat/webapps/*

# Create the ROOT webapp directory structure
RUN mkdir -p /usr/local/tomcat/webapps/ROOT/WEB-INF/classes \
             /usr/local/tomcat/webapps/ROOT/WEB-INF/lib

# Copy web content (JSP, CSS, JS, web.xml)
COPY WebContent/ /usr/local/tomcat/webapps/ROOT/

# Copy compiled classes from builder stage
COPY --from=builder /build/classes/ /usr/local/tomcat/webapps/ROOT/WEB-INF/classes/

# Copy runtime libraries (MariaDB driver, json-simple)
COPY WebContent/WEB-INF/lib/ /usr/local/tomcat/webapps/ROOT/WEB-INF/lib/

EXPOSE 8080

CMD ["catalina.sh", "run"]
```

**What each section does:**

| Section | Purpose |
|---------|---------|
| `FROM eclipse-temurin:18-jdk AS builder` | Uses Eclipse Temurin JDK 18 to compile Java code |
| `ADD jakarta.servlet-api` | Downloads the Jakarta Servlet 5.0 API jar (needed only for compilation, Tomcat provides it at runtime) |
| `javac ... @sources.txt` | Compiles all `.java` files in `src/` against the libraries in `lib/` |
| `FROM tomcat:10.0-jdk18-temurin` | Official Apache Tomcat 10.0 image with JDK 18 |
| `rm -rf webapps/*` | Removes default Tomcat apps (ROOT, manager, etc.) |
| `COPY WebContent/ ... ROOT/` | Deploys our app as the ROOT webapp (accessible at `/`) |
| `COPY --from=builder classes/` | Copies compiled `.class` files from Stage 1 |

### 2. docker-compose.yml

Create `docker-compose.yml` in the project root:

```yaml
services:

  # ── MariaDB Database ────────────────────────────────────
  mariadb:
    image: mariadb:11
    container_name: tycoon-db
    restart: unless-stopped
    environment:
      MARIADB_ROOT_PASSWORD: rootpassword
      MARIADB_DATABASE: db
      MARIADB_USER: user
      MARIADB_PASSWORD: password
    volumes:
      # Persist database between restarts
      - db_data:/var/lib/mysql
      # Auto-run init script on first startup
      - ./docker/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
    networks:
      - tycoon-net
    healthcheck:
      test: ["CMD", "healthcheck.sh", "--connect", "--innodb_initialized"]
      interval: 5s
      timeout: 5s
      retries: 10

  # ── Tomcat Application Server ───────────────────────────
  tomcat:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: tycoon-app
    restart: unless-stopped
    ports:
      - "8080:8080"
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
```

**What each section does:**

| Section | Purpose |
|---------|---------|
| `mariadb` service | Runs MariaDB 11, creates database `db` and user `user` automatically via environment variables |
| `volumes: db_data` | Persists database files so data survives `docker compose down` / `up` cycles |
| `docker-entrypoint-initdb.d` | MariaDB runs any `.sql` files in this directory on **first startup only** (when the volume is empty) |
| `healthcheck` | Ensures MariaDB is fully ready before Tomcat starts connecting |
| `tomcat` service | Builds from our Dockerfile, maps port 8080 |
| `depends_on: condition: service_healthy` | Tomcat waits for MariaDB to pass health checks before starting |
| `tycoon-net` | Internal bridge network so containers can reach each other by hostname |

### 3. Database Init Script

Create `docker/init.sql`:

```sql
-- ============================================================
-- Trade Empire — Database Initialization
-- This runs automatically on first container startup.
-- ============================================================

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
```

This is essentially the same as `sql/configure_db.sql`, but adapted for Docker:
- No `CREATE USER` / `GRANT` — MariaDB's `MARIADB_USER` env var handles that
- No `CREATE DATABASE` — MariaDB's `MARIADB_DATABASE` env var handles that
- Uses `IF NOT EXISTS` for safety
- Uses `InnoDB` engine explicitly and `utf8mb4` charset

### 4. Provider.java Change

The one code change needed: update the database hostname from `localhost` to `mariadb` (the Docker service name).

In `src/database/Provider.java`, change:

```java
// Before (localhost — for running outside Docker)
String CONNECTION_URL = "jdbc:mariadb://localhost/db";

// After (mariadb — Docker service name)
String CONNECTION_URL = "jdbc:mariadb://mariadb/db";
```

**Why?** Inside the Docker network, containers reach each other by service name. The MariaDB container is named `mariadb` in `docker-compose.yml`, so that becomes its hostname on the `tycoon-net` network.

> **Tip:** To support both local and Docker development, you could read this from an environment variable instead:
> ```java
> String CONNECTION_URL = System.getenv("DB_URL") != null
>     ? System.getenv("DB_URL")
>     : "jdbc:mariadb://localhost/db";
> ```

---

## Build & Run

### First time

```bash
# Create the docker/ directory for the init script
mkdir -p docker

# Copy the init SQL (or create it as shown above)
# ... (see Step 3 above)

# Build and start everything
docker compose up --build -d
```

The `--build` flag ensures the Dockerfile is rebuilt. The `-d` flag runs in detached (background) mode.

### Watch the logs

```bash
# All services
docker compose logs -f

# Just Tomcat
docker compose logs -f tomcat

# Just MariaDB
docker compose logs -f mariadb
```

### Subsequent starts

```bash
# Start (no rebuild needed if code hasn't changed)
docker compose up -d

# Start with rebuild (after code changes)
docker compose up --build -d
```

---

## Verify It Works

### 1. Check containers are running

```bash
docker compose ps
```

Expected output:
```
NAME          SERVICE    STATUS      PORTS
tycoon-app    tomcat     Up          0.0.0.0:8080->8080/tcp
tycoon-db     mariadb    Up (healthy)   3306/tcp
```

### 2. Check Tomcat logs for startup

```bash
docker compose logs tomcat | tail -20
```

Look for:
```
INFO [main] org.apache.catalina.startup.Catalina.start Server startup in [XXX] milliseconds
```

### 3. Open in browser

Navigate to `http://localhost:8080/`

You should see the Trade Empire login page. Create an account and start playing.

### 4. Check database connection

```bash
# Connect to MariaDB inside the container
docker compose exec mariadb mysql -u user -ppassword db -e "SHOW TABLES;"
```

Expected:
```
+----------------+
| Tables_in_db   |
+----------------+
| offers         |
| production     |
| users          |
+----------------+
```

---

## Common Operations

### Stop everything

```bash
docker compose down
```

Database data is preserved in the `db_data` volume.

### Stop and wipe database

```bash
docker compose down -v
```

The `-v` flag removes volumes. Next `up` will re-create the database from `init.sql`.

### Rebuild after code changes

```bash
docker compose up --build -d
```

### Connect to MariaDB interactively

```bash
docker compose exec mariadb mysql -u user -ppassword db
```

### View a player's data

```bash
docker compose exec mariadb mysql -u user -ppassword db \
  -e "SELECT id, user, money FROM users;"
```

### Reset a player's money

```bash
docker compose exec mariadb mysql -u user -ppassword db \
  -e "UPDATE users SET money = 100000 WHERE user = 'PlayerName';"
```

### View Tomcat application logs

```bash
docker compose exec tomcat cat /usr/local/tomcat/logs/catalina.out
```

### Shell into the Tomcat container

```bash
docker compose exec tomcat bash
```

---

## Environment Variables

### MariaDB Container

| Variable                 | Default         | Description                  |
|--------------------------|-----------------|------------------------------|
| `MARIADB_ROOT_PASSWORD`  | `rootpassword`  | Root password for MariaDB    |
| `MARIADB_DATABASE`       | `db`            | Database name to create      |
| `MARIADB_USER`           | `user`          | Application database user    |
| `MARIADB_PASSWORD`       | `password`      | Application database password|

### Customizing

To change credentials, update **both** `docker-compose.yml` and `Provider.java` (or use environment variable injection as described in Step 4).

---

## Troubleshooting

### Tomcat exits immediately / "Connection refused"

**Cause:** Tomcat started before MariaDB was ready.

**Fix:** The `depends_on` with `condition: service_healthy` should handle this. If it still fails:

```bash
# Check MariaDB health
docker compose ps
docker compose logs mariadb

# Restart just Tomcat
docker compose restart tomcat
```

### "Communications link failure" in Tomcat logs

**Cause:** `Provider.java` still points to `localhost` instead of `mariadb`.

**Fix:** Change `CONNECTION_URL` to `jdbc:mariadb://mariadb/db` and rebuild:

```bash
docker compose up --build -d
```

### Port 8080 already in use

**Cause:** Another process (local Tomcat, Jenkins, etc.) is using port 8080.

**Fix:** Change the host port in `docker-compose.yml`:

```yaml
ports:
  - "9090:8080"   # Access at http://localhost:9090/
```

### Database tables not created

**Cause:** The init script only runs when the volume is fresh. If you changed `init.sql` after the first run, the changes won't apply.

**Fix:**

```bash
docker compose down -v    # Remove volumes
docker compose up -d      # Recreate from scratch
```

### Java compilation errors during build

**Cause:** Missing dependencies or wrong Java version.

**Fix:** Check the Dockerfile uses `eclipse-temurin:18-jdk` and all JARs are in `WebContent/WEB-INF/lib/`.

```bash
# Rebuild with no cache to start fresh
docker compose build --no-cache
```

---

## Production Hardening

For a production deployment, consider these improvements:

### 1. Use environment variables for secrets

```yaml
# docker-compose.yml
tomcat:
  environment:
    DB_URL: jdbc:mariadb://mariadb/db
    DB_USER: user
    DB_PASS: password
```

Update `Provider.java` to read from `System.getenv()`.

### 2. Add a reverse proxy (Nginx)

```yaml
# Add to docker-compose.yml
nginx:
  image: nginx:alpine
  ports:
    - "80:80"
    - "443:443"
  volumes:
    - ./nginx.conf:/etc/nginx/nginx.conf:ro
    - ./certs:/etc/nginx/certs:ro
  depends_on:
    - tomcat
```

### 3. Enable HTTPS

Use Let's Encrypt with certbot or provide your own certificates through the Nginx reverse proxy.

### 4. Limit database exposure

The MariaDB container already has no host port mapping — it's only accessible within the Docker network. Keep it that way.

### 5. Set resource limits

```yaml
tomcat:
  deploy:
    resources:
      limits:
        memory: 512M
        cpus: "1.0"

mariadb:
  deploy:
    resources:
      limits:
        memory: 256M
        cpus: "0.5"
```

### 6. Use Docker secrets

For sensitive values (passwords, API keys), use Docker secrets instead of environment variables:

```yaml
secrets:
  db_password:
    file: ./secrets/db_password.txt

services:
  mariadb:
    secrets:
      - db_password
    environment:
      MARIADB_PASSWORD_FILE: /run/secrets/db_password
```

### 7. Backup the database

```bash
# Dump the database
docker compose exec mariadb mysqldump -u user -ppassword db > backup.sql

# Restore from backup
docker compose exec -T mariadb mysql -u user -ppassword db < backup.sql
```
