package config;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Singleton config loader for GameConfig.properties.
 * Loads from config/GameConfig.properties relative to working directory.
 * In Docker: /usr/local/tomcat/config/GameConfig.properties
 * In local dev: <project-root>/config/GameConfig.properties
 *
 * Supports hot-reload via hasChanged() + reload().
 */
public class ConfigManager {

    private static volatile ConfigManager instance;
    private Properties properties;
    private final Path configPath;
    private long lastModified = 0;

    private ConfigManager() {
        this.configPath = Paths.get("config/GameConfig.properties");
        reload();
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager();
                }
            }
        }
        return instance;
    }

    public synchronized void reload() {
        try {
            if (!Files.exists(configPath)) {
                System.err.println("[CONFIG] Not found: " + configPath.toAbsolutePath() + " — using defaults");
                if (properties == null) properties = new Properties();
                return;
            }

            Properties fresh = new Properties();
            try (InputStream is = new FileInputStream(configPath.toFile())) {
                fresh.load(is);
            }
            this.properties = fresh;
            this.lastModified = Files.getLastModifiedTime(configPath).toMillis();
            System.out.println("[CONFIG] Loaded from " + configPath.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("[CONFIG] Error loading config: " + e.getMessage());
            if (properties == null) properties = new Properties();
        }
    }

    /** Returns true if the config file has been modified since last load. */
    public boolean hasChanged() {
        try {
            if (Files.exists(configPath)) {
                return Files.getLastModifiedTime(configPath).toMillis() > lastModified;
            }
        } catch (IOException ignored) {}
        return false;
    }

    public int getInt(String key, int defaultValue) {
        String val = properties.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            System.err.println("[CONFIG] Invalid int for '" + key + "': " + val);
            return defaultValue;
        }
    }

    public double getDouble(String key, double defaultValue) {
        String val = properties.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            System.err.println("[CONFIG] Invalid double for '" + key + "': " + val);
            return defaultValue;
        }
    }

    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String val = properties.getProperty(key);
        if (val == null) return defaultValue;
        return val.trim().equalsIgnoreCase("true");
    }
}
