# Trade Empire MCP Server

Model Context Protocol (MCP) server for Trade Empire, wrapping the REST API for LLM integration.

## Quick Start

### Build
```bash
cd mcp-server
npm install
npm run build
```

### Run (with player token)
```bash
# Get a bearer token from the game (via /api/v1/auth/login)
export GAME_API_TOKEN="<your-bearer-token>"
export GAME_API_URL="http://localhost:8080"
npm start
```

### Connect with Claude Desktop
Edit `~/.config/claude-desktop/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "trade-empire": {
      "command": "node",
      "args": ["/absolute/path/to/mcp-server/dist/index.js"],
      "env": {
        "GAME_API_TOKEN": "YOUR_TOKEN_HERE",
        "GAME_API_URL": "http://localhost:8080"
      }
    }
  }
}
```

Then ask Claude:
> Read the game rules resource to understand the game, then tell me what resources I have.

## Architecture

```
Claude / GPT
    |
    v (MCP stdio)
MCP Server (Node.js)
    |
    v (HTTP)
Trade Empire API (Java/Tomcat)
    |
    v (SQL)
MariaDB
```

## Tools (20+)

- `get_game_state` — Your cash, inventory, facilities
- `build_facility` — Build production facility
- `idle_facility`, `activate_facility` — Control production
- `post_sell_offer`, `post_buy_offer` — Trade on market
- `cancel_offer` — Remove order
- `get_orderbook`, `get_price_history` — Market data
- `stock_shop`, `set_shop_price` — Retail management
- `send_message`, `get_messages` — Chat

## Resources (5+)

- `game://rules` — Game rules text
- `game://recipes` — Production recipes (JSON)
- `game://my/state` — Your state (JSON)
- `game://market/prices` — All prices (JSON)
- `game://leaderboard` — Top players (JSON)
- `game://market/history/{resource}` — Price history (JSON)

## Files

- `src/index.ts` — MCP server entry point
- `src/client.ts` — HTTP client for game API
- `src/tools.ts` — Tool definitions (20+)
- `src/resources.ts` — Resource definitions (5+)
- `package.json` — Dependencies
- `tsconfig.json` — TypeScript config

## Environment

- `GAME_API_TOKEN` — Bearer token (required)
- `GAME_API_URL` — Game server URL (default: http://localhost:8080)

## Troubleshooting

**MCP server won't start:**
- Check `GAME_API_TOKEN` is set
- Verify game server is running (`http://localhost:8080`)
- Check `dist/` exists (run `npm run build`)

**Claude can't call tools:**
- Verify token is valid: `curl -H "Authorization: Bearer TOKEN" http://localhost:8080/api/v1/state`
- Check Claude Desktop config has correct path and environment

**Error responses:**
- 401: Token expired or invalid
- 400: Invalid parameters
- 500: Server error (check game logs)

## Next: System AI Corporations

Once the MCP server is working, start the Java-based AI decision engine:
```bash
# In game, check logs for "Trade Empire MCP Server started"
# AI corporations (AgriCorp, IronWorks, TechVentures, LuxuryCraft) will automatically make decisions
```

Set the `ANTHROPIC_API_KEY` environment variable for AI decisions:
```bash
export ANTHROPIC_API_KEY="sk-ant-..."
```

## Next: User API Keys

Players can generate personal API keys:
```bash
# As a player with a valid token:
curl -X POST http://localhost:8080/api/v1/settings/apikey \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "My Agent"}'
```

Response:
```json
{
  "apiKey": "te_12345abc...",
  "name": "My Agent",
  "warning": "Store this key securely. It grants full access to your account."
}
```

Use this key with the same MCP server (just set `GAME_API_TOKEN` to the API key).
