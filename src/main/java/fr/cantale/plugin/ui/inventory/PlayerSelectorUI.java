package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.function.Consumer;

public class PlayerSelectorUI extends BaseUI {

    private final Consumer<Player> onSelect;
    private final String returnTitle;

    public PlayerSelectorUI(CantalePlugin plugin, Player player, String title, Consumer<Player> onSelect, String returnTitle) {
        super(plugin, player, title, 5);
        this.onSelect = onSelect;
        this.returnTitle = returnTitle;
    }

    @Override
    protected void init() {
        fillEmpty();
        setItem(4, Material.COMPASS, "§6§lSélectionner un joueur", "§7Cliquez sur un joueur");

        Player[] online = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        int slot = 10;
        for (int i = 0; i < online.length && slot < 35; i++) {
            Player target = online[i];
            if (target.equals(player)) continue;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName("§e" + target.getName());
                meta.setLore(java.util.Arrays.asList(
                    "§7Clic pour sélectionner"
                ));
                head.setItemMeta(meta);
            }
            inventory.setItem(slot, head);

            slot++;
            if (slot % 9 == 8) slot += 2;
        }

        addNavigationBar(true, false);
    }

    @Override
    protected void handleClick(int slot) {
        if (slot == 45) {
            plugin.getUIManager().goBack(player);
            return;
        }
        if (slot == 49) {
            close();
            return;
        }

        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType() != Material.PLAYER_HEAD) return;

        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) item.getItemMeta();
        if (meta == null || meta.getOwningPlayer() == null) return;

        Player target = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
        if (target != null) {
            close();
            onSelect.accept(target);
        }
    }
}
