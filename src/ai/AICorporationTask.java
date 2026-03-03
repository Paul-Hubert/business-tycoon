package ai;

import config.ConfigManager;
import database.DB;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.*;

/**
 * Calls the Anthropic API to make decisions for an AI corporation.
 * The AI sees the same game state as a human and calls the same REST API.
 */
public class AICorporationTask implements Runnable {
    private final String name;
    private final String strategy;
    private String apiToken; // Bearer token for this AI player
    private int playerId = -1;

    public AICorporationTask(String name, String strategy) {
        this.name = name;
        this.strategy = strategy;
        this.loadTokenAndPlayerId();
    }

    private void loadTokenAndPlayerId() {
        try (Connection conn = DB.connect()) {
            // Find player by username
            String playerSql = "SELECT id FROM players WHERE username = ? AND is_ai = TRUE";
            try (PreparedStatement ps = conn.prepareStatement(playerSql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        this.playerId = rs.getInt("id");
                    } else {
                        System.err.println("[AI:" + name + "] Player not found in database");
                        return;
                    }
                }
            }

            // Find token for this player
            String tokenSql = "SELECT token FROM auth_tokens WHERE player_id = ? AND expires_at > NOW() LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(tokenSql)) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        this.apiToken = rs.getString("token");
                    } else {
                        System.err.println("[AI:" + name + "] No valid token found for player " + playerId);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[AI:" + name + "] Failed to load token: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        if (playerId == -1 || apiToken == null || apiToken.isEmpty()) {
            System.err.println("[AI:" + name + "] Missing player ID or token, skipping decision");
            return;
        }

        try {
            // 1. Get game state
            String stateJson = callGameApi("GET", "/api/v1/state", null);
            String pricesJson = callGameApi("GET", "/api/v1/market/prices", null);
            String leaderboardJson = callGameApi("GET", "/api/v1/leaderboard", null);

            // 2. Call Anthropic API with strategy prompt + state
            String decision = callAnthropic(stateJson, pricesJson, leaderboardJson);

            // 3. Execute decision
            if (decision != null && !decision.trim().isEmpty()) {
                executeDecision(decision);
            }

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
            return null;
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
        JSONArray messages = new JSONArray();
        JSONObject msg = new JSONObject();
        msg.put("role", "user");
        msg.put("content", userMessage);
        messages.add(msg);
        requestBody.put("messages", messages);

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .header("x-api-key", anthropicKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toJSONString()))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.err.println("[AI:" + name + "] Anthropic API error " + response.statusCode() + ": " + response.body());
            return null;
        }

        JSONObject result = (JSONObject) new JSONParser().parse(response.body());
        JSONArray content = (JSONArray) result.get("content");
        if (content != null && content.size() > 0) {
            JSONObject firstContent = (JSONObject) content.get(0);
            return (String) firstContent.get("text");
        }
        return null;
    }

    private void executeDecision(String decisionJson) throws Exception {
        try {
            JSONObject decision = (JSONObject) new JSONParser().parse(decisionJson.trim());
            String action = (String) decision.get("action");

            if (action == null) {
                System.err.println("[AI:" + name + "] No action in decision");
                return;
            }

            switch (action) {
                case "build" -> {
                    String resource = (String) decision.get("resource");
                    JSONObject body = new JSONObject();
                    body.put("resource", resource);
                    callGameApi("POST", "/api/v1/production/build", body.toJSONString());
                    System.out.println("[AI:" + name + "] Built " + resource);
                }

                case "sell" -> {
                    JSONObject body = new JSONObject();
                    body.put("resource", decision.get("resource"));
                    body.put("price", decision.get("price"));
                    body.put("quantity", decision.get("quantity"));
                    if (decision.containsKey("keepReserve")) {
                        body.put("keepReserve", decision.get("keepReserve"));
                    }
                    callGameApi("POST", "/api/v1/market/sell", body.toJSONString());
                    System.out.println("[AI:" + name + "] Posted sell order");
                }

                case "buy" -> {
                    JSONObject body = new JSONObject();
                    body.put("resource", decision.get("resource"));
                    body.put("price", decision.get("price"));
                    body.put("quantity", decision.get("quantity"));
                    callGameApi("POST", "/api/v1/market/buy", body.toJSONString());
                    System.out.println("[AI:" + name + "] Posted buy order");
                }

                case "idle" -> {
                    JSONObject body = new JSONObject();
                    body.put("facilityId", decision.get("facilityId"));
                    callGameApi("POST", "/api/v1/production/idle", body.toJSONString());
                    System.out.println("[AI:" + name + "] Idled facility");
                }

                case "wait" -> {
                    System.out.println("[AI:" + name + "] Decided to wait");
                }

                default -> {
                    System.err.println("[AI:" + name + "] Unknown action: " + action);
                }
            }
        } catch (Exception e) {
            System.err.println("[AI:" + name + "] Failed to execute decision: " + e.getMessage());
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
        if (response.statusCode() >= 400) {
            System.err.println("[AI:" + name + "] API error " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }
}
