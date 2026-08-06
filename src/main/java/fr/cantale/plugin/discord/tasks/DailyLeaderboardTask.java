package fr.cantale.plugin.discord.tasks;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.discord.DiscordBot;
import fr.cantale.plugin.discord.DiscordConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.Color;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Envoie les leaderboards quotidiens à 18h dans les salons Discord dédiés.
 */
public class DailyLeaderboardTask extends BukkitRunnable {

    private final CantalePlugin plugin;
    private final DiscordBot discordBot;

    public DailyLeaderboardTask(CantalePlugin plugin, DiscordBot discordBot) {
        this.plugin = plugin;
        this.discordBot = discordBot;
    }

    public void schedule() {
        long delayTicks = calculateDelayUntil18h();
        runTaskTimerAsynchronously(plugin, delayTicks, 20L * 60 * 60 * 24); // tous les 24h
    }

    private long calculateDelayUntil18h() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Paris"));
        ZonedDateTime next18h = now.withHour(18).withMinute(0).withSecond(0);
        if (!now.isBefore(next18h)) {
            next18h = next18h.plusDays(1);
        }
        long seconds = Duration.between(now, next18h).getSeconds();
        return seconds * 20L; // ticks
    }

    @Override
    public void run() {
        DiscordConfig config = discordBot.getConfig();
        if (config == null) return;

        sendKillsLeaderboard(config.getLbKillsChannelId());
        sendMoneyLeaderboard(config.getLbMoneyChannelId());
        sendWantedLeaderboard(config.getLbWantedChannelId());
        sendFactionsLeaderboard(config.getLbFactionsChannelId());
        sendChatReactionsLeaderboard(config.getLbChatReactionsChannelId());
    }

    private void sendKillsLeaderboard(long channelId) {
        if (channelId == 0L) return;
        List<Entry> top = queryTop("SELECT username, kills AS value FROM players ORDER BY kills DESC LIMIT 10");
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.RED)
            .setTitle("🏆 Leaderboard — Kills")
            .setDescription(formatTop(top, "kills"))
            .setTimestamp(java.time.Instant.now());
        discordBot.getJda().getTextChannelById(channelId).sendMessageEmbeds(embed.build()).queue();
    }

    private void sendMoneyLeaderboard(long channelId) {
        if (channelId == 0L) return;
        List<Entry> top = queryTop("SELECT username, balance AS value FROM players ORDER BY balance DESC LIMIT 10");
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.GREEN)
            .setTitle("🏆 Leaderboard — Cantox")
            .setDescription(formatTop(top, "Cantox"))
            .setTimestamp(java.time.Instant.now());
        discordBot.getJda().getTextChannelById(channelId).sendMessageEmbeds(embed.build()).queue();
    }

    private void sendWantedLeaderboard(long channelId) {
        if (channelId == 0L) return;
        List<Entry> top = queryTop("SELECT target_name AS username, reward AS value FROM wanted WHERE active = 1 ORDER BY reward DESC LIMIT 10");
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.ORANGE)
            .setTitle("🏆 Leaderboard — Wanted (Primes actives)")
            .setDescription(formatTop(top, "Cantox"))
            .setTimestamp(java.time.Instant.now());
        discordBot.getJda().getTextChannelById(channelId).sendMessageEmbeds(embed.build()).queue();
    }

    private void sendFactionsLeaderboard(long channelId) {
        if (channelId == 0L) return;
        List<Entry> top = queryTop("SELECT name AS username, power AS value FROM factions ORDER BY power DESC LIMIT 10");
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.CYAN)
            .setTitle("🏆 Leaderboard — Factions (Power)")
            .setDescription(formatTop(top, "power"))
            .setTimestamp(java.time.Instant.now());
        discordBot.getJda().getTextChannelById(channelId).sendMessageEmbeds(embed.build()).queue();
    }

    private void sendChatReactionsLeaderboard(long channelId) {
        if (channelId == 0L) return;
        List<Entry> top = queryTop("SELECT username, chat_reactions AS value FROM players ORDER BY chat_reactions DESC LIMIT 10");
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.MAGENTA)
            .setTitle("🏆 Leaderboard — Réactions Chat")
            .setDescription(formatTop(top, "victoires"))
            .setTimestamp(java.time.Instant.now());
        var channel = discordBot.getJda().getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessageEmbeds(embed.build()).queue();
        }
    }

    private List<Entry> queryTop(String sql) {
        List<Entry> list = new ArrayList<>();
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            int rank = 1;
            while (rs.next()) {
                list.add(new Entry(rank++, rs.getString("username"), rs.getDouble("value")));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Leaderboard] Erreur requete: " + e.getMessage());
        }
        return list;
    }

    private String formatTop(List<Entry> top, String unit) {
        if (top.isEmpty()) return "Aucune donnée.";
        StringBuilder sb = new StringBuilder();
        String[] medals = {"🥇", "🥈", "🥉"};
        for (Entry e : top) {
            String prefix = e.rank <= 3 ? medals[e.rank - 1] : "**#" + e.rank + "**";
            sb.append(prefix).append(" ").append(e.name).append(" — ").append(String.format("%.0f", e.value)).append(" ").append(unit).append("\n");
        }
        return sb.toString();
    }

    private record Entry(int rank, String name, double value) {}
}
