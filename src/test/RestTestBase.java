package test;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;

/**
 * Base class for REST API tests.
 * Provides reusable HTTP helpers, JSON parsing, and database access.
 */
public class RestTestBase {

    protected static final String BASE_URL = "http://localhost:8080";
    protected static final String API_PREFIX = "/api/v1";

    // ── HTTP Helpers ────────────────────────────────────────────────────────────

    /**
     * Make a POST request to the API.
     */
    protected HttpResponse post(String endpoint, JSONObject body, String token) throws Exception {
        return request("POST", endpoint, body, token);
    }

    /**
     * Make a GET request to the API.
     */
    protected HttpResponse get(String endpoint, String token) throws Exception {
        return request("GET", endpoint, null, token);
    }

    /**
     * Make a DELETE request to the API.
     */
    protected HttpResponse delete(String endpoint, JSONObject body, String token) throws Exception {
        return request("DELETE", endpoint, body, token);
    }

    private HttpResponse request(String method, String endpoint, JSONObject body, String token) throws Exception {
        URL url = new URL(BASE_URL + API_PREFIX + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        conn.setDoInput(true);

        if (body != null) {
            conn.setDoOutput(true);
            String jsonStr = body.toJSONString();
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonStr.getBytes(StandardCharsets.UTF_8));
            }
        }

        return new HttpResponse(conn);
    }

    /**
     * HTTP response wrapper.
     */
    public static class HttpResponse {
        public final int status;
        public final JSONObject json;
        public final String rawBody;

        HttpResponse(HttpURLConnection conn) throws Exception {
            this.status = conn.getResponseCode();

            // Read response body
            InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
            this.rawBody = sb.toString();

            // Parse JSON
            try {
                if (!rawBody.isEmpty()) {
                    this.json = (JSONObject) new JSONParser().parse(rawBody);
                } else {
                    this.json = null;
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse response: " + rawBody, e);
            }
        }

        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }

        public Object data() {
            return json != null ? json.get("data") : null;
        }

        public String error() {
            return json != null ? (String) json.get("error") : null;
        }
    }

    // ── Database Helpers ────────────────────────────────────────────────────────

    /**
     * Reset the database to a clean state.
     * Deletes all data but keeps the schema.
     * Also resets the TickEngine singleton to match the database reset.
     */
    protected void resetDatabase() throws Exception {
        String[] tables = {
            "auth_tokens",
            "price_history",
            "market_orders",
            "shop_sales",
            "shop_inventory",
            "shops",
            "inventory",
            "facilities",
            "players",
            "chat_messages"
        };

        try (Connection conn = database.DB.connect()) {
            for (String table : tables) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DELETE FROM " + table);
                }
            }
            // Reset game_state
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM game_state");
                stmt.execute("INSERT INTO game_state (current_tick) VALUES (0)");
            }
        }

        // Sync the TickEngine singleton with the database reset
        // This prevents tick count desynchronization between in-memory and database state
        try {
            simulation.TickEngine engine = simulation.TickEngine.getInstance();
            if (engine.isRunning()) {
                engine.stop();
            }
            engine.reset();
        } catch (Exception e) {
            System.err.println("[TEST] Warning: Could not reset TickEngine: " + e.getMessage());
        }
    }

    /**
     * Query a single row from the database.
     */
    protected JSONObject queryOne(String sql, Object... params) throws Exception {
        try (Connection conn = database.DB.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                JSONObject row = new JSONObject();
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                return row;
            }
        }
    }

    /**
     * Query multiple rows from the database.
     */
    protected java.util.List<JSONObject> queryAll(String sql, Object... params) throws Exception {
        java.util.List<JSONObject> rows = new java.util.ArrayList<>();
        try (Connection conn = database.DB.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JSONObject row = new JSONObject();
                    ResultSetMetaData meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /**
     * Get the current tick from game_state.
     */
    protected int getCurrentTick() throws Exception {
        JSONObject row = queryOne("SELECT current_tick FROM game_state LIMIT 1");
        return ((Number) row.get("current_tick")).intValue();
    }

    /**
     * Simulate N ticks by starting the TickEngine and waiting.
     * At 4 ticks/sec, each tick takes ~250ms. We use 300ms per tick for margin.
     */
    protected void simulateTicks(int count) throws Exception {
        simulation.TickEngine engine = simulation.TickEngine.getInstance();
        if (!engine.isRunning()) {
            engine.start();
        }
        try {
            Thread.sleep(count * 300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
