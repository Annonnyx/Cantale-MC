package fr.cantale.plugin.network;

/**
 * Décision pure : le forwarding moderne est exigé seulement quand le secret d'environnement est présent.
 * Paper 1.21.11 n'expose pas {@code PaperVelocity} ni {@code setProxyMessaging}.
 * L'état réel est {@code ServerConfiguration#isProxyEnabled()} (Velocity ou BungeeCord dans paper-global.yml).
 */
public final class VelocityForwarding {

    public enum Decision {
        ALLOW,
        REJECT
    }

    private VelocityForwarding() {
    }

    public static Decision decide(boolean velocityEnabled, boolean paperProxyEnabled) {
        if (!velocityEnabled) {
            return Decision.ALLOW;
        }
        if (!paperProxyEnabled) {
            return Decision.REJECT;
        }
        return Decision.ALLOW;
    }
}
