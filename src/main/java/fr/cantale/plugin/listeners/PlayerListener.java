package fr.cantale.plugin.listeners;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.custom.CantalameManager;
import fr.cantale.plugin.custom.CustomItemType;
import fr.cantale.plugin.database.PlayerDAO;
import fr.cantale.plugin.lives.LifeManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.Bukkit;
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

    /**
     * Gate pré-login : refuse les comptes à 0 vie avant qu'ils n'entrent en jeu
     * (évite le bypass BanList NAME via changement de pseudo).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!plugin.getConfigManager().isBanOnZero()) return;
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;

        UUID uuid = event.getUniqueId();
        // Assurer la ligne joueur (INSERT default lives pour les vrais nouveaux)
        playerDAO.createOrUpdatePlayer(uuid, event.getName());
        int lives = lifeManager.getLives(uuid);
        if (lives <= 0) {
            event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                ChatColor.translateAlternateColorCodes('&',
                    "&cVous n'avez plus de vies ! Achetez-en une ou attendez qu'un ami vous en donne une.")
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        playerDAO.createOrUpdatePlayer(uuid, player.getName());
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            playerDAO.setLastIp(uuid, player.getAddress().getAddress().getHostAddress());
        }

        int lives = lifeManager.getLives(uuid);
        if (lives <= 0 && plugin.getConfigManager().isBanOnZero()) {
            player.kickPlayer(ChatColor.translateAlternateColorCodes('&',
                    "&cVous n'avez plus de vies ! Achetez-en une ou attendez qu'un ami vous en donne une."));
            return;
        }

        if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
            boolean inFaction = plugin.getFactionManager() != null
                && plugin.getFactionManager().getPlayerFaction(uuid) != null;
            plugin.getDiscordBot().syncFactionRoleForPlayer(uuid, inFaction);
        }

        checkPendingRewards(player);

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

        player.sendMessage(formatMessage("&eBienvenue sur Cantale ! Vous avez &6" + lives + " &evie(s)."));
        player.sendMessage(formatMessage("&7Solde : &6" + playerDAO.getBalance(uuid) + " &7Cantox"));

        // Accueil Première Connexion
        if (!player.hasPlayedBefore()) {
            // Enregistrer le joueur comme nouveau pour /bvn
            plugin.getWelcomeCommand().registerNewcomer(player.getUniqueId());

            // Message de bienvenue global plus visible
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("§8§m--------------------------------------------------");
            Bukkit.broadcastMessage("§8[§6§lBienvenue§8] §e§l" + player.getName() + " §a§lrejoint Cantale pour la première fois !");
            Bukkit.broadcastMessage("§7Souhaitez-lui la bienvenue avec §e/bvn " + player.getName() + " §7et gagnez §e100 Cantox§7 !");
            Bukkit.broadcastMessage("§8§m--------------------------------------------------");

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

        // Pack : uniquement si send-on-join=true. Sinon c'est server.properties qui l'envoie.
        // Les deux en même temps → reloads en boucle + kick (require-resource-pack).
        if (plugin.getConfigManager().isResourcePackSendOnJoin()) {
            String packUrl = plugin.getConfigManager().getResourcePackUrl();
            String packHash = plugin.getConfigManager().getResourcePackHash();
            if (!packUrl.isEmpty() && !packHash.isEmpty()) {
                player.setResourcePack(packUrl, packHash);
            }
        }

        // Daily reward is now collectable via /daily or /journalier
        player.sendMessage("§8[§6Cantale§8] §7Utilisez §e/daily §7ou §e/journalier §7pour réclamer votre récompense quotidienne.");

        // Monthly VIE items for Aventurier/Héros
        int livesGiven = plugin.getRankManager().grantMonthlyLives(player);
        if (livesGiven > 0) {
            player.sendMessage("§8[§6Cantale§8] §aRécompense mensuelle : §e" + livesGiven + " item(s) Vie §aoctroyé(s) !");
        }

        // Sync playtime dès la connexion (sinon le site reste à 0 / périmé
        // tant que le joueur ne se déconnecte pas).
        UUID joinUuid = player.getUniqueId();
        long joinPlaytime = fr.cantale.plugin.tasks.PlaytimeSyncTask.playtimeSeconds(player);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            playerDAO.updatePlaytime(joinUuid, joinPlaytime);
        });

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

        // Sauvegarder le temps de jeu en BDD de manière asynchrone
        UUID uuid = player.getUniqueId();
        long playtimeSeconds = fr.cantale.plugin.tasks.PlaytimeSyncTask.playtimeSeconds(player);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            playerDAO.updatePlaytime(uuid, playtimeSeconds);
        });

        // Nettoyer l'équipe scoreboard du joueur
        plugin.getTagManager().removePlayerTeam(player);

        // Retirer le joueur du suivi des nouveaux pour /bvn
        plugin.getWelcomeCommand().unregisterNewcomer(player.getUniqueId());

        // Logger la déconnexion
        plugin.getActionLogger().logQuit(player.getName());

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

        // Pouvoir de faction : +5 pour la faction du tueur, -5 pour celle de la victime (PvP)
        Player killer = player.getKiller();
        if (killer != null && !killer.getUniqueId().equals(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean gained = plugin.getFactionManager().addPowerToPlayerFaction(killer.getUniqueId(), 5);
                boolean lost = plugin.getFactionManager().addPowerToPlayerFaction(player.getUniqueId(), -5);
                if (gained || lost) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (gained) {
                            killer.sendMessage("§8[§6Faction§8] §a+5 pouvoir pour ta faction grâce à ce kill !");
                        }
                        if (lost) {
                            player.sendMessage("§8[§6Faction§8] §c-5 pouvoir pour ta faction suite à ta mort.");
                        }
                    });
                }
            });
        }

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

    /** Invulnérabilité post-respawn pour éviter le spawn-kill le temps de se TP. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // 10 secondes (200 ticks) — setNoDamageTicks est en ticks
        player.setNoDamageTicks(20 * 10);
        player.sendMessage("§8[§6Cantale§8] §7Invulnérabilité de §e10s §7après respawn.");
        // Sécurité : re-appliquer au tick suivant (certains plugins reset noDamageTicks)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), 20 * 10));
            }
        });
    }

    private void checkPendingRewards(Player player) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "SELECT id, reward_type, reward_value, quantity FROM pending_rewards WHERE player_uuid = ? AND claimed = 0";
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                java.sql.ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String type = rs.getString("reward_type");
                    String value = rs.getString("reward_value");
                    int quantity = rs.getInt("quantity");

                    // Appliquer la récompense sur le thread principal de Bukkit
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        applyReward(player, type, value, quantity);
                        
                        // Marquer comme consommée en asynchrone
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            try (java.sql.Connection c = plugin.getDatabaseManager().getConnection();
                                 java.sql.PreparedStatement uStmt = c.prepareStatement("UPDATE pending_rewards SET claimed = 1 WHERE id = ?")) {
                                uStmt.setInt(1, id);
                                uStmt.executeUpdate();
                            } catch (Exception e) {
                                plugin.getLogger().severe("Erreur update pending_rewards: " + e.getMessage());
                            }
                        });
                    });
                }
            } catch (Exception e) {
                // La table n'existe peut-être pas ou erreur de connexion
            }
        });
    }

    private void applyReward(Player player, String type, String value, int quantity) {
        UUID uuid = player.getUniqueId();
        switch (type.toUpperCase()) {
            case "CANTOX":
                try {
                    double amount = Double.parseDouble(value);
                    plugin.getEconomyManager().addBalance(uuid, amount);
                    player.sendMessage("§8[§6Giveaway§8] §aFélicitations ! Tu as reçu §e" + String.format("%.0f", amount) + " Cantox§a de ton gain Discord !");
                } catch (NumberFormatException ignored) {}
                break;
            case "RANK":
                fr.cantale.plugin.rank.PlayerRank rank = fr.cantale.plugin.rank.PlayerRank.fromString(value);
                if (rank != fr.cantale.plugin.rank.PlayerRank.NONE) {
                    plugin.getRankManager().setRank(uuid, rank);
                    player.sendMessage("§8[§6Giveaway§8] §aFélicitations ! Tu as reçu le grade " + rank.getDisplayName() + " §ade ton gain Discord !");
                }
                break;
            case "ITEM":
                try {
                    CustomItemType itemType = CustomItemType.valueOf(value.toUpperCase());
                    // Cantalame doit passer par CantalameManager (PDC kills/victimes),
                    // pas createCustomItem — sinon l'épée ne progresse pas.
                    ItemStack itemStack = itemType.isCantalame()
                        ? new CantalameManager(plugin).createCantalame()
                        : plugin.getCustomItemManager().createCustomItem(itemType);
                    if (itemStack != null) {
                        itemStack.setAmount(quantity);
                        player.sendMessage("§8[§6Giveaway§8] §aFélicitations ! Tu as gagné §e" + quantity + "x " + itemType.getDisplayName() + " §asur Discord !");
                        
                        // Essayer d'ajouter à l'inventaire
                        java.util.HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(itemStack.clone());
                        if (remaining.isEmpty()) {
                            player.sendMessage("§aLes items ont été ajoutés à ton inventaire.");
                            return;
                        }
                        
                        // S'il reste des items, essayer l'Ender Chest
                        ItemStack remItem = remaining.values().iterator().next();
                        java.util.HashMap<Integer, ItemStack> ecRemaining = player.getEnderChest().addItem(remItem.clone());
                        if (ecRemaining.isEmpty()) {
                            player.sendMessage("§aTon inventaire était plein, les items ont été placés dans ton Ender Chest.");
                            return;
                        }
                        
                        // S'il en reste encore, essayer le coffre privé virtuel (/pc)
                        ItemStack pcRem = ecRemaining.values().iterator().next();
                        java.util.List<ItemStack> pcRemaining = plugin.getPrivateChestService().storeItemsInPrivateChest(uuid, java.util.List.of(pcRem.clone()));
                        if (pcRemaining.isEmpty()) {
                            player.sendMessage("§aTon inventaire et ton Ender Chest étaient pleins, les items ont été placés dans ton coffre privé virtuel (/pc).");
                            return;
                        }
                        
                        // Si toujours plein, drop par terre
                        for (ItemStack drop : pcRemaining) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                        player.sendMessage("§cTes coffres étaient pleins ! Les items restants ont été jetés à tes pieds.");
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Erreur applyReward ITEM: " + e.getMessage());
                }
                break;
            default:
                player.sendMessage("§8[§6Giveaway§8] §aFélicitations ! Tu as gagné le lot suivant : §e" + value);
                break;
        }
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
