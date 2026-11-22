package fr.cantale.plugin.database;

import com.zaxxer.hikari.HikariConfig;

/**
 * Réglages du pool HikariCP.
 * MySQL : taille bornée à 10–20 (la clé {@code database.mysql.pool-size} vaut 10).
 * SQLite : une seule connexion, les écritures du fichier sont sérialisées.
 */
public final class DatabasePoolSettings {

    public static final int MIN_POOL = 10;
    public static final int MAX_POOL = 20;
    public static final long LEAK_DETECTION_MS = 5_000L;
    public static final long MAX_LIFETIME_MS = 1_800_000L;
    public static final long CONNECTION_TIMEOUT_MS = 10_000L;
    public static final int SQLITE_POOL = 1;

    private DatabasePoolSettings() {
    }

    public static int clampPoolSize(int configured) {
        if (configured < MIN_POOL) {
            return MIN_POOL;
        }
        if (configured > MAX_POOL) {
            return MAX_POOL;
        }
        return configured;
    }

    public static void apply(HikariConfig config, int configuredPoolSize, boolean sqlite) {
        int size = sqlite ? SQLITE_POOL : clampPoolSize(configuredPoolSize);
        config.setMaximumPoolSize(size);
        config.setMinimumIdle(size);
        config.setLeakDetectionThreshold(LEAK_DETECTION_MS);
        config.setMaxLifetime(MAX_LIFETIME_MS);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
    }
}
