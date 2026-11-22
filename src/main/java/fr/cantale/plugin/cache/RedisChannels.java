package fr.cantale.plugin.cache;

/**
 * Canaux Redis du pont Minecraft ↔ site. Noms stables : le site publie le même contrat JSON.
 */
public final class RedisChannels {

    public static final String CHAT_WEB_TO_MC = "cantale:chat:web2mc";
    public static final String CHAT_MC_TO_WEB = "cantale:chat:mc2web";
    public static final String ADMIN_ACTIONS = "cantale:admin:actions";

    public static final String[] SUBSCRIPTIONS = {
            CHAT_WEB_TO_MC,
            CHAT_MC_TO_WEB,
            ADMIN_ACTIONS
    };

    private RedisChannels() {
    }
}
