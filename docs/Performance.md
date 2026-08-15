# Architecture performance — Cantale

> Guide ops complet (configs serveur, flags JVM, Spark/Chunky) : [ServerOptimization.md](ServerOptimization.md).
> Ce document décrit le code.

## Vue d'ensemble

```
PerformanceGovernor (toutes les 5 s, sync)
  ├─ lit TPS 1 min + MSPT moyen (via ServerOptimizer)
  ├─ mode NORMAL / DEGRADED / CRITICAL (hystérésis : min-mode-seconds + recovery-seconds)
  ├─ World#setSimulationDistance / #setViewDistance (borné, restauré à la sortie et au stop)
  ├─ SpawnerManager.setRateMultiplier(1.0 / 1.5 / 2.0)  → intervalle de spawn
  ├─ MobStackManager.setIntervalOverrideSeconds(5)      → merges plus fréquents
  ├─ CRITICAL → ServerOptimizer.emergencyCleanup() (cooldown 2 min) + alerte staff/Discord
  └─ log [Santé] toutes les 5 min (TPS×3, MSPT, entités, chunks, mode)

ServerOptimizer
  ├─ cleanupEntities() 1 passe / monde / 60 s (flèches 30 s, XP optionnel,
  │   cull max-items-per-chunk + max-entities-per-chunk, grâce loot de mort 5 min)
  ├─ autoSave() : joueurs uniquement (Paper sauvegarde les mondes de façon incrémentale)
  └─ getStats() : TPS×3, MSPT, mémoire + MXBeans GC, chunks/entités/tiles/distances par monde

ClearLagTask (180 s) — annonces + clear items hors grâce (loot mort 5 min protégé)
MobStackManager — stack animaux + mobs de spawners Cantale (1 entité simulée par stack)
HopperLimitListener — craft bloqué, pose par pouvoir de faction, cap achat /shop
ChunkLoader — préchargement async TP (rayon config) + préchauffage spawn au boot
AsyncTaskManager — pool partagé (hotspots async, …), shutdown propre
VillagerSpawnBlocker — blocage spawn/transform villageois si villagers-disabled
```

## Gouverneur : détails

- Config : `optimization.governor.*` dans `config.yml` (seuils, drops, minima, alertes).
- Les distances de base sont captées au boot par monde ; les changements sont
  **restaurés** à la remontée de mode et dans `shutdown()` (onDisable).
- Override manuel : `/optimize mode <auto|normal|degraded|critical>` (application immédiate,
  sans attendre min-mode-seconds).
- Alertes CRITICAL : joueurs avec `cantale.moderator` + embed Discord salon `mod-logs`,
  cooldown 5 min.

## Diagnostic

- `/optimize stats` : TPS 1m/5m/15m, MSPT, mode, mémoire + GC, par monde (chunks,
  entités, tile entities, sim/view distance actuelles), détail du dernier cleanup.
- `/optimize hotspots [n]` : snapshot sync des chunks chargés → tri async via
  `AsyncTaskManager` → top N par (entités + tile entities) avec coords monde/x,z.
- Log santé 5 min en console.

## Règles du module

- Jamais de `System.gc()` (G1GC + Aikar : contre-productif ; `-XX:+DisableExplicitGC`).
- Jamais de chargement/génération de chunk synchrone (`getChunkAtAsync` partout).
- Jamais de `world.save()` (freeze) — Paper gère ; Cantale ne sauve que les joueurs.
- Le cache de préchargement (`ChunkPreloadCache`) est borné (10 000 entrées, FIFO).
- Le package `cache/RedisCache` est initialisé mais **dormant** : pas d'usage pertinent
  en mono-serveur (le cross-serveur passera par le canal Velocity `cantale:sync`).
