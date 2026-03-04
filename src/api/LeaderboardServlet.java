package api;

import database.DB;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.sql.*;

/**
 * GET /api/v1/leaderboard — Returns all players ranked by net_worth.
 * Used by the chat UI to populate recipient list and by the leaderboard display.
 * Requires a valid Bearer token (enforced by AuthFilter).
 */
@WebServlet("/api/v1/leaderboard")
public class LeaderboardServlet extends HttpServlet {

    @Override
    @SuppressWarnings("unchecked")
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=utf-8");

        Integer playerId = (Integer) req.getAttribute("playerId");
        if (playerId == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"success\":false,\"data\":null,\"error\":\"unauthorized\"}");
            return;
        }

        try (Connection conn = DB.connect()) {
            JSONArray players = new JSONArray();

            String sql = """
                SELECT id, username, cash, net_worth, COALESCE(is_ai, FALSE) AS is_ai
                FROM players
                ORDER BY net_worth DESC
                LIMIT 100
                """;
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                int rank = 0;
                while (rs.next()) {
                    rank++;
                    JSONObject p = new JSONObject();
                    p.put("playerId", (long) rs.getInt("id"));
                    p.put("username", rs.getString("username"));
                    p.put("cash", rs.getDouble("cash"));
                    p.put("netWorth", rs.getDouble("net_worth"));
                    p.put("isAi", rs.getBoolean("is_ai"));
                    p.put("rank", (long) rank);
                    players.add(p);
                }
            }

            JSONObject data = new JSONObject();
            data.put("players", players);

            JSONObject result = new JSONObject();
            result.put("success", Boolean.TRUE);
            result.put("data", data);
            result.put("error", null);

            resp.setStatus(200);
            resp.getWriter().write(result.toJSONString());

        } catch (SQLException e) {
            System.err.println("[LEADERBOARD] DB error: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write("{\"success\":false,\"data\":null,\"error\":\"db_error\"}");
        }
    }
}
