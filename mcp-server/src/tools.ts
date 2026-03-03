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
