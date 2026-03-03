# Test Quick Start Guide

Get up and running with Trade Empire tests in 3 steps.

## Step 1: Download Test Dependencies

```bash
chmod +x setup-tests.sh
./setup-tests.sh
```

This downloads:
- `junit-4.13.2.jar`
- `hamcrest-core-1.3.jar`

## Step 2: Start the Application

```bash
./setup.sh
```

Verify it's running:
```bash
curl http://localhost:8080/
# Should return 200 OK
```

## Step 3: Run Tests

### Option A: Eclipse IDE (Recommended)

1. Right-click `src/test/AllTests.java`
2. Run As → JUnit Test
3. Watch the test runner output

### Option B: Run Individual Test Class

1. Right-click any test file in `src/test/`
   - `AuthenticationTests.java`
   - `ProductionAndTickTests.java`
   - `MarketAndTradingTests.java`
   - `DatabaseAndTickIntegrityTests.java`
   - `EndToEndGameplayTests.java`
2. Run As → JUnit Test

### Option C: Command Line

```bash
# Compile tests
javac -cp "bin:WebContent/WEB-INF/lib/*" src/test/*.java -d bin

# Run all tests
java -cp "bin:WebContent/WEB-INF/lib/*" org.junit.runner.JUnitCore test.AllTests

# Run single test class
java -cp "bin:WebContent/WEB-INF/lib/*" org.junit.runner.JUnitCore test.AuthenticationTests
```

---

## Test Overview

| Test Class | Count | Focus |
|---|---|---|
| **AuthenticationTests** | 8 | Login, signup, tokens |
| **ProductionAndTickTests** | 10 | Facilities, production, **determinism** |
| **MarketAndTradingTests** | 11 | Orders, matching, **determinism** |
| **DatabaseAndTickIntegrityTests** | 8 | Atomicity, consistency |
| **EndToEndGameplayTests** | 9 | Full game scenarios |
| **TOTAL** | **46 tests** | |

---

## What Gets Tested

✅ **REST API**
- Authentication (signup, login, logout)
- Production endpoints (build, idle, activate, downsize)
- Market endpoints (create, cancel, orderbook)
- Configuration endpoint

✅ **Database**
- Connection pooling
- Transaction atomicity
- Concurrent player isolation
- Inventory conservation

✅ **Simulation Engine**
- Production output (deterministic)
- Operating costs
- Resource decay (deterministic)
- Order matching (deterministic)
- Market fees
- Auto-idle on bankruptcy

✅ **Gameplay**
- Supply chains (wheat → flour → bread)
- Trading and price dynamics
- Economic growth
- Concurrent operations

---

## Understanding Test Results

### Successful Run

```
AuthenticationTests - 8 tests passed ✅
ProductionAndTickTests - 10 tests passed ✅
MarketAndTradingTests - 11 tests passed ✅
DatabaseAndTickIntegrityTests - 8 tests passed ✅
EndToEndGameplayTests - 9 tests passed ✅

Total: 46 tests passed in 2.3s
```

### Failed Test

```
FAILURES (1 test failed):
testProductionDeterminism - AssertionError: Production is deterministic
  Expected: 150
  Actual: 142

Debug:
1. Check that TickEngine is deterministic
2. Verify no random delays in production calculation
3. Check that same input always produces same output
```

### Timeout Error

```
testOrderMatching - TimeoutException
Cause: simulateTicks() waited but orders didn't match

Debug:
1. Verify TickEngine is running: http://localhost:8080/
2. Check market_orders table has entries
3. Increase sleep duration in simulateTicks()
```

---

## Determinism: Key Concept

The simulation is **deterministic** — same starting state + same actions = same outcome.

### Deterministic Tests

Each test resets the database, sets up identical state twice, runs ticks, and compares results:

```
Scenario 1:
  - Create player "alice"
  - Build wheat farm
  - Run 10 ticks
  - Measure wheat produced: 150 units

Scenario 2:
  - Create player "alice" (again)
  - Build wheat farm (again)
  - Run 10 ticks (again)
  - Measure wheat produced: 150 units

ASSERT: 150 == 150 ✅ (deterministic)
```

This tests in `ProductionAndTickTests.testProductionDeterminism()`, `MarketAndTradingTests.testMarketMatchingDeterminism()`, and `DatabaseAndTickIntegrityTests.testResourceDecayDeterminism()`.

---

## Test Builders: How to Write Tests

Use `TestDataBuilder` to set up test data via the REST API:

```java
// Create a player
TestDataBuilder alice = new TestDataBuilder();
alice.createPlayer("alice", "secret123");

// Build facilities
int wheatFarm = alice.buildFacility("wheat");
int flourMill = alice.buildFacility("flour");

// Create market orders
long sellOrder = alice.createSellOrder("wheat", 100, 10.0);
long buyOrder = alice.createBuyOrder("flour", 50, 20.0);

// Cancel orders
alice.cancelOrder(sellOrder);

// Get token/ID
String token = alice.getToken();
int playerId = alice.getPlayerId();
```

All builders use the REST API, so tests verify the API works end-to-end.

---

## Common Issues & Fixes

### "Connection refused: http://localhost:8080"

```bash
# Start the app
./setup.sh

# Or check if running
./setup.sh --status
```

### "Table doesn't exist"

```bash
# Reset database (wipes data, recreates schema)
./setup.sh --reset
```

### "ClassNotFoundException: org.junit.Test"

```bash
# Download JUnit
./setup-tests.sh

# Verify JARs exist
ls WebContent/WEB-INF/lib/junit*.jar
```

### Tests are slow or timeout

Tests use `Thread.sleep()` to wait for ticks. If tests timeout:
1. Increase sleep duration in `simulateTicks()`
2. Ensure TickEngine is running: `http://localhost:8080/api/v1/state`
3. Check system load (other processes consuming CPU)

---

## Extending Tests

Add a new test class:

```java
package test;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class MyNewTests extends RestTestBase {

    @Before
    public void setUp() throws Exception {
        resetDatabase();  // Clean state
    }

    @Test
    public void testMyScenario() throws Exception {
        // Setup
        TestDataBuilder builder = new TestDataBuilder();
        builder.createPlayer("alice", "pass123");

        // Action
        builder.buildFacility("wheat");

        // Verify
        JSONObject facility = queryOne(
            "SELECT id FROM facilities WHERE player_id = ?",
            builder.getPlayerId()
        );
        assertNotNull(facility);
    }
}
```

Then add to `AllTests.java`:

```java
@SuiteClasses({
    AuthenticationTests.class,
    ProductionAndTickTests.class,
    // ... other tests ...
    MyNewTests.class  // <- Add here
})
```

---

## Full Documentation

For comprehensive testing guide, see `TESTING.md`:
- Architecture and test structure
- All 46 test descriptions
- Determinism testing patterns
- Database and HTTP helper APIs
- Troubleshooting guide
- Performance metrics

---

## Quick Links

| Resource | File |
|---|---|
| Test source code | `src/test/*.java` |
| Full documentation | `TESTING.md` |
| Test setup script | `setup-tests.sh` |
| Main app setup | `setup.sh` |

---

## Support

If tests fail:

1. **Check application is running:** `./setup.sh --status`
2. **Read test output:** JUnit shows which assertion failed and why
3. **Check TESTING.md troubleshooting section**
4. **Review code:** Test file explains what's being tested

Example test output that failed:

```
FAIL: testOrderMatching
  at MarketAndTradingTests.java:84
  AssertionError: Trade should occur
  Expected wheat: > 0
  Actual: 0

Debug steps:
1. Verify Alice's sell order was created
2. Check Bob's buy order exists
3. Inspect database: SELECT * FROM market_orders
4. Check simulateTicks() is advancing tick count
```
