package fr.cantale.plugin.crate;

/**
 * Mode d'utilisation lore d'une caisse Cantale.
 * Chaque valeur a un handler dédié — Mythic (eau) ≠ Epic (autel).
 */
public enum CrateInteraction {
    /** VOTE — dépôt dans une boîte aux lettres (tonneau / métier à tisser). Sans animation inventaire. */
    MAILBOX,
    /** RARE — clic-droit → UI coffre + animation. */
    RIGHT_CLICK_ANIMATED,
    /** MYTHIC — jeter la pièce dans l'eau / fontaine. */
    THROW_WATER,
    /** EPIC — consacrer la médaille sur une balise ou table d'enchantement (≠ eau). */
    RITUAL_ALTAR,
    /** LEGENDARY — clic-droit → ticket Discord. */
    RIGHT_CLICK_TICKET
}
