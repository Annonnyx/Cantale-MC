package fr.cantale.plugin.ui.inventory;

import com.google.gson.JsonObject;
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

        setItem(11, Material.RED_WOOL, "§cSacrifier une vie", "§7Transforme 1 vie en item", "§7Il faut en garder au moins une");
        setItem(13, Material.GOLDEN_APPLE, "§eDonner une vie", "§7Choisir un joueur", "§7Il faut en garder au moins une");
        setItem(15, Material.TOTEM_OF_UNDYING, "§eItem Vie", "§7Prends-le en main", "§7Clic droit pour gagner 1 vie");

        addNavigationBar(true, false);
    }

    @Override
    public void writeClientDetail(JsonObject screen) {
        JsonObject detail = new JsonObject();
        detail.addProperty("lives", plugin.getLifeManager().getLives(player.getUniqueId()));
        screen.add("detail", detail);
    }

    @Override
    protected void handleClick(int slot) {
        NavigationManager nav = plugin.getUIManager().getNavigationManager();
        switch (slot) {
            case 11 -> {
                player.performCommand("dropvie");
                init();
            }
            case 13 -> nav.openUI(player, new PlayerSelectorUI(plugin, player, "§7Donner une vie à", target -> {
                player.performCommand("givevie " + target.getName());
            }, title));
        }
    }
}
