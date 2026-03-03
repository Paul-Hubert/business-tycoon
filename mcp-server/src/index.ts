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
