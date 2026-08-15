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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Intégration votes externes :
 *  - Votifier (NuVotifier) via réflexion — pas de dépendance compile-time (surtout site 1).
 *  - Polling API Serveur-Prive ({@link ServeurPriveVotePoller}, site 2).
 *  - Polling API ServeurListe ({@link ServeurListeVotePoller}, site 3, vérif par IP).
 *
 * Au boot :
 *  - Si la classe {@code com.vexsoftware.votifier.model.VotifierEvent} existe, on enregistre
 *    dynamiquement un EventExecutor qui réceptionne les votes et appelle {@link #handleVote}.
 *  - Sinon, on logue un avertissement (Top-Serveurs principalement).
 *  - Les pollers API démarrent dès qu'un token est présent.
 *
 * Si le joueur est en ligne : récompense immédiate (Cadeau(x) du Roi).
 * Sinon : vote stocké en pending, délivré à la prochaine connexion via {@link #onJoin}.
 */
public class VoteListener implements Listener {

    private static final String VOTIFIER_EVENT_CLASS = "com.vexsoftware.votifier.model.VotifierEvent";

    private final CantalePlugin plugin;
    private final VoteDAO voteDAO;
    private final VoteRewardManager rewardManager;
    private final VotePartyManager partyManager;
    private final ServeurPriveVotePoller serveurPrivePoller;
    private final ServeurListeVotePoller serveurListePoller;

    public VoteListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.voteDAO = plugin.getVoteDAO();
        this.rewardManager = plugin.getVoteRewardManager();
        this.partyManager = new VotePartyManager(plugin);
        new VoteReminder(plugin); // démarre le scheduler de rappels si activé
        VoteMonthlyManager.onStartup(plugin);
        this.serveurPrivePoller = new ServeurPriveVotePoller(plugin, this);
        this.serveurListePoller = new ServeurListeVotePoller(plugin, this);
        this.serveurPrivePoller.start();
        this.serveurListePoller.start();
    }

    public void shutdown() {
        if (serveurPrivePoller != null) {
            serveurPrivePoller.stop();
        }
        if (serveurListePoller != null) {
            serveurListePoller.stop();
        }
    }

    /**
     * Tente d'enregistrer le listener Votifier de manière dynamique.
     * @return true si l'API Votifier est présente et le listener a été câblé
     */
    @SuppressWarnings("unchecked")
    public boolean tryRegisterVotifier() {
        Class<?> votifierEventClass = loadVotifierEventClass();
        if (votifierEventClass == null) {
            plugin.getLogger().warning("[Vote] Votifier/NuVotifier ABSENT — Top-Serveurs ne pourra pas récompenser via Votifier. "
                    + "Serveur-Prive et ServeurListe utilisent l'API (polling). Installe NuVotifier.jar pour le site 1 (docs/Votes.md).");
            return false;
        }

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
        plugin.getLogger().info("[Vote] Après un vrai vote Top-Serveurs, tu dois voir « [Vote] VotifierEvent reçu ». "
                + "Serveur-Prive / ServeurListe = API polling (Votifier optionnel). Si ce log n'apparaît jamais "
                + "sur Top-Serveurs : le site n'atteint pas NuVotifier (IP/port/clé, host 0.0.0.0) — docs/Votes.md.");
        return true;
    }

    /**
     * Charge la classe VotifierEvent en utilisant le classloader du plugin NuVotifier/Votifier.
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

        try {
            return Class.forName(VOTIFIER_EVENT_CLASS);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

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
        String siteLabel = voteSite != null ? voteSite.displayName() : site;

        if (voteSite == null) {
            plugin.getLogger().warning("[Vote] Site Votifier non reconnu : \"" + site
                    + "\" — fallback 1× Cadeau du Roi. Vérifie le serviceName NuVotifier / config vote.sites.");
        }

        // Anti-doublon site (Votifier + polling API Serveur-Prive / ServeurListe, ou double event).
        // Pas de cooldown IP côté plugin : chaque site applique déjà le sien, et un filtre IP
        // bloquait les foyers à IP partagée et avalait des votes légitimes.
        if (voteSite != null && voteDAO.hasRecentVoteOnSite(uuid, siteLabel, voteSite.cooldownHours())) {
            plugin.getLogger().info("[Vote] Vote ignoré pour " + resolvedName + " sur " + siteLabel
                    + " : déjà récompensé dans la fenêtre de cooldown (" + voteSite.cooldownHours() + "h).");
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
            rewardManager.deliverTo(online, reward);
            voteDAO.recordVote(uuid, resolvedName, siteLabel, address,
                    "VOTE", reward.getSummary(), true);

            plugin.getLogger().info("[Vote] " + resolvedName + " a voté sur " + siteLabel
                    + " (service=" + site + ", online) — récompense : " + reward.getSummary());
            sendThanksMessage(online, siteLabel, reward, stats.streakDays());
            broadcastVote(resolvedName, siteLabel, reward.getSummary());
            partyManager.onVote(online);
            VoteMilestoneManager.checkAndReward(plugin, uuid, stats.totalVotes());
            VoteMonthlyManager.trackVote(plugin, uuid, resolvedName);
        } else {
            voteDAO.recordVote(uuid, resolvedName, siteLabel, address,
                    "VOTE", reward.getSummary(), false);
            plugin.getLogger().info("[Vote] " + resolvedName + " a voté sur " + siteLabel
                    + " (service=" + site + ", offline) — " + reward.getSummary() + " en attente.");
            broadcastVote(resolvedName, siteLabel, reward.getSummary());
            partyManager.onVote(null);
            VoteMilestoneManager.checkAndReward(plugin, uuid, stats.totalVotes());
            VoteMonthlyManager.trackVote(plugin, uuid, resolvedName);
        }
    }

    /**
     * Résout le site configuré à partir du serviceName Votifier.
     * Matching : displayName, URL/host, alias, clés {@code vote.tokens}, variantes alphanumériques.
     */
    public static VoteSite resolveSite(String siteName) {
        if (siteName == null || siteName.isBlank()) return null;
        List<VoteSite> sites = loadSites();
        for (VoteSite site : sites) {
            if (site.matches(siteName)) return site;
        }

        // Clés NuVotifier dans vote.tokens (souvent = hostname du site)
        CantalePlugin plugin = CantalePlugin.getInstance();
        if (plugin != null) {
            ConfigurationSection tokens = plugin.getConfig().getConfigurationSection("vote.tokens");
            if (tokens != null) {
                for (String tokenKey : tokens.getKeys(false)) {
                    VoteSite tokenAsSite = new VoteSite(tokenKey, "https://" + tokenKey, 1);
                    if (tokenAsSite.matches(siteName)) {
                        for (VoteSite site : sites) {
                            if (site.matches(tokenKey) || site.host().equals(VoteSite.normalize(tokenKey))) {
                                return site;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Charge les sites configurés dans config.yml.
     * Format : "Nom|URL|cooldownHeures" ou "Nom|URL|cooldownHeures|alias1,alias2".
     */
    public static List<VoteSite> loadSites() {
        List<VoteSite> sites = new ArrayList<>();
        var plugin = CantalePlugin.getInstance();
        if (plugin == null) return sites;
        List<String> raw = plugin.getConfig().getStringList("vote.sites");
        for (String entry : raw) {
            String[] parts = entry.split("\\|", 4);
            String name = parts[0].trim();
            String url = parts.length > 1 ? parts[1].trim() : "";
            int cooldown = 24;
            if (parts.length > 2) {
                try { cooldown = Integer.parseInt(parts[2].trim()); } catch (NumberFormatException ignored) {}
            }
            List<String> aliases = new ArrayList<>();
            if (parts.length > 3 && !parts[3].isBlank()) {
                aliases.addAll(Arrays.stream(parts[3].split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList());
            }
            // Alias implicites depuis vote.tokens dont la clé matche l'hôte
            ConfigurationSection tokens = plugin.getConfig().getConfigurationSection("vote.tokens");
            if (tokens != null) {
                String host = VoteSite.extractHost(url);
                for (String tokenKey : tokens.getKeys(false)) {
                    if (new VoteSite(name, url, cooldown).matches(tokenKey)
                            || host.equals(VoteSite.normalize(tokenKey))) {
                        aliases.add(tokenKey);
                    }
                }
            }
            sites.add(new VoteSite(name, url, cooldown, aliases));
        }
        return sites;
    }

    /**
     * Délivre les votes en attente à la connexion (Cadeau(x) du Roi uniquement).
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
                VoteReward reward = rewardFromPending(pv);
                rewardManager.deliverTo(player, reward);
                voteDAO.markDelivered(pv.id());

                player.sendMessage("§8[§6Vote§8] §aRécompense en attente délivrée : §e" + reward.getSummary()
                        + " §7via §f" + pv.site());
            }

            player.sendMessage("§8[§6Vote§8] §7Total : §a" + pending.size() + " §7vote(s) récupéré(s).");
        }, 60L);
    }

    /** Reconstruit la récompense clés à partir d'un pending (site ou summary). */
    public VoteReward rewardFromPending(VoteDAO.PendingVote pv) {
        VoteSite site = resolveSite(pv.site());
        if (site != null) {
            return rewardManager.roll(1, site);
        }
        int count = parseCrateCountFromSummary(pv.rewardSummary());
        return rewardManager.buildKeyReward(count);
    }

    static int parseCrateCountFromSummary(String summary) {
        if (summary == null || summary.isBlank()) return 1;
        // Formats attendus : "2× Cadeau du Roi" / "2x Cadeau du Roi"
        String s = summary.trim().toLowerCase(Locale.ROOT);
        if (s.contains("cadeau")) {
            try {
                String num = s.replaceAll("^(\\d+).*", "$1");
                int n = Integer.parseInt(num);
                if (n >= 1 && n <= 3) return n;
            } catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    private void sendThanksMessage(Player p, String site, VoteReward reward, int streak) {
        p.sendMessage("§8§m──────────§r §8[§6Vote§8] §8§m──────────");
        p.sendMessage("§aVote comptabilisé, récompense reçue !");
        p.sendMessage("§7Merci d'avoir voté sur §e" + site + " §7!");
        p.sendMessage("§7Récompense : §f" + reward.getSummary());
        if (streak > 1) {
            p.sendMessage("§7Streak : §6" + streak + " jour(s) §7consécutifs");
        }
        p.sendMessage("§8§m──────────────────────────");
    }

    private void broadcastVote(String name, String site, String rewardSummary) {
        if (!plugin.getConfig().getBoolean("vote.broadcast", true)) return;
        String msg = "§8[§6Vote§8] §e" + name + " §7a voté sur §f" + site
                + " §7et obtenu §f" + rewardSummary + "§7 !";
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
    }
}
