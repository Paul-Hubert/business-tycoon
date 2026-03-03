package servlet;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.sql.*;
import java.util.UUID;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import simulation.Market;
import simulation.TickEngine;
import simulation.World;
import ai.AICorporationEngine;
import config.ConfigManager;
import database.DB;

@WebListener
public class SimulationServlet implements ServletContextListener {

    private ScheduledExecutorService legacyScheduler;

    public static final long SIMULATION_INTERVAL    = 1000;
    public static final long PRICE_INTERVAL_MINUTES = 15;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            // ── Phase 3.2: Initialize AI Corporations ─────────────────────
            initializeAICorporations();
            ensureAITokens();

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

            // ── Phase 3.2: Start AI Engine ─────────────────────────────────
            AICorporationEngine.getInstance().start();

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
        AICorporationEngine.getInstance().stop();
        System.out.println("[SIM] Simulation stopped");
    }

    private void initializeAICorporations() {
        ConfigManager config = ConfigManager.getInstance();
        if (!config.getBoolean("ai.system_corporations_enabled", true)) {
            System.out.println("[SIM] AI corporations disabled in config");
            return;
        }

        String[] corporations = {"AgriCorp", "IronWorks", "TechVentures", "LuxuryCraft"};
        String[] strategies   = {"agricorp", "ironworks", "techventures", "luxurycraft"};

        try (Connection conn = DB.connect()) {
            for (int i = 0; i < corporations.length; i++) {
                String name = corporations[i];
                String strategy = strategies[i];

                // Upsert: create if not exists, leave alone if exists
                String sql = "INSERT IGNORE INTO players (username, password_hash, cash, is_ai, ai_strategy) VALUES (?, 'N/A_AI_ACCOUNT', ?, TRUE, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    double startingCash = config.getDouble("economy.starting_cash", 1000.0);
                    ps.setString(1, name);
                    ps.setDouble(2, startingCash);
                    ps.setString(3, strategy);
                    ps.executeUpdate();
                    System.out.println("[SIM] AI Corporation '" + name + "' initialized");
                }
            }
        } catch (Exception e) {
            System.err.println("[SIM] Failed to initialize AI corporations: " + e.getMessage());
        }
    }

    private void ensureAITokens() {
        try (Connection conn = DB.connect()) {
            String aisSql = "SELECT id, username FROM players WHERE is_ai = TRUE";
            try (PreparedStatement ps = conn.prepareStatement(aisSql);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    int playerId = rs.getInt("id");
                    String name = rs.getString("username");

                    // Check if token exists
                    String checkSql = "SELECT COUNT(*) FROM auth_tokens WHERE player_id = ? AND expires_at > NOW()";
                    boolean hasToken;
                    try (PreparedStatement cp = conn.prepareStatement(checkSql)) {
                        cp.setInt(1, playerId);
                        try (ResultSet cr = cp.executeQuery()) {
                            cr.next();
                            hasToken = cr.getInt(1) > 0;
                        }
                    }

                    if (!hasToken) {
                        // Create a long-lived token (100 years)
                        String token = UUID.randomUUID().toString();
                        String insertSql = "INSERT INTO auth_tokens (player_id, token, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 100 YEAR))";
                        try (PreparedStatement ip = conn.prepareStatement(insertSql)) {
                            ip.setInt(1, playerId);
                            ip.setString(2, token);
                            ip.executeUpdate();
                            System.out.println("[SIM] Token created for AI Corporation '" + name + "'");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[SIM] Failed to ensure AI tokens: " + e.getMessage());
        }
    }
}
