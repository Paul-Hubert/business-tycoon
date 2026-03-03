package api;

import config.ConfigManager;
import database.DB;
import database.PasswordHash;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.sql.*;
import java.util.UUID;

/**
 * Authentication endpoints for the REST API.
 *
 *   POST /api/v1/auth/signup  — Create a new player account
 *   POST /api/v1/auth/login   — Authenticate and receive a Bearer token
 *   POST /api/v1/auth/logout  — Invalidate the current Bearer token
 *
 * All endpoints use the 'players' table (separate from the legacy 'users' table).
 * Tokens are UUIDs stored in 'auth_tokens' with a configurable expiry.
 */
@WebServlet("/api/v1/auth/*")
public class AuthServlet extends HttpServlet {

    @Override
    @SuppressWarnings("unchecked")
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=utf-8");

        String pathInfo = req.getPathInfo();  // e.g., "/signup"
        if (pathInfo == null) pathInfo = "";

        switch (pathInfo) {
            case "/signup":  handleSignup(req, resp); break;
            case "/login":   handleLogin(req, resp);  break;
            case "/logout":  handleLogout(req, resp); break;
            default:
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                write(resp, error("not_found"));
        }
    }

    // ── Signup ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleSignup(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JSONObject body = parseBody(req);
        if (body == null) { resp.setStatus(400); write(resp, error("invalid_json")); return; }

        String username = (String) body.get("username");
        String password = (String) body.get("password");

        if (username == null || username.trim().isEmpty()) {
            resp.setStatus(400); write(resp, error("missing_username")); return;
        }
        if (password == null || password.trim().isEmpty()) {
            resp.setStatus(400); write(resp, error("missing_password")); return;
        }

        username = username.trim();
        double startingCash = ConfigManager.getInstance().getDouble("economy.starting_cash", 1000.0);
        String hash;
        try { hash = hexHash(password); }
        catch (Exception e) { resp.setStatus(500); write(resp, error("hash_error")); return; }

        String sql = "INSERT INTO players (username, password_hash, cash, net_worth) VALUES (?, ?, ?, ?)";
        try (Connection conn = DB.connect();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setDouble(3, startingCash);
            ps.setDouble(4, startingCash);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int playerId = keys.getInt(1);
                    JSONObject data = new JSONObject();
                    data.put("playerId", (long) playerId);
                    data.put("username", username);
                    resp.setStatus(HttpServletResponse.SC_CREATED);
                    write(resp, success(data));
                    return;
                }
            }
            resp.setStatus(500); write(resp, error("create_failed"));

        } catch (SQLIntegrityConstraintViolationException e) {
            resp.setStatus(409); write(resp, error("username_taken"));
        } catch (SQLException e) {
            System.err.println("[AUTH] Signup error: " + e.getMessage());
            resp.setStatus(500); write(resp, error("db_error"));
        }
    }

    // ── Login ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JSONObject body = parseBody(req);
        if (body == null) { resp.setStatus(400); write(resp, error("invalid_json")); return; }

        String username = (String) body.get("username");
        String password = (String) body.get("password");

        if (username == null || password == null) {
            resp.setStatus(400); write(resp, error("missing_credentials")); return;
        }

        String hash;
        try { hash = hexHash(password); }
        catch (Exception e) { resp.setStatus(500); write(resp, error("hash_error")); return; }

        String sql = "SELECT id FROM players WHERE username = ? AND password_hash = ?";
        try (Connection conn = DB.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ps.setString(2, hash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    resp.setStatus(401); write(resp, error("invalid_credentials")); return;
                }
                int playerId = rs.getInt("id");
                String token = issueToken(conn, playerId);

                JSONObject data = new JSONObject();
                data.put("token", token);
                data.put("playerId", (long) playerId);
                resp.setStatus(HttpServletResponse.SC_OK);
                write(resp, success(data));
            }
        } catch (SQLException e) {
            System.err.println("[AUTH] Login error: " + e.getMessage());
            resp.setStatus(500); write(resp, error("db_error"));
        }
    }

    // ── Logout ─────────────────────────────────────────────────────────────

    private void handleLogout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String token = extractBearerToken(req);
        if (token == null) { resp.setStatus(400); write(resp, error("missing_token")); return; }

        String sql = "DELETE FROM auth_tokens WHERE token = ?";
        try (Connection conn = DB.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.executeUpdate();
            write(resp, success(null));
        } catch (SQLException e) {
            System.err.println("[AUTH] Logout error: " + e.getMessage());
            resp.setStatus(500); write(resp, error("db_error"));
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String hexHash(String password) throws Exception {
        byte[] bytes = PasswordHash.hash(password);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String issueToken(Connection conn, int playerId) throws SQLException {
        // Revoke any existing tokens for this player
        try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM auth_tokens WHERE player_id = ?")) {
            del.setInt(1, playerId);
            del.executeUpdate();
        }

        int expiryHours = ConfigManager.getInstance().getInt("auth.token_expiry_hours", 168);
        String token = UUID.randomUUID().toString().replace("-", "");

        String sql = "INSERT INTO auth_tokens (player_id, token, expires_at) " +
                     "VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? HOUR))";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            ps.setString(2, token);
            ps.setInt(3, expiryHours);
            ps.executeUpdate();
        }
        return token;
    }

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

    private String extractBearerToken(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7).trim();
        return null;
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

    private void write(HttpServletResponse resp, JSONObject json) throws IOException {
        resp.getWriter().write(json.toJSONString());
    }
}
