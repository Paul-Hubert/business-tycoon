# Trade Empire — Feature List

Each feature is rated on:

- **AI Complexity** — How difficult it is to implement with AI code generation, factoring in well-established libraries/packages. Scale: `Trivial` | `Low` | `Medium` | `High` | `Very High`
- **Gameplay Value** — How much the feature contributes to the core experience (1–10 with explanation)

Features are grouped by system. Dependencies are noted where relevant.

---

## 1. Foundation & Infrastructure

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 1.1 | **Global Config System** — Single server-side file with all tuning parameters (starting cash, costs, rates, timers). Hot-reloadable. | Trivial | Java Properties / JSON config, or Spring-style `@Value` | **3/10** | Invisible to players but critical for development speed. Every other feature depends on tunable constants. Zero direct gameplay but massive indirect value. |
| 1.2 | **Database Schema Redesign** — New tables for land, facilities (with state/location), research progress, shops, contracts, alliances, reputation, chat. Migration from current schema. | Medium | SQL DDL, JDBC. Straightforward but large surface area (~15 new tables). | **2/10** | Pure infrastructure. No player-facing value on its own, but nothing else works without it. |
| 1.3 | **Unified REST API** — Every game action exposed as a REST endpoint. UI makes zero direct DB calls. Parity guarantee for MCP/bots. | High | Jakarta Servlet, JSON (org.json or Gson). Large surface (~50 endpoints) but each is simple CRUD. Well-trodden pattern. | **8/10** | The backbone of the game. Enables AI parity (core differentiator), custom clients, and clean separation. Without this, MCP and bot play are impossible. |
| 1.4 | **Simulation Tick Engine Overhaul** — Reliable per-tick processing: operating costs, decay, research progress, loan interest, production, transport. Ordered and transactional. | Medium | `ScheduledExecutorService` (already in codebase). Needs careful ordering and error handling. | **7/10** | The heartbeat of the game. Every economic mechanic depends on ticks firing correctly. Broken ticks = broken economy. |

---

## 2. World Map & Geography

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 2.1 | **Interactive Globe** — 3D or 2D world map. Rotate, zoom, click regions. Shows land ownership, facilities, trade routes. | High | **CesiumJS** (3D globe, free for non-commercial), **Leaflet** (2D map, very mature), or **Globe.gl** (Three.js wrapper, lightweight 3D). Leaflet is simplest; CesiumJS is most impressive. | **9/10** | The visual centerpiece and primary navigation tool. Transforms the game from a spreadsheet into a living world. Geography-driven strategy only works if players can see and interact with the map. |
| 2.2 | **Land System (Buy / Sell / Types)** — Land plots with types (Farm/Factory/Shop), max production capacity, ownership tracking. Purchase and sale through API. | Medium | DB tables + REST endpoints + map click handlers. No exotic packages needed. | **9/10** | Foundational mechanic. Every production and sales action requires land. Creates the spatial dimension that makes geography matter. Without land, there's no map gameplay. |
| 2.3 | **Dynamic Land Pricing** — Price = Base x Offer Multiplier x Saturation Penalty. Prices rise in popular areas, fall in empty ones. | Low | Pure math formula applied per region. Offer multiplier can reuse the Perlin noise system (Feature 4.3). | **6/10** | Adds strategic depth to land purchases — do you pay premium for a hub or go cheap and remote? Without it, everyone clusters in one spot. |
| 2.4 | **Location View** — Click any owned plot to see: owner, land type, facilities, production, capacity used. Link to Player View. | Low | Frontend panel/modal. Data already available from land + facility APIs. | **5/10** | Essential for map usability. Players need to inspect what's happening at locations. Low complexity, solid quality-of-life. |
| 2.5 | **Player View** — Public profile: annual revenue, production types, current market offers, chat button. | Low | Frontend page + a few API calls. | **6/10** | Social glue. Knowing what other players produce drives trade decisions. The chat button enables deal-making. Key for multiplayer feel. |
| 2.6 | **Trade Route Visualization** — Animated lines on the map showing goods moving between locations. Cost overlays. | Medium | Leaflet polylines with animation plugins, or CesiumJS arcs. **Leaflet.AntPath** for animated routes. | **4/10** | Visual polish that makes the economy feel alive. Not mechanically necessary but significantly improves immersion and helps players understand logistics costs at a glance. |
| 2.7 | **Regional Demand Heatmap Overlay** — Toggle overlay showing consumer demand intensity by region. | Low | Leaflet heatmap plugin (**Leaflet.heat**) or CesiumJS entity coloring. Data from demand model. | **5/10** | Helps players decide where to place shops. Reduces guesswork and rewards strategic thinking. Simple to implement, solid information value. |

---

## 3. Production System

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 3.1 | **Expanded Resource & Recipe System** — 12 raw, 11 intermediate, 9 consumer goods. Recipe graph with multi-input crafting. | Medium | Data modeling (DB tables or config file for recipes). Recursive input resolution for crafting. No external packages. | **9/10** | The core content of the game. More resources = deeper supply chains = more trade opportunities = more interesting decisions. A shallow resource list makes the game boring fast. |
| 3.2 | **Facility Management (Build / Idle / Activate / Downsize)** — State machine per facility: Active, Idle, Under Construction. Transitions with costs and rules. | Medium | State pattern or enum-based FSM. DB column for state. Timer for construction. Standard Java. | **9/10** | Transforms production from "click and forget" into active management. Idling during crashes, downsizing dead weight, timing construction — these are the moment-to-moment decisions that make the game engaging.
| 3.3 | **Build Delay & Construction Queue** — Facilities take time to build (30s to 10min by tier). Progress bars. Multiple concurrent builds. | Low | `System.currentTimeMillis()` + completion timestamp per facility. Tick engine checks completion. Frontend progress bars are trivial with CSS. | **7/10** | Creates anticipation, planning, and commitment. Instant builds remove strategic weight from investment decisions. The delay forces players to plan ahead and creates windows for competitors. |
| 3.4 | **Operating Costs Per Tick** — Every active facility drains cash automatically. Auto-idle on bankruptcy. Notifications. | Low | Per-tick deduction loop over active facilities. Simple multiplication. | **8/10** | The single most important economic pressure mechanic. Without operating costs, players hoard facilities forever. With them, every facility must justify its existence through revenue. Creates real profit margins. |
| 3.5 | **Facilities Bound to Land (Capacity Limits)** — Each land plot has max facility slots. Facilities count against the cap. | Trivial | Foreign key + count check on facility creation. | **5/10** | Prevents infinite stacking on one plot. Forces geographic expansion. Simple but necessary for the land system to matter. |
| 3.6 | **Production Capacity vs. Actual Output Display** — Show theoretical max output alongside real output (limited by input shortages, rate settings, research). | Low | Calculate both values server-side, return in API. Frontend side-by-side display. | **6/10** | Critical for bottleneck identification. Players need to know *why* they're under-producing to make informed decisions about what to buy or research. |

---

## 4. Shops & Consumer Sales

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 4.1 | **Shop System** — Shop land type. Stock with consumer goods (transported from factories). Set price or match market rate. NPC customers buy. | Medium | New entity (Shop) with inventory, pricing, and sales logic. REST endpoints for stocking/pricing. Ties into transport system. | **9/10** | The endgame revenue driver. Without shops, consumer goods have no spatial dimension. Shops create location strategy, competition, and a reason to care about the map for sales — not just production. |
| 4.2 | **NPC Consumer Demand Model** — Regional demand split among nearby shops weighted by price and proximity. Saturation effect. | High | Spatial query (find shops within radius), weighted distribution algorithm. Could use **KD-tree** (via a library) for spatial lookups, but brute force works with <1000 shops. | **8/10** | The core mechanism that makes shop placement matter. Without weighted demand splitting, there's no reason to spread shops out or care about competitors. This creates the "retail strategy" layer of the game. |
| 4.3 | **Dynamic Pricing (Perlin Noise Demand Multiplier)** — 1D Perlin noise with multi-octave, octave params also on Perlin noise. Organic boom/bust cycles. | Medium | **simplex-noise** (JS) or **OpenSimplex2** (Java). Well-documented, single-function call. The "octave params on Perlin" part is novel but just nesting noise calls. | **8/10** | Creates unpredictable but organic economic cycles that feel real. Fixed prices are boring. Random prices feel arbitrary. Perlin noise hits the sweet spot — trends you can read but can't perfectly predict. Enables speculation and timing-based strategy. |
| 4.4 | **Saturation Penalty** — Each unit sold in a demand cycle reduces price for subsequent units. Resets each cycle. | Low | Counter per resource per region per cycle. Simple decrement formula. | **6/10** | Prevents one mega-producer from dumping infinite goods at peak price. Creates fair competition and encourages geographic spread. |
| 4.5 | **Visible Demand Forecast** — Players see current demand multiplier and trend direction (rising/falling) but not exact future price. | Low | Expose current noise value + derivative sign via API. Simple UI indicator. | **7/10** | The information that enables speculation. Without visible trends, price changes feel random. With them, players can time production and shop stocking — rewarding attention and planning. |

---

## 5. Research Tree

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 5.1 | **Research Tree Data Model** — Per-resource tech trees with prerequisites. Nodes: Unlock, Efficiency I-III, Cost Reduction I-II, Input Conservation, Bulk Processing, Advanced Logistics. | Medium | Tree/DAG structure in DB (adjacency list or nested set). Config-driven node definitions. Standard graph traversal for prereq validation. | **10/10** | Replaces sector licenses as the specialization driver. Forces investment choices that define your strategy. "Do I go deep on Steel efficiency or unlock Circuits?" is the kind of decision that makes or breaks an empire. The single highest-value feature for long-term engagement. |
| 5.2 | **Research Spending & Logarithmic Progress** — Players allocate $/tick to research branches. Speed = log(1 + spending/base_cost). Diminishing returns. | Low | `Math.log()` formula per tick. Budget allocation stored in DB. | **7/10** | Prevents whales from instantly unlocking everything. Creates meaningful resource allocation — do you spend $50/tick on one branch or $10/tick across five? The logarithmic curve rewards patience over brute force. |
| 5.3 | **Auto-Research Prerequisites** — Clicking "research Circuit" automatically queues all unresearched prereqs in order. | Medium | Topological sort on the tech tree DAG. Well-known algorithm. | **6/10** | Quality-of-life that prevents frustration. Without it, players must manually discover and queue prereqs — tedious and error-prone. Makes the research system accessible without dumbing it down. |
| 5.4 | **Research UI — Per-Resource Tree View** — Visual tech tree per resource with progress bars, costs, unlock status. Interactive nodes. | High | **D3.js** (tree layout), **vis-network**, or **Cytoscape.js** for graph visualization. Or custom SVG/Canvas with a simpler approach. Lots of frontend work. | **7/10** | The research system is only as good as its UI. A confusing tree kills engagement. A clean, visual tree with clickable nodes and progress bars makes research feel rewarding and strategic. |
| 5.5 | **Research UI — Global Active Tab** — Shows all currently active research across all resources, with spending allocation and progress. | Low | Aggregation query + list UI. Trivial once per-resource data exists. | **5/10** | Dashboard for multi-resource researchers. Needed for players managing 5+ research branches. Without it, players lose track of spending. |

---

## 6. Transport & Logistics

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 6.1 | **Distance-Based Transport Costs** — Cost = Base Rate x Distance (km) x Volume x Weight Modifier. Applied to trades and internal transfers. | Low | **Haversine formula** (5 lines of code) for distance between coordinates. Multiply by config rates. | **8/10** | The mechanic that makes the map meaningful for economics. Without transport costs, location is cosmetic. With them, co-locating production saves money, remote cheap land has a hidden cost, and regional trade clusters emerge naturally. |
| 6.2 | **Transport Surcharge on Market Offers** — Buyer sees per-offer transport cost based on distance to their nearest storage. | Low | Distance calc per offer, shown in UI. Already have the formula from 6.1. | **6/10** | Information that drives purchasing decisions. "This Steel is cheap but 5000km away" creates real trade-offs. Without it, the cheapest offer always wins regardless of geography. |
| 6.3 | **Logistics Summary in Production Tab** — Total transport spending breakdown by route. | Low | Aggregation of transport transactions. Simple table UI. | **3/10** | Nice-to-have analytics. Helps players optimize routes but not critical for gameplay. |

---

## 7. Market & Trading

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 7.1 | **Enhanced Order Matching Engine** — Price-time priority matching. Cheapest sellers to highest bidders. Proper exchange semantics. | Medium | Classic limit order book algorithm. Well-documented pattern. Can reference open-source matching engines for structure. | **9/10** | Fair, predictable markets are essential. The current random matching is exploitable and feels unfair. Price-time priority is the real-world standard and creates a market that rewards good pricing. |
| 7.2 | **Sell Offers with Reserve** — "Keep at least N in stock, sell the rest at price P." Auto-replenishes. | Low | Conditional check on offer fulfillment: `available = stock - reserve`. | **6/10** | Prevents players from accidentally selling everything. Enables "set and forget" selling, which is critical for players who can't watch the market every second — and essential for AI agents to operate smoothly. |
| 7.3 | **Buy Offers with Target** — "Buy up to (N - current stock) at up to price P." Auto-adjusts quantity. | Low | Same pattern as 7.2, inverse direction. | **6/10** | Same logic — enables automated purchasing with guardrails. Key for supply chain management and AI agent behavior. |
| 7.4 | **Price History Charts** — Line charts for every resource showing historical prices. BUY and SELL lines. Filterable time ranges. | Medium | **Chart.js** (most popular, excellent docs), **ApexCharts**, or **Lightweight Charts** (TradingView open-source). All are well-established. | **8/10** | Players cannot make informed trading decisions without price history. Charts enable speculation, trend reading, and timing — the core of market gameplay. Without them, trading is blind gambling. |
| 7.5 | **Trade History Log** — Record of all executed trades with timestamp, parties, price, quantity, transport cost. | Low | Transaction log table in DB. Paginated API endpoint. | **4/10** | Audit trail and analytical tool. Useful for debugging strategies but not a core gameplay driver. |

---

## 8. Finance & Analytics

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 8.1 | **Income Statement (Revenue & Expense Tracking)** — Categorized transaction logging: sales by product, operating costs by facility, purchases, transport, research, loans, fees. | Medium | Transaction log with category enum. Aggregation queries per period. Decent DB design needed for performance. | **8/10** | Players need to know where their money goes. Without financial tracking, the game feels opaque and frustrating. A clear income statement turns "I'm losing money" into "my Car assembly costs more than my Car sales" — actionable insight. |
| 8.2 | **Net Worth Calculation** — Cash + inventory value + facility value + land value + loans receivable - loans payable. Live updating. | Low | Sum formula across multiple tables. Cached and recalculated per tick. | **6/10** | The primary score/ranking metric. Players need a single number that represents their progress. Also used for loan limits and leaderboard. |
| 8.3 | **Revenue/Spending Graphs with Category Filtering** — Interactive time-series charts. Toggle individual categories on/off. Time range selection. | Medium | **Chart.js** with dataset toggling (built-in feature). Time-series data from transaction log. | **8/10** | The power-user analytics tool. "Show me only Operating Costs and Car Sales over the last hour" lets players identify trends and optimize. High value for engaged players and essential for AI agents analyzing their own performance. |
| 8.4 | **Period Comparison (vs. Previous)** — Each category shows % change vs. previous period. Up/down arrows. | Low | Compare current and previous period aggregates. Simple math. | **4/10** | Quick visual indicator of whether things are improving or declining. Nice-to-have that adds polish to the finance tab. |
| 8.5 | **CSV Export** — Download financial data as CSV. | Trivial | String concatenation with commas. Serve as `text/csv`. | **2/10** | Niche feature for data enthusiasts. Very low effort, very low priority, but someone will appreciate it. |

---

## 9. Loans & Credit

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 9.1 | **Credit Score System** — Hidden score (0–1000) based on repayment history, net worth, account age, cash flow, defaults. | Medium | Scoring formula evaluated per-event (loan repaid, payment missed, etc.). Stored per player. | **7/10** | Adds consequence to financial behavior. Defaults aren't just "lose some money" — they permanently damage your borrowing power. Creates reputation that matters for mechanics, not just flavor. |
| 9.2 | **Central Bank Loan System** — Automated lender with tiered rates based on credit score. Fixed-term and interest-only options. Early repayment. Auto-default on insolvency. | High | Loan lifecycle state machine (offered → active → repaying → paid/defaulted). Per-tick interest compounding. Escrow for auto-deduction. | **8/10** | The primary financing tool. Without loans, players can only grow as fast as their revenue allows. Loans enable ambitious expansion, risk-taking, and recovery — all of which create more interesting gameplay. |
| 9.3 | **Player-to-Player Loans** — Players post loan offers with amount, rate, term, min credit score. Escrow, lifecycle, default handling. | High | Same loan lifecycle as 9.2, but with player as lender. Loan marketplace (list/accept). Escrow deducts from lender on posting. | **8/10** | Creates a banking metagame. LLM agents can become financiers. Players can fund allies' expansion. Predatory lending creates drama. This is the feature that makes the economy feel like a real economy, not just a production game. |
| 9.4 | **Dynamic Base Rate & Inflation Tracking** — Monitor money supply and velocity. Central Bank adjusts rates. Announcements to all players. | Medium | Track total money in economy per tick (sum of all cash). Rate adjustment formula based on money supply growth rate. System notification on rate change. | **6/10** | Adds a macroeconomic layer. Players must consider not just their own finances but the health of the whole economy. Rate hikes during inflation create collective strategic pressure. |
| 9.5 | **Loan Marketplace UI** — Browse available loans, filter by rate/amount/term. Accept loans. View active loans as borrower and lender. | Medium | Paginated list + filter UI. Standard CRUD. | **5/10** | Necessary for the loan system to be usable. No mechanical value on its own — it's the interface for 9.2 and 9.3. |

---

## 10. Contracts & Supply Agreements

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 10.1 | **Contract System** — Propose binding supply agreements: resource, quantity/cycle, price (fixed or market-linked), duration, penalty for breach. | High | Contract state machine (proposed → accepted → active → completed/breached). Per-cycle auto-execution: check stock, transfer goods, deduct payment. Breach detection and penalty application. | **7/10** | Supply chain stability. Without contracts, every trade is one-off and unreliable. Contracts let players build dependable supply chains, which enables longer production chains and more ambitious strategies. Key for AI agents who need reliable inputs. |
| 10.2 | **Contract Negotiation via API** — AI agents can propose, counter-offer, and accept contracts programmatically. | Low | Already covered by REST API if contract endpoints exist. No extra work beyond 10.1. | **6/10** | Enables AI-to-AI deal-making, which is where the "AI as first-class citizen" vision comes alive. Watching AIs negotiate contracts is compelling spectator content. |

---

## 11. Market Events & Economic Shocks

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 11.1 | **Random Event System** — Timer-based random events (Commodity Boom, Supply Shortage, Market Crash, etc.). 30-second warning. Configurable frequency. | Medium | Scheduled event generator with weighted random selection. Event effects applied as temporary modifiers to pricing/production formulas. Notification system for warnings. | **8/10** | Prevents the economy from reaching boring equilibrium. Events force adaptation, create winners and losers, and generate memorable moments. "I made $50K during the Gold Rush because I had 3 gold mines" — that's a story. Stories drive engagement. |
| 11.2 | **Event Notification & Countdown** — System-wide banner announcing upcoming event with 30-second countdown. | Low | WebSocket broadcast or polling. Simple UI banner with countdown timer. | **5/10** | The 30-second warning is what makes events strategic rather than random punishment. Players who react fastest profit. Without the warning, events would just feel unfair. |

---

## 12. Fees, Taxes & Money Sinks

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 12.1 | **Market Transaction Fee (2%)** — Percentage deducted from both buyer and seller on every trade. Revenue goes to Central Bank reserve. | Trivial | Two-line modification to trade execution. | **5/10** | Essential money sink. Without it, the economy inflates endlessly. Small enough to not discourage trading, large enough to matter at scale. Makes contracts (no fee) attractive for high-volume players. |
| 12.2 | **Luxury Tax (3% on Consumer Sales)** — Additional tax on shop revenue. | Trivial | One-line deduction on shop sale calculation. | **4/10** | Targets the highest-revenue activity. Ensures consumer good production isn't disproportionately profitable. Minor but important for balance. |

---

## 13. Resource Decay & Spoilage

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 13.1 | **Perishable Resource Decay** — Wheat, Cotton, Rubber, Petrol, Bread, Canned Food lose percentage of stock per 60 ticks. Non-perishable resources unaffected. | Low | Per-tick loop: `stock *= (1 - decayRate)` for flagged resources. Config-driven rates. | **6/10** | Prevents infinite hoarding of raw materials. Creates urgency to sell or process. Without decay, players can stockpile thousands of Wheat with zero risk, removing trade pressure. |

---

## 14. Storage & Warehouses

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 14.1 | **Storage Limits Per Location** — Max capacity per resource tier per location. Overflow is lost. | Low | Cap check on production/purchase. `if (stock + incoming > capacity) overflow = excess`. | **6/10** | Forces players to sell, process, or expand warehouses. Prevents passive accumulation. Combined with decay, creates constant economic activity. |
| 14.2 | **Warehouse Upgrades** — Pay to increase storage capacity per location. | Trivial | Increment capacity value in DB. Deduct cost. | **3/10** | Simple money sink and progression system. Not exciting but necessary for the storage system to not feel punishing. |

---

## 15. Reputation System

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 15.1 | **Corporate Reputation Score** — Public score based on contract fulfillment, fair lending, defaults, pricing behavior. Visible on Player View. Used by AI agents for trust decisions. | Medium | Event-driven score updates (similar to credit score but public). Behavior classification ("discount supplier", "premium supplier"). | **5/10** | Social signaling in a multiplayer economy. High reputation means more trade offers and better contracts. Creates incentive for good behavior beyond pure profit motive. More impactful in a mature server with many players. |

---

## 16. Alliances & Trade Blocs

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 16.1 | **Alliance System** — Create/join alliances (2–5 players). 50% reduced trade fees between members. Shared inventory visibility. Co-signed loans. | High | Group management (create/invite/join/leave). Fee discount logic. Permission system for shared data. Alliance-scoped queries. | **7/10** | The premier social feature. Alliances create teams, rivalry, and coordination. Reduced fees incentivize grouping without forcing it. Shared intelligence makes allies genuinely useful. Human-AI alliances are a unique selling point. |
| 16.2 | **Alliance Chat Channel** — Dedicated chat for alliance members. | Low | Scoped chat room. WebSocket or polling. | **4/10** | Social glue for alliances. Without communication, alliances are just a fee discount. Chat enables coordination and makes the alliance feel real. |

---

## 17. Seasonal Cycles

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 17.1 | **Seasonal Cycle System** — 30-minute seasons (Spring/Summer/Autumn/Winter) with global modifiers to production, demand, and costs. Visible calendar. | Low | Timer-based season rotation. Apply modifier multipliers to existing formulas. UI calendar widget. | **6/10** | Adds a predictable macro-rhythm to the economy. Players can plan ahead: stockpile in Spring (cheap production), sell in Summer (high demand), cut costs in Winter. Rewards planning over reaction. Predictability is key — seasons should feel like weather, not random events. |

---

## 18. Bankruptcy

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 18.1 | **Bankruptcy & Fresh Start** — Declare bankruptcy at -$5,000 net worth. Debts forgiven, all assets liquidated, restart with $500 and credit score 100. 30-minute profile marker. | Medium | Cascading deletion/reset of player assets. Loan write-off for lenders. Profile flag with timer. Edge cases around active contracts and alliance membership. | **5/10** | Safety net that prevents permanent death spirals. Without it, a bad loan or market crash can permanently ruin a player's experience. The harsh penalties (low restart cash, destroyed credit) prevent abuse. Also protects lenders from zombie debt. |

---

## 19. AI & MCP Integration

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 19.1 | **MCP Server** — Model Context Protocol server wrapping the REST API. Exposes all game actions as MCP tools and game state as MCP resources. | High | **MCP SDK** (TypeScript or Python — official SDKs exist). Thin wrapper that translates MCP tool calls to REST API calls. ~30 tool definitions + ~10 resource definitions. | **10/10** | The defining feature of the game. This is what makes "AI as first-class citizen" real. Without MCP, AI agents need custom integration. With MCP, any LLM that supports MCP can play immediately. This is the unique selling point that no other game offers. |
| 19.2 | **System AI Corporations** — Built-in LLM-driven bots (AgriCorp, TechVentures, GoldBank, etc.) with distinct strategy profiles. Same capabilities as human players. | Very High | LLM API integration (Anthropic/OpenAI). Prompt engineering for each corporation's strategy. Decision loop every 5-10 seconds. State evaluation, strategy execution. Error handling for LLM failures. Rate limiting. Cost management. | **9/10** | Ensures the economy functions with few humans. AI corporations create trade partners, market liquidity, and competition from day one. Different strategies create a diverse ecosystem. The spectacle of watching AI economies emerge is compelling content. |
| 19.3 | **User-Controlled AI Agents (API Keys)** — Players generate API keys. Key authenticates as their account via MCP. Player and AI can act simultaneously. | Medium | API key generation (UUID + hashing). Auth middleware that accepts key or session. Concurrent access handling (optimistic locking). | **8/10** | The metagame within the metagame. Players who write effective AI strategies gain an edge. Creates a programming challenge layer on top of the economic game. Also enables AFK play — your agent runs while you sleep. |

---

## 20. Social & Communication

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 20.1 | **Player-to-Player Chat** — Direct messaging between players. Accessible from Player View and chat tab. | Medium | **WebSocket** (Jakarta WebSocket API or a lightweight library). Message storage in DB. Basic chat UI. Alternatively, simple polling over REST. | **6/10** | Trade negotiation, alliance coordination, social interaction. Without chat, multiplayer feels like playing against bots (even when it isn't). Chat is what turns "other players" into "people." |
| 20.2 | **System Notifications** — Server-wide announcements for events, rate changes, seasonal shifts. Per-player notifications for auto-idle, loan due, contract breach. | Low | Notification table + polling endpoint. UI notification dropdown/toast. | **5/10** | Players need to know when important things happen. Missing a loan payment because you weren't watching is frustrating. Notifications are the minimum viable information system. |

---

## 21. UI Architecture

| # | Feature | AI Complexity | Packages / Approach | Gameplay Value | Explanation |
|---|---------|--------------|---------------------|----------------|-------------|
| 21.1 | **Tab-Based UI Redesign** — Main tabs: Map, Production, Research, Market, Finance, Loans, Contracts, Alliance. Persistent header with cash/net worth. | High | Major frontend restructure. Tab routing (vanilla JS history API or lightweight router). Consistent layout framework. Responsive design. | **8/10** | Usability determines whether players stay. A confusing UI kills engagement regardless of how good the mechanics are. Clean tab organization makes complexity manageable. |
| 21.2 | **Sidebar Resource Navigation** — Collapsible sidebar listing all resources by tier. Click to filter current tab to that resource. | Low | Sidebar component with scroll. Click handlers that filter content. CSS collapse animation. | **5/10** | Quick navigation across 32 resources. Without it, finding specific resource info requires too many clicks. Small effort, daily-use feature. |
| 21.3 | **Production Tab (Per-Resource + Global)** — Per-resource: all land/facilities producing it, capacity vs. actual, production/selling/price graphs. Global: all factories in one view. | High | Complex data aggregation across land, facilities, and market data. Multiple Chart.js instances. Table with filtering and sorting. | **7/10** | The operational command center. Players managing 10+ facilities across multiple locations need a centralized view. Without it, production management becomes a tedious map-clicking exercise. |

---

## Summary: Priority Matrix

Sorted by **Gameplay Value / AI Complexity** ratio (best bang for buck first):

| Rank | Feature | Value | Complexity | Notes |
|------|---------|-------|------------|-------|
| 1 | 3.4 Operating Costs Per Tick | 8 | Low | Instant economic depth, trivial to implement |
| 2 | 6.1 Distance-Based Transport Costs | 8 | Low | Makes the entire map meaningful with 5 lines of math |
| 3 | 12.1 Market Transaction Fee | 5 | Trivial | Two lines of code, prevents runaway inflation |
| 4 | 12.2 Luxury Tax | 4 | Trivial | One line, balances consumer goods |
| 5 | 14.2 Warehouse Upgrades | 3 | Trivial | Simple progression mechanic |
| 6 | 3.5 Facilities Bound to Land | 5 | Trivial | One constraint check, makes land matter |
| 7 | 4.5 Visible Demand Forecast | 7 | Low | Enables speculation with minimal code |
| 8 | 3.3 Build Delay | 7 | Low | Timestamp + check, creates strategic weight |
| 9 | 13.1 Resource Decay | 6 | Low | One formula per tick, prevents hoarding |
| 10 | 14.1 Storage Limits | 6 | Low | Simple cap, forces economic activity |
| 11 | 5.2 Logarithmic Research Spending | 7 | Low | One math formula, prevents pay-to-win |
| 12 | 17.1 Seasonal Cycles | 6 | Low | Timer + multipliers, adds macro-rhythm |
| 13 | 7.2 Sell Offers with Reserve | 6 | Low | Small logic change, big QoL |
| 14 | 7.3 Buy Offers with Target | 6 | Low | Same as above |
| 15 | 11.2 Event Notifications | 5 | Low | Banner + countdown, makes events strategic |
| 16 | 3.6 Capacity vs Actual Display | 6 | Low | Helps players optimize |
| 17 | 6.2 Transport Surcharge on Offers | 6 | Low | Makes geography visible in market |
| 18 | 20.2 System Notifications | 5 | Low | Essential information delivery |
| 19 | 2.4 Location View | 5 | Low | Makes map clickable and useful |
| 20 | 2.7 Demand Heatmap | 5 | Low | Helps shop placement decisions |
| 21 | 5.5 Research Global Tab | 5 | Low | Dashboard for multi-branch research |
| 22 | 1.1 Global Config System | 3 | Trivial | Foundational, speeds up all dev |
| 23 | 5.1 Research Tree Data Model | 10 | Medium | Highest value feature, moderate effort |
| 24 | 3.1 Expanded Resources | 9 | Medium | Core content, config-driven |
| 25 | 3.2 Facility Management | 9 | Medium | Core mechanic, standard state machine |
| 26 | 2.2 Land System | 9 | Medium | Foundational, standard CRUD |
| 27 | 7.1 Order Matching Engine | 9 | Medium | Well-documented algorithm, huge impact |
| 28 | 8.1 Income Statement | 8 | Medium | Transaction logging, essential transparency |
| 29 | 4.3 Dynamic Pricing (Perlin) | 8 | Medium | Library exists, creates organic economy |
| 30 | 11.1 Random Event System | 8 | Medium | Prevents stale equilibrium |
| 31 | 8.3 Revenue/Spending Graphs | 8 | Medium | Chart.js does the heavy lifting |
| 32 | 7.4 Price History Charts | 8 | Medium | Same — Chart.js + data |
| 33 | 1.4 Tick Engine Overhaul | 7 | Medium | Invisible but everything breaks without it |
| 34 | 4.4 Saturation Penalty | 6 | Low | Simple counter, fair competition |
| 35 | 2.5 Player View | 6 | Low | Social feature, drives trade |
| 36 | 2.3 Dynamic Land Pricing | 6 | Low | Adds location strategy |
| 37 | 8.2 Net Worth | 6 | Low | Primary score metric |
| 38 | 9.4 Inflation Tracking | 6 | Medium | Macroeconomic layer |
| 39 | 5.3 Auto-Research Prerequisites | 6 | Medium | QoL for research system |
| 40 | 20.1 Player Chat | 6 | Medium | Social glue |
| 41 | 9.1 Credit Score | 7 | Medium | Consequence for financial behavior |
| 42 | 18.1 Bankruptcy | 5 | Medium | Safety net, edge case heavy |
| 43 | 15.1 Reputation | 5 | Medium | Social signaling, slow burn value |
| 44 | 9.5 Loan Marketplace UI | 5 | Medium | Necessary interface for loan system |
| 45 | 21.2 Sidebar Navigation | 5 | Low | QoL for 32 resources |
| 46 | 4.1 Shop System | 9 | Medium | Endgame revenue, spatial sales |
| 47 | 1.3 Unified REST API | 8 | High | Massive scope, enables everything |
| 48 | 19.1 MCP Server | 10 | High | The unique selling point |
| 49 | 9.2 Central Bank Loans | 8 | High | Core financing, complex lifecycle |
| 50 | 9.3 Player-to-Player Loans | 8 | High | Banking metagame |
| 51 | 4.2 NPC Consumer Demand Model | 8 | High | Makes shops meaningful |
| 52 | 19.3 User AI Agents | 8 | Medium | Metagame, AFK play |
| 53 | 2.1 Interactive Globe | 9 | High | Visual centerpiece, library-dependent |
| 54 | 21.1 Tab-Based UI Redesign | 8 | High | Usability = retention |
| 55 | 10.1 Contract System | 7 | High | Supply chain stability |
| 56 | 16.1 Alliance System | 7 | High | Social + economic grouping |
| 57 | 5.4 Research Tree UI | 7 | High | Makes research system accessible |
| 58 | 21.3 Production Tab | 7 | High | Operational command center |
| 59 | 19.2 System AI Corporations | 9 | Very High | Economy bootstrap, spectacle, most complex feature |
| 60 | 1.2 Database Schema Redesign | 2 | Medium | Pure infra, must be done first |
| 61 | 10.2 Contract Negotiation API | 6 | Low | Free if contracts exist |
| 62 | 16.2 Alliance Chat | 4 | Low | Social glue for alliances |
| 63 | 2.6 Trade Route Visualization | 4 | Medium | Visual polish |
| 64 | 7.5 Trade History Log | 4 | Low | Audit trail |
| 65 | 8.4 Period Comparison | 4 | Low | Polish |
| 66 | 6.3 Logistics Summary | 3 | Low | Analytics nice-to-have |
| 67 | 8.5 CSV Export | 2 | Trivial | Niche |
