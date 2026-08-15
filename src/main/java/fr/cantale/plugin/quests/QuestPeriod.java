package fr.cantale.plugin.quests;

public enum QuestPeriod {
    DAILY("Journalière", "jour"),
    WEEKLY("Hebdomadaire", "semaine"),
    SEASONAL("Saisonnière", "saison");

    private final String french;
    private final String shortLabel;

    QuestPeriod(String french, String shortLabel) {
        this.french = french;
        this.shortLabel = shortLabel;
    }

    public String french() {
        return french;
    }

    public String shortLabel() {
        return shortLabel;
    }
}
