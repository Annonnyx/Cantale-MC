package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.trade.TradeSession;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * GUI d'un échange en cours (6 lignes) :
 *  - colonnes 0-3 : offre du joueur local (dépôt par clic avec item en main,
 *    shift-clic depuis l'inventaire, ou reprise par clic sur l'item offert) ;
 *  - colonne 4 : boutons (argent local/distant, statut, confirmer, annuler) ;
 *  - colonnes 5-8 : offre de l'autre joueur (miroir, lecture seule).
 *
 * Les offres vivent dans la {@link TradeSession}, jamais dans l'inventaire :
 * tout est re-rendu à chaque changement, donc aucune duplication possible.
 */
public class TradeSessionUI extends BaseUI {

    private static final int[] LOCAL_SLOTS = new int[TradeSession.OFFER_SLOTS];
    private static final int[] REMOTE_SLOTS = new int[TradeSession.OFFER_SLOTS];

    private static final int SLOT_MONEY_LOCAL = 4;
    private static final int SLOT_MONEY_REMOTE = 13;
    private static final int SLOT_STATUS = 22;
    private static final int SLOT_REMOTE_STATUS = 31;
    private static final int SLOT_CONFIRM = 40;
    private static final int SLOT_CANCEL = 49;

    static {
        int i = 0;
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 4; col++) {
                LOCAL_SLOTS[i] = row * 9 + col;
                REMOTE_SLOTS[i] = row * 9 + (8 - col); // miroir horizontal
                i++;
            }
        }
    }

    private final TradeSession session;

    public TradeSessionUI(CantalePlugin plugin, Player player, TradeSession session) {
        super(plugin, player, "§8Échange : §e" + otherName(player, session), 6);
        this.session = session;
    }

    private static String otherName(Player viewer, TradeSession session) {
        UUID otherUuid = session.getOtherPlayer(viewer.getUniqueId());
        Player other = otherUuid != null ? Bukkit.getPlayer(otherUuid) : null;
        return other != null ? other.getName() : "Hors ligne";
    }

    public Inventory getInventory() {
        return inventory;
    }

    /** Re-rend le contenu (les deux zones + boutons) sans réouvrir l'inventaire. */
    public void refresh() {
        init();
    }

    @Override
    protected void init() {
        inventory.clear();
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);

        UUID me = player.getUniqueId();
        UUID other = session.getOtherPlayer(me);

        // Zones d'offre : vidées puis remplies depuis la session.
        for (int slot : LOCAL_SLOTS) inventory.setItem(slot, null);
        for (int slot : REMOTE_SLOTS) inventory.setItem(slot, null);

        Map<Integer, ItemStack> myItems = session.getItems(me);
        for (Map.Entry<Integer, ItemStack> entry : myItems.entrySet()) {
            inventory.setItem(LOCAL_SLOTS[entry.getKey()], entry.getValue());
        }
        if (other != null) {
            Map<Integer, ItemStack> theirItems = session.getItems(other);
            for (Map.Entry<Integer, ItemStack> entry : theirItems.entrySet()) {
                inventory.setItem(REMOTE_SLOTS[entry.getKey()], entry.getValue());
            }
        }

        boolean iConfirmed = session.isConfirmed(me);
        boolean otherConfirmed = other != null && session.isConfirmed(other);

        setItem(SLOT_MONEY_LOCAL, Material.GOLD_INGOT,
            "§eVotre argent : §6" + formatMoney(session.getMoney(me)) + " Cantox",
            "§7Clic pour saisir un montant",
            "§7(tapez §f0 §7ou §cannuler §7dans le chat)");
        setItem(SLOT_MONEY_REMOTE, Material.GOLD_NUGGET,
            "§eSon argent : §6" + (other != null ? formatMoney(session.getMoney(other)) : "0") + " Cantox");

        if (session.isCountingDown()) {
            long remainingMs = session.getCountdownEndsAt() - System.currentTimeMillis();
            long seconds = Math.max(1, (long) Math.ceil(remainingMs / 1000.0));
            setItem(SLOT_STATUS, Material.CLOCK, "§6§lÉchange imminent !",
                "§7Finalisation dans §e" + seconds + " s",
                "§7(cliquez §cAnnuler§7 pour interrompre)");
        } else {
            setItem(SLOT_STATUS, Material.CLOCK, "§eStatut de l'échange",
                "§7En attente des confirmations",
                "§7Toute modification réinitialise les confirmations");
        }

        setItem(SLOT_REMOTE_STATUS, otherConfirmed ? Material.LIME_DYE : Material.RED_DYE,
            otherConfirmed ? "§a" + otherName(player, session) + " a confirmé"
                           : "§c" + otherName(player, session) + " n'a pas encore confirmé");

        setItem(SLOT_CONFIRM, iConfirmed ? Material.LIME_DYE : Material.RED_DYE,
            iConfirmed ? "§a§lConfirmé !" : "§c§lConfirmer l'échange",
            iConfirmed ? "§7Clic pour retirer votre confirmation"
                       : "§7Vérifiez l'offre adverse avant de confirmer");

        setItem(SLOT_CANCEL, Material.BARRIER, "§c§lAnnuler l'échange",
            "§7Les items posés sont rendus à chacun");
    }

    @Override
    protected void handleClick(int slot) {
        if (slot == SLOT_CONFIRM) {
            plugin.getTradeManager().toggleConfirm(player);
            return;
        }
        if (slot == SLOT_CANCEL) {
            plugin.getTradeManager().cancelTradeOf(player, "trade.cancelled-by", player.getName());
            return;
        }
        if (slot == SLOT_MONEY_LOCAL) {
            plugin.getTradeManager().beginMoneyInput(player);
            return;
        }

        int offerIndex = localOfferIndex(slot);
        if (offerIndex == -1) return; // zone distante / boutons : lecture seule

        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            // Dépôt du contenu du curseur : slot visé si libre, sinon premier libre.
            Map<Integer, ItemStack> myItems = session.getItems(player.getUniqueId());
            int target = myItems.containsKey(offerIndex) ? -1 : offerIndex;
            if (plugin.getTradeManager().depositItem(player, target, cursor)) {
                player.setItemOnCursor(null);
            }
        } else if (session.getItems(player.getUniqueId()).containsKey(offerIndex)) {
            plugin.getTradeManager().withdrawItem(player, offerIndex);
        }
    }

    /**
     * Garde-fous en plus de BaseUI :
     *  - shift-clic depuis l'inventaire du joueur → dépôt dans l'offre ;
     *  - double-clic (collect to cursor) annulé : il aspirerait les items affichés.
     */
    @EventHandler
    public void onClickExtra(InventoryClickEvent event) {
        if (event.getView().getTopInventory() != inventory) return;
        if (!event.getWhoClicked().equals(player)) return;

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        boolean clickedBottom = event.getClickedInventory() != null
            && event.getClickedInventory() != inventory;
        if (!clickedBottom || !event.isShiftClick()) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        if (plugin.getTradeManager().depositItem(player, -1, clicked)) {
            event.setCurrentItem(null);
        }
    }

    private int localOfferIndex(int slot) {
        for (int i = 0; i < LOCAL_SLOTS.length; i++) {
            if (LOCAL_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    private String formatMoney(double amount) {
        return amount == Math.floor(amount) ? String.valueOf((long) amount) : String.format("%.2f", amount);
    }
}
