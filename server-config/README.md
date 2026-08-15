# Configs serveur optimisées — Cantale (Paper 1.21.x)

Ce dossier contient des **EXTRAITS** de configuration (fichiers `*.extra`) à **fusionner**
dans les fichiers existants du serveur — ne JAMAIS écraser un fichier live avec ces extraits
(ils ne contiennent que les clés à modifier).

Fichiers cibles sur le serveur :

| Extrait                 | À fusionner dans                    |
|-------------------------|-------------------------------------|
| `server.properties.extra` | `<serveur>/server.properties`     |
| `bukkit.yml.extra`        | `<serveur>/bukkit.yml`            |
| `spigot.yml.extra`        | `<serveur>/spigot.yml`            |
| `paper-global.yml.extra`  | `<serveur>/config/paper-global.yml` |
| `paper-world-defaults.yml.extra` | `<serveur>/config/paper-world-defaults.yml` |

Sources : guide YouHaveTrouble/minecraft-optimization (1.21.11) + docs officielles Paper.

## Ordre d'application recommandé

1. **Snapshot/backup** des 5 fichiers live avant toute modification.
2. Appliquer `server.properties.extra` + `bukkit.yml.extra` + `spigot.yml.extra`.
3. Appliquer `paper-world-defaults.yml.extra` **sans** les blocs marqués `OPTIONNEL`.
4. Redémarrer, jouer 24–48 h, mesurer avec `/mspt`, `/optimize stats` et Spark.
5. Activer ensuite les options `OPTIONNEL` une par une si tout est stable.
6. Le plugin Cantale gère déjà : clear-lag, cull entités/chunk, mob-stacking, limite de
   hoppers, gouverneur adaptatif (distances/spawners sous charge). Ne PAS installer de
   plugin clearlag/stacker tiers (doublon, contre-productif).

## Options marquées OPTIONNEL (changent le gameplay)

- `misc.redstone-implementation: ALTERNATE_CURRENT` — redstone bien plus rapide, mais
  micro-différences possibles sur les circuits très techniques (clocks précises).
- `entities.armor-stands.tick: false` — les armor stands ne subissent plus gravité/poussée
  (OK pour la déco ; Cantale utilise des TextDisplay pour ses hologrammes, non affectés).
- `spigot.yml → nerf-spawner-mobs: true` — retire l'IA des mobs de spawners **vanilla**.
  Les spawners Cantale (spawn `CUSTOM`) ne sont PAS affectés. À éviter si des joueurs
  utilisent encore des spawners vanilla en farm active.
- `anticheat.anti-xray.enabled: true` — coûte un peu de CPU mais bloque les x-ray
  (recommandé sur un serveur factions). engine-mode 1 = le moins coûteux.

## Retour arrière

Restaurer le backup du fichier concerné et redémarrer. Chaque extrait liste les valeurs
par défaut Paper en commentaire pour revenir en arrière au cas par cas.
