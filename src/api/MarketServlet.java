package api;

import database.DB;
import simulation.ResourceRegistry;
import simulation.TickEngine;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.sql.*;

/**
 * Market order book API.
 *
 *   POST   /api/v1/market/order            — Place a buy or sell order
 *   DELETE /api/v1/market/order/{id}       — Cancel an open order
 *   GET    /api/v1/market/orderbook/{res}  — View current order book for a resource
 *   GET    /api/v1/market/history/{res}    — Price history for a resource
 *   GET    /api/v1/market/myorders         — Player's own open orders
 *
 * Orders are matched each tick by the TickEngine (step 5).
 * All endpoints require a valid Bearer token (enforced by AuthFilter).
 */
@WebServlet("/api/v1/market/*")
public class MarketServlet extends HttpServlet {

    // ── POST — place order or route sub-action ───────────────────────────────

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=utf-8");

        Integer playerId = (Integer) req.getAttribute("playerId");
        if (playerId == null) { sendUnauthorized(resp); return; }

        String path = req.getPathInfo();
        if (path == null) path = "";

        if (!path.equals("/order")) {
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

        try (Connection conn = DB.connect()) {
            conn.setAutoCommit(false);
            try {
                JSONObject result = handlePlaceOrder(conn, playerId, body);
                conn.commit();
                resp.setStatus(HttpServletResponse.SC_CREATED);
                resp.getWriter().write(result.toJSONString());
            } catch (IllegalArgumentException | IllegalStateException e) {
                conn.rollback();
                resp.setStatus(400);
                resp.getWriter().write(error(e.getMessage()).toJSONString());
            } catch (Exception e) {
                conn.rollback();
                System.err.println("[MARKET] Place order error: " + e.getMessage());
                resp.setStatus(500);
                resp.getWriter().write(error("internal_error").toJSONString());
            }
        } catch (SQLException e) {
            System.err.println("[MARKET] DB error: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write(error("db_error").toJSONString());
        }
    }

    // ── DELETE — cancel order ────────────────────────────────────────────────

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=utf-8");

        Integer playerId = (Integer) req.getAttribute("playerId");
        if (playerId == null) { sendUnauthorized(resp); return; }

        // Path: /order/{id}
        String path = req.getPathInfo();
        if (path == null || !path.startsWith("/order/")) {
            resp.setStatus(404);
            resp.getWriter().write(error("not_found").toJSONString());
            return;
        }

        int orderId;
        try {
            orderId = Integer.parseInt(path.substring("/order/".length()));
        } catch (NumberFormatException e) {
            resp.setStatus(400);
            resp.getWriter().write(error("invalid_order_id").toJSONString());
            return;
        }

        try (Connection conn = DB.connect()) {
            String sql = "DELETE FROM market_orders WHERE id = ? AND player_id = ?";
            int rows;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, orderId);
                ps.setInt(2, playerId);
                rows = ps.executeUpdate();
            }
            if (rows == 0) {
                resp.setStatus(404);
                resp.getWriter().write(error("order_not_found").toJSONString());
            } else {
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(success(null).toJSONString());
            }
        } catch (SQLException e) {
            System.err.println("[MARKET] Cancel order error: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write(error("db_error").toJSONString());
        }
    }

    // ── GET — orderbook, history, myorders ───────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=utf-8");

        Integer playerId = (Integer) req.getAttribute("playerId");
        if (playerId == null) { sendUnauthorized(resp); return; }

        String path = req.getPathInfo();
        if (path == null) path = "";

        try (Connection conn = DB.connect()) {
            if (path.startsWith("/orderbook/")) {
                String resource = path.substring("/orderbook/".length());
                handleOrderbook(conn, resource, resp);
            } else if (path.startsWith("/history/")) {
                String resource = path.substring("/history/".length());
                handleHistory(conn, resource, resp);
            } else if (path.equals("/myorders")) {
                handleMyOrders(conn, playerId, resp);
            } else if (path.equals("/prices")) {
                handleMarketPrices(conn, resp);
            } else {
                resp.setStatus(404);
                resp.getWriter().write(error("not_found").toJSONString());
            }
        } catch (SQLException e) {
            System.err.println("[MARKET] GET error: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write(error("db_error").toJSONString());
        } catch (Exception e) {
            System.err.println("[MARKET] GET error: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write(error("internal_error").toJSONString());
        }
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private JSONObject handlePlaceOrder(Connection conn, int playerId, JSONObject body) throws Exception {
        String resourceName = (String) body.get("resource");
        String side         = (String) body.get("side");   // "buy" or "sell"

        if (resourceName == null || resourceName.isBlank()) throw new IllegalArgumentException("missing_resource");
        if (!ResourceRegistry.exists(resourceName))         throw new IllegalArgumentException("unknown_resource");
        if (!"buy".equals(side) && !"sell".equals(side))   throw new IllegalArgumentException("invalid_side");

        Object priceObj    = body.get("price");
        Object quantityObj = body.get("quantity");
        if (priceObj == null || quantityObj == null)        throw new IllegalArgumentException("missing_price_or_quantity");

        double price    = ((Number) priceObj).doubleValue();
        int    quantity = ((Number) quantityObj).intValue();
        if (price <= 0)    throw new IllegalArgumentException("price_must_be_positive");
        if (quantity <= 0) throw new IllegalArgumentException("quantity_must_be_positive");

        // Optional: reserve floor (sell) or target ceiling (buy)
        Integer keepReserve  = body.containsKey("keep_reserve")   ? ((Number) body.get("keep_reserve")).intValue()   : null;
        Integer targetQty    = body.containsKey("target_quantity") ? ((Number) body.get("target_quantity")).intValue() : null;

        String sql = """
            INSERT INTO market_orders
                (player_id, resource_name, side, price, quantity, quantity_filled, keep_reserve, target_quantity)
            VALUES (?, ?, ?, ?, ?, 0, ?, ?)
            """;
        long orderId;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, playerId);
            ps.setString(2, resourceName);
            ps.setString(3, side);
            ps.setDouble(4, price);
            ps.setInt(5, quantity);
            if (keepReserve != null) ps.setInt(6, keepReserve); else ps.setNull(6, Types.INTEGER);
            if (targetQty   != null) ps.setInt(7, targetQty);   else ps.setNull(7, Types.INTEGER);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                orderId = keys.getLong(1);
            }
        }

        JSONObject data = new JSONObject();
        data.put("orderId",  orderId);
        data.put("resource", resourceName);
        data.put("side",     side);
        data.put("price",    price);
        data.put("quantity", (long) quantity);
        return success(data);
    }

    @SuppressWarnings("unchecked")
    private void handleOrderbook(Connection conn, String resource, HttpServletResponse resp) throws Exception {
        if (!ResourceRegistry.exists(resource)) {
            resp.setStatus(400);
            resp.getWriter().write(error("unknown_resource").toJSONString());
            return;
        }

        JSONArray sells = new JSONArray();
        JSONArray buys  = new JSONArray();

        String sellSql = """
            SELECT price, SUM(quantity - quantity_filled) AS total_qty, COUNT(*) AS num_orders
            FROM market_orders
            WHERE resource_name = ? AND side = 'sell' AND quantity > quantity_filled
            GROUP BY price ORDER BY price ASC LIMIT 50
            """;
        try (PreparedStatement ps = conn.prepareStatement(sellSql)) {
            ps.setString(1, resource);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JSONObject entry = new JSONObject();
                    entry.put("price",      rs.getDouble("price"));
                    entry.put("quantity",   rs.getLong("total_qty"));
                    entry.put("num_orders", rs.getLong("num_orders"));
                    sells.add(entry);
                }
            }
        }

        String buySql = """
            SELECT price, SUM(quantity - quantity_filled) AS total_qty, COUNT(*) AS num_orders
            FROM market_orders
            WHERE resource_name = ? AND side = 'buy' AND quantity > quantity_filled
            GROUP BY price ORDER BY price DESC LIMIT 50
            """;
        try (PreparedStatement ps = conn.prepareStatement(buySql)) {
            ps.setString(1, resource);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JSONObject entry = new JSONObject();
                    entry.put("price",      rs.getDouble("price"));
                    entry.put("quantity",   rs.getLong("total_qty"));
                    entry.put("num_orders", rs.getLong("num_orders"));
                    buys.add(entry);
                }
            }
        }

        JSONObject data = new JSONObject();
        data.put("resource", resource);
        data.put("sells",    sells);
        data.put("buys",     buys);
        data.put("tick",     (long) TickEngine.getInstance().getCurrentTick());

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(success(data).toJSONString());
    }

    @SuppressWarnings("unchecked")
    private void handleHistory(Connection conn, String resource, HttpServletResponse resp) throws Exception {
        if (!ResourceRegistry.exists(resource)) {
            resp.setStatus(400);
            resp.getWriter().write(error("unknown_resource").toJSONString());
            return;
        }

        JSONArray history = new JSONArray();
        String sql = """
            SELECT tick_number, buy_price, sell_price, volume_traded, timestamp
            FROM price_history
            WHERE resource_name = ?
            ORDER BY tick_number DESC
            LIMIT 200
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, resource);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JSONObject entry = new JSONObject();
                    entry.put("tick",         (long) rs.getInt("tick_number"));
                    entry.put("buy_price",    rs.getDouble("buy_price"));
                    entry.put("sell_price",   rs.getDouble("sell_price"));
                    entry.put("volume",       (long) rs.getInt("volume_traded"));
                    entry.put("timestamp",    rs.getString("timestamp"));
                    history.add(entry);
                }
            }
        }

        JSONObject data = new JSONObject();
        data.put("resource", resource);
        data.put("history",  history);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(success(data).toJSONString());
    }

    @SuppressWarnings("unchecked")
    private void handleMyOrders(Connection conn, int playerId, HttpServletResponse resp) throws Exception {
        JSONArray orders = new JSONArray();
        String sql = """
            SELECT id, resource_name, side, price, quantity, quantity_filled,
                   keep_reserve, target_quantity, created_at
            FROM market_orders
            WHERE player_id = ?
            ORDER BY created_at DESC
            LIMIT 100
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JSONObject o = new JSONObject();
                    o.put("id",              (long) rs.getInt("id"));
                    o.put("resource",        rs.getString("resource_name"));
                    o.put("side",            rs.getString("side"));
                    o.put("price",           rs.getDouble("price"));
                    o.put("quantity",        (long) rs.getInt("quantity"));
                    o.put("quantity_filled", (long) rs.getInt("quantity_filled"));
                    o.put("keep_reserve",    rs.getObject("keep_reserve"));
                    o.put("target_quantity", rs.getObject("target_quantity"));
                    o.put("created_at",      rs.getString("created_at"));
                    orders.add(o);
                }
            }
        }

        JSONObject data = new JSONObject();
        data.put("orders", orders);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(success(data).toJSONString());
    }

    @SuppressWarnings("unchecked")
    private void handleMarketPrices(Connection conn, HttpServletResponse resp) throws Exception {
        // Return current market prices for all resources based on recent trades
        JSONObject prices = new JSONObject();

        String sql = """
            SELECT resource_name,
                   COALESCE(AVG((buy_price + sell_price) / 2), NULL) as avg_price
            FROM price_history
            WHERE timestamp >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
            AND buy_price IS NOT NULL
            AND sell_price IS NOT NULL
            GROUP BY resource_name
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String resource = rs.getString("resource_name");
                double avgPrice = rs.getDouble("avg_price");
                prices.put(resource, avgPrice);
            }
        }

        // Ensure all resources have a price (use base price if no recent trades)
        for (ResourceRegistry.Resource res : ResourceRegistry.allResources()) {
            if (!prices.containsKey(res.name)) {
                prices.put(res.name, res.basePrice);
            }
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(success(prices).toJSONString());
    }

    // ── Utility ──────────────────────────────────────────────────────────────

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

    private void sendUnauthorized(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.getWriter().write("{\"success\":false,\"data\":null,\"error\":\"unauthorized\"}");
    }
}
