# Trade Empire — MVP Game Design Document

## Vision Statement

Trade Empire is a persistent multiplayer economic simulation where human players and LLM-powered AI corporations compete on equal footing. Players build production chains, trade resources on a dynamic marketplace, and sell consumer goods through shops — all within an economy shaped by supply, demand, and specialization that forces trade and strategic decisions.

The defining feature: **AI actors are not NPCs.** They are full participants connected via MCP (Model Context Protocol), with identical capabilities to human players. A human and an AI corporation use the same API and are bound by the same rules. Players can even run their own AI agents to automate their empire.

---

## Core Design Pillars

1. **Parity** — Human players and LLM agents have identical capabilities. No hidden advantages. The API is the game.
2. **Emergent Economy** — Prices, supply chains, and market dynamics emerge from player behavior, not designer fiat.
3. **Meaningful Decisions** — Every dollar spent on production is a dollar not spent elsewhere. Opportunity cost drives strategy.
4. **Interdependence** — Production complexity makes self-sufficiency expensive and slow. Trading is faster and cheaper.

---

## 1. Foundation & Infrastructure

### 1.1 Global Config System

A single server-side configuration file containing all tuning parameters (starting cash, costs, rates, timers). Hot-reloadable without server restart.

| Parameter | Default | Effect |
|-----------|---------|--------|
| Starting money | $1,000 | Early game speed |
| Facility build costs | $100–$2,000 | Expansion rate |
| Operating costs | $2–$30/tick | Profit margin pressure |
| Market fee | 2% | Trade friction |
| Luxury tax | 3% | Consumer good margin reduction |
| Decay rates | 0.2–2% per 60 ticks | Hoarding penalty |
| Consumer demand cycle periods | 5–45 min | Price volatility |
| Perlin noise octaves | 6 | Demand curve complexity |

### 1.2 Database Schema

New tables for facilities (with state), inventory, market orders, price history, shops, and chat messages. Clean relational design supporting all MVP game systems.

### 1.3 Unified REST API

Every game action is exposed as a REST endpoint. The UI makes zero direct DB calls. This guarantees parity between human players, MCP-connected AI agents, and custom clients.

```
GET  /api/state                        -> Full player state
GET  /api/market/{resource}            -> Orderbook for a resource
GET  /api/market/prices                -> Current prices for all resources
GET  /api/market/history/{resource}    -> Price history
GET  /api/leaderboard                  -> Top players by net worth
GET  /api/config                       -> Global game configuration values

POST /api/production/build             { resource }
POST /api/production/idle              { facilityId }
POST /api/production/activate          { facilityId }
POST /api/production/downsize          { facilityId }

POST /api/market/sell                  { resource, price, quantity, reserve }
POST /api/market/buy                   { resource, price, quantity, target }
DELETE /api/market/{offerId}

POST /api/shop/stock                   { shopId, resource, quantity }
POST /api/shop/price                   { shopId, resource, price }
GET  /api/shop/{shopId}/sales          -> Shop sales data

POST /api/chat/send                    { targetPlayerId, message }
GET  /api/chat/messages                -> Recent messages
```

#### Parity Guarantee

The UI makes **zero** direct database calls or server-side computations outside the API. Every button click in the browser is a REST call. This guarantees that any MCP agent or custom client has identical capability.

### 1.4 Simulation Tick Engine

A reliable per-tick processing loop that drives the entire economy. Each tick executes in strict order:

1. **Operating costs** — Deduct from all active facilities
2. **Production** — Run all active facilities, consume inputs, produce outputs
3. **Resource decay** — Apply spoilage to perishable goods
4. **Shop sales** — Process NPC consumer purchases
5. **Market matching** — Execute pending buy/sell orders
6. **Auto-idle** — Idle facilities for players who can't afford operating costs

The tick engine is transactional — if any step fails, the entire tick rolls back. Error handling ensures one broken facility doesn't halt the economy.

---

## 2. Resource & Production System

*See [resources.md](resources.md) for the full resource list, recipes, and production chains.*

### 2.1 Resource Tiers

| Tier | Description | Examples |
|------|-------------|----------|
| **Raw** (12) | Extracted or harvested | Wheat, Iron, Copper, Gold, Petrol, Cotton, Timber, Lithium, Rubber, Silicon, Bauxite, Coal |
| **Intermediate** (11) | Processed from raw materials | Bread, Steel, Plastic, Circuit, Fabric, Lumber, Glass, Aluminium, Battery, Rubber Compound, Canned Food |
| **Consumer** (9) | Final products sold in shops | Car, Phone, Clothing, Furniture, Laptop, Bicycle, Jewelry, Bread*, Canned Food* |

Recipes support multi-input crafting. For example:
- **Steel** requires Iron (2) + Coal (1)
- **Phone** requires Circuit (1) + Glass (1) + Battery (1) + Aluminium (1)
- **Car** requires Steel (4) + Rubber Compound (2) + Glass (2) + Circuit (2) + Battery (1)

### 2.2 Facility States

Each facility operates as a state machine:

| State | Effect |
|-------|--------|
| **Active** | Produces at full efficiency. Incurs full operating cost per tick. |
| **Idle** | Produces nothing. Incurs 30% maintenance cost (staff on standby, facility upkeep). |

### 2.3 Facility Management

| Action | Effect | Cost |
|--------|--------|------|
| **Build facility** | Creates a new facility for a specific resource | Build cost (immediate) |
| **Idle a facility** | Stops production, reduces operating cost to 30% | Free (instant) |
| **Reactivate a facility** | Resumes production at full cost | Free (instant) |
| **Downsize (sell facility)** | Permanently removes a facility. Recovers 40% of original build cost. | Instant |

### 2.4 Operating Costs

Every active facility incurs a per-tick operating cost:

| Tier | Build Cost | Operating Cost / tick |
|------|-----------|---------------------|
| Raw | $100 | $2.00 |
| Intermediate | $300 | $5.00 |
| Advanced Intermediate | $800 | $12.00 |
| Consumer | $2,000 | $30.00 |

Operating costs are deducted automatically each simulation tick. If a player cannot afford operating costs, facilities are auto-idled starting from the most expensive, and a notification is sent.

**Production is never free after the initial build** — running an empire has ongoing costs, creating real pressure on margins.

### 2.5 Production Capacity vs. Actual Output

Each facility has a **production capacity** (units per tick) and an **actual output** that may be lower if:

- Input resources are insufficient (e.g., no Iron means no Steel)
- The facility has been recently built and is ramping up

The Production UI shows both numbers side-by-side so players can identify bottlenecks and understand *why* they're under-producing.

---

## 3. Shops & Consumer Sales

### 3.1 How Shops Work

Consumer goods (market goods) are sold through **Shops** — retail locations where NPC customers purchase goods.

1. Player builds or acquires a shop
2. Player stocks the shop with consumer goods
3. Player sets a **price** — either a fixed price or "match current market rate"
4. **NPC customers** buy from the shop based on demand

### 3.2 Dynamic Pricing (Perlin Noise Demand)

Consumer goods have dynamic base prices driven by a **demand multiplier**:

```
Effective Price Cap = Base Price x Demand Multiplier x Saturation Penalty

Demand Multiplier = 1D Perlin noise (multi-octave, with octave parameters also driven by Perlin noise)
Saturation Penalty = decreases as more units are sold per cycle
```

| Consumer Good | Base Price | Demand Cycle Period |
|---------------|-----------|-------------------|
| Bread / Canned Food | $3.50 – $5.00 | 5 minutes |
| Clothing / Bicycle | $50.00 – $150.00 | 10 minutes |
| Furniture | $500.00 | 15 minutes |
| Phone / Laptop | $899.00 | 20 minutes |
| Jewelry | $2,500.00 | 30 minutes |
| Car | $35,000.00 | 45 minutes |

- **Demand Multiplier**: 1D Perlin noise oscillating between 0.7x and 1.4x base price. Multiple octaves with high detail. The octave parameters themselves (frequency, amplitude, persistence) are also driven by separate Perlin noise functions — creating organic, unpredictable economic cycles.

### 3.3 Saturation Penalty

Each unit sold in a demand cycle reduces the effective price for subsequent units. Resets each cycle. This prevents one mega-producer from dumping infinite goods at peak price — encouraging competition and geographic spread.

### 3.4 Visible Demand Forecast

Players can see the current demand multiplier and trend direction (rising/falling) but not the exact future price. This enables speculation — players who read trends correctly can time their production and shop stocking for maximum profit.

---

## 4. Market & Trading

### 4.1 Order Matching Engine

The market uses **price-time priority matching** — the standard exchange mechanic:

- **Sell offers**: "I want to keep at least N in stock; sell everything above that at price P." (Reserve system — auto-replenishes)
- **Buy offers**: "I want to reach N total stock; buy up to (N - current) at up to price P." (Target system — auto-adjusts quantity)
- Matching: cheapest sellers matched to highest bidders (price-time priority)
- **Market price** = last executed trade price (not cheapest ask)

### 4.2 Sell Offers with Reserve

Players set a reserve floor: "Keep at least N in stock, sell the rest at price P." The system automatically recalculates available quantity as production adds to inventory. This enables "set and forget" selling — critical for players who can't watch the market constantly and essential for AI agents to operate smoothly.

### 4.3 Buy Offers with Target

The inverse of reserve — players set a target: "Buy up to (N - current stock) at up to price P." The system auto-adjusts the buy quantity as stock arrives. Key for automated supply chain management and AI agent behavior.

### 4.4 Price History Charts

For every resource, the market displays:

- **BUY price history** — line chart over time
- **SELL price history** — line chart over time
- **Volume traded** — bar chart over time
- Filterable by time range (last 5 min, 30 min, 2 hours, all time)

Charts enable speculation, trend reading, and timing — the core of market gameplay. Without them, trading would be blind gambling.

---

## 5. Fees & Taxes

### 5.1 Market Transaction Fee (2%)

A percentage deducted from both buyer and seller on every market trade:

- 1% from buyer, 1% from seller
- Revenue goes to the Central Bank reserve
- Essential money sink preventing infinite inflation
- Small enough not to discourage trading, large enough to matter at scale

### 5.2 Luxury Tax (3%)

An additional tax on all consumer good shop sales:

- Deducted from shop revenue automatically
- Targets the highest-revenue activity in the game
- Ensures consumer good production isn't disproportionately profitable

---

## 6. Resource Decay & Spoilage

Perishable resources decay over time if not used or sold:

| Resource | Decay Rate | Rationale |
|----------|-----------|-----------|
| Wheat | 1% per 60 ticks | Grain spoils |
| Bread | 2% per 60 ticks | Baked goods go stale |
| Canned Food | 0.2% per 60 ticks | Preserved but not forever |
| Petrol | 0.5% per 60 ticks | Fuel evaporates/degrades |
| Rubber | 0.3% per 60 ticks | Degrades over time |
| Cotton | 0.5% per 60 ticks | Can mildew |

Non-perishable resources (metals, circuits, glass, etc.) don't decay. This prevents infinite hoarding and creates urgency to sell or process raw goods.

---

## 7. AI & MCP Integration

### 7.1 Design Philosophy

AI actors are **first-class citizens**. They are not NPCs with special rules — they are players that happen to be driven by an LLM instead of a human. They use the exact same API, follow the exact same rules, and appear identically in the UI (with an optional badge).

### 7.2 MCP Server Architecture

```
+------------------+     MCP      +--------------------------+
|  Claude / GPT /  |<------------>|  Trade Empire MCP Server |
|  Any LLM Agent   |   (tools +   |  (wraps game REST API)   |
|                  |   resources)  |                          |
+------------------+              +----------+---------------+
                                             | HTTP
                                             v
                                  +--------------------------+
                                  |  Trade Empire Game Server |
                                  |  (Java/Tomcat)           |
                                  +--------------------------+
```

The MCP server wraps the REST API, exposing all game actions as MCP tools and game state as MCP resources. Any LLM that supports MCP can play immediately — no custom integration needed.

### 7.3 MCP Tools (1:1 with Player Capabilities)

Every action a human player can take in the UI maps to an MCP tool:

**Account & State:**
- `get_game_state` — Full state snapshot (inventory, money, facilities, offers)
- `get_market_prices` — Current prices for all resources
- `get_leaderboard` — Top players by net worth

**Production:**
- `build_facility` — Start constructing a new facility
- `idle_facility` — Pause a facility
- `activate_facility` — Resume a paused facility
- `downsize_facility` — Permanently sell a facility

**Market:**
- `post_sell_offer` — List resources for sale (with optional reserve)
- `post_buy_offer` — Post a buy order (with optional target)
- `cancel_offer` — Cancel own offer
- `search_offers` — Browse the orderbook for a resource
- `get_price_history` — Historical prices for a resource

**Shops:**
- `stock_shop` — Send consumer goods to a shop
- `set_shop_price` — Set the selling price for a shop
- `get_shop_sales` — View sales data for a shop

**Social:**
- `send_message` — Send a chat message to a player

**MCP Resources (read-only context):**
- `game://rules` — Complete game rules and formulas
- `game://recipes` — All crafting recipes
- `game://my/state` — Current player state
- `game://market/prices` — Live market prices
- `game://market/history/{resource}` — Price history
- `game://leaderboard` — Current rankings

### 7.4 System AI Corporations

The server runs built-in **AI corporations** to ensure the economy functions even with few human players:

| AI Corporation | Strategy Profile | Focus |
|----------------|-----------------|-------|
| **AgriCorp** | Conservative, high-volume commodity trader | Agriculture, food chain |
| **TechVentures** | Aggressive, targets high-value consumer goods | Electronics, tech chain |
| **IronWorks** | Mid-chain specialist, sells intermediates | Steel, industrial goods |
| **LuxuryCraft** | Premium consumer goods, high-margin low-volume | Jewelry, cars |

AI corporations:
- Start with the same $1,000 as human players
- Make decisions every 5–10 seconds (configurable thinking interval)
- Have distinct but non-cheating strategies (they can only see what any player can see)
- Are clearly labeled in the UI with a robot badge
- Can be enabled/disabled by the server admin

### 7.5 User-Controlled AI Agents

Players can connect their own LLM agents to play on their behalf:

1. Player generates an **API key** from the settings menu
2. API key authenticates as that player's account via the MCP server
3. The player's own LLM agent (Claude, GPT, etc.) connects and acts as the player
4. While an AI agent is connected, the player can still use the UI — both can act simultaneously
5. Players are responsible for their agent's actions

---

## 8. Player Chat

### 8.1 Direct Messaging

Players can send direct messages to any other player (human or AI). Chat is accessible from the UI sidebar and from any player profile.

- Messages are stored in the database
- AI agents can send and receive messages via the API (`send_message` / `get_messages`)
- Enables trade negotiation, coordination, and social interaction
- Without chat, multiplayer feels like playing against bots — chat turns "other players" into "people"

---

## 9. UI Architecture

### 9.1 Tab-Based Layout

```
+------------------------------------------------------------------+
|  Trade Empire     $14,230                Paul         [Settings]   |
+----------+-------------------------------------------------------+
|          |  [Production] [Market] [Chat]                          |
|          |                                                        |
| SIDEBAR  +-------------------------------------------------------+
|          |                                                        |
| Raw      |              (Active tab content)                      |
|  Wheat   |                                                        |
|  Iron    |                                                        |
|  Copper  |                                                        |
|  Gold    |                                                        |
|  Petrol  |                                                        |
|  Cotton  |                                                        |
|  Timber  |                                                        |
|  Lithium |                                                        |
|  + more  |                                                        |
|          |                                                        |
| Crafted  |                                                        |
|  Steel   |                                                        |
|  Plastic |                                                        |
|  Fabric  |                                                        |
|  + more  |                                                        |
|          |                                                        |
| Consumer |                                                        |
|  Car     |                                                        |
|  Phone   |                                                        |
|  + more  |                                                        |
|          |                                                        |
+----------+-------------------------------------------------------+
```

**Tabs:**

1. **Production** — Per-resource view showing all facilities producing it, capacity vs. actual output. Global view showing all factories. Build/idle/activate/downsize controls.
2. **Market** — Orderbook, price charts (BUY/SELL lines), post/cancel offers with reserve/target, volume history.
3. **Chat** — Direct messaging with other players.

### 9.2 Sidebar Resource Navigation

A collapsible sidebar listing all resources organized by tier (Raw, Intermediate, Consumer). Clicking a resource filters the current tab to show data for that resource. Quick navigation across 32+ resources without excessive clicking.

### 9.3 Production Tab

The operational command center for managing your empire:

**Per-Resource View** (when a resource is selected in sidebar):
- All facilities producing this resource
- Production capacity vs. actual output for each facility
- Facility controls (idle / activate / downsize)
- Input resource requirements and current stock levels

**Global View** (no resource selected):
- All facilities across all resources in one table
- Sortable by resource, status, output, operating cost
- Quick-action buttons for idle/activate

### 9.4 Persistent Header

Always visible at the top of the screen:
- Game name
- Current cash balance
- Player name
- Settings button

---

## 10. Implementation Priority

### Phase 1 — Foundation (start here)

| Feature | Section | Notes |
|---------|---------|-------|
| Global Config System | 1.1 | All tuning values in one file. Everything else depends on this. |
| Database Schema | 1.2 | New tables for all MVP systems. |
| Unified REST API | 1.3 | Every action as an endpoint. UI uses only the API. |
| Tick Engine Overhaul | 1.4 | Reliable ordered processing. The heartbeat of the game. |

### Phase 2 — Core Economy

| Feature | Section | Notes |
|---------|---------|-------|
| Resource & Recipe System | 2.1 | 12 raw, 11 intermediate, 9 consumer goods. Multi-input crafting. |
| Facility Management | 2.2–2.4 | Build / idle / activate / downsize. Operating costs per tick. |
| Production Capacity Display | 2.5 | Show theoretical max vs. actual output. |
| Dynamic Consumer Pricing | 3.2 | Perlin noise demand multiplier. |
| Saturation Penalty | 3.3 | Price decreases with volume sold per cycle. |
| Demand Forecast | 3.4 | Visible multiplier and trend direction. |
| Order Matching Engine | 4.1 | Price-time priority. Proper exchange semantics. |
| Sell with Reserve | 4.2 | "Keep N, sell rest at P." |
| Buy with Target | 4.3 | "Buy up to N at up to P." |
| Price History Charts | 4.4 | Line charts per resource, BUY/SELL lines. |
| Market Transaction Fee | 5.1 | 2% fee, essential money sink. |
| Luxury Tax | 5.2 | 3% on consumer sales. |
| Resource Decay | 6 | Perishable goods lose stock over time. |

### Phase 3 — AI Integration

| Feature | Section | Notes |
|---------|---------|-------|
| MCP Server | 7.2–7.3 | Wraps REST API for LLM agents. The unique value proposition. |
| System AI Corporations | 7.4 | Built-in bots with distinct strategies. |
| User AI Agents (API Keys) | 7.5 | Players connect their own LLM agents. |

### Phase 4 — UI & Social

| Feature | Section | Notes |
|---------|---------|-------|
| Tab-Based UI | 9.1 | Production, Market, Chat tabs. |
| Sidebar Navigation | 9.2 | Resource list by tier. |
| Production Tab | 9.3 | Per-resource and global facility views. |
| Player Chat | 8.1 | Direct messaging. |

---

## 11. Session Flow Example

**New player "Alice" joins:**

1. Signs up, gets $1,000 starting cash. Sees the Production tab — no facilities yet.
2. Builds 2 Wheat facilities ($100 each) and 1 Iron facility ($100). Operating costs: $6/tick.
3. Wheat and Iron start accumulating every tick.
4. Checks the Market tab — sees current prices for all resources. Price history shows Iron trending up.
5. Posts sell offer: "Selling Wheat at $2.00, keep 100 in reserve." The reserve means she always has stock for her own crafting.
6. "AgriCorp_AI" buys her Wheat. She earns $400.
7. Needs more Iron and Coal to make Steel. Posts buy offer: "Buy Iron up to 200 stock at up to $3.00." The target system auto-adjusts as Iron arrives.
8. Builds a Steel facility ($300). Iron and Coal are consumed, Steel accumulates. Operating cost rises to $11/tick.
9. Notices Wheat decaying in storage (1% per 60 ticks) — better sell it or process into Bread before it spoils.
10. Checks demand forecast: Phone demand multiplier is rising. Decides to work toward Phone production.
11. Over 20 minutes, builds up the chain: Circuit, Glass, Battery, Aluminium, then Phone.
12. Stocks a shop with Phones. Sets price to match demand. Sales trickle in — but the saturation penalty means she can't dump infinite stock.
13. Checks the price chart: Phone demand is near peak. Good timing!
14. "TechVentures_AI" messages via chat: "Selling Circuit at $5. Interested?"
15. Market fee (2%) and luxury tax (3%) eat into margins — but she's profitable. Net cash is growing.

**Meanwhile, "IronWorks_AI":**
1. Builds Iron, Coal, and Steel facilities.
2. Sells Steel on the market with a reserve of 500 units.
3. Undercuts other Steel sellers by $0.10 — price-time priority means it gets matched first.
4. Never builds consumer goods — it's a pure intermediate supplier.

This emergent specialization — Alice as a consumer goods manufacturer, IronWorks as an industrial supplier — arises naturally from the game mechanics.
