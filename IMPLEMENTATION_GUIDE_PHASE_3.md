# Trade Empire — Phase 3 Implementation Guide

> **AI Integration**: MCP Server, System AI Corporations, User AI Agents

---

## Overview

Phase 3 is Trade Empire's defining differentiator. AI agents are not NPCs with special rules — they are full participants using the same API as human players. This phase builds:

1. **MCP Server** — wraps the REST API so any LLM can play via Model Context Protocol
2. **System AI Corporations** — built-in LLM-driven bots that keep the economy alive when few humans are online
3. **User AI Agents** — lets players connect their own Claude/GPT to manage their empire

**Estimated difficulty:** High (MCP spec, AI strategy design)
**Estimated duration:** 3–4 weeks
**Critical order:**
```
19.1 (MCP Server) → 19.2 (System AI) → 19.3 (User AI Agents)
```

The MCP server must exist before any AI can play. System AI relies on the MCP server. User AI Agents just need API keys + the same MCP server.

---

## 19.1 MCP Server

### What Is MCP?

Model Context Protocol (MCP) is a standardized way for LLMs to interact with external systems. An MCP server exposes:
- **Tools** — actions the LLM can call (like functions)
- **Resources** — read-only data the LLM can read (like context)

The LLM connects to your MCP server, reads game state as a resource, and calls tools to take actions — all without writing custom API clients.

**Architecture:**

```
LLM (Claude / GPT / etc.)
    │
    │ MCP Protocol (stdio or HTTP+SSE)
    ▼
MCP Server (Node.js / Python)
    │
    │ HTTP REST calls
    ▼
Trade Empire Game Server (Java/Tomcat)
    │
    │ SQL
    ▼
MariaDB
```

### Implementation

#### 1. Choose MCP Server Language

MCP SDKs are mature in **TypeScript/Node.js** and **Python**. The MCP server is a thin wrapper — it just translates MCP tool calls into REST API calls to your Java server.

Recommendation: **TypeScript + `@modelcontextprotocol/sdk`**

The SDK is actively maintained by Anthropic. You'll write maybe 300 lines of TypeScript.

#### 2. Create `mcp-server/` directory structure

```
mcp-server/
├── package.json
├── tsconfig.json
├── src/
│   ├── index.ts          // Main server entry point
│   ├── tools.ts          // Tool definitions
│   ├── resources.ts      // Resource definitions
│   └── client.ts         // HTTP client for the game API
```

#### 3. `package.json`

```json
{
  "name": "trade-empire-mcp",
  "version": "1.0.0",
  "scripts": {
    "build": "tsc",
    "start": "node dist/index.js"
  },
  "dependencies": {
    "@modelcontextprotocol/sdk": "^1.0.0",
    "node-fetch": "^3.0.0"
  },
  "devDependencies": {
    "typescript": "^5.0.0",
    "@types/node": "^20.0.0"
  }
}
```

#### 4. `src/client.ts` — HTTP wrapper for your game API

```typescript
const GAME_API_URL = process.env.GAME_API_URL || "http://localhost:8080";

export class GameApiClient {
    private token: string;

    constructor(token: string) {
        this.token = token;
    }

    async get(path: string): Promise<any> {
        const response = await fetch(`${GAME_API_URL}${path}`, {
            headers: { "Authorization": `Bearer ${this.token}` }
        });
        if (!response.ok) {
            const body = await response.text();
            throw new Error(`API error ${response.status}: ${body}`);
        }
        return response.json();
    }

    async post(path: string, body: any): Promise<any> {
        const response = await fetch(`${GAME_API_URL}${path}`, {
            method: "POST",
            headers: {
                "Authorization": `Bearer ${this.token}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify(body)
        });
        if (!response.ok) {
            const errorBody = await response.json().catch(() => ({}));
            throw new Error(errorBody.error || `API error ${response.status}`);
        }
        return response.json();
    }

    async delete(path: string): Promise<any> {
        const response = await fetch(`${GAME_API_URL}${path}`, {
            method: "DELETE",
            headers: { "Authorization": `Bearer ${this.token}` }
        });
        if (!response.ok) throw new Error(`API error ${response.status}`);
        return response.json();
    }
}
```

#### 5. `src/tools.ts` — define MCP tools

Each tool maps 1:1 to a REST endpoint:

```typescript
import { Tool } from "@modelcontextprotocol/sdk/types.js";

export const TOOLS: Tool[] = [
    // === Account & State ===
    {
        name: "get_game_state",
        description: "Get your full game state: cash, inventory, facilities, and open market orders.",
        inputSchema: { type: "object", properties: {}, required: [] }
    },
    {
        name: "get_market_prices",
        description: "Get current market prices for all resources.",
        inputSchema: { type: "object", properties: {}, required: [] }
    },
    {
        name: "get_leaderboard",
        description: "Get the top players ranked by net worth.",
        inputSchema: { type: "object", properties: {}, required: [] }
    },

    // === Production ===
    {
        name: "build_facility",
        description: "Build a new production facility for a specific resource. Costs cash immediately. Returns facilityId.",
        inputSchema: {
            type: "object",
            properties: {
                resource: {
                    type: "string",
                    description: "Resource name to produce (e.g., 'wheat', 'steel', 'phone')"
                }
            },
            required: ["resource"]
        }
    },
    {
        name: "idle_facility",
        description: "Pause a facility. It will no longer produce but costs 30% maintenance instead of full operating cost.",
        inputSchema: {
            type: "object",
            properties: { facilityId: { type: "number" } },
            required: ["facilityId"]
        }
    },
    {
        name: "activate_facility",
        description: "Resume a paused (idle) facility at full production and cost.",
        inputSchema: {
            type: "object",
            properties: { facilityId: { type: "number" } },
            required: ["facilityId"]
        }
    },
    {
        name: "downsize_facility",
        description: "Permanently sell a facility. Recovers 40% of the original build cost. Cannot be undone.",
        inputSchema: {
            type: "object",
            properties: { facilityId: { type: "number" } },
            required: ["facilityId"]
        }
    },

    // === Market ===
    {
        name: "post_sell_offer",
        description: "List a resource for sale on the market. 'keepReserve' keeps that many units in your inventory and only sells the rest. Market fee (2%) deducted from proceeds.",
        inputSchema: {
            type: "object",
            properties: {
                resource:    { type: "string",  description: "Resource name" },
                price:       { type: "number",  description: "Price per unit" },
                quantity:    { type: "number",  description: "Max units to sell" },
                keepReserve: { type: "number",  description: "Keep at least this many in inventory (optional)" }
            },
            required: ["resource", "price", "quantity"]
        }
    },
    {
        name: "post_buy_offer",
        description: "Post a buy order for a resource. 'targetQuantity' auto-adjusts how much to buy based on current stock. Market fee (2%) added to cost.",
        inputSchema: {
            type: "object",
            properties: {
                resource:       { type: "string",  description: "Resource name" },
                price:          { type: "number",  description: "Max price per unit" },
                quantity:       { type: "number",  description: "Units to buy" },
                targetQuantity: { type: "number",  description: "Buy up to this total stock (optional)" }
            },
            required: ["resource", "price", "quantity"]
        }
    },
    {
        name: "cancel_offer",
        description: "Cancel one of your open market orders.",
        inputSchema: {
            type: "object",
            properties: { offerId: { type: "number" } },
            required: ["offerId"]
        }
    },
    {
        name: "get_orderbook",
        description: "See current buy and sell orders for a resource.",
        inputSchema: {
            type: "object",
            properties: { resource: { type: "string" } },
            required: ["resource"]
        }
    },
    {
        name: "get_price_history",
        description: "Get historical prices for a resource (useful for spotting trends).",
        inputSchema: {
            type: "object",
            properties: {
                resource:  { type: "string" },
                timeRange: { type: "string", enum: ["5min", "30min", "2hr", "all"] }
            },
            required: ["resource"]
        }
    },

    // === Shops ===
    {
        name: "stock_shop",
        description: "Move consumer goods from your inventory into a shop for sale to NPC customers.",
        inputSchema: {
            type: "object",
            properties: {
                shopId:   { type: "number" },
                resource: { type: "string" },
                quantity: { type: "number" }
            },
            required: ["shopId", "resource", "quantity"]
        }
    },
    {
        name: "set_shop_price",
        description: "Set the selling price for a resource in a shop. NPC customers only buy if your price is at or below the demand cap.",
        inputSchema: {
            type: "object",
            properties: {
                shopId:   { type: "number" },
                resource: { type: "string" },
                price:    { type: "number" }
            },
            required: ["shopId", "resource", "price"]
        }
    },
    {
        name: "get_shop_sales",
        description: "Get recent sales data for a shop.",
        inputSchema: {
            type: "object",
            properties: { shopId: { type: "number" } },
            required: ["shopId"]
        }
    },

    // === Social ===
    {
        name: "send_message",
        description: "Send a chat message to another player.",
        inputSchema: {
            type: "object",
            properties: {
                toPlayerId: { type: "number" },
                message:    { type: "string" }
            },
            required: ["toPlayerId", "message"]
        }
    },
    {
        name: "get_messages",
        description: "Get recent chat messages from other players.",
        inputSchema: { type: "object", properties: {}, required: [] }
    }
];
```

#### 6. `src/resources.ts` — define MCP resources

Resources are read-only context the LLM can load:

```typescript
import { Resource } from "@modelcontextprotocol/sdk/types.js";

export const RESOURCES: Resource[] = [
    {
        uri: "game://rules",
        name: "Game Rules",
        description: "Complete game rules: how production, market, shops, and fees work.",
        mimeType: "text/plain"
    },
    {
        uri: "game://recipes",
        name: "Production Recipes",
        description: "All resource recipes showing required inputs and outputs.",
        mimeType: "application/json"
    },
    {
        uri: "game://my/state",
        name: "My State",
        description: "Your current player state: cash, inventory, facilities.",
        mimeType: "application/json"
    },
    {
        uri: "game://market/prices",
        name: "Market Prices",
        description: "Current market prices for all resources.",
        mimeType: "application/json"
    },
    {
        uri: "game://leaderboard",
        name: "Leaderboard",
        description: "Current top players ranked by net worth.",
        mimeType: "application/json"
    }
];

// Per-resource price history URIs: game://market/history/{resourceName}
export function isHistoryUri(uri: string): boolean {
    return uri.startsWith("game://market/history/");
}

export function getResourceNameFromHistoryUri(uri: string): string {
    return uri.replace("game://market/history/", "");
}
```

#### 7. `src/index.ts` — main server

```typescript
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { TOOLS } from "./tools.js";
import { RESOURCES, isHistoryUri, getResourceNameFromHistoryUri } from "./resources.js";
import { GameApiClient } from "./client.js";

const token = process.env.GAME_API_TOKEN;
if (!token) {
    console.error("GAME_API_TOKEN environment variable required");
    process.exit(1);
}

const api = new GameApiClient(token);

const server = new Server(
    { name: "trade-empire", version: "1.0.0" },
    { capabilities: { tools: {}, resources: {} } }
);

// List tools
server.setRequestHandler("tools/list", async () => ({ tools: TOOLS }));

// Call a tool
server.setRequestHandler("tools/call", async (request) => {
    const { name, arguments: args } = request.params;

    try {
        let result: any;

        switch (name) {
            case "get_game_state":       result = await api.get("/api/v1/state"); break;
            case "get_market_prices":    result = await api.get("/api/v1/market/prices"); break;
            case "get_leaderboard":      result = await api.get("/api/v1/leaderboard"); break;

            case "build_facility":       result = await api.post("/api/v1/production/build", args); break;
            case "idle_facility":        result = await api.post("/api/v1/production/idle", args); break;
            case "activate_facility":    result = await api.post("/api/v1/production/activate", args); break;
            case "downsize_facility":    result = await api.post("/api/v1/production/downsize", args); break;

            case "post_sell_offer":      result = await api.post("/api/v1/market/sell", args); break;
            case "post_buy_offer":       result = await api.post("/api/v1/market/buy", args); break;
            case "cancel_offer":         result = await api.delete(`/api/v1/market/${args.offerId}`); break;
            case "get_orderbook":        result = await api.get(`/api/v1/market/${args.resource}`); break;
            case "get_price_history":    result = await api.get(`/api/v1/market/history/${args.resource}?range=${args.timeRange || "30min"}`); break;

            case "stock_shop":           result = await api.post("/api/v1/shop/stock", args); break;
            case "set_shop_price":       result = await api.post("/api/v1/shop/price", args); break;
            case "get_shop_sales":       result = await api.get(`/api/v1/shop/${args.shopId}/sales`); break;

            case "send_message":         result = await api.post("/api/v1/chat/send", args); break;
            case "get_messages":         result = await api.get("/api/v1/chat/messages"); break;

            default:
                throw new Error(`Unknown tool: ${name}`);
        }

        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }]
        };
    } catch (error: any) {
        return {
            content: [{ type: "text", text: `Error: ${error.message}` }],
            isError: true
        };
    }
});

// List resources
server.setRequestHandler("resources/list", async () => ({ resources: RESOURCES }));

// Read a resource
server.setRequestHandler("resources/read", async (request) => {
    const uri = request.params.uri;

    try {
        let content: string;

        if (uri === "game://rules") {
            content = GAME_RULES_TEXT; // Static string (see below)
        } else if (uri === "game://recipes") {
            const config = await api.get("/api/v1/config");
            content = JSON.stringify(config.recipes, null, 2);
        } else if (uri === "game://my/state") {
            const state = await api.get("/api/v1/state");
            content = JSON.stringify(state, null, 2);
        } else if (uri === "game://market/prices") {
            const prices = await api.get("/api/v1/market/prices");
            content = JSON.stringify(prices, null, 2);
        } else if (uri === "game://leaderboard") {
            const lb = await api.get("/api/v1/leaderboard");
            content = JSON.stringify(lb, null, 2);
        } else if (isHistoryUri(uri)) {
            const resource = getResourceNameFromHistoryUri(uri);
            const history = await api.get(`/api/v1/market/history/${resource}`);
            content = JSON.stringify(history, null, 2);
        } else {
            throw new Error(`Unknown resource URI: ${uri}`);
        }

        return { contents: [{ uri, mimeType: "application/json", text: content }] };
    } catch (error: any) {
        throw new Error(`Failed to read ${uri}: ${error.message}`);
    }
});

// Static game rules text (loaded once)
const GAME_RULES_TEXT = `
Trade Empire — Game Rules

You are a corporation competing in a persistent economic simulation.
You start with $1,000. Your goal: maximize net worth.

PRODUCTION:
- Build facilities to produce resources (costs cash)
- Raw resources (wheat, iron, etc.) produce automatically
- Processed resources need inputs (steel = iron + coal)
- Each active facility costs cash per tick (operating cost)
- Idle a facility to pause production at 30% maintenance cost

MARKET:
- Post sell orders: set a price, optionally keep a reserve in stock
- Post buy orders: set a max price, optionally set a target stock level
- Orders match by price-time priority (cheapest seller to highest buyer)
- 2% market fee on each trade (1% from each side)

SHOPS:
- Stock shops with consumer goods (car, phone, clothing, etc.)
- Set a price — NPC customers buy if your price is below demand cap
- Demand fluctuates (higher = more sales at higher prices)
- 3% luxury tax on all shop revenue

STRATEGY TIPS:
- Raw resources are cheap to build but low margin
- Consumer goods have high margins but require complex supply chains
- Trade intermediates to fund your way up the production chain
- Watch price history — buy low, sell high
- Operating costs never stop — you must sell constantly to stay solvent
`;

// Start server
const transport = new StdioServerTransport();
server.connect(transport);
console.error("Trade Empire MCP Server started");
```

#### 8. Launching the MCP Server

```bash
# Build
cd mcp-server && npm install && npm run build

# Start (for a specific player token)
GAME_API_TOKEN=<bearer-token> GAME_API_URL=http://localhost:8080 node dist/index.js
```

For Claude Desktop integration, add to `~/.config/claude-desktop/claude_desktop_config.json`:
```json
{
  "mcpServers": {
    "trade-empire": {
      "command": "node",
      "args": ["/path/to/mcp-server/dist/index.js"],
      "env": {
        "GAME_API_TOKEN": "YOUR_TOKEN_HERE",
        "GAME_API_URL": "http://localhost:8080"
      }
    }
  }
}
```

### Pitfalls & Foresight

**Pitfall 1: Tool errors are silent**
- ❌ Bad: Tool returns empty result when the API call fails.
- ✅ Good: Return `isError: true` with a descriptive message. The LLM needs to know WHY something failed to adjust its strategy.

**Pitfall 2: Too many tools overwhelm the LLM**
- LLMs perform worse with too many tools. 20+ tools starts degrading reasoning.
- ✅ Group related tools. Consider merging `idle_facility` and `activate_facility` into `set_facility_state`.

**Pitfall 3: Resources not fresh enough**
- A resource cached 60 seconds ago is useless in a fast-moving market.
- ✅ Always fetch resources live from the API, never cache in the MCP server.

**Pitfall 4: Token included in logs**
- The bearer token gives full account access. Never log it.
- ✅ Log `[REDACTED]` instead of token values.

**Foresight for Phase 4 (UI):**
- Expose `/api/v1/config` with the recipes list so MCP resources can serve the recipe data dynamically.
- Don't hardcode recipes in the MCP server — they might change.

---

## 19.2 System AI Corporations

### What They Are

Built-in LLM-driven players that ensure the economy functions even with zero human players. Each corporation has:
- $1,000 starting cash (same as humans)
- A distinct strategy profile
- An LLM "brain" making decisions every 5–10 seconds
- Identical API access — no cheating, no omniscience

| Corporation | Strategy | Focus |
|------------|----------|-------|
| AgriCorp | Conservative, high-volume | Wheat, Bread, Canned Food supply chain |
| IronWorks | Mid-chain specialist | Iron, Coal, Steel, industrial intermediates |
| TechVentures | Aggressive growth | Electronics: Circuit, Battery, Phone, Laptop |
| LuxuryCraft | Premium, low-volume | Gold, Jewelry, Car |

### Implementation

#### 1. Register AI corporations as players on startup

In `SimulationServlet.contextInitialized()`:

```java
private void initializeAICorporations() {
    ConfigManager config = ConfigManager.getInstance();
    if (!config.getBoolean("ai.system_corporations_enabled", true)) return;

    String[] corporations = {"AgriCorp", "IronWorks", "TechVentures", "LuxuryCraft"};
    String[] strategies   = {"agricorp", "ironworks", "techventures", "luxurycraft"};

    try (Connection conn = DatabaseProvider.getConnection()) {
        for (int i = 0; i < corporations.length; i++) {
            String name = corporations[i];
            String strategy = strategies[i];

            // Upsert: create if not exists, leave alone if exists
            String sql = """
                INSERT IGNORE INTO players (username, password_hash, cash, is_ai, ai_strategy)
                VALUES (?, 'N/A_AI_ACCOUNT', ?, TRUE, ?)
                """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                double startingCash = config.getDouble("economy.starting_cash", 1000.0);
                ps.setString(1, name);
                ps.setDouble(2, startingCash);
                ps.setString(3, strategy);
                ps.executeUpdate();
            }
        }
    } catch (Exception e) {
        System.err.println("[AI] Failed to initialize AI corporations: " + e.getMessage());
    }
}
```

#### 2. Create a token for each AI corporation

Each AI corporation needs a permanent API token so the MCP server can authenticate as them:

```java
private void ensureAITokens() {
    try (Connection conn = DatabaseProvider.getConnection()) {
        String aisSql = "SELECT id, username FROM players WHERE is_ai = TRUE";
        try (PreparedStatement ps = conn.prepareStatement(aisSql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int playerId = rs.getInt("id");
                String name = rs.getString("username");

                // Check if token exists
                String checkSql = "SELECT COUNT(*) FROM auth_tokens WHERE player_id = ? AND expires_at > NOW()";
                boolean hasToken;
                try (PreparedStatement cp = conn.prepareStatement(checkSql)) {
                    cp.setInt(1, playerId);
                    try (ResultSet cr = cp.executeQuery()) {
                        cr.next();
                        hasToken = cr.getInt(1) > 0;
                    }
                }

                if (!hasToken) {
                    // Create a long-lived token (100 years)
                    String token = UUID.randomUUID().toString();
                    String insertSql = "INSERT INTO auth_tokens (player_id, token, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 100 YEAR))";
                    try (PreparedStatement ip = conn.prepareStatement(insertSql)) {
                        ip.setInt(1, playerId);
                        ip.setString(2, token);
                        ip.executeUpdate();
                        System.out.println("[AI] Token for " + name + ": " + token);
                    }
                }
            }
        }
    } catch (Exception e) {
        System.err.println("[AI] Failed to ensure AI tokens: " + e.getMessage());
    }
}
```

#### 3. Create the AI Decision Engine

This is where the real work is. Each AI corporation runs on a schedule and calls the LLM:

```java
package com.tradeempire.ai;

import com.tradeempire.config.ConfigManager;
import java.util.concurrent.*;

public class AICorporationEngine {
    private static AICorporationEngine instance;
    private ScheduledExecutorService executor;

    private AICorporationEngine() {}

    public static AICorporationEngine getInstance() {
        if (instance == null) {
            synchronized (AICorporationEngine.class) {
                if (instance == null) instance = new AICorporationEngine();
            }
        }
        return instance;
    }

    public void start() {
        executor = Executors.newScheduledThreadPool(4); // One thread per AI corp

        scheduleAI("AgriCorp",    "agricorp");
        scheduleAI("IronWorks",   "ironworks");
        scheduleAI("TechVentures","techventures");
        scheduleAI("LuxuryCraft", "luxurycraft");
    }

    private void scheduleAI(String name, String strategy) {
        ConfigManager config = ConfigManager.getInstance();
        int intervalSeconds = config.getInt("ai.decision_interval_seconds", 10);

        executor.scheduleAtFixedRate(
            new AICorporationTask(name, strategy),
            (long)(Math.random() * intervalSeconds), // Stagger start times
            intervalSeconds,
            TimeUnit.SECONDS
        );
    }

    public void stop() {
        if (executor != null) executor.shutdown();
    }
}
```

```java
package com.tradeempire.ai;

import java.net.http.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.json.*;

/**
 * Calls the Anthropic API to make decisions for an AI corporation.
 * The AI sees the same game state as a human and calls the same REST API.
 */
public class AICorporationTask implements Runnable {
    private final String name;
    private final String strategy;
    private final String apiToken; // Bearer token for this AI player

    public AICorporationTask(String name, String strategy) {
        this.name = name;
        this.strategy = strategy;
        this.apiToken = loadTokenForCorporation(name);
    }

    @Override
    public void run() {
        try {
            // 1. Get game state
            String stateJson = callGameApi("GET", "/api/v1/state", null);
            String pricesJson = callGameApi("GET", "/api/v1/market/prices", null);
            String leaderboardJson = callGameApi("GET", "/api/v1/leaderboard", null);

            // 2. Call Anthropic API with strategy prompt + state
            String decision = callAnthropic(stateJson, pricesJson, leaderboardJson);

            // 3. Execute decision
            executeDecision(decision);

        } catch (Exception e) {
            System.err.println("[AI:" + name + "] Error: " + e.getMessage());
        }
    }

    private String buildSystemPrompt() {
        return switch (strategy) {
            case "agricorp" -> """
                You are AgriCorp, a conservative agricultural trading corporation.
                Strategy: Build a high-volume agriculture operation.
                Focus: Wheat → Bread chain, Canned Food, sell at modest margins.
                Avoid: High-cost consumer electronics or luxury goods.
                Risk tolerance: LOW — prioritize survival over growth.
                """;

            case "ironworks" -> """
                You are IronWorks, an industrial mid-chain specialist.
                Strategy: Become the dominant Steel supplier in the market.
                Focus: Iron + Coal → Steel production. Sell Steel to other manufacturers.
                Avoid: Building final consumer goods — sell intermediates only.
                Risk tolerance: MEDIUM — expand steadily, undercut competitors by $0.10.
                """;

            case "techventures" -> """
                You are TechVentures, an aggressive electronics manufacturer.
                Strategy: Dominate the high-value electronics market.
                Focus: Build toward Phone and Laptop production. Buy intermediates on market.
                Avoid: Raw material extraction — buy it cheaper than building facilities.
                Risk tolerance: HIGH — invest aggressively, accept temporary cash shortfalls.
                """;

            case "luxurycraft" -> """
                You are LuxuryCraft, a premium luxury goods producer.
                Strategy: Low volume, very high margin. Quality over quantity.
                Focus: Gold → Jewelry, Car production. Never undersell.
                Avoid: Commodity markets. Don't compete on price.
                Risk tolerance: LOW — maintain large cash reserves.
                """;

            default -> "You are an AI player in Trade Empire. Maximize your net worth.";
        };
    }

    private String callAnthropic(String state, String prices, String leaderboard) throws Exception {
        ConfigManager config = ConfigManager.getInstance();
        String anthropicKey = config.getString("ai.anthropic_api_key", "");

        if (anthropicKey.isEmpty()) {
            System.err.println("[AI:" + name + "] No Anthropic API key configured");
            return "{}";
        }

        String userMessage = String.format("""
            Current game state:
            %s

            Current market prices:
            %s

            Leaderboard:
            %s

            You may take ONE action this turn. Choose the highest-value action for your strategy.
            Respond with a JSON object like one of these:
            {"action": "build", "resource": "wheat"}
            {"action": "sell", "resource": "steel", "price": 15.00, "quantity": 100, "keepReserve": 50}
            {"action": "buy", "resource": "iron", "price": 4.00, "quantity": 200}
            {"action": "idle", "facilityId": 5}
            {"action": "wait"}
            """, state, prices, leaderboard);

        // Call Anthropic API
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "claude-haiku-4-5-20251001"); // Fast, cheap for AI decisions
        requestBody.put("max_tokens", 256);
        requestBody.put("system", buildSystemPrompt());
        requestBody.put("messages", new JSONArray()
            .put(new JSONObject().put("role", "user").put("content", userMessage))
        );

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .header("x-api-key", anthropicKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject result = new JSONObject(response.body());
        return result.getJSONArray("content").getJSONObject(0).getString("text");
    }

    private void executeDecision(String decisionJson) throws Exception {
        JSONObject decision = new JSONObject(decisionJson.trim());
        String action = decision.getString("action");

        switch (action) {
            case "build" -> callGameApi("POST", "/api/v1/production/build",
                new JSONObject().put("resource", decision.getString("resource")).toString());

            case "sell" -> callGameApi("POST", "/api/v1/market/sell",
                new JSONObject()
                    .put("resource",    decision.getString("resource"))
                    .put("price",       decision.getDouble("price"))
                    .put("quantity",    decision.getInt("quantity"))
                    .put("keepReserve", decision.optInt("keepReserve", 0))
                    .toString());

            case "buy" -> callGameApi("POST", "/api/v1/market/buy",
                new JSONObject()
                    .put("resource", decision.getString("resource"))
                    .put("price",    decision.getDouble("price"))
                    .put("quantity", decision.getInt("quantity"))
                    .toString());

            case "idle" -> callGameApi("POST", "/api/v1/production/idle",
                new JSONObject().put("facilityId", decision.getInt("facilityId")).toString());

            case "wait" -> System.out.println("[AI:" + name + "] Decided to wait");

            default -> System.err.println("[AI:" + name + "] Unknown action: " + action);
        }
    }

    private String callGameApi(String method, String path, String body) throws Exception {
        String gameApiUrl = System.getenv().getOrDefault("GAME_API_URL", "http://localhost:8080");
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
            .uri(URI.create(gameApiUrl + path))
            .header("Authorization", "Bearer " + apiToken);

        if ("POST".equals(method)) {
            reqBuilder.header("Content-Type", "application/json")
                      .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            reqBuilder.GET();
        }

        HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String loadTokenForCorporation(String name) {
        // Load from DB or environment
        // Implementation: query auth_tokens for this AI's player_id
        return ""; // Placeholder
    }
}
```

### Pitfalls & Foresight

**Pitfall 1: AI API key in the database**
- ❌ Bad: `config.getString("ai.anthropic_api_key")` stored in `GameConfig.properties` and committed to git.
- ✅ Good: Environment variable `ANTHROPIC_API_KEY` only. Never in source control.

**Pitfall 2: AI makes decisions too fast**
- An AI running every 1 second will drain your Anthropic API budget instantly.
- ✅ Start at 30-second intervals. Tune down only if the economy feels sluggish.
- Add config: `ai.decision_interval_seconds=30`

**Pitfall 3: AI hallucinates resource names**
- LLMs will invent resources that don't exist ("I'll build a 'semiconductor_fab'").
- ✅ API validates all resource names. AI receives a clear error: `unknown_resource`.
- ✅ Include the full resource list in the AI system prompt or as a resource it reads first.

**Pitfall 4: AI goes bankrupt in minutes**
- A badly-tuned AI builds expensive facilities it can't sustain.
- ✅ Build guardrails: before executing a build action, check if operating costs would exceed projected income.
- Or: give the AI a longer context that includes its financial history + a "budgeting guideline".

**Pitfall 5: All AIs making identical decisions**
- If all 4 AIs read the same market and respond identically, they all dump Steel at the same time.
- ✅ Stagger decision times (random offset). Each AI's strategy prompt actively discourages others' niches.

---

## 19.3 User-Controlled AI Agents (API Keys)

### What It Does

Players generate a personal API key from their account settings. They give this key to their own Claude/GPT agent via the MCP server. Their agent plays on their behalf using the same API as the UI.

### Implementation

#### 1. Add API Key table

```sql
CREATE TABLE IF NOT EXISTS api_keys (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    key_name VARCHAR(100) NOT NULL DEFAULT 'Default',
    api_key VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_used TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    INDEX idx_player (player_id),
    INDEX idx_api_key (api_key)
);
```

#### 2. Key generation endpoint

```java
// POST /api/v1/settings/apikey
// Generates a new API key for the authenticated player
@WebServlet("/api/v1/settings/apikey")
public class ApiKeyServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws Exception {
        response.setContentType("application/json");
        int playerId = (int) request.getAttribute("playerId");

        JSONObject body = parseBody(request);
        String keyName = (String) body.getOrDefault("name", "Default");

        String newKey = "te_" + UUID.randomUUID().toString().replace("-", "");

        try (Connection conn = DatabaseProvider.getConnection()) {
            // Limit: max 5 API keys per player
            String countSql = "SELECT COUNT(*) FROM api_keys WHERE player_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) >= 5) {
                        response.setStatus(400);
                        response.getWriter().write("{\"error\": \"api_key_limit_reached\"}");
                        return;
                    }
                }
            }

            String insertSql = "INSERT INTO api_keys (player_id, key_name, api_key) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, playerId);
                ps.setString(2, keyName);
                ps.setString(3, newKey);
                ps.executeUpdate();
            }

            JSONObject result = new JSONObject();
            result.put("apiKey", newKey);
            result.put("name", keyName);
            result.put("warning", "Store this key securely. It grants full access to your account.");
            response.getWriter().write(result.toJSONString());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws Exception {
        // Revoke an API key
        int playerId = (int) request.getAttribute("playerId");
        String keyId = request.getPathInfo().substring(1); // /api/v1/settings/apikey/{id}

        try (Connection conn = DatabaseProvider.getConnection()) {
            String sql = "DELETE FROM api_keys WHERE id = ? AND player_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, keyId);
                ps.setInt(2, playerId);
                ps.executeUpdate();
            }
        }

        response.getWriter().write("{\"success\": true}");
    }
}
```

#### 3. Update AuthFilter to accept API keys

```java
// In AuthFilter.isValidToken(), also check api_keys table:
private boolean isValidToken(String token) {
    try (Connection conn = DatabaseProvider.getConnection()) {
        // Check regular auth token
        String tokenSql = "SELECT 1 FROM auth_tokens WHERE token = ? AND expires_at > NOW()";
        try (PreparedStatement ps = conn.prepareStatement(tokenSql)) {
            ps.setString(1, token);
            if (ps.executeQuery().next()) return true;
        }

        // Check API key
        String apiKeySql = "SELECT 1 FROM api_keys WHERE api_key = ?";
        try (PreparedStatement ps = conn.prepareStatement(apiKeySql)) {
            ps.setString(1, token);
            if (ps.executeQuery().next()) {
                // Update last_used
                conn.prepareStatement("UPDATE api_keys SET last_used = NOW() WHERE api_key = '" + token + "'").executeUpdate();
                return true;
            }
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }
    return false;
}
```

### Pitfalls & Foresight

**Pitfall 1: User's AI agent + user simultaneously acting**
- Player uses the UI at the same time their AI agent is posting orders.
- This is intentional and allowed. The UI shows real-time state, so conflicts are visible.
- Ensure API endpoints are idempotent where possible (posting same order twice should give a clear error, not two orders).

**Pitfall 2: API key revocation not immediate**
- If a player revokes a key but the AI has already started a series of actions, those in-flight requests still use the old key.
- ✅ Check the key on every request (no caching). RevocationSQLquery is fast with proper index.

**Pitfall 3: Rate limiting needed urgently**
- A user's AI agent with a bug could hammer the server with 1000 req/sec.
- ✅ Add per-player rate limiting in AuthFilter before Phase 3 ships.
- Simple approach: count requests in Redis or a `request_counts` in-memory map.

---

## Phase 3 Checklist

- [ ] MCP server builds and starts without errors
- [ ] All tools return valid JSON responses
- [ ] Resources return live data from the API
- [ ] MCP server tested with Claude Desktop — can read state and post an order
- [ ] System AI corporations created in DB on startup
- [ ] AI corporations have permanent tokens
- [ ] AI decision engine calls Anthropic API (with real key)
- [ ] AI corporations don't go bankrupt within 5 minutes of starting
- [ ] User API key generation endpoint works
- [ ] API keys accepted by AuthFilter
- [ ] Rate limiting in place

---

## Next: Phase 4

Once Phase 3 is stable, you'll build:
- The complete tab-based UI
- Sidebar resource navigation
- Production and market tabs
- Player chat

See `IMPLEMENTATION_GUIDE_PHASE_4.md`
