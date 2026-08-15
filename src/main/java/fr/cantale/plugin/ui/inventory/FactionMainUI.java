package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class FactionMainUI extends BaseUI {

    public FactionMainUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Faction", 6);
    }

    @Override
    protected void init() {
        fillEmpty();
        FactionManager fm = plugin.getFactionManager();
        Faction faction = fm.getPlayerFaction(player.getUniqueId());

        if (faction == null) {
            setItem(4, Material.SHIELD, "§6§lPas de Faction", "§7Vous n'êtes dans aucune faction.");
            setItem(20, Material.LIME_WOOL, "§aCréer une Faction", "§7Coût: §61000 §7Cantox", "§7Cliquez pour créer");
            setItem(24, Material.BOOK, "§eRejoindre une Faction", "§7Voir les invitations");
        } else {
            setItem(4, Material.SHIELD, "§6§l" + faction.getName(),
                "§7Membres: §e" + faction.getMemberCount(),
                "§7Claims: §e" + faction.getClaimCount(),
                "§7Banque: §6" + (int) faction.getBalance() + " §7Cantox",
                "§7Power: §e" + faction.getPower());

            setItem(11, Material.PLAYER_HEAD, "§eMembres", "§7Voir / gérer les membres", "§7Promote, demote, kick");
            setItem(12, Material.GRASS_BLOCK, "§eClaims", "§7Liste des chunks claimés", "§7TP et unclaim");
            setItem(13, Material.GOLD_INGOT, "§eBanque", "§7Déposer / Retirer", "§7Solde : §6" + (int) faction.getBalance() + " §7Cantox");
            setItem(14, Material.NETHER_STAR, "§bPouvoir",
                    "§7Pouvoir : §b" + faction.getPower(),
                    "§7Claims / warps / hoppers",
                    "§7Paliers + achat GUI",
                    "§8Aussi : §7/f power");
            setItem(15, Material.BOOK, "§ePermissions", "§7Voir les permissions par grade");
            setItem(16, Material.ENDER_PEARL, "§eSpawn", "§7TP au spawn faction");
            setItem(17, Material.MAP, "§eCarte", "§7Carte des claims");
            setItem(18, Material.COMPASS, "§eWarps", "§7Téléportations publiques", "§7de la faction");

            setItem(20, Material.LIME_WOOL, "§aInviter", "§7Envoyer une invitation");
            setItem(21, Material.ANVIL, "§ePromouvoir / Rétrograder", "§7Gérer les grades");
            setItem(22, Material.RED_WOOL, "§cQuitter", "§7Attention ! Action définitive");
            setItem(23, Material.TNT, "§cSupprimer", "§7Leader uniquement");
            setItem(24, Material.WRITABLE_BOOK, "§bQuêtes de faction",
                    "§7Courses partagées", "§7/quetes fac");
        }

        addNavigationBar(true, false);
    }

    @Override
    protected void handleClick(int slot) {
        if (slot == 45) {
            plugin.getUIManager().goBack(player);
            return;
        }
        if (slot == 49) {
            close();
            return;
        }

        FactionManager fm = plugin.getFactionManager();
        Faction faction = fm.getPlayerFaction(player.getUniqueId());

        NavigationManager nav = plugin.getUIManager().getNavigationManager();

        if (faction == null) {
            switch (slot) {
                case 20 -> { close(); player.sendMessage("§8[§6Cantale§8] §7Utilisez §e/f create <nom>§7 pour créer votre faction."); }
                case 24 -> { close(); player.sendMessage("§eVérifiez vos invitations avec §e/f accept§7."); }
            }
        } else {
            switch (slot) {
                case 11 -> nav.openUI(player, new FactionMembersUI(plugin, player, faction));
                case 12 -> nav.openUI(player, new FactionClaimsUI(plugin, player, faction));
                case 13 -> nav.openUI(player, new FactionBankUI(plugin, player, faction));
                case 14 -> nav.openUI(player, new FactionPowerShopUI(plugin, player, faction));
                case 15 -> { close(); player.performCommand("f perms"); }
                case 16 -> { close(); player.performCommand("f spawn"); }
                case 17 -> nav.openUI(player, new FactionMapUI(plugin, player));
                case 18 -> nav.openUI(player, new FactionWarpsUI(plugin, player, faction));
                case 20 -> nav.openUI(player, new PlayerSelectorUI(plugin, player, "§7Inviter un joueur", target -> {
                    player.performCommand("f invite " + target.getName());
                }, title));
                case 21 -> nav.openUI(player, new FactionMembersUI(plugin, player, faction));
                case 22 -> nav.openUI(player, new ConfirmDialogUI(plugin, player, "§7Quitter la faction", "Voulez-vous vraiment quitter " + faction.getName() + " ?", confirmed -> {
                    if (confirmed) player.performCommand("f leave");
                }));
                case 23 -> nav.openUI(player, new ConfirmDialogUI(plugin, player, "§7Supprimer la faction", "Voulez-vous vraiment SUPPRIMER " + faction.getName() + " ? Cette action est IRRÉVERSIBLE.", confirmed -> {
                    if (confirmed) player.performCommand("f disband");
                }));
                case 24 -> nav.openUI(player, new QuestFactionUI(plugin, player));
            }
        }
    }
}
