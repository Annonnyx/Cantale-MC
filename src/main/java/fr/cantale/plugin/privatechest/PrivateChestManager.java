package fr.cantale.plugin.privatechest;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @deprecated Utiliser PrivateChestService à la place.
 */
@Deprecated
public class PrivateChestManager {

    private final CantalePlugin plugin;
    private final PrivateChestDAO privateChestDAO;
    private final Map<UUID, PrivateChest> openChests;

    public PrivateChestManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.privateChestDAO = new PrivateChestDAO(plugin);
        this.openChests = new HashMap<>();
    }

    public void openPrivateChest(Player player) {
        UUID uuid = player.getUniqueId();
        PrivateChest chest = openChests.get(uuid);

        if (chest == null) {
            chest = new PrivateChest(uuid, 27);
            openChests.put(uuid, chest);
        }

        player.openInventory(chest.getInventory());
    }

    public void saveAndClose(Player player) {
        UUID uuid = player.getUniqueId();
        PrivateChest chest = openChests.get(uuid);

        if (chest != null) {
            privateChestDAO.saveChest(uuid, ChestType.PC1, chest.getContents());
            openChests.remove(uuid);
        }
    }

    public PrivateChest getPrivateChest(UUID uuid) {
        return openChests.get(uuid);
    }
}
