package fr.cantale.plugin.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.config.ConfigManager;
import fr.cantale.plugin.trade.TradePendingReturnRepository;

import java.io.File;
import java.sql.*;

public class DatabaseManager {

    private final CantalePlugin plugin;
    private final Object connectionLock = new Object();
    private HikariDataSource dataSource;
    private String databaseType;

    public DatabaseManager(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        this.databaseType = plugin.getConfigManager().getDatabaseType();
        if (!startPool()) {
            plugin.getLogger().severe("Impossible de démarrer la base de données : pool non établi.");
            return;
        }
        createTables();
    }

    /**
     * Ouvre le pool, ou le recrée si les connexions ne répondent plus.
     * Le mot de passe et l'utilisateur ne sont pas écrits dans les logs.
     */
    private boolean startPool() {
        synchronized (connectionLock) {
            if (dataSource != null && !dataSource.isClosed()) {
                try (Connection probe = dataSource.getConnection()) {
                    if (probe.isValid(2)) {
                        return true;
                    }
                } catch (SQLException ignored) {
                    // Le pool existant est inutilisable : on le remplace.
                }
                try {
                    dataSource.close();
                } catch (Exception ignored) {
                }
                dataSource = null;
            }
            if (databaseType == null) {
                this.databaseType = plugin.getConfigManager().getDatabaseType();
            }

            HikariDataSource created = null;
            try {
                HikariConfig hikari = new HikariConfig();
                hikari.setPoolName("CantalePool");
                ConfigManager cfg = plugin.getConfigManager();
                boolean sqlite = !databaseType.equalsIgnoreCase("mysql");
                if (!sqlite) {
                    String host = cfg.getMysqlHost();
                    String database = cfg.getMysqlDatabase();
                    String user = cfg.getMysqlUsername();
                    if (host.isBlank() || database.isBlank() || user.isBlank()) {
                        plugin.getLogger().severe("Identifiants MySQL incomplets. Renseigner CANTALE_DB_HOST, "
                                + "CANTALE_DB_NAME, CANTALE_DB_USER et CANTALE_DB_PASS "
                                + "(ou les clés database.mysql.*). Aucun secret n'est écrit dans les logs.");
                        return false;
                    }
                    if (cfg.getMysqlPassword().isEmpty()) {
                        plugin.getLogger().warning("Mot de passe MySQL vide. Définir CANTALE_DB_PASS "
                                + "si la base en exige un. Aucun secret n'est écrit dans les logs.");
                    }
                    String driver = firstDriver("com.mysql.cj.jdbc.Driver", "org.mariadb.jdbc.Driver");
                    if (driver == null) {
                        plugin.getLogger().severe("Aucun driver JDBC MySQL ou MariaDB n'est visible par le plugin.");
                        return false;
                    }
                    hikari.setDriverClassName(driver);
                    hikari.setJdbcUrl(String.format(
                            "jdbc:mysql://%s:%d/%s?useSSL=false&autoReconnect=true",
                            host, cfg.getMysqlPort(), database));
                    hikari.setUsername(user);
                    hikari.setPassword(cfg.getMysqlPassword());
                } else {
                    File dataFolder = plugin.getDataFolder();
                    if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                        plugin.getLogger().severe("Impossible de créer le dossier du plugin : " + dataFolder.getAbsolutePath());
                    }
                    File dbFile = new File(dataFolder, cfg.getDatabaseFile());
                    String driver = firstDriver("org.sqlite.JDBC");
                    if (driver == null) {
                        plugin.getLogger().severe("Le driver SQLite (org.sqlite.JDBC) n'est pas visible par le plugin.");
                        return false;
                    }
                    hikari.setDriverClassName(driver);
                    hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                    hikari.setConnectionInitSql("PRAGMA busy_timeout = 5000");
                }
                DatabasePoolSettings.apply(hikari, cfg.getMysqlPoolSize(), sqlite);
                created = new HikariDataSource(hikari);
                try (Connection probe = created.getConnection()) {
                    if (sqlite) {
                        try (Statement pragma = probe.createStatement()) {
                            pragma.execute("PRAGMA journal_mode = WAL");
                            pragma.execute("PRAGMA synchronous = NORMAL");
                        }
                    }
                    if (!probe.isValid(2)) {
                        throw new SQLException("connexion de contrôle invalide");
                    }
                }
                this.dataSource = created;
                created = null;
                plugin.getLogger().info(sqlite
                        ? "Pool SQLite HikariCP prêt (1 connexion)."
                        : "Pool MySQL HikariCP prêt (" + hikari.getMaximumPoolSize() + " connexions).");
                return true;
            } catch (Exception e) {
                if (created != null) {
                    try {
                        created.close();
                    } catch (Exception ignored) {
                    }
                }
                plugin.getLogger().severe("Impossible d'ouvrir le pool SQL : " + e.getMessage());
                return false;
            }
        }
    }

    private static String firstDriver(String... classNames) {
        for (String className : classNames) {
            try {
                Class.forName(className);
                return className;
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    /**
     * « Colonne déjà présente » = migration déjà appliquée. MySQL écrit « Duplicate column
     * name 'x' » (D majuscule), SQLite « duplicate column name » — comparaison en minuscules.
     */
    private static boolean isDuplicateColumn(SQLException e) {
        String msg = e.getMessage();
        return msg != null && msg.toLowerCase(java.util.Locale.ROOT).contains("duplicate column name");
    }

    private void createTables() {
        if (dataSource == null || dataSource.isClosed()) {
            plugin.getLogger().severe("Impossible de créer les tables : le pool SQL est indisponible.");
            return;
        }
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
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
                    last_death_world VARCHAR(255),
                    last_death_x REAL,
                    last_death_y REAL,
                    last_death_z REAL,
                    last_death_inventory TEXT,
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
                if (!isDuplicateColumn(e)) {
                    plugin.getLogger().warning("Erreur lors de la migration power : " + e.getMessage());
                }
            }

            // Migration : ajouter colonne type si elle n'existe pas
            try {
                stmt.execute("ALTER TABLE factions ADD COLUMN type TEXT DEFAULT 'normal'");
            } catch (SQLException e) {
                // La colonne existe probablement déjà
                if (!isDuplicateColumn(e)) {
                    plugin.getLogger().warning("Erreur lors de la migration type : " + e.getMessage());
                }
            }

            // Migration : ajouter colonnes spawn_x, spawn_y, spawn_z si elles n'existent pas
            try {
                stmt.execute("ALTER TABLE factions ADD COLUMN spawn_x REAL");
            } catch (SQLException e) {
                if (!isDuplicateColumn(e)) {
                    plugin.getLogger().warning("Erreur lors de la migration spawn_x : " + e.getMessage());
                }
            }
            try {
                stmt.execute("ALTER TABLE factions ADD COLUMN spawn_y REAL");
            } catch (SQLException e) {
                if (!isDuplicateColumn(e)) {
                    plugin.getLogger().warning("Erreur lors de la migration spawn_y : " + e.getMessage());
                }
            }
            try {
                stmt.execute("ALTER TABLE factions ADD COLUMN spawn_z REAL");
            } catch (SQLException e) {
                if (!isDuplicateColumn(e)) {
                    plugin.getLogger().warning("Erreur lors de la migration spawn_z : " + e.getMessage());
                }
            }

            // Migration : rôle Discord dédié par faction
            try {
                stmt.execute("ALTER TABLE factions ADD COLUMN discord_role_id BIGINT DEFAULT 0");
            } catch (SQLException e) {
                if (!isDuplicateColumn(e)) {
                    plugin.getLogger().warning("Erreur lors de la migration discord_role_id : " + e.getMessage());
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
            ensureFactionWarpUniqueNames(connection);

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

            // Historique banque faction (dépôts, retraits, achats pouvoir, admin)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS faction_bank_log (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    faction_id INTEGER NOT NULL,
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(32),
                    action VARCHAR(50) NOT NULL,
                    amount REAL NOT NULL,
                    balance_after REAL,
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

            // Cooldowns commande secret (legacy — remplacé par la taxe journalière)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS secret_cooldowns (
                    player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                    last_used INTEGER DEFAULT 0
                )
                """);

            // Raids actifs et historique
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS faction_raids (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    attacker_faction_id INTEGER NOT NULL,
                    defender_faction_id INTEGER NOT NULL,
                    world VARCHAR(255) NOT NULL,
                    chunk_x INTEGER NOT NULL,
                    chunk_z INTEGER NOT NULL,
                    started_at INTEGER DEFAULT 0,
                    ends_at INTEGER DEFAULT 0,
                    active INTEGER DEFAULT 1,
                    FOREIGN KEY (attacker_faction_id) REFERENCES factions(id) ON DELETE CASCADE,
                    FOREIGN KEY (defender_faction_id) REFERENCES factions(id) ON DELETE CASCADE,
                    INDEX idx_faction_raids_defender (defender_faction_id, active),
                    INDEX idx_faction_raids_attacker (attacker_faction_id, active)
                )
                """);

            // Migrations : mode secret (gratuité) et dernière date de raid
            try {
                stmt.execute("ALTER TABLE factions ADD COLUMN secret_free_until INTEGER DEFAULT 0");
            } catch (java.sql.SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE factions ADD COLUMN last_raid_at INTEGER DEFAULT 0");
            } catch (java.sql.SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE factions ADD COLUMN vuln_start_minute INTEGER DEFAULT 1080");
            } catch (SQLException e) {
                if (!isDuplicateColumn(e)) {
                    plugin.getLogger().warning("Migration vuln_start_minute : " + e.getMessage());
                }
            }
            try {
                stmt.execute("ALTER TABLE factions ADD COLUMN vuln_end_minute INTEGER DEFAULT 1320");
            } catch (SQLException e) {
                if (!isDuplicateColumn(e)) {
                    plugin.getLogger().warning("Migration vuln_end_minute : " + e.getMessage());
                }
            }

            // Nettoyage manuel/automatique des anciens secret_until pour ré-afficher toutes les factions
            try {
                stmt.execute("UPDATE factions SET secret_until = 0 WHERE secret_until > 0");
            } catch (java.sql.SQLException ignored) {}

            // Diplomatie
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS diplomacy (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    faction_id INTEGER NOT NULL,
                    target_faction_id INTEGER NOT NULL,
                    type VARCHAR(50) NOT NULL,
                    created_at INTEGER DEFAULT 0,
                    expires_at INTEGER DEFAULT 0,
                    FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE,
                    FOREIGN KEY (target_faction_id) REFERENCES factions(id) ON DELETE CASCADE
                )
                """);
            // Migration : expiration des déclarations de guerre / propositions de paix en attente
            // (0 = permanent : alliances et guerres actives)
            try {
                stmt.execute("ALTER TABLE diplomacy ADD COLUMN expires_at INTEGER DEFAULT 0");
            } catch (java.sql.SQLException ignored) {}

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

            // Warps publics de faction (/fw) : accessibles à tous, sans zone protégée
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS fwarps (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    name VARCHAR(255) UNIQUE NOT NULL,
                    faction_id INTEGER NOT NULL,
                    creator_uuid VARCHAR(36) NOT NULL,
                    world VARCHAR(255) NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    yaw REAL NOT NULL,
                    pitch REAL NOT NULL,
                    created_at INTEGER DEFAULT 0,
                    FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE
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

            // Private Chests (PC1, PC2, PC3, EC)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS private_chests (
                    owner_uuid VARCHAR(36) NOT NULL,
                    chest_type VARCHAR(16) NOT NULL,
                    contents TEXT NOT NULL,
                    size INTEGER DEFAULT 27,
                    updated_at INTEGER DEFAULT 0,
                    PRIMARY KEY (owner_uuid, chest_type)
                )
                """);
            migratePrivateChestsSchema(connection);

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
                    delivered_at INTEGER,
                    INDEX idx_votes_player (player_uuid),
                    INDEX idx_votes_address_site (address, site),
                    INDEX idx_votes_voted_at (voted_at)
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
                    last_vote_at INTEGER DEFAULT 0,
                    monthly_votes INTEGER DEFAULT 0,
                    last_month INTEGER DEFAULT 0
                )
                """);

            // Migration : ajouter monthly_votes / last_month si la table existait déjà
            try {
                stmt.execute("ALTER TABLE vote_stats ADD COLUMN monthly_votes INTEGER DEFAULT 0");
            } catch (SQLException e) {
                if (!isDuplicateColumn(e)) {
                    plugin.getLogger().warning("Erreur migration vote_stats.monthly_votes : " + e.getMessage());
                }
            }
            try {
                stmt.execute("ALTER TABLE vote_stats ADD COLUMN last_month INTEGER DEFAULT 0");
            } catch (SQLException e) {
                if (!isDuplicateColumn(e)) {
                    plugin.getLogger().warning("Erreur migration vote_stats.last_month : " + e.getMessage());
                }
            }

            // Vote party : compteur journalier
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS vote_party (
                    id INTEGER PRIMARY KEY,
                    day INTEGER DEFAULT 0,
                    count INTEGER DEFAULT 0,
                    goal INTEGER DEFAULT 50
                )
                """);
            // S'assurer qu'il y ait toujours une ligne
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO vote_party (id, day, count, goal) VALUES (1, 0, 0, 50) ON DUPLICATE KEY UPDATE id=id")) {
                ps.executeUpdate();
            } catch (SQLException sqle) {
                // SQLite ne supporte pas ON DUPLICATE KEY ; ignorer silencieusement
            }

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

            // Votes API externes déjà récompensés (Serveur-Prive polling, etc.)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS vote_external_ids (
                    site VARCHAR(64) NOT NULL,
                    external_id VARCHAR(191) NOT NULL,
                    player_name VARCHAR(255),
                    claimed_at INTEGER DEFAULT 0,
                    PRIMARY KEY (site, external_id)
                )
                """);

            // Milestones de votes (paliers déjà réclamés)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS vote_milestones (
                    player_uuid VARCHAR(36) NOT NULL,
                    milestone INTEGER NOT NULL,
                    claimed_at INTEGER DEFAULT 0,
                    PRIMARY KEY (player_uuid, milestone)
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
                    linked_at INTEGER DEFAULT 0,
                    discord_username VARCHAR(64) DEFAULT NULL,
                    discord_display_name VARCHAR(64) DEFAULT NULL
                )
                """);

            // Colonnes ajoutées pour la recherche par nom Discord
            try {
                stmt.execute("ALTER TABLE discord_links ADD COLUMN discord_username VARCHAR(64) DEFAULT NULL");
            } catch (java.sql.SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE discord_links ADD COLUMN discord_display_name VARCHAR(64) DEFAULT NULL");
            } catch (java.sql.SQLException ignored) {}

            // Table des récompenses de giveaway en attente de connexion
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pending_rewards (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    reward_type VARCHAR(32) NOT NULL,
                    reward_value TEXT NOT NULL,
                    quantity INTEGER DEFAULT 1,
                    claimed INTEGER DEFAULT 0,
                    created_at INTEGER DEFAULT 0
                )
                """);

            // Tickets légendaires (gagnés via les caisses, suivis par numéro unique)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS legendary_tickets (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    ticket_number VARCHAR(32) NOT NULL UNIQUE,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(255) NOT NULL,
                    discord_id VARCHAR(32),
                    status VARCHAR(16) DEFAULT 'OPEN',
                    created_at INTEGER DEFAULT 0,
                    used_at INTEGER DEFAULT 0
                )
                """);

            // Migration : ajouter colonne playtime à la table players
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN playtime INTEGER DEFAULT 0");
            } catch (java.sql.SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE players ADD COLUMN lang VARCHAR(8) DEFAULT 'fr'");
            } catch (java.sql.SQLException ignored) {}

            // Migration : victoires réactions chat
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN chat_reactions INTEGER DEFAULT 0");
            } catch (java.sql.SQLException ignored) {}

            // Migration : préférence BossBar infos joueur (1 = activée)
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN boss_bar_enabled INTEGER DEFAULT 1");
            } catch (java.sql.SQLException ignored) {}

            // Migration : dernière IP (lever ban IP après redonner des vies)
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN last_ip VARCHAR(45)");
            } catch (java.sql.SQLException ignored) {}

            // Migration : coords dernière mort (/lastdeath)
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN last_death_world VARCHAR(255)");
            } catch (java.sql.SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN last_death_x REAL");
            } catch (java.sql.SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN last_death_y REAL");
            } catch (java.sql.SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN last_death_z REAL");
            } catch (java.sql.SQLException ignored) {}
            // Migration : inventaire sérialisé à la mort (vue staff /lastdeath)
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN last_death_inventory TEXT");
            } catch (java.sql.SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN balance_peak REAL DEFAULT 0");
            } catch (java.sql.SQLException ignored) {}

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS seasons (
                    id INTEGER PRIMARY KEY %s,
                    started_at INTEGER NOT NULL,
                    ends_at INTEGER NOT NULL,
                    reason VARCHAR(64)
                )
                """.formatted(isSQLite() ? "AUTOINCREMENT" : "AUTO_INCREMENT"));
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS quest_pools (
                    pool_key VARCHAR(128) PRIMARY KEY,
                    payload TEXT
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS quest_progress (
                    owner_id VARCHAR(64) NOT NULL,
                    instance_id VARCHAR(191) NOT NULL,
                    amount INTEGER DEFAULT 0,
                    accepted INTEGER DEFAULT 0,
                    completed INTEGER DEFAULT 0,
                    PRIMARY KEY (owner_id, instance_id)
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS quest_contributions (
                    instance_id VARCHAR(191) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    amount INTEGER DEFAULT 0,
                    PRIMARY KEY (instance_id, player_uuid)
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS quest_winners (
                    instance_id VARCHAR(191) PRIMARY KEY,
                    faction_id INTEGER NOT NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS collection_progress (
                    player_uuid VARCHAR(36) NOT NULL,
                    category VARCHAR(32) NOT NULL,
                    progress_key VARCHAR(64) NOT NULL,
                    amount BIGINT DEFAULT 0,
                    PRIMARY KEY (player_uuid, category, progress_key)
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS collection_claimed (
                    player_uuid VARCHAR(36) NOT NULL,
                    category VARCHAR(32) NOT NULL,
                    progress_key VARCHAR(64) NOT NULL,
                    tier INTEGER NOT NULL,
                    PRIMARY KEY (player_uuid, category, progress_key, tier)
                )
                """);

            // Migration : /parrain (une fois par joueur invité)
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN referral_used INTEGER DEFAULT 0");
            } catch (java.sql.SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN referred_by VARCHAR(36)");
            } catch (java.sql.SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN referral_at INTEGER DEFAULT 0");
            } catch (java.sql.SQLException ignored) {}

            // Migration : points de pouvoir achetés via banque (courbe de prix)
            try {
                stmt.execute("ALTER TABLE factions ADD COLUMN power_purchased INTEGER DEFAULT 0");
            } catch (java.sql.SQLException ignored) {}

            // Migration : historique banque — nom joueur + solde après opération
            try {
                stmt.execute("ALTER TABLE faction_bank_log ADD COLUMN player_name VARCHAR(32)");
            } catch (java.sql.SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE faction_bank_log ADD COLUMN balance_after REAL");
            } catch (java.sql.SQLException ignored) {}

            // Backfill joined_at manquant : seuls les leaders ont une date fiable (création faction).
            // Les autres grades avec joined_at=0 restent à 0 (jamais enregistré historiquement).
            // SQL portable MySQL + SQLite (pas de UPDATE…JOIN).
            try {
                stmt.execute("""
                    UPDATE faction_members
                    SET joined_at = (
                        SELECT f.created_at FROM factions f
                        WHERE f.id = faction_members.faction_id AND f.created_at > 0
                    )
                    WHERE (joined_at IS NULL OR joined_at = 0)
                      AND UPPER(rank) = 'LEADER'
                      AND EXISTS (
                        SELECT 1 FROM factions f
                        WHERE f.id = faction_members.faction_id AND f.created_at > 0
                      )
                    """);
            } catch (java.sql.SQLException ignored) {}

            // Admin Shop : plafonds quotidiens d'achat/vente par joueur et par item
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shop_daily_limits (
                    player_uuid VARCHAR(36) NOT NULL,
                    material VARCHAR(64) NOT NULL,
                    limit_date VARCHAR(10) NOT NULL,
                    sold_qty INTEGER DEFAULT 0,
                    bought_qty INTEGER DEFAULT 0,
                    PRIMARY KEY (player_uuid, material)
                )
                """);

            // Statut serveur pour le site web : ligne unique (id = 1) upsertée par ServerStatusTask.
            // updated_at est un vrai TIMESTAMP (le site le lit comme une Date), contrairement
            // aux colonnes epoch INTEGER des autres tables.
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS server_status (
                    id INTEGER PRIMARY KEY,
                    online_count INTEGER DEFAULT 0,
                    max_players INTEGER DEFAULT 0,
                    tps REAL DEFAULT 20.0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

            // File d'actions faction déposées par le site web (Next.js). Le site n'écrit
            // JAMAIS dans faction_members : il dépose ici une intention (status='pending')
            // que WebFactionBridge rejoue via FactionManager, donc toute la logique métier
            // (cache, power, sync du rôle Discord "a une faction") se déclenche naturellement.
            // type : 'join' | 'leave' | 'set_rank' | 'disband'
            // payload : rang cible pour set_rank ('RECRUIT'|'MEMBER'|'VETERAN'|'OFFICER'), NULL sinon
            // status : 'pending' | 'done' | 'failed' (result contient la raison si failed)
            // created_at/processed_at sont de vrais TIMESTAMP (lus comme des Dates par le site).
            // AUTO_INCREMENT est une syntaxe MySQL refusée par SQLite : AUTOINCREMENT là-bas.
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS web_faction_actions (
                    id INTEGER PRIMARY KEY %s,
                    type VARCHAR(16) NOT NULL,
                    faction_id INTEGER NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    payload VARCHAR(255),
                    status VARCHAR(16) DEFAULT 'pending',
                    result VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    processed_at TIMESTAMP NULL DEFAULT NULL
                )
                """.formatted(isSQLite() ? "AUTOINCREMENT" : "AUTO_INCREMENT"));

            // Fil de chat pour le site (MC / Discord / Web / faction).
            // faction_id NULL = chat global ; sinon chat de faction.
            // source : 'mc' | 'discord' | 'web' | 'system' | 'faction'
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS web_chat_messages (
                    id INTEGER PRIMARY KEY %s,
                    source VARCHAR(16) NOT NULL,
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(64) NOT NULL,
                    message VARCHAR(512) NOT NULL,
                    faction_id INTEGER NULL DEFAULT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """.formatted(isSQLite() ? "AUTOINCREMENT" : "AUTO_INCREMENT"));

            try {
                stmt.execute("ALTER TABLE web_chat_messages ADD COLUMN faction_id INTEGER NULL DEFAULT NULL");
            } catch (SQLException e) {
                if (!e.getMessage().toLowerCase().contains("duplicate column")) {
                    plugin.getLogger().warning("Migration web_chat_messages.faction_id : " + e.getMessage());
                }
            }

            // File d'envoi site → jeu. faction_id NULL = global, sinon faction.
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS web_chat_outbox (
                    id INTEGER PRIMARY KEY %s,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(64) NOT NULL,
                    message VARCHAR(512) NOT NULL,
                    faction_id INTEGER NULL DEFAULT NULL,
                    status VARCHAR(16) DEFAULT 'pending',
                    result VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    processed_at TIMESTAMP NULL DEFAULT NULL
                )
                """.formatted(isSQLite() ? "AUTOINCREMENT" : "AUTO_INCREMENT"));

            try {
                stmt.execute("ALTER TABLE web_chat_outbox ADD COLUMN faction_id INTEGER NULL DEFAULT NULL");
            } catch (SQLException e) {
                if (!e.getMessage().toLowerCase().contains("duplicate column")) {
                    plugin.getLogger().warning("Migration web_chat_outbox.faction_id : " + e.getMessage());
                }
            }

            // Liste des joueurs en ligne (JSON) pour la page admin du site.
            try {
                stmt.execute("ALTER TABLE server_status ADD COLUMN online_json TEXT");
            } catch (SQLException e) {
                if (!e.getMessage().toLowerCase().contains("duplicate column")) {
                    plugin.getLogger().warning("Migration server_status.online_json : " + e.getMessage());
                }
            }

            // File d'actions admin déposées par le site (ADMIN_DISCORD_ID).
            // type : set_money | add_money | set_lives | add_lives | give_item | ban | unban | msg | discord_dm | inspect_player | set_pasdic
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS web_admin_actions (
                    id INTEGER PRIMARY KEY %s,
                    type VARCHAR(32) NOT NULL,
                    target_uuid VARCHAR(36),
                    target_name VARCHAR(64),
                    payload TEXT,
                    actor_discord_id VARCHAR(32) NOT NULL,
                    status VARCHAR(16) DEFAULT 'pending',
                    result TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    processed_at TIMESTAMP NULL DEFAULT NULL
                )
                """.formatted(isSQLite() ? "AUTOINCREMENT" : "AUTO_INCREMENT"));

            // Lots PASDIC (carte site) : payload JSON + résultat avec liste de chunks.
            if (!isSQLite()) {
                try {
                    stmt.execute("ALTER TABLE web_admin_actions MODIFY COLUMN payload TEXT");
                } catch (SQLException e) {
                    plugin.getLogger().warning("Migration web_admin_actions.payload TEXT : " + e.getMessage());
                }
                try {
                    stmt.execute("ALTER TABLE web_admin_actions MODIFY COLUMN result TEXT");
                } catch (SQLException e) {
                    plugin.getLogger().warning("Migration web_admin_actions.result TEXT : " + e.getMessage());
                }
            }

            // Snapshots d'inspection (inventaire / PC / EC / effets) — payload trop gros pour result VARCHAR(255).
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS web_admin_snapshots (
                    id INTEGER PRIMARY KEY %s,
                    action_id INTEGER NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    kind VARCHAR(32) NOT NULL,
                    payload TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """.formatted(isSQLite() ? "AUTOINCREMENT" : "AUTO_INCREMENT"));

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS spawners (
                    world VARCHAR(255) NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    mob_type VARCHAR(32) NOT NULL,
                    tier INTEGER NOT NULL,
                    faction_id INTEGER NOT NULL,
                    PRIMARY KEY (world, x, y, z)
                )
                """);

            // Preuves anti-cheat : chaque violation (native ou Grim) est persistée
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS anticheat_violations (
                    id INTEGER PRIMARY KEY %s,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(255) NOT NULL,
                    source VARCHAR(16) NOT NULL DEFAULT 'NATIVE',
                    check_name VARCHAR(64) NOT NULL,
                    vl INTEGER DEFAULT 1,
                    trust_after INTEGER DEFAULT 100,
                    ping INTEGER DEFAULT 0,
                    tps REAL DEFAULT 20.0,
                    details TEXT,
                    created_at INTEGER DEFAULT 0
                )
                """.formatted(isSQLite() ? "AUTOINCREMENT" : "AUTO_INCREMENT"));
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_ac_violations_player ON anticheat_violations (player_uuid, created_at)");
            } catch (SQLException e) {
                plugin.getLogger().warning("Index anticheat_violations : " + e.getMessage());
            }
            // Migration données : le code insérait historiquement des MILLIS dans des colonnes
            // INTEGER prévues pour des SECONDES epoch (rejetées en MySQL strict, stockées en SQLite).
            // Toute valeur > 99 999 999 999 (année 5138 en secondes) est nécessairement en millis.
            // Idempotent et portable (MySQL + SQLite) ; ne touche aucune valeur déjà en secondes.
            try {
                stmt.execute("UPDATE anticheat_violations SET created_at = created_at / 1000 WHERE created_at > 99999999999");
            } catch (SQLException e) {
                plugin.getLogger().warning("Migration anticheat_violations.created_at (millis→s) : " + e.getMessage());
            }
            try {
                stmt.execute("UPDATE players SET referral_at = referral_at / 1000 WHERE referral_at > 99999999999");
            } catch (SQLException e) {
                plugin.getLogger().warning("Migration players.referral_at (millis→s) : " + e.getMessage());
            }

            stmt.execute(TradePendingReturnRepository.createTableSql(isSQLite()));
            for (String upgrade : TradePendingReturnRepository.upgradeStatements()) {
                try {
                    stmt.execute(upgrade);
                } catch (SQLException e) {
                    if (!isDuplicateColumn(e)) {
                        plugin.getLogger().warning("Migration trade_pending_returns : " + e.getMessage());
                    }
                }
            }
            try {
                stmt.execute(TradePendingReturnRepository.CREATE_INDEX_SQL);
            } catch (SQLException e) {
                plugin.getLogger().warning("Index trade_pending_returns : " + e.getMessage());
            }

            stmt.execute(fr.cantale.plugin.siege.SiegeRepository.createSessionsSql(isSQLite()));
            stmt.execute(fr.cantale.plugin.siege.SiegeRepository.createBlocksSql(isSQLite()));
            stmt.execute(fr.cantale.plugin.skills.SkillRepository.createSql());
            stmt.execute(fr.cantale.plugin.world.WorldCycleRepository.createSql());
            stmt.execute(fr.cantale.plugin.world.KothRepository.createSql());
            for (String sql : fr.cantale.plugin.cosmetics.SeasonPassRepository.schema()) {
                stmt.execute(sql);
            }

            plugin.getLogger().info("Tables créées / vérifiées avec succès.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lors de la création des tables : " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Migre les anciens schémas de private_chests vers
     * (owner_uuid, chest_type, contents, size, updated_at).
     * L'ancienne table est conservée en backup (jamais supprimée).
     *
     * Schémas legacy connus :
     *  - id, owner_uuid, location, items, created_at
     *  - owner_uuid PK, contents, size, updated_at (sans chest_type)
     */
    private void migratePrivateChestsSchema(Connection conn) {
        try {
            if (!tableExists(conn, "private_chests")) return;

            boolean hasChestType = columnExists(conn, "private_chests", "chest_type");
            boolean hasContents = columnExists(conn, "private_chests", "contents");
            boolean hasItems = columnExists(conn, "private_chests", "items");

            if (hasChestType && hasContents) {
                return; // Schéma à jour
            }

            plugin.getLogger().warning("[DB] Migration private_chests détectée (schéma obsolète)...");

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS private_chests_new (
                        owner_uuid VARCHAR(36) NOT NULL,
                        chest_type VARCHAR(16) NOT NULL,
                        contents TEXT NOT NULL,
                        size INTEGER DEFAULT 27,
                        updated_at INTEGER DEFAULT 0,
                        PRIMARY KEY (owner_uuid, chest_type)
                    )
                    """);

                if (hasContents && !hasChestType) {
                    // Ancien schéma PC unique : owner_uuid + contents
                    stmt.execute("""
                        INSERT INTO private_chests_new (owner_uuid, chest_type, contents, size, updated_at)
                        SELECT owner_uuid, 'pc1', contents,
                               COALESCE(size, 27), COALESCE(updated_at, 0)
                        FROM private_chests
                        """);
                } else if (hasItems) {
                    // Schéma initial : items (+ éventuellement plusieurs lignes / owner)
                    if (columnExists(conn, "private_chests", "id")) {
                        stmt.execute("""
                            INSERT INTO private_chests_new (owner_uuid, chest_type, contents, size, updated_at)
                            SELECT t.owner_uuid, 'pc1', t.items, 27, COALESCE(t.created_at, 0)
                            FROM private_chests t
                            INNER JOIN (
                                SELECT owner_uuid, MAX(id) AS max_id
                                FROM private_chests
                                GROUP BY owner_uuid
                            ) latest ON t.owner_uuid = latest.owner_uuid AND t.id = latest.max_id
                            """);
                    } else {
                        stmt.execute("""
                            INSERT INTO private_chests_new (owner_uuid, chest_type, contents, size, updated_at)
                            SELECT owner_uuid, 'pc1', items, 27, COALESCE(created_at, 0)
                            FROM private_chests
                            """);
                    }
                } else {
                    plugin.getLogger().severe("[DB] private_chests : schéma inconnu, migration annulée.");
                    return;
                }

                String backupName = "private_chests_legacy_" + (System.currentTimeMillis() / 1000);
                if (isSQLite()) {
                    stmt.execute("ALTER TABLE private_chests RENAME TO " + backupName);
                    stmt.execute("ALTER TABLE private_chests_new RENAME TO private_chests");
                } else {
                    stmt.execute("RENAME TABLE private_chests TO " + backupName
                            + ", private_chests_new TO private_chests");
                }

                plugin.getLogger().info("[DB] private_chests migré. Ancienne table conservée : " + backupName);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[DB] Échec migration private_chests : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean tableExists(Connection conn, String table) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(conn.getCatalog(), null, table, new String[]{"TABLE"})) {
            if (rs.next()) return true;
        }
        // MySQL sous Linux peut renvoyer des noms en minuscules selon le mode
        try (ResultSet rs = meta.getTables(conn.getCatalog(), null, table.toLowerCase(), new String[]{"TABLE"})) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = meta.getTables(conn.getCatalog(), null, table.toUpperCase(), new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String[] names = {table, table.toLowerCase(), table.toUpperCase()};
        for (String name : names) {
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, name, column)) {
                if (rs.next()) return true;
            }
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, name, column.toLowerCase())) {
                if (rs.next()) return true;
            }
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, name, column.toUpperCase())) {
                if (rs.next()) return true;
            }
        }
        return false;
    }

    /**
     * Emprunte une connexion au pool. L'appelant doit la fermer (try-with-resources) pour la rendre.
     */
    public Connection getConnection() {
        if (dataSource == null || dataSource.isClosed()) {
            if (!startPool()) {
                plugin.getLogger().warning("Pool SQL indisponible : aucune connexion ne peut être empruntée.");
                return null;
            }
        }
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            plugin.getLogger().severe("Impossible d'emprunter une connexion au pool : " + e.getMessage());
            return null;
        }
    }

    /**
     * Indique si le pool peut fournir une connexion utilisable.
     * La connexion de contrôle est rendue au pool.
     */
    public boolean isConnected() {
        HikariDataSource ds = this.dataSource;
        if (ds == null || ds.isClosed()) {
            return false;
        }
        try (Connection conn = ds.getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            plugin.getLogger().warning("Erreur lors de la vérification du pool SQL : " + e.getMessage());
            return false;
        }
    }

    /**
     * Planifie des tentatives de reconnexion asynchrones. Utile si la DB est temporairement indisponible.
     * @param maxAttempts nombre maximum de tentatives
     * @param initialDelayTicks délai initial avant la première tentative (en ticks)
     * @param intervalTicks intervalle entre tentatives (en ticks)
     */
    public void scheduleReconnect(int maxAttempts, long initialDelayTicks, long intervalTicks) {
        new org.bukkit.scheduler.BukkitRunnable() {
            private int attempts = 0;

            @Override
            public void run() {
                attempts++;
                if (isConnected()) {
                    this.cancel();
                    return;
                }
                plugin.getLogger().info("Tentative de reconnexion DB (" + attempts + "/" + maxAttempts + ")...");
                startPool();
                if (isConnected()) {
                    plugin.getLogger().info("Reconnéxion DB réussie après tentative " + attempts + ".");
                    this.cancel();
                    return;
                }
                if (attempts >= maxAttempts) {
                    plugin.getLogger().severe("Échec des tentatives de reconnexion DB après " + attempts + " essais.");
                    this.cancel();
                }
            }
        }.runTaskTimerAsynchronously(plugin, initialDelayTicks, intervalTicks);
    }

    public void close() {
        synchronized (connectionLock) {
            HikariDataSource ds = this.dataSource;
            this.dataSource = null;
            if (ds != null && !ds.isClosed()) {
                ds.close();
                plugin.getLogger().info("Pool SQL fermé.");
            }
        }
    }

    /**
     * Les FW publics s'appellent par {@code /fw <nom>} : le nom doit être unique sur tout le serveur.
     * Dédoublonne les anciens enregistrements puis pose un index unique.
     */
    private void ensureFactionWarpUniqueNames(Connection connection) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name FROM faction_warps ORDER BY id")) {
            java.util.Set<String> used = new java.util.HashSet<>();
            while (rs.next()) {
                int id = rs.getInt("id");
                String original = rs.getString("name");
                if (original == null || original.isBlank()) continue;
                String key = original.toLowerCase(java.util.Locale.ROOT);
                if (used.add(key)) continue;
                String renamed = original + "_" + id;
                int suffix = 2;
                while (!used.add(renamed.toLowerCase(java.util.Locale.ROOT))) {
                    renamed = original + "_" + id + "_" + suffix;
                    suffix++;
                }
                try (PreparedStatement upd = connection.prepareStatement(
                        "UPDATE faction_warps SET name = ? WHERE id = ?")) {
                    upd.setString(1, renamed);
                    upd.setInt(2, id);
                    upd.executeUpdate();
                }
                plugin.getLogger().info("FW renommé pour unicité globale : " + original + " → " + renamed);
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("ensureFactionWarpUniqueNames (dédoublonnage) : " + ex.getMessage());
        }

        try (Statement stmt = connection.createStatement()) {
            if (isSQLite()) {
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_faction_warps_name ON faction_warps(name COLLATE NOCASE)");
            } else {
                stmt.execute("CREATE UNIQUE INDEX idx_faction_warps_name ON faction_warps(name)");
            }
        } catch (SQLException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase(java.util.Locale.ROOT) : "";
            if (!msg.contains("duplicate") && !msg.contains("exists") && !msg.contains("already")) {
                plugin.getLogger().warning("ensureFactionWarpUniqueNames (index) : " + ex.getMessage());
            }
        }
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public boolean isSQLite() {
        return databaseType.equalsIgnoreCase("sqlite");
    }

    /**
     * MariaDB / MySQL (même dialecte pour les upserts). Le config utilise {@code mysql}
     * même si le serveur réel est MariaDB.
     */
    public boolean isMariaDB() {
        return !isSQLite();
    }

    /**
     * Upsert portable : SQLite {@code INSERT OR REPLACE}, MariaDB/MySQL
     * {@code INSERT ... ON DUPLICATE KEY UPDATE}.
     *
     * @param intoClause table et colonnes, ex. {@code secret_cooldowns (player_uuid, last_used)}
     * @param valuesPlaceholders placeholders, ex. {@code ?, ?}
     * @param duplicateUpdates clause SET MariaDB, ex. {@code last_used = VALUES(last_used)}
     */
    public String upsertSql(String intoClause, String valuesPlaceholders, String duplicateUpdates) {
        if (isSQLite()) {
            return "INSERT OR REPLACE INTO " + intoClause + " VALUES (" + valuesPlaceholders + ")";
        }
        return "INSERT INTO " + intoClause + " VALUES (" + valuesPlaceholders + ") ON DUPLICATE KEY UPDATE " + duplicateUpdates;
    }

    /**
     * Supprime toutes les données de toutes les tables du plugin.
     * Les tables elles-mêmes sont conservées (TRUNCATE / DELETE).
     */
    public boolean resetAllTables() {
        String[] tables = {
            "players", "factions", "faction_members", "claims", "faction_warps",
            "faction_homes", "faction_permissions", "faction_bank_log", "npcs",
            "reinforced_blocks", "fortified_chunks", "secret_cooldowns", "diplomacy",
            "auction_listings", "homes", "warps", "player_warps", "mutes", "wanted",
            "private_chests", "player_permissions", "custom_tags", "player_unlocked_tags",
            "votes", "vote_stats", "vote_party", "link_codes", "vote_milestones",
            "discord_links", "pending_rewards", "legendary_tickets", "shop_daily_limits",
            "spawners", "seasons", "quest_pools", "quest_progress",
            "quest_contributions", "quest_winners", "collection_progress", "collection_claimed",
            "trade_pending_returns"
        };

        try (Connection conn = getConnection()) {
            if (conn == null) return false;
            try (Statement stmt = conn.createStatement()) {
                if (isSQLite()) {
                    stmt.execute("PRAGMA foreign_keys = OFF");
                }
                for (String table : tables) {
                    try {
                        stmt.executeUpdate("DELETE FROM " + table);
                    } catch (SQLException e) {
                        plugin.getLogger().warning("Impossible de vider la table " + table + " : " + e.getMessage());
                    }
                }
                if (isSQLite()) {
                    stmt.execute("PRAGMA foreign_keys = ON");
                }
            }
            plugin.getLogger().info("Base de données réinitialisée (toutes les tables vidées).");
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur resetAllTables : " + e.getMessage());
            return false;
        }
    }
}
