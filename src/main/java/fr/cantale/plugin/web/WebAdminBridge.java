package fr.cantale.plugin.web;

import com.google.gson.JsonObject;
import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.custom.CustomItemType;
import fr.cantale.plugin.privatechest.ChestType;
import fr.cantale.plugin.privatechest.PrivateChestService;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Exécute les actions admin publiées sur Redis {@code cantale:admin:actions}.
 * La table {@code web_admin_actions} reste l'archive (UPDATE de statut), sans SELECT périodique.
 */
public class WebAdminBridge {

    private static final int RESULT_MAX = 255;
    private static final int PASDIC_RESULT_MAX = 65000;

    private final CantalePlugin plugin;
    private final AdminInventoryMutator inventoryMutator;

    public WebAdminBridge(CantalePlugin plugin) {
        this.plugin = plugin;
        this.inventoryMutator = new AdminInventoryMutator(plugin);
    }

    public void start() {
        plugin.getLogger().info("[Web] WebAdminBridge branché sur Redis "
                + fr.cantale.plugin.cache.RedisChannels.ADMIN_ACTIONS
                + " (polling SQL désactivé, archivage conservé).");
    }

    public void stop() {
        // L'abonnement est fermé par RedisCache.shutdown().
    }

    /**
     * Appelé sur le thread Pub/Sub. {@link #dispatch} revient sur le thread principal.
     */
    public void onRedisMessage(String body) {
        Pending pending = fromRedis(body);
        if (pending == null) {
            plugin.getLogger().warning("[Web] Action admin Redis ignorée (JSON invalide).");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!reserve(pending)) {
                return;
            }
            try {
                Bukkit.getScheduler().runTask(plugin, () -> handleOnMain(pending));
            } catch (Exception e) {
                plugin.getLogger().warning("[Web] Action admin Redis non planifiée : " + e.getMessage());
            }
        });
    }

    private boolean reserve(Pending pending) {
        if (pending.id() <= 0) {
            if (plugin.getConfigManager().isVelocityEnabled()) {
                plugin.getLogger().warning("[Web] Action admin sans id ignorée : elle ne peut pas être dédoublonnée.");
                return false;
            }
            return true;
        }
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                return false;
            }
            return AdminActionClaim.claim(conn, pending.id());
        } catch (SQLException e) {
            plugin.getLogger().warning("[Web] Réservation admin #" + pending.id() + " impossible : " + e.getMessage());
            return false;
        }
    }

    private static Pending fromRedis(String body) {
        RedisBridgeMessages.Admin admin = RedisBridgeMessages.parseAdmin(body);
        if (admin == null) {
            return null;
        }
        return new Pending(admin.id(), admin.type(), admin.targetUuid(), admin.targetName(),
                admin.payload(), admin.actorDiscordId());
    }

    private void handleOnMain(Pending pending) {
        if ("set_pasdic".equalsIgnoreCase(pending.type())) {
            processSetPasdic(pending);
            return;
        }
        String err;
        try {
            err = dispatch(pending);
        } catch (Exception e) {
            err = e.getMessage();
        }
        String resultError = err;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> applyMark(pending.id(), resultError));
    }

    private void applyMark(long id, String err) {
        if (id <= 0) {
            return;
        }
        if (err == null) {
            mark(id, "done", null);
        } else if (err.startsWith("OK:")) {
            mark(id, "done", truncate(err.substring(3)));
        } else {
            mark(id, "failed", err);
        }
    }

    private record Pending(
            long id, String type, String targetUuid, String targetName, String payload, String actorDiscordId) {
    }

    /**
     * PASDIC carte site : charge chaque chunk en async, scanne spawners/stockage,
     * applique uniquement les claims propres. Hors timeout 12 s du pont.
     */
    private void processSetPasdic(Pending pending) {
        mark(pending.id(), "processing", null);
        var service = plugin.getPasdicService();
        if (service == null) {
            mark(pending.id(), "failed", "service PASDIC indisponible");
            return;
        }
        try {
            service.applyBatchFromWeb(pending.payload()).whenComplete((json, error) -> {
                if (error != null) {
                    mark(pending.id(), "failed", truncate(error.getMessage()));
                    return;
                }
                mark(pending.id(), "done", json, PASDIC_RESULT_MAX);
                plugin.getLogger().info("[Web] set_pasdic #" + pending.id() + " terminé.");
            });
        } catch (Exception e) {
            mark(pending.id(), "failed", truncate(e.getMessage()));
        }
    }

    private String dispatch(Pending p) {
        String type = p.type() == null ? "" : p.type().toLowerCase(Locale.ROOT);
        UUID uuid = parseUuid(p.targetUuid());
        String name = p.targetName();
        String payload = p.payload() == null ? "" : p.payload().trim();

        return switch (type) {
            case "set_money" -> setMoney(uuid, name, payload, false);
            case "add_money" -> setMoney(uuid, name, payload, true);
            case "set_lives" -> setLives(uuid, name, payload, false);
            case "add_lives" -> setLives(uuid, name, payload, true);
            case "give_item" -> giveItem(uuid, name, payload);
            case "ban" -> ban(uuid, name, payload);
            case "unban" -> unban(uuid, name);
            case "msg" -> msg(uuid, name, payload);
            case "discord_dm" -> discordDm(uuid, name, payload);
            case "inspect_player" -> inspectPlayer(p.id(), uuid, name);
            case "clear_container" -> inventoryMutator.clearContainer(uuid, name, payload);
            case "remove_slot" -> inventoryMutator.removeSlot(uuid, name, payload);
            case "clear_shulker" -> inventoryMutator.clearShulker(uuid, name, payload);
            case "give_effect" -> inventoryMutator.giveEffect(uuid, name, payload);
            case "clear_effect" -> inventoryMutator.clearEffect(uuid, name, payload);
            case "set_pasdic" -> "utiliser le traitement async";
            default -> "type inconnu: " + type;
        };
    }

    private String setMoney(UUID uuid, String name, String payload, boolean add) {
        UUID id = resolveUuid(uuid, name);
        if (id == null)
            return "joueur introuvable";
        double amount;
        try {
            amount = Double.parseDouble(payload);
        } catch (NumberFormatException e) {
            return "montant invalide";
        }
        if (add) {
            plugin.getEconomyManager().addBalance(id, amount);
        } else {
            plugin.getEconomyManager().removeBalance(id, plugin.getEconomyManager().getBalance(id));
            if (amount > 0)
                plugin.getEconomyManager().addBalance(id, amount);
        }
        return null;
    }

    private String setLives(UUID uuid, String name, String payload, boolean add) {
        UUID id = resolveUuid(uuid, name);
        if (id == null)
            return "joueur introuvable";
        int amount;
        try {
            amount = Integer.parseInt(payload);
        } catch (NumberFormatException e) {
            return "vies invalides";
        }
        if (add)
            plugin.getLifeManager().addLife(id, amount);
        else
            plugin.getLifeManager().setLives(id, Math.max(0, amount));
        return null;
    }

    private String giveItem(UUID uuid, String name, String payload) {
        Player target = resolveOnline(uuid, name);
        if (target == null)
            return "joueur hors-ligne";
        String typeArg = payload.toUpperCase(Locale.ROOT);
        try {
            CustomItemType type = CustomItemType.valueOf(typeArg);
            ItemStack item = type.isCantalame()
                    ? new fr.cantale.plugin.custom.CantalameManager(plugin).createCantalame()
                    : plugin.getCustomItemManager().createCustomItem(type);
            var leftover = target.getInventory().addItem(item);
            leftover.values().forEach(i -> target.getWorld().dropItemNaturally(target.getLocation(), i));
            target.sendMessage("§8[§6Cantale§8] §aTu as reçu (admin web) : §e" + type.getDisplayName());
            return null;
        } catch (IllegalArgumentException e) {
            Material mat = Material.matchMaterial(typeArg);
            if (mat == null || !mat.isItem())
                return "item inconnu";
            target.getInventory().addItem(new ItemStack(mat, 1));
            return null;
        }
    }

    private String ban(UUID uuid, String name, String payload) {
        String reason = payload.isBlank() ? "Ban admin (site)" : payload;
        Player online = resolveOnline(uuid, name);
        if (online != null) {
            try {
                online.ban(reason, (java.time.Instant) null, "Cantale-Web", true);
                return null;
            } catch (Exception e) {
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(
                        online.getName(), reason, (java.util.Date) null, "Cantale-Web");
                online.kickPlayer(ChatColor.RED + reason);
                return null;
            }
        }
        OfflinePlayer offline = resolveOffline(uuid, name);
        if (offline == null || offline.getName() == null)
            return "joueur introuvable";
        Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(
                offline.getName(), reason, (java.util.Date) null, "Cantale-Web");
        try {
            Bukkit.getBanList(org.bukkit.BanList.Type.PROFILE).addBan(
                    offline.getName(), reason, (java.util.Date) null, "Cantale-Web");
        } catch (Exception ignored) {
        }
        return null;
    }

    private String unban(UUID uuid, String name) {
        OfflinePlayer offline = resolveOffline(uuid, name);
        String playerName = offline != null ? offline.getName() : name;
        if (playerName == null || playerName.isBlank())
            return "joueur introuvable";
        UUID id = resolveUuid(uuid, name);
        if (id == null && offline != null)
            id = offline.getUniqueId();
        if (id != null) {
            plugin.getLifeManager().pardonLifeBan(id, playerName);
            if (plugin.getLifeManager().getLives(id) <= 0) {
                plugin.getLifeManager().setLives(id, 1);
            }
        } else {
            try {
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(playerName);
            } catch (Exception ignored) {
            }
            try {
                Bukkit.getBanList(org.bukkit.BanList.Type.PROFILE).pardon(playerName);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String msg(UUID uuid, String name, String payload) {
        if (payload.isBlank())
            return "message vide";
        Player target = resolveOnline(uuid, name);
        if (target == null)
            return "joueur hors-ligne";
        target.sendMessage("§8[§6Admin§8] §f" + payload);
        return null;
    }

    private String discordDm(UUID uuid, String name, String payload) {
        if (payload.isBlank())
            return "message vide";
        UUID id = resolveUuid(uuid, name);
        if (id == null)
            return "joueur introuvable";
        if (plugin.getDiscordBot() == null || !plugin.getDiscordBot().isConnected()) {
            return "bot discord indisponible";
        }
        String discordId = lookupDiscordId(id);
        if (discordId == null)
            return "compte discord non lié";
        try {
            plugin.getDiscordBot().getJda().retrieveUserById(discordId)
                    .queue(user -> user.openPrivateChannel()
                            .flatMap(pc -> pc.sendMessage("**[Cantale Admin]** " + payload)).queue(
                                    null,
                                    err -> plugin.getLogger().warning("[Web] DM Discord échoué: " + err.getMessage())));
            return null;
        } catch (Exception e) {
            return truncate(e.getMessage());
        }
    }

    /**
     * Capture inventaire (online), EC, PC1–3, effets actifs →
     * {@code web_admin_snapshots}.
     * 
     * @return OK:snapshot:&lt;id&gt; ou message d'erreur
     */
    private String inspectPlayer(long actionId, UUID uuid, String name) {
        UUID id = resolveUuid(uuid, name);
        if (id == null)
            return "joueur introuvable";

        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        String username = offline.getName() != null ? offline.getName() : (name != null ? name : id.toString());
        Player online = Bukkit.getPlayer(id);

        JsonObject root = new JsonObject();
        root.addProperty("uuid", id.toString());
        root.addProperty("username", username);
        root.addProperty("online", online != null && online.isOnline());
        root.addProperty("captured_at", Instant.now().toString());

        JsonObject inventory = new JsonObject();
        if (online != null && online.isOnline()) {
            PlayerInventory inv = online.getInventory();
            inventory.add("storage", AdminInspectSerializer.items(inv.getStorageContents()));
            inventory.add("armor", AdminInspectSerializer.items(inv.getArmorContents()));
            JsonObject off = AdminInspectSerializer.item(inv.getItemInOffHand(), 0);
            inventory.add("offhand", off != null ? off : new JsonObject());
            root.add("effects", AdminInspectSerializer.effects(online.getActivePotionEffects()));

            JsonObject ec = new JsonObject();
            ec.addProperty("source", "vanilla");
            ec.add("items", AdminInspectSerializer.items(online.getEnderChest().getContents()));
            root.add("ender_chest", ec);
        } else {
            inventory.addProperty("note", "Inventaire et effets disponibles uniquement en ligne.");
            root.add("effects", new com.google.gson.JsonArray());

            // EC hors-ligne : tentative legacy DB (sinon vanilla inaccessible)
            PrivateChestService pcs = plugin.getPrivateChestService();
            ItemStack[] legacyEc = pcs != null ? pcs.peekStorage(id, ChestType.EC) : new ItemStack[0];
            boolean hasLegacy = false;
            for (ItemStack s : legacyEc) {
                if (s != null && !s.getType().isAir()) {
                    hasLegacy = true;
                    break;
                }
            }
            JsonObject ec = new JsonObject();
            if (hasLegacy) {
                ec.addProperty("source", "legacy_db");
                ec.add("items", AdminInspectSerializer.items(legacyEc));
            } else {
                ec.addProperty("source", "offline_unavailable");
                ec.addProperty("note", "Ender chest vanilla : joueur hors-ligne.");
                ec.add("items", new com.google.gson.JsonArray());
            }
            root.add("ender_chest", ec);
        }
        root.add("inventory", inventory);

        PrivateChestService pcs = plugin.getPrivateChestService();
        PlayerRank rank = plugin.getRankManager().getRank(id);
        JsonObject pcsJson = new JsonObject();
        for (ChestType type : new ChestType[] { ChestType.PC1, ChestType.PC2, ChestType.PC3 }) {
            JsonObject chest = new JsonObject();
            boolean access = pcs != null && pcs.canAccess(rank, type);
            chest.addProperty("accessible", access);
            if (pcs != null) {
                chest.addProperty("size", pcs.getPrivateChestSize(rank));
                if (access) {
                    chest.add("items", AdminInspectSerializer.items(pcs.peekStorage(id, type)));
                } else {
                    chest.add("items", new com.google.gson.JsonArray());
                }
            } else {
                chest.add("items", new com.google.gson.JsonArray());
            }
            pcsJson.add(type.getKey(), chest);
        }
        root.add("private_chests", pcsJson);

        Long snapId = insertSnapshot(actionId, id, "player_inspect", root.toString());
        if (snapId == null)
            return "echec enregistrement snapshot";
        return "OK:snapshot:" + snapId;
    }

    private Long insertSnapshot(long actionId, UUID playerUuid, String kind, String payload) {
        String sql = "INSERT INTO web_admin_snapshots (action_id, player_uuid, kind, payload) VALUES (?, ?, ?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null)
                return null;
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, actionId);
                ps.setString(2, playerUuid.toString());
                ps.setString(3, kind);
                ps.setString(4, payload);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next())
                        return keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Web] web_admin_snapshots insert : " + e.getMessage());
        }
        return null;
    }

    private String lookupDiscordId(UUID uuid) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT discord_id FROM discord_links WHERE uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("discord_id");
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    private UUID resolveUuid(UUID uuid, String name) {
        if (uuid != null)
            return uuid;
        OfflinePlayer offline = resolveOffline(null, name);
        return offline != null ? offline.getUniqueId() : null;
    }

    private Player resolveOnline(UUID uuid, String name) {
        if (uuid != null) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline())
                return p;
        }
        if (name != null && !name.isBlank()) {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null && p.isOnline())
                return p;
        }
        return null;
    }

    private OfflinePlayer resolveOffline(UUID uuid, String name) {
        if (uuid != null)
            return Bukkit.getOfflinePlayer(uuid);
        if (name != null && !name.isBlank())
            return Bukkit.getOfflinePlayer(name);
        return null;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank())
            return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void mark(long id, String status, String result) {
        mark(id, status, result, RESULT_MAX);
    }

    private void mark(long id, String status, String result, int maxLen) {
        String sql = "UPDATE web_admin_actions SET status = ?, result = ?, processed_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null)
                return;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status);
                if (result == null)
                    ps.setNull(2, java.sql.Types.VARCHAR);
                else
                    ps.setString(2, truncate(result, maxLen));
                ps.setLong(3, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Web] WebAdminBridge mark #" + id + " : " + e.getMessage());
        }
    }

    private static String truncate(String value) {
        return truncate(value, RESULT_MAX);
    }

    private static String truncate(String value, int max) {
        if (value == null)
            return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
