Overview

Cantale est un plugin Bukkit/Paper pour Minecraft qui fournit une expérience serveur personnalisée : factions, economy, homes/warps, téléportation, modération, intégration Discord et plus.

Objectifs principaux :
- Jeu PvP/PvE persistant avec gestion de factions et claims
- Économie et systèmes persistants (enchères, coffres privés)
- Outils de modération et bridge Discord
- Robustesse : tolérance aux pannes de DB et opérations asynchrones

Composants clés :
- `CantalePlugin` : point d'entrée et initialisation des managers
- DAO/DatabaseManager : accès DB (MySQL ou fallback SQLite)
- DiscordBot (JDA) : intégration Discord, commandes slash et relai chat
- Managers : `WarpManager`, `HomeManager`, `TeleportDelayManager`, `ChunkLoader`, etc.
