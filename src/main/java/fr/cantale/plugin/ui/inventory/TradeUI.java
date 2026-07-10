package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class TradeUI extends BaseUI {

    private final CantalePlugin plugin;

    public TradeUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Trade", 3);
        this.plugin = plugin;
    }

    @Override
    protected void init() {
        fillEmpty();
        
        setItem(13, Material.DIAMOND, "§6§lSystème d'Échange",
            "§7Échangez des items et de l'argent",
            "§7avec d'autres joueurs");

        setItem(11, Material.PLAYER_HEAD, "§eNouvel échange", "§7Sélectionner un joueur");
        setItem(15, Material.GOLD_INGOT, "§eAjouter de l'argent", "§7Définir le montant à échanger");

        boolean hasActiveTrade = plugin.getTradeManager().hasActiveTrade(player.getUniqueId());
        if (hasActiveTrade) {
            setItem(22, Material.EMERALD, "§aAccepter l'échange", "§7Confirmer l'échange en cours");
        } else {
            setItem(22, Material.REDSTONE, "§cAucun échange en cours", "§7Utilisez /trade <joueur>");
        }

        addNavigationBar(true, false);
    }

    @Override
    protected void handleClick(int slot) {
        NavigationManager nav = plugin.getUIManager().getNavigationManager();
        
        if (slot == 11) {
            nav.openUI(player, new PlayerSelectorUI(plugin, player, "§7Sélectionner un joueur", target -> {
                player.closeInventory();
                player.performCommand("trade " + target.getName());
            }, "Échanger avec"));
        } else if (slot == 15) {
            player.closeInventory();
            player.sendMessage("§8[§6Cantale§8] §7Tapez: §e/trade money <montant>");
        } else if (slot == 22) {
            if (plugin.getTradeManager().hasActiveTrade(player.getUniqueId())) {
                plugin.getTradeManager().acceptTrade(player);
                player.closeInventory();
            }
        }
    }
}
