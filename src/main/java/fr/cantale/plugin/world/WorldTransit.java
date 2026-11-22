package fr.cantale.plugin.world;

/**
 * Téléportations : on n'entre en terres de guerre que si la destination a été choisie
 * (warp, home, tpa, spawn de faction) ou si le joueur y est déjà.
 * {@code /spawn} et {@code /rtp} ne déposent personne en warzone depuis un autre monde.
 */
public final class WorldTransit {

    private WorldTransit() {
    }

    public static boolean allows(String fromWorld, String toWorld, boolean explicitDestination) {
        if (toWorld == null || toWorld.isBlank()) {
            return false;
        }
        if (!WorldTopology.WARZONE.equals(toWorld)) {
            return true;
        }
        if (WorldTopology.WARZONE.equals(fromWorld)) {
            return true;
        }
        return explicitDestination;
    }

    /**
     * Monde du {@code /rtp}. Le sanctuaire envoie vers les terres sauvages.
     * La warzone n'est choisie que si le joueur y est déjà.
     */
    public static String rtpWorld(String currentWorld) {
        if (currentWorld == null || currentWorld.isBlank() || WorldTopology.HUB.equals(currentWorld)) {
            return WorldTopology.RESOURCES;
        }
        return currentWorld;
    }

    /** Spawn public : sanctuaire, sinon l'overworld historique. Jamais la warzone. */
    public static String spawnWorld(boolean hubLoaded, boolean legacyLoaded) {
        if (hubLoaded) {
            return WorldTopology.HUB;
        }
        if (legacyLoaded) {
            return WorldTopology.LEGACY;
        }
        return WorldTopology.HUB;
    }
}
