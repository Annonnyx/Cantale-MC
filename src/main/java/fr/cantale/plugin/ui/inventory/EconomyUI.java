package fr.cantale.plugin.ui.inventory;

import com.google.gson.JsonObject;
import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class EconomyUI extends BaseUI {

    private String paying = "";
    private String notice = "";

    public EconomyUI(CantalePlugin plugin, Player player) {
        this(plugin, player, "");
    }

    public EconomyUI(CantalePlugin plugin, Player player, String paying) {
        super(plugin, player, "§8[§6Cantale§8] §7Économie", 4);
        this.paying = paying == null ? "" : paying;
    }

    @Override
    protected void init() {
        fillEmpty();
        double balance = plugin.getEconomyManager().getBalance(player);

        setItem(4, Material.GOLD_BLOCK, "§6§l" + player.getName(),
            "§7Solde: §6" + (int) balance + " §7Cantox");

        setItem(11, Material.GOLD_NUGGET, "§ePayer un joueur", "§7Transférer de l'argent");
        setItem(13, Material.EMERALD, "§eAuction House", "§7Acheter / Vendre");
        if (notice.isBlank()) {
            setItem(15, Material.BOOK, "§eTransactions", "§7Historique");
        } else {
            setItem(15, Material.BOOK, "§eTransactions", "§7" + notice);
        }

        addNavigationBar(true, false);
    }

    @Override
    public void writeClientDetail(JsonObject screen) {
        JsonObject detail = new JsonObject();
        detail.addProperty("balance", (int) plugin.getEconomyManager().getBalance(player));
        detail.addProperty("pay", paying);
        detail.addProperty("notice", notice);
        screen.add("detail", detail);
    }

    @Override
    public void clientAction(String id, String text) {
        if ("cancelpay".equals(id)) {
            paying = "";
            notice = "";
            init();
            return;
        }
        if (!"pay".equals(id) || paying.isBlank()) return;
        if (!paying.matches("[A-Za-z0-9_]{1,16}")) {
            notice = "Choisis un joueur.";
            return;
        }
        String raw = text == null ? "" : text.trim().replace(',', '.');
        double amount;
        try {
            amount = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            notice = "Montant invalide.";
            return;
        }
        if (amount <= 0 || !Double.isFinite(amount)) {
            notice = "Le montant doit être positif.";
            return;
        }
        String money = amount == Math.rint(amount) ? Long.toString((long) amount) : Double.toString(amount);
        player.performCommand("pay " + paying + " " + money);
        paying = "";
        notice = "";
        init();
    }

    @Override
    protected void handleClick(int slot) {
        NavigationManager nav = plugin.getUIManager().getNavigationManager();
        switch (slot) {
            case 11 -> nav.openUI(player, new PlayerSelectorUI(plugin, player, "§7Payer un joueur", target -> {
                plugin.getUIManager().getNavigationManager().openUI(player, new EconomyUI(plugin, player, target.getName()));
            }, title));
            case 13 -> nav.openUI(player, new AuctionUI(plugin, player));
            case 15 -> {
                notice = "L'historique des transactions n'est pas encore disponible.";
                init();
            }
        }
    }
}
