package fr.cantale.plugin.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pass de saison visuel. {@link SeasonPassRules#balanceReward()} et {@link SeasonPassRules#livesReward()}
 * restent à zéro : cette classe n'appelle ni l'économie ni les vies.
 */
public final class SeasonPassService implements Listener {

    private final CantalePlugin plugin;
    private final ConcurrentHashMap<UUID, Look> looks = new ConcurrentHashMap<>();

    public SeasonPassService(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().warning("[Pass] Saison non préparée : pool SQL indisponible.");
                return;
            }
            SeasonPassRepository.ensureSeason(conn, System.currentTimeMillis() / 1000L);
            plugin.getLogger().info("[Pass] Saison cosmétique " + SeasonPassRules.SEASON_ID
                    + " prête (aucun Cantox, aucune vie).");
        } catch (SQLException e) {
            plugin.getLogger().severe("[Pass] Préparation impossible : " + e.getMessage());
        }
    }

    public int grantPlayXp(UUID uuid, int amount) {
        if (uuid == null || amount <= 0) {
            return 0;
        }
        if (SeasonPassRules.balanceReward() != 0 || SeasonPassRules.livesReward() != 0) {
            plugin.getLogger().severe("[Pass] Progression refusée : une récompense de gameplay est définie.");
            return 0;
        }
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                return 0;
            }
            SeasonPassRepository.ensureSeason(conn, System.currentTimeMillis() / 1000L);
            int xp = SeasonPassRepository.addXp(conn, plugin.getDatabaseManager().isSQLite(), uuid, amount);
            SeasonPassRepository.unlockThrough(conn, uuid, SeasonPassRules.tierReached(xp),
                    System.currentTimeMillis() / 1000L);
            return xp;
        } catch (SQLException e) {
            plugin.getLogger().warning("[Pass] XP non écrit pour " + uuid + " : " + e.getMessage());
            return 0;
        }
    }

    public int xpOf(UUID uuid) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null || uuid == null) {
                return 0;
            }
            return SeasonPassRepository.readXp(conn, uuid);
        } catch (SQLException e) {
            return 0;
        }
    }

    public SeasonPassRules.Equip equip(Player player, String cosmeticId) {
        CosmeticDefinition definition = CosmeticDefinition.byId(cosmeticId);
        boolean unlocked = false;
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                return SeasonPassRules.Equip.UNKNOWN;
            }
            unlocked = definition != null && SeasonPassRepository.owns(conn, player.getUniqueId(), definition.id());
            SeasonPassRules.Equip decision = SeasonPassRules.equip(definition != null, unlocked);
            if (decision != SeasonPassRules.Equip.OK) {
                return decision;
            }
            if (!SeasonPassRepository.equip(conn, player.getUniqueId(), definition)) {
                return SeasonPassRules.Equip.LOCKED;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Pass] Équipement refusé : " + e.getMessage());
            return SeasonPassRules.Equip.UNKNOWN;
        }
        refresh(player.getUniqueId());
        publish();
        return SeasonPassRules.Equip.OK;
    }

    public boolean unequip(Player player, CosmeticKind kind) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                return false;
            }
            boolean changed = SeasonPassRepository.unequip(conn, player.getUniqueId(), kind);
            refresh(player.getUniqueId());
            publish();
            return changed;
        } catch (SQLException e) {
            plugin.getLogger().warning("[Pass] Retrait impossible : " + e.getMessage());
            return false;
        }
    }

    public Look look(UUID uuid) {
        return looks.getOrDefault(uuid, Look.NONE);
    }

    public JsonArray snapshot(Player viewer) {
        JsonArray array = new JsonArray();
        for (Player online : Bukkit.getOnlinePlayers()) {
            Look look = looks.getOrDefault(online.getUniqueId(), Look.NONE);
            JsonObject row = new JsonObject();
            row.addProperty("id", online.getUniqueId().toString());
            row.addProperty("name", online.getName());
            row.addProperty("titleId", look.titleId());
            row.addProperty("title", titleLabel(viewer, look.titleId()));
            row.addProperty("aura", look.aura());
            row.addProperty("cape", look.cape());
            array.add(row);
            if (array.size() >= 100) {
                break;
            }
        }
        return array;
    }

    private String titleLabel(Player viewer, String titleId) {
        if (titleId == null || titleId.isBlank()) {
            return "";
        }
        return plugin.messages().raw(viewer, "cosmetic." + titleId);
    }

    private void publish() {
        if (plugin.getClientMapBridge() != null) {
            plugin.getClientMapBridge().broadcastCosmetics();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer().getUniqueId());
        publish();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        looks.remove(event.getPlayer().getUniqueId());
        publish();
    }

    private void refresh(UUID uuid) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                return;
            }
            String title = "";
            String aura = "";
            String cape = "";
            for (SeasonPassRepository.Equipped equipped : SeasonPassRepository.equipped(conn, uuid)) {
                switch (equipped.kind()) {
                    case TITLE -> title = equipped.id();
                    case AURA -> aura = equipped.id();
                    case CAPE -> cape = equipped.id();
                }
            }
            looks.put(uuid, new Look(title, aura, cape));
        } catch (SQLException e) {
            plugin.getLogger().warning("[Pass] Apparence illisible : " + e.getMessage());
        }
    }

    public record Look(String titleId, String aura, String cape) {
        public static final Look NONE = new Look("", "", "");
    }
}
