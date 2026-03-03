# Trade Empire — Test Runner Guide

This guide explains the two test scripts: `run-tests.sh` (comprehensive) and `test.sh` (quick).

## Overview

| Script | Use Case | Dependencies | Time |
|--------|----------|--------------|------|
| `./run-tests.sh` | **First run, CI/CD, full validation** | Ensures app running, downloads JUnit | ~30-60s |
| `./test.sh` | **Development iteration** | Requires app already running, JUnit downloaded | ~5-10s |

## Quick Start

### First Run (Setup Everything)
```bash
# Start the app (one terminal)
./setup.sh

# In another terminal, run all tests with full setup
./run-tests.sh
```

This will:
1. ✓ Check if app is running at `http://localhost:8080`
2. ✓ Start Docker containers if needed
3. ✓ Download JUnit 4.13.2 and Hamcrest 1.3
4. ✓ Compile all source and test code
5. ✓ Run all 46 tests
6. ✓ Report pass/fail statistics

### Development (Fast Iteration)
After first setup, use `./test.sh` for faster test cycles:

```bash
# Run all tests (recompile, ~5s)
./test.sh

# Run only authentication tests
./test.sh Auth

# Run only production tests
./test.sh Production

# Run only market tests
./test.sh MarketAndTrading
```

**Note:** `test.sh` assumes:
- App is already running (started via `./setup.sh`)
- JUnit is already downloaded (via `run-tests.sh`)

## Complete Test Script Reference

### `run-tests.sh` — Full Test Runner

**Purpose:** One-command test execution with zero assumptions.

**What it does:**
1. Checks if app responds at `http://localhost:8080`
2. If not, attempts to start Docker containers
3. Downloads JUnit 4.13.2 if missing
4. Downloads Hamcrest 1.3 if missing
5. Compiles all source code (`src/`) to `build/`
6. Compiles all tests (`src/test/`) to `build/`
7. Runs tests via `JUnitCore`
8. Parses output and reports pass/fail with timing

**Usage:**
```bash
./run-tests.sh                    # Run all 46 tests
./run-tests.sh --only Auth        # Run only AuthenticationTests
./run-tests.sh --only Production  # Run only ProductionAndTickTests
./run-tests.sh --verbose          # Show compilation details
./run-tests.sh --help             # Show usage
```

**Output Example (All Pass):**
```
[INFO]   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[INFO]   Running tests...
[INFO]   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[✓]     All tests passed! (46 tests, 45s)
```

**Output Example (With Failures):**
```
[WARN]   Tests completed with failures (52s)

  Tests run: 46
  Failures: 4
  Errors: 0

Failed tests:
  1) testBuildFacility(ProductionAndTickTests)
  2) testProduction determinism(ProductionAndTickTests)
  ...
```

### `test.sh` — Quick Test Runner

**Purpose:** Lightweight script for fast development iteration.

**What it does:**
1. Verifies JUnit is installed
2. Compiles source + tests
3. Runs specified test class
4. Shows results

**Usage:**
```bash
./test.sh                         # All tests (test.AllTests)
./test.sh Auth                    # AuthenticationTests
./test.sh Production              # ProductionAndTickTests
./test.sh MarketAndTrading        # MarketAndTradingTests
./test.sh DatabaseAndTickIntegrity # DatabaseAndTickIntegrityTests
./test.sh EndToEndGameplay        # EndToEndGameplayTests
```

## Test Classes & What They Cover

### 1. AuthenticationTests (8 tests)
REST API authentication endpoints.
- User signup, login, token validation, logout
- Token expiration, invalid credentials

### 2. ProductionAndTickTests (10 tests)
Facility building, production logic, determinism.
- Build/activate/idle/downsize facilities
- Production with inputs (recipes)
- **Determinism tests:** Same state + actions = same output

### 3. MarketAndTradingTests (11 tests)
Order matching, price discovery, trading.
- Create/cancel orders, match orders, partial fills
- Order determinism, self-trade prevention
- Market fees and price history

### 4. DatabaseAndTickIntegrityTests (8 tests)
Database consistency, transaction atomicity, simulation integrity.
- Facility balance isolation, concurrent orders
- Tick persistence and resumption
- **Determinism tests:** Replay same ticks = same state

### 5. EndToEndGameplayTests (9 tests)
Complete game scenarios: supply chains, trading, growth.
- Build facilities, produce goods, trade on market
- Player bankruptcy, NPC shop sales
- Complete game flow validation

## Troubleshooting

### Script fails with "App not running"
**Problem:** `run-tests.sh` couldn't connect to app.

**Solution:**
1. Manually start app: `./setup.sh`
2. Check Docker status: `./setup.sh --status`
3. View logs: `./setup.sh --logs`
4. Wait 30 seconds for Tomcat to fully start

### Script fails with "JUnit not found"
**Problem:** `test.sh` requires JUnit to be downloaded first.

**Solution:** Run `./run-tests.sh` once to download dependencies.

### Script fails with "javac not found"
**Problem:** Java compiler is not installed.

**Solution:** Install JDK 17 or later:
- Windows/Mac: https://www.oracle.com/java/technologies/downloads/
- Linux: `apt-get install default-jdk`

### Compilation fails with "cannot find symbol"
**Problem:** Source code has syntax errors or missing classes.

**Solution:**
1. Check for compilation errors in output
2. Fix the Java file
3. Re-run `./test.sh`

### Tests timeout or hang
**Problem:** Tests exceed 5-minute timeout.

**Possible causes:**
1. App is unresponsive (check logs: `./setup.sh --logs`)
2. Database is locked (restart: `./setup.sh --reset && ./setup.sh`)
3. Tests waiting for slow operations (increase TEST_TIMEOUT in run-tests.sh)

### Tests fail with "Connection refused"
**Problem:** Tests can't reach app at `http://localhost:8080`.

**Solution:**
1. Verify app is running: `curl http://localhost:8080`
2. Check port isn't blocked: `lsof -i :8080` (macOS/Linux)
3. Check firewall settings
4. Restart: `./setup.sh --reset && ./setup.sh`

## Environment Details

### Java Version
Tests require Java 17+ (JDK). The build process uses:
- `javac -source 17 -target 17`
- Compiles to JDK 17 bytecode (class version 61)

### JUnit & Hamcrest Versions
- **JUnit:** 4.13.2
- **Hamcrest:** 1.3 (core matcher library)

Downloaded to `WebContent/WEB-INF/lib/`.

### Classpath
Compilation and test execution use:
```
build:
WebContent/WEB-INF/lib/*:
junit-4.13.2.jar:
hamcrest-core-1.3.jar
```

## Test Infrastructure Details

### RestTestBase
Provides HTTP helpers for all test classes:
```java
protected String post(String path, JSONObject body) throws Exception
protected JSONObject get(String path) throws Exception
protected void delete(String path) throws Exception
```

### TestDataBuilder
Fluent builder for creating test data via REST API:
```java
TestDataBuilder builder = new TestDataBuilder();
int playerId = builder.buildPlayer("alice", "pass");
int facilityId = builder.buildFacility(playerId, "bread");
```

### Database Reset
Each test automatically resets the database:
```java
@Before
public void setUp() {
    resetDatabase();
    // ... test setup ...
}
```

## CI/CD Integration

### GitHub Actions Example
```yaml
- name: Run tests
  run: |
    chmod +x run-tests.sh
    ./run-tests.sh
```

### Docker-based CI
```bash
docker exec business-tycoon-tomcat /workspace/run-tests.sh
```

## Performance Notes

- **First run:** ~60 seconds (downloads JUnit + Hamcrest)
- **Subsequent runs:** ~45 seconds (compilation + execution)
- **Quick test (./test.sh):** ~5-10 seconds

### Optimization Tips
1. Reuse app instance (don't reset Docker between runs)
2. Use `./test.sh` for single test classes during development
3. Run full `./run-tests.sh` before committing

## See Also

- `TESTING.md` — Detailed test documentation
- `TEST_QUICK_START.md` — Original quick start guide
- `TESTING_SUMMARY.md` — Test statistics and coverage
