package fr.cantale.plugin.anticheat.packet;

/**
 * Placeholder pour les checks de packets réseau.
 *
 * Les vérifications de packets (Blink, BadPackets, TimerPackets) nécessitent
 * ProtocolLib pour intercepter les packets bruts.
 *
 * Structure prête pour une future intégration :
 * - BlinkCheck : détection de blink (freeze + teleport)
 * - BadPacketsCheck : packets avec des valeurs impossibles
 * - TimerPacketsCheck : fréquence de packets anormale
 *
 * Pour activer : ajouter ProtocolLib au pom.xml, implémenter les PacketListeners,
 * puis activer "anticheat.packets.enabled: true" dans config.yml.
 */
public class PacketCheckPlaceholder {

    // Intentionnellement vide — placeholder pour future intégration ProtocolLib

    /**
     * Vérifie si ProtocolLib est disponible sur le serveur.
     *
     * @return true si ProtocolLib est chargé
     */
    public static boolean isProtocolLibAvailable() {
        try {
            Class.forName("com.comphenix.protocol.ProtocolLibrary");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
