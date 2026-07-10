package fr.cantale.plugin.listeners;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import fr.cantale.plugin.lives.LifeManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

public class PlayerListener implements Listener {

    private final CantalePlugin plugin;
    private final PlayerDAO playerDAO;
    private final LifeManager lifeManager;

    public PlayerListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.playerDAO = new PlayerDAO(plugin);
        this.lifeManager = plugin.getLifeManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        playerDAO.createOrUpdatePlayer(uuid, player.getName());

        // Mettre à jour le nametag (tags, faction, grade) — 1 tick de délai pour que le joueur soit chargé
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getTagManager().updatePlayerNametag(player);
            }
        }, 1L);

        // Charger les homes du joueur
        plugin.getHomeManager().loadPlayerHomes(uuid);

        // Logger la connexion
        plugin.getActionLogger().logJoin(player.getName());

        // Bridge Discord
        if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
            plugin.getDiscordBot().sendSystemMessage("🟢 **" + player.getName() + "** a rejoint le serveur.");
        }

        int lives = lifeManager.getLives(uuid);
        if (lives <= 0 && plugin.getConfigManager().isBanOnZero()) {
            player.kickPlayer(ChatColor.translateAlternateColorCodes('&',
                    "&cVous n'avez plus de vies ! Achetez-en une ou attendez qu'un ami vous en donne une."));
            return;
        }

        player.sendMessage(formatMessage("&eBienvenue sur Cantale ! Vous avez &6" + lives + " &evie(s)."));
        player.sendMessage(formatMessage("&7Solde : &6" + playerDAO.getBalance(uuid) + " &7Cantox"));

        // Accueil Première Connexion
        if (!player.hasPlayedBefore()) {
            // Message de bienvenue global
            Bukkit.broadcastMessage("§8[§6Cantale§8] §aBienvenue à §e" + player.getName() + " §apour sa première connexion sur le serveur !");

            // Guide de mods en privé
            player.sendMessage("§8[§6Cantale§8] §7=== Guide de Démarrage ===");
            player.sendMessage("§e• Bobby : §7Permet d'augmenter votre render distance au-delà de la limite du serveur ! Installez Bobby en mod.");
            player.sendMessage("§e• Voice Chat : §7Nous vous conseillons d'installer Simple Voice Chat pour parler avec les joueurs en jeu.");
            player.sendMessage("§e• Texture Pack : §7Acceptez le resource pack proposé pour voir les items personnalisés de Cantale.");

            // Livre écrit donné à l'arrivée
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta meta = (BookMeta) book.getItemMeta();
            if (meta != null) {
                meta.setTitle("§6Guide de Cantale");
                meta.setAuthor("Cantale");
                
                List<String> pages = new ArrayList<>();
                pages.add(
                    "§8§lBienvenue sur\n§6§lCantale Network !\n\n" +
                    "§7Ce serveur PvP Faction est unique en son genre.\n\n" +
                    "§7Vous commencez avec §63 vies§7.\nChaque mort vous fait perdre une vie."
                );
                pages.add(
                    "§c§lSystème de Vies\n\n" +
                    "§7Si vous tombez à §c0 vie§7, vous serez §cbanni §7du serveur.\n\n" +
                    "§7Vous pouvez :\n" +
                    "§e• §7Acheter des vies au shop ou sur le site.\n" +
                    "§e• §7En donner à vos amis avec §a/givevie§7."
                );
                pages.add(
                    "§9§lMods Recommandés\n\n" +
                    "§7Pour profiter pleinement :\n" +
                    "§e• Bobby §7(Render distance infinie en solo/multi)\n" +
                    "§e• Simple Voice Chat §7(Chat vocal de proximité)\n\n" +
                    "§dAmusez-vous bien !"
                );
                
                meta.setPages(pages);
                book.setItemMeta(meta);
                
                // Ajouter le livre à l'inventaire du joueur
                player.getInventory().addItem(book);
            }
        }

        // Envoyer le resource pack Cantale
        String packUrl = plugin.getConfigManager().getResourcePackUrl();
        String packHash = plugin.getConfigManager().getResourcePackHash();
        if (!packUrl.isEmpty() && !packHash.isEmpty()) {
            player.setResourcePack(packUrl, packHash);
        }

        // Daily reward is now collectable via /daily or /journalier
        player.sendMessage("§8[§6Cantale§8] §7Utilisez §e/daily §7ou §e/journalier §7pour réclamer votre récompense quotidienne.");

        // Monthly VIE items for Aventurier/Héros
        int livesGiven = plugin.getRankManager().grantMonthlyLives(player);
        if (livesGiven > 0) {
            player.sendMessage("§8[§6Cantale§8] §aRécompense mensuelle : §e" + livesGiven + " item(s) Vie §aoctroyé(s) !");
        }

        // Wanted tier announcements
        handleWantedJoin(player);
    }

    private void handleWantedJoin(Player player) {
        var wantedMgr = plugin.getWantedManager();
        if (wantedMgr == null || !wantedMgr.isWanted(player.getUniqueId())) return;
        int tier = wantedMgr.getWantedTier(player.getUniqueId());
        if (tier < 2) return;

        double total = wantedMgr.getTotalBounty(player.getUniqueId());
        String tierLabel = wantedMgr.getTierLabel(tier);

        String loc = "";
        if (tier >= 3) {
            var facMgr = plugin.getFactionManager();
            var fac = facMgr != null ? facMgr.getFactionAtClaim(player.getWorld().getName(), player.getLocation().getChunk().getX(), player.getLocation().getChunk().getZ()) : null;
            boolean inClaim = fac != null;
            if (!inClaim) {
                loc = "\n📍 **Localisation:** " + String.format("%.0f", player.getLocation().getX()) + " " + String.format("%.0f", player.getLocation().getY()) + " " + String.format("%.0f", player.getLocation().getZ()) + " (" + player.getWorld().getName() + ")";
            } else {
                loc = "\n📍 **Localisation:** Dans un claim (caché)";
            }
        }

        String desc = "**" + player.getName() + "** s'est connecté !\n**Prime totale:** " + String.format("%.0f", total) + " Cantox\n**Palier:** " + tierLabel + loc;

        if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
            long ch = plugin.getDiscordBot().getConfig().getMcDeathsChannelId();
            if (ch != 0L) {
                plugin.getDiscordBot().sendEmbedToChannel(ch, java.awt.Color.ORANGE, "🚨 WANTED — Connexion", desc + "\n\n<@&1504909846288535705>");
            }
        }

        if (tier >= 4) {
            Bukkit.broadcastMessage("§8[§cWANTED§8] §e" + player.getName() + " §7s'est connecté ! Prime: §6" + String.format("%.0f", total) + " §7Cantox");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Nettoyer l'équipe scoreboard du joueur
        plugin.getTagManager().removePlayerTeam(player);

        // Logger la déconnexion
        plugin.getActionLogger().logQuit(player.getName());

        // Bridge Discord
        if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
            plugin.getDiscordBot().sendSystemMessage("🔴 **" + player.getName() + "** a quitté le serveur.");
        }

        // Wanted tier announcements
        handleWantedQuit(player);
    }

    private void handleWantedQuit(Player player) {
        var wantedMgr = plugin.getWantedManager();
        if (wantedMgr == null || !wantedMgr.isWanted(player.getUniqueId())) return;
        int tier = wantedMgr.getWantedTier(player.getUniqueId());
        if (tier < 2) return;

        double total = wantedMgr.getTotalBounty(player.getUniqueId());
        String tierLabel = wantedMgr.getTierLabel(tier);
        String desc = "**" + player.getName() + "** s'est déconnecté !\n**Prime totale:** " + String.format("%.0f", total) + " Cantox\n**Palier:** " + tierLabel;

        if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
            long ch = plugin.getDiscordBot().getConfig().getMcDeathsChannelId();
            if (ch != 0L) {
                plugin.getDiscordBot().sendEmbedToChannel(ch, java.awt.Color.ORANGE, "🚨 WANTED — Déconnexion", desc + "\n\n<@&1504909846288535705>");
            }
        }

        if (tier >= 4) {
            Bukkit.broadcastMessage("§8[§cWANTED§8] §e" + player.getName() + " §7s'est déconnecté ! Prime: §6" + String.format("%.0f", total) + " §7Cantox");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        lifeManager.handleDeath(player);

        // Les items VIE custom dans l'inventaire sont perdus à la mort (ne drop pas)
        fr.cantale.plugin.custom.CustomItemManager cim = plugin.getCustomItemManager();
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && cim.isCustomItem(item)
                    && cim.getCustomItemType(item) == fr.cantale.plugin.custom.CustomItemType.VIE) {
                item.setAmount(0);
            }
        }

        // Bridge Discord
        if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
            String deathMsg = event.getDeathMessage();
            if (deathMsg == null) deathMsg = player.getName() + " est mort";
            long ch = plugin.getDiscordBot().getConfig().getMcDeathsChannelId();
            if (ch != 0L) {
                plugin.getDiscordBot().sendEmbedToChannel(
                    ch,
                    java.awt.Color.DARK_GRAY,
                    "💀 Mort",
                    "**" + deathMsg + "**"
                );
            }
        }
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
