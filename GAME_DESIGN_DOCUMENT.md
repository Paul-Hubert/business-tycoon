# Trade Empire — Game Design Document

## Vision Statement

Trade Empire is a persistent multiplayer economic simulation set on a global map where human players and LLM-powered AI corporations compete on equal footing. Players claim land, build production chains across real-world geography, trade resources on a dynamic marketplace, manage finances through loans and credit, and navigate an economy shaped by distance, location, and specialization — forcing trade, logistics planning, and strategic alliances.

The defining feature: **AI actors are not NPCs.** They are full participants connected via MCP (Model Context Protocol), with identical capabilities to human players. A human and an AI corporation see the same map, use the same API, and are bound by the same rules. Players can even run their own AI agents to automate their empire.

---

## Core Design Pillars

1. **Geography Matters** — Production, trade, and sales are all tied to physical locations on a world map. Distance costs money. Location drives strategy.
2. **Parity** — Human players and LLM agents have identical capabilities. No hidden advantages. The API is the game.
3. **Emergent Economy** — Prices, credit rates, supply chains, and regional economies emerge from player behavior, not designer fiat.
4. **Meaningful Decisions** — Every dollar spent on land is a dollar not spent on research, production, or market speculation. Opportunity cost drives strategy.
5. **Interdependence** — The research tree and production complexity make self-sufficiency expensive and slow. Trading is faster and cheaper in the early and mid game.

---

## 1. World Map & Geography

### 1.1 The Globe

The game world is a **full interactive globe** displaying the real world map. Players can rotate, zoom, and click anywhere to interact with the world.

**What the map shows:**
- **Unclaimed land** — Available for purchase, color-coded by land type
- **Player-owned land** — Colored/branded per player, showing facility icons
- **Other players' companies** — Clickable to open the Location View
- **Trade routes** — Animated lines showing active goods movement between locations
- **Regional demand heatmaps** — Toggle overlay showing consumer demand intensity by region

### 1.2 Land Types

Every plot of land has a **type** that determines what can be built on it:

| Land Type | Can Build | Examples |
|-----------|-----------|----------|
| **Farm** | Agricultural production (crops, livestock) | Wheat farms, cattle ranches |
| **Factory** | Industrial production (raw extraction, processing, assembly) | Mines, refineries, assembly plants |
| **Shop** | Retail storefronts for selling consumer goods | Bakeries, car dealerships, phone stores |

### 1.3 Buying Land

Before any production can begin, a player must **purchase land**:

- Land prices vary by region using a **dynamic pricing model**:
  ```
  Land Price = Base Price × Offer Multiplier × Saturation Penalty
  ```
- **Offer Multiplier** — Fluctuates per region based on demand. Popular areas cost more.
- **Saturation Penalty** — The more land already owned by players in an area, the higher the price for new plots.
- Each plot of land has a **maximum production capacity** — the number of facilities that can operate on it.
- Players can own land in multiple regions across the globe.

### 1.4 Location View

Clicking on any owned plot (yours or another player's) opens the **Location View**:

- Owner name and link to their **Player View**
- Land type (Farm / Factory / Shop)
- Production resource type(s) active on this plot
- Number of facilities and their status (active / idle / under construction)
- Production capacity used vs. maximum

### 1.5 Player View

Clicking on any player (from Location View, leaderboard, or market) opens the **Player View**:

- **Annual revenue** and key financial metrics
- **Production resource types** — what they produce and where
- **Current market offers** — their active buy/sell orders
- **Chat system** — direct messaging with the player

---

## 2. Resource & Production System

*See [resources.md](resources.md) for the full resource list, recipes, and production chains.*

### 2.1 Resource Tiers

| Tier | Description | Examples |
|------|-------------|----------|
| **Raw** | Extracted or harvested from land | Wheat, Iron, Copper, Gold, Petrol, Cotton, Timber, Lithium, Rubber, Silicon, Bauxite, Coal |
| **Intermediate** | Processed from raw materials | Bread, Steel, Plastic, Circuit, Fabric, Lumber, Glass, Aluminium, Battery, Rubber Compound |
| **Consumer (Market Goods)** | Final products sold in shops | Car, Phone, Clothing, Furniture, Laptop, Bicycle, Canned Food, Jewelry |

### 2.2 Key Rules

- Raw resources require **Farm** or **Factory** land (depending on the resource).
- Intermediate resources require **Factory** land.
- Consumer goods are **market goods** — they must be sold through a **Shop**.
- Every resource must be **unlocked via the research tree** before it can be produced (see Section 5).

---

## 3. Land, Facilities & Production Manager

### 3.1 Production Prerequisites

To produce any resource, a player must:

1. **Own appropriate land** — the right type (Farm/Factory) in some location
2. **Unlock the resource** — via the research tree (Section 5)
3. **Build a facility** — pay the build cost and wait for construction

### 3.2 Facility States

| State | Effect |
|-------|--------|
| **Active** | Produces at full efficiency. Incurs full operating cost per tick. |
| **Idle** | Produces nothing. Incurs 30% maintenance cost (staff on standby, facility upkeep). |
| **Under Construction** | Not yet producing. Construction time depends on complexity tier. |

### 3.3 Build Delay & Costs

Building new facilities takes time and money. Complex goods require longer construction:

| Tier | Build Time | Build Cost |
|------|------------|------------|
| **Raw** | 30 seconds | $100 |
| **Intermediate** | 2 minutes | $300 |
| **Advanced Intermediate** | 5 minutes | $800 |
| **Consumer** | 10 minutes | $2,000 |

- Payment is taken upfront when construction begins.
- A progress bar shows time remaining.
- Multiple facilities can be constructed simultaneously (no queue limit), but each costs money immediately.
- Facilities under construction are visible to other players via the API and on the world map (enabling market intelligence).
- Each facility is bound to a specific land plot and counts against that plot's **maximum production capacity**.

### 3.4 Facility Management

| Action | Effect | Cost |
|--------|--------|------|
| **Idle a facility** | Stops production, reduces operating cost to 30% | Free (instant) |
| **Reactivate a facility** | Resumes production at full cost | Free (instant) |
| **Downsize (sell facility)** | Permanently removes a facility. Recovers 40% of original build cost. | Instant |
| **Cut production rate** | Set a facility to produce at 50% capacity (half output, 60% cost) | Free (instant) |

### 3.5 Operating Costs

Every active facility incurs a per-tick operating cost:

| Tier | Operating Cost / tick |
|------|---------------------|
| Raw | $2.00 |
| Intermediate | $5.00 |
| Advanced Intermediate | $12.00 |
| Consumer | $30.00 |

Operating costs are deducted automatically each simulation tick. If a player cannot afford operating costs, facilities are auto-idled starting from the most expensive, and a notification is sent.

**Production is never free after the initial build** — running an empire has ongoing costs, creating real pressure on margins.

### 3.6 Production Capacity vs. Actual Output

Each facility has a **production capacity** (units per tick) and an **actual output** that may be lower if:

- Input resources are insufficient (e.g., no Iron means no Steel)
- The facility is set to 50% rate
- Research efficiency bonuses haven't been unlocked yet

The Production UI shows both numbers side-by-side so players can identify bottlenecks.

---

## 4. Shops & Selling Consumer Goods

### 4.1 How Shops Work

Consumer goods (market goods) cannot be sold on the open market like raw/intermediate resources. They must be sold through **Shops** — retail locations on the world map.

1. Player buys **Shop land** in a location
2. Player stocks the shop with consumer goods (transported from factories)
3. Player sets a **price** — either a fixed price or "match current market rate"
4. **Customers (NPCs)** buy from the shop based on demand

### 4.2 Shop Demand Model

Each shop's sales volume depends on:

```
Sales = Regional Demand × Price Competitiveness × Proximity Weight

Regional Demand = Base Demand × Demand Multiplier (perlin noise)
Price Competitiveness = inverse relationship — lower price = more sales
Proximity Weight = demand split among nearby shops of the same type
```

- **Proximity effect**: If two phone shops are close together, they split the regional demand. Spreading shops across different regions captures more total demand.
- **Saturation**: Too many shops of the same type in a region drives down sales per shop and increases land prices.
- **Demand is divided**, not duplicated — total regional demand is split across all shops weighted by price and proximity.

### 4.3 Consumer Good Pricing (Dynamic NPC Demand)

Consumer goods have dynamic base prices driven by a **demand multiplier**:

```
Effective Price Cap = Base Price × Demand Multiplier × Saturation Penalty

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
- **Saturation Penalty**: Each unit sold in a cycle reduces the effective price for subsequent units. Resets each cycle. Prevents dumping.
- **Visible Forecast**: Players can see the current demand multiplier and trend direction (rising/falling) but not the exact future price — enabling speculation.

---

## 5. Research Tree

### 5.1 Replacing Sector Licenses

There are no sector licenses. Instead, every resource has its own **research tree** that must be progressed to unlock and improve production. This creates natural specialization — players invest research into the resources they want to focus on — while allowing anyone to eventually unlock anything given enough time and money.

### 5.2 Research Mechanics

- Players **pick a tech branch** to research and **spend money each tick** to fund it.
- Research speed scales **logarithmically** with spending — doubling your investment does not double your speed. Diminishing returns prevent whales from instantly unlocking everything.
- Players can research **multiple branches simultaneously** but each splits their budget.
- Researching a tech **automatically researches all prerequisites** in sequence if they aren't already completed.

```
Research Speed = log(1 + spending_per_tick / base_cost) × efficiency_modifier
```

### 5.3 Research Tree Structure (Per Resource)

Each resource has a branching tree:

```
[Unlock Production] ──→ [Efficiency I] ──→ [Efficiency II] ──→ [Efficiency III]
        │                     │
        │                     └──→ [Reduced Operating Costs I] ──→ [Reduced Costs II]
        │
        └──→ [Input Conservation I] ──→ [Input Conservation II]
                    │
                    └──→ [Bulk Processing] (craft 2 units per cycle)
                              │
                              └──→ [Advanced Logistics] (reduce input waste by 10%)
```

| Tech Node | Effect | Typical Research Time |
|-----------|--------|----------------------|
| **Unlock Production** | Allows building facilities for this resource | 1–5 minutes (raw), 5–15 min (intermediate), 15–30 min (consumer) |
| **Efficiency I–III** | +25% output per level | 5–20 minutes each |
| **Reduced Operating Costs I–II** | -15% operating cost per level | 10–30 minutes each |
| **Input Conservation I–II** | % chance input resources are not consumed during crafting | 10–25 minutes each |
| **Bulk Processing** | Produce 2 units per cycle instead of 1 | 30–60 minutes |
| **Advanced Logistics** | Reduce input waste by 10% | 20–40 minutes |

### 5.4 Research UI

- **Per-resource tab**: Shows the full tech tree for one resource, with progress bars, costs, and unlock status
- **Global tab**: Shows all currently active research across all resources, with progress and spending allocation

---

## 6. Transport & Logistics

### 6.1 Distance Costs

Moving goods between locations costs money based on **distance** and **volume**:

```
Transport Cost = Base Rate × Distance (km) × Volume (units) × Weight Modifier
```

| Resource Tier | Weight Modifier |
|---------------|----------------|
| Raw | 1.0× (heavy, bulky) |
| Intermediate | 0.7× |
| Consumer | 0.5× (high-value, compact) |

### 6.2 Implications

- Producing Wheat in Germany and shipping it to your Bread factory in France costs more than co-locating both in France.
- Trade offers on the market include a **transport surcharge** based on the distance between buyer and seller.
- Players near each other can trade cheaply — creating **regional trade clusters**.
- Vertically integrating in one region is cheaper for logistics but may mean higher land prices.
- This makes **geography a strategic decision**: cheap land far away means high transport costs; expensive land in a hub means lower logistics but more competition.

### 6.3 Transport in the UI

- Trade offers show the transport cost to your nearest storage location
- The world map can display trade route lines with cost overlays
- A logistics summary in the Production tab shows total transport spending

---

## 7. Dynamic Pricing & Markets

### 7.1 Player-to-Player Market

The order-matching system for raw and intermediate resources:

- **Sell offers**: "I want to keep at least N in stock; sell everything above that at price P."
- **Buy offers**: "I want to reach N total stock; buy up to (N - current) at up to price P."
- Matching: cheapest sellers matched to highest bidders (price-time priority).
- **Market price** = last executed trade price (not cheapest ask).
- **Transport surcharge**: Automatically added based on distance between buyer and seller.

### 7.2 Raw Material Price Discovery

Raw material prices emerge purely from player trading. The displayed "market price" is the weighted average of the last 20 trades. No NPC floor or ceiling — prices can crash to near-zero or spike based on supply/demand.

### 7.3 Price Charts & Graphs

For every resource, the market displays:

- **BUY price history** — line chart over time
- **SELL price history** — line chart over time
- **Volume traded** — bar chart over time
- Filterable by time range (last 5 min, 30 min, 2 hours, all time)

---

## 8. Finance Tab

A dedicated tab providing full transparency into the player's economic performance.

### 8.1 Income Statement (Per Tick & Cumulative)

```
+-------------------------------------------------------------+
|  FINANCIAL OVERVIEW              Period: Last 5 minutes      |
+-------------------------------------------------------------+
|                                                              |
|  REVENUE                                    $12,450.00       |
|  +-- Consumer Good Sales      $8,200.00        65.9%   ##>  |
|  |   +-- Car Sales            $7,000.00        56.2%        |
|  |   +-- Phone Sales          $1,100.00         8.8%        |
|  |   +-- Bread Sales            $100.00         0.8%        |
|  +-- Market Sales (P2P)       $3,800.00        30.5%   #=   |
|  |   +-- Steel                $2,100.00        16.9%        |
|  |   +-- Wheat                $1,200.00         9.6%        |
|  |   +-- Copper                 $500.00         4.0%        |
|  +-- Loan Interest Received     $450.00         3.6%   .    |
|                                                              |
|  EXPENSES                                   ($9,870.00)      |
|  +-- Operating Costs         ($4,200.00)       42.6%   ##   |
|  +-- Market Purchases (P2P)  ($3,100.00)       31.4%   #=   |
|  +-- Transport Costs         ($1,070.00)       10.8%   #    |
|  +-- Research Investment     ($1,500.00)       15.2%   #    |
|  +-- Facility Construction     ($800.00)        8.1%   =    |
|  +-- Land Purchases            ($500.00)        5.1%   =    |
|  +-- Loan Interest Paid        ($270.00)        2.7%   .    |
|  +-- Trade Fees & Taxes        ($430.00)        4.4%   .    |
|                                                              |
|  -----------------------------------------------------------  |
|  NET PROFIT                                  $2,580.00       |
|  PROFIT MARGIN                                   20.7%       |
|  CASH ON HAND                              $14,230.00        |
|  NET WORTH                                 $52,800.00        |
|                                                              |
|  [ Per Tick v ]  [ Trend Chart ]  [ Export CSV ]             |
+-------------------------------------------------------------+
```

### 8.2 Categories Tracked

**Revenue categories:**
- Consumer good shop sales (by product, by shop location)
- Player-to-player market sales (by resource)
- Loan interest received
- Facility downsizing refunds
- Contract income

**Expense categories:**
- Facility operating costs (by resource, by location)
- Facility construction costs
- Land purchases
- Market purchases (by resource)
- Transport costs (by route)
- Research investment (by resource)
- Loan interest paid
- Loan principal repayment
- Trade fees & taxes

### 8.3 Net Worth Calculation

```
Net Worth = Cash
          + Inventory Value (each resource x current market price)
          + Facility Value (each facility x 40% of build cost)
          + Land Value (each plot x current market value)
          + Outstanding Loans Receivable
          - Outstanding Loans Payable
```

### 8.4 Graphs & Trend Charts

Interactive line charts with filtering:

- **Revenue vs. Expenses over time** — filterable by category (e.g., show only "Operating Costs" or only "Car Sales")
- **Cash over time**
- **Net worth over time**
- **Individual resource prices over time** (BUY and SELL lines)
- **Production output over time** (per resource)
- **Transport costs over time** (per route)

All graphs support time range selection and category filtering.

### 8.5 Percentage Breakdowns

Every category shows:
- Absolute dollar amount
- Percentage of total revenue or total expenses
- A small bar indicator for visual scanning
- Comparison to previous period (up/down arrows with percentage change)

---

## 9. Loan & Credit System

### 9.1 System Bank

The game includes an automated **Central Bank** that offers loans at dynamic interest rates.

#### Credit Score

Each player has a hidden **credit score** (0–1000) that affects their borrowing terms:

| Factor | Effect on Score |
|--------|----------------|
| On-time loan repayments | +20 per repayment |
| Missed payments | -50 per miss |
| High net worth | +1 per $10,000 net worth (capped at +100) |
| Account age | +1 per 10 minutes of play (capped at +50) |
| Active income (positive cash flow) | +10 if profitable last period |
| Default (bankruptcy) | -200 |

Starting score: **500** (neutral).

#### Central Bank Loan Terms

| Credit Score Range | Interest Rate (per tick) | Max Loan Amount |
|-------------------|------------------------|-----------------|
| 800–1000 (Excellent) | 0.005% | 5x net worth |
| 600–799 (Good) | 0.01% | 3x net worth |
| 400–599 (Fair) | 0.02% | 1.5x net worth |
| 200–399 (Poor) | 0.05% | 0.5x net worth |
| 0–199 (Delinquent) | No loans available | $0 |

- Interest compounds per tick on outstanding principal.
- Repayment schedule: player chooses between fixed-term (e.g., repay in 300 ticks) or interest-only with balloon payment.
- **Early repayment** is always allowed with no penalty.
- **Default**: If a player's cash reaches $0 and they have outstanding loans, they enter **distressed** status. Facilities are auto-idled. If debt still remains after liquidation, the loan is written off and the player's credit score craters.

#### Dynamic Base Rate & Inflation

The Central Bank adjusts its base rate based on economic conditions, following a simplified version of real central bank policy:

- **Inflation tracking**: The system monitors total money supply and velocity of money in the economy.
- **Inflation rising** (money supply growing fast, prices increasing): Central Bank **raises base rate** — making loans more expensive, slowing expansion, cooling the economy.
- **Deflation / stagnation** (money supply contracting, prices dropping): Central Bank **lowers base rate** — making loans cheaper, encouraging borrowing and investment.
- Rate changes are announced to all players as system notifications.
- The inflation rate and current Central Bank rate are visible in the Finance tab.

### 9.2 Player-to-Player Loans

Players (and LLM agents) can offer loans to each other:

```
+---------------------------------------+
|  LOAN MARKETPLACE                     |
|                                       |
|  AVAILABLE LOANS                      |
|  +---------------------------------+  |
|  | Lender: MegaCorp_AI             |  |
|  | Amount: $5,000   Rate: 0.008%   |  |
|  | Term: 500 ticks  Score req: 400 |  |
|  | [ Accept Loan ]                 |  |
|  +---------------------------------+  |
|  | Lender: Player_Steve            |  |
|  | Amount: $20,000  Rate: 0.015%   |  |
|  | Term: 1000 ticks Score req: 300 |  |
|  | [ Accept Loan ]                 |  |
|  +---------------------------------+  |
|                                       |
|  [ Offer a Loan ]  [ My Loans ]      |
+---------------------------------------+
```

**Loan offer parameters:**
- **Amount**: How much the lender is offering
- **Interest rate**: Per-tick rate (can undercut or exceed the Central Bank)
- **Term**: Number of ticks until full repayment is due
- **Minimum credit score**: Lender can set a floor for borrower quality
- **Collateral requirement** (optional): Lender can require the borrower to hold a minimum net worth

**Loan lifecycle:**
1. Lender posts a loan offer (money is escrowed from their account)
2. Borrower accepts — money transfers instantly
3. Each tick: interest accrues, auto-deducted from borrower's cash
4. At term end: remaining principal auto-deducted. If borrower can't pay, they default (credit score damage, asset liquidation, lender recovers what's possible)

**Why this matters:**
- LLM agents can become banks, offering competitive loan rates and making money from interest
- Players can fund each other's expansion — a social/economic layer
- Predatory lending is possible (high rates to desperate players) — creating emergent drama
- Undercutting the Central Bank is viable if you have capital and want passive income

### 9.3 Loan API (MCP Parity)

All loan operations are available through the same API:

| Action | Params | Description |
|--------|--------|-------------|
| `offerLoan` | amount, rate, term, minScore | Post a loan offer |
| `acceptLoan` | loanId | Accept an available loan |
| `repayLoan` | loanId, amount | Make an early repayment |
| `cancelLoanOffer` | loanId | Cancel an unfunded loan offer |
| `searchLoans` | — | List available loan offers |
| `myLoans` | — | List active loans (as borrower and lender) |

---

## 10. Contracts & Supply Agreements

Players can propose **contracts** to other players or AI agents:

- "I will sell you 100 Steel per cycle at $X for the next 500 ticks"
- "I will buy all your Wheat at market price minus 5% for 1,000 ticks"

Contracts are binding — breaking a contract incurs a penalty (credit score hit + financial penalty). This creates supply chain stability and long-term strategic relationships. LLM agents can negotiate contracts via the API.

**Contract parameters:**
- Resource type and quantity per cycle
- Price (fixed, market-rate, or market +/- percentage)
- Duration (in ticks)
- Penalty for breach
- Transport cost responsibility (buyer pays, seller pays, or split)

---

## 11. Market Events & Economic Shocks

Periodic random events that disrupt the economy:

| Event | Effect | Duration |
|-------|--------|----------|
| **Commodity Boom** | One raw resource's demand doubles | 5 minutes |
| **Supply Shortage** | One raw resource's production halved globally | 3 minutes |
| **Tech Breakthrough** | Circuit/Phone production cost reduced 30% | 10 minutes |
| **Market Crash** | All consumer good prices drop 40% | 5 minutes |
| **Gold Rush** | Gold price spikes 3x | 3 minutes |
| **Trade Embargo** | Market fees doubled | 5 minutes |
| **Regional Boom** | One region's demand multiplier spikes 2x | 5 minutes |
| **Fuel Crisis** | Petrol transport costs tripled | 5 minutes |
| **Harvest Festival** | Agricultural output +50% globally | 5 minutes |

Events are announced **30 seconds before** they take effect, giving players (and AIs) time to react — post offers, idle facilities, take out loans, etc.

Average frequency: 1 event per 10 minutes (configurable).

---

## 12. Trade Fees & Taxation

A small transaction fee on all market trades creates a money sink:

- **Market fee**: 2% of trade value (split: 1% from buyer, 1% from seller)
- **Luxury tax**: Additional 3% on consumer good shop sales
- **Transport tax**: Included in transport cost calculation
- Tax revenue goes into the Central Bank reserve (funding future loans)

This prevents infinite money growth and makes direct contracts (Section 10) more attractive for high-volume traders.

---

## 13. Resource Decay & Spoilage

Perishable resources decay over time if not used:

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

## 14. Warehouse & Storage Limits

Each resource has a maximum storage capacity per location:

| Tier | Default Capacity | Upgrade Cost (per +1000) |
|------|-----------------|------------------------|
| Raw | 5,000 | $500 |
| Intermediate | 2,000 | $1,500 |
| Consumer | 500 | $5,000 |

When storage is full, production overflows and is lost. Players must either sell, process into higher-tier goods, transport to another location, or invest in warehouse expansion. This prevents passive accumulation and keeps trade volumes high.

Storage is **per location** — owning land in multiple regions gives you distributed storage.

---

## 15. Corporate Reputation & Branding

Players earn a public **reputation** based on their behavior:

- Fulfilling contracts: +reputation
- Offering fair loan rates: +reputation
- Defaulting on loans: -reputation
- Consistently low prices: +reputation (labeled "discount supplier")
- Consistently high prices: neutral (labeled "premium supplier")
- On-time deliveries: +reputation
- Breach of contract: -reputation

Reputation is visible to all players on the Player View and used by AI agents when deciding who to trade with. High-reputation players get better contract offers from AI corporations.

---

## 16. Alliances & Trade Blocs

Players can form **alliances** (2–5 players):

- Alliance members get **50% reduced trade fees** when trading with each other
- **Shared market intelligence**: alliance members can see each other's inventory levels and production
- **Alliance chat channel**
- **Joint credit**: alliance members can co-sign loans, pooling credit scores
- **Shared trade routes**: reduced transport costs between alliance members' locations

LLM agents can join alliances too — creating human-AI coalitions. The alliance system is fully accessible via the API.

---

## 17. Seasonal Cycles

A meta-cycle (e.g., 30 minutes = 1 "season") that shifts the economy:

| Season | Effect |
|--------|--------|
| **Spring** | Agricultural production +30%, raw material prices low |
| **Summer** | Consumer demand +20%, consumer good prices high |
| **Autumn** | Stable — no modifiers |
| **Winter** | Operating costs +25%, production efficiency -10% |

Seasons are predictable (visible calendar in UI), allowing players to plan ahead — stockpile in spring, sell in summer, cut costs in winter.

---

## 18. Bankruptcy & Fresh Start

If a player's net worth goes below -$5,000 (negative from unpaid loans), they can declare **bankruptcy**:

- All debts are forgiven
- All facilities are liquidated
- Land is seized and returned to the market
- Inventory is wiped
- Player restarts with $500 (half the normal starting amount)
- Credit score resets to 100 (very low — hard to get loans again)
- A "bankruptcy" marker appears on their profile for 30 minutes

This provides a safety net while making bankruptcy genuinely painful. It also protects lenders from being owed money forever by inactive players.

---

## 19. LLM AI Actors via MCP

### 19.1 Design Philosophy

AI actors are **first-class citizens**. They are not NPCs with special rules — they are players that happen to be driven by an LLM instead of a human. They use the exact same API, follow the exact same rules, and appear identically in the UI (with an optional badge).

### 19.2 MCP Server Architecture

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

### 19.3 MCP Tools (1:1 with Player Capabilities)

Every action a human player can take in the UI maps to an MCP tool:

**Account & State:**
- `get_game_state` — Full state snapshot (inventory, money, facilities, land, offers, loans)
- `get_market_prices` — Current prices for all resources
- `get_leaderboard` — Top players by net worth
- `get_financial_report` — Income statement and expense breakdown
- `get_player_info` — View another player's public info

**Land & Location:**
- `buy_land` — Purchase a land plot at a location
- `sell_land` — Sell owned land back to the market
- `get_land_prices` — Current land prices by region
- `get_location_info` — View details of a map location

**Production:**
- `build_facility` — Start constructing a new facility on owned land
- `idle_facility` — Pause a facility
- `activate_facility` — Resume a paused facility
- `downsize_facility` — Permanently sell a facility
- `set_production_rate` — Set facility to full or half capacity

**Research:**
- `start_research` — Begin researching a tech node (auto-queues prerequisites)
- `set_research_budget` — Allocate spending per tick to a research branch
- `get_research_tree` — View full tech tree for a resource
- `get_research_status` — View all active research progress

**Market:**
- `post_sell_offer` — List resources for sale
- `post_buy_offer` — Post a buy order
- `cancel_offer` — Cancel own offer
- `search_offers` — Browse the orderbook for a resource
- `get_price_history` — Historical prices for a resource

**Shops:**
- `stock_shop` — Send consumer goods to a shop location
- `set_shop_price` — Set the selling price for a shop
- `get_shop_sales` — View sales data for a shop

**Contracts:**
- `propose_contract` — Offer a supply agreement to another player
- `accept_contract` — Accept a proposed contract
- `get_contracts` — View active and proposed contracts

**Loans:**
- `offer_loan` — Post a loan offer for other players
- `accept_loan` — Accept an available loan
- `repay_loan` — Make a repayment
- `search_loans` — Browse available loans
- `get_my_loans` — View active loans

**Social:**
- `send_message` — Send a chat message to a player
- `create_alliance` — Form a new alliance
- `invite_to_alliance` — Invite a player to your alliance
- `get_alliance_info` — View alliance details

**MCP Resources (read-only context):**
- `game://rules` — Complete game rules and formulas
- `game://recipes` — All crafting recipes
- `game://map/regions` — World map region data
- `game://my/state` — Current player state
- `game://my/land` — Player's owned land
- `game://market/prices` — Live market prices
- `game://market/history/{resource}` — Price history
- `game://leaderboard` — Current rankings
- `game://my/finances` — Financial breakdown
- `game://my/research` — Research progress

### 19.4 System AI Corporations

The server runs a configurable number of **built-in AI corporations** to ensure the economy functions even with few human players:

| AI Corporation | Strategy Profile | Focus |
|----------------|-----------------|-------|
| **AgriCorp** | Conservative, high-volume commodity trader | Agriculture, food chain |
| **TechVentures** | Aggressive R&D, targets high-value consumer goods | Electronics, tech chain |
| **GoldBank** | Minimal production, focuses on lending and market-making | Finance, precious metals |
| **IronWorks** | Mid-chain specialist, sells intermediates | Steel, industrial goods |
| **GlobalLogistics** | Buys cheap in one region, sells expensive in another | Arbitrage, transport |
| **LuxuryCraft** | Premium consumer goods, high-margin low-volume | Jewelry, cars |

AI corporations:
- Start with the same $1,000 as human players
- Make decisions every 5–10 seconds (configurable thinking interval)
- Have distinct but non-cheating strategies (they can only see what any player can see)
- Are clearly labeled in the UI with a robot badge
- Own land and shops on the map like any player
- Can be enabled/disabled by the server admin

### 19.5 User-Controlled AI Agents

Players can connect their own LLM agents to play on their behalf:

1. Player generates an **API key** from the settings menu
2. API key authenticates as that player's account via the MCP server
3. The player's own LLM agent (Claude, GPT, etc.) connects and acts as the player
4. While an AI agent is connected, the player can still use the UI — both can act simultaneously
5. Players are responsible for their agent's actions (including loan defaults)

---

## 20. API Design for Full Parity

### 20.1 Unified REST API

All game state and actions are available through a single REST API. The web UI, MCP server, and any custom client all use the same endpoints:

```
GET  /api/state                        -> Full player state
GET  /api/market/{resource}            -> Orderbook for a resource
GET  /api/market/prices                -> Current prices for all resources
GET  /api/market/history/{resource}    -> Price history
GET  /api/leaderboard                  -> Top players
GET  /api/finances                     -> Player's income statement
GET  /api/loans                        -> Available loan offers
GET  /api/loans/mine                   -> Player's active loans
GET  /api/map/regions                  -> Map region data and land prices
GET  /api/map/location/{id}            -> Location details
GET  /api/player/{id}                  -> Player view (public info)
GET  /api/research/{resource}          -> Tech tree for a resource
GET  /api/research/active              -> Currently researching
GET  /api/contracts                    -> Active and proposed contracts
GET  /api/alliance                     -> Alliance info
GET  /api/config                       -> Global game configuration values

POST /api/land/buy                     { regionId, landType }
POST /api/land/sell                    { landId }

POST /api/production/build             { resource, landId }
POST /api/production/idle              { resource, facilityId }
POST /api/production/activate          { resource, facilityId }
POST /api/production/downsize          { resource, facilityId }
POST /api/production/rate              { resource, facilityId, rate }

POST /api/research/start               { resource, techNode }
POST /api/research/budget              { resource, techNode, amountPerTick }

POST /api/market/sell                  { resource, price, quantity }
POST /api/market/buy                   { resource, price, quantity }
DELETE /api/market/{offerId}

POST /api/shop/stock                   { shopId, resource, quantity }
POST /api/shop/price                   { shopId, resource, price }
GET  /api/shop/{shopId}/sales          -> Shop sales data

POST /api/loans/offer                  { amount, rate, term, minScore }
POST /api/loans/accept                 { loanId }
POST /api/loans/repay                  { loanId, amount }
DELETE /api/loans/{loanId}

POST /api/contracts/propose            { targetPlayer, resource, qty, price, duration }
POST /api/contracts/accept             { contractId }
POST /api/contracts/cancel             { contractId }

POST /api/alliance/create              { name }
POST /api/alliance/invite              { playerId }
POST /api/alliance/join                { allianceId }
POST /api/alliance/leave               {}

POST /api/chat/send                    { targetPlayerId, message }
GET  /api/chat/messages                -> Recent messages
```

### 20.2 Parity Guarantee

The UI makes **zero** direct database calls or server-side computations outside the API. Every button click in the browser is a REST call. This guarantees that any MCP agent or custom client has identical capability.

---

## 21. UI Tab Structure

```
+------------------------------------------------------------------+
|  Trade Empire     $14,230    NW: $52,800    Paul    [Settings]    |
+----------+-------------------------------------------------------+
|          |  [Map] [Production] [Research] [Market] [Finance]      |
|          |  [Loans] [Contracts] [Alliance]                        |
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
|----------|                                                        |
| Events   |                                                        |
| Chat     |                                                        |
| Settings |                                                        |
+----------+-------------------------------------------------------+
```

**Tabs:**

1. **Map** — Interactive globe, buy/sell land, view locations, trade routes, demand heatmaps
2. **Production** — Per-resource tab showing all land/facilities producing it, capacity vs. actual output, production/selling/price graphs. Global tab showing all factories.
3. **Research** — Per-resource tab showing full tech tree with progress. Global tab showing all active research and budget allocation.
4. **Market** — Orderbook, price charts (BUY/SELL), post/cancel offers, trade history
5. **Finance** — Income statement, expense breakdown, net worth, trend charts with category filtering, revenue/spending graphs
6. **Loans** — Central Bank loans, player loan marketplace, active loans, credit score, inflation rate
7. **Contracts** — Propose, accept, view active supply agreements
8. **Alliance** — Create/join alliances, shared intelligence, alliance chat

---

## 22. Global Configuration

All game balance values are defined in a single server-side configuration file, making tuning easy without code changes. Base prices for resources and buildings should eventually be informed by real-world market data (web-searched) for semi-realistic economic simulation.

### 22.1 Tuning Parameters

| Parameter | Default | Effect |
|-----------|---------|--------|
| Starting money | $1,000 | Early game speed |
| Land base prices | $50–$5,000 | Expansion cost by region |
| Facility build costs | $100–$2,000 | Expansion rate |
| Operating costs | $2–$30/tick | Profit margin pressure |
| Research speed base cost | $10/tick | R&D pacing |
| Research logarithmic base | ratio=2000 | Diminishing returns curve |
| Market fee | 2% | Trade friction |
| Luxury tax | 3% | Consumer good margin reduction |
| Transport base rate | $0.01/km/unit | Logistics cost |
| Decay rates | 0.2–2% per 60 ticks | Hoarding penalty |
| Central Bank base rate | 0.01% | Credit cost |
| Inflation target | 2% | Central Bank policy |
| Consumer demand cycle periods | 5–45 min | Price volatility |
| Perlin noise octaves | 6 | Demand curve complexity |
| Event frequency | 1 per 10 min avg | Disruption level |
| Storage limits | 500–5,000 | Trade pressure |
| Season duration | 30 minutes | Economic cycle length |
| Starting credit score | 500 | Loan accessibility |
| Bankruptcy threshold | -$5,000 | Safety net trigger |
| Alliance max size | 5 | Coalition limits |
| Contract breach penalty | 10% of contract value | Agreement enforcement |

---

## 23. Implementation Priority

| Priority | Feature | Complexity | Dependencies |
|----------|---------|-----------|-------------|
| **P0** | Global config system | Low | None |
| **P0** | World map & land system | High | None |
| **P0** | Facility build delay & operating costs | Medium | Land system |
| **P0** | Downsize / idle / reactivate facilities | Medium | Facilities |
| **P0** | Research tree (unlock + upgrades) | High | None |
| **P0** | Finance tab (tracking + display) | Medium | Operating costs |
| **P1** | Shops & consumer good selling | High | Land, facilities |
| **P1** | Dynamic consumer good pricing (Perlin noise) | Medium | Shops |
| **P1** | Transport & logistics costs | Medium | Land, map |
| **P1** | Central Bank loan system | High | Credit score, finance |
| **P1** | Player-to-player loans | High | Central Bank |
| **P1** | REST API redesign (parity) | High | All game systems |
| **P1** | Revenue/spending graphs with filtering | Medium | Finance tab |
| **P2** | MCP server | Medium | REST API |
| **P2** | System AI corporations | High | MCP server |
| **P2** | User-controlled AI agents (API keys) | Medium | REST API |
| **P2** | Contracts & supply agreements | Medium | Market, loans |
| **P2** | Market events & economic shocks | Low | Dynamic pricing |
| **P2** | Trade fees & taxation | Low | Market |
| **P2** | Inflation & Central Bank rate adjustment | Medium | Loans, finance |
| **P3** | Resource decay & spoilage | Low | Inventory system |
| **P3** | Storage limits & warehouses | Low | Inventory, land |
| **P3** | Location View & Player View | Medium | Map, social |
| **P3** | Alliances & trade blocs | Medium | Social systems |
| **P3** | Seasonal cycles | Low | Dynamic pricing |
| **P3** | Reputation system | Medium | Contracts, loans |
| **P3** | Bankruptcy & fresh start | Low | Loans, net worth |

---

## 24. Session Flow Example

**New player "Alice" joins:**

1. Signs up, sees the world map. Browses regions for affordable farm land.
2. Gets $1,000 starting cash. Buys a Farm plot in rural France ($200) and a Factory plot nearby ($300).
3. Opens Research tab — starts researching "Unlock Wheat Production" (fast, ~2 min) and "Unlock Iron Production" (~3 min).
4. While waiting, explores the map, checks market prices, looks at other players' locations.
5. Wheat unlocks! Builds 2 Wheat facilities on her farm. Iron unlocks shortly after — builds an Iron facility in her factory.
6. Wheat and Iron start accumulating every tick. Operating costs: $6/tick.
7. Needs Petrol for Plastic — hasn't researched it, and has no Energy land. Checks the market.
8. Sees "AgriCorp_AI" selling Petrol at $1.80/unit — buys 50 units. Transport cost: $12 (AgriCorp is in the Middle East).
9. Posts sell offer: "Selling Wheat at $2.00, keep 100 in reserve."
10. "TechVentures_AI" buys her Wheat. She earns $400.
11. Takes a $2,000 loan from the Central Bank at 0.01%/tick to build more facilities and buy Shop land.
12. Starts researching "Unlock Steel Production" (prerequisite: Iron already unlocked). Builds Steel facility when ready.
13. Steel facility finishes (2-min build time). Iron is consumed, Steel accumulates.
14. Opens Finance tab: Revenue $1,200 (market sales), Expenses $800 (operating + transport + loan interest). Net profit: $400.
15. Over 20 minutes, builds up her chain. Researches Circuit, then Phone.
16. Buys Shop land in Paris (expensive but high demand). Opens a phone store.
17. Sets phone price to market rate. Sales trickle in — shared with 2 other phone shops nearby.
18. Checks graphs: revenue trending up, phone demand multiplier is rising (good timing!).
19. "Player_Bob" messages via chat: "Want to form an alliance? I have cheap Petrol in Saudi Arabia."
20. Alliance formed — trade fees halved between them. Transport still costs money, but it's worth it.

**Meanwhile, "GoldBank_AI":**
1. Buys land near gold deposits. Researches Gold and Petrol production.
2. Produces Gold and Petrol, sells raw Gold at market price.
3. Uses profits to offer loans at 0.008%/tick (undercutting the Central Bank).
4. Alice accepts a loan from GoldBank. GoldBank earns passive interest.
5. Never builds consumer good facilities or shops — it's a pure financier.

This emergent specialization — Alice as a manufacturer, GoldBank as a financier, Bob as a fuel supplier — arises naturally from the game mechanics and geography.
