Performance

Recommandations :
- Ne jamais effectuer d'opérations DB lourdes sur le thread principal. Utiliser `Bukkit.getScheduler().runTaskAsynchronously`.
- Cacher les lectures fréquentes (ranks, permissions, warps) en mémoire avec invalidation lors d'écritures.
- Éviter `World.loadChunk(x,z,true)` sur le thread principal; précharger peu avant la téléportation ou utiliser des loaders asynchrones et `callSyncMethod` pour les opérations sensibles.
- Utiliser des batchs pour les écritures massives et des transactions pour garantir l'intégrité.

Outils :
- `ChunkLoader` pour précharger les zones.
- `DatabaseManager` dispose d'une logique de reconnexion et d'indicateurs `isConnected()`.
