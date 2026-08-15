/**
 * Repository LECTURE des factions, claims, banques.
 */

import { getDb } from "../db/db";
import { publicRankingExcludeFactionSql } from "./publicRankingExclusions";

export interface FactionSummary {
  id: number;
  name: string;
  tag: string;
  description: string;
  leader_uuid: string;
  balance: number;
  power: number;
  type: string;
  created_at: number;
  memberCount: number;
  claimCount: number;
}

export interface FactionMember {
  player_uuid: string;
  rank: string;
  joined_at: number;
}

/**
 * Liste publique des factions — TOUTES, y compris celles en /f secret
 * (aligné sur le jeu : /f list les montre). Seule leur position est
 * protégée : getFactionClaims ne renvoie rien pour une faction secrète.
 */
export async function listFactions(): Promise<FactionSummary[]> {
  const db = getDb();
  return await db.query<FactionSummary>(`
    SELECT f.id, f.name, f.tag, f.description, f.leader_uuid, f.balance, f.power, f.type, f.created_at,
           (SELECT COUNT(*) FROM faction_members fm WHERE fm.faction_id = f.id) AS memberCount,
           (SELECT COUNT(*) FROM claims c            WHERE c.faction_id = f.id) AS claimCount
    FROM factions f
    WHERE ${publicRankingExcludeFactionSql("f.name", "f.tag")}
    ORDER BY f.power DESC, memberCount DESC
  `);
}

export async function getFaction(id: number): Promise<FactionSummary | null> {
  const db = getDb();
  return await db.queryOne<FactionSummary>(`
    SELECT f.id, f.name, f.tag, f.description, f.leader_uuid, f.balance, f.power, f.type, f.created_at,
           (SELECT COUNT(*) FROM faction_members fm WHERE fm.faction_id = f.id) AS memberCount,
           (SELECT COUNT(*) FROM claims c            WHERE c.faction_id = f.id) AS claimCount
    FROM factions f
    WHERE f.id = ?
  `, [id]);
}

export async function getFactionByTag(tag: string): Promise<FactionSummary | null> {
  const db = getDb();
  return await db.queryOne<FactionSummary>(`
    SELECT f.id, f.name, f.tag, f.description, f.leader_uuid, f.balance, f.power, f.type, f.created_at,
           (SELECT COUNT(*) FROM faction_members fm WHERE fm.faction_id = f.id) AS memberCount,
           (SELECT COUNT(*) FROM claims c            WHERE c.faction_id = f.id) AS claimCount
    FROM factions f
    WHERE LOWER(f.tag) = LOWER(?)
  `, [tag]);
}

export async function getFactionMembers(factionId: number): Promise<Array<FactionMember & { username: string | null }>> {
  const db = getDb();
  return await db.query(`
    SELECT fm.player_uuid, fm.rank, fm.joined_at, p.username
    FROM faction_members fm
    LEFT JOIN players p ON p.uuid = fm.player_uuid
    WHERE fm.faction_id = ?
    ORDER BY
      CASE fm.rank
        WHEN 'LEADER'  THEN 0
        WHEN 'OFFICER' THEN 1
        WHEN 'MEMBER'  THEN 2
        WHEN 'RECRUIT' THEN 3
        ELSE 4
      END,
      fm.joined_at ASC
  `, [factionId]);
}

/**
 * Claims d'une faction. Une faction en /f secret ne doit JAMAIS exposer sa
 * position : filtre au niveau SQL (secret_until, unix secondes — miroir de
 * Faction.isSecret() côté plugin), même comportement que la carte du site
 * Next.js (web/src/server/repo/map.ts).
 */
export async function getFactionClaims(factionId: number): Promise<Array<{ world: string; chunk_x: number; chunk_z: number; pasdic: number }>> {
  const db = getDb();
  return await db.query(`
    SELECT c.world, c.chunk_x, c.chunk_z, c.pasdic
    FROM claims c
    INNER JOIN factions f ON f.id = c.faction_id
    WHERE c.faction_id = ?
      AND COALESCE(f.secret_until, 0) < ?
  `, [factionId, Math.floor(Date.now() / 1000)]);
}
