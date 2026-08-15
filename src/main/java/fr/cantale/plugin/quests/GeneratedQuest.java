package fr.cantale.plugin.quests;

/**
 * Quête tirée pour une période / un pool.
 */
public record GeneratedQuest(
        String id,
        String templateId,
        String title,
        QuestObjectiveType objective,
        String targetKey,
        int amount,
        QuestPeriod period,
        QuestScope scope,
        int bonusCantox,
        int bonusLives
) {
    public String serialize() {
        return String.join("\t",
                id, templateId, title, objective.name(), targetKey,
                Integer.toString(amount), period.name(), scope.name(),
                Integer.toString(bonusCantox), Integer.toString(bonusLives));
    }

    public static GeneratedQuest deserialize(String line) {
        String[] p = line.split("\t", -1);
        if (p.length < 10) return null;
        return new GeneratedQuest(
                p[0], p[1], p[2],
                QuestObjectiveType.valueOf(p[3]),
                p[4],
                Integer.parseInt(p[5]),
                QuestPeriod.valueOf(p[6]),
                QuestScope.valueOf(p[7]),
                Integer.parseInt(p[8]),
                Integer.parseInt(p[9])
        );
    }
}
