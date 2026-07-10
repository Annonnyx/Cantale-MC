package fr.cantale.plugin.wanted;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class WantedManager {

    private final CantalePlugin plugin;
    private final WantedDAO wantedDAO;
    private final List<Wanted> activeWanted;

    public WantedManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.wantedDAO = new WantedDAO(plugin);
        this.activeWanted = new CopyOnWriteArrayList<>();
    }

    public void loadWanted() {
        activeWanted.clear();
        activeWanted.addAll(wantedDAO.getActiveWanted());
    }

    public boolean addWanted(Player issuer, Player target, double reward, String reason) {
        EconomyManager economy = plugin.getEconomyManager();
        if (!economy.hasEnough(issuer.getUniqueId(), reward)) {
            issuer.sendMessage("§cVous n'avez pas assez d'argent pour mettre cette prime.");
            return false;
        }

        economy.removeBalance(issuer.getUniqueId(), reward);
        
        Wanted wanted = new Wanted(
                0, target.getUniqueId(), target.getName(),
                issuer.getUniqueId(), issuer.getName(),
                reward, System.currentTimeMillis() / 1000, reason, true
        );
        
        int id = wantedDAO.createWanted(wanted);
        if (id > 0) {
            wanted = new Wanted(id, wanted.getTargetUuid(), wanted.getTargetName(),
                    wanted.getIssuerUuid(), wanted.getIssuerName(),
                    wanted.getReward(), wanted.getIssuedAt(), wanted.getReason(), true);
            activeWanted.add(wanted);
            double total = getTotalBounty(target.getUniqueId());
            Bukkit.broadcastMessage("§8[§cWANTED§8] §e" + target.getName() + " §7est recherché ! Prime: §6" + reward + " Cantox §7(Total: §6" + String.format("%.0f", total) + "§7)");
            announceWantedToDiscord(target.getUniqueId(), target.getName(), total, reward, reason);
            return true;
        }
        return false;
    }

    public boolean addWanted(UUID targetUuid, String targetName, double reward, String reason, UUID issuerUuid) {
        // Pour Serial Killer, pas de déduction d'argent (prime système)
        Wanted wanted = new Wanted(
                0, targetUuid, targetName,
                issuerUuid != null ? issuerUuid : UUID.randomUUID(),
                issuerUuid != null ? "Système" : "Serial Killer",
                reward, System.currentTimeMillis() / 1000, reason, true
        );
        
        int id = wantedDAO.createWanted(wanted);
        if (id > 0) {
            wanted = new Wanted(id, wanted.getTargetUuid(), wanted.getTargetName(),
                    wanted.getIssuerUuid(), wanted.getIssuerName(),
                    wanted.getReward(), wanted.getIssuedAt(), wanted.getReason(), true);
            activeWanted.add(wanted);
            return true;
        }
        return false;
    }

    public void completeWanted(Player hunter, Player target) {
        Wanted wanted = getWanted(target.getUniqueId());
        if (wanted == null || !wanted.isActive()) return;

        wantedDAO.completeWanted(wanted.getId());
        wanted.setActive(false);
        activeWanted.remove(wanted);

        plugin.getEconomyManager().addBalance(hunter.getUniqueId(), wanted.getReward());
        Bukkit.broadcastMessage("§8[§cWANTED§8] §e" + hunter.getName() + " §7a capturé §e" + target.getName() + " §7et gagné §6" + wanted.getReward() + " Cantox§7!");
    }

    public Wanted getWanted(UUID targetUuid) {
        for (Wanted w : activeWanted) {
            if (w.getTargetUuid().equals(targetUuid) && w.isActive()) {
                return w;
            }
        }
        return null;
    }

    public boolean isWanted(UUID targetUuid) {
        return getWanted(targetUuid) != null;
    }

    public List<Wanted> getActiveWantedList() {
        return List.copyOf(activeWanted);
    }

    public double getTotalBounty(UUID targetUuid) {
        double total = 0;
        for (Wanted w : activeWanted) {
            if (w.getTargetUuid().equals(targetUuid) && w.isActive()) {
                total += w.getReward();
            }
        }
        return total;
    }

    public int getWantedTier(UUID targetUuid) {
        double total = getTotalBounty(targetUuid);
        if (total >= 5_000_000) return 4;
        if (total >= 1_000_000) return 3;
        if (total >= 100_000) return 2;
        return 1;
    }

    public String getTierLabel(int tier) {
        return switch (tier) {
            case 4 -> "🔴 NIVEAU 4 (5M+)";
            case 3 -> "🟠 NIVEAU 3 (1M+)";
            case 2 -> "🟡 NIVEAU 2 (100K+)";
            default -> "🟢 NIVEAU 1";
        };
    }

    private void announceWantedToDiscord(UUID targetUuid, String targetName, double total, double reward, String reason) {
        if (plugin.getDiscordBot() == null || !plugin.getDiscordBot().isConnected()) return;
        long ch = plugin.getDiscordBot().getConfig().getMcDeathsChannelId();
        if (ch == 0L) return;

        int tier = getWantedTier(targetUuid);
        if (tier == 1) return;

        String tierLabel = getTierLabel(tier);
        String desc = "**Cible:** " + targetName + "\n**Prime ajoutée:** " + String.format("%.0f", reward) + " Cantox\n**Total:** " + String.format("%.0f", total) + " Cantox\n**Raison:** " + reason + "\n**Palier:** " + tierLabel;
        if (tier >= 2) desc += "\n\n<@&1504909846288535705>";

        plugin.getDiscordBot().sendEmbedToChannel(ch, java.awt.Color.RED, "🎯 Nouvelle Prime", desc);
    }

    public boolean removeWanted(Player target, String reason) {
        Wanted wanted = getWanted(target.getUniqueId());
        if (wanted == null || !wanted.isActive()) {
            return false;
        }

        wantedDAO.completeWanted(wanted.getId());
        wanted.setActive(false);
        activeWanted.remove(wanted);

        // Rembourser l'émetteur de la prime
        plugin.getEconomyManager().addBalance(wanted.getIssuerUuid(), wanted.getReward());
        Bukkit.broadcastMessage("§8[§cWANTED§8] §e" + target.getName() + " §7n'est plus recherché. Prime annulée par: §e" + reason);
        return true;
    }
}
