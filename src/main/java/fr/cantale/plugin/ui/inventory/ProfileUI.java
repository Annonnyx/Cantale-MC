package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class ProfileUI extends BaseUI {

    public ProfileUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Profil de " + player.getName(), 5);
    }

    @Override
    protected void init() {
        fillEmpty();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName("§6§l" + player.getName());
            PlayerRank rank = plugin.getRankManager().getRank(player);
            Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
            int lives = plugin.getLifeManager().getLives(player.getUniqueId());
            double balance = plugin.getEconomyManager().getBalance(player);
            meta.setLore(java.util.Arrays.asList(
                "§7Grade: " + formatRank(rank),
                "§7Faction: §e" + (faction != null ? faction.getName() : "Aucune"),
                "§7Vies: §c" + lives,
                "§7Cantox: §6" + (int) balance
            ));
            head.setItemMeta(meta);
        }
        setItem(4, head);

        setItem(11, Material.BOOK, "§eStatistiques", "§7Kills, morts, playtime");
        setItem(13, Material.TOTEM_OF_UNDYING, "§eVies", "§7Gérer vos vies");
        setItem(15, Material.GOLD_NUGGET, "§eÉconomie", "§7Balance & transactions");
        setItem(17, Material.SHIELD, "§eFaction", "§7Votre faction");

        setItem(29, Material.RED_BED, "§eHomes", "§7Vos homes");
        setItem(31, Material.ENDER_PEARL, "§eWarps", "§7Warps publics");
        setItem(33, Material.SKELETON_SKULL, "§eWanted", "§7Primes");
        setItem(35, Material.NAME_TAG, "§ePersonnaliser mes Tags", "§7Gérer vos tags et grades affichés");

        addNavigationBar(true, false);
    }

    @Override
    protected void handleClick(int slot) {
        NavigationManager nav = plugin.getUIManager().getNavigationManager();
        switch (slot) {
            case 11 -> nav.openUI(player, new StatisticsUI(plugin, player));
            case 13 -> nav.openUI(player, new LifeUI(plugin, player));
            case 15 -> nav.openUI(player, new EconomyUI(plugin, player));
            case 17 -> nav.openUI(player, new FactionMainUI(plugin, player));
            case 29 -> player.performCommand("home");
            case 31 -> player.performCommand("warp");
            case 33 -> player.performCommand("wanted");
            case 35 -> nav.openUI(player, new TagCustomizationUI(plugin, player));
        }
    }

    private String formatRank(PlayerRank rank) {
        return switch (rank) {
            case AVENTURIER -> "§bAventurier";
            case VIP -> "§aVIP";
            case CHEVRE -> "§6Chèvre";
            case MODERATOR -> "§9Modérateur";
            case ADMIN -> "§cAdmin";
            case OWNER -> "§4Owner";
            default -> "§7Aucun";
        };
    }
}
