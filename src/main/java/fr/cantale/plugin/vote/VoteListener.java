package fr.cantale.plugin.vote;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Intégration Votifier (NuVotifier) via réflexion — pas de dépendance compile-time.
 *
 * Au boot :
 *  - Si la classe {@code com.vexsoftware.votifier.model.VotifierEvent} existe, on enregistre
 *    dynamiquement un EventExecutor qui réceptionne les votes et appelle {@link #handleVote}.
 *  - Sinon, on logue un avertissement et le système devient passif (commandes /vote
 *    fonctionnent toujours mais aucun vote ne sera reçu).
 *
 * Si le joueur est en ligne : récompense immédiate.
 * Sinon : vote stocké en pending, délivré à la prochaine connexion via {@link #onJoin}.
 */
public class VoteListener implements Listener {

    private static final String VOTIFIER_EVENT_CLASS = "com.vexsoftware.votifier.model.VotifierEvent";

    private final CantalePlugin plugin;
    private final VoteDAO voteDAO;
    private final VoteRewardManager rewardManager;
    private final VotePartyManager partyManager;
    private final VoteReminder reminder;

    public VoteListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.voteDAO = plugin.getVoteDAO();
        this.rewardManager = plugin.getVoteRewardManager();
        this.partyManager = new VotePartyManager(plugin);
        this.reminder = new VoteReminder(plugin);
        VoteMonthlyManager.onStartup(plugin);
    }

    /**
     * Tente d'enregistrer le listener Votifier de manière dynamique.
     * @return true si l'API Votifier est présente et le listener a été câblé
     */
    @SuppressWarnings("unchecked")
    public boolean tryRegisterVotifier() {
        Class<?> votifierEventClass = loadVotifierEventClass();
        if (votifierEventClass == null) {
            plugin.getLogger().info("[Vote] Votifier/NuVotifier non détecté — système de vote passif. Installe NuVotifier.jar pour activer.");
            return false;
        }

        // Caster en Class<? extends Event>
        if (!Event.class.isAssignableFrom(votifierEventClass)) {
            plugin.getLogger().warning("[Vote] La classe " + VOTIFIER_EVENT_CLASS + " ne dérive pas de Event ?!");
            return false;
        }

        Class<? extends Event> eventClass = (Class<? extends Event>) votifierEventClass;
        PluginManager pm = Bukkit.getPluginManager();
        EventExecutor executor = (listener, event) -> {
            try {
                handleVotifierEvent(event);
            } catch (Throwable t) {
                plugin.getLogger().warning("[Vote] Erreur traitement vote : " + t.getMessage());
                t.printStackTrace();
            }
        };

        pm.registerEvent(eventClass, this, EventPriority.NORMAL, executor, plugin);
        plugin.getLogger().info("[Vote] Votifier détecté — listener enregistré sur " + eventClass.getSimpleName());
        return true;
    }

    /**
     * Charge la classe VotifierEvent en utilisant le classloader du plugin NuVotifier/Votifier.
     * C'est nécessaire car Bukkit isole les classloaders entre plugins.
     */
    private Class<?> loadVotifierEventClass() {
        PluginManager pm = Bukkit.getPluginManager();

        String[] pluginNames = {"NuVotifier", "Votifier"};
        for (String name : pluginNames) {
            Plugin votifierPlugin = pm.getPlugin(name);
            if (votifierPlugin != null && votifierPlugin.isEnabled()) {
                try {
                    ClassLoader cl = votifierPlugin.getClass().getClassLoader();
                    Class<?> clazz = Class.forName(VOTIFIER_EVENT_CLASS, true, cl);
                    plugin.getLogger().info("[Vote] Plugin " + name + " trouvé et classe VotifierEvent chargée via son classloader.");
                    return clazz;
                } catch (ClassNotFoundException e) {
                    plugin.getLogger().warning("[Vote] Plugin " + name + " trouvé mais classe " + VOTIFIER_EVENT_CLASS + " introuvable dans son classloader.");
                }
            }
        }

        // Fallback si le plugin n'est pas encore chargé dans le PluginManager
        try {
            return Class.forName(VOTIFIER_EVENT_CLASS);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    /**
     * Reçoit un VotifierEvent en tant qu'objet générique et extrait via réflexion :
     *  - getVote() → Vote
     *  - vote.getUsername() / getServiceName() / getAddress()
     */
    private void handleVotifierEvent(Event event) {
        plugin.getLogger().info("[Vote] VotifierEvent reçu : " + event.getClass().getName());
        try {
            Method getVote = event.getClass().getMethod("getVote");
            Object vote = getVote.invoke(event);
            if (vote == null) {
                plugin.getLogger().warning("[Vote] VotifierEvent.getVote() a retourné null");
                return;
            }

            String username = invokeStringMethod(vote, "getUsername");
            String site     = invokeStringMethod(vote, "getServiceName");
            String address  = invokeStringMethod(vote, "getAddress");

            plugin.getLogger().info("[Vote] Extrait — username=" + username + ", site=" + site + ", address=" + address);

            if (username == null || username.isEmpty()) {
                plugin.getLogger().warning("[Vote] Vote reçu sans username");
                return;
            }
            handleVote(username, site == null ? "Inconnu" : site, address == null ? "" : address);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("[Vote] Impossible d'extraire le vote de l'événement : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String invokeStringMethod(Object obj, String name) {
        try {
            Method m = obj.getClass().getMethod(name);
            Object r = m.invoke(obj);
            return r == null ? null : r.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Logique principale de traitement d'un vote (en ligne ou offline). */
    public void handleVote(String username, String site, String address) {
        Player online = Bukkit.getPlayerExact(username);
        UUID uuid;
        String resolvedName;
        if (online != null) {
            uuid = online.getUniqueId();
            resolvedName = online.getName();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(username);
            uuid = offline.getUniqueId();
            resolvedName = offline.getName() != null ? offline.getName() : username;
        }

        VoteSite voteSite = resolveSite(site);

        // Anti-abuse : vérifier le cooldown IP pour ce site
        if (voteSite != null && voteDAO.hasRecentVoteFromIp(address, voteSite, plugin.getConfig().getInt("vote.ip-cooldown-hours", 1))) {
            plugin.getLogger().info("[Vote] Vote ignoré pour " + resolvedName + " sur " + site
                    + " : cooldown IP actif (" + address + ").");
            return;
        }

        VoteDAO.VoteStats stats = voteDAO.applyVote(uuid, resolvedName);
        if (stats == null) {
            plugin.getLogger().warning("[Vote] Impossible de comptabiliser le vote de " + resolvedName
                    + " sur " + site + " (erreur base de données).");
            if (online != null) {
                online.sendMessage("§8[§6Vote§8] §cUne erreur est survenue lors de l'enregistrement de ton vote. " +
                        "Contacte un administrateur si le problème persiste.");
            }
            return;
        }

        VoteReward reward = rewardManager.roll(stats.streakDays(), voteSite);

        if (online != null) {
            // Délivre uniquement les clés de vote (Cadeaux du Roi) au lieu de la récompense aléatoire
            VoteReward keyReward = buildKeyRewardOnly(voteSite, reward.getTier());
            rewardManager.deliverTo(online, keyReward);
            voteDAO.recordVote(uuid, resolvedName, site, address,
                    reward.getTier().name(), keyReward.getSummary(), true);

            plugin.getLogger().info("[Vote] " + resolvedName + " a voté sur " + site
                    + " (online) — récompense : " + keyReward.getSummary());
            sendThanksMessage(online, site, keyReward, stats.streakDays());
            broadcastVote(resolvedName, site, keyReward.getTier());
            partyManager.onVote(online);
            VoteMilestoneManager.checkAndReward(plugin, uuid, stats.totalVotes());
            VoteMonthlyManager.trackVote(plugin, uuid, resolvedName);
        } else {
            voteDAO.recordVote(uuid, resolvedName, site, address,
                    reward.getTier().name(), reward.getSummary(), false);
            plugin.getLogger().info("[Vote] " + resolvedName + " a voté sur " + site
                    + " (offline) — " + reward.getSummary() + " en attente.");
            broadcastVote(resolvedName, site, reward.getTier());
            partyManager.onVote(null);
            VoteMilestoneManager.checkAndReward(plugin, uuid, stats.totalVotes());
            VoteMonthlyManager.trackVote(plugin, uuid, resolvedName);
        }
    }

    private VoteSite resolveSite(String siteName) {
        if (siteName == null) return null;
        for (VoteSite site : loadSites()) {
            if (siteName.equalsIgnoreCase(site.displayName())
                    || siteName.equalsIgnoreCase(site.url())) {
                return site;
            }
        }
        return null;
    }

    /**
     * Charge les sites configurés dans config.yml.
     * Format attendu : "Nom|URL|cooldownHeures".
     */
    public static List<VoteSite> loadSites() {
        List<VoteSite> sites = new ArrayList<>();
        var plugin = fr.cantale.plugin.CantalePlugin.getInstance();
        if (plugin == null) return sites;
        List<String> raw = plugin.getConfig().getStringList("vote.sites");
        for (String entry : raw) {
            String[] parts = entry.split("\\|", 3);
            String name = parts[0];
            String url = parts.length > 1 ? parts[1] : "";
            int cooldown = 24;
            if (parts.length > 2) {
                try { cooldown = Integer.parseInt(parts[2].trim()); } catch (NumberFormatException ignored) {}
            }
            sites.add(new VoteSite(name, url, cooldown));
        }
        return sites;
    }

    /**
     * Délivre les votes en attente à la connexion.
     * Re-tire une récompense de tier équivalent (les items custom doivent être
     * reconstruits via le {@link VoteRewardManager}).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        List<VoteDAO.PendingVote> pending = voteDAO.getPendingVotes(uuid);
        if (pending.isEmpty()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            for (VoteDAO.PendingVote pv : pending) {
                VoteReward.Tier tier;
                try {
                    tier = VoteReward.Tier.valueOf(pv.tier());
                } catch (Exception e) {
                    tier = VoteReward.Tier.COMMUN;
                }
                VoteReward reward = generateForTier(tier);
                rewardManager.deliverTo(player, reward);
                voteDAO.markDelivered(pv.id());

                player.sendMessage("§8[§6Vote§8] §aRécompense en attente délivrée : §e" + reward.getSummary()
                        + " §8(" + tier.getDisplayName() + "§8) §7via §f" + pv.site());
            }

            player.sendMessage("§8[§6Vote§8] §7Total : §a" + pending.size() + " §7vote(s) récupéré(s).");
        }, 60L);
    }

    private VoteReward generateForTier(VoteReward.Tier tier) {
        return rewardManager.roll(switch (tier) {
            case LEGENDAIRE -> 999;
            case EPIQUE -> 30;
            case RARE -> 7;
            case COMMUN -> 1;
        });
    }

    private VoteReward buildKeyRewardOnly(VoteSite site, VoteReward.Tier tier) {
        if (site == null) {
            return new VoteReward(tier, 0, List.of(), "Aucune clé de vote");
        }
        int crateCount = site.crateCount();
        fr.cantale.plugin.crate.CrateManager crateManager = plugin.getCrateManager();
        if (crateManager == null || crateCount <= 0) {
            return new VoteReward(tier, 0, List.of(), "Aucune clé de vote");
        }
        List<org.bukkit.inventory.ItemStack> items = new ArrayList<>();
        for (int i = 0; i < crateCount; i++) {
            items.add(crateManager.createCrateItem(fr.cantale.plugin.crate.CrateType.VOTE));
        }
        String summary = crateCount + "× Cadeau du Roi";
        return new VoteReward(tier, 0, items, summary);
    }

    private void sendThanksMessage(Player p, String site, VoteReward reward, int streak) {
        p.sendMessage("§8§m──────────§r §8[§6Vote§8] §8§m──────────");
        p.sendMessage("§aVote comptabilisé, récompense reçue !");
        p.sendMessage("§7Merci d'avoir voté sur §e" + site + " §7!");
        p.sendMessage("§7Récompense " + reward.getTier().getDisplayName() + " §7: §f" + reward.getSummary());
        if (streak > 1) {
            p.sendMessage("§7Streak : §6" + streak + " jour(s) §7consécutifs");
            if (streak == 6) p.sendMessage("§eDemain → bonus minimum §9Rare §egaranti !");
            else if (streak == 29) p.sendMessage("§eDemain → bonus minimum §5Épique §egaranti !");
        }
        p.sendMessage("§8§m──────────────────────────");
    }

    private void broadcastVote(String name, String site, VoteReward.Tier tier) {
        if (!plugin.getConfig().getBoolean("vote.broadcast", true)) return;
        String msg = "§8[§6Vote§8] §e" + name + " §7a voté sur §f" + site
                + " §7et obtenu " + tier.getDisplayName() + "§7 !";
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
    }
}
