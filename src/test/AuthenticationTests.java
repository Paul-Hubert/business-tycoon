package test;

import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Comprehensive tests for the authentication REST API.
 * Each test is isolated and starts with a clean database.
 */
public class AuthenticationTests extends RestTestBase {

    @Before
    public void setUp() throws Exception {
        resetDatabase();
    }

    /**
     * Test: Signup with valid credentials creates a player record.
     */
    @Test
    public void testSignupCreatesPlayer() throws Exception {
        JSONObject body = new JSONObject();
        body.put("username", "alice");
        body.put("password", "secret123");

        HttpResponse resp = post("/auth/signup", body, null);

        assertEquals(201, resp.status);
        assertTrue((Boolean) resp.json.get("success"));
        assertNull(resp.error());

        JSONObject data = (JSONObject) resp.data();
        assertTrue(((Number) data.get("playerId")).intValue() > 0);
        assertEquals("alice", data.get("username"));

        // Verify in database
        JSONObject player = queryOne("SELECT username, cash FROM players WHERE username = ?", "alice");
        assertNotNull(player);
        assertEquals("alice", player.get("username"));
    }

    /**
     * Test: Signup with missing username fails.
     */
    @Test
    public void testSignupMissingUsername() throws Exception {
        JSONObject body = new JSONObject();
        body.put("password", "secret123");

        HttpResponse resp = post("/auth/signup", body, null);

        assertEquals(400, resp.status);
        assertFalse((Boolean) resp.json.get("success"));
        assertEquals("missing_username", resp.error());
    }

    /**
     * Test: Signup with missing password fails.
     */
    @Test
    public void testSignupMissingPassword() throws Exception {
        JSONObject body = new JSONObject();
        body.put("username", "alice");

        HttpResponse resp = post("/auth/signup", body, null);

        assertEquals(400, resp.status);
        assertEquals("missing_password", resp.error());
    }

    /**
     * Test: Duplicate username fails with 409 Conflict.
     */
    @Test
    public void testSignupDuplicateUsername() throws Exception {
        JSONObject body = new JSONObject();
        body.put("username", "alice");
        body.put("password", "secret123");

        // First signup succeeds
        HttpResponse resp1 = post("/auth/signup", body, null);
        assertEquals(201, resp1.status);

        // Second signup with same username fails
        HttpResponse resp2 = post("/auth/signup", body, null);
        assertEquals(409, resp2.status);
        assertEquals("username_taken", resp2.error());
    }

    /**
     * Test: Login with valid credentials returns a token.
     */
    @Test
    public void testLoginReturnsToken() throws Exception {
        // Signup
        JSONObject signup = new JSONObject();
        signup.put("username", "alice");
        signup.put("password", "secret123");
        post("/auth/signup", signup, null);

        // Login
        JSONObject login = new JSONObject();
        login.put("username", "alice");
        login.put("password", "secret123");

        HttpResponse resp = post("/auth/login", login, null);

        assertEquals(200, resp.status);
        assertTrue((Boolean) resp.json.get("success"));

        JSONObject data = (JSONObject) resp.data();
        String token = (String) data.get("token");
        assertNotNull(token);
        assertTrue(token.length() > 0);
    }

    /**
     * Test: Login with incorrect password fails.
     */
    @Test
    public void testLoginIncorrectPassword() throws Exception {
        // Signup
        JSONObject signup = new JSONObject();
        signup.put("username", "alice");
        signup.put("password", "secret123");
        post("/auth/signup", signup, null);

        // Login with wrong password
        JSONObject login = new JSONObject();
        login.put("username", "alice");
        login.put("password", "wrongpassword");

        HttpResponse resp = post("/auth/login", login, null);

        assertEquals(401, resp.status);
        assertEquals("invalid_credentials", resp.error());
    }

    /**
     * Test: Login with non-existent username fails.
     */
    @Test
    public void testLoginNonExistentUser() throws Exception {
        JSONObject login = new JSONObject();
        login.put("username", "nonexistent");
        login.put("password", "anypassword");

        HttpResponse resp = post("/auth/login", login, null);

        assertEquals(401, resp.status);
        assertEquals("invalid_credentials", resp.error());
    }

    /**
     * Test: Valid token allows API access, invalid token fails.
     */
    @Test
    public void testTokenValidation() throws Exception {
        TestDataBuilder builder = new TestDataBuilder();
        builder.createPlayer("alice", "secret123");
        String validToken = builder.getToken();

        // Valid token should work
        HttpResponse resp1 = get("/state", validToken);
        assertEquals(200, resp1.status);

        // Invalid token should fail (AuthFilter rejects it)
        HttpResponse resp2 = get("/state", "invalid_token_xyz");
        assertEquals(401, resp2.status);
    }

    /**
     * Test: Logout invalidates the token.
     */
    @Test
    public void testLogoutInvalidatesToken() throws Exception {
        TestDataBuilder builder = new TestDataBuilder();
        builder.createPlayer("alice", "secret123");
        String token = builder.getToken();

        // Verify token works
        HttpResponse resp1 = get("/state", token);
        assertEquals(200, resp1.status);

        // Logout
        HttpResponse resp2 = post("/auth/logout", null, token);
        assertEquals(200, resp2.status);

        // Token should now be invalid
        HttpResponse resp3 = get("/state", token);
        assertEquals(401, resp3.status);
    }

    /**
     * Test: Each login revokes previous tokens (single-token limit).
     */
    @Test
    public void testLoginRevokesOldTokens() throws Exception {
        JSONObject signup = new JSONObject();
        signup.put("username", "alice");
        signup.put("password", "secret123");
        post("/auth/signup", signup, null);

        // First login
        JSONObject login = new JSONObject();
        login.put("username", "alice");
        login.put("password", "secret123");
        HttpResponse resp1 = post("/auth/login", login, null);
        String token1 = (String) ((JSONObject) resp1.data()).get("token");

        // Second login
        HttpResponse resp2 = post("/auth/login", login, null);
        String token2 = (String) ((JSONObject) resp2.data()).get("token");

        // First token should be invalid
        HttpResponse resp3 = get("/state", token1);
        assertEquals(401, resp3.status);

        // Second token should work
        HttpResponse resp4 = get("/state", token2);
        assertEquals(200, resp4.status);
    }

    /**
     * Test: Players start with correct initial cash (from config).
     */
    @Test
    public void testInitialCash() throws Exception {
        TestDataBuilder builder = new TestDataBuilder();
        builder.createPlayer("alice", "secret123");

        JSONObject player = queryOne(
            "SELECT cash FROM players WHERE id = ?",
            builder.getPlayerId()
        );
        assertNotNull(player);
        // Default from config: 1000.0
        double cash = ((Number) player.get("cash")).doubleValue();
        assertEquals("Initial cash should match config default", 1000.0, cash, 0.01);
    }
}
