package api;

import database.DB;
import simulation.TickEngine;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.sql.*;

/**
 * GET /api/v1/state
 *
 * Returns the authenticated player's full game state:
 *   - Player info (id, username, cash, net_worth)
 *   - Inventory (resource -> quantity)
 *   - Facilities list
 *   - Current tick number
 *
 * Requires a valid Bearer token (enforced by AuthFilter).
 */
@WebServlet("/api/v1/state")
public class StateServlet extends HttpServlet {

    @Override
    @SuppressWarnings("unchecked")
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=utf-8");

        Integer playerId = (Integer) req.getAttribute("playerId");
        if (playerId == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            write(resp, error("unauthorized"));
            return;
        }

        try (Connection conn = DB.connect()) {
            JSONObject data = new JSONObject();

            // ── Player info ────────────────────────────────────────────
            String playerSql = "SELECT id, username, cash, net_worth FROM players WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(playerSql)) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        write(resp, error("player_not_found"));
                        return;
                    }
                    data.put("playerId",  (long) rs.getInt("id"));
                    data.put("username",  rs.getString("username"));
                    data.put("cash",      rs.getDouble("cash"));
                    data.put("net_worth", rs.getDouble("net_worth"));
                }
            }

            // ── Inventory ──────────────────────────────────────────────
            JSONObject inventory = new JSONObject();
            String invSql = "SELECT resource_name, quantity FROM inventory WHERE player_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(invSql)) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        inventory.put(rs.getString("resource_name"), rs.getDouble("quantity"));
                    }
                }
            }
            data.put("inventory", inventory);

            // ── Facilities ─────────────────────────────────────────────
            JSONArray facilities = new JSONArray();
            String facSql = "SELECT id, resource_name, state, production_capacity, created_at " +
                            "FROM facilities WHERE player_id = ? ORDER BY id";
            try (PreparedStatement ps = conn.prepareStatement(facSql)) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        JSONObject f = new JSONObject();
                        f.put("id",                  (long) rs.getInt("id"));
                        f.put("resource_name",       rs.getString("resource_name"));
                        f.put("state",               rs.getString("state"));
                        f.put("production_capacity", (long) rs.getInt("production_capacity"));
                        f.put("created_at",          rs.getString("created_at"));
                        facilities.add(f);
                    }
                }
            }
            data.put("facilities", facilities);

            // ── Current tick ───────────────────────────────────────────
            data.put("current_tick", (long) TickEngine.getInstance().getCurrentTick());

            resp.setStatus(HttpServletResponse.SC_OK);
            write(resp, success(data));

        } catch (SQLException e) {
            System.err.println("[STATE] DB error: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            write(resp, error("db_error"));
        }
    }

    @SuppressWarnings("unchecked")
    private JSONObject success(Object data) {
        JSONObject r = new JSONObject();
        r.put("success", Boolean.TRUE);
        r.put("data",    data);
        r.put("error",   null);
        return r;
    }

    @SuppressWarnings("unchecked")
    private JSONObject error(String code) {
        JSONObject r = new JSONObject();
        r.put("success", Boolean.FALSE);
        r.put("data",    null);
        r.put("error",   code);
        return r;
    }

    private void write(HttpServletResponse resp, JSONObject json) throws IOException {
        resp.getWriter().write(json.toJSONString());
    }
}
