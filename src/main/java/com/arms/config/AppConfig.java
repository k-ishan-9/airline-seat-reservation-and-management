package com.arms.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Settings from arms.properties, each overridable by an environment variable.
 * No credentials are hard-coded in Java code (the old version had them in every class).
 */
public record AppConfig(String dbUrl, String dbUser, String dbPassword,
                        int holdMinutes, int hashIterations, String adminPassword) {

    public static AppConfig load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/arms.properties")) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read arms.properties", e);
        }
        return new AppConfig(
                value(props, "ARMS_DB_URL", "db.url"),
                value(props, "ARMS_DB_USER", "db.user"),
                value(props, "ARMS_DB_PASSWORD", "db.password"),
                Integer.parseInt(value(props, "ARMS_HOLD_MINUTES", "booking.holdMinutes")),
                Integer.parseInt(value(props, "ARMS_HASH_ITERATIONS", "security.hashIterations")),
                value(props, "ARMS_ADMIN_PASSWORD", "seed.adminPassword"));
    }

    private static String value(Properties props, String envName, String key) {
        String fromEnv = System.getenv(envName);
        if (fromEnv != null) return fromEnv;
        String fromSystem = System.getProperty(envName);
        return fromSystem != null ? fromSystem : props.getProperty(key, "");
    }
}
