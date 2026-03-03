# Trade Empire — Minimal Feature Set

Core features for MVP. This is the focused scope for initial development.

---

## 1. Foundation & Infrastructure

| # | Feature | AI Complexity | Gameplay Value |
|---|---------|--------------|----------------|
| 1.1 | **Global Config System** — Single server-side file with all tuning parameters (starting cash, costs, rates, timers). Hot-reloadable. | Trivial | 3/10 |
| 1.2 | **Database Schema Redesign** — New tables for land, facilities, research progress, shops, and market data. | Medium | 2/10 |
| 1.3 | **Unified REST API** — Every game action exposed as a REST endpoint. UI makes zero direct DB calls. | High | 8/10 |
| 1.4 | **Simulation Tick Engine Overhaul** — Reliable per-tick processing: operating costs, decay, research progress, production. | Medium | 7/10 |

---

## 3. Production System

| # | Feature | AI Complexity | Gameplay Value |
|---|---------|--------------|----------------|
| 3.1 | **Expanded Resource & Recipe System** — 12 raw, 11 intermediate, 9 consumer goods. Multi-input crafting. | Medium | 9/10 |
| 3.2 | **Facility Management (Build / Idle / Activate / Downsize)** — State machine per facility with cost/rule transitions. | Medium | 9/10 |
| 3.4 | **Operating Costs Per Tick** — Every active facility drains cash automatically. Auto-idle on bankruptcy. | Low | 8/10 |
| 3.6 | **Production Capacity vs. Actual Output Display** — Show theoretical max alongside real output. | Low | 6/10 |

---

## 4. Shops & Consumer Sales

| # | Feature | AI Complexity | Gameplay Value |
|---|---------|--------------|----------------|
| 4.3 | **Dynamic Pricing (Perlin Noise Demand Multiplier)** — Organic boom/bust cycles. Prices driven by simplex noise. | Medium | 8/10 |
| 4.4 | **Saturation Penalty** — Each unit sold reduces price for subsequent units. Resets each cycle. | Low | 6/10 |
| 4.5 | **Visible Demand Forecast** — Players see current demand multiplier and trend direction. | Low | 7/10 |

---

## 7. Market & Trading

| # | Feature | AI Complexity | Gameplay Value |
|---|---------|--------------|----------------|
| 7.1 | **Enhanced Order Matching Engine** — Price-time priority matching. Cheapest sellers to highest bidders. | Medium | 9/10 |
| 7.2 | **Sell Offers with Reserve** — "Keep at least N in stock, sell the rest at price P." Auto-replenishes. | Low | 6/10 |
| 7.3 | **Buy Offers with Target** — "Buy up to (N - current stock) at up to price P." Auto-adjusts quantity. | Low | 6/10 |
| 7.4 | **Price History Charts** — Line charts for every resource showing historical prices. BUY and SELL lines. | Medium | 8/10 |

---

## 12. Fees, Taxes & Money Sinks

| # | Feature | AI Complexity | Gameplay Value |
|---|---------|--------------|----------------|
| 12.1 | **Market Transaction Fee (2%)** — Percentage deducted from both buyer and seller. | Trivial | 5/10 |
| 12.2 | **Luxury Tax (3% on Consumer Sales)** — Additional tax on shop revenue. | Trivial | 4/10 |

---

## 13. Resource Decay & Spoilage

| # | Feature | AI Complexity | Gameplay Value |
|---|---------|--------------|----------------|
| 13.1 | **Perishable Resource Decay** — Wheat, Cotton, Rubber, Petrol, Bread, Canned Food lose % of stock per 60 ticks. | Low | 6/10 |

---

## 19. AI & MCP Integration

| # | Feature | AI Complexity | Gameplay Value |
|---|---------|--------------|----------------|
| 19.1 | **MCP Server** — Model Context Protocol server wrapping the REST API. | High | 10/10 |
| 19.2 | **System AI Corporations** — Built-in LLM-driven bots with distinct strategies. | Very High | 9/10 |
| 19.3 | **User-Controlled AI Agents (API Keys)** — Players generate API keys for autonomous play. | Medium | 8/10 |

---

## 20. Social & Communication

| # | Feature | AI Complexity | Gameplay Value |
|---|---------|--------------|----------------|
| 20.1 | **Player-to-Player Chat** — Direct messaging between players. | Medium | 6/10 |

---

## 21. UI Architecture

| # | Feature | AI Complexity | Gameplay Value |
|---|---------|--------------|----------------|
| 21.1 | **Tab-Based UI Redesign** — Main tabs: Map, Production, Research, Market, Finance. | High | 8/10 |
| 21.2 | **Sidebar Resource Navigation** — Collapsible sidebar listing all resources by tier. | Low | 5/10 |
| 21.3 | **Production Tab (Per-Resource + Global)** — Centralized view of all factories and production. | High | 7/10 |

---

## Implementation Priority

**Highest ROI (start here):**
- 1.1 (config)
- 1.2 (schema)
- 1.3 (API)
- 1.4 (tick engine)

**Core Economy:**
- 3.1, 3.2, 3.4
- 4.3, 4.4, 4.5
- 7.1, 7.2, 7.3, 7.4
- 12.1, 12.2
- 13.1

**AI Integration (enables unique value):**
- 19.1, 19.2, 19.3

**UI & Social:**
- 21.1, 21.2, 21.3
- 20.1
