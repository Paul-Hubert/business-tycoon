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
