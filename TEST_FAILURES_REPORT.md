# Test Failures Report
**Date:** 2026-03-03
**Total Tests:** 45
**Passed:** 33
**Failed:** 12
**Pass Rate:** 73.3%

---

## Bug Report Summary

### Category A: API Integration Issues (4 failures)
Issues with the REST API returning null or malformed responses.

### Category B: Database Schema Issues (1 failure)
Incorrect SQL queries or column name references.

### Category C: Game Logic Issues (7 failures)
Issues with production, market matching, decay, and persistence logic.

---

## Detailed Bug Reports

### 1. testProductionWithInputs
**Status:** ❌ FAILING
**Error Type:** NullPointerException
**Error Message:**
```
java.lang.NullPointerException: Cannot invoke "org.json.simple.JSONObject.get(Object)"
because "<local4>" is null at test.TestDataBuilder.buildFacility(TestDataBuilder.java:110)
at test.ProductionAndTickTests.testProductionWithInputs(ProductionAndTickTests.java:262)
```

**Root Cause:**
- TestDataBuilder.buildFacility() calls `resp.data()` at line 110
- The response object is not wrapping the data in the expected `{ success, data, error }` format
- When `resp.data()` is null, calling `.get("facilityId")` on null throws NPE

**Expected Behavior:**
- POST /api/v1/production/build should return `{ success: true, data: { facilityId: 123, ... }, error: null }`
- TestDataBuilder.buildFacility() should be able to extract facilityId from response

**Test Details:**
- Line 262 in ProductionAndTickTests.java
- Attempts to build a facility with input requirements
- Test assumes facility creation returns facility ID

**Recommended Fix:**
1. Verify ProductionServlet.handleBuild() is returning success(data) wrapper
2. Check if Docker container was rebuilt with the fixed code
3. Manually test: `curl -X POST http://localhost:8080/api/v1/production/build` with valid token
4. Verify response includes `"data": { "facilityId": ... }`

---

### 2. testProductionRequiresInputs
**Status:** ❌ FAILING
**Error Type:** NullPointerException
**Error Message:**
```
java.lang.NullPointerException: Cannot invoke "org.json.simple.JSONObject.get(Object)"
because "<local4>" is null at test.TestDataBuilder.buildFacility(TestDataBuilder.java:110)
at test.ProductionAndTickTests.testProductionRequiresInputs(ProductionAndTickTests.java:235)
```

**Root Cause:** Same as #1 - buildFacility API response format issue

**Test Details:**
- Line 235 in ProductionAndTickTests.java
- Tests that production facilities require input resources
- Expects to build facility but buildFacility() returns null data

**Recommended Fix:** Same as #1

---

### 3. testMultipleFacilitiesProduction
**Status:** ❌ FAILING
**Error Type:** SQLSyntaxErrorException
**Error Message:**
```
java.sql.SQLSyntaxErrorException: (conn=2698) Unknown column 'wheat' in 'SELECT'
at test.ProductionAndTickTests.testMultipleFacilitiesProduction(ProductionAndTickTests.java:295)
```

**Root Cause:**
- Test at line 295 is using incorrect SQL syntax
- Attempting to SELECT 'wheat' as if it's a column name
- Should be a parameter placeholder or proper column reference

**Expected Behavior:**
- Query should reference the correct column names from facilities or inventory tables
- Use parametrized queries to avoid SQL errors

**Test Details:**
- Line 295 in ProductionAndTickTests.java
- Tests production with multiple facilities
- SQL query likely: `SELECT [something] WHERE resource = 'wheat'` (incorrect)
- Should be: `SELECT [something] WHERE resource_name = 'wheat'` (correct)

**Recommended Fix:**
1. Review the SQL query at line 295 in ProductionAndTickTests.java
2. Replace direct string selection with proper column references
3. Verify column names match database schema (use `resource_name` not `resource`)
4. Use parametrized queries: `WHERE resource_name = ?` with parameter value 'wheat'

---

### 4. testProductionDeterminism
**Status:** ❌ FAILING
**Error Type:** AssertionError
**Error Message:**
```
java.lang.AssertionError: expected:<10> but was:<1014>
at test.ProductionAndTickTests.testProductionDeterminism(ProductionAndTickTests.java:131)
```

**Root Cause:**
- Production output is not deterministic
- Running identical production scenario twice produces different results
- Expected: 10 units, Actual: 1014 units (100x difference)
- Suggests production is accumulating across multiple ticks or has randomness

**Expected Behavior:**
- Same starting state + same actions = same production output
- Determinism is critical for game balance and replay-ability
- Production should be: per-tick rate × number of ticks

**Test Details:**
- Line 131 in ProductionAndTickTests.java
- Tests wheat production over identical scenarios
- Compares production results between two identical runs
- First run produces 10 units, second run produces 1014 units

**Recommended Fix:**
1. Check TickEngine.step2_production() for non-deterministic behavior
2. Verify ResourceRegistry.getDefaultProductionPerTick() returns consistent values
3. Check for:
   - Random number generation (should be none for production)
   - Floating point precision issues (use exact comparisons)
   - Time-based logic (should use tick count, not system time)
   - Uninitialized variables
4. Review facility state initialization - ensure same starting conditions both runs
5. Check if production rate multiplier is being applied inconsistently

---

### 5. testDownsizeRefund
**Status:** ❌ FAILING
**Error Type:** AssertionError
**Error Message:**
```
java.lang.AssertionError: expected:<200> but was:<400>
at test.ProductionAndTickTests.testDownsizeRefund(ProductionAndTickTests.java:206)
```

**Root Cause:**
- Downsize refund calculation is incorrect
- Expected refund: 200 (40% of 500 build cost)
- Actual refund: 400 (80% of 500 build cost)
- Suggests refund rate is being applied incorrectly (doubled)

**Expected Behavior:**
- Build cost for wheat facility: typically 500
- Downsize refund rate (from config): 0.40 (40%)
- Expected refund: 500 × 0.40 = 200
- Actual refund: 400 (suggests refund rate is 0.80)

**Test Details:**
- Line 206 in ProductionAndTickTests.java
- Tests facility downsize refund logic
- Builds facility, downsizes it, checks refund amount

**Recommended Fix:**
1. Check ProductionServlet.handleDownsize() line 176 (refund rate calculation)
2. Verify ConfigManager.getDouble("facility.downsize_refund_rate", 0.40) returns 0.40
3. Check GameConfig.properties for correct value: `facility.downsize_refund_rate=0.40`
4. Verify calculation: `refund = resource.getBuildCost() × refundRate`
5. Check if refund is being applied twice (both in code and in another location)
6. Review database schema - ensure refund is being persisted/retrieved correctly

---

### 6. testPartialOrderMatching
**Status:** ❌ FAILING
**Error Type:** AssertionError
**Error Message:**
```
java.lang.AssertionError: Alice should have 50 wheat remaining expected:<50> but was:<49>
at test.MarketAndTradingTests.testPartialOrderMatching(MarketAndTradingTests.java:330)
```

**Root Cause:**
- Partial order matching is off by 1 unit
- Alice sells 100 wheat, Bob buys 50
- Expected: Alice should have 50 remaining
- Actual: Alice has 49 remaining
- Lost 1 wheat unit (rounding error or logic bug)

**Expected Behavior:**
- When order matching occurs:
  - Seller inventory decreases by matched quantity
  - Buyer inventory increases by matched quantity
  - No units should be lost
- Inventory conservation: seller before - matched qty = seller after

**Test Details:**
- Line 330 in MarketAndTradingTests.java
- Scenario: Alice sells 100 wheat at 10/unit, Bob buys 50 at 10/unit
- After matching, Alice should have 100 - 50 = 50 wheat
- Actually has 49 wheat (1 unit lost)

**Recommended Fix:**
1. Review TickEngine.step5_market_matching() for quantity handling
2. Check for:
   - Rounding errors in integer/decimal conversions
   - Off-by-one errors in loop logic
   - Duplicate quantity deductions
   - Incomplete quantity tracking
3. Verify inventory update SQL uses exact quantities from order matching
4. Add debug logging to trace quantity changes
5. Check if market fee is being deducted from seller (should it be?)
6. Verify quantity_filled is being updated correctly in database

---

### 7. testMarketMatchingDeterminism
**Status:** ❌ FAILING
**Error Type:** AssertionError
**Error Message:**
```
java.lang.AssertionError: Alice's wheat should be identical expected:<49.0> but was:<49.5>
at test.MarketAndTradingTests.testMarketMatchingDeterminism(MarketAndTradingTests.java:191)
```

**Root Cause:**
- Market matching produces non-deterministic results
- Same scenario run twice produces different wheat quantities for Alice
- First run: 49.0 wheat
- Second run: 49.5 wheat
- Suggests fractional/floating-point math or random rounding

**Expected Behavior:**
- Identical market conditions should result in identical trades
- All quantities should be integers (no fractional wheat)
- Rounding should be deterministic and consistent

**Test Details:**
- Line 191 in MarketAndTradingTests.java
- Runs identical market scenario twice, compares results
- Tests determinism of order matching

**Recommended Fix:**
1. Review TickEngine.step5_market_matching() for floating-point operations
2. Check for:
   - Floating-point precision issues
   - Non-deterministic rounding (use floor/ceiling consistently)
   - Order processing order (may vary due to database ordering)
3. Verify order matching uses integer quantity arithmetic, not floating-point
4. Ensure order matching processes orders in deterministic sequence
5. Check database indexes to ensure consistent ordering
6. Review market fee calculation - if it's fractional, ensure consistent rounding

---

### 8. testResourceDecayDeterminism
**Status:** ❌ FAILING
**Error Type:** AssertionError
**Error Message:**
```
java.lang.AssertionError: Wheat should decay
at test.DatabaseAndTickIntegrityTests.testResourceDecayDeterminism(DatabaseAndTickIntegrityTests.java:327)
```

**Root Cause:**
- Resource decay mechanism is not working
- Test expects inventory to decay over time (e.g., 100 units → 99 units after decay)
- Actual: No decay is occurring

**Expected Behavior:**
- Inventory should decay at a configured rate per tick or time period
- Decay should be applied to player inventory based on last_decay_tick
- Wheat at tick 0 → after 60 ticks → should be less due to decay

**Test Details:**
- Line 327 in DatabaseAndTickIntegrityTests.java
- Tests that inventory decays over time
- Checks that wheat quantity decreases after simulation

**Recommended Fix:**
1. Check TickEngine.step3_decay() implementation
2. Verify:
   - Decay is being calculated: `decay_amount = quantity × decay_rate`
   - Decay is being applied to inventory: `UPDATE inventory SET quantity = quantity - decay_amount`
   - Decay condition is correct: `WHERE last_decay_tick <= ? - 60`
   - Decay rate is configured in GameConfig.properties
3. Check if decay function is even being called in tick loop
4. Verify inventory table has `last_decay_tick` column
5. Check test expectations - ensure decay_rate is non-zero in config
6. Review decay logic - may need to check player inventory, not global

---

### 9. testOrderMatchingInventoryConsistency
**Status:** ❌ FAILING
**Error Type:** AssertionError
**Error Message:**
```
java.lang.AssertionError: Total wheat should be conserved expected:<100.0> but was:<98.00999999999999>
at test.DatabaseAndTickIntegrityTests.testOrderMatchingInventoryConsistency(DatabaseAndTickIntegrityTests.java:225)
```

**Root Cause:**
- Inventory is not conserved during order matching
- Started with 100 wheat total
- After matching: 98.01 wheat (lost 1.99 wheat)
- Suggests:
  - Market fee is being deducted from inventory (should come from cash)
  - Floating-point precision errors accumulating
  - Inventory lost in transaction

**Expected Behavior:**
- Total wheat in system should remain constant: 100 units
- Market matching should only transfer between players
- No wheat should be destroyed or created
- Market fees should come from player CASH, not inventory

**Test Details:**
- Line 225 in DatabaseAndTickIntegrityTests.java
- Scenario: Alice has 100 wheat, creates sell order
- Bob matches buy order
- Expected: Total wheat still = 100 (just transferred)
- Actual: Total wheat = 98.01 (lost ~2 units)

**Recommended Fix:**
1. Review TickEngine.step5_market_matching() - specifically fee calculation
2. Verify:
   - Market fee is deducted from seller CASH: `UPDATE players SET cash = cash - fee`
   - Market fee is NOT deducted from inventory
   - Inventory transfer is correct: `UPDATE inventory SET quantity = quantity - matched_qty WHERE player_id = seller`
3. Check for duplicate fee deductions
4. Review inventory UPDATE statements for accuracy
5. Check floating-point precision - use DECIMAL(15, 2) in database
6. Add inventory audit logging to track where wheat is being lost

---

### 10. testTickCountPersistence
**Status:** ❌ FAILING
**Error Type:** AssertionError
**Error Message:**
```
java.lang.AssertionError: Tick count should persist expected:<1198> but was:<209>
at test.DatabaseAndTickIntegrityTests.testTickCountPersistence(DatabaseAndTickIntegrityTests.java:48)
```

**Root Cause:**
- Tick count persistence logic is incorrect
- Test expects tick count to resume from previous value
- Expected: 1198 (previous ticks), Actual: 209 (partial)
- Suggests:
  - TickEngine is not loading persisted tick count on startup
  - Tick count is being reset instead of resumed
  - Database is not persisting tick count correctly

**Expected Behavior:**
- When TickEngine stops, it should persist current_tick to game_state table
- When TickEngine starts again, it should RESUME from that tick number
- Test: run to tick 1000, stop, start again → should be at 1198+ (after simulating more)
- Not reset to 0

**Test Details:**
- Line 48 in DatabaseAndTickIntegrityTests.java
- Starts TickEngine, runs some ticks (209 shown)
- Stops engine, verifies tick count is in database
- Restarts engine, expects it to resume from previous count
- Actually resets to 0 or a lower value

**Recommended Fix:**
1. Check TickEngine.start() method - does it load tick count from database?
2. Verify:
   - On start: `SELECT current_tick FROM game_state` and resume from that value
   - On stop: `UPDATE game_state SET current_tick = ?` to persist current value
3. Check game_state table schema - should have `current_tick` column
4. Verify test scenario:
   - First run: simulate some ticks
   - Stop engine
   - Query database for saved tick count
   - Start engine again
   - Run more ticks
   - Verify count continued from previous value
5. Check if test is resetting database between operations (may be wiping tick count)
6. Review initialization logic in SimulationServlet

---

### 11. testBankruptcyAndAutoIdle
**Status:** ❌ FAILING
**Error Type:** NullPointerException
**Error Message:**
```
java.lang.NullPointerException: Cannot invoke "org.json.simple.JSONObject.get(Object)"
because "<local4>" is null at test.TestDataBuilder.buildFacility(TestDataBuilder.java:110)
at test.EndToEndGameplayTests.testBankruptcyAndAutoIdle(EndToEndGameplayTests.java:138)
```

**Root Cause:** Same as #1 - buildFacility API response format issue

**Test Details:**
- Line 138 in EndToEndGameplayTests.java
- Tests that players go bankrupt and facilities auto-idle
- Attempts to build facility but API returns null data

**Recommended Fix:** Same as #1 - verify ProductionServlet response format

---

### 12. testSupplyChain
**Status:** ❌ FAILING
**Error Type:** NullPointerException
**Error Message:**
```
java.lang.NullPointerException: Cannot invoke "org.json.simple.JSONObject.get(Object)"
because "<local4>" is null at test.TestDataBuilder.buildFacility(TestDataBuilder.java:110)
at test.EndToEndGameplayTests.testSupplyChain(EndToEndGameplayTests.java:92)
```

**Root Cause:** Same as #1 - buildFacility API response format issue

**Test Details:**
- Line 92 in EndToEndGameplayTests.java
- Tests supply chain: wheat → flour → bread
- Attempts to build facilities but API returns null data

**Recommended Fix:** Same as #1 - verify ProductionServlet response format

---

## Summary by Category

### Category A: API Integration (4 failures)
**Tests:** 1, 2, 11, 12
**Issue:** ProductionServlet buildFacility() returning null data object
**Impact:** Cannot build facilities, blocks 4 end-to-end tests
**Priority:** 🔴 CRITICAL
**Action:** Verify Docker rebuild included ProductionServlet fixes

### Category B: Database Schema (1 failure)
**Tests:** 3
**Issue:** Incorrect SQL column reference in test
**Impact:** One test cannot execute
**Priority:** 🟡 HIGH
**Action:** Fix SQL query to use correct column names

### Category C: Game Logic (7 failures)
**Tests:** 4, 5, 6, 7, 8, 9, 10
**Issues:**
- Non-deterministic production & market matching
- Inventory conservation failures
- Decay not working
- Refund calculation incorrect
- Tick persistence not working

**Impact:** Core game mechanics broken
**Priority:** 🔴 CRITICAL
**Action:** Debug simulation engine implementation

---

## Recommended Next Steps

1. **IMMEDIATE:** Verify Docker container has latest code
   - Check if ProductionServlet.success() wrapper is in compiled bytecode
   - If not, rebuild with: `./setup.sh --reset`

2. **HIGH PRIORITY:** Fix Category A issues
   - Test buildFacility API directly: `curl -X POST http://localhost:8080/api/v1/production/build`
   - Verify response includes `{ "success": true, "data": { "facilityId": ... } }`

3. **HIGH PRIORITY:** Debug TickEngine determinism
   - Add logging to production calculation
   - Verify ResourceRegistry values are consistent
   - Check for floating-point math in quantity calculations

4. **MEDIUM PRIORITY:** Fix inventory conservation
   - Trace order matching to find where wheat is lost
   - Verify fee deduction comes from cash, not inventory
   - Add inventory audit logging

5. **ONGOING:** Add comprehensive logging
   - Log all inventory changes with reason
   - Log all cash changes with reason
   - Log tick engine state transitions
   - Use structured logging for easier debugging

---

## Test Execution Commands

```bash
# Run all tests
java -cp "bin;WebContent/WEB-INF/lib/*" org.junit.runner.JUnitCore test.AllTests

# Run single test class
java -cp "bin;WebContent/WEB-INF/lib/*" org.junit.runner.JUnitCore test.ProductionAndTickTests

# Run single test
java -cp "bin;WebContent/WEB-INF/lib/*" org.junit.runner.JUnitCore test.ProductionAndTickTests#testDownsizeRefund
```

---

**Generated:** 2026-03-03
**Status:** Test suite in development, core API integration functional, game logic needs debugging
