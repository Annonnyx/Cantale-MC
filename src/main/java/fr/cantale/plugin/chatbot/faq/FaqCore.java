package fr.cantale.plugin.chatbot.faq;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/**
 * Cœur du bot FAQ. Exposé aux adaptateurs Minecraft et Discord.
 */
public class FaqCore {

    private final JavaPlugin plugin;
    private final FaqRepository repository;
    private final FaqCooldownManager cooldownManager;
    private final FaqLogger logger;

    public FaqCore(JavaPlugin plugin) {
        this.plugin = plugin;
        File faqFile = new File(plugin.getDataFolder(), "faq.yml");
        this.repository = new FaqRepository(faqFile);
        this.cooldownManager = new FaqCooldownManager(repository.loadAndGetConfig());
        this.logger = new FaqLogger(plugin.getDataFolder(), repository.getConfig().getUnmatchedLog());
    }

    /**
     * Recharge la FAQ depuis faq.yml.
     */
    public void reload() {
        load();
    }

    private void load() {
        repository.load();
        cooldownManager.updateConfig(repository.getConfig());
        cooldownManager.clearAll();
        logger.setLogPath(repository.getConfig().getUnmatchedLog());
    }

    /**
     * Tente de trouver une réponse à une question.
     *
     * @param message  message original
     * @param authorId identifiant unique de l'auteur (UUID MC ou ID Discord)
     * @param source   "minecraft" ou "discord"
     * @return l'entrée correspondante, ou null si aucune ou cooldown actif
     */
    public FaqEntry match(String message, String authorId, String source) {
        String normalized = FaqNormalizer.stripPolitenessWords(FaqNormalizer.normalize(message));

        FaqConfig config = repository.getConfig();
        if (config.isRequireQuestionMarker() && !FaqQuestionDetector.isQuestion(normalized)) {
            return null;
        }
        if (config.isRequireExplicitQuestion() && !FaqQuestionDetector.isExplicitQuestion(normalized)) {
            return null;
        }

        FaqMatcher.MatchResult result = FaqMatcher.findBestMatch(
            normalized, repository.getEntries(), config.getConfidenceThreshold()
        );

        if (result == null) {
            logger.logUnmatched(source, message, normalized);
            return null;
        }

        FaqEntry entry = result.entry();
        int cooldownSeconds = entry.getCooldownSeconds() > 0 ? entry.getCooldownSeconds() : config.getCooldownSeconds();
        if (cooldownManager.isOnCooldown(authorId, entry.getId(), cooldownSeconds)) {
            return null;
        }
        cooldownManager.setCooldown(authorId, entry.getId());
        return entry;
    }

    /**
     * Récupère toutes les entrées (pour admin commands).
     */
    public List<FaqEntry> getEntries() {
        return repository.getEntries();
    }

    public FaqConfig getConfig() {
        return repository.getConfig();
    }
}
