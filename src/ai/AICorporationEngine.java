package ai;

import config.ConfigManager;
import java.util.concurrent.*;

/**
 * Scheduler for AI corporation decision-making.
 * Each AI corporation runs in its own thread, making decisions at configurable intervals.
 */
public class AICorporationEngine {
    private static AICorporationEngine instance;
    private ScheduledExecutorService executor;
    private volatile boolean running = false;

    private AICorporationEngine() {}

    public static AICorporationEngine getInstance() {
        if (instance == null) {
            synchronized (AICorporationEngine.class) {
                if (instance == null) instance = new AICorporationEngine();
            }
        }
        return instance;
    }

    public void start() {
        if (running) return;
        synchronized (this) {
            if (running) return;
            running = true;
        }

        ConfigManager config = ConfigManager.getInstance();
        if (!config.getBoolean("ai.system_corporations_enabled", true)) {
            System.out.println("[AI] System AI corporations disabled in config");
            return;
        }

        executor = Executors.newScheduledThreadPool(4); // One thread per AI corp

        System.out.println("[AI] Starting AI corporation engine");
        scheduleAI("AgriCorp",    "agricorp");
        scheduleAI("IronWorks",   "ironworks");
        scheduleAI("TechVentures","techventures");
        scheduleAI("LuxuryCraft", "luxurycraft");
    }

    private void scheduleAI(String name, String strategy) {
        ConfigManager config = ConfigManager.getInstance();
        int intervalSeconds = config.getInt("ai.decision_interval_seconds", 30);

        executor.scheduleAtFixedRate(
            new AICorporationTask(name, strategy),
            (long)(Math.random() * intervalSeconds), // Stagger start times
            intervalSeconds,
            TimeUnit.SECONDS
        );
        System.out.println("[AI] Scheduled " + name + " with " + intervalSeconds + " second interval");
    }

    public void stop() {
        if (!running) return;
        synchronized (this) {
            if (!running) return;
            running = false;
        }

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("[AI] AI corporation engine stopped");
    }

    public boolean isRunning() {
        return running;
    }
}
