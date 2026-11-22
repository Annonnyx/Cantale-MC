package fr.cantale.plugin.web;

import fr.cantale.plugin.cache.RedisChannels;

/**
 * Décide quoi faire d'un message Redis. Le polling SQL n'est jamais le chemin actif :
 * Redis absent = pont temps réel coupé, les tables restent pour l'archivage uniquement.
 */
public final class RedisRelayPolicy {

    public enum Action {
        /** Site → jeu : diffuser en jeu. */
        RELAY_CHAT,
        /** Jeu → site : le serveur publie, il ne réinjecte pas son propre écho. */
        PUBLISH_ONLY,
        /** Chat d'un autre nœud : afficher ici, sans republier. */
        RELAY_CROSS_CHAT,
        /** Action admin du site, exécutée sur le thread principal. */
        ADMIN,
        IGNORE
    }

    private RedisRelayPolicy() {
    }

    public static Action actionFor(String channel) {
        if (RedisChannels.CHAT_WEB_TO_MC.equals(channel)) {
            return Action.RELAY_CHAT;
        }
        if (RedisChannels.CHAT_MC_TO_WEB.equals(channel)) {
            return Action.PUBLISH_ONLY;
        }
        if (RedisChannels.ADMIN_ACTIONS.equals(channel)) {
            return Action.ADMIN;
        }
        return Action.IGNORE;
    }

    /**
     * @return toujours {@code false}. Aucun repli SELECT périodique, que Redis soit joignable ou non.
     */
    public static boolean sqlPollingEnabled(boolean redisConnected) {
        return false;
    }

    public static boolean relaysIntoGame(String channel) {
        return actionFor(channel) == Action.RELAY_CHAT;
    }

    /**
     * Garde de server-id sur l'écho Redis. Un message sans origine reste sur le comportement
     * historique (le site n'envoie pas de server-id). Un nœud n'applique pas son propre écho.
     */
    public static Action accept(String channel, String originServerId, String localServerId) {
        Action base = actionFor(channel);
        boolean own = originServerId != null && !originServerId.isBlank()
                && originServerId.equals(localServerId);
        if (base == Action.PUBLISH_ONLY) {
            if (!own && originServerId != null && !originServerId.isBlank()) {
                return Action.RELAY_CROSS_CHAT;
            }
            return Action.PUBLISH_ONLY;
        }
        if (base == Action.ADMIN && own) {
            return Action.IGNORE;
        }
        return base;
    }
}
