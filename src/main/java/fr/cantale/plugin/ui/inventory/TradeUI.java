package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.trade.TradeManager;
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

        setItem(11, Material.PLAYER_HEAD, "§eNouvel échange", "§7Envoyer une demande à un joueur");
        setItem(15, Material.GOLD_INGOT, "§eAjouter de l'argent",
            "§7Dans la fenêtre d'échange, cliquez sur",
            "§7le lingot d'or puis tapez le montant");

        TradeManager tm = plugin.getTradeManager();
        if (tm.hasActiveTrade(player.getUniqueId())) {
            setItem(22, Material.EMERALD, "§aReprendre l'échange", "§7Rouvrir la fenêtre d'échange");
        } else if (tm.hasPendingRequest(player.getUniqueId())) {
            setItem(22, Material.EMERALD, "§aAccepter la demande", "§7Ouvrir l'échange proposé");
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
            if (plugin.getTradeManager().hasActiveTrade(player.getUniqueId())) {
                plugin.getTradeManager().beginMoneyInput(player);
            } else {
                player.sendMessage("§8[§6Cantale§8] §cAucun échange en cours — §f/trade <joueur>");
            }
        } else if (slot == 22) {
            TradeManager tm = plugin.getTradeManager();
            if (tm.hasActiveTrade(player.getUniqueId())) {
                player.closeInventory();
                tm.openSessionGUI(player, tm.getSession(player.getUniqueId()));
            } else if (tm.hasPendingRequest(player.getUniqueId())) {
                player.closeInventory();
                tm.accept(player);
            }
        }
    }
}
