# Trade Empire (Business Tycoon)

A multiplayer browser-based economic simulation game where players produce resources, invest in research, craft goods, and trade with each other on a live marketplace. Built with Java Servlets (Jakarta EE), JSP, jQuery, and MariaDB.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Database Schema](#database-schema)
- [Backend — Servlets & Simulation](#backend--servlets--simulation)
- [Frontend — JSP, CSS, JavaScript](#frontend--jsp-css--javascript)
- [Design System](#design-system)
- [Game Mechanics](#game-mechanics)
- [API Reference](#api-reference)
- [Prerequisites](#prerequisites)
- [Local Development Setup](#local-development-setup)
- [Docker Deployment](#docker-deployment)
- [Configuration Reference](#configuration-reference)
- [Authors](#authors)

---

## Overview

Trade Empire is a real-time multiplayer economic simulation. Every second, a server-side simulation tick processes all players:

1. **Raw resources** (wheat, iron, copper, gold, petrol) are produced based on each player's production level.
2. **Crafted goods** (bread, steel, plastic, circuits) are manufactured by consuming raw materials.
3. **Consumer products** (cars, phones) are assembled from crafted components.
4. **Research investment** improves production efficiency over time.
5. **The marketplace** matches buy/sell offers between players every tick.
6. **Consumer goods** (bread, cars, phones) auto-sell at real-world stock prices fetched from a financial API.

Players start with $1,000.00 and compete to accumulate the most wealth.

---

## Architecture

```
Browser (jQuery + JSP)
    |
    | HTTP (GET/POST, JSON)
    |
Apache Tomcat 10.x (Jakarta Servlet 5.0)
    |
    |-- IndexServlet   GET/POST /        Login/signup/logout + redirect
    |-- GameServlet    GET      /game    Serves game.jsp (authenticated)
    |-- ActionServlet  POST     /action  All game actions (buy, sell, produce, research)
    |-- UpdateServlet  GET/POST /update  Returns JSON state via update.jsp
    |-- SimulationServlet (WebListener) — Background scheduled tasks:
    |       |-- World.step()   every 1s   — production, crafting, research for all players
    |       |-- Market.step()  every 1s   — order matching for all resources
    |       |-- Market.updatePrice() every 15min — fetch real stock prices
    |
MariaDB
    |-- users       (id, user, pass, money)
    |-- production  (user_id, resource, count, production, research_cost, research)
    |-- offers      (id, user_id, resource, buy, price, quantity)
```

---

## Technology Stack

| Layer     | Technology                                                      |
|-----------|-----------------------------------------------------------------|
| Runtime   | Java 18+, Jakarta Servlet 5.0                                   |
| Server    | Apache Tomcat 10.0.x                                            |
| Database  | MariaDB (any recent version)                                    |
| JDBC      | MariaDB Java Client 3.0.4 (`WebContent/WEB-INF/lib/`)          |
| JSON      | json-simple 1.1.1 (`WebContent/WEB-INF/lib/`)                  |
| Frontend  | JSP, jQuery 3.6, custom CSS design system (no Bootstrap in game)|
| IDE       | Eclipse JEE (project files included)                            |

---

## Project Structure

```
business-tycoon/
├── src/
│   ├── data/                          # Domain models
│   │   ├── Resource.java              # Enum: wheat, bread, iron, steel, copper, gold, petrol, plastic, circuit, car, phone
│   │   ├── ResourceStack.java         # (Resource, count) tuple for recipes
│   │   ├── Recipe.java                # Crafting recipe (result + ingredients[])
│   │   ├── Crafting.java              # Static recipe registry
│   │   ├── Money.java                 # Money formatting/parsing (stored as long cents)
│   │   ├── User.java                  # User model, auth (login/signup/logout), DB persistence
│   │   ├── Production.java            # Per-user production map (Resource → ResourceProduction)
│   │   ├── ResourceProduction.java    # Per-resource: count, production level, research, DB CRUD
│   │   ├── Offer.java                 # Single marketplace offer, DB CRUD
│   │   └── Offers.java                # Per-user offer collection, search logic
│   ├── database/                      # Database layer
│   │   ├── Provider.java              # JDBC connection constants (driver, URL, user, pass)
│   │   ├── ConnectionProvider.java    # Singleton JDBC connection manager
│   │   └── PasswordHash.java          # SHA-512 + salt password hashing
│   ├── exception/
│   │   └── NotEnoughMoneyException.java
│   ├── servlet/                       # HTTP layer
│   │   ├── IndexServlet.java          # GET/POST /        — login page + auth actions
│   │   ├── GameServlet.java           # GET      /game    — game dashboard (auth required)
│   │   ├── ActionServlet.java         # POST     /action  — game actions dispatcher
│   │   ├── UpdateServlet.java         # GET/POST /update  — JSON state endpoint
│   │   └── SimulationServlet.java     # WebListener — background simulation scheduler
│   └── simulation/                    # Game simulation engine
│       ├── World.java                 # Per-tick: production, crafting, research for all users
│       └── Market.java                # Per-tick: order matching; per-15min: real stock price fetch
├── sql/
│   ├── configure_db.sql               # Database + user + table creation script
│   ├── reset_db.sql                   # Drop database + user (cleanup)
│   └── memo.txt                       # Quick reference notes
├── WebContent/
│   ├── META-INF/
│   │   └── MANIFEST.MF
│   ├── WEB-INF/
│   │   ├── web.xml                    # Servlet 5.0 descriptor, static file mappings
│   │   ├── lib/
│   │   │   ├── json-simple-1.1.1.jar
│   │   │   └── mariadb-java-client-3.0.4.jar
│   │   ├── index.jsp                  # Login/signup page
│   │   ├── game.jsp                   # Main game dashboard
│   │   └── update.jsp                 # JSON API response template
│   ├── css/
│   │   ├── variables.css              # Design tokens (colors, typography, spacing, shadows)
│   │   ├── layout.css                 # Page structure, grid, responsive breakpoints
│   │   ├── components.css             # Buttons, cards, forms, badges, toasts, offers
│   │   ├── animations.css             # Value flashes, pulse, toast enter/exit, hover effects
│   │   ├── game.css                   # Game-specific overrides
│   │   └── common.css                 # App-wide overrides
│   └── js/
│       ├── common.js                  # Currency formatting utilities
│       └── game.js                    # Game logic: API calls, DOM updates, view switching, toasts
├── .classpath                         # Eclipse classpath (Java 18, Tomcat 10)
├── .gitignore
├── .settings/                         # Eclipse project settings
└── README.md                          # This file
```

---

## Database Schema

All monetary values are stored as **`BIGINT` cents** (e.g., `$10.00` = `1000`).

### `users`

| Column  | Type           | Description                           |
|---------|----------------|---------------------------------------|
| `id`    | BIGINT PK AUTO | Unique user ID                        |
| `user`  | VARCHAR(128)   | Username                              |
| `pass`  | BINARY(64)     | SHA-512 salted password hash          |
| `money` | BIGINT         | Balance in cents (starting: 100000 = $1,000.00) |

### `production`

| Column          | Type       | Description                                  |
|-----------------|------------|----------------------------------------------|
| `user_id`       | BIGINT FK  | References `users(id)` ON DELETE CASCADE      |
| `resource`      | INT        | Resource enum ordinal (0-10)                  |
| `count`         | BIGINT     | Current stock quantity                        |
| `production`    | BIGINT     | Production level (units added per tick)       |
| `research_cost` | BIGINT     | Research investment per tick (cents)           |
| `research`      | BIGINT     | Accumulated research points                   |

### `offers`

| Column     | Type           | Description                             |
|------------|----------------|-----------------------------------------|
| `id`       | BIGINT PK AUTO | Offer ID                                |
| `user_id`  | BIGINT FK      | References `users(id)`                   |
| `resource` | INT            | Resource enum ordinal                    |
| `buy`      | BOOLEAN        | `true` = buy offer, `false` = sell offer |
| `price`    | BIGINT         | Price per unit in cents                  |
| `quantity` | BIGINT         | Target quantity                          |

### Setup

```sql
-- Create from scratch:
sudo mysql -u root -p < sql/configure_db.sql

-- Tear down:
sudo mysql -u root -p < sql/reset_db.sql
```

---

## Backend — Servlets & Simulation

### Servlets

| Servlet               | URL        | Method   | Purpose                                                                     |
|-----------------------|------------|----------|-----------------------------------------------------------------------------|
| `IndexServlet`        | `/`        | GET      | Show login page (redirect to `/game` if already authenticated)              |
| `IndexServlet`        | `/`        | POST     | Handle `login`, `signup`, `logout` actions                                  |
| `GameServlet`         | `/game`    | GET      | Serve `game.jsp` (redirect to `/` if not authenticated)                     |
| `ActionServlet`       | `/action`  | POST     | Dispatch game actions: `addProduction`, `publish`, `search`, `delete`, `changeResearch` |
| `UpdateServlet`       | `/update`  | GET/POST | Return JSON game state via `update.jsp`                                     |
| `SimulationServlet`   | (listener) | —        | Schedule background simulation ticks                                        |

### Simulation Engine

**`SimulationServlet`** (a `ServletContextListener`) starts two scheduled tasks on server boot:

1. **Every 1 second** — `World.step()` + `Market.step()`
   - Iterates all users in the database
   - For each user, for each resource:
     - Deducts research cost, probabilistically increases research points
     - Calculates effective production = `production * (1 + research/10000)`
     - Raw resources: adds production directly to stock
     - Crafted resources: consumes ingredients, adds output (limited by ingredient stock)
   - Market: matches buy/sell offers, executes trades, transfers money + goods

2. **Every 15 minutes** — `Market.updatePrice()`
   - Fetches real stock prices from Yahoo Finance API (WMT, AAPL, TSLA)
   - Maps them to consumer good prices: bread (WMT/10), phone (AAPL*5), car (TSLA*10)

### Authentication

- Session-based via `HttpSession`
- Passwords hashed with **SHA-512 + 4-byte salt** (`PasswordHash.java`)
- User ID stored in session attribute `"id"`

### Database Connection

- Single static JDBC connection via `ConnectionProvider`
- Connection params defined in `Provider.java`:
  - Driver: `org.mariadb.jdbc.Driver`
  - URL: `jdbc:mariadb://localhost/db`
  - Username: `user`
  - Password: `password`

---

## Frontend — JSP, CSS, JavaScript

### Pages

| File          | Route   | Description                                         |
|---------------|---------|-----------------------------------------------------|
| `index.jsp`   | `/`     | Centered login card with username/password form      |
| `game.jsp`    | `/game` | Full dashboard: topbar, sidebar, resource grid, market |
| `update.jsp`  | `/update` | JSON template returning full game state             |

### JavaScript (`game.js`)

The frontend polls `/update` every 2 seconds and updates the DOM:

- **`refresh()`** — GET `/update`, pass response to `reload()`
- **`reload(data)`** — Update money, leaderboard, all resource cards, sidebar counts, offers list
- **`addProduction(id)`** — POST to add production for a resource
- **`changeResearch(id, el)`** — POST to change research investment
- **`search()` / `publish()`** — POST marketplace search/publish
- **`deleteOffer(e)`** — POST to delete an offer
- **`switchView(view)`** — Toggle between Production and Market views
- **`showToast(msg, type)`** — Display animated notification toast
- Value-change animations (green flash for increase, red for decrease)

### Currency Formatting (`common.js`)

- `formatNumber(n)` — Adds thousand separators: `1234567 → 1,234,567`
- `formatMoney(m)` — Converts cents to dollars: `150000 → 1,500.00`
- `money(m)` — Divides by 100 for display

---

## Design System

The UI uses a custom CSS design system (no framework dependency) built from four layers:

### `variables.css` — Design Tokens

80+ CSS custom properties organized into:

| Category    | Examples                                                             |
|-------------|----------------------------------------------------------------------|
| Colors      | Deep navy backgrounds (`#0b1117` → `#243447`), gold accent (`#f0b429`) |
| Status      | Green (`#27ae60`), red (`#e74c3c`), blue (`#3498db`) with glow variants |
| Typography  | Inter (sans), JetBrains Mono (mono), modular scale 11px–36px         |
| Spacing     | 4px–64px scale (`--space-1` through `--space-16`)                    |
| Shadows     | 4 depth levels + colored glow shadows                                 |
| Transitions | Custom easings (out, in-out, spring), 3 duration tiers               |
| Z-index     | 5-tier scale (base, dropdown, sticky, overlay, modal, toast)         |

### `layout.css` — Page Structure

- `.page` → `.topbar` + `.main` → `.sidebar` + `.content`
- `.resource-grid` — CSS Grid with `auto-fill` for responsive resource cards
- `.split` — Two-column layout for marketplace
- `.login-page` / `.login-card` — Centered login form
- Responsive breakpoints at 1024px (tablet), 768px (mobile), 480px (small)

### `components.css` — UI Components

- **Buttons** — `.btn--primary` (gold), `--secondary` (outlined), `--ghost`, `--danger`, `--success` + size variants
- **Cards** — `.card` with `.card__header`, `.card__body`, `.card__stat`, `.card__footer`, `.card__recipe`
- **Forms** — `.form-input`, `.form-select`, `.radio-group` (toggle-style radio buttons)
- **Badges** — `.badge--raw`, `.badge--crafted`, `.badge--consumer`
- **Toasts** — `.toast--success`, `.toast--error`, `.toast--gold` with enter/exit animations
- **Progress bars** — `.progress` with shimmer effect
- **Offer list** — `.offer-item` with user, price, quantity, actions
- **Leaderboard** — `.leaderboard__entry` with rank badge
- **Alerts** — `.alert--error`, `.alert--success`, `.alert--info`

### `animations.css` — Motion Design

| Animation       | Trigger                          | Effect                          |
|-----------------|----------------------------------|---------------------------------|
| `flash-green`   | Stock count increases             | Green background pulse          |
| `flash-red`     | Stock count decreases             | Red background pulse            |
| `pulse-glow`    | Resource actively producing       | Gold glow oscillation           |
| `toast-enter`   | Notification appears              | Slide in from right + scale     |
| `toast-exit`    | Notification dismissed            | Slide out + fade                |
| `money-tick`    | Balance changes                   | Brief scale bump                |
| `slide-in-left` | Sidebar items on page load        | Staggered entrance (30ms each)  |
| `shake`         | Error feedback                    | Horizontal shake                |
| `shimmer`       | Progress bar loading              | Moving gradient highlight       |

All animations respect `prefers-reduced-motion`.

---

## Game Mechanics

### Resources

| ID | Resource | Type     | Recipe                                          |
|----|----------|----------|-------------------------------------------------|
| 0  | wheat    | Raw      | —                                               |
| 1  | bread    | Crafted  | 2 wheat                                         |
| 2  | iron     | Raw      | —                                               |
| 3  | steel    | Crafted  | 1 iron                                          |
| 4  | copper   | Raw      | —                                               |
| 5  | gold     | Raw      | —                                               |
| 6  | petrol   | Raw      | —                                               |
| 7  | plastic  | Crafted  | 1 petrol                                        |
| 8  | circuit  | Crafted  | 3 plastic + 1 copper + 1 gold                   |
| 9  | car      | Consumer | 10 steel + 1 circuit + 5 petrol                 |
| 10 | phone    | Consumer | 3 steel + 5 circuit + 3 plastic + 1 copper      |

### Production

- Each resource has a **production level** (starts at 0)
- **Add Production** costs $100.00 per level (`getProductionCost() = 10000 cents`)
- Each tick, raw resources gain `production * efficiency` units
- Crafted resources are limited by available ingredient stock

### Research

- Players set a **research investment** ($/tick) per resource
- Each tick, the investment is deducted from the player's balance
- Research success is probabilistic: `P(success) = 1 - 2000 / (2000 + investment/100)`
- Each success adds 1 research point
- Efficiency = `(10000 + research) / 10000` (displayed as percentage, e.g., 10050%)

### Marketplace

- Players post **buy** or **sell** offers with a price and quantity
- The `Market.step()` engine matches compatible offers each tick
- For raw/crafted resources: cheapest sell offers are matched with buy offers
- For consumer goods (bread, car, phone): auto-sold at real stock prices
  - The **target quantity** on a sell offer = minimum stock to keep

### Leaderboard

- The top bar displays the wealthiest player and their balance
- Fetched via `User.getFirst()` — SQL query ordered by money descending

---

## API Reference

### `GET /update` — Game State

Returns JSON with the full game state for the authenticated user:

```json
{
  "user": "PlayerName",
  "user_id": "1",
  "money": 150000,
  "topPlayer": "RichestPlayer",
  "topMoney": 5000000,
  "resources": {
    "wheat": {
      "id": 0,
      "name": "wheat",
      "count": 42,
      "price": 500,
      "production_cost": 10000,
      "production": 3,
      "research_cost": 1000,
      "research": 15
    }
  },
  "offers": [
    {
      "id": 7,
      "user_id": "1",
      "user_name": "PlayerName",
      "res_id": "wheat",
      "buy": true,
      "price": 500,
      "quantity": 10
    }
  ]
}
```

### `POST /action` — Game Actions

| `action` param    | Additional params                     | Description                     |
|-------------------|---------------------------------------|---------------------------------|
| `addProduction`   | `resource` (int ID)                   | Add 1 production level          |
| `changeResearch`  | `resource` (int ID), `cost` (string)  | Set research investment         |
| `publish`         | `resource`, `buy`, `price`, `quantity` | Create marketplace offer        |
| `search`          | `resource`, `buy`, `price`, `quantity` | Search marketplace offers       |
| `delete`          | `id` (offer ID)                       | Delete own offer                |

### `POST /` — Auth Actions

| `action` param | Additional params    | Description        |
|----------------|----------------------|--------------------|
| `login`        | `user`, `pass`       | Log in             |
| `signup`       | `user`, `pass`       | Create account     |
| `logout`       | —                    | End session        |

---

## Prerequisites

- **Java 18+** (JDK)
- **Apache Tomcat 10.0.x** (Jakarta EE / Servlet 5.0)
- **MariaDB** (any recent version)
- **Eclipse JEE** (optional, for IDE development)

---

## Local Development Setup

### 1. Database

```bash
# Start MariaDB
sudo systemctl start mariadb

# Create database, user, and tables
sudo mysql -u root -p < sql/configure_db.sql
```

This creates:
- Database `db` with UTF-8 charset
- User `user@localhost` with password `password`
- Tables: `users`, `production`, `offers`

### 2. Tomcat

1. Download and extract [Apache Tomcat 10.0.x](https://tomcat.apache.org/download-10.cgi)
2. Deploy the project:
   - **Eclipse**: Import as "Dynamic Web Project", add Tomcat 10 runtime, Run on Server
   - **Manual**: Compile Java sources, package as WAR, deploy to `$TOMCAT_HOME/webapps/`

### 3. Configuration

Database connection is configured in `src/database/Provider.java`:

```java
String DRIVER = "org.mariadb.jdbc.Driver";
String CONNECTION_URL = "jdbc:mariadb://localhost/db";
String USERNAME = "user";
String PASSWORD = "password";
```

### 4. Access

Open `http://localhost:8080/` in your browser.

---

## Docker Deployment

See [DOCKER_GUIDE.md](DOCKER_GUIDE.md) for complete instructions to containerize the application with Docker Compose (Tomcat + MariaDB).

---

## Configuration Reference

| Setting               | Location                    | Default                        |
|-----------------------|-----------------------------|--------------------------------|
| DB driver             | `database/Provider.java`    | `org.mariadb.jdbc.Driver`      |
| DB URL                | `database/Provider.java`    | `jdbc:mariadb://localhost/db`  |
| DB user               | `database/Provider.java`    | `user`                         |
| DB password            | `database/Provider.java`    | `password`                     |
| Starting money        | `data/User.java`            | `100000` ($1,000.00)           |
| Production cost       | `data/ResourceProduction.java` | `10000` ($100.00)           |
| Simulation interval   | `servlet/SimulationServlet.java` | `1000` ms                 |
| Price update interval | `servlet/SimulationServlet.java` | `15` minutes              |
| Client refresh rate   | `js/game.js`                | `2000` ms                      |
| Password salt         | `database/PasswordHash.java`| `{12, 54, 86, 25}`            |

---

## Authors

- Paul Boursin
- Quentin Palmisano
- Robin Louis

Originally created as a PC3R university project.
