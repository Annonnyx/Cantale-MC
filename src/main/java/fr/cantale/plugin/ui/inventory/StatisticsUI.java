package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.concurrent.TimeUnit;

public class StatisticsUI extends BaseUI {

    private final PlayerDAO playerDAO;

    public StatisticsUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Statistiques de " + player.getName(), 5);
        this.playerDAO = new PlayerDAO(plugin);
    }

    @Override
    protected void init() {
        fillEmpty();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName("§6§l" + player.getName());
            PlayerRank rank = plugin.getRankManager().getRank(player);
            Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
            int lives = plugin.getLifeManager().getLives(player.getUniqueId());
            double balance = plugin.getEconomyManager().getBalance(player);
            meta.setLore(java.util.Arrays.asList(
                "§7Grade: " + formatRank(rank),
                "§7Faction: §e" + (faction != null ? faction.getName() : "Aucune"),
                "§7Vies: §c" + lives,
                "§7Cantox: §6" + (int) balance
            ));
            head.setItemMeta(meta);
        }
        setItem(4, head);

        int kills = playerDAO.getKills(player.getUniqueId());
        int deaths = playerDAO.getDeaths(player.getUniqueId());
        int killStreak = playerDAO.getKillStreak(player.getUniqueId());
        double kdr = deaths == 0 ? kills : (double) kills / deaths;

        setItem(19, Material.DIAMOND_SWORD, "§c§lKills",
                "§7Nombre de kills: §e" + kills,
                "",
                "§7Total des éliminations");

        setItem(21, Material.SKELETON_SKULL, "§7§lMorts",
                "§7Nombre de morts: §e" + deaths,
                "",
                "§7Total des morts");

        setItem(23, Material.FIREWORK_ROCKET, "§6§lKill Streak",
                "§7Série actuelle: §e" + killStreak,
                "",
                "§7Meilleure série consécutive");

        setItem(25, Material.GOLDEN_APPLE, "§a§lRatio K/D",
                "§7K/D: §e" + String.format("%.2f", kdr),
                "",
                "§7Kills / Morts");

        long playtimeSeconds = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L;
        String playtime = formatDuration(playtimeSeconds);

        setItem(31, Material.CLOCK, "§b§lTemps de jeu",
                "§7Playtime: §e" + playtime,
                "",
                "§7Temps total passé sur Cantale");

        addNavigationBar(true, false);
    }

    @Override
    protected void handleClick(int slot) {
        // Aucune interaction spécifique pour l'instant
    }

    private String formatRank(PlayerRank rank) {
        return switch (rank) {
            case AVENTURIER -> "§bAventurier";
            case VIP -> "§aVIP";
            case CHEVRE -> "§6Chèvre";
            case MODERATOR -> "§9Modérateur";
            case ADMIN -> "§cAdmin";
            case OWNER -> "§4Owner";
            default -> "§7Aucun";
        };
    }

    private String formatDuration(long totalSeconds) {
        long days = TimeUnit.SECONDS.toDays(totalSeconds);
        long hours = TimeUnit.SECONDS.toHours(totalSeconds) % 24;
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;
        long seconds = totalSeconds % 60;

        if (days > 0) {
            return days + "j " + hours + "h " + minutes + "m";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        } else if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
