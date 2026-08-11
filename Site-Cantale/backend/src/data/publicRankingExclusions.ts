/**
 * Comptes exclus des classements publics (legacy API Express).
 * Aligner sur Site-Cantale/web/src/server/public-ranking-exclusions.ts
 */
export const PUBLIC_RANKING_EXCLUDED_USERNAMES = ["Anox26"] as const;
export const PUBLIC_RANKING_EXCLUDED_UUIDS = [] as const;

function sqlStringLiterals(values: readonly string[]): string {
  return values
    .map((value) => `'${value.replace(/\\/g, "\\\\").replace(/'/g, "''")}'`)
    .join(", ");
}

export function publicRankingExcludeSql(
  usernameColumn: string,
  uuidColumn: string | null = "uuid",
): string {
  const parts: string[] = [];

  if (PUBLIC_RANKING_EXCLUDED_USERNAMES.length > 0) {
    const lowered = PUBLIC_RANKING_EXCLUDED_USERNAMES.map((name) => name.toLowerCase());
    parts.push(`LOWER(${usernameColumn}) NOT IN (${sqlStringLiterals(lowered)})`);
  }

  if (uuidColumn && PUBLIC_RANKING_EXCLUDED_UUIDS.length > 0) {
    parts.push(`${uuidColumn} NOT IN (${sqlStringLiterals(PUBLIC_RANKING_EXCLUDED_UUIDS)})`);
  }

  return parts.length > 0 ? parts.join(" AND ") : "1=1";
}
