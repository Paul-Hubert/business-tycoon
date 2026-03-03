import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.Before;

public class TestRunner {
    public static void main(String[] args) throws Exception {
        String[] testClasses = {
            "test.AuthenticationTests",
            "test.ProductionAndTickTests",
            "test.MarketAndTradingTests",
            "test.DatabaseAndTickIntegrityTests",
            "test.EndToEndGameplayTests"
        };
        
        int passCount = 0, failCount = 0;
        
        for (String className : testClasses) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("Running: " + className);
            System.out.println("=".repeat(60));
            
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            
            // Get all test methods
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(Test.class)) {
                    try {
                        // Run @Before
                        for (Method setup : methods) {
                            if (setup.isAnnotationPresent(Before.class)) {
                                setup.invoke(instance);
                                break;
                            }
                        }
                        
                        method.invoke(instance);
                        System.out.println("  PASS: " + method.getName());
                        passCount++;
                    } catch (Exception e) {
                        System.out.println("  FAIL: " + method.getName());
                        e.getCause().printStackTrace(System.out);
                        failCount++;
                    }
                }
            }
        }
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Results: " + passCount + " passed, " + failCount + " failed");
        System.out.println("=".repeat(60));
    }
}
