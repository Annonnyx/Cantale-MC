package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.cooldown.CooldownManager;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FeedCommand implements CommandExecutor {

    private static final String COOLDOWN_KEY = "feed";
    private static final long VIP_COOLDOWN_MS = 3 * 60 * 1000L;
    private static final long CHEVRE_COOLDOWN_MS = 60 * 1000L;

    private final CantalePlugin plugin;
    private final CooldownManager cooldownManager;

    public FeedCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.cooldownManager = plugin.getCooldownManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        PlayerRank rank = plugin.getRankManager().getRank(player);
        boolean isAdmin = player.hasPermission("cantale.admin");
        boolean hasFeedPerm = player.hasPermission("cantale.feed");

        if (!rank.isAtLeastVIP() && !isAdmin && !hasFeedPerm) {
            player.sendMessage("§8[§6Cantale§8] §cCette commande est réservée aux grades §eVIP §7et §eChèvre§c.");
            player.sendMessage("§8[§6Cantale§8] §7Achetez le grade sur la boutique : §ecantale.store");
            return true;
        }

        // Admin : pas de cooldown. Sinon VIP = 3 min, Chèvre et au-dessus = 1 min.
        if (!isAdmin) {
            long cooldownMs = resolveCooldownMs(rank);
            if (cooldownManager.hasCooldown(player, COOLDOWN_KEY, cooldownMs)) {
                long remaining = cooldownManager.getRemainingCooldown(player, COOLDOWN_KEY, cooldownMs);
                player.sendMessage("§8[§6Cantale§8] §cCooldown ! Attendez encore §e" + formatRemaining(remaining) + "§c.");
                return true;
            }
        }

        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);

        if (!isAdmin) {
            cooldownManager.setCooldown(player, COOLDOWN_KEY);
        }

        player.sendMessage("§8[§6Cantale§8] §aVotre faim a été restaurée !");
        return true;
    }

    /**
     * VIP → 3 min ; Chèvre / staff (grades ≥ Chèvre) → 1 min.
     * Permission seule ({@code cantale.feed} sans grade VIP+) : cooldown VIP par défaut.
     */
    private long resolveCooldownMs(PlayerRank rank) {
        if (rank == PlayerRank.VIP) {
            return VIP_COOLDOWN_MS;
        }
        if (rank.hasNoTpCooldown() || rank == PlayerRank.CHEVRE) {
            return CHEVRE_COOLDOWN_MS;
        }
        return VIP_COOLDOWN_MS;
    }

    private String formatRemaining(long remainingMs) {
        long seconds = (remainingMs + 999) / 1000;
        if (seconds >= 60) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            if (secs == 0) {
                return minutes + " min";
            }
            return minutes + " min " + secs + " s";
        }
        return seconds + " s";
    }
}
