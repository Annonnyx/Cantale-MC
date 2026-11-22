package fr.cantale.plugin.ui.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.events.FactionEventManager;
import fr.cantale.plugin.privatechest.ChestType;
import fr.cantale.plugin.privatechest.PrivateChestHolder;
import fr.cantale.plugin.rank.PlayerRank;
import fr.cantale.plugin.ui.inventory.BaseUI;
import fr.cantale.plugin.ui.inventory.CrateOpenUI;
import fr.cantale.plugin.ui.inventory.TradeSessionUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Canal {@code cantale:ui}. Le client dessine, le plugin décide.
 * Sans handshake, les menus restent les coffres {@link BaseUI}.
 */
public final class ClientUiBridge implements Listener, PluginMessageListener {

    public static final String CHANNEL = "cantale:ui";
    public static final int PROTOCOL_VERSION = 1;

    private static final int MAX_CLIENT_BYTES = 2048;
    private static final int MAX_SERVER_BYTES = 30_000;

    private static final Set<String> BOARDS = Set.of(
            "ProfileUI", "FactionMainUI", "FactionMembersUI", "MemberActionUI", "LifeUI",
            "QuestMainUI", "QuestFactionUI", "QuestSoloUI", "QuestCategorySelectUI",
            "LeaderboardUI", "LeaderboardCategoryUI", "StatisticsUI", "WantedUI",
            "CollectionMainUI", "CollectionCategoryUI", "CollectionTiersUI",
            "EconomyUI", "FactionClaimsUI", "FactionHomesUI", "FactionWarpsUI",
            "FactionPowerShopUI", "FactionBankUI", "FactionBankHistoryUI",
            "TagCustomizationUI", "ConfirmDialogUI"
    );

    private final CantalePlugin plugin;
    private final Set<UUID> modded = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private BukkitTask pulse;

    public ClientUiBridge(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        pulse = Bukkit.getScheduler().runTaskTimer(plugin, this::pulseCrates, 4L, 4L);
        plugin.getLogger().info("[Ui] Canal cantale:ui enregistré.");
    }

    public void shutdown() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
        for (Map.Entry<UUID, Session> entry : new ArrayList<>(sessions.entrySet())) {
            if (entry.getValue().kind != Kind.STORAGE) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) saveStorage(player, entry.getValue());
        }
        sessions.clear();
        modded.clear();
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }

    public boolean isModded(Player player) {
        return player != null && modded.contains(player.getUniqueId());
    }

    public boolean isShowing(Player player, BaseUI ui) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.ui == ui;
    }

    /** Ouvre un menu déjà initialisé. Faux si le joueur n'a pas le mod : le coffre s'ouvre. */
    public boolean presentMenu(Player player, BaseUI ui) {
        if (!isModded(player)) return false;
        Session session = new Session(Kind.MENU, ui, ui, ui.getInventory(), ui.getTitle(), -1);
        sessions.put(player.getUniqueId(), session);
        if (!sendMenu(player, ui)) {
            sessions.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public void pushMenu(Player player, BaseUI ui) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.ui != ui) return;
        sendMenu(player, ui);
    }

    public boolean presentStorage(Player player, Inventory inventory, String title, boolean hasNavigation) {
        if (!isModded(player) || inventory == null) return false;
        int navStart = hasNavigation ? inventory.getSize() - 9 : -1;
        Session session = new Session(Kind.STORAGE, inventory, null, inventory, title, navStart);
        sessions.put(player.getUniqueId(), session);
        if (!sendStorage(player, session)) {
            sessions.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public boolean presentHarvest(Player player, String label) {
        if (!isModded(player)) return false;
        Session session = new Session(Kind.HARVEST, "harvest", null, null, "Récolte", -1);
        sessions.put(player.getUniqueId(), session);
        JsonObject msg = baseScreen("actions", "Récolte");
        msg.addProperty("hint", "Le " + label + " est pris dans ton inventaire.");
        JsonArray actions = new JsonArray();
        actions.add(action("deposit", "Déposer tout le " + label, true));
        actions.add(action("cancel", "Annuler", false));
        msg.add("actions", actions);
        if (!send(player, msg)) {
            sessions.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    /** Ferme l'écran du mod s'il montre ce menu. */
    public boolean dismissIfMenu(Player player, BaseUI ui) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.kind != Kind.MENU || session.ui != ui) return false;
        sessions.remove(player.getUniqueId());
        sendClose(player);
        if (ui instanceof TradeSessionUI) scheduleTradeClose(player);
        return true;
    }

    /** Ferme l'écran du mod, quel que soit le menu (échange annulé, saisie d'argent). */
    public void dismissOpen(Player player) {
        if (player == null) return;
        Session session = sessions.remove(player.getUniqueId());
        if (session == null || session.kind != Kind.MENU) {
            if (session != null) sessions.put(player.getUniqueId(), session);
            return;
        }
        sendClose(player);
        if (session.ui instanceof TradeSessionUI) scheduleTradeClose(player);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || player == null || message == null) return;
        if (message.length == 0 || message.length > MAX_CLIENT_BYTES) return;
        String payload = new String(message, StandardCharsets.UTF_8).trim();
        if (Bukkit.isPrimaryThread()) {
            handle(player, payload);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> handle(player, payload));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        modded.remove(player.getUniqueId());
        Session session = sessions.remove(player.getUniqueId());
        if (session != null && session.kind == Kind.STORAGE) {
            saveStorage(player, session);
        }
    }

    private void handle(Player player, String payload) {
        try {
            if (payload.regionMatches(true, 0, "HELLO", 0, 5)) {
                acceptHello(player);
                return;
            }
            JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
            String type = opt(obj, "type");
            if ("hello".equalsIgnoreCase(type)) {
                acceptHello(player);
                return;
            }
            if (!modded.contains(player.getUniqueId())) return;
            if ("close".equals(type)) {
                onClientClose(player);
                return;
            }
            if ("click".equals(type)) {
                onClientClick(player, obj);
            }
        } catch (Exception e) {
            plugin.getLogger().fine("[Ui] Message ignoré de " + player.getName() + " : " + e.getMessage());
        }
    }

    private void acceptHello(Player player) {
        boolean first = modded.add(player.getUniqueId());
        JsonObject welcome = new JsonObject();
        welcome.addProperty("v", PROTOCOL_VERSION);
        welcome.addProperty("type", "welcome");
        welcome.addProperty("protocol", PROTOCOL_VERSION);
        send(player, welcome);
        if (first) {
            plugin.getLogger().info("[Ui] " + player.getName() + " utilise les menus du mod.");
        }
    }

    private void onClientClose(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        if (session.kind == Kind.STORAGE) saveStorage(player, session);
        if (session.kind == Kind.MENU && session.ui instanceof TradeSessionUI) scheduleTradeClose(player);
    }

    private void onClientClick(Player player, JsonObject obj) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (obj.has("action")) {
            String text = "";
            if (obj.has("text") && obj.get("text").isJsonPrimitive()) {
                text = obj.get("text").getAsString().replace('\n', ' ').replace('\r', ' ').trim();
                if (text.length() > 32) text = text.substring(0, 32);
            }
            onAction(player, session, obj.get("action").getAsString(), text);
            return;
        }
        if (!obj.has("slot")) return;
        int slot = obj.get("slot").getAsInt();
        boolean right = obj.has("button") && obj.get("button").getAsInt() == 1;
        boolean shift = obj.has("shift") && obj.get("shift").getAsBoolean();
        Object token = session.token;
        if (session.kind == Kind.MENU && session.ui != null) {
            if (slot < 0 || slot >= session.ui.getSize()) return;
            session.ui.clientClick(slot, right, shift);
        } else if (session.kind == Kind.STORAGE) {
            storageClick(player, session, slot, right, shift);
        }
        Session now = sessions.get(player.getUniqueId());
        if (now == null || now.token != token) return;
        if (now.kind == Kind.MENU && now.ui != null) sendMenu(player, now.ui);
        else if (now.kind == Kind.STORAGE) sendStorage(player, now);
    }

    private void onAction(Player player, Session session, String id, String text) {
        if (session.kind == Kind.MENU && session.ui != null) {
            Object token = session.token;
            session.ui.clientAction(id, text == null ? "" : text);
            Session now = sessions.get(player.getUniqueId());
            if (now == null || now.token != token) return;
            if (now.kind == Kind.MENU && now.ui != null) sendMenu(player, now.ui);
            return;
        }
        if (session.kind != Kind.HARVEST) return;
        if ("cancel".equals(id)) {
            sessions.remove(player.getUniqueId());
            sendClose(player);
            return;
        }
        if (!"deposit".equals(id)) return;
        FactionEventManager events = plugin.getFactionEventManager();
        if (events == null) return;
        Material target = events.getActiveTarget();
        if (target == null) {
            player.sendMessage("§8[§6Récolte§8] §cPas de récolte en cours.");
            return;
        }
        List<ItemStack> matched = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && stack.getType() == target) {
                matched.add(stack.clone());
                slots.add(i);
            }
        }
        ItemStack[] arr = matched.toArray(ItemStack[]::new);
        events.depositHarvest(player, arr);
        boolean removed = false;
        for (int n = 0; n < arr.length; n++) {
            if (arr[n] == null) {
                player.getInventory().setItem(slots.get(n), null);
                removed = true;
            }
        }
        if (removed) {
            sessions.remove(player.getUniqueId());
            sendClose(player);
        }
    }

    private void storageClick(Player player, Session session, int slot, boolean right, boolean shift) {
        Inventory inventory = session.inventory;
        if (inventory == null || slot < 0 || slot >= inventory.getSize()) return;
        if (session.navStart >= 0 && slot >= session.navStart) {
            ItemStack nav = inventory.getItem(slot);
            if (plugin.getPrivateChestService().isNavItem(nav)) openNextChest(player, inventory);
            return;
        }
        moveStorage(player, inventory, slot, right, shift);
        ChestType type = typeOf(inventory, session.title);
        if (type != null) plugin.getPrivateChestService().saveChest(player, inventory, type, false);
    }

    private void openNextChest(Player player, Inventory inventory) {
        PrivateChestHolder holder = inventory.getHolder() instanceof PrivateChestHolder h ? h : null;
        ChestType type = holder != null ? holder.getType() : null;
        if (type == null) return;
        plugin.getPrivateChestService().saveChest(player, inventory, type, false);
        PlayerRank rank;
        if (holder != null && holder.isForeignInspect() && holder.getOwnerUuid() != null) {
            rank = plugin.getRankManager().getRank(holder.getOwnerUuid());
        } else if (player.hasPermission("cantale.admin")) {
            rank = PlayerRank.CHEVRE;
        } else {
            rank = plugin.getRankManager().getRank(player);
        }
        ChestType next = plugin.getPrivateChestService().getNextPrivateChest(rank, type);
        if (holder != null && holder.isForeignInspect() && holder.getOwnerUuid() != null) {
            UUID owner = holder.getOwnerUuid();
            String name = Bukkit.getOfflinePlayer(owner).getName();
            plugin.getPrivateChestService().openForeignChest(player, owner, name, next);
        } else {
            plugin.getPrivateChestService().openChest(player, next, player.hasPermission("cantale.admin"));
        }
    }

    private void moveStorage(Player player, Inventory inventory, int slot, boolean right, boolean shift) {
        ItemStack slotStack = inventory.getItem(slot);
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean handEmpty = hand == null || hand.getType().isAir();
        boolean slotEmpty = slotStack == null || slotStack.getType().isAir();
        if (slotEmpty && handEmpty) return;
        if (slotEmpty) {
            placeHand(player, hand, inventory, slot, right && !shift);
            return;
        }
        if (handEmpty || shift) {
            takeSlot(player, inventory, slot, slotStack, right && !shift);
            return;
        }
        if (hand.isSimilar(slotStack)) {
            int space = slotStack.getMaxStackSize() - slotStack.getAmount();
            if (space <= 0) {
                swap(player, inventory, slot, slotStack, hand);
                return;
            }
            int moved = Math.min(space, right ? 1 : hand.getAmount());
            slotStack.setAmount(slotStack.getAmount() + moved);
            shrinkHand(player, hand, moved);
            return;
        }
        swap(player, inventory, slot, slotStack, hand);
    }

    private void placeHand(Player player, ItemStack hand, Inventory inventory, int slot, boolean one) {
        int amount = Math.min(one ? 1 : hand.getAmount(), hand.getMaxStackSize());
        ItemStack placed = hand.clone();
        placed.setAmount(amount);
        inventory.setItem(slot, placed);
        shrinkHand(player, hand, amount);
    }

    private void takeSlot(Player player, Inventory inventory, int slot, ItemStack slotStack, boolean one) {
        int amount = Math.min(one ? 1 : slotStack.getAmount(), slotStack.getAmount());
        ItemStack taken = slotStack.clone();
        taken.setAmount(amount);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(taken);
        int left = 0;
        for (ItemStack extra : leftover.values()) left += extra.getAmount();
        int accepted = amount - left;
        if (accepted <= 0) return;
        int remain = slotStack.getAmount() - accepted;
        if (remain <= 0) inventory.setItem(slot, null);
        else slotStack.setAmount(remain);
    }

    private void swap(Player player, Inventory inventory, int slot, ItemStack slotStack, ItemStack hand) {
        inventory.setItem(slot, hand.clone());
        player.getInventory().setItemInMainHand(slotStack.clone());
    }

    private void shrinkHand(Player player, ItemStack hand, int amount) {
        hand.setAmount(hand.getAmount() - amount);
        if (hand.getAmount() <= 0) player.getInventory().setItemInMainHand(null);
    }

    private void saveStorage(Player player, Session session) {
        if (session.inventory == null) return;
        ChestType type = typeOf(session.inventory, session.title);
        if (type != null) plugin.getPrivateChestService().saveChest(player, session.inventory, type, false);
    }

    private ChestType typeOf(Inventory inventory, String title) {
        if (inventory.getHolder() instanceof PrivateChestHolder holder) return holder.getType();
        return plugin.getPrivateChestService().resolveTypeFromTitle(title);
    }

    private void scheduleTradeClose(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (plugin.getTradeManager().isAwaitingMoneyInput(player.getUniqueId())) return;
            var trade = plugin.getTradeManager().getSession(player.getUniqueId());
            if (trade == null) return;
            Session open = sessions.get(player.getUniqueId());
            if (open != null && open.ui instanceof TradeSessionUI) return;
            plugin.getTradeManager().cancelTrade(trade, "trade.closed", player.getName());
        });
    }

    private void pulseCrates() {
        for (Map.Entry<UUID, Session> entry : new ArrayList<>(sessions.entrySet())) {
            Session session = entry.getValue();
            if (!(session.ui instanceof CrateOpenUI)) continue;
            int fingerprint = fingerprint(session.inventory);
            if (fingerprint == session.hash) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            sendMenu(player, session.ui);
        }
    }

    private boolean sendMenu(Player player, BaseUI ui) {
        boolean grid = ui instanceof TradeSessionUI;
        String mode = modeFor(ui);
        JsonObject msg = baseScreen(mode, ui.getTitle());
        msg.addProperty("cols", 9);
        msg.add("slots", slotsOf(ui.getInventory(), ui.getSize(), grid));
        ui.writeClientDetail(msg);
        Session session = sessions.get(player.getUniqueId());
        if (session != null && session.ui == ui) session.hash = fingerprint(ui.getInventory());
        return send(player, msg);
    }

    private boolean sendStorage(Player player, Session session) {
        JsonObject msg = baseScreen("storage", session.title);
        msg.addProperty("cols", 9);
        JsonArray slots = new JsonArray();
        Inventory inventory = session.inventory;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            String role;
            if (session.navStart >= 0 && i >= session.navStart) {
                role = plugin.getPrivateChestService().isNavItem(item) ? "item" : "pad";
            } else if (item == null || item.getType().isAir()) {
                role = "cell";
            } else {
                role = "item";
            }
            slots.add(slotJson(i, item, role));
        }
        msg.add("slots", slots);
        return send(player, msg);
    }

    private JsonArray slotsOf(Inventory inventory, int size, boolean keepEmpty) {
        JsonArray slots = new JsonArray();
        for (int i = 0; i < size; i++) {
            ItemStack item = inventory.getItem(i);
            boolean empty = item == null || item.getType().isAir() || blankPane(item);
            if (keepEmpty) {
                slots.add(slotJson(i, item, empty ? "cell" : "item"));
                continue;
            }
            if (empty) continue;
            String role = "content";
            Material type = item.getType();
            if (i == size - 9 && type == Material.ARROW) role = "back";
            else if (i == size - 5 && type == Material.BARRIER) role = "close";
            else if (i == size - 1 && type == Material.COMPASS) role = "refresh";
            slots.add(slotJson(i, item, role));
        }
        return slots;
    }

    private JsonObject slotJson(int index, ItemStack item, String role) {
        JsonObject slot = new JsonObject();
        slot.addProperty("i", index);
        slot.addProperty("role", role);
        if (item == null || item.getType().isAir() || "cell".equals(role) || "pad".equals(role)) {
            slot.addProperty("item", "AIR");
            slot.addProperty("name", "");
            slot.addProperty("amt", 0);
            slot.add("lore", new JsonArray());
            return slot;
        }
        slot.addProperty("item", item.getType().name());
        slot.addProperty("amt", item.getAmount());
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
        if (name.isBlank()) name = item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        slot.addProperty("name", clip(name, 80));
        JsonArray lore = new JsonArray();
        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            int lines = 0;
            for (String line : meta.getLore()) {
                if (lines++ >= 8) break;
                lore.add(clip(line, 100));
            }
        }
        slot.add("lore", lore);
        return slot;
    }

    private String modeFor(BaseUI ui) {
        String name = ui.getClass().getSimpleName();
        return switch (name) {
            case "MainMenuUI" -> "home";
            case "CrateOpenUI" -> "crate";
            case "TradeSessionUI" -> "grid";
            case "ProfileUI" -> "profile";
            case "LifeUI" -> "lives";
            case "StatisticsUI" -> "stats";
            case "FactionMainUI" -> "faction";
            case "WantedUI" -> "bounties";
            case "LeaderboardUI" -> "ranks";
            case "LeaderboardCategoryUI" -> "podium";
            case "QuestMainUI" -> "quests";
            case "EconomyUI" -> "economy";
            default -> BOARDS.contains(name) ? "board" : "icons";
        };
    }

    private JsonObject baseScreen(String mode, String title) {
        JsonObject msg = new JsonObject();
        msg.addProperty("v", PROTOCOL_VERSION);
        msg.addProperty("type", "screen");
        msg.addProperty("mode", mode);
        msg.addProperty("title", title == null ? "" : title);
        return msg;
    }

    private JsonObject action(String id, String label, boolean primary) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("label", label);
        obj.addProperty("primary", primary);
        return obj;
    }

    private boolean send(Player player, JsonObject msg) {
        String json = msg.toString();
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_SERVER_BYTES && msg.has("slots")) {
            for (var element : msg.getAsJsonArray("slots")) {
                element.getAsJsonObject().remove("lore");
            }
            data = msg.toString().getBytes(StandardCharsets.UTF_8);
        }
        if (data.length > 32_000) {
            plugin.getLogger().warning("[Ui] Écran trop lourd pour " + player.getName());
            return false;
        }
        try {
            player.sendPluginMessage(plugin, CHANNEL, data);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[Ui] Envoi impossible vers " + player.getName() + " : " + e.getMessage());
            return false;
        }
    }

    private void sendClose(Player player) {
        JsonObject msg = new JsonObject();
        msg.addProperty("v", PROTOCOL_VERSION);
        msg.addProperty("type", "close");
        send(player, msg);
    }

    private boolean blankPane(ItemStack item) {
        if (item == null || item.getType().isAir()) return true;
        if (!item.getType().name().contains("GLASS_PANE")) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return true;
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        return name == null || name.isBlank();
    }

    private int fingerprint(Inventory inventory) {
        if (inventory == null) return 0;
        int hash = 1;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            hash = 31 * hash + i;
            if (stack == null || stack.getType().isAir()) continue;
            hash = 31 * hash + stack.getType().ordinal();
            hash = 31 * hash + stack.getAmount();
            if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
                hash = 31 * hash + stack.getItemMeta().getDisplayName().hashCode();
            }
        }
        return hash;
    }

    private String clip(String value, int max) {
        if (value == null) return "";
        String plain = ChatColor.stripColor(value);
        if (plain.length() <= max) return value;
        return plain.substring(0, max);
    }

    private String opt(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : "";
    }

    private enum Kind { MENU, STORAGE, HARVEST }

    private static final class Session {
        private final Kind kind;
        private final Object token;
        private final BaseUI ui;
        private final Inventory inventory;
        private final String title;
        private final int navStart;
        private int hash;

        private Session(Kind kind, Object token, BaseUI ui, Inventory inventory, String title, int navStart) {
            this.kind = kind;
            this.token = token;
            this.ui = ui;
            this.inventory = inventory;
            this.title = title;
            this.navStart = navStart;
        }
    }
}
