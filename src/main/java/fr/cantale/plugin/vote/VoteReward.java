package fr.cantale.plugin.vote;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Récompense de vote : Cadeau(x) du Roi (items) + libellé d'affichage.
 * Le champ {@link Tier} est conservé pour compatibilité BDD (valeur ignorée côté messages).
 */
public class VoteReward {

    public enum Tier {
        COMMUN("§7Commun"),
        RARE("§9Rare"),
        EPIQUE("§5Épique"),
        LEGENDAIRE("§6Légendaire");

        private final String displayName;
        Tier(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    private final Tier tier;
    private final double cantox;
    private final List<ItemStack> items;
    private final String summary;

    public VoteReward(Tier tier, double cantox, List<ItemStack> items, String summary) {
        this.tier = tier;
        this.cantox = cantox;
        this.items = items;
        this.summary = summary;
    }

    public Tier getTier() { return tier; }
    public double getCantox() { return cantox; }
    public List<ItemStack> getItems() { return items; }
    public String getSummary() { return summary; }
}
