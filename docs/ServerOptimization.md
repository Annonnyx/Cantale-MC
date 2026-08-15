# Optimisation serveur — Guide complet Cantale

Objectif : serveur le plus rapide possible, piloté au maximum **par le plugin Cantale**.
Ce document couvre : (1) ce que fait le plugin, (2) les configs serveur à appliquer,
(3) les flags JVM, (4) les outils complémentaires (Spark, Chunky), (5) la checklist ops.

---

## 1. Ce que le plugin Cantale gère (aucun plugin tiers requis)

### 1.1 Gouverneur de performance adaptatif (`PerformanceGovernor`)

Boucle de surveillance (toutes les 5 s par défaut) qui lit **TPS (1 min)** et
**MSPT moyen** (Paper) et fait basculer le serveur entre 3 modes, avec hystérésis
(temps minimum par mode + période saine continue avant de remonter — pas de flapping) :

| Mode     | Déclencheur (défauts)          | Actions |
|----------|--------------------------------|---------|
| NORMAL   | —                              | Distances de base (captées au boot), spawners et stacking nominaux |
| DEGRADED | TPS < 18 ou MSPT > 40          | simulation-distance −2 (min 4) · intervalle spawners ×1.5 · stacking 5 s |
| CRITICAL | TPS < 15 ou MSPT > 47.5        | simulation-distance −3 (min 3) · view-distance −1 si activé (min 6) · spawners ×2 · cleanup d'urgence · alerte staff MC + Discord |

Tout est configuré dans `config.yml` sous `optimization.governor.*` et borné
(les distances ne descendent jamais sous les minimums, et sont **restaurées** à la
remontée de mode et à l'arrêt du plugin).

Commandes staff :

```
/optimize governor                          → état (mode, seuils, distances de base)
/optimize mode <auto|normal|degraded|critical>  → forcer un mode (auto = retour gouverneur)
```

### 1.2 Nettoyage des entités (`ServerOptimizer`)

- **Une seule passe** par monde toutes les 60 s : flèches > 30 s, orbes XP (optionnel),
  cull `max-entities-per-chunk` (15) et `max-items-per-chunk` (20, hors grâce loot de mort).
- Cleanup d'urgence déclenché par le gouverneur en CRITICAL (cooldown interne 2 min) :
  items hors grâce, flèches > 10 s, toutes les orbes XP.
- `/optimize clearall [named]` : purge manuelle complète (joueurs/tames/NPCs préservés).

### 1.3 ClearLag (`ClearLagTask`)

Clear des items au sol toutes les 180 s avec annonces T-30/10/5/3/2/1 et grâce 5 min
(loot de mort protégé). `/optimize clearlag <sec>` pour ajuster (persisté).

### 1.4 Mob stacking (`MobStackManager`)

Animaux de ferme + mobs de spawners Cantale empilés (`Mouton ×12`) : une seule entité
simulée par stack. Passe de merge toutes les 10 s (5 s en mode DEGRADED+ via gouverneur).

### 1.5 Hoppers (`HopperLimitListener`)

Craft bloqué, achat `/shop` cher avec cap quotidien, pose limitée par pouvoir de faction
(`factions.hopper-limit`), uniquement en claim de sa fac. Complété côté serveur par
`ticks-per.hopper-check: 8` (voir §2).

### 1.6 Chunks (`ChunkLoader`)

Préchargement **async** autour des destinations de téléportation (rayon
`optimization.chunk-preload-radius`, défaut 2) et préchauffage du spawn au boot
(`optimization.spawn-prewarm-radius`, défaut 6). Cache borné anti-spam (10 000 entrées).

### 1.7 Diagnostic in-game

```
/optimize stats        → TPS 1m/5m/15m, MSPT, mode gouverneur, mémoire + GC,
                         chunks/entités/tiles par monde, distances actuelles
/optimize hotspots [n] → top N des chunks les plus chargés (entités + tile entities)
                         avec coordonnées — localise les fermes qui laggent
```

Log automatique toutes les 5 min : `[Santé] TPS … MSPT … entités … chunks … mode …`.

---

## 2. Configs serveur (templates prêts dans `server-config/`)

Les plus gros gains sont dans les fichiers du serveur. Extraits commentés à fusionner :

| Fichier | Gains principaux |
|---|---|
| `server.properties.extra` | `simulation-distance: 4` / `view-distance: 7` (nominal), compression 256 |
| `bukkit.yml.extra` | spawn-limits resserrés, ticks-per espacés |
| `spigot.yml.extra` | entity-activation-range, tracking-range, merge-radius, hoppers 8/8 |
| `paper-world-defaults.yml.extra` | chunk unloads 10 s, auto-save étalé (8/tick), per-player-mob-spawns, despawn-ranges 72/30, alt-item-despawn-rate (junk 15 s), optimize-explosions, anti chunk-ban |
| `paper-global.yml.extra` | packet-limiter, envoi de chunks |

Voir `server-config/README.md` pour l'ordre d'application et le retour arrière.
Options marquées **OPTIONNEL** (changent le gameplay) : ALTERNATE_CURRENT (redstone),
`armor-stands.tick: false`, `nerf-spawner-mobs: true`, anti-xray.

---

## 3. Flags JVM (Aikar / G1GC)

Java 21+ obligatoire (déjà le cas). Lancer Paper avec les flags Aikar :

```bash
java -Xms8G -Xmx8G \
  --add-modules=jdk.incubator.vector \
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 \
  -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 \
  -Dpaper.playerconnection.keepalive=90 \
  -jar paper.jar nogui
```

- `-Xms` = `-Xmx` (évite le resize du heap). Adapter `8G` à la RAM allouée.
- `-XX:+DisableExplicitGC` : rend `System.gc()` inoffensif (Cantale ne l'appelle plus).
- `-Dpaper.playerconnection.keepalive=90` : délai avant le kick « Timed out » (keepalive).
  **30 s par défaut** — un joueur dont la connexion gèle 30 s est expulsé. 60–90 s tolère
  les micro-coupures / connexions instables. **Impossible de désactiver totalement** (sinon
  les connexions mortes resteraient en ligne comme « fantômes »). Ne pas dépasser ~120 s.
- Générateur à jour : <https://flags.sh>.

Ne JAMAIS mettre moins de ~4 G sur un serveur factions avec farms (GC trop fréquents).

---

## 4. Outils complémentaires (optionnels)

| Outil | Rôle | Quand |
|---|---|---|
| **Spark** | Profiling CPU/mémoire, `spark tps`, `spark profiler` | Diagnostic uniquement — installable en permanence sans surcoût mesurable |
| **Chunky** | Pré-génération du monde dans la world border | Une fois à l'ouverture (et après agrandissement de border) : supprime les freezes de génération |

**À NE PAS installer** (doublons avec Cantale, souvent contre-productifs) : plugins
clear-lag, mob-stackers, « optimisers » génériques, limiters d'entités.

Forks serveur : Pufferfish/Purpur apportent des gains supplémentaires (optionnel,
décision hébergeur) — tout ce guide reste compatible.

---

## 5. Checklist de déploiement

1. [ ] Déployer le JAR Cantale (gouverneur + optimizer refondu + diagnostic).
2. [ ] Merger le bloc `optimization.governor` dans le `config.yml` live
       (absent = défauts Java raisonnables, mais la config explicite est recommandée).
3. [ ] Backup puis fusion des extraits `server-config/*.extra` (ordre du README).
4. [ ] Vérifier les flags JVM Aikar dans le script/panneau de démarrage.
5. [ ] Redémarrer. Vérifier au boot : `[Governor] Actif …` et `[ChunkLoader] Préchauffage spawn …`.
6. [ ] En jeu : `/optimize stats`, `/optimize governor`, `/optimize hotspots`.
7. [ ] 24–48 h plus tard : `/mspt`, Spark si un lag persiste → viser avec `/optimize hotspots`.
8. [ ] (Optionnel) Activer les options OPTIONNEL une par une en mesurant.

## 6. Signaux à surveiller

- **MSPT > 45 en continu** → hotspots d'entités (`/optimize hotspots`), trop de chunks
  chargés (vérifier machines chunk-loaders), ou hoppers en masse.
- **TPS 1m bas mais 15m bon** → pic transitoire (event, raid TNT) : le gouverneur gère.
- **Mémoire qui monte sans redescendre** → fuite : `spark heapdump` et ticket dev.
