package fr.cantale.plugin.trade;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TradeManager {

    private final CantalePlugin plugin;
    private final EconomyManager economyManager;
    private final Map<UUID, TradeSession> activeTrades;
    private static final long TRADE_TIMEOUT_MS = 120000; // 2 minutes

    public TradeManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.activeTrades = new HashMap<>();
    }

    public boolean sendTradeRequest(Player requester, Player target) {
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            requester.sendMessage("§8[§6Cantale§8] §cVous ne pouvez pas échanger avec vous-même.");
            return false;
        }

        if (hasActiveTrade(requester.getUniqueId())) {
            requester.sendMessage("§8[§6Cantale§8] §cVous avez déjà un échange en cours.");
            return false;
        }

        if (hasActiveTrade(target.getUniqueId())) {
            requester.sendMessage("§8[§6Cantale§8] §cCe joueur a déjà un échange en cours.");
            return false;
        }

        // TODO: Système de demandes avec acceptation
        // Pour l'instant, démarrer directement
        startTrade(requester, target);
        return true;
    }

    public void startTrade(Player player1, Player player2) {
        TradeSession session = new TradeSession(player1.getUniqueId(), player2.getUniqueId());
        activeTrades.put(player1.getUniqueId(), session);
        activeTrades.put(player2.getUniqueId(), session);

        player1.sendMessage("§8[§6Cantale§8] §aÉchange démarré avec §e" + player2.getName() + "§a.");
        player2.sendMessage("§8[§6Cantale§8] §aÉchange démarré avec §e" + player1.getName() + "§a.");

        sendTradeUI(player1);
        sendTradeUI(player2);
    }

    public void addItem(Player player, int slot, ItemStack item) {
        TradeSession session = activeTrades.get(player.getUniqueId());
        if (session == null) return;

        session.addItem(player.getUniqueId(), slot, item);
        session.resetAcceptance();

        Player other = Bukkit.getPlayer(session.getOtherPlayer(player.getUniqueId()));
        if (other != null) {
            sendTradeUI(other);
        }
        sendTradeUI(player);
    }

    public void addMoney(Player player, double amount) {
        TradeSession session = activeTrades.get(player.getUniqueId());
        if (session == null) return;

        double current = session.getMoney(player.getUniqueId());
        double available = economyManager.getBalance(player);

        if (current + amount > available) {
            amount = available - current;
        }

        session.setMoney(player.getUniqueId(), current + amount);
        session.resetAcceptance();

        Player other = Bukkit.getPlayer(session.getOtherPlayer(player.getUniqueId()));
        if (other != null) {
            sendTradeUI(other);
        }
        sendTradeUI(player);
    }

    public void setMoney(Player player, double amount) {
        TradeSession session = activeTrades.get(player.getUniqueId());
        if (session == null) return;

        double available = economyManager.getBalance(player);
        if (amount > available) amount = available;
        if (amount < 0) amount = 0;

        session.setMoney(player.getUniqueId(), amount);
        session.resetAcceptance();

        Player other = Bukkit.getPlayer(session.getOtherPlayer(player.getUniqueId()));
        if (other != null) {
            sendTradeUI(other);
        }
        sendTradeUI(player);
    }

    public void acceptTrade(Player player) {
        TradeSession session = activeTrades.get(player.getUniqueId());
        if (session == null) return;

        session.setAccepted(player.getUniqueId(), true);

        if (session.bothAccepted()) {
            completeTrade(session);
        } else {
            Player other = Bukkit.getPlayer(session.getOtherPlayer(player.getUniqueId()));
            if (other != null) {
                other.sendMessage("§8[§6Cantale§8] §e" + player.getName() + " §7a accepté l'échange.");
            }
            sendTradeUI(player);
        }
    }

    public void declineTrade(Player player) {
        TradeSession session = activeTrades.get(player.getUniqueId());
        if (session == null) return;

        cancelTrade(session, "Échange annulé par " + player.getName());
    }

    public void cancelTrade(TradeSession session, String reason) {
        UUID p1 = session.getPlayer1Uuid();
        UUID p2 = session.getPlayer2Uuid();

        activeTrades.remove(p1);
        activeTrades.remove(p2);

        // Retourner les items
        returnItems(p1, session.getPlayerItems(p1));
        returnItems(p2, session.getPlayerItems(p2));

        if (Bukkit.getPlayer(p1) != null) {
            Bukkit.getPlayer(p1).sendMessage("§8[§6Cantale§8] §c" + reason);
        }
        if (Bukkit.getPlayer(p2) != null) {
            Bukkit.getPlayer(p2).sendMessage("§8[§6Cantale§8] §c" + reason);
        }
    }

    private void completeTrade(TradeSession session) {
        UUID p1 = session.getPlayer1Uuid();
        UUID p2 = session.getPlayer2Uuid();

        Player player1 = Bukkit.getPlayer(p1);
        Player player2 = Bukkit.getPlayer(p2);

        // Transférer l'argent
        double money1 = session.getMoney(p1);
        double money2 = session.getMoney(p2);

        if (player1 != null && money2 > 0) {
            economyManager.deposit(player1, money2);
        }
        if (player2 != null && money1 > 0) {
            economyManager.deposit(player2, money1);
        }
        if (player1 != null && money1 > 0) {
            economyManager.withdraw(player1, money1);
        }
        if (player2 != null && money2 > 0) {
            economyManager.withdraw(player2, money2);
        }

        // Transférer les items
        if (player1 != null) {
            for (ItemStack item : session.getPlayerItems(p2).values()) {
                player1.getInventory().addItem(item.clone());
            }
        }
        if (player2 != null) {
            for (ItemStack item : session.getPlayerItems(p1).values()) {
                player2.getInventory().addItem(item.clone());
            }
        }

        activeTrades.remove(p1);
        activeTrades.remove(p2);

        if (player1 != null) player1.sendMessage("§8[§6Cantale§8] §aÉchange réussi !");
        if (player2 != null) player2.sendMessage("§8[§6Cantale§8] §aÉchange réussi !");
    }

    private void returnItems(UUID uuid, Map<Integer, ItemStack> items) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            for (ItemStack item : items.values()) {
                player.getInventory().addItem(item.clone());
            }
        }
    }

    private void sendTradeUI(Player player) {
        TradeSession session = activeTrades.get(player.getUniqueId());
        if (session == null) return;

        UUID otherUuid = session.getOtherPlayer(player.getUniqueId());
        Player other = Bukkit.getPlayer(otherUuid);

        player.sendMessage("§8[§6Cantale§8] §7=== ÉCHANGE ===");
        player.sendMessage("§7Avec : §e" + (other != null ? other.getName() : "Hors ligne"));

        player.sendMessage("§7\n§aVos items :");
        for (ItemStack item : session.getPlayerItems(player.getUniqueId()).values()) {
            player.sendMessage("§7  - §e" + item.getType().name() + " §7x" + item.getAmount());
        }
        player.sendMessage("§7  Votre argent : §6" + session.getMoney(player.getUniqueId()) + " Cantox");

        if (other != null) {
            player.sendMessage("§7\n§cSes items :");
            for (ItemStack item : session.getPlayerItems(otherUuid).values()) {
                player.sendMessage("§7  - §e" + item.getType().name() + " §7x" + item.getAmount());
            }
            player.sendMessage("§7  Son argent : §6" + session.getMoney(otherUuid) + " Cantox");
        }

        player.sendMessage("§7\n§aVous : " + (session.isAccepted(player.getUniqueId()) ? "§2ACCEPTÉ" : "§cEN ATTENTE"));
        if (other != null) {
            player.sendMessage("§7Lui : " + (session.isAccepted(otherUuid) ? "§2ACCEPTÉ" : "§cEN ATTENTE"));
        }

        player.sendMessage("§7\n§eUtilisez /trade accept pour accepter, /trade decline pour refuser");
    }

    public boolean hasActiveTrade(UUID uuid) {
        return activeTrades.containsKey(uuid);
    }

    public void cleanupExpired() {
        for (TradeSession session : new HashMap<>(activeTrades).values()) {
            if (session.isExpired(TRADE_TIMEOUT_MS)) {
                cancelTrade(session, "Échange expiré (inactivité)");
            }
        }
    }
}
