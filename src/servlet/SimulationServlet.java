package servlet;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import simulation.Market;
import simulation.TickEngine;
import simulation.World;

@WebListener
public class SimulationServlet implements ServletContextListener {

    private ScheduledExecutorService legacyScheduler;

    public static final long SIMULATION_INTERVAL    = 1000;
    public static final long PRICE_INTERVAL_MINUTES = 15;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            // ── Legacy simulation (Market + World) ────────────────────────
            // Kept running for backward compatibility with the JSP-based UI.
            // Phase 2 will migrate this logic into the TickEngine steps.
            legacyScheduler = Executors.newScheduledThreadPool(4);

            World world = World.create();

            legacyScheduler.scheduleAtFixedRate(() -> {
                try {
                    Market.updatePrice();
                } catch (Exception e) {
                    System.err.println("[SIM] Market.updatePrice error: " + e.getMessage());
                }
            }, 0, PRICE_INTERVAL_MINUTES, TimeUnit.MINUTES);

            legacyScheduler.scheduleAtFixedRate(() -> {
                try {
                    Market.step(legacyScheduler);
                    world.step(legacyScheduler);
                } catch (Exception e) {
                    System.err.println("[SIM] Simulation step error: " + e.getMessage());
                }
            }, 0, SIMULATION_INTERVAL, TimeUnit.MILLISECONDS);

            // ── Phase 1 Tick Engine ────────────────────────────────────────
            TickEngine.getInstance().start();

            System.out.println("[SIM] Simulation initialized successfully");

        } catch (Exception e) {
            System.err.println("[SIM] FATAL: Failed to initialize simulation: " + e.getMessage());
            e.printStackTrace(System.err);
            throw new RuntimeException("SimulationServlet initialization failed", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        System.out.println("[SIM] Shutting down simulation...");
        if (legacyScheduler != null) {
            legacyScheduler.shutdownNow();
        }
        TickEngine.getInstance().stop();
        System.out.println("[SIM] Simulation stopped");
    }
}
