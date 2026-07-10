package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

public class ConfirmDialogUI extends BaseUI {

    private final Consumer<Boolean> onConfirm;
    private final String message;

    public ConfirmDialogUI(CantalePlugin plugin, Player player, String title, String message, Consumer<Boolean> onConfirm) {
        super(plugin, player, title, 3);
        this.onConfirm = onConfirm;
        this.message = message;
    }

    @Override
    protected void init() {
        fillEmpty();
        setItem(4, Material.PAPER, "§6§lConfirmation", "§7" + message);
        setItem(11, Material.LIME_WOOL, "§a§lConfirmer", "§7Cliquez pour confirmer");
        setItem(15, Material.RED_WOOL, "§c§lAnnuler", "§7Cliquez pour annuler");
    }

    @Override
    protected void handleClick(int slot) {
        close();
        if (slot == 11) onConfirm.accept(true);
        else if (slot == 15) onConfirm.accept(false);
    }
}
