package api;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import database.DB;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.*;
import java.util.UUID;

/**
 * Servlet for managing player API keys (Phase 3.3).
 * Endpoints:
 * - POST /api/v1/settings/apikey — generate a new API key
 * - GET /api/v1/settings/apikey — list all API keys for the player
 * - DELETE /api/v1/settings/apikey/{id} — revoke an API key
 */
@WebServlet("/api/v1/settings/apikey")
public class ApiKeyServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        Integer playerId = (Integer) request.getAttribute("playerId");

        if (playerId == null) {
            response.setStatus(401);
            response.getWriter().write("{\"error\": \"unauthorized\"}");
            return;
        }

        try {
            JSONObject body = parseBody(request);
            String keyName = body.containsKey("name") ? (String) body.get("name") : "Default";

            String newKey = "te_" + UUID.randomUUID().toString().replace("-", "");

            try (Connection conn = DB.connect()) {
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
        } catch (Exception e) {
            response.setStatus(500);
            response.getWriter().write("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        Integer playerId = (Integer) request.getAttribute("playerId");

        if (playerId == null) {
            response.setStatus(401);
            response.getWriter().write("{\"error\": \"unauthorized\"}");
            return;
        }

        try {
            JSONArray keys = new JSONArray();
            try (Connection conn = DB.connect()) {
                String sql = "SELECT id, key_name, created_at, last_used FROM api_keys WHERE player_id = ? ORDER BY created_at DESC";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, playerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            JSONObject key = new JSONObject();
                            key.put("id", rs.getInt("id"));
                            key.put("name", rs.getString("key_name"));
                            key.put("createdAt", rs.getString("created_at"));
                            key.put("lastUsed", rs.getString("last_used"));
                            keys.add(key);
                        }
                    }
                }
            }

            JSONObject result = new JSONObject();
            result.put("apiKeys", keys);
            response.getWriter().write(result.toJSONString());
        } catch (Exception e) {
            response.setStatus(500);
            response.getWriter().write("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        Integer playerId = (Integer) request.getAttribute("playerId");

        if (playerId == null) {
            response.setStatus(401);
            response.getWriter().write("{\"error\": \"unauthorized\"}");
            return;
        }

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                response.setStatus(400);
                response.getWriter().write("{\"error\": \"missing_key_id\"}");
                return;
            }

            String keyIdStr = pathInfo.substring(1);
            int keyId = Integer.parseInt(keyIdStr);

            try (Connection conn = DB.connect()) {
                String sql = "DELETE FROM api_keys WHERE id = ? AND player_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, keyId);
                    ps.setInt(2, playerId);
                    ps.executeUpdate();
                }
            }

            response.getWriter().write("{\"success\": true}");
        } catch (Exception e) {
            response.setStatus(500);
            response.getWriter().write("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private JSONObject parseBody(HttpServletRequest request) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return (JSONObject) new JSONParser().parse(sb.toString());
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
