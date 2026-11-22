package fr.cantale.plugin.ui.inventory;

import com.google.gson.JsonObject;
import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionCreateRules;
import fr.cantale.plugin.factions.FactionManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class FactionMainUI extends BaseUI {

    private String notice = "";

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
            setItem(20, Material.LIME_WOOL, "§aCréer une Faction",
                    "§7Le nom se choisit dans le menu",
                    "§7Fondateurs sans faction autour de toi",
                    "§7Ce chunk devient le premier claim");
            if (notice.isBlank()) {
                setItem(24, Material.BOOK, "§eRejoindre une Faction", "§7Les invitations reçues");
            } else {
                setItem(24, Material.BOOK, "§eRejoindre une Faction", "§7" + notice);
            }
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
            setItem(18, Material.COMPASS, "§eWarps publics (FW)",
                    "§7Téléportations publiques",
                    "§7/fw create · /fw delete · /fw");

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
                case 20 -> {
                    close();
                    int need = Math.max(1, plugin.getConfigManager().getMinMembersCreate());
                    int radius = (int) FactionCreateRules.FOUNDING_RADIUS;
                    player.sendMessage("§8[§6Cantale§8] §7Utilise §e/f create <nom>§7. Il faut §e" + need
                            + "§7 joueurs sans faction dans §e" + radius
                            + "§7 blocs. Le chunk actuel devient le premier claim.");
                }
                case 24 -> {
                    notice = "Pas d'invitation dans ce menu. Quand une faction t'invite, confirme avec /f accept.";
                    init();
                }
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

    @Override
    public void writeClientDetail(JsonObject screen) {
        Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        JsonObject detail = new JsonObject();
        detail.addProperty("has", faction != null);
        detail.addProperty("notice", notice);
        detail.addProperty("founders", Math.max(1, plugin.getConfigManager().getMinMembersCreate()));
        detail.addProperty("radius", (int) FactionCreateRules.FOUNDING_RADIUS);
        if (faction != null) {
            detail.addProperty("name", faction.getName());
            detail.addProperty("members", faction.getMemberCount());
            detail.addProperty("claims", faction.getClaimCount());
            detail.addProperty("bank", (int) faction.getBalance());
            detail.addProperty("power", faction.getPower());
        }
        screen.add("detail", detail);
    }

    @Override
    public void clientAction(String id, String text) {
        if (!"create".equals(id)) return;
        FactionManager fm = plugin.getFactionManager();
        if (fm.getPlayerFaction(player.getUniqueId()) != null) return;
        String name = text == null ? "" : text.trim();
        String error = fm.validateFactionName(name);
        if (error != null) {
            notice = plain(error);
            return;
        }
        notice = "";
        player.performCommand("f create " + name);
        init();
    }

    private String plain(String encoded) {
        String[] parts = encoded.split("\u001f", -1);
        Object[] args = new Object[Math.max(0, parts.length - 1)];
        System.arraycopy(parts, 1, args, 0, args.length);
        String raw = plugin.messages().raw(player, parts[0], args);
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', raw));
    }
}
