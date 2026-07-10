package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.lives.LifeManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class LifeUI extends BaseUI {

    public LifeUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Système de Vies", 4);
    }

    @Override
    protected void init() {
        fillEmpty();
        LifeManager lm = plugin.getLifeManager();
        int lives = lm.getLives(player.getUniqueId());

        setItem(4, Material.TOTEM_OF_UNDYING, "§6§lVies", "§7Vies restantes: §c" + lives);

        setItem(11, Material.RED_WOOL, "§cSacrifier une vie", "§7/dropvie", "§7Transforme 1 vie en item", "§7Minimum: 2 vies requises");
        setItem(13, Material.GOLDEN_APPLE, "§eDonner une vie", "§7/givevie <joueur>", "§7Minimum: 2 vies requises");
        setItem(15, Material.TOTEM_OF_UNDYING, "§eUtiliser une Vie (item)", "§7Cliquez pour utiliser", "§7+1 vie en DB");

        addNavigationBar(true, false);
    }

    @Override
    protected void handleClick(int slot) {
        NavigationManager nav = plugin.getUIManager().getNavigationManager();
        switch (slot) {
            case 11 -> player.performCommand("dropvie");
            case 13 -> nav.openUI(player, new PlayerSelectorUI(plugin, player, "§7Donner une vie à", target -> {
                player.performCommand("givevie " + target.getName());
            }, title));
            case 15 -> player.sendMessage("§eCliquez droit avec l'item VIE dans votre main pour l'utiliser.");
        }
    }
}
