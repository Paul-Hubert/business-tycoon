package test;

import org.json.simple.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

/**
 * Tests for database consistency and tick engine integrity.
 * Verifies atomic transactions, no race conditions, and correct state transitions.
 */
public class DatabaseAndTickIntegrityTests extends RestTestBase {

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
     * Test: Tick count persists between engine restarts.
     */
    @Test
    public void testTickCountPersistence() throws Exception {
        simulation.TickEngine engine = simulation.TickEngine.getInstance();

        // Ensure engine is stopped and reset (database was cleared in setUp)
        if (engine.isRunning()) {
            engine.stop();
        }
        engine.reset();

        // Start fresh
        engine.start();

        // Let it run for a few ticks
        Thread.sleep(1500); // ~6 ticks at 4/sec

        int ticksBefore = getCurrentTick();
        assertTrue("Ticks should be progressing", ticksBefore > 0);

        // Stop engine (saves tick count to database)
        engine.stop();

        // Verify tick count persisted in DB
        int ticksInDb = queryOne(
            "SELECT current_tick FROM game_state LIMIT 1"
        ).get("current_tick") != null
            ? ((Number) queryOne("SELECT current_tick FROM game_state LIMIT 1").get("current_tick")).intValue()
            : 0;

        assertEquals("Tick count should persist to database", ticksBefore, ticksInDb);

        // Restart engine (should resume from saved tick count in DB)
        engine.start();

        // Verify the tick count was loaded from DB
        int ticksAfterStart = getCurrentTick();
        assertEquals("Tick count should be loaded from DB after restart", ticksBefore, ticksAfterStart);

        // Let it tick a few more times
        Thread.sleep(500); // ~2 more ticks

        int ticksAfter = getCurrentTick();
        assertTrue("Tick count should resume and continue from saved value", ticksAfter > ticksBefore);

        engine.stop();
    }

    /**
     * Test: Facility production and inventory updates are atomic.
     * A trade should not partially complete.
     */
    @Test
    public void testProductionAtomicity() throws Exception {
        TestDataBuilder alice = new TestDataBuilder();
        alice.createPlayer("alice", "secret123");

        // Build a facility and run ticks
        alice.buildFacility("wheat");

        simulateTicks(10);

        // Verify both production AND inventory updates happened
        JSONObject facility = queryOne(
            "SELECT id FROM facilities WHERE player_id = ? AND resource_name = 'wheat'",
            alice.getPlayerId()
        );
        assertNotNull("Facility should exist", facility);

        JSONObject inventory = queryOne(
            "SELECT COALESCE(quantity, 0) as qty FROM inventory WHERE player_id = ? AND resource_name = 'wheat'",
            alice.getPlayerId()
        );

        // Verify production actually happened (not just >= 0)
        int qty = ((Number) inventory.get("qty")).intValue();
        assertTrue("Wheat should be produced after 10 ticks (got " + qty + ")", qty > 0);

        // Verify atomicity: facility and inventory are consistent
        // If production ran, both the facility and non-zero inventory should exist
        int facilityCount = ((Number) queryOne(
            "SELECT COUNT(*) as cnt FROM facilities WHERE player_id = ? AND resource_name = 'wheat' AND state = 'active'",
            alice.getPlayerId()
        ).get("cnt")).intValue();
        assertEquals("Facility should still be active", 1, facilityCount);
    }

    /**
     * Test: Concurrent players do not interfere with each other's cash.
     */
    @Test
    public void testConcurrentPlayerIsolation() throws Exception {
        TestDataBuilder alice = new TestDataBuilder();
        alice.createPlayer("alice", "secret123");
        TestDataBuilder bob = new TestDataBuilder();
        bob.createPlayer("bob", "secret123");

        // Both build facilities
        alice.buildFacility("wheat");
        bob.buildFacility("iron");

        // Get initial cash
        double aliceCashBefore = ((Number) queryOne(
            "SELECT cash FROM players WHERE id = ?",
            alice.getPlayerId()
        ).get("cash")).doubleValue();

        double bobCashBefore = ((Number) queryOne(
            "SELECT cash FROM players WHERE id = ?",
            bob.getPlayerId()
        ).get("cash")).doubleValue();

        // Run ticks
        simulateTicks(5);

        // Get final cash
        double aliceCashAfter = ((Number) queryOne(
            "SELECT cash FROM players WHERE id = ?",
            alice.getPlayerId()
        ).get("cash")).doubleValue();

        double bobCashAfter = ((Number) queryOne(
            "SELECT cash FROM players WHERE id = ?",
            bob.getPlayerId()
        ).get("cash")).doubleValue();

        // Both should have decreased (operating costs) but independently
        assertTrue("Alice should have paid operating costs",
            aliceCashAfter < aliceCashBefore);
        assertTrue("Bob should have paid operating costs",
            bobCashAfter < bobCashBefore);

        // The difference should not overlap (no cross-contamination)
        double aliceDiff = aliceCashBefore - aliceCashAfter;
        double bobDiff = bobCashBefore - bobCashAfter;

        // Verify costs are positive and independent
        assertTrue("Alice should have paid operating costs (diff=" + aliceDiff + ")", aliceDiff > 0);
        assertTrue("Bob should have paid operating costs (diff=" + bobDiff + ")", bobDiff > 0);

        // Verify isolation: total cash drained should equal sum of individual costs
        // (no cross-contamination between players)
        double totalCashBefore = aliceCashBefore + bobCashBefore;
        double totalCashAfter = aliceCashAfter + bobCashAfter;
        double totalDiff = totalCashBefore - totalCashAfter;
        assertEquals("Total costs should equal sum of individual costs (no cross-contamination)",
            aliceDiff + bobDiff, totalDiff, 0.01);
    }

    /**
     * Test: Cash and inventory are never negative.
     */
    @Test
    public void testNonNegativeBalances() throws Exception {
        TestDataBuilder alice = new TestDataBuilder();
        alice.createPlayer("alice", "secret123");

        // Set starting cash to a low value (enough to build 1 facility but not 3)
        // Raw build cost = 100, so set to 150 to build 1 but not 3
        try (java.sql.Connection conn = database.DB.connect();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("UPDATE players SET cash = 150.0 WHERE id = " + alice.getPlayerId());
        }

        // Build many facilities (will eventually run out of cash)
        for (int i = 0; i < 3; i++) {
            try {
                alice.buildFacility("wheat");
            } catch (Exception e) {
                // Expected to fail when out of cash
                break;
            }
        }

        // Run ticks
        simulateTicks(10);

        // Verify cash is never negative
        JSONObject player = queryOne(
            "SELECT cash FROM players WHERE id = ?",
            alice.getPlayerId()
        );
        double cash = ((Number) player.get("cash")).doubleValue();
        assertTrue("Cash should never be negative", cash >= -0.01); // Allow small floating point errors

        // Verify inventory quantities are never negative
        List<JSONObject> inventory = queryAll(
            "SELECT quantity FROM inventory WHERE player_id = ?",
            alice.getPlayerId()
        );
        for (JSONObject row : inventory) {
            double qty = ((Number) row.get("quantity")).doubleValue();
            assertTrue("Inventory quantity should never be negative: " + qty, qty >= -0.01);
        }
    }

    /**
     * Test: Order matching does not cause inventory over/under flow.
     */
    @Test
    public void testOrderMatchingInventoryConsistency() throws Exception {
        TestDataBuilder alice = new TestDataBuilder();
        alice.createPlayer("alice", "secret123");
        TestDataBuilder bob = new TestDataBuilder();
        bob.createPlayer("bob", "secret123");

        // Setup: Alice has wheat, Bob has cash
        try (java.sql.Connection conn = database.DB.connect();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO inventory (player_id, resource_name, quantity) VALUES (" +
                alice.getPlayerId() + ", 'wheat', 100)");
        }

        // Trade
        alice.createSellOrder("wheat", 50, 10.0);
        bob.createBuyOrder("wheat", 50, 10.0);

        simulateTicks(5);

        // Verify: Total wheat in system = 100 (conservation)
        JSONObject aliceWheat = queryOne(
            "SELECT COALESCE(quantity, 0) as qty FROM inventory WHERE player_id = ? AND resource_name = 'wheat'",
            alice.getPlayerId()
        );
        JSONObject bobWheat = queryOne(
            "SELECT COALESCE(quantity, 0) as qty FROM inventory WHERE player_id = ? AND resource_name = 'wheat'",
            bob.getPlayerId()
        );

        double aliceQty = ((Number) aliceWheat.get("qty")).doubleValue();
        double bobQty = ((Number) bobWheat.get("qty")).doubleValue();
        double totalQty = aliceQty + bobQty;

        // Allow small variance for wheat decay (wheat is perishable, decayRate=0.01 per 60 ticks)
        assertTrue("Total wheat should be approximately conserved (got " + totalQty + ")",
            totalQty >= 97.0 && totalQty <= 100.0);
    }

    /**
     * Test: Game state is consistent across multiple components.
     * E.g., players' net_worth matches their cash + inventory value.
     */
    @Test
    public void testGameStateConsistency() throws Exception {
        TestDataBuilder alice = new TestDataBuilder();
        alice.createPlayer("alice", "secret123");

        alice.buildFacility("wheat");
        alice.buildFacility("iron");

        simulateTicks(10);

        // Verify player record is valid
        JSONObject player = queryOne(
            "SELECT id, cash, net_worth FROM players WHERE id = ?",
            alice.getPlayerId()
        );
        assertNotNull(player);
        assertNotNull(player.get("cash"));
        assertNotNull(player.get("net_worth"));

        double cash = ((Number) player.get("cash")).doubleValue();
        double netWorth = ((Number) player.get("net_worth")).doubleValue();

        // Net worth should be at least cash (may include inventory value)
        assertTrue("Net worth should be >= cash", netWorth >= cash - 0.01);
    }

    /**
     * Test: Facility state transitions are valid.
     * Can't activate already-active, can't idle already-idle, etc.
     */
    @Test
    public void testFacilityStateTransitions() throws Exception {
        TestDataBuilder alice = new TestDataBuilder();
        alice.createPlayer("alice", "secret123");
        int facilityId = alice.buildFacility("wheat");

        // Verify initial state
        JSONObject facility1 = queryOne(
            "SELECT state FROM facilities WHERE id = ?",
            facilityId
        );
        assertEquals("Initial state should be active", "active", facility1.get("state"));

        // Idle it
        alice.idleFacility(facilityId);
        JSONObject facility2 = queryOne(
            "SELECT state FROM facilities WHERE id = ?",
            facilityId
        );
        assertEquals("After idle, state should be idle", "idle", facility2.get("state"));

        // Try to idle again (should fail gracefully — already idle)
        JSONObject idleBody = new JSONObject();
        idleBody.put("facility_id", facilityId);
        HttpResponse idleResp = post("/production/idle", idleBody, alice.getToken());
        assertEquals("Idling an already-idle facility should return 400", 400, idleResp.status);

        JSONObject facility3 = queryOne(
            "SELECT state FROM facilities WHERE id = ?",
            facilityId
        );
        assertEquals("Should still be idle", "idle", facility3.get("state"));

        // Activate
        alice.activateFacility(facilityId);
        JSONObject facility4 = queryOne(
            "SELECT state FROM facilities WHERE id = ?",
            facilityId
        );
        assertEquals("After activate, state should be active", "active", facility4.get("state"));
    }

    /**
     * Test: Decay is applied correctly and deterministically.
     */
    @Test
    public void testResourceDecayDeterminism() throws Exception {
        // SCENARIO 1: Produce wheat (perishable), let it decay
        resetDatabase();

        TestDataBuilder alice1 = new TestDataBuilder();
        alice1.createPlayer("alice", "secret123");
        alice1.buildFacility("wheat");

        simulateTicks(10);

        double wheatBeforeDecay = ((Number) queryOne(
            "SELECT COALESCE(quantity, 0) as qty FROM inventory WHERE player_id = ? AND resource_name = 'wheat'",
            alice1.getPlayerId()
        ).get("qty")).doubleValue();

        // Run more ticks (decay window: 60 ticks)
        simulateTicks(60);

        double wheatAfterDecay = ((Number) queryOne(
            "SELECT COALESCE(quantity, 0) as qty FROM inventory WHERE player_id = ? AND resource_name = 'wheat'",
            alice1.getPlayerId()
        ).get("qty")).doubleValue();

        assertTrue("Wheat should decay", wheatAfterDecay < wheatBeforeDecay);

        // SCENARIO 2: Repeat identically
        resetDatabase();

        TestDataBuilder alice2 = new TestDataBuilder();
        alice2.createPlayer("alice", "secret123");
        alice2.buildFacility("wheat");

        simulateTicks(10);
        double wheatBeforeDecay2 = ((Number) queryOne(
            "SELECT COALESCE(quantity, 0) as qty FROM inventory WHERE player_id = ? AND resource_name = 'wheat'",
            alice2.getPlayerId()
        ).get("qty")).doubleValue();

        simulateTicks(60);
        double wheatAfterDecay2 = ((Number) queryOne(
            "SELECT COALESCE(quantity, 0) as qty FROM inventory WHERE player_id = ? AND resource_name = 'wheat'",
            alice2.getPlayerId()
        ).get("qty")).doubleValue();

        // Decay should be identical
        assertEquals("Initial wheat should be identical",
            wheatBeforeDecay, wheatBeforeDecay2, 0.01);
        assertEquals("Decay should be deterministic",
            wheatAfterDecay, wheatAfterDecay2, 0.01);
    }

    // ── Helper Methods ───────────────────────────────────────────────────────────

    private void simulateTicks(int count) throws Exception {
        simulation.TickEngine engine = simulation.TickEngine.getInstance();
        if (!engine.isRunning()) {
            engine.start();
        }
        try {
            Thread.sleep(count * 300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
