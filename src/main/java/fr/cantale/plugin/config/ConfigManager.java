package fr.cantale.plugin.config;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final CantalePlugin plugin;
    private final FileConfiguration config;

    public ConfigManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getDatabaseFile() {
        return config.getString("database.sqlite.file", "cantale.db");
    }

    public String getMysqlHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public int getMysqlPort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public String getMysqlDatabase() {
        return config.getString("database.mysql.database", "cantale");
    }

    public String getMysqlUsername() {
        return config.getString("database.mysql.username", "root");
    }

    public String getMysqlPassword() {
        return config.getString("database.mysql.password", "");
    }

    public int getDefaultLives() {
        return config.getInt("lives.default-lives", 3);
    }

    public int getMinLives() {
        return config.getInt("lives.min-lives", 0);
    }

    public double getLifeBuyPrice() {
        return config.getDouble("lives.buy-price", 10000.0);
    }

    public boolean isBanOnZero() {
        return config.getBoolean("lives.ban-on-zero", true);
    }

    public String getBanMessage() {
        return config.getString("lives.ban-message",
                "&cVous n'avez plus de vies ! Achetez-en une ou attendez qu'un ami vous en donne une.");
    }

    public double getStartBalance() {
        return config.getDouble("economy.start-balance", 10000.0);
    }

    public int getTeleportDelay() {
        return config.getInt("teleport.delay", 5);
    }

    public int getMaxHomes() {
        return config.getInt("teleport.max-homes", 3);
    }

    public int getMinMembersCreate() {
        return config.getInt("factions.min-members-create", 8);
    }

    public int getStartChunks() {
        return config.getInt("factions.start-chunks", 60);
    }

    public int getStartHomes() {
        return config.getInt("factions.start-homes", 30);
    }

    public String getMessagePrefix() {
        return config.getString("messages.prefix", "&8[&6Cantale&8] &r");
    }

    public String getMessage(String key) {
        return config.getString("messages." + key, "");
    }

    public String getResourcePackUrl() {
        return config.getString("resource-pack.url", "");
    }

    public String getResourcePackHash() {
        return config.getString("resource-pack.sha1", "");
    }
}
