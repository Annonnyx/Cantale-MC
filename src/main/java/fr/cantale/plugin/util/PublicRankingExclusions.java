package fr.cantale.plugin.util;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Comptes exclus des classements publics (jeu, Discord, sync) et des tops
 * joueurs. Liste centrale — ne pas hardcoder le pseudo ailleurs.
 *
 * Aligné sur Site-Cantale/web/src/server/public-ranking-exclusions.ts
 */
public final class PublicRankingExclusions {

    /** Pseudos exclus (comparaison SQL via LOWER). */
    public static final List<String> USERNAMES = List.of("Anox26");

    /** UUID offline optionnels — plus durables si le pseudo change. */
    public static final List<String> UUIDS = List.of();

    private PublicRankingExclusions() {
    }

    /**
     * Clause SQL complète sans WHERE/AND, ex.
     * {@code LOWER(username) NOT IN ('anox26')}.
     *
     * @param usernameColumn colonne pseudo ({@code username}, {@code p.username}, {@code player_name})
     * @param uuidColumn     colonne UUID, ou {@code null} pour ignorer
     */
    public static String sqlExclude(String usernameColumn, String uuidColumn) {
        StringBuilder sb = new StringBuilder();

        if (!USERNAMES.isEmpty()) {
            String list = USERNAMES.stream()
                    .map(name -> "'" + escapeSql(name.toLowerCase(Locale.ROOT)) + "'")
                    .collect(Collectors.joining(", "));
            sb.append("LOWER(").append(usernameColumn).append(") NOT IN (").append(list).append(")");
        }

        if (uuidColumn != null && !UUIDS.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" AND ");
            }
            String list = UUIDS.stream()
                    .map(uuid -> "'" + escapeSql(uuid) + "'")
                    .collect(Collectors.joining(", "));
            sb.append(uuidColumn).append(" NOT IN (").append(list).append(")");
        }

        return sb.length() > 0 ? sb.toString() : "1=1";
    }

    /** Préfixe {@code WHERE …} pour une requête sans clause WHERE existante. */
    public static String whereExclude(String usernameColumn, String uuidColumn) {
        return " WHERE " + sqlExclude(usernameColumn, uuidColumn);
    }

    /** Préfixe {@code AND …} pour une requête qui a déjà un WHERE. */
    public static String andExclude(String usernameColumn, String uuidColumn) {
        String clause = sqlExclude(usernameColumn, uuidColumn);
        return "1=1".equals(clause) ? "" : " AND " + clause;
    }

    private static String escapeSql(String value) {
        return value.replace("'", "''");
    }
}
