package fr.cantale.plugin.database;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.config.ConfigManager;

import java.io.File;
import java.sql.*;

public class DatabaseManager {

    private final CantalePlugin plugin;
    private Connection connection;
    private String databaseType;

    public DatabaseManager(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        this.databaseType = plugin.getConfigManager().getDatabaseType();
        connect();
        createTables();
    }

    private void connect() {
        try {
            if (databaseType.equalsIgnoreCase("mysql")) {
                ConfigManager cfg = plugin.getConfigManager();
                String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&autoReconnect=true",
                        cfg.getMysqlHost(), cfg.getMysqlPort(), cfg.getMysqlDatabase());
                this.connection = DriverManager.getConnection(url, cfg.getMysqlUsername(), cfg.getMysqlPassword());
            } else {
                // Créer le dossier du plugin s'il n'existe pas
                File dataFolder = plugin.getDataFolder();
                if (!dataFolder.exists()) {
                    boolean created = dataFolder.mkdirs();
                    if (!created) {
                        plugin.getLogger().severe("Impossible de créer le dossier du plugin: " + dataFolder.getAbsolutePath());
                    } else {
                        plugin.getLogger().info("Dossier du plugin créé: " + dataFolder.getAbsolutePath());
                    }
                }
                File dbFile = new File(dataFolder, plugin.getConfigManager().getDatabaseFile());
                String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                this.connection = DriverManager.getConnection(url);
                
                // Optimisations SQLite pour éviter les blocages
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA busy_timeout = 5000"); // Timeout 5 secondes
                    stmt.execute("PRAGMA journal_mode = WAL");  // Write-Ahead Logging pour meilleures performances
                    stmt.execute("PRAGMA synchronous = NORMAL"); // Sync normal pour équilibre perf/sécurité
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Impossible de se connecter à la base de données : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            // Joueurs (vies, économie)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    username VARCHAR(255) NOT NULL,
                    lives INTEGER DEFAULT 3,
                    balance REAL DEFAULT 10000.0,
                    deaths INTEGER DEFAULT 0,
                    kills INTEGER DEFAULT 0,
                    kill_streak INTEGER DEFAULT 0,
                    last_death INTEGER DEFAULT 0,
                    tutorial_progress INTEGER DEFAULT 0,
                    created_at INTEGER DEFAULT 0
                )
                """);

            // Factions
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS factions (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    name VARCHAR(255) NOT NULL UNIQUE,
                    tag VARCHAR(32) NOT NULL UNIQUE,
                    description TEXT,
                    leader_uuid VARCHAR(36) NOT NULL,
                    balance REAL DEFAULT 0,
                    power INTEGER DEFAULT 0,
                    secret_until INTEGER DEFAULT 0,
                    created_at INTEGER DEFAULT 0,
                    spawn_world VARCHAR(255)
                )
                """);

            // Migration : ajouter colonne power si elle n'existe pas
            try {
                stmt.execute("ALTER TABLE factions ADD COLUMN power INTEGER DEFAULT 0");
            } catch (SQLException e) {
                // La colonne existe probablement déjà
                if (!e.getMessage().contains("duplicate column name")) {
                    plugin.getLogger().warning("Erreur lors de la migration power : " + e.getMessage());
                }
            }

            // Migration : ajouter colonne type si elle n'existe pas
            try {
                stmt.execute("ALTER TABLE factions ADD COLUMN type TEXT DEFAULT 'normal'");
            } catch (SQLException e) {
                // La colonne existe probablement déjà
                if (!e.getMessage().contains("duplicate column name")) {
                    plugin.getLogger().warning("Erreur lors de la migration type : " + e.getMessage());
                }
            }

            // Migration : ajouter colonnes spawn_x, spawn_y, spawn_z si elles n'existent pas
            try {
                stmt.execute("ALTER TABLE factions ADD COLUMN spawn_x REAL");
            } catch (SQLException e) {
                if (!e.getMessage().contains("duplicate column name")) {
                    plugin.getLogger().warning("Erreur lors de la migration spawn_x : " + e.getMessage());
                }
            }
            try {
                stmt.execute("ALTER TABLE factions ADD COLUMN spawn_y REAL");
            } catch (SQLException e) {
                if (!e.getMessage().contains("duplicate column name")) {
                    plugin.getLogger().warning("Erreur lors de la migration spawn_y : " + e.getMessage());
                }
            }
            try {
                stmt.execute("ALTER TABLE factions ADD COLUMN spawn_z REAL");
            } catch (SQLException e) {
                if (!e.getMessage().contains("duplicate column name")) {
                    plugin.getLogger().warning("Erreur lors de la migration spawn_z : " + e.getMessage());
                }
            }

            // Membres de faction
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS faction_members (
                    faction_id INTEGER NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    rank VARCHAR(32) DEFAULT 'recruit',
                    joined_at INTEGER DEFAULT 0,
                    PRIMARY KEY (faction_id, player_uuid),
                    FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE
                )
                """);

            // Claims
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS claims (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    faction_id INTEGER NOT NULL,
                    world VARCHAR(255) NOT NULL,
                    chunk_x INTEGER NOT NULL,
                    chunk_z INTEGER NOT NULL,
                    created_at INTEGER DEFAULT 0,
                    pasdic INTEGER DEFAULT 0,
                    FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE,
                    UNIQUE(world, chunk_x, chunk_z)
                )
                """);

            // Warp de faction
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS faction_warps (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    faction_id INTEGER NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    world VARCHAR(255) NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE
                )
                """);

            // Homes personnels (faction)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS faction_homes (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    faction_id INTEGER NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    world VARCHAR(255) NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE
                )
                """);

            // Permissions de faction
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS faction_permissions (
                    faction_id INTEGER NOT NULL,
                    rank VARCHAR(32) NOT NULL,
                    permission VARCHAR(100) NOT NULL,
                    value TEXT,
                    PRIMARY KEY (faction_id, rank, permission),
                    FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE
                )
                """);

            // Historique banque faction
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS faction_bank_log (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    faction_id INTEGER NOT NULL,
                    player_uuid VARCHAR(36),
                    action VARCHAR(50) NOT NULL,
                    amount REAL NOT NULL,
                    reason TEXT,
                    created_at INTEGER DEFAULT 0,
                    FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE
                )
                """);

            // NPCs
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS npcs (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    name VARCHAR(255) NOT NULL,
                    owner_uuid VARCHAR(36) NOT NULL,
                    type VARCHAR(50) NOT NULL,
                    world VARCHAR(255) NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    yaw REAL DEFAULT 0,
                    pitch REAL DEFAULT 0,
                    skin VARCHAR(50) DEFAULT 'default',
                    created_at INTEGER DEFAULT 0
                )
                """);

            // Coffres privés
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS private_chests (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    owner_uuid VARCHAR(36) NOT NULL,
                    location VARCHAR(255) NOT NULL,
                    items TEXT NOT NULL,
                    created_at INTEGER DEFAULT 0
                )
                """);

            // Blocks fortifiés (runes) - LEGACY (par bloc)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS reinforced_blocks (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    world VARCHAR(255) NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    faction_id INTEGER NOT NULL,
                    reinforced_at INTEGER DEFAULT 0
                )
                """);

            // Chunks fortifiés (runes) - NOUVEAU : 1 rune = 1 chunk protégé
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS fortified_chunks (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    world VARCHAR(255) NOT NULL,
                    chunk_x INTEGER NOT NULL,
                    chunk_z INTEGER NOT NULL,
                    faction_id INTEGER NOT NULL,
                    fortified_by VARCHAR(36),
                    fortified_at INTEGER DEFAULT 0,
                    UNIQUE(world, chunk_x, chunk_z),
                    FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE
                )
                """);

            // Cooldowns commande secret
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS secret_cooldowns (
                    player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                    last_used INTEGER DEFAULT 0
                )
                """);

            // Diplomatie
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS diplomacy (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    faction_id INTEGER NOT NULL,
                    target_faction_id INTEGER NOT NULL,
                    type VARCHAR(50) NOT NULL,
                    created_at INTEGER DEFAULT 0,
                    FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE,
                    FOREIGN KEY (target_faction_id) REFERENCES factions(id) ON DELETE CASCADE
                )
                """);

            // Hôtel des ventes
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS auction_listings (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    seller_uuid VARCHAR(36) NOT NULL,
                    seller_name VARCHAR(255) NOT NULL,
                    item_data TEXT NOT NULL,
                    item_type VARCHAR(255) NOT NULL,
                    quantity INTEGER NOT NULL,
                    price_per_unit REAL NOT NULL,
                    created_at INTEGER DEFAULT 0,
                    expires_at INTEGER NOT NULL,
                    buyer_uuid VARCHAR(36)
                )
                """);

            // Homes personnels
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS homes (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    world VARCHAR(255) NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    yaw REAL NOT NULL,
                    pitch REAL NOT NULL,
                    created_at INTEGER DEFAULT 0,
                    UNIQUE(player_uuid, name)
                )
                """);

            // Warps
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS warps (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    name VARCHAR(255) UNIQUE NOT NULL,
                    world VARCHAR(255) NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    yaw REAL NOT NULL,
                    pitch REAL NOT NULL,
                    is_event INTEGER DEFAULT 0,
                    is_active INTEGER DEFAULT 1,
                    created_at INTEGER DEFAULT 0
                )
                """);

            // Warps personnels
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_warps (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    world VARCHAR(255) NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    yaw REAL NOT NULL,
                    pitch REAL NOT NULL,
                    created_at INTEGER DEFAULT 0,
                    UNIQUE(player_uuid, name)
                )
                """);

            // Mutes
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mutes (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(255) NOT NULL,
                    muted_by INTEGER NOT NULL,
                    reason TEXT,
                    duration INTEGER NOT NULL,
                    muted_at INTEGER DEFAULT 0
                )
                """);

            // Wanted (primes)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS wanted (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    target_uuid VARCHAR(36) NOT NULL,
                    target_name VARCHAR(255) NOT NULL,
                    issuer_uuid VARCHAR(36) NOT NULL,
                    issuer_name VARCHAR(255) NOT NULL,
                    reward REAL NOT NULL,
                    issued_at INTEGER DEFAULT 0,
                    reason TEXT,
                    active INTEGER DEFAULT 1
                )
                """);

            // Private Chests
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS private_chests (
                    owner_uuid VARCHAR(36) PRIMARY KEY,
                    contents TEXT NOT NULL,
                    size INTEGER DEFAULT 27,
                    updated_at INTEGER DEFAULT 0
                )
                """);

            // Faction Permissions
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS faction_permissions (
                    faction_id INTEGER NOT NULL,
                    rank VARCHAR(32) NOT NULL,
                    permission VARCHAR(100) NOT NULL,
                    value INTEGER DEFAULT 0,
                    PRIMARY KEY (faction_id, rank, permission)
                )
                """);

            // Player Permissions
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_permissions (
                    uuid VARCHAR(36) NOT NULL,
                    role VARCHAR(32) NOT NULL,
                    updated_at INTEGER DEFAULT 0,
                    PRIMARY KEY (uuid)
                )
                """);

            // Migrations pour les colonnes de tags
            try {
                stmt.execute("ALTER TABLE player_permissions ADD COLUMN show_rank_tag INTEGER DEFAULT 1");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE player_permissions ADD COLUMN active_custom_tag VARCHAR(32) DEFAULT NULL");
            } catch (SQLException ignored) {}

            // Tables pour les tags customisés
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS custom_tags (
                    tag_id VARCHAR(32) PRIMARY KEY,
                    display_name VARCHAR(64) NOT NULL
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_unlocked_tags (
                    uuid VARCHAR(36) NOT NULL,
                    tag_id VARCHAR(32) NOT NULL,
                    PRIMARY KEY (uuid, tag_id),
                    FOREIGN KEY (tag_id) REFERENCES custom_tags(tag_id) ON DELETE CASCADE
                )
                """);

            // Votes (Votifier integration)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS votes (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(255) NOT NULL,
                    site VARCHAR(255) NOT NULL,
                    address VARCHAR(255),
                    tier VARCHAR(50),
                    reward_summary TEXT,
                    voted_at INTEGER DEFAULT 0,
                    delivered INTEGER DEFAULT 0,
                    delivered_at INTEGER
                )
                """);

            // Compteur cumulatif de votes par joueur (lifetime + streak)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS vote_stats (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(255) NOT NULL,
                    total_votes INTEGER DEFAULT 0,
                    streak_days INTEGER DEFAULT 0,
                    last_vote_day INTEGER DEFAULT 0,
                    last_vote_at INTEGER DEFAULT 0
                )
                """);

            // Codes de liaison Discord (temporaires)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS link_codes (
                    code VARCHAR(10) PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(32),
                    created_at INTEGER DEFAULT 0,
                    expires_at INTEGER DEFAULT 0,
                    used INTEGER DEFAULT 0
                )
                """);

            // Migration : ajouter player_name si la table existait sans (SQLite ignore silencieusement les erreurs de dup colonne via try/catch)
            try {
                stmt.execute("ALTER TABLE link_codes ADD COLUMN player_name VARCHAR(32)");
            } catch (java.sql.SQLException ignored) {
                // Colonne déjà présente
            }

            // Liaisons Discord <-> Minecraft
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS discord_links (
                    discord_id VARCHAR(32) PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    linked_at INTEGER DEFAULT 0
                )
                """);

            plugin.getLogger().info("Tables créées / vérifiées avec succès.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lors de la création des tables : " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur de connexion : " + e.getMessage());
        }
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Base de données fermée.");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Erreur lors de la fermeture de la base de données : " + e.getMessage());
        }
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public boolean isSQLite() {
        return databaseType.equalsIgnoreCase("sqlite");
    }
}
