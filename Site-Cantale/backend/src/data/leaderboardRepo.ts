/**
 * Repository LECTURE pour les leaderboards.
 */

import { getDb } from "../db/db";
import { publicRankingExcludeFactionSql, publicRankingExcludeSql } from "./publicRankingExclusions";

export type LeaderboardType = "kills" | "deaths" | "balance" | "kill_streak" | "votes" | "factions";

export interface LeaderboardEntry {
  rank: number;
  uuid: string;
  username: string;
  value: number;
  extra?: string;
}

const LIMIT_MAX = 100;

export async function getLeaderboard(type: LeaderboardType, limit = 10): Promise<LeaderboardEntry[]> {
  const db = getDb();
  const lim = Math.min(Math.max(limit, 1), LIMIT_MAX);
  const playersExclude = publicRankingExcludeSql("username", "uuid");
  const votesExclude = publicRankingExcludeSql("player_name", "player_uuid");

  let rows: Array<{ uuid?: string; username?: string; value: number; extra?: string }> = [];

  switch (type) {
    case "kills":
      rows = await db.query(
        `SELECT uuid, username, kills AS value FROM players
         WHERE kills > 0 AND ${playersExclude}
         ORDER BY kills DESC LIMIT ?`,
        [lim]
      );
      break;
    case "deaths":
      rows = await db.query(
        `SELECT uuid, username, deaths AS value FROM players
         WHERE deaths > 0 AND ${playersExclude}
         ORDER BY deaths DESC LIMIT ?`,
        [lim]
      );
      break;
    case "balance":
      rows = await db.query(
        `SELECT uuid, username, balance AS value FROM players
         WHERE ${playersExclude}
         ORDER BY balance DESC LIMIT ?`,
        [lim]
      );
      break;
    case "kill_streak":
      rows = await db.query(
        `SELECT uuid, username, kill_streak AS value FROM players
         WHERE kill_streak > 0 AND ${playersExclude}
         ORDER BY kill_streak DESC LIMIT ?`,
        [lim]
      );
      break;
    case "votes":
      rows = await db.query(
        `SELECT player_uuid AS uuid, player_name AS username, total_votes AS value, CAST(streak_days AS TEXT) AS extra
         FROM vote_stats
         WHERE ${votesExclude}
         ORDER BY total_votes DESC LIMIT ?`,
        [lim]
      );
      break;
    case "factions":
      // Factions /f secret incluses : visibles partout (comme /f list en jeu) —
      // seuls leurs claims sont masqués, jamais la faction elle-même.
      rows = await db.query(
        `SELECT f.tag AS uuid, f.name AS username, f.power AS value,
                CAST((SELECT COUNT(*) FROM faction_members fm WHERE fm.faction_id = f.id) AS TEXT) AS extra
         FROM factions f
         WHERE ${publicRankingExcludeFactionSql("f.name", "f.tag")}
         ORDER BY f.power DESC LIMIT ?`,
        [lim]
      );
      break;
  }

  return rows.map((r, i) => ({
    rank: i + 1,
    uuid: r.uuid ?? "",
    username: r.username ?? "Inconnu",
    value: r.value,
    extra: r.extra,
  }));
}
