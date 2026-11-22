package fr.cantale.plugin.lang;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class PlayerLanguageRepository {

    private PlayerLanguageRepository() {
    }

    public static String read(Connection conn, UUID uuid) throws SQLException {
        if (conn == null || uuid == null) {
            return LanguageCodes.FR;
        }
        try (PreparedStatement ps = conn.prepareStatement("SELECT lang FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return LanguageCodes.orFrench(rs.getString(1));
                }
            }
        }
        return LanguageCodes.FR;
    }

    public static void write(Connection conn, UUID uuid, String lang) throws SQLException {
        if (conn == null || uuid == null) {
            return;
        }
        String canonical = LanguageCodes.canonical(lang);
        if (canonical == null) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement("UPDATE players SET lang = ? WHERE uuid = ?")) {
            ps.setString(1, canonical);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }
}
