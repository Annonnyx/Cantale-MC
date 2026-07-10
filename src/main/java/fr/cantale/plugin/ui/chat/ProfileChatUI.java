package fr.cantale.plugin.ui.chat;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.lives.LifeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class ProfileChatUI {

    public static void open(CantalePlugin plugin, Player player) {
        PlayerDAO playerDAO = new PlayerDAO(plugin);
        LifeManager lifeManager = plugin.getLifeManager();
        FactionManager factionManager = plugin.getFactionManager();
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());

        long createdAt = playerDAO.getCreatedAt(player.getUniqueId());
        String playTime = formatPlayTime(createdAt);

        Component menu = ChatMenu.header("PROFIL - " + player.getName())
                .append(ChatMenu.line("Joue depuis", playTime, NamedTextColor.AQUA))
                .append(ChatMenu.line("Monnaie", playerDAO.getBalance(player.getUniqueId()) + " Cantox", NamedTextColor.GREEN))
                .append(ChatMenu.line("Vies restantes", String.valueOf(lifeManager.getLives(player.getUniqueId())), NamedTextColor.RED))
                .append(ChatMenu.line("Morts", String.valueOf(playerDAO.getDeaths(player.getUniqueId())), NamedTextColor.DARK_RED))
                .append(Component.text("\n"));

        if (faction != null) {
            menu = menu.append(ChatMenu.line("Faction", faction.getName(), NamedTextColor.GOLD))
                    .append(ChatMenu.line("Grade", faction.getMemberRank(player.getUniqueId()).getDisplayName(), NamedTextColor.YELLOW))
                    .append(ChatMenu.line("Claims faction", faction.getClaimCount() + "", NamedTextColor.AQUA));
        } else {
            menu = menu.append(ChatMenu.line("Faction", "Aucune", NamedTextColor.RED));
        }

        menu = menu.append(Component.text("\n"))
                .append(ChatMenu.clickableLine("Voir ma faction", "/f", "Ouvrir le menu faction"))
                .append(ChatMenu.clickableLine("Hôtel des ventes", "/ah", "Ouvrir l'HDV"))
                .append(ChatMenu.clickableLine("Mes homes", "/home", "Liste des homes"))
                .append(Component.text("\n"))
                .append(ChatMenu.separator());

        player.sendMessage(menu);
    }

    private static String formatPlayTime(long timestamp) {
        if (timestamp == 0) return "Inconnu";
        
        Instant created = Instant.ofEpochSecond(timestamp);
        Instant now = Instant.now();
        
        long days = ChronoUnit.DAYS.between(created, now);
        long months = days / 30;
        long years = days / 365;
        
        if (years > 0) {
            return years + " an" + (years > 1 ? "s" : "");
        } else if (months > 0) {
            return months + " mois";
        } else if (days > 0) {
            return days + " jour" + (days > 1 ? "s" : "");
        } else {
            return "Aujourd'hui";
        }
    }
}
