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
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
    private final Map<UUID, List<PendingReturn>> pendingReturns = new HashMap<>(); // items à rendre au login
    private final TradePendingReturnDAO pendingStore;
    private final Map<TradeSession, BukkitTask> countdownTasks = new HashMap<>();

    private record PendingRequest(UUID requester, String requesterName, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > REQUEST_TIMEOUT_MS;
        }
    }

    private record PendingReturn(long id, ItemStack item) {}

    public TradeManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.pendingStore = new TradePendingReturnDAO(plugin);
        loadPendingReturns();
        // Balayage périodique : demandes, sessions et saisies expirées.
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpired, 20L * 30, 20L * 30);
    }

    private void loadPendingReturns() {
        pendingStore.abandonUnackedClaims();
        for (TradePendingReturnDAO.StoredItem stored : pendingStore.loadAll()) {
            pendingReturns.computeIfAbsent(stored.playerId(), key -> new ArrayList<>())
                    .add(new PendingReturn(stored.id(), stored.item().clone()));
        }
    }

    // ---------------------------------------------------------------- demandes

    public void sendTradeRequest(Player requester, Player target) {
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            plugin.messages().send(requester, "trade.self");
            return;
        }
        if (hasActiveTrade(requester.getUniqueId())) {
            plugin.messages().send(requester, "trade.busy-self");
            return;
        }
        if (hasActiveTrade(target.getUniqueId())) {
            plugin.messages().send(requester, "trade.busy-other", target.getName());
            return;
        }
        PendingRequest existing = pendingRequests.get(target.getUniqueId());
        if (existing != null && !existing.isExpired()) {
            plugin.messages().send(requester, "trade.pending", target.getName());
            return;
        }

        pendingRequests.put(target.getUniqueId(),
            new PendingRequest(requester.getUniqueId(), requester.getName(), System.currentTimeMillis()));

        plugin.messages().send(requester, "trade.sent", target.getName());

        target.sendMessage(plugin.messages().component(target, "prefix")
            .append(plugin.messages().component(target, "trade.prompt", requester.getName()))
            .append(Component.text(plugin.messages().raw(target, "trade.accept"), NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/trade accept"))
                .hoverEvent(HoverEvent.showText(Component.text(
                        plugin.messages().raw(target, "trade.hover-accept"), NamedTextColor.GREEN))))
            .append(Component.text(" "))
            .append(Component.text(plugin.messages().raw(target, "trade.refuse"), NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/trade decline"))
                .hoverEvent(HoverEvent.showText(Component.text(
                        plugin.messages().raw(target, "trade.hover-refuse"), NamedTextColor.RED)))));
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
                plugin.messages().send(player, "trade.offline", req.requesterName());
                return;
            }
            if (hasActiveTrade(requester.getUniqueId()) || hasActiveTrade(player.getUniqueId())) {
                plugin.messages().send(player, "trade.cannot-start");
                return;
            }
            startSession(requester, player);
            return;
        }
        if (req != null) {
            plugin.messages().send(player, "trade.expired");
            return;
        }

        TradeSession session = getSession(player.getUniqueId());
        if (session == null) {
            plugin.messages().send(player, "trade.none");
            return;
        }
        if (!session.isConfirmed(player.getUniqueId())) {
            toggleConfirm(player);
        } else {
            plugin.messages().send(player, "trade.confirmed");
        }
    }

    /** Refus d'une demande ou annulation d'un échange en cours. */
    public void decline(Player player) {
        PendingRequest req = pendingRequests.remove(player.getUniqueId());
        if (req != null) {
            plugin.messages().send(player, "trade.refused");
            Player requester = Bukkit.getPlayer(req.requester());
            if (requester != null) {
                plugin.messages().send(requester, "trade.refused-other", player.getName());
            }
            return;
        }
        TradeSession session = getSession(player.getUniqueId());
        if (session == null) {
            plugin.messages().send(player, "trade.none");
            return;
        }
        cancelTrade(session, "trade.cancelled-by", player.getName());
    }

    // ---------------------------------------------------------------- session

    private void startSession(Player player1, Player player2) {
        TradeSession session = new TradeSession(player1.getUniqueId(), player2.getUniqueId());
        activeTrades.put(player1.getUniqueId(), session);
        activeTrades.put(player2.getUniqueId(), session);

        plugin.messages().send(player1, "trade.started", player2.getName());
        plugin.messages().send(player2, "trade.started", player1.getName());

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
            stopCountdown(session, "trade.unconfirmed", player.getName());
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
            plugin.messages().send(player, "trade.locked");
            return false;
        }
        int index = offerIndex >= 0 ? offerIndex : session.firstFreeSlot(player.getUniqueId());
        if (index == -1) {
            plugin.messages().send(player, "trade.full");
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
            plugin.messages().send(player, "trade.locked");
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
            stopCountdown(session, "trade.offer-changed");
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
            plugin.messages().send(player, "trade.money-locked");
            return;
        }
        moneyInputs.put(player.getUniqueId(), System.currentTimeMillis());
        player.closeInventory();
        plugin.messages().send(player, "trade.money-prompt");
        plugin.messages().send(player, "trade.money-balance", formatMoney(economyManager.getBalance(player)));
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
            plugin.messages().send(player, "trade.money-cancel");
            reopenGui(player, session);
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(message.replace(",", ".").trim());
        } catch (NumberFormatException e) {
            plugin.messages().send(player, "trade.money-bad");
            reopenGui(player, session);
            return;
        }
        double balance = economyManager.getBalance(player);
        if (amount < 0) amount = 0;
        if (amount > balance) {
            amount = balance;
            plugin.messages().send(player, "trade.money-capped", formatMoney(amount));
        }
        session.setMoney(player.getUniqueId(), amount);
        plugin.messages().send(player, "trade.money-set", formatMoney(amount));
        Player other = Bukkit.getPlayer(session.getOtherPlayer(player.getUniqueId()));
        if (other != null) {
            plugin.messages().send(other, "trade.money-other", player.getName(), formatMoney(amount));
        }
        onOfferChanged(session);
        reopenGui(player, session);
    }

    /** Définit directement le montant (compat /trade money <montant>). */
    public void setMoney(Player player, double amount) {
        TradeSession session = getSession(player.getUniqueId());
        if (session == null) return;
        if (session.isConfirmed(player.getUniqueId())) {
            plugin.messages().send(player, "trade.money-locked");
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
                plugin.messages().send(p, "trade.both-confirmed");
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
                        p.sendActionBar(Component.text(plugin.messages().raw(p, "trade.countdown", remaining),
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

    private void stopCountdown(TradeSession session, String key, Object... args) {
        session.stopCountdown();
        BukkitTask task = countdownTasks.remove(session);
        if (task != null) task.cancel();
        if (key != null) {
            for (UUID uuid : new UUID[]{session.getPlayer1Uuid(), session.getPlayer2Uuid()}) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendActionBar(Component.empty());
                    plugin.messages().send(p, key, args);
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
            cancelTrade(session, "trade.disconnect");
            return;
        }

        // Re-vérification des soldes au moment de l'échange (achat entre-temps possible).
        double money1 = session.getMoney(p1);
        double money2 = session.getMoney(p2);
        if (economyManager.getBalance(p1) < money1 || economyManager.getBalance(p2) < money2) {
            cancelTrade(session, "trade.funds");
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
            plugin.messages().send(player1, "trade.success", nameOf(p2, player2));
        }
        if (player2 != null) {
            player2.sendActionBar(Component.empty());
            plugin.messages().send(player2, "trade.success", nameOf(p1, player1));
        }
        plugin.getLogger().info("[Trade] Échange complété entre " + nameOf(p1, player1) + " et "
            + nameOf(p2, player2) + " (" + session.getItemList(p1).size() + " item(s) + "
            + formatMoney(money1) + " Cantox contre " + session.getItemList(p2).size()
            + " item(s) + " + formatMoney(money2) + " Cantox)");
    }

    public void cancelTrade(TradeSession session, String key, Object... args) {
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
            plugin.messages().send(player1, key, args);
        }
        if (player2 != null) {
            player2.sendActionBar(Component.empty());
            plugin.messages().send(player2, key, args);
        }
    }

    /** Annule l'échange d'un joueur (fermeture du GUI, commande, etc.). */
    public void cancelTradeOf(Player player, String key, Object... args) {
        TradeSession session = getSession(player.getUniqueId());
        if (session != null) cancelTrade(session, key, args);
    }

    private void transferItems(TradeSession session, UUID to, List<ItemStack> items) {
        if (items.isEmpty()) return;
        Player target = Bukkit.getPlayer(to);
        if (target == null) {
            rememberOffline(to, items);
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
            rememberOffline(uuid, items);
        }
    }

    private void rememberOffline(UUID uuid, List<ItemStack> items) {
        synchronized (pendingReturns) {
            List<PendingReturn> queued = pendingReturns.computeIfAbsent(uuid, key -> new ArrayList<>());
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                ItemStack copy = item.clone();
                long id = pendingStore.insert(uuid, copy);
                queued.add(new PendingReturn(id, copy));
            }
        }
    }

    /**
     * @return {@code false} seulement si rien n'a été posé (joueur absent ou item vide)
     */
    private boolean placeReturn(Player player, ItemStack item) {
        if (player == null || !player.isOnline() || item == null || item.getType().isAir()) {
            return false;
        }
        boolean stored = false;
        try {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            stored = true;
            World world = player.getWorld();
            Location location = player.getLocation();
            for (ItemStack left : leftovers.values()) {
                if (left != null && !left.getType().isAir()) {
                    world.dropItemNaturally(location, left);
                }
            }
            return true;
        } catch (RuntimeException e) {
            if (!stored) {
                return false;
            }
            plugin.getLogger().warning("[Trade] Surplus non déposé au sol : " + e.getMessage());
            return true;
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        placeReturn(player, item);
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
            cancelTrade(session, "trade.quit", player.getName());
        }
    }

    /** Connexion : rend les items bloqués par une annulation hors ligne. */
    public void handleJoin(Player player) {
        List<PendingReturn> memory;
        synchronized (pendingReturns) {
            memory = pendingReturns.remove(player.getUniqueId());
        }
        boolean gave = false;
        for (TradePendingReturnDAO.StoredItem stored : pendingStore.load(player.getUniqueId())) {
            if (deliverPersisted(player, stored.id(), stored.item())) {
                gave = true;
            }
        }
        if (memory != null) {
            for (PendingReturn pending : memory) {
                if (pending.id() <= 0 && placeReturn(player, pending.item())) {
                    gave = true;
                }
            }
        }
        if (gave) {
            plugin.messages().send(player, "trade.returned");
        }
    }

    /**
     * Donne au plus une fois. Un crash après le don n'est pas rejoué si l'objet
     * est encore chez le joueur ou si l'accusé a été écrit. Un don qui n'a pas
     * eu lieu laisse la ligne en base.
     */
    private boolean deliverPersisted(Player player, long id, ItemStack item) {
        boolean witnessed = alreadyHolding(player, id);
        TradePendingReturnDAO.Claim claim = pendingStore.claim(id, witnessed);
        if (claim.delivery() != TradePendingReturnRepository.Delivery.GIVE) {
            return false;
        }
        if (!placeReturn(player, tagReturn(item, id))) {
            pendingStore.release(id, claim.token());
            return false;
        }
        if (!pendingStore.acknowledge(id)) {
            plugin.getLogger().severe("[Trade] Don effectué mais accusé impossible (id=" + id + "). "
                    + "Le marqueur sur l'objet empêchera un second don.");
            return true;
        }
        pendingStore.finishAcked(id);
        return true;
    }

    private ItemStack tagReturn(ItemStack source, long id) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(returnKey(), PersistentDataType.LONG, id);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean alreadyHolding(Player player, long id) {
        if (player == null || id <= 0) {
            return false;
        }
        if (containsReturn(player.getItemOnCursor(), id)) {
            return true;
        }
        ItemStack[] contents = player.getInventory().getContents();
        if (contents != null) {
            for (ItemStack stack : contents) {
                if (containsReturn(stack, id)) {
                    return true;
                }
            }
        }
        World world = player.getWorld();
        if (world == null) {
            return false;
        }
        Location location = player.getLocation();
        for (Entity entity : world.getNearbyEntities(location, 8, 8, 8)) {
            if (entity instanceof Item dropped && containsReturn(dropped.getItemStack(), id)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsReturn(ItemStack stack, long id) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Long marked = meta.getPersistentDataContainer().get(returnKey(), PersistentDataType.LONG);
        return marked != null && marked == id;
    }

    private NamespacedKey returnKey() {
        return new NamespacedKey(plugin, "trade_return");
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
                cancelTrade(session, "trade.idle");
            }
        }
        pendingRequests.values().removeIf(PendingRequest::isExpired);
        moneyInputs.entrySet().removeIf(e ->
            System.currentTimeMillis() - e.getValue() > MONEY_INPUT_TIMEOUT_MS);
    }
}
