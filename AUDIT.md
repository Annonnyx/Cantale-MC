Audit rapide du plugin Cantale

Objectif: repérer les points chauds affectant la stabilité et les performances (I/O synchrones, accès BD fréquents, chargement de chunks en bloc).

Méthodologie: recherche de `getConnection()`, `executeQuery`, `loadChunk`, `getChunkAt`, et patterns I/O.

Résumé des hotspots identifiés

- Nombreux DAOs effectuent des opérations synchrones sur la thread principal via `DatabaseManager.getConnection()` et `executeQuery/executeUpdate`.
  - Fichiers importants: `PlayerDAO`, `FactionDAO`, `AuctionDAO`, `FortifiedChunkDAO`, `RankManager`, `TagManager`, `WantedDAO`, `ReinforcedBlockDAO`, `MuteDAO`, etc.
  - Impact: blocages lors de latence DB ou indisponibilité. Déjà partiellement atténué par déférer le chargement initial de certains composants au démarrage.

- Commandes et listeners qui effectuent des opérations DB synchrones:
  - `LinkCommand`, `DiscordCommand` (certaines parties sont async déjà), `Pc2Command`, plusieurs commandes liées aux ranks, homes, warps.
  - Impact: potentiellement perceptible pour les joueurs lors d'exécution de ces commandes.

- Chargement de chunks synchrones:
  - `RtpCommand` utilise `world.getChunkAt(x >> 4, z >> 4).load(true);` (synchrones, forcent le chargement instantané).
  - Teleport managers et spawn/warp/home invoquent `world.loadChunk()` dans certaines implémentations (déjà partiellement modifié).
  - Impact: spikes CPU et I/O disque lors de téléportations en zone non chargée.

Recommandations prioritaires (court terme)

1) Déplacer les opérations DB lourdes en asynchrone
   - Pour chaque commande/DAO utilisé depuis la thread MC (ex: commandes de profil, link, pc2, tag, rank), exécuter l'accès BD dans `Bukkit.getScheduler().runTaskAsynchronously(...)` et retourner les résultats à la main: `runTask(...)` pour mise à jour UI/players.
   - Priorité: `LinkCommand`, `Pc2Command`, `DiscordCommand` (compléter parties sync), `TagManager` (opérations admin fréquentes).

2) Cacher les lectures fréquentes en mémoire
   - Cache léger (ConcurrentHashMap) pour warps, ranks, permissions, tags. Invalider/rafraîchir lors d'écriture.
   - Priorité: `RankManager.getRank()` et `WarpManager.getWarp()`.

3) Limiter le chargement instantané de chunks
   - Remplacer `world.getChunkAt(...).load(true)` par `Chunk#load` asynchrone ou `chunk.load()` non-blocking, précharger uniquement le chunk cible (1 chunk) plutôt qu'un bloc.
   - Introduire un petit préchauffage asynchrone avec timeout pour éviter d'attendre trop longtemps.

4) Graceful degradation quand la DB est indisponible
   - Déjà ajouté: différer chargements au démarrage et scheduleReconnect.
   - Étendre: commandes qui nécessitent DB doivent répondre proprement (message d'erreur amical) si DB indisponible.

Mise en œuvre proposée (itérative)

- Sprint 1 (immédiat):
  - Convertir `LinkCommand` et `Pc2Command` pour exécuter DB en tâche asynchrone.
  - Ajouter cache pour `WarpManager`.
  - Modifier `RtpCommand` pour charger le chunk de manière non bloquante ou limiter l'usage de `load(true)`.

- Sprint 2:
  - Refactorer autres DAOs/commandes critiques (TagManager, RankManager, PlayerDAO) vers async or cached patterns.
  - Mettre en place métriques simples (durées moyennes des requêtes) pour voir gains.

- Sprint 3:
  - Documenter toutes les commandes et permissions (générer `COMMANDS.md`).
  - Tests de charge et validation.

Fichiers à modifier en priorité (suggestion):
- `src/main/java/fr/cantale/plugin/commands/LinkCommand.java`
- `src/main/java/fr/cantale/plugin/commands/Pc2Command.java`
- `src/main/java/fr/cantale/plugin/teleport/RtpCommand.java`
- `src/main/java/fr/cantale/plugin/teleport/*` (assurer async chunk loading)
- `src/main/java/fr/cantale/plugin/teleport/WarpManager.java` (ajouter cache)

Prochaine action que je vais exécuter: convertir `LinkCommand` en version asynchrone et ajouter un petit cache pour `WarpManager` (Sprint 1 première sous-tâche). Voulez-vous que je commence par `LinkCommand` ?
