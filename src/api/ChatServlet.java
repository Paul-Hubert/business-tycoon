package api;

import database.DB;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.sql.*;

/**
 * Player chat API.
 *
 *   POST /api/v1/chat/send       — Send a message to another player
 *   GET  /api/v1/chat/messages    — Get all messages for the current player
 *
 * Messages persist in the chat_messages table.
 * All endpoints require a valid Bearer token (enforced by AuthFilter).
 */
@WebServlet("/api/v1/chat/*")
public class ChatServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=utf-8");

        Integer playerId = (Integer) req.getAttribute("playerId");
        if (playerId == null) { sendUnauthorized(resp); return; }

        String path = req.getPathInfo();
        if (!"/send".equals(path)) {
            resp.setStatus(404);
            resp.getWriter().write(error("not_found").toJSONString());
            return;
        }

        JSONObject body = parseBody(req);
        if (body == null) {
            resp.setStatus(400);
            resp.getWriter().write(error("invalid_json").toJSONString());
            return;
        }

        handleSend(playerId, body, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=utf-8");

        Integer playerId = (Integer) req.getAttribute("playerId");
        if (playerId == null) { sendUnauthorized(resp); return; }

        String path = req.getPathInfo();
        if ("/messages".equals(path)) {
            handleGetMessages(playerId, req, resp);
        } else {
            resp.setStatus(404);
            resp.getWriter().write(error("not_found").toJSONString());
        }
    }

    // ── Send message ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleSend(int fromPlayerId, JSONObject body, HttpServletResponse resp) throws IOException {
        Object toObj = body.get("toPlayerId");
        String message = (String) body.get("message");

        if (toObj == null) {
            resp.setStatus(400);
            resp.getWriter().write(error("missing_toPlayerId").toJSONString());
            return;
        }
        int toPlayerId = ((Number) toObj).intValue();

        if (message == null || message.trim().isEmpty()) {
            resp.setStatus(400);
            resp.getWriter().write(error("missing_message").toJSONString());
            return;
        }

        message = message.trim();
        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }

        if (fromPlayerId == toPlayerId) {
            resp.setStatus(400);
            resp.getWriter().write(error("cannot_message_self").toJSONString());
            return;
        }

        try (Connection conn = DB.connect()) {
            // Verify recipient exists
            String checkSql = "SELECT id FROM players WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, toPlayerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        resp.setStatus(404);
                        resp.getWriter().write(error("recipient_not_found").toJSONString());
                        return;
                    }
                }
            }

            // Insert message
            String sql = "INSERT INTO chat_messages (from_player_id, to_player_id, message) VALUES (?, ?, ?)";
            long messageId;
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, fromPlayerId);
                ps.setInt(2, toPlayerId);
                ps.setString(3, message);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    messageId = keys.getLong(1);
                }
            }

            JSONObject data = new JSONObject();
            data.put("messageId", messageId);
            resp.setStatus(201);
            resp.getWriter().write(success(data).toJSONString());

        } catch (SQLException e) {
            System.err.println("[CHAT] Send error: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write(error("db_error").toJSONString());
        }
    }

    // ── Get messages ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleGetMessages(int playerId, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String sinceParam = req.getParameter("since");

        try (Connection conn = DB.connect()) {
            String sql;
            if (sinceParam != null) {
                sql = """
                    SELECT m.id, m.from_player_id, m.to_player_id, m.message, m.created_at,
                           pf.username AS from_username, COALESCE(pf.is_ai, FALSE) AS from_is_ai,
                           pt.username AS to_username
                    FROM chat_messages m
                    JOIN players pf ON m.from_player_id = pf.id
                    JOIN players pt ON m.to_player_id = pt.id
                    WHERE (m.from_player_id = ? OR m.to_player_id = ?)
                      AND m.created_at > ?
                    ORDER BY m.created_at ASC
                    LIMIT 200
                    """;
            } else {
                sql = """
                    SELECT m.id, m.from_player_id, m.to_player_id, m.message, m.created_at,
                           pf.username AS from_username, COALESCE(pf.is_ai, FALSE) AS from_is_ai,
                           pt.username AS to_username
                    FROM chat_messages m
                    JOIN players pf ON m.from_player_id = pf.id
                    JOIN players pt ON m.to_player_id = pt.id
                    WHERE (m.from_player_id = ? OR m.to_player_id = ?)
                    ORDER BY m.created_at DESC
                    LIMIT 100
                    """;
            }

            JSONArray messages = new JSONArray();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, playerId);
                ps.setInt(2, playerId);
                if (sinceParam != null) {
                    ps.setString(3, sinceParam);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        JSONObject msg = new JSONObject();
                        msg.put("id", (long) rs.getInt("id"));
                        msg.put("fromPlayerId", (long) rs.getInt("from_player_id"));
                        msg.put("toPlayerId", (long) rs.getInt("to_player_id"));
                        msg.put("fromUsername", rs.getString("from_username"));
                        msg.put("fromIsAi", rs.getBoolean("from_is_ai"));
                        msg.put("toUsername", rs.getString("to_username"));
                        msg.put("message", rs.getString("message"));
                        msg.put("createdAt", rs.getString("created_at"));
                        messages.add(msg);
                    }
                }
            }

            // If no since param, reverse to get chronological order
            if (sinceParam == null) {
                java.util.Collections.reverse(messages);
            }

            JSONObject data = new JSONObject();
            data.put("messages", messages);

            resp.setStatus(200);
            resp.getWriter().write(success(data).toJSONString());

        } catch (SQLException e) {
            System.err.println("[CHAT] Get messages error: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write(error("db_error").toJSONString());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JSONObject parseBody(HttpServletRequest req) {
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = req.getReader()) {
                while ((line = reader.readLine()) != null) sb.append(line);
            }
            return (JSONObject) new JSONParser().parse(sb.toString());
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private JSONObject success(Object data) {
        JSONObject r = new JSONObject();
        r.put("success", Boolean.TRUE);
        r.put("data", data);
        r.put("error", null);
        return r;
    }

    @SuppressWarnings("unchecked")
    private JSONObject error(String code) {
        JSONObject r = new JSONObject();
        r.put("success", Boolean.FALSE);
        r.put("data", null);
        r.put("error", code);
        return r;
    }

    private void sendUnauthorized(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.getWriter().write("{\"success\":false,\"data\":null,\"error\":\"unauthorized\"}");
    }
}
