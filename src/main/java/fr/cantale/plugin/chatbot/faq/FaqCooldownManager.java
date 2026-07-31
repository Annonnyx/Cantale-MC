package fr.cantale.plugin.chatbot.faq;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Cooldown par auteur et par entrée FAQ.
 */
public class FaqCooldownManager {

    private final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();
    private FaqConfig config;

    public FaqCooldownManager(FaqConfig config) {
        this.config = config;
    }

    public void updateConfig(FaqConfig config) {
        this.config = config;
    }

    /**
     * Vérifie si l'auteur est encore en cooldown pour une entrée donnée.
     *
     * @param authorId identifiant de l'auteur (UUID MC ou ID Discord)
     * @param entryId  identifiant de l'entrée FAQ
     * @param overrideSeconds durée spécifique (<= 0 pour utiliser la config globale)
     * @return true si le cooldown est actif
     */
    public boolean isOnCooldown(String authorId, String entryId, int overrideSeconds) {
        long cooldownMs = (overrideSeconds > 0 ? overrideSeconds : config.getCooldownSeconds()) * 1000L;
        if (cooldownMs <= 0) return false;

        Long last = cooldowns.get(key(authorId, entryId));
        if (last == null) return false;
        return System.currentTimeMillis() - last < cooldownMs;
    }

    public void setCooldown(String authorId, String entryId) {
        cooldowns.put(key(authorId, entryId), System.currentTimeMillis());
    }

    public void clearCooldown(String authorId, String entryId) {
        cooldowns.remove(key(authorId, entryId));
    }

    public void clearAll() {
        cooldowns.clear();
    }

    private String key(String authorId, String entryId) {
        return authorId + ":" + entryId;
    }
}
