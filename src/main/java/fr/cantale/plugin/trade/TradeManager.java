package fr.cantale.plugin.trade;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.economy.EconomyManager;
import fr.cantale.plugin.ui.inventory.TradeSessionUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestionnaire des échanges joueur ↔ joueur.
 *
 * Flux : /trade <joueur> envoie une demande (60 s) → le receveur accepte
 * (bouton cliquable ou /trade accept) → GUI d'échange ouvert des deux côtés →
 * dépôt d'items + argent via bouton (saisie du montant dans le chat) →
 * confirmation des deux joueurs → compte à rebours de 5 s → échange finalisé.
 *
 * Sécurités : toute modification d'offre annule les confirmations et le
 * compte à rebours ; fermeture du GUI ou déconnexion annule l'échange avec
 * retour des items (rendus au prochain login si le joueur est hors ligne) ;
 * l'argent n'est prélevé qu'à la complétion après re-vérification des soldes.
 */
public class TradeManager {

    private static final long REQUEST_TIMEOUT_MS = 60_000;      // durée de vie d'une demande
    private static final long TRADE_TIMEOUT_MS = 180_000;       // inactivité max d'une session
    private static final long COUNTDOWN_MS = 5_000;             // délai final avant échange
    private static final long MONEY_INPUT_TIMEOUT_MS = 30_000;  // saisie du montant dans le chat

    private final CantalePlugin plugin;
    private final EconomyManager economyManager;

    private final Map<UUID, TradeSession> activeTrades = new HashMap<>();
    private final Map<UUID, PendingRequest> pendingRequests = new HashMap<>(); // clé = cible
    private final Map<UUID, Long> moneyInputs = new HashMap<>();               // saisie montant en cours
    private final Map<UUID, TradeSessionUI> openGuis = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingReturns = new HashMap<>(); // items à rendre au login
    private final Map<TradeSession, BukkitTask> countdownTasks = new HashMap<>();

    private record PendingRequest(UUID requester, String requesterName, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > REQUEST_TIMEOUT_MS;
        }
    }

    public TradeManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        // Balayage périodique : demandes, sessions et saisies expirées.
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpired, 20L * 30, 20L * 30);
    }

    // ---------------------------------------------------------------- demandes

    public void sendTradeRequest(Player requester, Player target) {
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            requester.sendMessage("§8[§6Cantale§8] §cVous ne pouvez pas échanger avec vous-même.");
            return;
        }
        if (hasActiveTrade(requester.getUniqueId())) {
            requester.sendMessage("§8[§6Cantale§8] §cVous avez déjà un échange en cours.");
            return;
        }
        if (hasActiveTrade(target.getUniqueId())) {
            requester.sendMessage("§8[§6Cantale§8] §e" + target.getName() + " §ca déjà un échange en cours.");
            return;
        }
        PendingRequest existing = pendingRequests.get(target.getUniqueId());
        if (existing != null && !existing.isExpired()) {
            requester.sendMessage("§8[§6Cantale§8] §e" + target.getName()
                + " §ca déjà une demande d'échange en attente.");
            return;
        }

        pendingRequests.put(target.getUniqueId(),
            new PendingRequest(requester.getUniqueId(), requester.getName(), System.currentTimeMillis()));

        requester.sendMessage("§8[§6Cantale§8] §aDemande d'échange envoyée à §e" + target.getName()
            + " §7(expire dans 60 s).");

        target.sendMessage(Component.text("[Cantale] ", NamedTextColor.DARK_GRAY)
            .append(Component.text(requester.getName(), NamedTextColor.YELLOW))
            .append(Component.text(" veut échanger avec vous : ", NamedTextColor.GRAY))
            .append(Component.text("[Accepter]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/trade accept"))
                .hoverEvent(HoverEvent.showText(Component.text("Accepter l'échange", NamedTextColor.GREEN))))
            .append(Component.text(" "))
            .append(Component.text("[Refuser]", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/trade decline"))
                .hoverEvent(HoverEvent.showText(Component.text("Refuser l'échange", NamedTextColor.RED)))));
    }

    public boolean hasPendingRequest(UUID targetUuid) {
        PendingRequest req = pendingRequests.get(targetUuid);
        return req != null && !req.isExpired();
    }

    /** Acceptation : demande en attente → démarre la session ; session active → confirme. */
    public void accept(Player player) {
        PendingRequest req = pendingRequests.remove(player.getUniqueId());
        if (req != null && !req.isExpired()) {
            Player requester = Bukkit.getPlayer(req.requester());
            if (requester == null) {
                player.sendMessage("§8[§6Cantale§8] §c" + req.requesterName() + " n'est plus en ligne.");
                return;
            }
            if (hasActiveTrade(requester.getUniqueId()) || hasActiveTrade(player.getUniqueId())) {
                player.sendMessage("§8[§6Cantale§8] §cImpossible de démarrer : un échange est déjà en cours.");
                return;
            }
            startSession(requester, player);
            return;
        }
        if (req != null) {
            player.sendMessage("§8[§6Cantale§8] §cCette demande d'échange a expiré.");
            return;
        }

        TradeSession session = getSession(player.getUniqueId());
        if (session == null) {
            player.sendMessage("§8[§6Cantale§8] §cAucune demande ni échange en cours.");
            return;
        }
        if (!session.isConfirmed(player.getUniqueId())) {
            toggleConfirm(player);
        } else {
            player.sendMessage("§8[§6Cantale§8] §7Vous avez déjà confirmé l'échange.");
        }
    }

    /** Refus d'une demande ou annulation d'un échange en cours. */
    public void decline(Player player) {
        PendingRequest req = pendingRequests.remove(player.getUniqueId());
        if (req != null) {
            player.sendMessage("§8[§6Cantale§8] §7Demande d'échange refusée.");
            Player requester = Bukkit.getPlayer(req.requester());
            if (requester != null) {
                requester.sendMessage("§8[§6Cantale§8] §e" + player.getName()
                    + " §ca refusé votre demande d'échange.");
            }
            return;
        }
        TradeSession session = getSession(player.getUniqueId());
        if (session == null) {
            player.sendMessage("§8[§6Cantale§8] §cAucune demande ni échange en cours.");
            return;
        }
        cancelTrade(session, player.getName() + " a annulé l'échange.");
    }

    // ---------------------------------------------------------------- session

    private void startSession(Player player1, Player player2) {
        TradeSession session = new TradeSession(player1.getUniqueId(), player2.getUniqueId());
        activeTrades.put(player1.getUniqueId(), session);
        activeTrades.put(player2.getUniqueId(), session);

        player1.sendMessage("§8[§6Cantale§8] §aÉchange démarré avec §e" + player2.getName() + "§a.");
        player2.sendMessage("§8[§6Cantale§8] §aÉchange démarré avec §e" + player1.getName() + "§a.");

        openSessionGUI(player1, session);
        openSessionGUI(player2, session);
    }

    /** Ouvre (ou rouvre) le GUI d'échange pour ce joueur. */
    public void openSessionGUI(Player player, TradeSession session) {
        TradeSessionUI gui = new TradeSessionUI(plugin, player, session);
        openGuis.put(player.getUniqueId(), gui);
        gui.open();
    }

    public TradeSession getSession(UUID uuid) {
        return activeTrades.get(uuid);
    }

    public boolean hasActiveTrade(UUID uuid) {
        return activeTrades.containsKey(uuid);
    }

    /** Confirme / dé-confirme. La dé-confirmation annule le compte à rebours. */
    public void toggleConfirm(Player player) {
        TradeSession session = getSession(player.getUniqueId());
        if (session == null) return;

        boolean nowConfirmed = !session.isConfirmed(player.getUniqueId());
        session.setConfirmed(player.getUniqueId(), nowConfirmed);
        session.updateActivity();

        if (!nowConfirmed && session.isCountingDown()) {
            stopCountdown(session, player.getName() + " a retiré sa confirmation.");
        }

        if (nowConfirmed && session.bothConfirmed()) {
            startCountdown(session);
        }
        refreshGuis(session);
    }

    /** Dépose un item (depuis le curseur ou l'inventaire) dans l'offre du joueur. */
    public boolean depositItem(Player player, int offerIndex, ItemStack item) {
        TradeSession session = getSession(player.getUniqueId());
        if (session == null || item == null || item.getType().isAir()) return false;
        if (session.isConfirmed(player.getUniqueId())) {
            player.sendMessage("§8[§6Cantale§8] §cRetirez votre confirmation pour modifier votre offre.");
            return false;
        }
        int index = offerIndex >= 0 ? offerIndex : session.firstFreeSlot(player.getUniqueId());
        if (index == -1) {
            player.sendMessage("§8[§6Cantale§8] §cVotre zone d'offre est pleine.");
            return false;
        }
        session.setItem(player.getUniqueId(), index, item);
        onOfferChanged(session);
        return true;
    }

    /** Reprend l'item à l'index donné de l'offre du joueur (retour inventaire / sol). */
    public void withdrawItem(Player player, int offerIndex) {
        TradeSession session = getSession(player.getUniqueId());
        if (session == null) return;
        if (session.isConfirmed(player.getUniqueId())) {
            player.sendMessage("§8[§6Cantale§8] §cRetirez votre confirmation pour modifier votre offre.");
            return;
        }
        Map<Integer, ItemStack> items = session.getItems(player.getUniqueId());
        ItemStack item = items != null ? items.get(offerIndex) : null;
        if (item == null) return;
        session.setItem(player.getUniqueId(), offerIndex, null);
        giveOrDrop(player, item);
        onOfferChanged(session);
    }

    /** Toute modification d'offre : reset des confirmations + countdown, refresh des deux GUI. */
    private void onOfferChanged(TradeSession session) {
        if (session.isCountingDown()) {
            stopCountdown(session, "§8[§6Cantale§8] §eOffre modifiée : confirmations réinitialisées.");
        }
        session.resetConfirmations();
        refreshGuis(session);
    }

    // ---------------------------------------------------------------- argent

    /** Clic sur le bouton argent : fermeture du GUI puis saisie du montant dans le chat. */
    public void beginMoneyInput(Player player) {
        TradeSession session = getSession(player.getUniqueId());
        if (session == null) return;
        if (session.isConfirmed(player.getUniqueId())) {
            player.sendMessage("§8[§6Cantale§8] §cRetirez votre confirmation pour modifier le montant.");
            return;
        }
        moneyInputs.put(player.getUniqueId(), System.currentTimeMillis());
        player.closeInventory();
        player.sendMessage("§8[§6Cantale§8] §eTapez le montant à échanger dans le chat.");
        player.sendMessage("§7Solde : §6" + formatMoney(economyManager.getBalance(player))
            + " Cantox §7— tapez §cannuler §7pour revenir.");
    }

    public boolean isAwaitingMoneyInput(UUID uuid) {
        Long since = moneyInputs.get(uuid);
        if (since == null) return false;
        if (System.currentTimeMillis() - since > MONEY_INPUT_TIMEOUT_MS) {
            moneyInputs.remove(uuid);
            return false;
        }
        return true;
    }

    /** Traite la saisie chat du montant. Appelé par TradeListener (thread principal). */
    public void handleMoneyInput(Player player, String message) {
        TradeSession session = getSession(player.getUniqueId());
        moneyInputs.remove(player.getUniqueId());
        if (session == null) return;

        if (message.equalsIgnoreCase("annuler") || message.equalsIgnoreCase("cancel")) {
            player.sendMessage("§8[§6Cantale§8] §7Saisie annulée.");
            reopenGui(player, session);
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(message.replace(",", ".").trim());
        } catch (NumberFormatException e) {
            player.sendMessage("§8[§6Cantale§8] §cMontant invalide.");
            reopenGui(player, session);
            return;
        }
        double balance = economyManager.getBalance(player);
        if (amount < 0) amount = 0;
        if (amount > balance) {
            amount = balance;
            player.sendMessage("§8[§6Cantale§8] §eMontant plafonné à votre solde : §6"
                + formatMoney(amount) + " Cantox");
        }
        session.setMoney(player.getUniqueId(), amount);
        player.sendMessage("§8[§6Cantale§8] §aVous proposez §6" + formatMoney(amount) + " Cantox§a.");
        Player other = Bukkit.getPlayer(session.getOtherPlayer(player.getUniqueId()));
        if (other != null) {
            other.sendMessage("§8[§6Cantale§8] §e" + player.getName() + " §7propose désormais §6"
                + formatMoney(amount) + " Cantox§7.");
        }
        onOfferChanged(session);
        reopenGui(player, session);
    }

    /** Définit directement le montant (compat /trade money <montant>). */
    public void setMoney(Player player, double amount) {
        TradeSession session = getSession(player.getUniqueId());
        if (session == null) return;
        if (session.isConfirmed(player.getUniqueId())) {
            player.sendMessage("§8[§6Cantale§8] §cRetirez votre confirmation pour modifier le montant.");
            return;
        }
        double balance = economyManager.getBalance(player);
        if (amount < 0) amount = 0;
        if (amount > balance) amount = balance;
        session.setMoney(player.getUniqueId(), amount);
        onOfferChanged(session);
    }

    private void reopenGui(Player player, TradeSession session) {
        if (getSession(player.getUniqueId()) != session) return;
        openSessionGUI(player, session);
    }

    // ---------------------------------------------------------------- countdown

    private void startCountdown(TradeSession session) {
        session.startCountdown(COUNTDOWN_MS);
        for (UUID uuid : new UUID[]{session.getPlayer1Uuid(), session.getPlayer2Uuid()}) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage("§8[§6Cantale§8] §aLes deux joueurs ont confirmé ! §7Échange dans §e5 s§7...");
            }
        }
        java.util.concurrent.atomic.AtomicReference<BukkitTask> taskRef =
            new java.util.concurrent.atomic.AtomicReference<>();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int remaining = (int) (COUNTDOWN_MS / 1000);

            @Override
            public void run() {
                if (getSession(session.getPlayer1Uuid()) != session
                    || !session.isCountingDown() || !session.bothConfirmed()) {
                    BukkitTask self = taskRef.get();
                    if (self != null) self.cancel();
                    countdownTasks.remove(session);
                    return;
                }
                if (remaining <= 0) {
                    BukkitTask self = taskRef.get();
                    if (self != null) self.cancel();
                    countdownTasks.remove(session);
                    completeTrade(session);
                    return;
                }
                for (UUID uuid : new UUID[]{session.getPlayer1Uuid(), session.getPlayer2Uuid()}) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.sendActionBar(Component.text("Échange dans " + remaining + " s...",
                            NamedTextColor.GOLD, TextDecoration.BOLD));
                    }
                }
                refreshGuis(session);
                remaining--;
            }
        }, 20L, 20L);
        taskRef.set(task);
        countdownTasks.put(session, task);
    }

    private void stopCountdown(TradeSession session, String reason) {
        session.stopCountdown();
        BukkitTask task = countdownTasks.remove(session);
        if (task != null) task.cancel();
        if (reason != null) {
            for (UUID uuid : new UUID[]{session.getPlayer1Uuid(), session.getPlayer2Uuid()}) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendActionBar(Component.empty());
                    p.sendMessage(reason.startsWith("§") ? reason
                        : "§8[§6Cantale§8] §c" + reason + " §7Compte à rebours annulé.");
                }
            }
        }
    }

    // ---------------------------------------------------------------- fin d'échange

    private void completeTrade(TradeSession session) {
        UUID p1 = session.getPlayer1Uuid();
        UUID p2 = session.getPlayer2Uuid();
        Player player1 = Bukkit.getPlayer(p1);
        Player player2 = Bukkit.getPlayer(p2);

        // Les deux joueurs doivent être en ligne à la complétion (la déconnexion annule).
        if (player1 == null || player2 == null) {
            cancelTrade(session, "Échange annulé : un joueur s'est déconnecté.");
            return;
        }

        // Re-vérification des soldes au moment de l'échange (achat entre-temps possible).
        double money1 = session.getMoney(p1);
        double money2 = session.getMoney(p2);
        if (economyManager.getBalance(p1) < money1 || economyManager.getBalance(p2) < money2) {
            cancelTrade(session, "Échange annulé : solde insuffisant chez l'un des joueurs.");
            return;
        }

        // Retirer la session AVANT de fermer les GUI (le close event ne doit rien annuler).
        activeTrades.remove(p1);
        activeTrades.remove(p2);
        openGuis.remove(p1);
        openGuis.remove(p2);
        moneyInputs.remove(p1);
        moneyInputs.remove(p2);

        if (money1 > 0) {
            economyManager.withdraw(player1, money1);
            economyManager.deposit(player2, money1);
        }
        if (money2 > 0) {
            economyManager.withdraw(player2, money2);
            economyManager.deposit(player1, money2);
        }

        transferItems(session, p1, session.getItemList(p2)); // items de p2 → p1
        transferItems(session, p2, session.getItemList(p1)); // items de p1 → p2

        closeGuiIfOpen(player1);
        closeGuiIfOpen(player2);

        if (player1 != null) {
            player1.sendActionBar(Component.empty());
            player1.sendMessage("§8[§6Cantale§8] §aÉchange réussi avec §e" + nameOf(p2, player2) + " §a!");
        }
        if (player2 != null) {
            player2.sendActionBar(Component.empty());
            player2.sendMessage("§8[§6Cantale§8] §aÉchange réussi avec §e" + nameOf(p1, player1) + " §a!");
        }
        plugin.getLogger().info("[Trade] Échange complété entre " + nameOf(p1, player1) + " et "
            + nameOf(p2, player2) + " (" + session.getItemList(p1).size() + " item(s) + "
            + formatMoney(money1) + " Cantox contre " + session.getItemList(p2).size()
            + " item(s) + " + formatMoney(money2) + " Cantox)");
    }

    public void cancelTrade(TradeSession session, String reason) {
        UUID p1 = session.getPlayer1Uuid();
        UUID p2 = session.getPlayer2Uuid();

        stopCountdown(session, null);
        activeTrades.remove(p1);
        activeTrades.remove(p2);
        openGuis.remove(p1);
        openGuis.remove(p2);
        moneyInputs.remove(p1);
        moneyInputs.remove(p2);

        returnItemsTo(p1, session.getItemList(p1));
        returnItemsTo(p2, session.getItemList(p2));

        Player player1 = Bukkit.getPlayer(p1);
        Player player2 = Bukkit.getPlayer(p2);
        closeGuiIfOpen(player1);
        closeGuiIfOpen(player2);
        if (player1 != null) {
            player1.sendActionBar(Component.empty());
            player1.sendMessage("§8[§6Cantale§8] §c" + reason);
        }
        if (player2 != null) {
            player2.sendActionBar(Component.empty());
            player2.sendMessage("§8[§6Cantale§8] §c" + reason);
        }
    }

    /** Annule l'échange d'un joueur (fermeture du GUI, commande, etc.). */
    public void cancelTradeOf(Player player, String reason) {
        TradeSession session = getSession(player.getUniqueId());
        if (session != null) cancelTrade(session, reason);
    }

    private void transferItems(TradeSession session, UUID to, List<ItemStack> items) {
        if (items.isEmpty()) return;
        Player target = Bukkit.getPlayer(to);
        if (target == null) {
            pendingReturns.computeIfAbsent(to, k -> new ArrayList<>()).addAll(items);
            return;
        }
        for (ItemStack item : items) {
            giveOrDrop(target, item);
        }
    }

    private void returnItemsTo(UUID uuid, List<ItemStack> items) {
        if (items.isEmpty()) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            for (ItemStack item : items) {
                giveOrDrop(player, item);
            }
        } else {
            pendingReturns.computeIfAbsent(uuid, k -> new ArrayList<>()).addAll(items);
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
        leftovers.values().forEach(left ->
            player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    // ---------------------------------------------------------------- connexions

    /** Déconnexion : annule demandes et échange en cours. */
    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        pendingRequests.remove(uuid);
        pendingRequests.values().removeIf(req -> req.requester().equals(uuid));
        moneyInputs.remove(uuid);
        openGuis.remove(uuid);
        TradeSession session = activeTrades.get(uuid);
        if (session != null) {
            cancelTrade(session, player.getName() + " s'est déconnecté : échange annulé.");
        }
    }

    /** Connexion : rend les items bloqués par une annulation hors ligne. */
    public void handleJoin(Player player) {
        List<ItemStack> returns = pendingReturns.remove(player.getUniqueId());
        if (returns == null || returns.isEmpty()) return;
        for (ItemStack item : returns) {
            giveOrDrop(player, item);
        }
        player.sendMessage("§8[§6Cantale§8] §aLes items de votre échange annulé vous ont été rendus.");
    }

    // ---------------------------------------------------------------- utilitaires

    public void refreshGuis(TradeSession session) {
        for (UUID uuid : new UUID[]{session.getPlayer1Uuid(), session.getPlayer2Uuid()}) {
            TradeSessionUI gui = openGuis.get(uuid);
            if (gui != null) gui.refresh();
        }
    }

    /** Vrai si cet inventaire est le GUI d'échange actuellement ouvert pour ce joueur. */
    public boolean isTradeInventory(Player player, org.bukkit.inventory.Inventory inventory) {
        TradeSessionUI gui = openGuis.get(player.getUniqueId());
        return gui != null && gui.getInventory() == inventory;
    }

    private void closeGuiIfOpen(Player player) {
        if (player == null) return;
        TradeSessionUI gui = openGuis.get(player.getUniqueId());
        if (gui != null && player.getOpenInventory().getTopInventory() == gui.getInventory()) {
            player.closeInventory();
        }
    }

    private String nameOf(UUID uuid, Player online) {
        if (online != null) return online.getName();
        org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        return off.getName() != null ? off.getName() : uuid.toString();
    }

    private String formatMoney(double amount) {
        return amount == Math.floor(amount) ? String.valueOf((long) amount) : String.format("%.2f", amount);
    }

    private void cleanupExpired() {
        for (TradeSession session : new HashMap<>(activeTrades).values()) {
            if (session.isExpired(TRADE_TIMEOUT_MS)) {
                cancelTrade(session, "Échange expiré (inactivité).");
            }
        }
        pendingRequests.values().removeIf(PendingRequest::isExpired);
        moneyInputs.entrySet().removeIf(e ->
            System.currentTimeMillis() - e.getValue() > MONEY_INPUT_TIMEOUT_MS);
    }
}
