package fr.cantale.plugin.quests;

import fr.cantale.plugin.CantalePlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class SeasonManager {

    public static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final CantalePlugin plugin;
    private final QuestConfig config;
    private int currentId;
    private long startedAt;
    private long endsAt;

    public SeasonManager(CantalePlugin plugin, QuestConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void loadOrStart() {
        String sql = "SELECT id, started_at, ends_at FROM seasons ORDER BY id DESC LIMIT 1";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                currentId = rs.getInt("id");
                startedAt = rs.getLong("started_at");
                endsAt = rs.getLong("ends_at");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Saison] load : " + e.getMessage());
        }
        long now = System.currentTimeMillis() / 1000L;
        if (currentId <= 0 || now >= endsAt) {
            startNew("auto");
        }
    }

    public boolean startNew(String reason) {
        long now = System.currentTimeMillis() / 1000L;
        long duration = config.seasonDurationDays() * 86_400L;
        String sql = "INSERT INTO seasons (started_at, ends_at, reason) VALUES (?, ?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, now);
            stmt.setLong(2, now + duration);
            stmt.setString(3, reason);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                currentId = keys.getInt(1);
            } else {
                currentId = currentId + 1;
            }
            startedAt = now;
            endsAt = now + duration;
            plugin.getLogger().info("[Saison] #" + currentId + " démarrée (" + reason + "), fin "
                    + ZonedDateTime.ofInstant(Instant.ofEpochSecond(endsAt), PARIS));
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("[Saison] start : " + e.getMessage());
            return false;
        }
    }

    public void tickAuto() {
        long now = System.currentTimeMillis() / 1000L;
        if (currentId <= 0 || now >= endsAt) {
            startNew("auto");
        }
    }

    public int currentId() { return currentId; }
    public long startedAt() { return startedAt; }
    public long endsAt() { return endsAt; }

    public String poolKey(QuestPeriod period) {
        ZonedDateTime now = ZonedDateTime.now(PARIS);
        return switch (period) {
            case DAILY -> now.toLocalDate().toString();
            case WEEKLY -> now.get(java.time.temporal.WeekFields.ISO.weekBasedYear())
                    + "-W" + now.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
            case SEASONAL -> "S" + currentId;
        };
    }
}
