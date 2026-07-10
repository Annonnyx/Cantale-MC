package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.leaderboard.LeaderboardManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public class LeaderboardCategoryUI extends BaseUI {

    private final String category;
    private final LeaderboardManager leaderboardManager;

    public LeaderboardCategoryUI(CantalePlugin plugin, Player player, String category) {
        super(plugin, player, "§8[§6Cantale§8] §7Classement: " + category, 5);
        this.category = category;
        this.leaderboardManager = new LeaderboardManager(plugin);
    }

    @Override
    protected void init() {
        fillEmpty();

        String label = switch (category) {
            case "KILLS" -> "§c§lTop Kills";
            case "DEATHS" -> "§4§lTop Morts";
            case "RICHEST" -> "§6§lTop Richesse";
            case "FACTIONS" -> "§b§lTop Factions";
            default -> "§e§lClassement";
        };

        setItem(4, Material.BOOK, label, "§7Affichage des meilleurs joueurs/factions.");

        int slot = 10;
        int maxSlots = 35;

        if (category.equals("FACTIONS")) {
            List<LeaderboardManager.FactionLeaderboardEntry> entries = leaderboardManager.getTopFactions(21);
            for (LeaderboardManager.FactionLeaderboardEntry entry : entries) {
                if (slot >= maxSlots) break;
                if (slot % 9 == 8) slot += 2; // skip borders

                String medal = getMedal(entry.getRank());
                setItem(slot, Material.SHIELD, medal + " §e#" + entry.getRank() + " §f" + entry.getName(),
                        "§7Solde: §6" + entry.getBalance() + " Cantox",
                        "§7Power: §b" + entry.getPower());
                slot++;
            }
        } else {
            List<LeaderboardManager.LeaderboardEntry> entries = switch (category) {
                case "KILLS" -> leaderboardManager.getTopKills(21);
                case "DEATHS" -> leaderboardManager.getTopDeaths(21);
                default -> leaderboardManager.getTopRichest(21);
            };

            for (LeaderboardManager.LeaderboardEntry entry : entries) {
                if (slot >= maxSlots) break;
                if (slot % 9 == 8) slot += 2; // skip borders

                String medal = getMedal(entry.getRank());
                String valSuffix = switch (category) {
                    case "KILLS" -> " kills";
                    case "DEATHS" -> " morts";
                    default -> " Cantox";
                };

                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) skull.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.getName()));
                    meta.setDisplayName(medal + " §e#" + entry.getRank() + " §f" + entry.getName());
                    meta.setLore(java.util.Arrays.asList(
                            "§7Valeur: §6" + entry.getValue() + valSuffix
                    ));
                    skull.setItemMeta(meta);
                }
                setItem(slot, skull);
                slot++;
            }
        }

        addNavigationBar(true, false);
    }

    private String getMedal(int rank) {
        return switch (rank) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> "";
        };
    }

    @Override
    protected void handleClick(int slot) {
        // Clics passifs sur les stats : rien à faire
    }
}
