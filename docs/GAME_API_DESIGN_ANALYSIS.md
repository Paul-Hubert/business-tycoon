# Trade Empire — Game API Design Analysis

## Comprehensive Per-Phase Documentation

> **Date:** 2026-03-02
> **Scope:** Full architectural audit of every layer — database, backend servlets, simulation engine, data models, frontend JavaScript, and JSP views.
> **Codebase:** 18 Java files, 3 JSP views, 2 JavaScript files, 6 CSS files, 3 SQL tables.

---

## Table of Contents

1. [Phase 1 — Database Layer & Schema Design](#phase-1--database-layer--schema-design)
2. [Phase 2 — Data Model Layer (Java POJOs)](#phase-2--data-model-layer-java-pojos)
3. [Phase 3 — Database Connection & Security Infrastructure](#phase-3--database-connection--security-infrastructure)
4. [Phase 4 — Servlet / Controller Layer (HTTP API)](#phase-4--servlet--controller-layer-http-api)
5. [Phase 5 — Simulation Engine (World & Market Tick)](#phase-5--simulation-engine-world--market-tick)
6. [Phase 6 — Frontend JavaScript & AJAX Polling](#phase-6--frontend-javascript--ajax-polling)
7. [Phase 7 — View Layer (JSP Templates)](#phase-7--view-layer-jsp-templates)
8. [Phase 8 — Cross-Cutting Concerns & Systemic Issues](#phase-8--cross-cutting-concerns--systemic-issues)
9. [Appendix A — Complete SQL Query Inventory](#appendix-a--complete-sql-query-inventory)
10. [Appendix B — Complete API Endpoint Map](#appendix-b--complete-api-endpoint-map)
11. [Appendix C — Resource Dependency Graph](#appendix-c--resource-dependency-graph)

---

## Phase 1 — Database Layer & Schema Design

### 1.1 Current Schema

**Source:** `sql/configure_db.sql`

```sql
CREATE TABLE users (
    id    BIGINT NOT NULL AUTO_INCREMENT,
    user  VARCHAR(128) NOT NULL,
    pass  BINARY(64) NOT NULL,
    money BIGINT NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE production (
    user_id       BIGINT NOT NULL,
    resource      INT NOT NULL,
    count         BIGINT NOT NULL,
    production    BIGINT NOT NULL,
    research_cost BIGINT NOT NULL,
    research      BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE offers (
    id       BIGINT NOT NULL AUTO_INCREMENT,
    user_id  BIGINT NOT NULL,
    resource INT NOT NULL,
    buy      BOOLEAN NOT NULL,
    price    BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    PRIMARY KEY (id)
);
```

### 1.2 Issues Found

#### 1.2.1 Missing Primary Key on `production`

**Severity:** Critical
**Location:** `sql/configure_db.sql:15-25`

The `production` table has no PRIMARY KEY and no UNIQUE constraint on `(user_id, resource)`. This means:

- Duplicate rows can be inserted for the same user + resource combination.
- `UPDATE production SET ... WHERE user_id=? AND resource=?` (used in `ResourceProduction.java:56`) will succeed but may update multiple rows silently if duplicates exist.
- There is no index to accelerate lookups — every query does a full table scan filtered by `user_id`.

**Recommended fix:**
```sql
ALTER TABLE production
    ADD PRIMARY KEY (user_id, resource);
```

#### 1.2.2 Missing Foreign Key on `offers.user_id`

**Severity:** Medium
**Location:** `sql/configure_db.sql:27-35`

The `offers` table references `user_id` but does not declare a FOREIGN KEY constraint. If a user is deleted, their offers become orphaned rows. The `users` table has CASCADE on `production`, so this inconsistency means:

- Deleting a user removes their production rows but leaves their offers.
- The `Market.java` seller/buyer queries join on `users`, so orphaned offers are silently excluded from trade but still take up space and could appear in `Offers.search()`.

**Recommended fix:**
```sql
ALTER TABLE offers
    ADD FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
```

#### 1.2.3 Missing Indexes

**Severity:** High
**Location:** `sql/configure_db.sql`

The following queries run on every simulation tick (every 1000 ms) and have no supporting indexes:

| Query location | Columns accessed | Missing index |
|---|---|---|
| `Market.java:74` seller query | `offers.resource`, `offers.buy`, `production.user_id`, `production.resource` | `(resource, buy)` on `offers` |
| `Market.java:75` buyer query | `offers.resource`, `offers.buy`, `users.money` | `(resource, buy)` on `offers` |
| `World.java:97` user iteration | `users.id` | Already has PK index |
| `Production.java:25` | `production.user_id` | `(user_id)` on `production` |
| `Offers.java:93` search | `offers.resource`, `offers.buy`, `offers.user_id` | `(resource, buy, user_id)` on `offers` |

**Recommended fix:**
```sql
CREATE INDEX idx_production_user ON production(user_id);
CREATE INDEX idx_offers_resource_buy ON offers(resource, buy);
CREATE INDEX idx_offers_user ON offers(user_id);
```

#### 1.2.4 No `UNIQUE` Constraint on `users.user`

**Severity:** Medium
**Location:** `sql/configure_db.sql:7-13`

The `user` column has no UNIQUE constraint. The `signup()` method in `User.java:124` first attempts `login()` and falls through to `INSERT` — but if two requests race, duplicate usernames can be created. The login query (`WHERE user=? AND pass=?`) would then return the first match non-deterministically.

**Recommended fix:**
```sql
ALTER TABLE users ADD UNIQUE INDEX idx_user_unique (user);
```

#### 1.2.5 `resource` Column Uses Raw Integers (No Referential Integrity)

**Severity:** Low
**Location:** `production.resource`, `offers.resource`

Resources are stored as Java enum ordinals (0–10). If the `Resource` enum changes order or gains/removes members, all existing data becomes corrupt. There is no CHECK constraint or reference table ensuring valid values.

**Recommended fix:** Create a `resources` reference table or add a CHECK constraint:
```sql
ALTER TABLE production ADD CHECK (resource BETWEEN 0 AND 10);
ALTER TABLE offers ADD CHECK (resource BETWEEN 0 AND 10);
```

---

## Phase 2 — Data Model Layer (Java POJOs)

### 2.1 Architecture Overview

The data layer consists of 9 files in the `data` package:

| File | Purpose | Lines |
|---|---|---|
| `Resource.java` | Enum of 11 resources (wheat → phone) | 29 |
| `ResourceStack.java` | Immutable (resource, count) pair for recipes | 29 |
| `Recipe.java` | (result resource, ingredient list) | 33 |
| `Crafting.java` | Static registry of all 6 recipes | 51 |
| `Money.java` | Cents ↔ display string conversion | 19 |
| `User.java` | Authentication + user state + DB persistence | 226 |
| `Production.java` | Map<Resource, ResourceProduction> per user | 57 |
| `ResourceProduction.java` | Single resource's count/production/research per user | 72 |
| `Offer.java` | Single buy/sell marketplace offer | 79 |
| `Offers.java` | Collection + search logic for offers | 109 |

### 2.2 Issues Found

#### 2.2.1 `Offer.update()` Writes to Wrong Table — SQL Bug

**Severity:** Critical
**Location:** `src/data/Offer.java:64`

```java
PreparedStatement ps = con.prepareStatement(
    "update production set buy=?, price=?, quantity=? where id=?;"
);
```

This updates `production` instead of `offers`. The `production` table does not have `buy`, `price`, or `quantity` columns, and does not have an `id` column. This query will:

1. Silently fail (update 0 rows) if `production` has no matching `id`.
2. Corrupt data if `id` accidentally matches a `user_id` in some edge case.

**Fix:**
```java
"update offers set buy=?, price=?, quantity=? where id=?;"
```

#### 2.2.2 `User.signup()` Silently Swallows Login Failures

**Severity:** High
**Location:** `src/data/User.java:124-132`

```java
public static void signup(HttpServletRequest request) throws Exception {
    try {
        login(request);
    } catch(Exception e) {}   // swallowed

    if(isConnected(request.getSession())) {
        return;
    }
    // ... proceed to create user
}
```

The method first attempts `login()`, catches ALL exceptions (not just "user not found"), and proceeds. This means:

- A **database connection failure** during login is silently ignored, then the method attempts to INSERT a new user — which may also fail or create a duplicate.
- There is no way to distinguish "user doesn't exist yet" from "database is down."
- The `login()` call shares the same request, so `getParameter("user")` and `getParameter("pass")` are parsed twice.

#### 2.2.3 `User.isConnected()` Makes a DB Query Every Call

**Severity:** High
**Location:** `src/data/User.java:65-81`

```java
public static boolean isConnected(HttpSession session) throws Exception {
    var id = session.getAttribute("id");
    if(id == null) return false;

    Connection con = ConnectionProvider.getCon();
    PreparedStatement ps = con.prepareStatement("select * from users where id=?;");
    ps.setLong(1, (long) id);
    ResultSet rs = ps.executeQuery();
    if(!rs.next()) return false;
    return true;
}
```

This method is called on **every request** (every servlet checks `isConnected()` first). Then `getConnected()` is called, which runs the **same query again** via `User.create(id)`. So every single HTTP request executes the `SELECT * FROM users WHERE id=?` query **at least twice**.

With the 2-second polling interval, each connected client generates ~1 request/second to `/update`, each running this query twice = **~2 user lookups/second/client** just for authentication.

#### 2.2.4 `User.getFirst()` Sorts the Entire `users` Table

**Severity:** Medium
**Location:** `src/data/User.java:192-200`

```java
public static void getFirst() throws Exception {
    var ps = con.prepareStatement("select user, money from users order by money desc;");
    ResultSet rs = ps.executeQuery();
    rs.next();
    firstUser = rs.getString("user");
    firstMoney = rs.getLong("money");
}
```

This sorts ALL users by money descending to get the #1 player. It runs on every `/update` request (called from `update.jsp:12`). With N users, this is O(N log N) per request.

**Fix:** Add `LIMIT 1`:
```sql
SELECT user, money FROM users ORDER BY money DESC LIMIT 1;
```

Or add an index:
```sql
CREATE INDEX idx_users_money ON users(money DESC);
```

#### 2.2.5 `Production.get()` Creates Orphan Objects

**Severity:** Medium
**Location:** `src/data/Production.java:39-45`

```java
public ResourceProduction get(Resource resource) {
    var rp = resources.get(resource);
    if(rp == null) {
        return new ResourceProduction(user_id, resource);  // empty=true
    }
    return rp;
}
```

When a resource has no production row in the DB, this creates a new `ResourceProduction` with `empty=true`. But this new object is **not stored in the `resources` map**. If `World.step()` later calls `rp.count += max` on this object and then calls `user.getProduction().update()`, the update iterates `resources.keySet()` — which does **not contain this new object**. The mutation is lost.

This means: resources without an existing production row can never accumulate stock through the simulation tick.

#### 2.2.6 String Comparison with `==` Instead of `.equals()`

**Severity:** Medium
**Location:** `src/data/User.java:102,105`

```java
if(user == null || user == "") throw new InputMismatchException("Username not set");
if(pass == null || pass == "") throw new InputMismatchException("Password not set");
```

In Java, `==` compares object references, not string content. `request.getParameter()` returns a new String object, so `user == ""` will **always be false** even for empty strings. The empty-string check is effectively dead code.

**Fix:**
```java
if(user == null || user.isEmpty()) ...
```

This same pattern repeats at lines 135 and 138.

#### 2.2.7 `ResourceProduction.getProductionCost()` Returns Hardcoded Constant

**Severity:** Low (Design issue)
**Location:** `src/data/ResourceProduction.java:68-70`

```java
public int getProductionCost() {
    return 10000;
}
```

Every resource costs exactly 10000 cents ($100.00) to add one unit of production, regardless of resource rarity, current production level, or game progression. This is a balance issue — adding production for wheat costs the same as for circuits.

#### 2.2.8 `ResourceStack.substract()` Method Name Typo

**Severity:** Trivial
**Location:** `src/data/ResourceStack.java:17`

```java
public void substract(int n) {
```

Should be `subtract`. This method is currently unused but would create API inconsistency if called.

---

## Phase 3 — Database Connection & Security Infrastructure

### 3.1 Architecture Overview

| File | Purpose |
|---|---|
| `database/Provider.java` | Interface with DB credentials as constants |
| `database/ConnectionProvider.java` | Singleton JDBC connection |
| `database/PasswordHash.java` | SHA-512 hashing with static salt |

### 3.2 Issues Found

#### 3.2.1 Single Shared JDBC Connection (No Connection Pooling)

**Severity:** Critical
**Location:** `src/database/ConnectionProvider.java:6-19`

```java
public class ConnectionProvider {
    static Connection con = null;

    public static Connection getCon() throws Exception {
        if(con == null) {
            connect();
        }
        return con;
    }
}
```

The entire application shares **one single JDBC connection**. This is catastrophic for a multi-threaded application:

1. **Thread safety:** `java.sql.Connection` is not thread-safe. The `SimulationServlet` uses a 16-thread `ScheduledExecutorService`. `World.step()` spawns concurrent tasks per user (line 107), and `Market.step()` spawns concurrent tasks per resource (line 49). All of these call `ConnectionProvider.getCon()` and get the **same Connection object**.
2. **Statement interleaving:** Two threads executing queries on the same connection can corrupt each other's ResultSets. For example, thread A calls `executeQuery()`, thread B calls `executeQuery()` on the same connection, and thread A's ResultSet may be invalidated.
3. **Transaction isolation:** There is no transaction management. Every statement auto-commits. In `Market.step()`, the buyer update and seller update (lines 153-167) are two separate statements — if the application crashes between them, money is deducted from the buyer but never credited to the seller.
4. **Connection recovery:** If the database connection drops (timeout, restart), `con` is never set back to `null`. All subsequent requests fail permanently until Tomcat is restarted.

**Recommended fix:** Use a connection pool (HikariCP, Tomcat JDBC Pool, or DBCP):
```java
// Example with HikariCP
HikariConfig config = new HikariConfig();
config.setJdbcUrl(CONNECTION_URL);
config.setUsername(USERNAME);
config.setPassword(PASSWORD);
config.setMaximumPoolSize(20);
HikariDataSource ds = new HikariDataSource(config);

public static Connection getCon() throws SQLException {
    return ds.getConnection(); // returns a pooled connection
}
```

#### 3.2.2 Hardcoded Database Credentials

**Severity:** High
**Location:** `src/database/Provider.java`

```java
public interface Provider {
    String DRIVER = "org.mariadb.jdbc.Driver";
    String CONNECTION_URL = "jdbc:mariadb://mariadb/db";
    String USERNAME = "user";
    String PASSWORD = "password";
}
```

Credentials are hardcoded in source code and checked into version control. The `setup.sh` script patches this file to swap `localhost` → `mariadb`, which means the credentials are duplicated in two places.

**Recommended fix:** Use environment variables or JNDI:
```java
String url = System.getenv("DB_URL");
String user = System.getenv("DB_USER");
String pass = System.getenv("DB_PASS");
```

#### 3.2.3 Weak Password Hashing (SHA-512 with Static Salt)

**Severity:** Critical (Security)
**Location:** `src/database/PasswordHash.java:9-16`

```java
private static byte[] salt = new byte[] {12, 54, 86, 25};

public static byte[] hash(String password) throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance("SHA-512");
    md.update(salt);
    return md.digest(password.getBytes(StandardCharsets.UTF_8));
}
```

Problems:

1. **Static salt:** Every user gets the same 4-byte salt. If two users have the same password, they have identical hashes. An attacker who obtains the database can build a rainbow table once and crack all passwords.
2. **No iteration/stretching:** SHA-512 is a fast hash. Modern GPUs can compute billions of SHA-512 hashes per second. Password hashing should use slow, memory-hard algorithms.
3. **Salt is only 4 bytes:** Even if the salt were per-user, 4 bytes (32 bits) only provides ~4 billion possibilities — trivially enumerable.

**Recommended fix:** Use bcrypt, scrypt, or Argon2:
```java
// Using jBCrypt
String hashed = BCrypt.hashpw(password, BCrypt.gensalt(12));
boolean match = BCrypt.checkpw(password, hashed);
```

#### 3.2.4 Resources Not Closed (ResultSet, PreparedStatement, Connection)

**Severity:** High
**Location:** Every file that uses JDBC

Across the entire codebase, JDBC resources are **never closed**. No file uses try-with-resources or finally blocks. Examples:

- `User.java:44-51` — PreparedStatement and ResultSet never closed.
- `Production.java:23-33` — PreparedStatement and ResultSet never closed.
- `Market.java:74-81` — Three PreparedStatements and two ResultSets never closed.
- `World.java:97-99` — PreparedStatement and ResultSet never closed.
- `Offers.java:79-103` — Two PreparedStatements and two ResultSets never closed.

With a single shared connection this causes cursor leaks. With a connection pool, unclosed connections would cause pool exhaustion.

**Recommended fix:** Use try-with-resources everywhere:
```java
try (PreparedStatement ps = con.prepareStatement("...")) {
    ps.setLong(1, id);
    try (ResultSet rs = ps.executeQuery()) {
        // ...
    }
}
```

---

## Phase 4 — Servlet / Controller Layer (HTTP API)

### 4.1 Architecture Overview

| Servlet | Route | Method | Purpose |
|---|---|---|---|
| `IndexServlet` | `/` | GET/POST | Login page, authentication |
| `GameServlet` | `/game` | GET | Main game page |
| `ActionServlet` | `/action` | POST | All game actions |
| `UpdateServlet` | `/update` | GET/POST | JSON state response |
| `SimulationServlet` | (listener) | — | Background simulation |

### 4.2 Endpoint: `POST /action`

**Source:** `src/servlet/ActionServlet.java`

This is the core game API. All player actions route through a single endpoint, differentiated by an `action` parameter.

#### Supported Actions

| Action | Parameters | Effect |
|---|---|---|
| `addProduction` | `resource` (int) | Pay $100, increment resource production level |
| `publish` | `resource`, `buy`, `price`, `quantity` | Create marketplace offer + return search results |
| `changeResearch` | `resource`, `cost` | Set research spending per tick |
| `search` | `resource`, `buy`, `price`, `quantity` | Search marketplace offers |
| `delete` | `id` | Delete an offer by ID |

### 4.3 Issues Found

#### 4.3.1 No Input Validation

**Severity:** Critical
**Location:** `src/servlet/ActionServlet.java:26-117`

The `doPost` method does not validate any input parameters:

```java
var action = request.getParameter("action");
if (action.equals("addProduction")) {
    var resource = request.getParameter("resource");
    Resource r = Resource.get(Integer.parseInt(resource));
```

- `action` can be `null` → `NullPointerException` at line 37.
- `resource` can be `null` → `NullPointerException` at `Integer.parseInt(null)`.
- `resource` can be non-numeric → `NumberFormatException`.
- `resource` can be out of range (e.g., `99`) → unhandled exception from `Resource.get()`.
- `price` and `quantity` in publish/search can be negative — a user could publish a sell offer with negative price, effectively paying buyers.
- `quantity` of 0 is accepted, creating useless offers.

All of these are caught by the outer `catch (Exception e)` at line 114, which calls `e.printStackTrace()` and **returns nothing to the client**. The client's AJAX callback never fires (no response body), and the `.fail(error)` handler shows a generic toast.

#### 4.3.2 No Authorization on `delete` Action

**Severity:** Critical (Security)
**Location:** `src/servlet/ActionServlet.java:88-96`

```java
} else if(action.equals("delete")) {
    long id = Integer.parseInt(request.getParameter("id"));
    Offer.delete(id);
```

Any authenticated user can delete **any other user's offer** by providing its ID. There is no check that `offer.user_id == currentUser.id`. An attacker could enumerate offer IDs and delete all marketplace offers.

**Recommended fix:**
```java
// Verify ownership before deletion
Offer offer = Offer.findById(id);
if (offer.user_id != user.id) {
    throw new SecurityException("Cannot delete another user's offer");
}
Offer.delete(id);
```

#### 4.3.3 No Authorization on `changeResearch`

**Severity:** Medium
**Location:** `src/servlet/ActionServlet.java:97-106`

```java
} else if(action.equals("changeResearch")) {
    var resource = request.getParameter("resource");
    Resource r = Resource.get(Integer.parseInt(resource));
    ResourceProduction rp = user.getProduction().get(r);
    var cost = Money.parse(request.getParameter("cost"));
    rp.research_cost = cost;
    rp.update();
}
```

While this correctly uses the authenticated user's production, there is no validation that `cost` is non-negative. A user could set `research_cost` to a negative value. In `World.research()` at line 30:

```java
user.pay(rp.research_cost);
```

`User.pay()` checks `if(price > money)` — a negative price passes this check, and `this.money = money - price` would **increase** the user's money. This is an infinite money exploit.

#### 4.3.4 Silent Exception Handling

**Severity:** High
**Location:** `src/servlet/ActionServlet.java:114-116`

```java
} catch (Exception e) {
    e.printStackTrace();
}
```

If any exception occurs during action processing, it is printed to server logs and the response is **empty**. The client receives no HTTP status code, no error message, no JSON body. The jQuery `.fail()` handler triggers with a parse error (since JSON is expected), showing a generic "Something went wrong" toast.

**Recommended fix:** Return proper error responses:
```java
} catch (Exception e) {
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    response.setContentType("application/json");
    response.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
}
```

#### 4.3.5 `addProduction` Silently Fails on Insufficient Funds

**Severity:** Medium
**Location:** `src/servlet/ActionServlet.java:43-46`

```java
try {
    user.pay(rp.getProductionCost());
    rp.addProduction();
} catch(NotEnoughMoneyException e) {}
```

If the user cannot afford the production upgrade, the exception is swallowed and the response contains the unchanged game state. The client shows "Production added!" toast (line 29 of `game.js`) regardless, because the success callback always fires.

#### 4.3.6 `UpdateServlet` Re-runs Search on Every Poll

**Severity:** Medium
**Location:** `src/servlet/UpdateServlet.java:31-36`

```java
data.Offer search = (data.Offer) request.getSession().getAttribute("lastOffer");
if(search != null) {
    var offers = user.getOffers().search(search);
    request.setAttribute("offers", offers);
}
```

The last search/publish offer is stored in the HTTP session. Every subsequent `/update` request (every 2 seconds) re-executes the search query against the database. This means a user who searched once will trigger 2 extra SQL queries every 2 seconds **forever** until their session expires.

#### 4.3.7 Integer Overflow in `delete` Action

**Severity:** Low
**Location:** `src/servlet/ActionServlet.java:90`

```java
long id = Integer.parseInt(request.getParameter("id"));
```

The offer ID is defined as `BIGINT` in the database and `long` in Java, but parsed as `int` here. Offer IDs above `Integer.MAX_VALUE` (2,147,483,647) would cause a truncation or parse failure.

**Fix:**
```java
long id = Long.parseLong(request.getParameter("id"));
```

---

## Phase 5 — Simulation Engine (World & Market Tick)

### 5.1 Architecture Overview

The game runs two scheduled tasks from `SimulationServlet.java`:

1. **Market price update** — every 15 minutes, fetches real stock prices from Yahoo Finance API.
2. **Game tick** — every 1000 ms:
   - `Market.step()` — executes buy/sell order matching.
   - `World.step()` — runs production, crafting, and research for all users.

### 5.2 `World.step()` — Production & Crafting Engine

**Source:** `src/simulation/World.java`

#### Flow Per User Per Tick

```
For each user:
  For each resource (in enum order: wheat, bread, iron, ...):
    1. Run research()  — deduct research_cost, probabilistic research++
    2. Calculate efficiency = (100 + research/100) / 100
    3. Calculate max = production * efficiency
    4. If raw resource (no recipe): count += max
    5. If crafted resource:
       a. For each ingredient, cap max by (ingredient.count / ingredient.required)
       b. count += max
       c. Deduct ingredients
    6. Save to DB
```

### 5.3 Issues Found

#### 5.3.1 Race Condition: Concurrent User Updates on Shared Connection

**Severity:** Critical
**Location:** `src/simulation/World.java:93-125`

```java
public void step(ScheduledExecutorService scheduler) throws Exception {
    Connection con = ConnectionProvider.getCon();
    PreparedStatement userps = con.prepareStatement("select id from users;");
    ResultSet users = userps.executeQuery();
    ArrayList<ScheduledFuture<?>> futures = new ArrayList<>();

    while(users.next()) {
        final var user_id = users.getLong("id");
        futures.add(scheduler.schedule(() -> {
            try { step(user_id); }
            catch (Exception e) { e.printStackTrace(); }
        }, 0, TimeUnit.SECONDS));
    }
    for(var future : futures) { future.get(); }
}
```

Each user's `step(user_id)` runs on a separate thread from the 16-thread pool. Inside `step(user_id)`:

- `User.create(user_id)` calls `ConnectionProvider.getCon()` → same connection.
- `user.getProduction()` creates PreparedStatements on the same connection.
- `user.update()` and `production.update()` write to the same connection.

With 16 threads sharing one connection, PreparedStatements and ResultSets **corrupt each other**. This can cause:

- Wrong data read (ResultSet from another thread's query).
- Partial writes (two UPDATE statements interleaved).
- `CommunicationsException` or `ResultSet closed` exceptions.

#### 5.3.2 Resource Processing Order Creates Unfair Advantage

**Severity:** Medium
**Location:** `src/simulation/World.java:44`

```java
for(Resource resource : Resource.values()) {
```

`Resource.values()` returns enum constants in declaration order: `wheat, bread, iron, steel, copper, gold, petrol, plastic, circuit, car, phone`.

This means **bread is processed immediately after wheat** in the same tick. The wheat production at line 58 (`rp.count += max`) increases the wheat count, and then bread crafting at line 66 (`max = Math.min(max, irp.count / ing.getCount())`) uses that updated count in the same tick. This effectively gives crafted resources a one-tick advantage: wheat → bread happens instantly within one tick.

However, for car (which needs steel + circuit + petrol), the dependencies are:
- `steel` is processed at index 3 (already updated in this tick ✓)
- `circuit` is processed at index 8 (already updated ✓)
- `petrol` is processed at index 6 (already updated ✓)

So the enum ordering coincidentally works, but it's fragile — reordering the enum would break crafting chains.

#### 5.3.3 Research Formula Has Diminishing Returns That Plateau

**Severity:** Low (Design)
**Location:** `src/simulation/World.java:32-35`

```java
final double ratio = 2000;
var prob = 1. - ratio / (ratio + rp.research_cost / 100.);
if(Math.random() < prob) rp.research += 1;
```

The probability formula: `P = 1 - 2000 / (2000 + cost/100)`

| Research Cost (cents) | Cost ($) | Probability | Expected ticks per +1 |
|---|---|---|---|
| 10,000 | $1.00 | 0.05% | 2,001 |
| 100,000 | $10.00 | 0.50% | 201 |
| 1,000,000 | $100.00 | 4.8% | 21 |
| 10,000,000 | $1,000.00 | 33.3% | 3 |
| 100,000,000 | $10,000.00 | 83.3% | 1.2 |

The efficiency bonus is `(100 + research/100) / 100`, so research of 10,000 gives a 2x multiplier. Getting 10,000 research points at the highest probability costs ~$120M and ~12,000 ticks (3.3 hours). This seems intentionally slow but the curve is extremely flat at low investment levels — making early research nearly useless.

#### 5.3.4 `Market.step()` — Auto-Sell Has No Transaction Safety

**Severity:** High
**Location:** `src/simulation/Market.java:83-108`

```java
while(sellers.next()) {
    long price = Market.price(res);
    long stock = sellers.getLong("p.count");
    long quantity = sellers.getLong("o.quantity");
    long amount = stock - quantity;
    if(amount <= 0) continue;
    long cost = amount * price;

    PreparedStatement sellerUpdate = con.prepareStatement(
        "update users as u, production as p set u.money=u.money+?, p.count=p.count-? ..."
    );
    sellerUpdate.execute();
}
```

For auto-sellable resources (bread, car, phone), the code:
1. Reads `p.count` from the ResultSet (a snapshot from when the query ran).
2. Calculates `amount = stock - quantity`.
3. Executes `SET p.count = p.count - amount`.

But between steps 1 and 3, `World.step()` may have already modified `p.count` (production adds to count). The `p.count - amount` in step 3 uses the **current** DB value (not the snapshot), which is correct — but there's a window where `p.count` could have gone below `quantity` due to a concurrent market trade, causing the subtraction to produce a negative count.

#### 5.3.5 `Market.step()` — Buyer Order Matching is Non-Deterministic

**Severity:** Medium
**Location:** `src/simulation/Market.java:75`

```java
PreparedStatement buyerStatement = con.prepareStatement(
    "select * from offers ... where o.resource=? and buy=true ... order by rand()"
);
```

Buyers are matched in **random order** (`ORDER BY RAND()`). This means:

- A buyer offering $10 might be matched before a buyer offering $20.
- The seller always gets the lowest posted sell price (sellers are `ORDER BY o.price ASC`).
- The price the buyer pays is the **seller's** ask price, not their own bid.
- `ORDER BY RAND()` is also expensive — it materializes the entire result set and assigns random values.

#### 5.3.6 `Market.updatePrice()` — Hardcoded API Key in Source

**Severity:** Critical (Security)
**Location:** `src/simulation/Market.java:177-181`

```java
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://yfapi.net/v6/finance/quote?region=US&lang=en&symbols=WMT%2CAAPL%2CTSLA"))
    .header("x-api-key", "Ad3Cs8Xv1Y2pOZ42as1ptyREYq2DyYOaajV0cUH3")
    .build();
```

A third-party API key is hardcoded in source and committed to version control. This key could be revoked, abused, or rate-limited. Additionally:

- If the API is unreachable, `HttpClient.send()` blocks the scheduled thread.
- There is no timeout configured on the HTTP request.
- If the API returns malformed JSON, a `ParseException` propagates but is caught by the scheduler's exception handler — prices remain at their previous values silently.

#### 5.3.7 `Market.step()` — ResultSet Cursor Not Advanced After Stock Depletion

**Severity:** High
**Location:** `src/simulation/Market.java:122-126`

```java
long stock = sellers.getLong("p.count");
if(stock <= 0) {
    if(!sellers.next()) {
        return;
    }
}
```

When a seller's stock reaches 0, the code advances to the next seller. But it then **falls through** to the rest of the loop body, which reads from the **new** seller's row without re-reading `price`, `stock`, etc. The variables `price` (line 117) and `stock` (line 121) still hold the **previous** seller's values.

#### 5.3.8 Integer Overflow in Market Calculations

**Severity:** Medium
**Location:** `src/simulation/Market.java:96,135`

```java
long cost = amount * price;
```

Both `amount` and `price` are `long`, but their product can overflow. For example, if a player has 10 billion units of bread and the price is $100 (10000 cents), `10_000_000_000 * 10_000 = 100_000_000_000_000` which fits in a `long`, but larger values could overflow silently, producing negative costs — effectively **creating money** for the seller and **stealing money** from the buyer.

---

## Phase 6 — Frontend JavaScript & AJAX Polling

### 6.1 Architecture Overview

| File | Lines | Purpose |
|---|---|---|
| `WebContent/js/game.js` | 262 | Game logic: API calls, DOM updates, view switching, toasts |
| `WebContent/js/common.js` | 106 | Utility: number formatting, currency input |

**Polling model:** The client calls `GET /update` every 2000 ms via `setInterval`. Every game action (`POST /action`) also receives the full game state as its response.

### 6.2 Issues Found

#### 6.2.1 Polling Creates N+1 Query Problem at Scale

**Severity:** High
**Location:** `WebContent/js/game.js:6,260`

```javascript
const refresh_rate = 2000;
setInterval(refresh, refresh_rate);
```

Every connected client polls `/update` every 2 seconds. Each poll triggers:

1. `User.isConnected()` → `SELECT * FROM users WHERE id=?`
2. `User.getConnected()` → calls `User.create(id)` → `SELECT * FROM users WHERE id=?` (duplicate)
3. `Production.create(id)` → `SELECT * FROM production WHERE user_id=?`
4. `Offers.search()` → 2 queries (if lastOffer exists in session)
5. `User.getFirst()` → `SELECT user, money FROM users ORDER BY money DESC`

That's **4-6 DB queries per poll per client**. With 100 concurrent users: 200-300 queries/second just from polling, plus the simulation engine's queries every tick.

#### 6.2.2 No Request Deduplication / Queue

**Severity:** Medium
**Location:** `WebContent/js/game.js:21-22`

```javascript
function refresh() {
    $.getJSON("/update", {}, reload, "json").fail(error);
}
```

If a network request takes >2 seconds (slow connection, server overload), the next `setInterval` fires and starts a second concurrent request. Multiple overlapping responses can cause the DOM to flash erratically as `reload()` is called out of order.

**Recommended fix:**
```javascript
let refreshPending = false;
function refresh() {
    if (refreshPending) return;
    refreshPending = true;
    $.getJSON("/update", {}, reload, "json")
        .fail(error)
        .always(() => { refreshPending = false; });
}
```

#### 6.2.3 XSS Vulnerability in Toast Notifications

**Severity:** High (Security)
**Location:** `WebContent/js/game.js:217-223`

```javascript
let toast = $("<div>")
    .addClass("toast toast--" + type + " toast-enter")
    .html(
        '<span class="toast__icon">' + (icons[type] || "") + '</span>' +
        '<span class="toast__message">' + message + '</span>' +
        ...
    );
```

The `message` parameter is injected directly into HTML via `.html()`. If any server-returned error message contains user-controlled content (e.g., a username with `<script>` tags), it will be executed as HTML/JavaScript.

Currently, `showToast()` is only called with hardcoded strings ("Production added!", "Offer published!"), so this is not immediately exploitable. But the `error()` function (line 11-13) shows "Something went wrong" — if it were changed to show the server error message, it would become exploitable.

**Recommended fix:** Use `.text()` instead of `.html()` for the message:
```javascript
$('<span class="toast__message">').text(message)
```

#### 6.2.4 Offer List DOM Manipulation Uses Fragile ID Swapping

**Severity:** Medium
**Location:** `WebContent/js/game.js:118-136`

```javascript
let offer_card = template.clone();
offer_card.appendTo(offers);
offer_card.attr("id", "modify");           // temporarily set to "modify"
// ... update fields using #modify selector ...
offer_card.attr("id", "offer" + offer_id); // rename to final ID
```

The code:
1. Clones a template element.
2. Sets its ID to `"modify"`.
3. Uses `$("#modify .price")` etc. to populate it.
4. Renames the ID to `"offer" + offer_id`.

If two offers are processed in the same loop iteration (they can't be, since it's synchronous), or if another element has `id="modify"`, the selectors would target the wrong element. More practically, this pattern is fragile because:

- It pollutes the DOM with temporary IDs.
- `$("#modify")` is a global selector — if the template clone already has children with `.price`, `.quantity`, `.offerer` classes from a previous iteration's leftover `#modify`, the wrong element gets updated.

**Recommended fix:** Use the cloned element directly:
```javascript
let offer_card = template.clone().removeAttr("id");
offer_card.find(".price").html(formatMoney(offer.price));
offer_card.find(".quantity").html(formatNumber(offer.quantity));
offer_card.find(".offerer").html(offer.user_name);
offers.append(offer_card);
```

#### 6.2.5 `updateCurrencySpy()` Is a No-Op

**Severity:** Low
**Location:** `WebContent/js/common.js:2-19`

```javascript
function updateCurrencySpy() {
    /* ... entire body is commented out ... */
}
```

This function is called from `reload()` (game.js:138) and `window.onload` (game.js:257) every 2 seconds, but does nothing. It should either be implemented or removed to avoid confusion.

#### 6.2.6 Offer Cards Don't Show Resource Name or Buy/Sell Type Clearly

**Severity:** Low (UX)
**Location:** `WebContent/js/game.js:114-136`

The offer card template is populated with price, quantity, offerer name, and a delete button. But `offer.res_id` (the resource name) is available in the data and never displayed. If a user has offers for multiple resources, the offer list doesn't indicate which resource each offer is for — only the current search context implies it.

---

## Phase 7 — View Layer (JSP Templates)

### 7.1 Architecture Overview

| JSP | Purpose |
|---|---|
| `WEB-INF/index.jsp` | Login / signup form |
| `WEB-INF/game.jsp` | Main game interface (343 lines of HTML) |
| `WEB-INF/update.jsp` | JSON response generator (63 lines) |

### 7.2 Issues Found

#### 7.2.1 `update.jsp` Generates Invalid JSON

**Severity:** High
**Location:** `WebContent/WEB-INF/update.jsp:1-9`

```jsp
{
    <%
    if(request.getAttribute("error") != null) {
    %>
    error: "${requestScope.error}",
    <% } %>
```

The `error` key is **not quoted** — it should be `"error"`. This produces:

```json
{ error: "some message", "user": "..." }
```

Which is valid JavaScript but **invalid JSON**. jQuery's `$.getJSON()` uses `JSON.parse()` internally, which requires quoted keys. If an error occurs, the entire response fails to parse, and the client shows a generic error instead of the actual error message.

Additionally, the error value uses `${requestScope.error}` (EL expression) which is not escaped for JSON. If the error message contains a `"` character, it breaks the JSON string.

#### 7.2.2 `update.jsp` Has No Null-Safety

**Severity:** Medium
**Location:** `WebContent/WEB-INF/update.jsp:11-13`

```jsp
<%
User user = (User) request.getAttribute("user");
User.getFirst();
%>
```

If `user` is null (e.g., session expired between the servlet check and the JSP render), the subsequent `user.name`, `user.id`, `user.money` expressions throw `NullPointerException`, producing a partial JSON response that the client cannot parse.

#### 7.2.3 `update.jsp` Is Vulnerable to JSON Injection

**Severity:** Medium (Security)
**Location:** `WebContent/WEB-INF/update.jsp:14`

```jsp
"user": "<%= user.name %>",
```

The username is injected directly into a JSON string without escaping. If a username contains `"`, `\`, or newline characters, it breaks the JSON structure. A malicious username like `admin", "money": 99999999, "x": "` would inject arbitrary JSON fields.

**Recommended fix:** Use a JSON library (Jackson, Gson) to serialize the response, or manually escape strings:
```java
String safeName = user.name.replace("\\", "\\\\").replace("\"", "\\\"");
```

#### 7.2.4 Leaderboard Query Called From View Layer

**Severity:** Medium (Architecture)
**Location:** `WebContent/WEB-INF/update.jsp:12`

```jsp
User.getFirst();
```

A database query (`SELECT user, money FROM users ORDER BY money DESC`) is executed from within a JSP template. This violates MVC separation — views should only render data provided by the controller. The leaderboard data should be fetched in `UpdateServlet.doGet()` and passed as a request attribute.

---

## Phase 8 — Cross-Cutting Concerns & Systemic Issues

### 8.1 Concurrency Model

The application has three sources of concurrent execution:

1. **Servlet container threads** — Tomcat handles HTTP requests on its thread pool (~200 threads by default).
2. **Simulation scheduler** — A 16-thread `ScheduledExecutorService` runs game ticks.
3. **Scheduled market price updates** — Shares the same scheduler.

All three access the **same single JDBC connection** without synchronization. This is the single most dangerous architectural issue in the codebase.

**Impact matrix:**

| Thread A | Thread B | Conflict |
|---|---|---|
| HTTP request reads user | Simulation updates user money | Stale read → incorrect balance shown |
| Simulation reads production count | Market auto-sell subtracts count | Count goes negative |
| Market matches buyer + seller | HTTP request creates new offer | New offer matched with stale price data |
| World.step() iterates users | New user signs up | ConcurrentModificationException or missed user |

### 8.2 Error Handling Strategy

The codebase follows a consistent (but problematic) error handling pattern:

```java
} catch (Exception e) {
    e.printStackTrace();
}
```

This appears in:
- `SimulationServlet.java:34,45`
- `World.java:113`
- `Market.java:55`
- `ActionServlet.java:114`

Consequences:
- Errors are logged to `stderr` (Tomcat's `catalina.out`) but never reported to the user.
- No monitoring, alerting, or metrics.
- Simulation failures are silently skipped — if one user's tick fails, they miss production for that tick but no one knows.

### 8.3 Session Management

**Session state stored:**
- `id` (long) — user ID, set at login.
- `lastOffer` (Offer object) — last search/publish parameters.

**Issues:**
- Session ID is the standard Tomcat `JSESSIONID` cookie. No CSRF protection.
- `lastOffer` persists in the session indefinitely, causing repeated search queries on every poll (see Phase 4, §4.3.6).
- The `HttpSession` is used for both authentication state and UI state, coupling concerns.

### 8.4 Money Representation

Money is stored as `BIGINT` (cents). `Money.java` handles conversion:

```java
public static long parse(String money) {
    return (long) Math.floor(Double.parseDouble(money) * 100.0);
}
```

Using `double` for intermediate currency calculations introduces floating-point errors. For example:
- `Double.parseDouble("0.1") * 100.0` = `10.000000000000002` → `Math.floor()` = `10` ✓
- `Double.parseDouble("1.005") * 100.0` = `100.49999999999999` → `Math.floor()` = `100` (should be `101`)

**Recommended fix:** Parse the string directly:
```java
public static long parse(String money) {
    BigDecimal bd = new BigDecimal(money);
    return bd.multiply(BigDecimal.valueOf(100)).longValueExact();
}
```

### 8.5 Missing CSRF Protection

No endpoint validates a CSRF token. Since actions are performed via `POST /action` with session cookies, a malicious website could craft a form that auto-submits:

```html
<form action="http://target:8080/action" method="POST">
    <input type="hidden" name="action" value="changeResearch">
    <input type="hidden" name="resource" value="0">
    <input type="hidden" name="cost" value="-999999999">
</form>
<script>document.forms[0].submit()</script>
```

This would set the victim's research cost to a negative value, enabling the infinite money exploit described in Phase 4 §4.3.3.

### 8.6 No Rate Limiting

There is no rate limiting on any endpoint:
- `/action` with `addProduction` could be called thousands of times per second by a script.
- `signup` could be used to create unlimited accounts.
- `publish` could flood the marketplace with offers.

### 8.7 Lack of Database Transactions

No code in the entire codebase uses `con.setAutoCommit(false)` or `con.commit()`. Every SQL statement is auto-committed immediately. Multi-step operations that should be atomic include:

1. **Buying production** (`ActionServlet:43-46`): `user.pay()` → `UPDATE users SET money` then `rp.addProduction()` → `UPDATE production SET production`. If the second fails, the user lost money but didn't get production.

2. **Market trades** (`Market.java:153-167`): buyer's `UPDATE` then seller's `UPDATE`. If seller's update fails, buyer's money is gone but seller never gets paid.

3. **Crafting** (`World.java:60-84`): `count += max` for the crafted resource, then `count -= max * ing.getCount()` for each ingredient. If one ingredient deduction fails, the crafted resource was created from nothing.

---

## Appendix A — Complete SQL Query Inventory

### Queries by File

| # | File:Line | SQL | Frequency |
|---|---|---|---|
| 1 | `User.java:44` | `SELECT * FROM users WHERE id=?` | Every request (×2) |
| 2 | `User.java:72` | `SELECT * FROM users WHERE id=?` | Every request (auth check) |
| 3 | `User.java:110` | `SELECT * FROM users WHERE user=? AND pass=?` | Login |
| 4 | `User.java:143` | `INSERT INTO users (user,pass,money) VALUES (?,?,?)` | Signup |
| 5 | `User.java:194` | `SELECT user,money FROM users ORDER BY money DESC` | Every /update poll |
| 6 | `User.java:207` | `UPDATE users SET money=? WHERE id=?` | Every tick + pay |
| 7 | `Production.java:25` | `SELECT * FROM production WHERE user_id=?` | Every request + tick |
| 8 | `ResourceProduction.java:47` | `INSERT INTO production (...) VALUES (?,?,?,?,?,?)` | First production |
| 9 | `ResourceProduction.java:56` | `UPDATE production SET count=?,production=?,research_cost=?,research=? WHERE user_id=? AND resource=?` | Every tick |
| 10 | `Offers.java:26` | `SELECT * FROM offers AS o INNER JOIN users AS u ON u.id=o.user_id WHERE user_id=?` | Every /update poll |
| 11 | `Offers.java:79` | `SELECT * FROM offers ... WHERE user_id=? AND resource=? AND buy=?` | Search/publish |
| 12 | `Offers.java:93` | `SELECT * FROM offers ... WHERE resource=? AND buy=? AND user_id!=? ORDER BY price ... LIMIT 10` | Search/publish |
| 13 | `Offer.java:42` | `INSERT INTO offers (...) VALUES (?,?,?,?,?)` | Publish |
| 14 | `Offer.java:64` | `UPDATE production SET buy=?,price=?,quantity=? WHERE id=?` | ⚠️ BUG: wrong table |
| 15 | `Offer.java:75` | `DELETE FROM offers WHERE id=?` | Delete offer |
| 16 | `Market.java:74` | `SELECT * FROM offers ... WHERE resource=? AND buy=false ... ORDER BY price` | Every tick × 11 resources |
| 17 | `Market.java:75` | `SELECT * FROM offers ... WHERE resource=? AND buy=true ... ORDER BY RAND()` | Every tick × 11 resources |
| 18 | `Market.java:100` | `UPDATE users AS u, production AS p SET u.money=u.money+?, p.count=p.count-? ...` | Auto-sell per seller |
| 19 | `Market.java:141` | `INSERT INTO production (...) VALUES (?,?,?,?,?,?)` | First trade for resource |
| 20 | `Market.java:153` | `UPDATE users AS u, production AS p SET u.money=u.money-?, p.count=p.count+? ...` | Buyer matched |
| 21 | `Market.java:161` | `UPDATE users AS u, production AS p SET u.money=u.money+?, p.count=p.count-? ...` | Seller matched |
| 22 | `World.java:97` | `SELECT id FROM users` | Every tick |

**Total unique queries:** 22
**Queries per tick (simulation):** 1 (user list) + 22 (market: 11 resources × 2 queries) + N (per-user updates) ≈ 23 + 3N
**Queries per client poll:** 4–6

---

## Appendix B — Complete API Endpoint Map

### `GET /` — Index Page

```
Request:  (none)
Auth:     Optional (redirects to /game if connected)
Response: HTML (index.jsp)
```

### `POST /` — Authentication

```
Request:  action=login|signup|logout, user=<string>, pass=<string>
Auth:     None required
Response: Redirect → /game (success) or HTML with error (failure)
```

### `GET /game` — Main Game Page

```
Request:  (none)
Auth:     Required (redirects to / if not connected)
Response: HTML (game.jsp)
```

### `GET /update` — Game State Poll

```
Request:  (none)
Auth:     Required (redirects to / if not connected)
Response: application/json
{
    "user": "<username>",
    "user_id": "<id>",
    "money": <long>,
    "topPlayer": "<username>",
    "topMoney": "<formatted>",
    "resources": {
        "<name>": {
            "id": <int>,
            "name": "<name>",
            "count": <long>,
            "price": <long>,
            "production_cost": <int>,
            "production": <long>,
            "research_cost": <long>,
            "research": <long>
        },
        ...
    },
    "offers": [
        {
            "id": <long>,
            "user_id": "<long>",
            "user_name": "<string>",
            "res_id": "<resource_name>",
            "buy": <boolean>,
            "price": <long>,
            "quantity": <long>
        },
        ...
    ]
}
```

### `POST /action` — Game Action

```
Request:  action=addProduction   + resource=<int>
          action=publish         + resource=<int>, buy=<bool>, price=<string>, quantity=<long>
          action=search          + resource=<int>, buy=<bool>, price=<string>, quantity=<long>
          action=delete          + id=<long>
          action=changeResearch  + resource=<int>, cost=<string>
Auth:     Required
Response: application/json (same as /update)
```

---

## Appendix C — Resource Dependency Graph

### Raw Resources (No Recipe — Produced Directly)

```
wheat     (id: 0)
iron      (id: 2)
copper    (id: 4)
gold      (id: 5)
petrol    (id: 6)
```

### Crafting Chain

```
wheat ──(×2)──→ bread     (id: 1)  [auto-sell]
iron  ──(×1)──→ steel     (id: 3)

petrol ──(×1)──→ plastic  (id: 7)

plastic ──(×3)──┐
copper  ──(×1)──┼──→ circuit  (id: 8)
gold    ──(×1)──┘

steel   ──(×10)──┐
circuit ──(×1)───┼──→ car     (id: 9)  [auto-sell]
petrol  ──(×5)───┘

steel   ──(×3)───┐
circuit ──(×5)───┤
plastic ──(×3)───┼──→ phone   (id: 10) [auto-sell]
copper  ──(×1)───┘
```

### Auto-Sell Resources

Only three resources can be auto-sold to the market at real-world-derived prices:

| Resource | Price Source | Stock Symbol | Multiplier |
|---|---|---|---|
| bread | Walmart | WMT | `price / 10` |
| phone | Apple | AAPL | `price × 5` |
| car | Tesla | TSLA | `price × 10` |

Prices are fetched every 15 minutes from Yahoo Finance API and stored in `Market.prices[]`.

---

## Summary of Critical Issues (Ranked by Severity)

| # | Phase | Issue | Severity |
|---|---|---|---|
| 1 | 3 | Single shared JDBC connection (no pooling, no thread safety) | Critical |
| 2 | 5 | Race conditions in concurrent simulation threads | Critical |
| 3 | 3 | SHA-512 with static 4-byte salt for passwords | Critical |
| 4 | 5 | API key hardcoded in source code | Critical |
| 5 | 2 | `Offer.update()` writes to wrong table (`production` not `offers`) | Critical |
| 6 | 4 | No authorization check on offer deletion (any user can delete any offer) | Critical |
| 7 | 1 | No PRIMARY KEY on `production` table | Critical |
| 8 | 4 | Negative research cost enables infinite money exploit | High |
| 9 | 3 | JDBC resources (ResultSet, PreparedStatement) never closed | High |
| 10 | 5 | No database transactions for multi-step operations | High |
| 11 | 6 | Polling creates 4-6 DB queries per client every 2 seconds | High |
| 12 | 7 | `update.jsp` produces invalid JSON on error | High |
| 13 | 4 | UpdateServlet re-runs search query on every poll forever | Medium |
| 14 | 8 | No CSRF protection on any endpoint | Medium |
| 15 | 8 | Floating-point errors in money parsing | Medium |
| 16 | 1 | No foreign key on `offers.user_id` | Medium |
| 17 | 1 | No UNIQUE constraint on `users.user` | Medium |
| 18 | 2 | String comparison with `==` instead of `.equals()` | Medium |
| 19 | 7 | JSON injection via unescaped username | Medium |
| 20 | 6 | No request deduplication in polling | Medium |

---

*End of analysis.*
