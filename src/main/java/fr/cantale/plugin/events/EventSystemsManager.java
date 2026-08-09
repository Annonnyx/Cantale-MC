package fr.cantale.plugin.events;

import fr.cantale.plugin.CantalePlugin;

/**
 * Point d'entrée unique pour charger events.yml et démarrer les sous-systèmes.
 */
public class EventSystemsManager {

    private final CantalePlugin plugin;
    private final EventsConfig config;
    private final ChatReactionManager chatReactionManager;
    private final FactionEventManager factionEventManager;
    private final WeeklyKeyDropManager weeklyKeyDropManager;

    public EventSystemsManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.config = new EventsConfig(plugin);
        this.config.load();
        this.chatReactionManager = new ChatReactionManager(plugin, config);
        this.factionEventManager = new FactionEventManager(plugin, config);
        this.weeklyKeyDropManager = new WeeklyKeyDropManager(plugin, config);
    }

    public void start() {
        chatReactionManager.start();
        factionEventManager.start();
        weeklyKeyDropManager.start();
    }

    public void stop() {
        chatReactionManager.stop();
        factionEventManager.stop();
        weeklyKeyDropManager.stop();
    }

    public void reload() {
        stop();
        config.load();
        start();
        if (plugin.getRotatingBossBarManager() != null) {
            plugin.getRotatingBossBarManager().reload();
        }
        plugin.getLogger().info("[Events] Configuration rechargée.");
    }

    public EventsConfig getConfig() {
        return config;
    }

    public ChatReactionManager getChatReactionManager() {
        return chatReactionManager;
    }

    public FactionEventManager getFactionEventManager() {
        return factionEventManager;
    }

    public WeeklyKeyDropManager getWeeklyKeyDropManager() {
        return weeklyKeyDropManager;
    }
}
