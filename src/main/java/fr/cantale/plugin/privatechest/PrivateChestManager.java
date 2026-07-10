package fr.cantale.plugin.privatechest;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
            chest = privateChestDAO.loadPrivateChest(uuid);
            openChests.put(uuid, chest);
        }
        
        player.openInventory(chest.getInventory());
    }

    public void saveAndClose(Player player) {
        UUID uuid = player.getUniqueId();
        PrivateChest chest = openChests.get(uuid);
        
        if (chest != null) {
            privateChestDAO.savePrivateChest(uuid, chest.getContents());
            openChests.remove(uuid);
        }
    }

    public PrivateChest getPrivateChest(UUID uuid) {
        return openChests.get(uuid);
    }
}
