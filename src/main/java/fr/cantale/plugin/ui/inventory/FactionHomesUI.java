package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Ancien GUI des homes de faction — retiré du gameplay.
 * Les homes perso restent {@code /home} ; mobilité de fac : {@code /f spawn} et {@code /f warp}.
 * Fichier conservé (interdiction de supprimer des fichiers du dépôt).
 */
public class FactionHomesUI extends BaseUI {

    public FactionHomesUI(CantalePlugin plugin, Player player, Faction faction) {
        super(plugin, player, "§8[§6Cantale§8] §7Homes — " + faction.getName(), 3);
    }

    @Override
    protected void init() {
        fillEmpty();
        setItem(13, Material.BARRIER, "§cHomes de faction retirés",
                "§7Utilise §e/home §7(perso)",
                "§7ou §e/f spawn §7/ §e/f warp");
        addNavigationBar(true, true);
    }

    @Override
    protected void handleClick(int slot) {
        if (slot == size - 9) {
            plugin.getUIManager().goBack(player);
            return;
        }
        if (slot == size - 5) {
            close();
        }
    }
}
