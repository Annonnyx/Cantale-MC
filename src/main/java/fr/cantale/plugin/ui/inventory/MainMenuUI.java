package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class MainMenuUI extends BaseUI {

    public MainMenuUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Menu Principal", 6);
    }

    @Override
    protected void init() {
        fillEmpty();

        setItem(4, Material.NETHER_STAR, "§6§lCANTALE", "§7Version 1.0.0", "§7Paper 1.20.4");

        // Ligne 2
        setItem(10, Material.EMERALD, "§eBoutique Admin", "§7Acheter / Vendre au shop", "§7Plancher de prix garanti");
        setItem(11, Material.SHIELD, "§eFactions", "§7Gérer votre faction", "§7Claims, membres, banque");
        setItem(12, Material.GOLD_NUGGET, "§eÉconomie", "§7Balance, paiements", "§7Cantox: §6" + (int) plugin.getEconomyManager().getBalance(player));
        setItem(13, Material.EMERALD, "§eAuction House", "§7Acheter / Vendre");
        setItem(14, Material.RED_BED, "§eHomes", "§7Téléportation personnelle");
        setItem(15, Material.ENDER_PEARL, "§eWarps", "§7Téléportation publique");
        setItem(16, Material.TOTEM_OF_UNDYING, "§eVies", "§7Système Hardcore");

        // Ligne 3
        setItem(20, Material.PLAYER_HEAD, "§eProfil", "§7Votre profil complet");
        setItem(21, Material.BOOK, "§eLeaderboards", "§7Classements");
        setItem(22, Material.PAPER, "§eVote", "§7Voter pour le serveur");
        setItem(23, Material.SKELETON_SKULL, "§eWanted", "§7Système de primes");
        setItem(24, Material.BOOK, "§eTrade", "§7Échanger avec un joueur");
        setItem(25, Material.DIAMOND_PICKAXE, "§eItems Custom", "§7Pickantaxes, Cantaxes...");

        // Ligne 4
        setItem(29, Material.CHEST, "§eCoffres Privés", "§7/pc /pc2 /ec");
        setItem(30, Material.DIAMOND_SWORD, "§eModération", "§7Outils modérateur");
        setItem(31, Material.COMMAND_BLOCK, "§eAdmin", "§7Panel administrateur");
        setItem(32, Material.NETHER_STAR, "§eRanks", "§7Grades VIP / Chèvre");
        setItem(33, Material.REPEATER, "§ePermissions", "§7Gérer les permissions");
        setItem(34, Material.COMPASS, "§eWeb / Link", "§7Lier compte Discord/Site");

        // Ligne 5 — sous Profil / Vote
        setItem(38, Material.WRITABLE_BOOK, "§eQuêtes",
                "§7Journalières, hebdo, saison", "§7Courses de faction");
        setItem(40, Material.ITEM_FRAME, "§eCollections",
                "§7Paliers long terme", "§7Minage, farming, combat");

        addNavigationBar(false, false);
    }

    @Override
    protected void handleClick(int slot) {
        NavigationManager nav = plugin.getUIManager().getNavigationManager();
        switch (slot) {
            case 10 -> nav.openUI(player, new ShopMainUI(plugin, player));
            case 11 -> nav.openUI(player, new FactionMainUI(plugin, player));
            case 12 -> nav.openUI(player, new EconomyUI(plugin, player));
            case 13 -> { close(); player.performCommand("ah"); }
            case 14 -> { close(); player.performCommand("home"); }
            case 15 -> { close(); player.performCommand("warp"); }
            case 16 -> nav.openUI(player, new LifeUI(plugin, player));
            case 20 -> nav.openUI(player, new ProfileUI(plugin, player));
            case 21 -> { close(); player.performCommand("leaderboard kills"); }
            case 22 -> { close(); player.performCommand("vote"); }
            case 23 -> { close(); player.performCommand("wanted list"); }
            case 24 -> nav.openUI(player, new PlayerSelectorUI(plugin, player, "§7Échanger avec", target -> {
                player.performCommand("trade " + target.getName());
            }, title));
            case 25 -> { close(); player.performCommand("customitem"); }
            case 29 -> { close(); player.performCommand("pc"); }
            case 30 -> {
                if (player.hasPermission("cantale.admin") || player.hasPermission("cantale.moderator")) { close(); player.performCommand("moderation"); }
                else player.sendMessage("§cPermission refusée.");
            }
            case 31 -> {
                if (player.hasPermission("cantale.admin")) { close(); player.performCommand("admin"); }
                else player.sendMessage("§cPermission refusée.");
            }
            case 32 -> { close(); player.sendMessage("§8[§6Cantale§8] §7Vous êtes §e" + plugin.getRankManager().getRank(player).name() + "§7. Visitez la boutique en ligne pour upgrade !"); }
            case 33 -> { close(); player.performCommand("perm list"); }
            case 34 -> { close(); player.performCommand("link"); }
            case 38 -> nav.openUI(player, new QuestMainUI(plugin, player));
            case 40 -> plugin.getCollectionManager().syncDerived(player.getUniqueId(), () ->
                    nav.openUI(player, new CollectionMainUI(plugin, player)));
            case 49 -> close();
        }
    }
}
