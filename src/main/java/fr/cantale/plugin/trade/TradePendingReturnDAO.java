package fr.cantale.plugin.trade;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.util.ItemStackSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Accès plugin à {@link TradePendingReturnRepository}. Les items illisibles restent en base.
 */
public final class TradePendingReturnDAO {

    private final CantalePlugin plugin;

    public TradePendingReturnDAO(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public long insert(UUID playerId, ItemStack item) {
        if (playerId == null || item == null || item.getType().isAir()) {
            return -1L;
        }
        String data = ItemStackSerializer.toBase64(new ItemStack[]{item.clone()});
        if (data == null || data.isEmpty()) {
            plugin.getLogger().severe("[Trade] Sérialisation impossible : l'item en attente reste seulement en mémoire.");
            return -1L;
        }
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("[Trade] Pool SQL indisponible : item en attente non persisté.");
                return -1L;
            }
            return TradePendingReturnRepository.insert(conn, playerId, data, System.currentTimeMillis() / 1000L);
        } catch (SQLException e) {
            plugin.getLogger().severe("[Trade] Échec persistance d'un item en attente : " + e.getMessage());
            return -1L;
        }
    }

    public List<StoredItem> loadAll() {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("[Trade] Pool SQL indisponible : items en attente non rechargés.");
                return List.of();
            }
            return decode(TradePendingReturnRepository.loadAll(conn));
        } catch (SQLException e) {
            plugin.getLogger().severe("[Trade] Échec lecture des items en attente : " + e.getMessage());
            return List.of();
        }
    }

    public List<StoredItem> load(UUID playerId) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("[Trade] Pool SQL indisponible : items en attente du joueur non rechargés.");
                return List.of();
            }
            return decode(TradePendingReturnRepository.loadByPlayer(conn, playerId));
        } catch (SQLException e) {
            plugin.getLogger().severe("[Trade] Échec lecture des items en attente : " + e.getMessage());
            return List.of();
        }
    }

    public void delete(long id) {
        if (id <= 0) {
            return;
        }
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("[Trade] Pool SQL indisponible : ligne d'item en attente non supprimée (id=" + id + ").");
                return;
            }
            TradePendingReturnRepository.delete(conn, id);
        } catch (SQLException e) {
            plugin.getLogger().severe("[Trade] Échec suppression de l'item en attente " + id + " : " + e.getMessage());
        }
    }

    /** Claim exclusif. {@link TradePendingReturnRepository.Delivery#GIVE} = cet appel doit donner une seule fois. */
    public Claim claim(long id, boolean witnessed) {
        String token = UUID.randomUUID().toString();
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("[Trade] Pool SQL indisponible : item en attente non réclamé (id=" + id + ").");
                return new Claim(TradePendingReturnRepository.Delivery.SKIP, token);
            }
            TradePendingReturnRepository.Delivery delivery =
                    TradePendingReturnRepository.resolve(conn, id, token, witnessed);
            return new Claim(delivery, token);
        } catch (SQLException e) {
            plugin.getLogger().severe("[Trade] Échec du claim de l'item en attente " + id + " : " + e.getMessage());
            return new Claim(TradePendingReturnRepository.Delivery.SKIP, token);
        }
    }

    public boolean acknowledge(long id) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("[Trade] Pool SQL indisponible : accusé de l'item " + id + " non écrit.");
                return false;
            }
            return TradePendingReturnRepository.acknowledge(conn, id);
        } catch (SQLException e) {
            plugin.getLogger().severe("[Trade] Échec de l'accusé de l'item " + id + " : " + e.getMessage());
            return false;
        }
    }

    public void release(long id, String token) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("[Trade] Pool SQL indisponible : claim " + id + " non relâché.");
                return;
            }
            TradePendingReturnRepository.release(conn, id, token);
        } catch (SQLException e) {
            plugin.getLogger().severe("[Trade] Échec du relâchement du claim " + id + " : " + e.getMessage());
        }
    }

    public void finishAcked(long id) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("[Trade] Pool SQL indisponible : ligne " + id + " non supprimée après don.");
                return;
            }
            TradePendingReturnRepository.deleteIfHandedOff(conn, id);
        } catch (SQLException e) {
            plugin.getLogger().severe("[Trade] Échec suppression après don de l'item " + id + " : " + e.getMessage());
        }
    }

    /** Claims laissés ouverts par un arrêt entre le claim et l'accusé. */
    public void abandonUnackedClaims() {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("[Trade] Pool SQL indisponible : claims ouverts non abandonnés.");
                return;
            }
            TradePendingReturnRepository.abandonOpenClaims(conn);
        } catch (SQLException e) {
            plugin.getLogger().severe("[Trade] Échec abandon des claims ouverts : " + e.getMessage());
        }
    }

    public record Claim(TradePendingReturnRepository.Delivery delivery, String token) {
    }

    private List<StoredItem> decode(List<TradePendingReturnRepository.Row> rows) {
        List<StoredItem> items = new ArrayList<>();
        for (TradePendingReturnRepository.Row row : rows) {
            ItemStack[] decoded = ItemStackSerializer.fromBase64(row.itemData());
            if (decoded == null || decoded.length == 0 || decoded[0] == null || decoded[0].getType().isAir()) {
                plugin.getLogger().severe("[Trade] Item en attente illisible, conservé en base (id=" + row.id() + ").");
                continue;
            }
            items.add(new StoredItem(row.id(), row.playerId(), decoded[0]));
        }
        return items;
    }

    public record StoredItem(long id, UUID playerId, ItemStack item) {
    }
}
