# SiteIntegration — contrat de données site ↔ plugin

Comment le site web (cantale.world) lit les données du plugin, et les règles
de visibilité à respecter — en particulier la sémantique du mode `/f secret`.

## Pipeline

```
Plugin Paper (Java) ──écrit──► MySQL partagé (PixelHorizons)
                                     │  tables : factions, faction_members,
                                     │  claims, players, warps, vote_stats…
                                     ▼
        Site Next.js (Site-Cantale/web, Vercel — cantale.world)
        lecture SQL directe : web/src/server/db.ts (mysql2, lecture seule,
        cache 30 s par requête via web/src/server/cache.ts)

        Backend Express legacy (Site-Cantale/backend — ancien site statique,
        pm2 cantale-api) : même base, lecture seule + tables propres web_*.
```

Il n'y a **pas d'export périodique ni d'endpoint JSON côté plugin** : le site
lit les tables live. Toute donnée écrite par le plugin est donc visible par le
site au prochain cycle de cache (≤ 30 s), sans reboot ni redeploy du plugin.

## Sémantique `/f secret` (règle de visibilité)

- Colonne : `factions.secret_until` (BIGINT, unix **secondes**, NULL/0 = jamais
  secret). Miroir exact : `Faction.isSecret()` =
  `System.currentTimeMillis() / 1000 < secretUntil`
  (`src/main/java/fr/cantale/plugin/factions/Faction.java`).
- Écriture : immédiate au `/f secret`
  (`SecretTaxManager.toggleSecret` → `FactionDAO.updateFaction`).

Comportement attendu (aligné sur le jeu : `/f list` et `/f info` montrent
toutes les factions, seuls les claims sont cachés aux non-membres) :

| Donnée d'une faction secrète              | Visible sur le site ? |
| ----------------------------------------- | --------------------- |
| Nom, tag, description, stats (power…)     | ✅ oui                |
| Page publique `/factions/<slug>`          | ✅ oui                |
| Membres (roster, profils joueurs)         | ✅ oui                |
| Classements / annuaire                    | ✅ oui                |
| **Claims sur la carte web** (`/carte`, `/api/map/claims`) | ❌ **non** |
| f spawn / warps de faction                | n/a — jamais affichés par le site |

Le site n'a pas de notion de membre → les claims d'une faction secrète sont
masqués **pour tous**, sans exception.

## Requêtes de référence (à reproduire par tout consommateur SQL)

Claims de la carte — exclusion en SQL, jamais côté client :

```sql
SELECT c.chunk_x, c.chunk_z, c.world, c.pasdic, f.id AS faction_id, f.name, f.tag
FROM claims c
INNER JOIN factions f ON f.id = c.faction_id
  AND COALESCE(f.secret_until, 0) <= UNIX_TIMESTAMP();
```

Listings / pages faction — **aucun** filtre sur `secret_until` (une faction
secrète est une faction normale dont on masque uniquement le territoire).

Implémentations : `Site-Cantale/web/src/server/repo/map.ts` (filtre claims,
constante `NOT_SECRET`) et `Site-Cantale/web/src/server/repo/factions.ts`
(aucun filtre secret — en-tête du fichier). Côté legacy :
`Site-Cantale/backend/src/data/factionRepo.ts` (`getFactionClaims` filtre,
`listFactions` non).

## Historique

- **Août 2026** : correction — le site excluait auparavant la faction secrète
  ENTIÈRE (annuaire, page, roster, profils) au lieu de ne masquer que ses
  claims. Le backend legacy exposait en prime les coordonnées de claims d'une
  faction secrète via `GET /api/factions/:id` (fuite corrigée).
