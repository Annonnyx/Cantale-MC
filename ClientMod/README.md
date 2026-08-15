# Cantale Client — mod Fabric

Mod client officiel du serveur **Cantale** : une vraie carte du monde façon JourneyMap,
intégrée au serveur (claims de factions colorés par relation, warps, homes, spawn de
faction synchronisés en direct).

- **Minecraft** : 1.21.11 · **Java** : 21 · **Loader** : Fabric (Fabric API requis)
- **Licence** : propriétaire — réservé au serveur Cantale.

## Fonctionnalités

| Touche | Action |
| ------ | ------ |
| **M** | Carte plein écran : terrain réel top-down (teintes biome + ombrage de relief), **drag** souris pour se déplacer, **molette** pour zoomer (1 → 16 blocs/pixel), centrée sur le joueur à l'ouverture. |
| **N** | Minimap en haut à droite (zoom fixe 4 blocs/px, centrée sur le joueur, masquée en F3). |

**Overlays serveur** (quand le serveur exécute le plugin Cantale) :

- **Claims colorés par relation** : vert = ta faction, bleu = allié, **orange = guerre**,
  rouge = ennemi, **violet = PASDIC**, gris = autre. Les factions en mode **secret**
  sont invisibles pour les non-membres (même règle que la carte du site et `/carte`).
- **Warps** publics (cyan, or pour les warps d'event), **homes** perso (vert),
  **/f spawn** de ta faction (orange), **flèche du joueur** orientée.
- Mises à jour **en direct** : claim/unclaim, changement de diplomatie, PASDIC,
  warps/homes créés ou supprimés.

Les touches sont reconfigurables dans *Options → Contrôles → Cantale*.

## Build

Prérequis : **JDK 21+** installé (le wrapper Gradle télécharge le reste).

```bash
cd ClientMod
./gradlew build        # Windows : gradlew.bat build
```

Le jar se trouve dans `build/libs/cantale-client-<version>.jar`.
Le premier build est long (téléchargement des mappings Yarn + client Minecraft) ;
les suivants prennent quelques secondes.

## Installation / distribution

1. Le joueur installe **Fabric Loader 1.21.11** (installateur sur fabricmc.net) ou
   utilise un launcher compatible (Prism, Modrinth App…).
2. Déposer dans le dossier `mods/` :
   - `cantale-client-<version>.jar`
   - **Fabric API** 1.21.11 (obligatoire — téléchargeable sur Modrinth/CurseForge).
3. Lancer le jeu et rejoindre le serveur : le log client affiche
   `Serveur Cantale détecté — carte synchronisée.`

Pour une distribution de masse : intégrer le jar + Fabric API dans le **modpack /
launcher** du serveur (Profil Prism exporté, modpack CurseForge/Modrinth, ou
launcher maison) — le mod n'a aucune configuration côté joueur.

## Comportement dégradé (jamais d'erreur)

| Situation | Comportement |
| --------- | ------------ |
| Serveur **sans** le plugin Cantale (ou bridge désactivé `map.client-mod.enabled: false`) | La carte affiche **uniquement le terrain** : pas de claims ni marqueurs, aucune erreur. |
| **Sans le mod** (client vanilla) | Le serveur n'envoie rien (le client n'a pas fait le handshake) : rien ne change, `/carte` et `/f map` restent disponibles. |
| Version du protocole plus récente que le mod | Les messages sont ignorés proprement (log debug), la carte reste fonctionnelle. |
| **Geyser / Bedrock** | Les joueurs Bedrock ne peuvent pas installer de mods Java : pas de carte moddée pour eux (ils gardent `/carte` en jeu). Aucun impact serveur. |

## Protocole `cantale:map` (v1)

JSON UTF-8 dans un custom payload, messages < 32 Ko. Le client envoie
`HELLO <version>` à la connexion ; le serveur répond par un `welcome` puis l'état
initial (claims paginés + markers) et envoie ensuite des deltas.

```json
{"v":1,"type":"welcome","protocol":1,"worlds":[{"name":"world","dim":"overworld"}]}
{"v":1,"type":"claims","world":"world","page":0,"pages":2,"chunks":[{"x":12,"z":-5,"rel":"OWN"}]}
{"v":1,"type":"markers","warps":[{"name":"Spawn","world":"world","x":-67,"y":65,"z":-144,"event":false}],"homes":[…],"fspawn":{…}}
{"v":1,"type":"claim_add","world":"world","x":13,"z":-5,"rel":"ALLY"}
{"v":1,"type":"claim_remove","world":"world","x":13,"z":-5}
{"v":1,"type":"claims_reset"}
```

`rel` ∈ `OWN | ALLY | WAR | ENEMY | OTHER | PASDIC`, calculée **pour le joueur
receveur**. Les pages d'un envoi complet sont assemblées côté client avant affichage.

## Structure du code

```
src/main/java/fr/cantale/client/
├── CantaleMod.java                  point d'entrée, touches, tick
├── net/
│   ├── CantaleMapPayload.java       payload brut du canal cantale:map
│   └── CantaleNetworkHandler.java   handshake + réception + décodage JSON (try/catch)
├── data/
│   └── ServerDataStore.java         stockage thread-safe claims / marqueurs / mondes
├── render/
│   ├── TerrainSampler.java          couleurs top-down du terrain (cache par chunk)
│   └── MapCanvas.java               texture de carte + overlays (claims, marqueurs, joueur)
└── ui/
    ├── MapScreen.java               écran plein format (touche M)
    └── MinimapHud.java              overlay HUD (touche N)
```
