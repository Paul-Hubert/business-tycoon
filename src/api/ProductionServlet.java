package api;

import config.ConfigManager;
import database.DB;
import simulation.ResourceRegistry;
import simulation.ResourceRegistry.Resource;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.sql.*;

/**
 * Manages player facilities (production buildings).
 *
 *   POST /api/v1/production/build      — Build a new facility for a resource
 *   POST /api/v1/production/idle       — Idle a facility (30% operating cost, no output)
 *   POST /api/v1/production/activate   — Re-activate an idle facility
 *   POST /api/v1/production/downsize   — Permanently remove a facility (40% cash refund)
 *
 * All endpoints require a valid Bearer token (enforced by AuthFilter).
 */
@WebServlet("/api/v1/production/*")
public class ProductionServlet extends HttpServlet {

    @Override
    @SuppressWarnings("unchecked")
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=utf-8");

        Integer playerId = (Integer) req.getAttribute("playerId");
        if (playerId == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write(error("unauthorized").toJSONString());
            return;
        }

        String action = req.getPathInfo(); // /build, /idle, /activate, /downsize
        if (action == null) action = "";

        JSONObject body = parseBody(req);
        if (body == null) {
            resp.setStatus(400);
            resp.getWriter().write(error("invalid_json").toJSONString());
            return;
        }

        try (Connection conn = DB.connect()) {
            conn.setAutoCommit(false);
            try {
                JSONObject result = switch (action) {
                    case "/build"    -> handleBuild(conn, playerId, body);
                    case "/idle"     -> handleIdle(conn, playerId, body);
                    case "/activate" -> handleActivate(conn, playerId, body);
                    case "/downsize" -> handleDownsize(conn, playerId, body);
                    default -> throw new IllegalArgumentException("unknown_action");
                };
                conn.commit();
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(result.toJSONString());
            } catch (IllegalArgumentException | IllegalStateException e) {
                conn.rollback();
                resp.setStatus(400);
                resp.getWriter().write(error(e.getMessage()).toJSONString());
            } catch (Exception e) {
                conn.rollback();
                System.err.println("[PRODUCTION] Error: " + e.getMessage());
                resp.setStatus(500);
                resp.getWriter().write(error("internal_error").toJSONString());
            }
        } catch (SQLException e) {
            System.err.println("[PRODUCTION] DB connection error: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write(error("db_error").toJSONString());
        }
    }

    // ── /build ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private JSONObject handleBuild(Connection conn, int playerId, JSONObject body) throws Exception {
        String resourceName = (String) body.get("resource");
        if (resourceName == null || resourceName.isBlank()) throw new IllegalArgumentException("missing_resource");
        if (!ResourceRegistry.exists(resourceName))         throw new IllegalArgumentException("unknown_resource");

        Resource resource  = ResourceRegistry.get(resourceName);
        double   buildCost = resource.getBuildCost();

        // Atomic cash deduction — WHERE clause acts as the race-condition guard
        String deductSql = "UPDATE players SET cash = cash - ? WHERE id = ? AND cash >= ?";
        int rows;
        try (PreparedStatement ps = conn.prepareStatement(deductSql)) {
            ps.setDouble(1, buildCost);
            ps.setInt(2, playerId);
            ps.setDouble(3, buildCost);
            rows = ps.executeUpdate();
        }
        if (rows == 0) throw new IllegalStateException("insufficient_cash");

        // Insert facility
        String insertSql = """
            INSERT INTO facilities (player_id, resource_name, state, production_capacity)
            VALUES (?, ?, 'active', ?)
            """;
        long facilityId;
        try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, playerId);
            ps.setString(2, resourceName);
            ps.setInt(3, resource.defaultProductionPerTick);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                facilityId = keys.getLong(1);
            }
        }

        JSONObject result = new JSONObject();
        result.put("success",     Boolean.TRUE);
        result.put("facility_id", facilityId);
        result.put("resource",    resourceName);
        result.put("cost",        buildCost);
        return result;
    }

    // ── /idle ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private JSONObject handleIdle(Connection conn, int playerId, JSONObject body) throws Exception {
        int facilityId = getFacilityId(body);
        int rows = updateFacilityState(conn, facilityId, playerId, "active", "idle");
        if (rows == 0) throw new IllegalArgumentException("facility_not_found_or_wrong_state");

        JSONObject result = new JSONObject();
        result.put("success",     Boolean.TRUE);
        result.put("facility_id", (long) facilityId);
        result.put("state",       "idle");
        return result;
    }

    // ── /activate ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private JSONObject handleActivate(Connection conn, int playerId, JSONObject body) throws Exception {
        int facilityId = getFacilityId(body);
        int rows = updateFacilityState(conn, facilityId, playerId, "idle", "active");
        if (rows == 0) throw new IllegalArgumentException("facility_not_found_or_wrong_state");

        JSONObject result = new JSONObject();
        result.put("success",     Boolean.TRUE);
        result.put("facility_id", (long) facilityId);
        result.put("state",       "active");
        return result;
    }

    // ── /downsize ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private JSONObject handleDownsize(Connection conn, int playerId, JSONObject body) throws Exception {
        int facilityId = getFacilityId(body);

        // Verify ownership and get resource name
        String checkSql = "SELECT resource_name FROM facilities WHERE id = ? AND player_id = ? AND state != 'destroyed'";
        String resourceName;
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setInt(1, facilityId);
            ps.setInt(2, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("facility_not_found");
                resourceName = rs.getString("resource_name");
            }
        }

        Resource resource  = ResourceRegistry.get(resourceName);
        double   refundRate = ConfigManager.getInstance().getDouble("facility.downsize_refund_rate", 0.40);
        double   refund    = (resource != null) ? resource.getBuildCost() * refundRate : 0.0;

        // Mark as destroyed
        String destroySql = "UPDATE facilities SET state = 'destroyed' WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(destroySql)) {
            ps.setInt(1, facilityId);
            ps.executeUpdate();
        }

        // Refund cash
        if (refund > 0) {
            String refundSql = "UPDATE players SET cash = cash + ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(refundSql)) {
                ps.setDouble(1, refund);
                ps.setInt(2, playerId);
                ps.executeUpdate();
            }
        }

        JSONObject result = new JSONObject();
        result.put("success",     Boolean.TRUE);
        result.put("facility_id", (long) facilityId);
        result.put("refund",      refund);
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private int getFacilityId(JSONObject body) {
        Object raw = body.get("facility_id");
        if (raw == null) throw new IllegalArgumentException("missing_facility_id");
        return ((Number) raw).intValue();
    }

    private int updateFacilityState(Connection conn, int facilityId, int playerId,
                                    String fromState, String toState) throws Exception {
        String sql = "UPDATE facilities SET state = ? WHERE id = ? AND player_id = ? AND state = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toState);
            ps.setInt(2, facilityId);
            ps.setInt(3, playerId);
            ps.setString(4, fromState);
            return ps.executeUpdate();
        }
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

    @SuppressWarnings("unchecked")
    private JSONObject error(String code) {
        JSONObject r = new JSONObject();
        r.put("success", Boolean.FALSE);
        r.put("data",    null);
        r.put("error",   code);
        return r;
    }
}
