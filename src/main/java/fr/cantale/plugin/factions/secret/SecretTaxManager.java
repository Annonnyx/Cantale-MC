package fr.cantale.plugin.factions.secret;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.discord.DiscordLinkService;
import fr.cantale.plugin.economy.BankManager;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.factions.FactionRank;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire du mode secret : taxe journalière, secret gratuit, notifications
 * MC + Discord aux officiers.
 */
public class SecretTaxManager {

    private final CantalePlugin plugin;
    private final FactionManager factionManager;
    private final BankManager bankManager;
    private final DiscordLinkService discordLinkService;
    private final double dailyCost;
    private final int intervalSeconds;
    private final int warningSeconds;
    private final int freeDaysNew;
    private final int freeDaysAfterRaid;

    private final Map<Integer, Long> lastWarningSent = new ConcurrentHashMap<>();

    public SecretTaxManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
        this.bankManager = plugin.getBankManager();
        this.discordLinkService = plugin.getDiscordLinkService();
        this.dailyCost = plugin.getConfigManager().getSecretDailyCost();
        this.intervalSeconds = plugin.getConfigManager().getSecretChargeIntervalMinutes() * 60;
        this.warningSeconds = plugin.getConfigManager().getSecretWarningMinutesBefore() * 60;
        this.freeDaysNew = plugin.getConfigManager().getSecretFreeDaysNewFaction();
        this.freeDaysAfterRaid = plugin.getConfigManager().getSecretFreeDaysAfterRaid();
    }

    public double getDailyCost() {
        return dailyCost;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public boolean isFreeSecret(Faction faction) {
        return faction != null && faction.getSecretFreeUntil() > System.currentTimeMillis() / 1000;
    }

    public void grantFreeSecret(Faction faction, int days) {
        if (faction == null || days <= 0) return;
        long now = System.currentTimeMillis() / 1000;
        long newUntil = now + days * 86400L;
        if (newUntil > faction.getSecretFreeUntil()) {
            faction.setSecretFreeUntil(newUntil);
        }
        if (faction.getSecretUntil() < newUntil) {
            faction.setSecretUntil(newUntil);
        }
        factionManager.saveFaction(faction);
        notifyMapBridgeSecretChanged();
    }

    public void grantFreeSecretNewFaction(Faction faction) {
        grantFreeSecret(faction, freeDaysNew);
    }

    public void grantFreeSecretAfterRaid(Faction faction) {
        grantFreeSecret(faction, freeDaysAfterRaid);
    }

    /**
     * Bascule le mode secret pour la faction du joueur.
     */
    public void toggleSecret(Player player) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§8[§6Cantale§8] §cTu dois être dans une faction.");
            return;
        }
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (rank == null || rank.isLessThan(FactionRank.OFFICER)) {
            player.sendMessage("§8[§6Cantale§8] §cRang insuffisant (Officier requis).");
            return;
        }

        long now = System.currentTimeMillis() / 1000;
        if (faction.isSecret()) {
            faction.setSecretUntil(0);
            factionManager.saveFaction(faction);
            notifyMapBridgeSecretChanged();
            String msg = "§8[§6Cantale§8] §cLe mode secret a été désactivé par §e" + player.getName() + "§c.";
            notifyOfficers(faction, msg);
            notifyDiscord(faction, "Mode secret désactivé par " + player.getName() + ".");
            player.sendMessage("§8[§6Cantale§8] §cMode secret désactivé.");
            return;
        }

        if (isFreeSecret(faction)) {
            long until = faction.getSecretFreeUntil();
            faction.setSecretUntil(until);
            factionManager.saveFaction(faction);
            notifyMapBridgeSecretChanged();
            player.sendMessage("§8[§6Cantale§8] §aMode secret activé gratuitement jusqu'au prochain paiement.");
            notifyOfficers(faction, "§8[§6Cantale§8] §aMode secret gratuit activé par §e" + player.getName() + "§a.");
            notifyDiscord(faction, "Mode secret gratuit activé par " + player.getName() + ".");
            return;
        }

        if (faction.getBalance() < dailyCost) {
            player.sendMessage("§8[§6Cantale§8] §cLa banque de faction n'a pas assez pour activer le secret ("
                    + String.format("%.0f", dailyCost) + " Cantox).");
            return;
        }

        faction.setBalance(faction.getBalance() - dailyCost);
        faction.setSecretUntil(now + intervalSeconds);
        factionManager.saveFaction(faction);
        notifyMapBridgeSecretChanged();
        bankManager.logTransaction(faction.getId(), player.getUniqueId(),
                BankManager.ACTION_SECRET_TAX, dailyCost, faction.getBalance(),
                "Activation du mode secret");
        player.sendMessage("§8[§6Cantale§8] §aMode secret activé. Coût : "
                + String.format("%.0f", dailyCost) + " Cantox.");
        notifyOfficers(faction, "§8[§6Cantale§8] §aMode secret activé par §e" + player.getName()
                + "§a (" + String.format("%.0f", dailyCost) + " Cantox).");
        notifyDiscord(faction, "Mode secret activé par " + player.getName() + " (" + String.format("%.0f", dailyCost) + " Cantox).");
    }

    /**
     * Prélèvement périodique. Appelé toutes les X minutes par le SecretTaxTask.
     */
    public void chargeAll() {
        long now = System.currentTimeMillis() / 1000;
        for (Faction faction : factionManager.getAllFactions()) {
            if (!faction.isSecret()) continue;
            if (isFreeSecret(faction)) continue;

            if (faction.getSecretUntil() <= now) {
                if (faction.getBalance() >= dailyCost) {
                    faction.setBalance(faction.getBalance() - dailyCost);
                    faction.setSecretUntil(now + intervalSeconds);
                    factionManager.saveFaction(faction);
                    bankManager.logTransaction(faction.getId(), null,
                            BankManager.ACTION_SECRET_TAX, dailyCost, faction.getBalance(),
                            "Taxe secret journalière");
                    lastWarningSent.remove(faction.getId());
                } else {
                    faction.setSecretUntil(0);
                    factionManager.saveFaction(faction);
                    notifyMapBridgeSecretChanged();
                    notifyOfficers(faction, "§8[§6Cantale§8] §cLe mode secret a été désactivé : la banque de faction n'a pas assez de Cantox.");
                    notifyDiscord(faction, "Mode secret désactivé : la banque de faction n'a pas assez de Cantox.");
                    lastWarningSent.remove(faction.getId());
                }
            } else {
                long timeUntilDue = faction.getSecretUntil() - now;
                if (timeUntilDue <= warningSeconds && faction.getBalance() < dailyCost) {
                    Long lastWarn = lastWarningSent.get(faction.getId());
                    if (lastWarn == null || now - lastWarn >= Math.max(300, warningSeconds)) {
                        lastWarningSent.put(faction.getId(), now);
                        notifyOfficers(faction, "§8[§6Cantale§8] §eAttention : la banque de faction n'a pas assez pour payer le prochain secret ("
                                + String.format("%.0f", dailyCost) + " Cantox).");
                        notifyDiscord(faction, "Attention : la banque de faction n'a pas assez pour payer le prochain secret ("
                                + String.format("%.0f", dailyCost) + " Cantox).");
                    }
                }
            }
        }
    }

    /** Signale au bridge carte client que la visibilité d'une faction (mode secret) a changé. */
    private void notifyMapBridgeSecretChanged() {
        if (plugin.getClientMapBridge() != null) {
            plugin.getClientMapBridge().onSecretToggled();
        }
    }

    public void notifyOfficers(Faction faction, String message) {
        for (Map.Entry<UUID, FactionRank> entry : faction.getMemberRanks().entrySet()) {
            if (entry.getValue().isAtLeast(FactionRank.OFFICER)) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    p.sendMessage(message);
                }
            }
        }
    }

    public void notifyDiscord(Faction faction, String message) {
        if (plugin.getDiscordBot() == null || !plugin.getDiscordBot().isConnected()) return;
        for (Map.Entry<UUID, FactionRank> entry : faction.getMemberRanks().entrySet()) {
            if (entry.getValue().isAtLeast(FactionRank.OFFICER)) {
                DiscordLinkService.LinkInfo info = discordLinkService.getLinkInfo(entry.getKey().toString());
                if (info != null && info.discordId() != null && !info.discordId().isBlank()) {
                    plugin.getDiscordBot().sendPrivateMessage(info.discordId(), message);
                }
            }
        }
    }
}
