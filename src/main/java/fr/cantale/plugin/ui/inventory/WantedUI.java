package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.wanted.Wanted;
import fr.cantale.plugin.wanted.WantedManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public class WantedUI extends BaseUI {

    private final WantedManager wantedManager;

    public WantedUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §cPrimes Actives (Wanted)", 5);
        // We retrieve the wanted manager from plugin.getWantedManager()
        this.wantedManager = plugin.getWantedManager();
    }

    @Override
    protected void init() {
        fillEmpty();

        setItem(4, Material.SKELETON_SKULL, "§c§lWanted — Avis de Recherche",
                "§7Visualisez les primes actives sur les joueurs.",
                "§7Éliminez ces cibles pour récupérer les Cantox !");

        if (wantedManager == null) {
            setItem(22, Material.BARRIER, "§cErreur", "§7Le gestionnaire de primes n'est pas disponible.");
            addNavigationBar(true, false);
            return;
        }

        List<Wanted> activeWanted = wantedManager.getActiveWantedList();
        if (activeWanted.isEmpty()) {
            setItem(22, Material.PAPER, "§cAucune prime active", "§7Tous les criminels ont été capturés !");
        } else {
            int slot = 10;
            int maxSlots = 35;
            for (Wanted wanted : activeWanted) {
                if (slot >= maxSlots) break;
                if (slot % 9 == 8) slot += 2; // skip borders

                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(wanted.getTargetName()));
                    meta.setDisplayName("§c§l" + wanted.getTargetName());
                    meta.setLore(java.util.Arrays.asList(
                            "§6Prime : §e" + String.format("%.0f", wanted.getReward()) + " Cantox",
                            "§7Raison : §f" + wanted.getReason()
                    ));
                    head.setItemMeta(meta);
                }
                setItem(slot, head);
                slot++;
            }
        }

        addNavigationBar(true, false);
    }

    @Override
    protected void handleClick(int slot) {
        // Clics passifs sur les têtes : rien à faire
    }
}
