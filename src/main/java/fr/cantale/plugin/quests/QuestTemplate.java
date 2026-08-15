package fr.cantale.plugin.quests;

public record QuestTemplate(
        String id,
        String title,
        QuestObjectiveType objective,
        String targetKey,
        int dailyAmount
) {
}
