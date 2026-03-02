# Trade Empire — Project Memory

## Quick Start (Reproduce on Any PC)

Only requirement: **Docker + Docker Compose**.

```bash
git clone <repo-url>
cd business-tycoon
chmod +x setup.sh
./setup.sh
```

Opens at `http://localhost:8080/`

## What setup.sh Does

1. Checks Docker/Compose are installed
2. Patches `src/database/Provider.java` — swaps `localhost` → `mariadb` (Docker service hostname)
3. Generates `docker/init.sql` (DB schema), `Dockerfile` (multi-stage: JDK 18 compile → Tomcat 10 runtime), `docker-compose.yml` (Tomcat + MariaDB 11), `.dockerignore`
4. Runs `docker compose up --build -d`
5. Waits for Tomcat startup, prints URL

## setup.sh Flags

| Flag | Action |
|------|--------|
| `./setup.sh` | Build and start |
| `./setup.sh --stop` | Stop containers, keep data |
| `./setup.sh --reset` | Stop + wipe database |
| `./setup.sh --logs` | Tail live logs |
| `./setup.sh --status` | Container health |

## Credentials (configurable at top of setup.sh)

- **DB name:** `db`
- **DB user:** `user`
- **DB password:** `password`
- **Host port:** `8080`

## Tech Stack

- Java 18, Jakarta Servlet 5.0, Apache Tomcat 10.0
- MariaDB 11 (JDBC driver 3.0.4 bundled in `WebContent/WEB-INF/lib/`)
- jQuery 3.6, custom CSS design system (no framework)

## Key Files

- `setup.sh` — One-command deployment
- `README.md` — Full project documentation
- `DOCKER_GUIDE.md` — Detailed Docker guide
- `sql/configure_db.sql` — Manual DB setup (non-Docker)
- `src/database/Provider.java` — DB connection config

## Branch

Development branch: `claude/game-ui-design-plan-6NtmH`
