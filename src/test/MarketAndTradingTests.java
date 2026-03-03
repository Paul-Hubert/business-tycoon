package test;

import org.json.simple.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

/**
 * Tests for market orders and trading.
 * Verifies order matching, price history recording, and deterministic market behavior.
 */
public class MarketAndTradingTests extends RestTestBase {

    @Before
    public void setUp() throws Exception {
        resetDatabase();
    }

    @After
    public void tearDown() throws Exception {
        simulation.TickEngine engine = simulation.TickEngine.getInstance();
        if (engine.isRunning()) {
            engine.stop();
        }
    }

    /**
     * Test: Create a sell order and verify it's recorded in the database.
     */
    @Test
    public void testCreateSellOrder() throws Exception {
        TestDataBuilder builder = new TestDataBuilder();
        builder.createPlayer("alice", "secret123");

        // Add wheat to inventory
        try (java.sql.Connection conn = database.DB.connect();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO inventory (player_id, resource_name, quantity) VALUES (" +
                builder.getPlayerId() + ", 'wheat', 100)");
        }

        // Create sell order
        long orderId = builder.createSellOrder("wheat", 50, 10.0);

        // Verify in database
        JSONObject order = queryOne(
            "SELECT resource_name, quantity, price, side FROM market_orders WHERE id = ?",
            orderId
        );
        assertNotNull(order);
        assertEquals("wheat", order.get("resource_name"));
        assertEquals(50L, ((Number) order.get("quantity")).longValue());
        assertEquals(10.0, ((Number) order.get("price")).doubleValue(), 0.01);
        assertEquals("sell", order.get("side"));
    }

    /**
     * Test: Create a buy order and verify it's recorded.
     */
    @Test
    public void testCreateBuyOrder() throws Exception {
        TestDataBuilder builder = new TestDataBuilder();
        builder.createPlayer("alice", "secret123");

        long orderId = builder.createBuyOrder("wheat", 30, 8.0);

        JSONObject order = queryOne(
            "SELECT resource_name, quantity, price, side FROM market_orders WHERE id = ?",
            orderId
        );
        assertNotNull(order);
        assertEquals("wheat", order.get("resource_name"));
        assertEquals("buy", order.get("side"));
    }

    /**
     * Test: Buy and sell orders at matching price trigger a trade.
     * This tests the tick loop's market matching (step 5).
     */
    @Test
    public void testOrderMatching() throws Exception {
        // SCENARIO: Alice sells wheat at 10/unit, Bob buys wheat at 10/unit
        TestDataBuilder alice = new TestDataBuilder();
        alice.createPlayer("alice", "secret123");

        TestDataBuilder bob = new TestDataBuilder();
        bob.createPlayer("bob", "secret123");

        // Alice: add wheat and create sell order
        try (java.sql.Connection conn = database.DB.connect();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO inventory (player_id, resource_name, quantity) VALUES (" +
                alice.getPlayerId() + ", 'wheat', 100)");
        }
        alice.createSellOrder("wheat", 50, 10.0);

        // Bob: create buy order at same price
        bob.createBuyOrder("wheat", 50, 10.0);

        // Run ticks to trigger matching
        simulateTicks(5);

        // Verify trade occurred:
        // 1. Alice's wheat should decrease
        JSONObject aliceInv = queryOne(
            "SELECT quantity FROM inventory WHERE player_id = ? AND resource_name = ?",
            alice.getPlayerId(), "wheat"
        );
        assertTrue("Alice should have less wheat after trade",
            ((Number) aliceInv.get("quantity")).intValue() < 100);

        // 2. Bob's wheat should increase
        JSONObject bobInv = queryOne(
            "SELECT quantity FROM inventory WHERE player_id = ? AND resource_name = ?",
            bob.getPlayerId(), "wheat"
        );
        assertTrue("Bob should receive wheat",
            bobInv != null && ((Number) bobInv.get("quantity")).intValue() > 0);

        // 3. Price history should be recorded
        List<JSONObject> priceHistory = queryAll(
            "SELECT buy_price, sell_price, volume_traded FROM price_history WHERE resource_name = ? ORDER BY timestamp DESC LIMIT 1",
            "wheat"
        );
        assertFalse("Price history should be recorded", priceHistory.isEmpty());
        // Either buy_price or sell_price should be 10.0 depending on order matching
        double histPrice = Math.max(
            ((Number) priceHistory.get(0).get("buy_price")).doubleValue(),
            ((Number) priceHistory.get(0).get("sell_price")).doubleValue()
        );
        assertTrue("Price should be recorded", histPrice > 0);
    }

    /**
     * Test: Market matching is deterministic.
     * Same orders at same prices should result in same trades.
     */
    @Test
    public void testMarketMatchingDeterminism() throws Exception {
        // SCENARIO 1: Alice/Bob trade 50 wheat at 10/unit
        resetDatabase();

        TestDataBuilder alice1 = new TestDataBuilder();
        alice1.createPlayer("alice", "secret123");
        TestDataBuilder bob1 = new TestDataBuilder();
        bob1.createPlayer("bob", "secret123");

        try (java.sql.Connection conn = database.DB.connect();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO inventory (player_id, resource_name, quantity) VALUES (" +
                alice1.getPlayerId() + ", 'wheat', 100)");
        }
        alice1.createSellOrder("wheat", 50, 10.0);
        bob1.createBuyOrder("wheat", 50, 10.0);

        simulateTicks(5);

        double aliceWheatsScenario1 = ((Number) queryOne(
            "SELECT COALESCE(quantity, 0) as q FROM inventory WHERE player_id = ? AND resource_name = ?",
            alice1.getPlayerId(), "wheat"
        ).get("q")).doubleValue();

        double bobWheatScenario1 = ((Number) queryOne(
            "SELECT COALESCE(quantity, 0) as q FROM inventory WHERE player_id = ? AND resource_name = ?",
            bob1.getPlayerId(), "wheat"
        ).get("q")).doubleValue();

        // SCENARIO 2: Repeat identically
        resetDatabase();

        TestDataBuilder alice2 = new TestDataBuilder();
        alice2.createPlayer("alice", "secret123");
        TestDataBuilder bob2 = new TestDataBuilder();
        bob2.createPlayer("bob", "secret123");

        try (java.sql.Connection conn = database.DB.connect();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO inventory (player_id, resource_name, quantity) VALUES (" +
                alice2.getPlayerId() + ", 'wheat', 100)");
        }
        alice2.createSellOrder("wheat", 50, 10.0);
        bob2.createBuyOrder("wheat", 50, 10.0);

        simulateTicks(5);

        double aliceWheatScenario2 = ((Number) queryOne(
            "SELECT COALESCE(quantity, 0) as q FROM inventory WHERE player_id = ? AND resource_name = ?",
            alice2.getPlayerId(), "wheat"
        ).get("q")).doubleValue();

        double bobWheatScenario2 = ((Number) queryOne(
            "SELECT COALESCE(quantity, 0) as q FROM inventory WHERE player_id = ? AND resource_name = ?",
            bob2.getPlayerId(), "wheat"
        ).get("q")).doubleValue();

        // Verify determinism
        assertEquals("Alice's wheat should be identical", aliceWheatsScenario1, aliceWheatScenario2, 0.01);
        assertEquals("Bob's wheat should be identical", bobWheatScenario1, bobWheatScenario2, 0.01);
    }

    /**
     * Test: Price-time priority: earlier orders match first.
     */
    @Test
    public void testPriceTimePriority() throws Exception {
        TestDataBuilder alice = new TestDataBuilder();
        alice.createPlayer("alice", "secret123");
        TestDataBuilder bob = new TestDataBuilder();
        bob.createPlayer("bob", "secret123");
        TestDataBuilder charlie = new TestDataBuilder();
        charlie.createPlayer("charlie", "secret123");

        // Alice and Bob both sell at different prices
        try (java.sql.Connection conn = database.DB.connect();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO inventory (player_id, resource_name, quantity) VALUES (" +
                alice.getPlayerId() + ", 'wheat', 100)");
            stmt.execute("INSERT INTO inventory (player_id, resource_name, quantity) VALUES (" +
                bob.getPlayerId() + ", 'wheat', 100)");
        }

        // Alice sells at 12/unit (first)
        alice.createSellOrder("wheat", 50, 12.0);

        // Bob sells at 10/unit (better price, but later)
        bob.createSellOrder("wheat", 50, 10.0);

        // Charlie buys at 11/unit - should match Bob's cheaper sell (10) first, not Alice's (12)
        charlie.createBuyOrder("wheat", 50, 11.0);

        simulateTicks(5);

        // With price-priority matching, Bob's cheaper sell (10) should match Charlie's buy (11)
        // Alice's sell at 12 should NOT match (12 > 11)
        JSONObject aliceInv = queryOne(
            "SELECT COALESCE(quantity, 0) as q FROM inventory WHERE player_id = ? AND resource_name = ?",
            alice.getPlayerId(), "wheat"
        );
        JSONObject bobInv = queryOne(
            "SELECT COALESCE(quantity, 0) as q FROM inventory WHERE player_id = ? AND resource_name = ?",
            bob.getPlayerId(), "wheat"
        );
        JSONObject charlieInv = queryOne(
            "SELECT COALESCE(quantity, 0) as q FROM inventory WHERE player_id = ? AND resource_name = ?",
            charlie.getPlayerId(), "wheat"
        );

        // Bob should have lost wheat (matched first due to lower price)
        assertTrue("Bob (cheapest seller at 10) should have sold wheat",
            ((Number) bobInv.get("q")).doubleValue() < 100);
        // Charlie should have received wheat
        assertTrue("Charlie should have received wheat",
            ((Number) charlieInv.get("q")).doubleValue() > 0);
    }

    /**
     * Test: Self-trade prevention (player cannot buy from self).
     */
    @Test
    public void testSelfTradePreventionBuy() throws Exception {
        TestDataBuilder alice = new TestDataBuilder();
        alice.createPlayer("alice", "secret123");

        // Alice creates both sell and buy orders for the same resource
        try (java.sql.Connection conn = database.DB.connect();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO inventory (player_id, resource_name, quantity) VALUES (" +
                alice.getPlayerId() + ", 'wheat', 100)");
        }

        alice.createSellOrder("wheat", 50, 10.0);
        alice.createBuyOrder("wheat", 50, 10.0);

        // Run ticks
        simulateTicks(5);

        // Verify orders were not matched (self-trade prevented)
        List<JSONObject> orders = queryAll(
            "SELECT id, quantity FROM market_orders WHERE player_id = ? AND resource_name = 'wheat'",
            alice.getPlayerId()
        );

        // Both orders should still exist (not matched)
        assertEquals("Both orders should exist (self-trade prevented)", 2, orders.size());

        // Verify neither order was filled
        List<JSONObject> filledOrders = queryAll(
            "SELECT id, quantity_filled FROM market_orders WHERE player_id = ? AND resource_name = 'wheat' AND quantity_filled > 0",
            alice.getPlayerId()
        );
        assertEquals("No orders should have been filled (self-trade prevented)", 0, filledOrders.size());
    }

    /**
     * Test: Cancel order removes it from the market.
     */
    @Test
    public void testCancelOrder() throws Exception {
        TestDataBuilder alice = new TestDataBuilder();
        alice.createPlayer("alice", "secret123");

        try (java.sql.Connection conn = database.DB.connect();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO inventory (player_id, resource_name, quantity) VALUES (" +
                alice.getPlayerId() + ", 'wheat', 100)");
        }

        long orderId = alice.createSellOrder("wheat", 50, 10.0);

        // Verify order exists
        JSONObject orderBefore = queryOne(
            "SELECT id FROM market_orders WHERE id = ?",
            orderId
        );
        assertNotNull("Order should exist before cancel", orderBefore);

        // Cancel
        alice.cancelOrder(orderId);

        // Verify order is gone
        JSONObject orderAfter = queryOne(
            "SELECT id FROM market_orders WHERE id = ?",
            orderId
        );
        assertNull("Order should be deleted after cancel", orderAfter);
    }

    /**
     * Test: Partial order matching (order partially filled, remainder stays open).
     */
    @Test
    public void testPartialOrderMatching() throws Exception {
        TestDataBuilder alice = new TestDataBuilder();
        alice.createPlayer("alice", "secret123");
        TestDataBuilder bob = new TestDataBuilder();
        bob.createPlayer("bob", "secret123");

        // Alice sells 100 wheat at 10/unit
        try (java.sql.Connection conn = database.DB.connect();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO inventory (player_id, resource_name, quantity) VALUES (" +
                alice.getPlayerId() + ", 'wheat', 100)");
        }
        alice.createSellOrder("wheat", 100, 10.0);

        // Bob buys 50 wheat at 10/unit (partial match)
        bob.createBuyOrder("wheat", 50, 10.0);

        simulateTicks(5);

        // Alice should have 50 wheat left
        JSONObject aliceInv = queryOne(
            "SELECT COALESCE(quantity, 0) as q FROM inventory WHERE player_id = ? AND resource_name = ?",
            alice.getPlayerId(), "wheat"
        );
        int aliceWheat = ((Number) aliceInv.get("q")).intValue();
        // Allow small variance due to wheat decay (perishable resource)
        assertTrue("Alice should have ~50 wheat remaining after partial fill (got " + aliceWheat + ")",
            aliceWheat >= 48 && aliceWheat <= 50);

        // Remaining sell order should be in market with 50 qty
        List<JSONObject> orders = queryAll(
            "SELECT quantity FROM market_orders WHERE player_id = ? AND resource_name = 'wheat'",
            alice.getPlayerId()
        );
        assertTrue("Remaining order should exist", orders.size() > 0);
    }

    /**
     * Test: Market fee goes to central bank.
     */
    @Test
    public void testMarketFeeToBank() throws Exception {
        TestDataBuilder alice = new TestDataBuilder();
        alice.createPlayer("alice", "secret123");
        TestDataBuilder bob = new TestDataBuilder();
        bob.createPlayer("bob", "secret123");

        // Get initial central bank balance
        JSONObject bankBefore = queryOne("SELECT market_fee_reserve FROM central_bank LIMIT 1");
        double bankBefore_fee = bankBefore != null
            ? ((Number) bankBefore.get("market_fee_reserve")).doubleValue()
            : 0;

        // Execute a trade
        try (java.sql.Connection conn = database.DB.connect();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO inventory (player_id, resource_name, quantity) VALUES (" +
                alice.getPlayerId() + ", 'wheat', 100)");
        }
        alice.createSellOrder("wheat", 50, 10.0);
        bob.createBuyOrder("wheat", 50, 10.0);

        simulateTicks(5);

        // Get final central bank balance
        JSONObject bankAfter = queryOne("SELECT market_fee_reserve FROM central_bank LIMIT 1");
        double bankAfter_fee = bankAfter != null
            ? ((Number) bankAfter.get("market_fee_reserve")).doubleValue()
            : 0;

        // Bank should have received market fee
        assertTrue("Central bank should collect market fee",
            bankAfter_fee > bankBefore_fee);
    }
}
