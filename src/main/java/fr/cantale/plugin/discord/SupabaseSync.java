package fr.cantale.plugin.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.util.PublicRankingExclusions;
import fr.cantale.plugin.database.DatabaseManager;
import okhttp3.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

/**
 * Client de synchronisation vers Supabase (PostgreSQL REST API).
 * Envoie les données du serveur Minecraft vers Supabase pour le site web.
 */
public class SupabaseSync {

    private final CantalePlugin plugin;
    private final DiscordConfig config;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private int taskId = -1;

    public SupabaseSync(CantalePlugin plugin, DiscordConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
    }

    public void start() {
        if (!config.isSupabaseEnabled()) {
            plugin.getLogger().info("[Discord] Supabase sync désactivée.");
            return;
        }

        long intervalTicks = config.getSyncIntervalMinutes() * 60L * 20L;

        new BukkitRunnable() {
            @Override
            public void run() {
                syncAll();
            }
        }.runTaskTimerAsynchronously(plugin, 0L, intervalTicks);

        plugin.getLogger().info("[Discord] Supabase sync démarrée (intervalle: " + config.getSyncIntervalMinutes() + " min).");
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    /**
     * Synchronise toutes les tables vers Supabase.
     */
    public void syncAll() {
        try {
            syncPlayers();
            syncFactions();
            syncLeaderboardKills();
            syncLeaderboardBalance();
            syncOnlineCount();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Discord] Erreur sync Supabase", e);
        }
    }

    private void syncPlayers() {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) return;

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT uuid, username, lives, balance, deaths, kills, kill_streak, last_death, created_at FROM players")) {

            JsonArray players = new JsonArray();
            while (rs.next()) {
                JsonObject p = new JsonObject();
                p.addProperty("uuid", rs.getString("uuid"));
                p.addProperty("username", rs.getString("username"));
                p.addProperty("lives", rs.getInt("lives"));
                p.addProperty("balance", rs.getInt("balance"));
                p.addProperty("deaths", rs.getInt("deaths"));
                p.addProperty("kills", rs.getInt("kills"));
                p.addProperty("kill_streak", rs.getInt("kill_streak"));
                p.addProperty("last_death", rs.getLong("last_death"));
                p.addProperty("created_at", rs.getLong("created_at"));
                players.add(p);
            }

            upsert("players", players, "uuid");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Discord] syncPlayers SQL error", e);
        }
    }

    private void syncFactions() {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) return;

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT id, name, tag, description, leader_uuid, balance, power, type, created_at FROM factions")) {

            JsonArray factions = new JsonArray();
            while (rs.next()) {
                JsonObject f = new JsonObject();
                f.addProperty("id", rs.getInt("id"));
                f.addProperty("name", rs.getString("name"));
                f.addProperty("tag", rs.getString("tag"));
                f.addProperty("description", rs.getString("description"));
                f.addProperty("leader_uuid", rs.getString("leader_uuid"));
                f.addProperty("balance", rs.getInt("balance"));
                f.addProperty("power", rs.getInt("power"));
                f.addProperty("type", rs.getString("type"));
                f.addProperty("created_at", rs.getLong("created_at"));
                factions.add(f);
            }

            upsert("factions", factions, "id");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Discord] syncFactions SQL error", e);
        }
    }

    private void syncLeaderboardKills() {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) return;

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT uuid, username, kills, deaths FROM players"
                     + PublicRankingExclusions.whereExclude("username", "uuid")
                     + " ORDER BY kills DESC LIMIT 50")) {

            JsonArray entries = new JsonArray();
            int rank = 1;
            while (rs.next()) {
                JsonObject e = new JsonObject();
                e.addProperty("uuid", rs.getString("uuid"));
                e.addProperty("username", rs.getString("username"));
                e.addProperty("kills", rs.getInt("kills"));
                e.addProperty("deaths", rs.getInt("deaths"));
                e.addProperty("rank", rank++);
                e.addProperty("category", "kills");
                entries.add(e);
            }

            // On vide d'abord puis insère pour les leaderboards
            deleteWhere("leaderboards", "category", "kills");
            insert("leaderboards", entries);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Discord] syncLeaderboardKills SQL error", e);
        }
    }

    private void syncLeaderboardBalance() {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) return;

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT uuid, username, balance FROM players"
                     + PublicRankingExclusions.whereExclude("username", "uuid")
                     + " ORDER BY balance DESC LIMIT 50")) {

            JsonArray entries = new JsonArray();
            int rank = 1;
            while (rs.next()) {
                JsonObject e = new JsonObject();
                e.addProperty("uuid", rs.getString("uuid"));
                e.addProperty("username", rs.getString("username"));
                e.addProperty("balance", rs.getInt("balance"));
                e.addProperty("rank", rank++);
                e.addProperty("category", "balance");
                entries.add(e);
            }

            deleteWhere("leaderboards", "category", "balance");
            insert("leaderboards", entries);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Discord] syncLeaderboardBalance SQL error", e);
        }
    }

    private void syncOnlineCount() {
        int online = plugin.getServer().getOnlinePlayers().size();
        int max = plugin.getServer().getMaxPlayers();
        double tps = plugin.getServer().getTPS()[0];

        JsonObject status = new JsonObject();
        status.addProperty("server_name", "cantale");
        status.addProperty("online_players", online);
        status.addProperty("max_players", max);
        status.addProperty("tps", Math.round(tps * 100.0) / 100.0);
        status.addProperty("status", "online");
        status.addProperty("updated_at", System.currentTimeMillis());

        JsonArray arr = new JsonArray();
        arr.add(status);

        upsert("server_status", arr, "server_name");
    }

    // ─── Helpers HTTP ───

    private void upsert(String table, JsonArray data, String onConflict) {
        if (data.size() == 0) return;
        String url = config.getSupabaseUrl() + "/rest/v1/" + table;
        RequestBody body = RequestBody.create(data.toString(), MediaType.parse("application/json"));

        Request request = new Request.Builder()
            .url(url + "?on_conflict=" + onConflict)
            .header("apikey", config.getSupabaseKey())
            .header("Authorization", "Bearer " + config.getSupabaseKey())
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates")
            .post(body)
            .build();

        execute(request, table + " upsert");
    }

    private void insert(String table, JsonArray data) {
        if (data.size() == 0) return;
        String url = config.getSupabaseUrl() + "/rest/v1/" + table;
        RequestBody body = RequestBody.create(data.toString(), MediaType.parse("application/json"));

        Request request = new Request.Builder()
            .url(url)
            .header("apikey", config.getSupabaseKey())
            .header("Authorization", "Bearer " + config.getSupabaseKey())
            .header("Content-Type", "application/json")
            .post(body)
            .build();

        execute(request, table + " insert");
    }

    private void deleteWhere(String table, String column, String value) {
        String url = config.getSupabaseUrl() + "/rest/v1/" + table + "?" + column + "=eq." + value;

        Request request = new Request.Builder()
            .url(url)
            .header("apikey", config.getSupabaseKey())
            .header("Authorization", "Bearer " + config.getSupabaseKey())
            .delete()
            .build();

        execute(request, table + " delete");
    }

    private void execute(Request request, String action) {
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "empty";
                plugin.getLogger().warning("[Supabase] " + action + " failed: " + response.code() + " " + body);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Supabase] " + action + " IO error", e);
        }
    }
}
