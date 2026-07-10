package fr.cantale.plugin.ui.chat;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.factions.FactionRank;
import fr.cantale.plugin.lives.LifeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class FactionChatUI {

    public static void open(CantalePlugin plugin, Player player) {
        FactionManager factionManager = plugin.getFactionManager();
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());

        if (faction == null) {
            openNoFactionMenu(plugin, player);
            return;
        }

        openFactionMenu(plugin, player, faction);
    }

    private static void openNoFactionMenu(CantalePlugin plugin, Player player) {
        PlayerDAO playerDAO = new PlayerDAO(plugin);
        LifeManager lifeManager = plugin.getLifeManager();

        Component menu = ChatMenu.header("FACTION")
                .append(ChatMenu.line("Status", "Sans faction", NamedTextColor.RED))
                .append(Component.text("\n"))
                .append(ChatMenu.clickableLine("Créer une faction (/f create)", "/f create ", "Cliquez puis entrez le nom"))
                .append(ChatMenu.clickableLine("Rejoindre une faction (/f list)", "/f list", "Voir les factions disponibles"))
                .append(Component.text("\n"))
                .append(ChatMenu.line("Vies", String.valueOf(lifeManager.getLives(player.getUniqueId())), NamedTextColor.RED))
                .append(ChatMenu.line("Cantox", String.valueOf(playerDAO.getBalance(player.getUniqueId())), NamedTextColor.GREEN))
                .append(ChatMenu.separator());

        player.sendMessage(menu);
    }

    private static void openFactionMenu(CantalePlugin plugin, Player player, Faction faction) {
        PlayerDAO playerDAO = new PlayerDAO(plugin);
        LifeManager lifeManager = plugin.getLifeManager();
        boolean isOfficier = faction.isOfficier(player.getUniqueId());
        boolean isLeader = faction.isLeader(player.getUniqueId());

        Component menu = ChatMenu.header(faction.getName().toUpperCase())
                .append(ChatMenu.line("Tag", faction.getTag(), NamedTextColor.YELLOW))
                .append(ChatMenu.line("Type", faction.getType(), NamedTextColor.GOLD))
                .append(ChatMenu.line("Membres", faction.getMemberCount() + "", NamedTextColor.GREEN))
                .append(ChatMenu.line("Claims", faction.getClaimCount() + "", NamedTextColor.AQUA))
                .append(ChatMenu.line("Puissance", faction.getPower() + "", NamedTextColor.LIGHT_PURPLE))
                .append(ChatMenu.line("Banque", faction.getBalance() + " Cantox", NamedTextColor.GREEN))
                .append(Component.text("\n"));

        // Navigation buttons
        Component buttons = Component.text("  ")
                .append(ChatMenu.button("INFO", NamedTextColor.AQUA, "/f info", "Infos détaillées"))
                .append(ChatMenu.button("MEMBRES", NamedTextColor.GREEN, "/f list", "Liste des membres"));

        if (isOfficier) {
            buttons = buttons
                    .append(ChatMenu.button("CLAIMS", NamedTextColor.YELLOW, "/f claim", "Gérer les claims"))
                    .append(ChatMenu.button("PERMS", NamedTextColor.LIGHT_PURPLE, "/f perms", "Voir les permissions"));
        }

        menu = menu.append(buttons).append(Component.text("\n\n"));

        // Quick actions
        menu = menu.append(ChatMenu.clickableLine("Claim ce chunk", "/f claim", "Claim le chunk actuel"));
        menu = menu.append(ChatMenu.clickableLine("Unclaim ce chunk", "/f unclaim", "Unclaim le chunk actuel"));

        if (isOfficier) {
            menu = menu.append(ChatMenu.clickableLine("Inviter un joueur", "/f invite ", "Cliquez puis entrez le pseudo"));
            menu = menu.append(ChatMenu.clickableLine("Expulser un membre", "/f kick ", "Cliquez puis entrez le pseudo"));
        }

        menu = menu.append(ChatMenu.clickableLine("Quitter la faction", "/f leave", "Attention, irréversible !"));
        menu = menu.append(Component.text("\n"));

        // Player stats
        menu = menu.append(ChatMenu.line("Vos vies", String.valueOf(lifeManager.getLives(player.getUniqueId())), NamedTextColor.RED))
                .append(ChatMenu.line("Vos Cantox", String.valueOf(playerDAO.getBalance(player.getUniqueId())), NamedTextColor.GREEN))
                .append(ChatMenu.line("Votre grade", faction.getMemberRank(player.getUniqueId()).getDisplayName(), NamedTextColor.GOLD));

        menu = menu.append(ChatMenu.separator());

        player.sendMessage(menu);
    }

    public static void openMemberList(Player player, Faction faction) {
        Component menu = ChatMenu.header("MEMBRES - " + faction.getName())
                .append(Component.text("\n"));

        for (var entry : faction.getMemberRanks().entrySet()) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = "Inconnu";
            FactionRank rank = entry.getValue();
            NamedTextColor color = rank == FactionRank.LEADER ? NamedTextColor.GOLD :
                    rank == FactionRank.OFFICER ? NamedTextColor.YELLOW : NamedTextColor.GRAY;

            menu = menu.append(Component.text("  • ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(name).color(color))
                    .append(Component.text(" [" + rank.getDisplayName() + "]\n").color(NamedTextColor.DARK_GRAY));
        }

        menu = menu.append(Component.text("\n"))
                .append(ChatMenu.button("RETOUR", NamedTextColor.RED, "/f", "Retour au menu faction"))
                .append(ChatMenu.separator());

        player.sendMessage(menu);
    }
}
