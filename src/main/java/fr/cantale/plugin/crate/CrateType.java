package fr.cantale.plugin.crate;

import java.util.List;

/**
 * Types de caisses Cantale et leur mode d'interaction lore.
 */
public enum CrateType {
    VOTE(
            "Cadeau du Roi",
            "§7Cadeau du Roi",
            "§7Un présent royal envoyé par la poste...",
            CrateInteraction.MAILBOX,
            List.of(
                    "§8Courrier royal",
                    "§7Dépose-le dans une §fboîte aux lettres",
                    "§7(§eTonneau §7ou §eMétier à tisser§7)",
                    "§eClic-droit sur la boîte en le tenant"
            ),
            "§7Dépose ce courrier dans une §fboîte aux lettres §7(§eTonneau §7ou §eMétier à tisser§7)."
    ),
    RARE(
            "Trésor Public",
            "§9Trésor Public",
            "§9Une clef mystérieuse ouvrant un coffre ancien.",
            CrateInteraction.RIGHT_CLICK_ANIMATED,
            List.of(
                    "§8Clef du Trésor Public",
                    "§7Ouvre le §fcoffre du Trésor Public",
                    "§7au §espawn§7.",
                    "§eClic-droit sur le coffre en la tenant"
            ),
            "§7Ouvre le coffre du §9Trésor Public §7au spawn."
    ),
    EPIC(
            "Médaille Épique",
            "§5Médaille du Tournoi",
            "§5Trophée glorieux à consacrer sur l'autel.",
            CrateInteraction.RITUAL_ALTAR,
            List.of(
                    "§8Médaille du Tournoi",
                    "§7Consacre-la sur une §dBalise",
                    "§7ou une §eTable d'enchantement§7.",
                    "§eClic-droit sur l'autel en la tenant"
            ),
            "§7Consacre cette §5Médaille du Tournoi §7sur une §dBalise §7ou une §eTable d'enchantement§7."
    ),
    MYTHIC(
            "Pièce Mythique",
            "§dPièce Mythique",
            "§dUne pièce légendaire à offrir à la fontaine.",
            CrateInteraction.THROW_WATER,
            List.of(
                    "§8Offrande mythique",
                    "§7§eJette-la §7dans la §efontaine",
                    "§7du §espawn §7(eau / chaudron)",
                    "§epour réclamer ta récompense."
            ),
            "§7§eJette §7cette §dPièce Mythique §7dans la §efontaine au spawn§7."
    ),
    LEGENDARY(
            "Ticket Légendaire",
            "§6Ticket Légendaire",
            "§6Contacte le staff sur Discord pour recevoir l'item de ton choix.",
            CrateInteraction.RIGHT_CLICK_TICKET,
            List.of(
                    "§8Audience royale",
                    "§7Clic-droit pour ouvrir ton",
                    "§6ticket Discord §7automatiquement."
            ),
            "§7Clic-droit pour ouvrir ton ticket Discord."
    );

    private final String displayName;
    private final String itemName;
    private final String defaultLore;
    private final CrateInteraction interaction;
    private final List<String> usageLore;
    private final String usageHint;

    CrateType(String displayName, String itemName, String defaultLore,
              CrateInteraction interaction, List<String> usageLore, String usageHint) {
        this.displayName = displayName;
        this.itemName = itemName;
        this.defaultLore = defaultLore;
        this.interaction = interaction;
        this.usageLore = usageLore;
        this.usageHint = usageHint;
    }

    public String getDisplayName() { return displayName; }
    public String getItemName() { return itemName; }
    public String getDefaultLore() { return defaultLore; }
    public CrateInteraction getInteraction() { return interaction; }
    public List<String> getUsageLore() { return usageLore; }
    public String getUsageHint() { return usageHint; }
}
