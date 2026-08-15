package fr.cantale.plugin.anticheat.grim;

import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimUser;
import ac.grim.grimac.api.event.EventBus;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.grim.grimac.api.plugin.GrimPlugin;
import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.anticheat.AntiCheatManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Pont entre Grim AC (packet-based, compensation ping/TPS native) et le module
 * anti-cheat natif Cantale.
 *
 * - Remonte chaque flag Grim dans le pipeline natif (trust, alertes MC/Discord, BDD).
 * - Annule le traitement Grim pour les joueurs exemptés natifs (admin, vanish, trace).
 * - Fallback : si Grim est absent au démarrage, les checks natifs mouvement/combat
 *   (désactivés par défaut car doublons de Grim) sont réactivés à chaud — jamais
 *   de trou de protection.
 *
 * Les handlers Grim sont appelés sur les threads Netty : tout traitement Bukkit
 * est rebasculé sur le thread principal.
 */
public class GrimBridge {

    private final AntiCheatManager manager;
    private final CantalePlugin plugin;

    private volatile boolean grimPresent;
    private GrimPlugin grimPlugin;

    public GrimBridge(AntiCheatManager manager) {
        this.manager = manager;
        this.plugin = manager.getPlugin();
    }

    /**
     * Détecte Grim et enregistre le bridge. À appeler après l'init des checks natifs.
     */
    public void enable() {
        Plugin grim = Bukkit.getPluginManager().getPlugin("GrimAC");
        if (grim == null || !grim.isEnabled()) {
            activateFallback("GrimAC introuvable ou désactivé");
            return;
        }

        try {
            GrimAbstractAPI api = GrimAPIProvider.get();
            this.grimPlugin = api.getGrimPlugin(plugin);
            EventBus bus = api.getEventBus();
            bus.get(FlagEvent.class).onFlag(grimPlugin, this::onGrimFlag);

            this.grimPresent = true;
            manager.getConfig().setGrimDetected(true);
            // Grim présent : les doublons natifs restent désactivés
            manager.getConfig().disableNativeDuplicates();
            plugin.getLogger().info("[AntiCheat] Grim détecté (" + grim.getDescription().getVersion()
                    + ") — bridge actif, checks natifs mouvement/combat en mode complément.");
        } catch (Throwable t) {
            plugin.getLogger().warning("[AntiCheat] Échec de l'initialisation du bridge Grim : " + t.getMessage());
            activateFallback("erreur d'initialisation de l'API Grim");
        }
    }

    /**
     * Appelé par Grim sur le thread Netty du joueur.
     * Retourne le nouvel état d'annulation (true = Grim ne traite pas le flag).
     */
    private boolean onGrimFlag(GrimUser user, AbstractCheck check, String verbose, boolean cancelled) {
        UUID uuid = user.getUniqueId();
        String checkName = check.getCheckName();

        // Exemptions staff natives : admin / bypass / modérateur en vanish ou trace
        Player player = uuid != null ? Bukkit.getPlayer(uuid) : null;
        if (player != null && manager.shouldBypass(player)) {
            return true; // annule le flag côté Grim (pas d'alerte ni de sanction Grim)
        }

        if (!manager.getConfig().isGrimIntegrationEnabled()) {
            return cancelled;
        }

        // Rebascule sur le thread principal pour tout le traitement Bukkit
        final String verboseSafe = verbose == null ? "" : verbose;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null || !online.isOnline()) return;
            manager.handleGrimViolation(online, checkName, verboseSafe);
        });

        return cancelled;
    }

    /**
     * Grim absent → le natif repasse en mode complet (checks mouvement/combat réactivés).
     */
    private void activateFallback(String reason) {
        this.grimPresent = false;
        manager.getConfig().setGrimDetected(false);
        manager.getConfig().enableNativeFallback();
        plugin.getLogger().warning("[AntiCheat] " + reason
                + " — fallback : checks natifs mouvement/combat/rotation réactivés à chaud.");
    }

    public void disable() {
        if (grimPresent) {
            try {
                GrimAPIProvider.get().getEventBus().unregisterAllListeners(grimPlugin);
            } catch (Throwable ignored) {
                // Grim déjà déchargé
            }
        }
        grimPresent = false;
    }

    public boolean isGrimPresent() {
        return grimPresent;
    }
}
