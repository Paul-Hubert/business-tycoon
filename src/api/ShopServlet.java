package api;

import database.DB;
import simulation.ResourceRegistry;
import simulation.TickEngine;
import simulation.DemandEngine;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.sql.*;

/**
 * Shop (retail) management API.
 *
 *   POST /api/v1/shop/create   — Create a new shop (one per player allowed by default)
 *   POST /api/v1/shop/stock    — Transfer inventory from player to shop
 *   POST /api/v1/shop/price    — Set the sell price for a resource in a shop
 *   POST /api/v1/shop/unstock  — Retrieve inventory from shop back to player
 *   GET  /api/v1/shop/list     — List player's shops with inventory
 *   GET  /api/v1/shop/demand   — Demand forecast for consumer goods
 *
 * NPC customers buy from shops each tick (TickEngine step 4).
 * Prices above the demand cap receive no NPC purchases.
 * All endpoints require a valid Bearer token (enforced by AuthFilter).
 */
@WebServlet("/api/v1/shop/*")
public class ShopServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=utf-8");

        Integer playerId = (Integer) req.getAttribute("playerId");
        if (playerId == null) { sendUnauthorized(resp); return; }

        String path = req.getPathInfo();
        if (path == null) path = "";

        JSONObject body = parseBody(req);
        if (body == null) {
            resp.setStatus(400);
            resp.getWriter().write(error("invalid_json").toJSONString());
            return;
        }

        try (Connection conn = DB.connect()) {
            conn.setAutoCommit(false);
            try {
                JSONObject result = switch (path) {
                    case "/create"  -> handleCreate(conn, playerId, body);
                    case "/stock"   -> handleStock(conn, playerId, body);
                    case "/price"   -> handlePrice(conn, playerId, body);
                    case "/unstock" -> handleUnstock(conn, playerId, body);
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
                System.err.println("[SHOP] POST error: " + e.getMessage());
                resp.setStatus(500);
                resp.getWriter().write(error("internal_error").toJSONString());
            }
        } catch (SQLException e) {
            System.err.println("[SHOP] DB error: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write(error("db_error").toJSONString());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=utf-8");

        Integer playerId = (Integer) req.getAttribute("playerId");
        if (playerId == null) { sendUnauthorized(resp); return; }

        String path = req.getPathInfo();
        if (path == null) path = "";

        try (Connection conn = DB.connect()) {
            switch (path) {
                case "/list"   -> handleList(conn, playerId, resp);
                case "/demand" -> handleDemand(resp);
                default -> {
                    resp.setStatus(404);
                    resp.getWriter().write(error("not_found").toJSONString());
                }
            }
        } catch (Exception e) {
            System.err.println("[SHOP] GET error: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write(error("internal_error").toJSONString());
        }
    }

    // ── POST handlers ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private JSONObject handleCreate(Connection conn, int playerId, JSONObject body) throws Exception {
        String shopName = body.containsKey("shop_name") ? (String) body.get("shop_name") : "My Shop";
        if (shopName == null || shopName.isBlank()) shopName = "My Shop";

        String sql = "INSERT INTO shops (player_id, shop_name) VALUES (?, ?)";
        long shopId;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, playerId);
            ps.setString(2, shopName.trim());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                shopId = keys.getLong(1);
            }
        }

        JSONObject result = new JSONObject();
        result.put("success",   Boolean.TRUE);
        result.put("shop_id",   shopId);
        result.put("shop_name", shopName.trim());
        return result;
    }

    @SuppressWarnings("unchecked")
    private JSONObject handleStock(Connection conn, int playerId, JSONObject body) throws Exception {
        int    shopId       = getInt(body, "shop_id");
        String resourceName = getString(body, "resource");
        int    quantity     = getInt(body, "quantity");

        if (quantity <= 0)                            throw new IllegalArgumentException("quantity_must_be_positive");
        if (!ResourceRegistry.exists(resourceName))   throw new IllegalArgumentException("unknown_resource");

        // Verify shop ownership
        verifyShopOwner(conn, shopId, playerId);

        // Verify player has enough inventory
        String invSql = "SELECT quantity FROM inventory WHERE player_id = ? AND resource_name = ?";
        double availableQty;
        try (PreparedStatement ps = conn.prepareStatement(invSql)) {
            ps.setInt(1, playerId);
            ps.setString(2, resourceName);
            try (ResultSet rs = ps.executeQuery()) {
                availableQty = rs.next() ? rs.getDouble("quantity") : 0.0;
            }
        }
        if (availableQty < quantity) throw new IllegalStateException("insufficient_inventory");

        // Deduct from player inventory
        String deductSql = "UPDATE inventory SET quantity = GREATEST(0, quantity - ?) WHERE player_id = ? AND resource_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(deductSql)) {
            ps.setDouble(1, quantity);
            ps.setInt(2, playerId);
            ps.setString(3, resourceName);
            ps.executeUpdate();
        }

        // Add to shop inventory
        String upsertSql = """
            INSERT INTO shop_inventory (shop_id, resource_name, quantity)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)
            """;
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setInt(1, shopId);
            ps.setString(2, resourceName);
            ps.setInt(3, quantity);
            ps.executeUpdate();
        }

        JSONObject result = new JSONObject();
        result.put("success",  Boolean.TRUE);
        result.put("shop_id",  (long) shopId);
        result.put("resource", resourceName);
        result.put("stocked",  (long) quantity);
        return result;
    }

    @SuppressWarnings("unchecked")
    private JSONObject handlePrice(Connection conn, int playerId, JSONObject body) throws Exception {
        int    shopId       = getInt(body, "shop_id");
        String resourceName = getString(body, "resource");
        Object priceObj     = body.get("price");

        if (priceObj == null)                          throw new IllegalArgumentException("missing_price");
        if (!ResourceRegistry.exists(resourceName))    throw new IllegalArgumentException("unknown_resource");
        double price = ((Number) priceObj).doubleValue();
        if (price < 0)                                 throw new IllegalArgumentException("price_must_be_non_negative");

        verifyShopOwner(conn, shopId, playerId);

        String sql = """
            INSERT INTO shop_inventory (shop_id, resource_name, quantity, set_price)
            VALUES (?, ?, 0, ?)
            ON DUPLICATE KEY UPDATE set_price = VALUES(set_price)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shopId);
            ps.setString(2, resourceName);
            ps.setDouble(3, price);
            ps.executeUpdate();
        }

        JSONObject result = new JSONObject();
        result.put("success",  Boolean.TRUE);
        result.put("shop_id",  (long) shopId);
        result.put("resource", resourceName);
        result.put("price",    price);
        return result;
    }

    @SuppressWarnings("unchecked")
    private JSONObject handleUnstock(Connection conn, int playerId, JSONObject body) throws Exception {
        int    shopId       = getInt(body, "shop_id");
        String resourceName = getString(body, "resource");
        int    quantity     = getInt(body, "quantity");

        if (quantity <= 0)                           throw new IllegalArgumentException("quantity_must_be_positive");
        if (!ResourceRegistry.exists(resourceName))  throw new IllegalArgumentException("unknown_resource");

        verifyShopOwner(conn, shopId, playerId);

        // Check shop has enough
        String checkSql = "SELECT quantity FROM shop_inventory WHERE shop_id = ? AND resource_name = ?";
        int shopQty;
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setInt(1, shopId);
            ps.setString(2, resourceName);
            try (ResultSet rs = ps.executeQuery()) {
                shopQty = rs.next() ? rs.getInt("quantity") : 0;
            }
        }
        int actual = Math.min(quantity, shopQty);
        if (actual <= 0) throw new IllegalStateException("no_shop_inventory");

        // Deduct from shop
        String deductSql = "UPDATE shop_inventory SET quantity = GREATEST(0, quantity - ?) WHERE shop_id = ? AND resource_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(deductSql)) {
            ps.setInt(1, actual);
            ps.setInt(2, shopId);
            ps.setString(3, resourceName);
            ps.executeUpdate();
        }

        // Return to player inventory
        String returnSql = """
            INSERT INTO inventory (player_id, resource_name, quantity)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)
            """;
        try (PreparedStatement ps = conn.prepareStatement(returnSql)) {
            ps.setInt(1, playerId);
            ps.setString(2, resourceName);
            ps.setInt(3, actual);
            ps.executeUpdate();
        }

        JSONObject result = new JSONObject();
        result.put("success",   Boolean.TRUE);
        result.put("shop_id",   (long) shopId);
        result.put("resource",  resourceName);
        result.put("returned",  (long) actual);
        return result;
    }

    // ── GET handlers ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleList(Connection conn, int playerId, HttpServletResponse resp) throws Exception {
        JSONArray shops = new JSONArray();

        String shopSql = "SELECT id, shop_name, created_at FROM shops WHERE player_id = ? ORDER BY id";
        try (PreparedStatement ps = conn.prepareStatement(shopSql)) {
            ps.setInt(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int shopId = rs.getInt("id");

                    JSONObject shop = new JSONObject();
                    shop.put("id",         (long) shopId);
                    shop.put("name",       rs.getString("shop_name"));
                    shop.put("created_at", rs.getString("created_at"));

                    // Fetch inventory for this shop
                    JSONArray inventory = new JSONArray();
                    String invSql = "SELECT resource_name, quantity, set_price FROM shop_inventory WHERE shop_id = ? ORDER BY resource_name";
                    try (PreparedStatement ips = conn.prepareStatement(invSql)) {
                        ips.setInt(1, shopId);
                        try (ResultSet irs = ips.executeQuery()) {
                            while (irs.next()) {
                                JSONObject item = new JSONObject();
                                item.put("resource",  irs.getString("resource_name"));
                                item.put("quantity",  (long) irs.getInt("quantity"));
                                item.put("set_price", irs.getObject("set_price"));
                                inventory.add(item);
                            }
                        }
                    }
                    shop.put("inventory", inventory);
                    shops.add(shop);
                }
            }
        }

        JSONObject data = new JSONObject();
        data.put("shops", shops);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(success(data).toJSONString());
    }

    @SuppressWarnings("unchecked")
    private void handleDemand(HttpServletResponse resp) throws Exception {
        DemandEngine demand = DemandEngine.getInstance();
        int tick = TickEngine.getInstance().getCurrentTick();

        JSONObject forecasts = new JSONObject();
        for (ResourceRegistry.Resource resource : ResourceRegistry.allResources()) {
            if (!resource.isConsumerGood()) continue;
            java.util.Map<String, Object> raw = demand.getDemandForecast(resource.name, tick);
            // Convert Map to JSONObject for proper json-simple serialization
            JSONObject f = new JSONObject();
            raw.forEach((k, v) -> f.put(k, v));
            forecasts.put(resource.name, f);
        }

        JSONObject data = new JSONObject();
        data.put("tick",      (long) tick);
        data.put("forecasts", forecasts);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(success(data).toJSONString());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void verifyShopOwner(Connection conn, int shopId, int playerId) throws Exception {
        String sql = "SELECT id FROM shops WHERE id = ? AND player_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shopId);
            ps.setInt(2, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("shop_not_found");
            }
        }
    }

    private int getInt(JSONObject body, String key) {
        Object val = body.get(key);
        if (val == null) throw new IllegalArgumentException("missing_" + key);
        return ((Number) val).intValue();
    }

    private String getString(JSONObject body, String key) {
        Object val = body.get(key);
        if (val == null || val.toString().isBlank()) throw new IllegalArgumentException("missing_" + key);
        return val.toString().trim();
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
