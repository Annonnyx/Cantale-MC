-- ╔════════════════════════════════════════════════════════════╗
-- ║         Schéma Supabase — Cantale Site Web                 ║
-- ╚════════════════════════════════════════════════════════════╝
-- Ce script crée les tables pour que le site web Vercel
-- puisse afficher les données du serveur Minecraft en temps réel.
-- Les données sont synchronisées par le bot Discord/plugin.

-- ─── 1. Table des joueurs ───
CREATE TABLE IF NOT EXISTS players (
    uuid            TEXT PRIMARY KEY,
    username        TEXT NOT NULL,
    lives           INTEGER DEFAULT 3,
    balance         INTEGER DEFAULT 0,
    deaths          INTEGER DEFAULT 0,
    kills           INTEGER DEFAULT 0,
    kill_streak     INTEGER DEFAULT 0,
    last_death      BIGINT DEFAULT 0,
    created_at      BIGINT DEFAULT 0,
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Index recherche par nom
CREATE INDEX IF NOT EXISTS idx_players_username ON players(username);

-- ─── 2. Table des factions ───
CREATE TABLE IF NOT EXISTS factions (
    id              INTEGER PRIMARY KEY,
    name            TEXT NOT NULL,
    tag             TEXT NOT NULL,
    description     TEXT,
    leader_uuid     TEXT,
    balance         INTEGER DEFAULT 0,
    power           INTEGER DEFAULT 0,
    type            TEXT DEFAULT 'faction',
    created_at      BIGINT DEFAULT 0,
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_factions_power ON factions(power DESC);

-- ─── 3. Table membres de factions ───
CREATE TABLE IF NOT EXISTS faction_members (
    faction_id      INTEGER REFERENCES factions(id) ON DELETE CASCADE,
    player_uuid     TEXT REFERENCES players(uuid) ON DELETE CASCADE,
    rank            TEXT DEFAULT 'RECRUIT',
    joined_at       BIGINT DEFAULT 0,
    PRIMARY KEY (faction_id, player_uuid)
);

-- ─── 4. Table leaderboards ───
-- Catégories: kills, balance, deaths, playtime
CREATE TABLE IF NOT EXISTS leaderboards (
    id              SERIAL PRIMARY KEY,
    category        TEXT NOT NULL,
    rank            INTEGER NOT NULL,
    uuid            TEXT NOT NULL,
    username        TEXT NOT NULL,
    kills           INTEGER,
    balance         INTEGER,
    deaths          INTEGER,
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_leaderboards_category ON leaderboards(category, rank);

-- ─── 5. Table statut serveur ───
CREATE TABLE IF NOT EXISTS server_status (
    server_name     TEXT PRIMARY KEY,
    online_players  INTEGER DEFAULT 0,
    max_players     INTEGER DEFAULT 0,
    tps             NUMERIC(4,2) DEFAULT 20.00,
    status          TEXT DEFAULT 'offline',
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ─── 6. Table web_sessions (auth site) ───
-- Le backend Vercel utilise cette table pour les sessions
CREATE TABLE IF NOT EXISTS web_sessions (
    id              SERIAL PRIMARY KEY,
    token           TEXT NOT NULL UNIQUE,
    player_uuid     TEXT NOT NULL,
    player_name     TEXT NOT NULL,
    created_at      BIGINT NOT NULL,
    expires_at      BIGINT NOT NULL,
    last_seen_at    BIGINT NOT NULL,
    ip              TEXT,
    user_agent      TEXT
);

CREATE INDEX IF NOT EXISTS idx_web_sessions_token ON web_sessions(token);
CREATE INDEX IF NOT EXISTS idx_web_sessions_uuid  ON web_sessions(player_uuid);

-- ─── 7. Table codes liaison in-game ───
CREATE TABLE IF NOT EXISTS web_link_codes (
    id              SERIAL PRIMARY KEY,
    code            TEXT NOT NULL UNIQUE,
    player_uuid     TEXT,
    player_name     TEXT,
    created_at      BIGINT NOT NULL,
    expires_at      BIGINT NOT NULL,
    consumed_at     BIGINT
);

CREATE INDEX IF NOT EXISTS idx_link_codes_code ON web_link_codes(code);

-- ─── 8. Table panier boutique ───
CREATE TABLE IF NOT EXISTS web_cart_items (
    id              SERIAL PRIMARY KEY,
    player_uuid     TEXT NOT NULL,
    item_id         TEXT NOT NULL,
    quantity        INTEGER NOT NULL DEFAULT 1,
    added_at        BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cart_player ON web_cart_items(player_uuid);

-- ─── Politique RLS (Row Level Security) ───
-- Active RLS sur toutes les tables
ALTER TABLE players ENABLE ROW LEVEL SECURITY;
ALTER TABLE factions ENABLE ROW LEVEL SECURITY;
ALTER TABLE faction_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE leaderboards ENABLE ROW LEVEL SECURITY;
ALTER TABLE server_status ENABLE ROW LEVEL SECURITY;

-- Politique: tout le monde peut lire
CREATE POLICY "read_players" ON players FOR SELECT USING (true);
CREATE POLICY "read_factions" ON factions FOR SELECT USING (true);
CREATE POLICY "read_faction_members" ON faction_members FOR SELECT USING (true);
CREATE POLICY "read_leaderboards" ON leaderboards FOR SELECT USING (true);
CREATE POLICY "read_server_status" ON server_status FOR SELECT USING (true);

-- Politique: seul le bot (clé service) peut écrire
-- À configurer via le Dashboard Supabase → Auth → Policies

-- ════════════════════════════════════════════════════════════
-- Instructions d'installation
-- ════════════════════════════════════════════════════════════
-- 1. Crée un projet sur https://supabase.com
-- 2. Va dans SQL Editor → New query
-- 3. Copie-colle ce script entier
-- 4. Exécute
-- 5. Va dans Settings → API pour récupérer:
--    - URL (ex: https://xxxxxx.supabase.co)
--    - anon public key (pour le site web)
--    - service_role key (pour le bot/plugin — NE PAS PARTAGER)
-- 6. Mets ces valeurs dans config.yml du plugin (section discord.supabase)
-- 7. Mets l'URL + anon key dans le site web (config.js)
