package fr.cantale.plugin.web;

import fr.cantale.plugin.CantalePlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * DAO côté plugin pour la table web_link_codes (partagée avec le backend Node).
 *
 * @deprecated La commande {@code /web link} est obsolète : la connexion site
 * se fait via Discord OAuth sur https://www.cantale.world/connexion.
 * Conservé (pas de suppression de fichier) ; plus appelé par {@code WebCommand}.
 *
 * Historique : le backend générait les codes (INSERT) ; ce DAO les consommait
 * quand un joueur tapait /web link &lt;code&gt;.
 */
public class WebLinkDAO {

    private final CantalePlugin plugin;

    public WebLinkDAO(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Tente de consommer un code. Retourne :
     *   - null si le code n'existe pas ou est expiré
     *   - "ALREADY_USED" si déjà consommé
     *   - "OK" en cas de succès
     */
    public String consumeCode(String code, java.util.UUID playerUuid, String playerName) {
        long now = System.currentTimeMillis() / 1000L;

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            // Récupérer le code
            String sel = "SELECT id, player_uuid, expires_at, consumed_at FROM web_link_codes WHERE code = ?";
            int id;
            long expiresAt;
            String existingUuid;
            Long consumedAt;

            try (PreparedStatement st = conn.prepareStatement(sel)) {
                st.setString(1, code);
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) return null;
                    id = rs.getInt("id");
                    existingUuid = rs.getString("player_uuid");
                    expiresAt = rs.getLong("expires_at");
                    long ca = rs.getLong("consumed_at");
                    consumedAt = rs.wasNull() ? null : ca;
                }
            }

            if (consumedAt != null || existingUuid != null) return "ALREADY_USED";
            if (expiresAt < now) return null;

            // Update : marque le code comme consommé pour ce joueur
            String upd = "UPDATE web_link_codes SET player_uuid = ?, player_name = ?, consumed_at = ? WHERE id = ?";
            try (PreparedStatement st = conn.prepareStatement(upd)) {
                st.setString(1, playerUuid.toString());
                st.setString(2, playerName);
                st.setLong(3, now);
                st.setInt(4, id);
                int rows = st.executeUpdate();
                return rows > 0 ? "OK" : null;
            }
        } catch (SQLException e) {
            // Table peut-être inexistante si le backend n'a jamais tourné
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("no such table") || msg.contains("doesn't exist")) {
                plugin.getLogger().warning("[Web] Table web_link_codes inexistante. Démarrez le backend Node d'abord.");
                return null;
            }
            plugin.getLogger().severe("[Web] consumeCode error: " + e.getMessage());
            return null;
        }
    }
}
