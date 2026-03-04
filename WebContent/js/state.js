/**
 * state.js — Single source of truth for the application state.
 * Every DOM update reads from State, never from the DOM itself.
 */
const State = {
    player: null,              // { playerId, username, cash, netWorth }
    inventory: {},             // { wheat: 120, iron: 45, ... }
    facilities: [],            // [{ id, resource_name, state, production_capacity, created_at }]
    marketOrders: [],          // Player's open orders
    selectedResource: null,    // Currently selected in sidebar
    activeTab: 'production',   // 'production' | 'market' | 'chat'
    config: null,              // Game config from /api/v1/config
    resources: [],             // Resource definitions from config
    recipes: {},               // Recipe definitions from config
    buildCosts: {},            // Tier -> cost
    opCosts: {},               // Tier -> cost per tick
    messages: [],              // Chat messages
    players: [],               // Leaderboard / player list
    currentTick: 0
};
