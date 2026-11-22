package fr.cantale.plugin.world;

/**
 * Noms des quatre mondes de la phase 3.
 * <p>
 * Le dépôt ne contient pas de dossier de datapacks (les mondes vivent sur le serveur).
 * {@code world_factions} et {@code world_warzone} sont créés en relief amplifié
 * (falaises et vallées du générateur vanilla). Aucun datapack vide n'est ajouté.
 */
public final class WorldTopology {

    public static final String HUB = "world_hub";
    public static final String FACTIONS = "world_factions";
    public static final String RESOURCES = "world_resources";
    public static final String WARZONE = "world_warzone";

    /** Overworld historique : les bases déjà posées y restent claimables. */
    public static final String LEGACY = "world";

    private WorldTopology() {
    }

    public static boolean isManaged(String worldName) {
        return HUB.equals(worldName)
                || FACTIONS.equals(worldName)
                || RESOURCES.equals(worldName)
                || WARZONE.equals(worldName);
    }
}
