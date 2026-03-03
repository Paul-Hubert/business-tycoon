package test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Master test suite for Trade Empire.
 *
 * Runs all test classes in order:
 *   1. AuthenticationTests         - REST API auth endpoints
 *   2. ProductionAndTickTests      - Facilities and production determinism
 *   3. MarketAndTradingTests       - Order matching and trading
 *   4. DatabaseAndTickIntegrityTests - Transaction atomicity and consistency
 *   5. EndToEndGameplayTests       - Complete game scenarios
 *
 * To run all tests:
 *   mvn test
 *
 * Or in Eclipse:
 *   Right-click > Run As > JUnit Test
 */
@RunWith(Suite.class)
@SuiteClasses({
    AuthenticationTests.class,
    ProductionAndTickTests.class,
    MarketAndTradingTests.class,
    DatabaseAndTickIntegrityTests.class,
    EndToEndGameplayTests.class
})
public class AllTests {
    // Suite runner
}
