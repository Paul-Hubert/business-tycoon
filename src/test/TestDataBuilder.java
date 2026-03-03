package test;

import org.json.simple.JSONObject;
import static org.junit.Assert.*;

/**
 * Fluent builder for creating test data via the REST API.
 * Every test can call these methods to set up players, facilities, and orders.
 */
public class TestDataBuilder extends RestTestBase {

    private String token;
    private int playerId;
    private String username;

    // ── Player Setup ────────────────────────────────────────────────────────────

    /**
     * Sign up a new player.
     * @param username unique username
     * @param password plain text password
     * @return this builder for chaining
     */
    public TestDataBuilder signUp(String username, String password) throws Exception {
        this.username = username;

        JSONObject body = new JSONObject();
        body.put("username", username);
        body.put("password", password);

        HttpResponse resp = post("/auth/signup", body, null);
        assertEquals("Signup failed: " + resp.rawBody, 201, resp.status);

        JSONObject data = (JSONObject) resp.data();
        this.playerId = ((Number) data.get("playerId")).intValue();

        return this;
    }

    /**
     * Log in the player (requires prior signUp).
     * @return this builder for chaining
     */
    public TestDataBuilder login() throws Exception {
        assertNotNull("Must call signUp first", username);

        JSONObject body = new JSONObject();
        body.put("username", username);
        body.put("password", ""); // Will be set by signUp

        // For simplicity, we'll just use the password from the previous signUp call
        // In a real scenario, you'd store it. For now, let's require explicit password.
        throw new UnsupportedOperationException(
            "Use login(String password) and ensure password matches prior signUp");
    }

    /**
     * Log in with explicit password.
     * @param password plain text password
     * @return this builder for chaining
     */
    public TestDataBuilder login(String password) throws Exception {
        assertNotNull("Must call signUp first", username);

        JSONObject body = new JSONObject();
        body.put("username", username);
        body.put("password", password);

        HttpResponse resp = post("/auth/login", body, null);
        assertEquals("Login failed: " + resp.rawBody, 200, resp.status);

        JSONObject data = (JSONObject) resp.data();
        this.token = (String) data.get("token");

        return this;
    }

    /**
     * Get the authentication token for this player.
     */
    public String getToken() {
        assertNotNull("Must call login first", token);
        return token;
    }

    /**
     * Get the player ID.
     */
    public int getPlayerId() {
        assertTrue("Must call signUp first", playerId > 0);
        return playerId;
    }

    // ── Facility Management ──────────────────────────────────────────────────────

    /**
     * Build a facility for a resource.
     * @param resourceKey e.g., "wheat", "bread"
     * @return facility ID
     */
    public int buildFacility(String resourceKey) throws Exception {
        assertNotNull("Must be logged in", token);

        JSONObject body = new JSONObject();
        body.put("resource", resourceKey);

        HttpResponse resp = post("/production/build", body, token);
        assertEquals("Build failed: " + resp.rawBody, 200, resp.status);

        JSONObject data = (JSONObject) resp.data();
        return ((Number) data.get("facilityId")).intValue();
    }

    /**
     * Idle a facility (reduce operating cost to 30%).
     * @param facilityId facility ID to idle
     */
    public TestDataBuilder idleFacility(int facilityId) throws Exception {
        assertNotNull("Must be logged in", token);

        JSONObject body = new JSONObject();
        body.put("facility_id", facilityId);

        HttpResponse resp = post("/production/idle", body, token);
        assertEquals("Idle failed: " + resp.rawBody, 200, resp.status);

        return this;
    }

    /**
     * Activate (un-idle) a facility.
     * @param facilityId facility ID to activate
     */
    public TestDataBuilder activateFacility(int facilityId) throws Exception {
        assertNotNull("Must be logged in", token);

        JSONObject body = new JSONObject();
        body.put("facility_id", facilityId);

        HttpResponse resp = post("/production/activate", body, token);
        assertEquals("Activate failed: " + resp.rawBody, 200, resp.status);

        return this;
    }

    // ── Market Orders ───────────────────────────────────────────────────────────

    /**
     * Create a sell order on the market.
     * @param resource resource key (e.g., "wheat")
     * @param quantity units to sell
     * @param pricePerUnit price per unit
     * @return order ID
     */
    public long createSellOrder(String resource, int quantity, double pricePerUnit) throws Exception {
        assertNotNull("Must be logged in", token);

        JSONObject body = new JSONObject();
        body.put("resource", resource);
        body.put("quantity", quantity);
        body.put("price", pricePerUnit);
        body.put("side", "sell");

        HttpResponse resp = post("/market/order", body, token);
        assertEquals("Create sell order failed: " + resp.rawBody, 201, resp.status);

        JSONObject data = (JSONObject) resp.data();
        return ((Number) data.get("orderId")).longValue();
    }

    /**
     * Create a buy order on the market.
     * @param resource resource key (e.g., "wheat")
     * @param quantity units to buy
     * @param pricePerUnit price per unit
     * @return order ID
     */
    public long createBuyOrder(String resource, int quantity, double pricePerUnit) throws Exception {
        assertNotNull("Must be logged in", token);

        JSONObject body = new JSONObject();
        body.put("resource", resource);
        body.put("quantity", quantity);
        body.put("price", pricePerUnit);
        body.put("side", "buy");

        HttpResponse resp = post("/market/order", body, token);
        assertEquals("Create buy order failed: " + resp.rawBody, 201, resp.status);

        JSONObject data = (JSONObject) resp.data();
        return ((Number) data.get("orderId")).longValue();
    }

    /**
     * Cancel an existing order.
     * @param orderId order ID to cancel
     */
    public TestDataBuilder cancelOrder(long orderId) throws Exception {
        assertNotNull("Must be logged in", token);

        HttpResponse resp = delete("/market/order/" + orderId, null, token);
        assertEquals("Cancel order failed: " + resp.rawBody, 200, resp.status);

        return this;
    }

    // ── Helper Methods ───────────────────────────────────────────────────────────

    /**
     * Create a fresh player in one call.
     * Calls signUp and login.
     * @param username unique username
     * @param password plain text password
     * @return this builder for chaining
     */
    public TestDataBuilder createPlayer(String username, String password) throws Exception {
        signUp(username, password);
        login(password);
        return this;
    }
}
