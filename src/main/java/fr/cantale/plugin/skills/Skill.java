package fr.cantale.plugin.skills;

public enum Skill {
    ARCHITECTE("architecte", "Architecte"),
    NEGOCIANT("negociant", "Négociant"),
    ARTISAN("artisan", "Artisan"),
    CHEVALIER("chevalier", "Chevalier");

    private final String argument;
    private final String label;

    Skill(String argument, String label) {
        this.argument = argument;
        this.label = label;
    }

    public String argument() {
        return argument;
    }

    public String label() {
        return label;
    }

    public static Skill fromArgument(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toLowerCase(java.util.Locale.ROOT);
        for (Skill skill : values()) {
            if (skill.argument.equals(normalized) || skill.name().equalsIgnoreCase(normalized)) {
                return skill;
            }
        }
        return null;
    }
}
