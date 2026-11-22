package fr.cantale.plugin.ui.inventory;

import com.google.gson.JsonObject;
import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.cosmetics.SeasonPassService;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import org.bukkit.OfflinePlayer;

public class ProfileUI extends BaseUI {

    private final OfflinePlayer target;

    public ProfileUI(CantalePlugin plugin, Player player) {
        this(plugin, player, player);
    }

    public ProfileUI(CantalePlugin plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer, "§8[§6Cantale§8] §7Profil de " + (target.getName() != null ? target.getName() : "Joueur"), 5);
        this.target = target;
    }

    @Override
    protected void init() {
        fillEmpty();

        boolean isSelf = player.getUniqueId().equals(target.getUniqueId());

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.setDisplayName("§6§l" + (target.getName() != null ? target.getName() : "Inconnu"));
            PlayerRank rank = plugin.getRankManager().getRank(target.getUniqueId());
            Faction faction = plugin.getFactionManager().getPlayerFaction(target.getUniqueId());
            int lives = plugin.getLifeManager().getLives(target.getUniqueId());
            double balance = plugin.getPlayerDAO().getBalance(target.getUniqueId());
            meta.setLore(java.util.Arrays.asList(
                "§7Grade: " + formatRank(rank),
                "§7Faction: §e" + (faction != null ? faction.getName() : "Aucune"),
                "§7Vies: §c" + lives,
                "§7Cantox: §6" + (int) balance
            ));
            head.setItemMeta(meta);
        }
        setItem(4, head);

        if (isSelf) {
            setItem(11, Material.BOOK, "§eStatistiques", "§7Kills, morts, playtime");
            setItem(13, Material.TOTEM_OF_UNDYING, "§eVies", "§7Gérer vos vies");
            setItem(15, Material.GOLD_NUGGET, "§eÉconomie", "§7Balance & transactions");
            setItem(17, Material.SHIELD, "§eFaction", "§7Votre faction");

            setItem(29, Material.RED_BED, "§eHomes", "§7Vos homes");
            setItem(31, Material.ENDER_PEARL, "§eWarps", "§7Warps publics");
            setItem(33, Material.SKELETON_SKULL, "§eWanted", "§7Primes");
            setItem(35, Material.NAME_TAG, "§ePersonnaliser mes Tags", "§7Gérer vos tags et grades affichés");

            boolean bossBarOn = plugin.getPlayerDAO().isBossBarEnabled(player.getUniqueId());
            setItem(27, Material.BEACON,
                    bossBarOn ? "§aBossBar infos" : "§cBossBar infos",
                    "§7Barre d'infos en haut de l'écran",
                    "§7(solde, vies, faction, events…)",
                    "",
                    bossBarOn ? "§aÉtat : Activée" : "§cÉtat : Désactivée",
                    "§eClic pour " + (bossBarOn ? "désactiver" : "activer"));
        } else {
            PlayerRank rank = plugin.getRankManager().getRank(target.getUniqueId());
            Faction faction = plugin.getFactionManager().getPlayerFaction(target.getUniqueId());
            int lives = plugin.getLifeManager().getLives(target.getUniqueId());
            double balance = plugin.getPlayerDAO().getBalance(target.getUniqueId());

            setItem(11, Material.BOOK, "§eGrade", "§7" + formatRank(rank));
            setItem(13, Material.TOTEM_OF_UNDYING, "§eVies", "§7Vies restantes : §c" + lives);
            setItem(15, Material.GOLD_NUGGET, "§eÉconomie", "§7Solde : §6" + (int) balance + " Cantox");
            setItem(17, Material.SHIELD, "§eFaction", "§7" + (faction != null ? faction.getName() : "Aucune"));
        }

        addNavigationBar(true, false);
    }

    @Override
    protected void handleClick(int slot) {
        boolean isSelf = player.getUniqueId().equals(target.getUniqueId());
        if (!isSelf) return;

        NavigationManager nav = plugin.getUIManager().getNavigationManager();
        switch (slot) {
            case 11 -> nav.openUI(player, new StatisticsUI(plugin, player));
            case 13 -> nav.openUI(player, new LifeUI(plugin, player));
            case 15 -> nav.openUI(player, new EconomyUI(plugin, player));
            case 17 -> nav.openUI(player, new FactionMainUI(plugin, player));
            case 27 -> {
                boolean next = !plugin.getPlayerDAO().isBossBarEnabled(player.getUniqueId());
                plugin.getPlayerDAO().setBossBarEnabled(player.getUniqueId(), next);
                if (plugin.getRotatingBossBarManager() != null) {
                    plugin.getRotatingBossBarManager().setPlayerEnabled(player, next);
                }
                init();
            }
            case 29 -> player.performCommand("home");
            case 31 -> player.performCommand("warp");
            case 33 -> player.performCommand("wanted");
            case 35 -> nav.openUI(player, new TagCustomizationUI(plugin, player));
        }
    }

    @Override
    public void writeClientDetail(JsonObject screen) {
        boolean self = player.getUniqueId().equals(target.getUniqueId());
        PlayerRank rank = plugin.getRankManager().getRank(target.getUniqueId());
        if (rank == null) rank = PlayerRank.NONE;
        Faction faction = plugin.getFactionManager().getPlayerFaction(target.getUniqueId());
        SeasonPassService pass = plugin.getSeasonPass();
        SeasonPassService.Look look = pass == null
                ? SeasonPassService.Look.NONE
                : pass.look(target.getUniqueId());
        JsonObject detail = new JsonObject();
        detail.addProperty("self", self);
        detail.addProperty("uuid", target.getUniqueId().toString());
        detail.addProperty("name", target.getName() != null ? target.getName() : "Inconnu");
        detail.addProperty("rank", rank.getDisplayName());
        detail.addProperty("rankId", rank.name());
        detail.addProperty("faction", faction != null ? faction.getName() : "Aucune");
        detail.addProperty("lives", plugin.getLifeManager().getLives(target.getUniqueId()));
        detail.addProperty("cantox", (int) plugin.getPlayerDAO().getBalance(target.getUniqueId()));
        detail.addProperty("bossbar", self && plugin.getPlayerDAO().isBossBarEnabled(player.getUniqueId()));
        detail.addProperty("title", cosmeticName(look.titleId()));
        detail.addProperty("aura", cosmeticName(look.aura()));
        detail.addProperty("cape", cosmeticName(look.cape()));
        screen.add("detail", detail);
    }

    private String cosmeticName(String id) {
        if (id == null || id.isBlank()) return "";
        String key = "cosmetic." + id;
        String raw = plugin.messages().raw(player, key);
        if (raw != null && !raw.isBlank() && !raw.equals(key)) {
            return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', raw));
        }
        int cut = id.indexOf('_');
        String rest = cut >= 0 && cut < id.length() - 1 ? id.substring(cut + 1) : id;
        if (rest.isBlank()) return "";
        return Character.toUpperCase(rest.charAt(0)) + rest.substring(1);
    }

    private String formatRank(PlayerRank rank) {
        return switch (rank) {
            case AVENTURIER -> "§bAventurier";
            case VIP -> "§aVIP";
            case CHEVRE -> "§6Chèvre";
            case MODERATOR -> "§9Modo";
            case ADMIN -> "§cAdmin";
            case OWNER -> "§4Owner";
            default -> "§7Aucun";
        };
    }
}
