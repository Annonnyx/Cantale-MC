Teleportation

Comportement :
- Les téléportations (spawn, warp, home, faction home) respectent un délai configurable et peuvent être annulées si le joueur bouge ou subit des dégâts.
- Pour réduire les blocages, le plugin planifie le préchargement du chunk cible environ 1 seconde avant la téléportation lorsque la téléportation a un délai.

Détails :
- `TeleportDelayManager` gère l'affichage du compte à rebours et annule si nécessaire.
- `ChunkLoader` fournit des utilitaires pour précharger des zones quand nécessaire, éviter les charges massives en une seule fois.
