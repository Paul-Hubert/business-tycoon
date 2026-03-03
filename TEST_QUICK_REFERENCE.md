# Test Runner — Quick Reference Card

## The Two Scripts

```bash
# Complete test runner (handles everything)
./run-tests.sh

# Quick runner (for development, assumes app is running)
./test.sh
```

## Common Workflows

### First Run (Everything from Scratch)
```bash
# Terminal 1: Start app
./setup.sh

# Terminal 2: Run full test suite
./run-tests.sh
```
Takes ~120 seconds. Downloads JUnit, compiles everything, runs tests.

### Development Iteration (Fastest)
```bash
# Make code changes
# Run tests
./test.sh

# Run specific test class
./test.sh Production
./test.sh MarketAndTrading
./test.sh Auth
./test.sh DatabaseAndTickIntegrity
./test.sh EndToEndGameplay
```
Takes ~5-10 seconds per run.

### Before Committing
```bash
# Full validation
./run-tests.sh

# Or run specific classes you modified
./test.sh Production
./test.sh MarketAndTrading
```

## Script Flags

### `run-tests.sh` Flags
```bash
./run-tests.sh                # Run all 45 tests
./run-tests.sh --only Auth    # Run only AuthenticationTests
./run-tests.sh --verbose      # Show compilation details
./run-tests.sh --help         # Show usage
```

### `test.sh` Flags
```bash
./test.sh                      # All tests
./test.sh Auth                 # AuthenticationTests (or Authentication)
./test.sh Production           # ProductionAndTickTests (or ProductionAndTick)
./test.sh Market               # MarketAndTradingTests (or MarketAndTrading)
./test.sh Database             # DatabaseAndTickIntegrityTests (or DatabaseAndTickIntegrity)
./test.sh EndToEnd             # EndToEndGameplayTests (or EndToEndGameplay)
```

Uses substring matching, so many variations work!

## Output Examples

### All Pass ✓
```
[✓]     All tests passed! (45 tests, 97s)
```

### Some Failures ✗
```
[WARN]   Tests completed with failures (102s)

  Tests run: 45
  Failures: 6
  Errors: 0

Failed tests:
  1) testProductionDeterminism(ProductionAndTickTests)
  2) testMarketMatchingDeterminism(MarketAndTradingTests)
  ...
```

## Troubleshooting (2 Minutes)

| Problem | Solution |
|---------|----------|
| "App not running" | Run `./setup.sh` in another terminal |
| "JUnit not found" | Run `./run-tests.sh` once to download |
| "javac not found" | Install JDK 17+ |
| Tests hang | Check app: `curl http://localhost:8080` |
| Compilation errors | Check logs, fix source, re-run `./test.sh` |

## Current Status

- **Tests:** 45 total
- **Passing:** 39 ✓
- **Failing:** 6 ✗
- **Success Rate:** 87%

See `TEST_RUNNER_SUMMARY.md` for known failures.

## File Locations

| File | Purpose |
|------|---------|
| `run-tests.sh` | Main comprehensive test runner |
| `test.sh` | Quick development test runner |
| `TEST_RUNNER_GUIDE.md` | Complete documentation |
| `TEST_RUNNER_SUMMARY.md` | Implementation details & failures |
| `src/test/AllTests.java` | Test suite definition (JUnit) |

## Test Classes

1. **AuthenticationTests** (8) — Auth endpoints
2. **ProductionAndTickTests** (10) — Facilities & production
3. **MarketAndTradingTests** (11) — Orders & matching
4. **DatabaseAndTickIntegrityTests** (8) — DB consistency
5. **EndToEndGameplayTests** (9) — Complete scenarios

## Performance

| Scenario | Time |
|----------|------|
| `./run-tests.sh` (1st run) | ~120s |
| `./run-tests.sh` (subsequent) | ~100s |
| `./test.sh Auth` | ~8s |
| `./test.sh` (all) | ~100s |

## Example Session

```bash
# Start app
$ ./setup.sh
# ... wait for Tomcat to start ...

# Terminal 2: Quick tests
$ ./test.sh Production
[✓]     Running: test.ProductionAndTickTests
...
OK (10 tests)

# Make a change, test again
$ # ... edit some code ...
$ ./test.sh Production
# ... tests run again ...

# Before commit: Full validation
$ ./run-tests.sh
[✓]     All tests passed! (45 tests, 97s)
```

## More Info

- `TEST_RUNNER_GUIDE.md` — Complete guide (troubleshooting, CI/CD, details)
- `TEST_RUNNER_SUMMARY.md` — Technical implementation (Windows bash path fix, etc.)
- `TESTING.md` — Original test documentation
