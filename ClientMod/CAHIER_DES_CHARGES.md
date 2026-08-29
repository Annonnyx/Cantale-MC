# Cahier des charges — Mod client Fabric Cantale

| | |
|---|---|
| **Document** | Cahier des charges fonctionnel et technique |
| **Produit** | Mod client officiel **Cantale Client** (`id` Fabric : `cantale`) |
| **Version du document** | 1.0 |
| **Date** | 29 août 2026 |
| **Périmètre** | Dossier `ClientMod/` + pont serveur `fr.cantale.plugin.map.ClientMapBridge` |
| **Livrable logiciel** | `cantale-client-1.0.0.jar` |
| **Statut** | Exigences figées. Distingue **déjà livré** (code actuel) et **à livrer** (écarts). |

Ce document est un **cahier des charges** : il prescrit le comportement attendu (« le système doit… »). Il n’est pas un changelog. Chaque exigence porte un statut :

| Statut | Signification |
|---|---|
| **LIVRÉ** | Implémenté dans le code actuel ; l’exigence est figée et déjà satisfaite. Toute régression est un défaut. |
| **PARTIEL** | Présent mais insuffisant vis-à-vis de la cible figée. Le comportement actuel est décrit ; le système **doit** atteindre la cible. |
| **À FAIRE** | Exigence figée absente du code. Le système **doit** la réaliser. |

Sources d’audit (aucune invention hors de ces fichiers) :

- `ClientMod/src/main/java/fr/cantale/client/**`
- `ClientMod/src/main/resources/fabric.mod.json`
- `ClientMod/src/main/resources/assets/cantale/lang/{fr_fr,en_us}.json`
- `ClientMod/gradle.properties`, `ClientMod/build.gradle`
- `src/main/java/fr/cantale/plugin/map/ClientMapBridge.java` et voisins
- `src/main/resources/config.yml` (section `map:`)
- `TODO.md` § « Mod client Cantale »

---

## 1. Contexte et objectifs

### 1.1 Contexte

Le serveur **Cantale** tourne sous **Paper 1.21.11**. Le plugin Java `CantalePlugin` gère factions, claims, diplomatie, warps, homes, spawn de faction (`/f spawn`), mode secret, PASDIC, etc.

La carte **in-game vanilla** du plugin (tuiles `FILLED_MAP`, commandes `/carte` / `/carte mini`) est **dormante** : `map.enabled` vaut `false` dans `config.yml` et dans le code ; la commande n’est plus enregistrée. La visualisation carte destinée aux joueurs Java est le **mod client Fabric**, synchronisé par le canal plugin `cantale:map`.

Le plugin tente en outre, à la connexion, de **désactiver les minimaps tierces** (JourneyMap, Xaero’s Minimap, VoxelMap) via des plugin messages (`MapIntegrationListener` → `MapIntegrationManager.suppressThirdPartyMinimaps`). Le mod Cantale est donc le remplacement officiel de ces outils sur le serveur.

### 1.2 Objectifs du produit

Le système doit fournir au joueur Java, **uniquement côté client**, une carte du monde comparable à **JourneyMap** sur les points suivants :

- lisibilité du terrain (vue top-down, biomes, relief) ;
- ergonomie (plein écran, minimap, pan, zoom, filtres, liste de marqueurs) ;
- **waypoints visibles dans le monde 3D** (faisceau, icône HUD, nom, distance, indicateurs de bord d’écran).

Il doit en plus exposer les **données Cantale** calculées pour le joueur connecté :

- claims de factions colorés par **relation** (pas par nom de faction) ;
- warps publics actifs ;
- homes personnels ;
- spawn de la faction du joueur (`/f spawn`, libellé **FW** dans l’UI).

### 1.3 Objectifs hors scope (figés)

Le système **ne doit pas** :

- exister en Forge, NeoForge, Quilt ou autre loader que Fabric ;
- réactiver la map in-game plugin (`/carte`, tuiles `FILLED_MAP`) — module dormant conservé côté serveur, hors livrable de ce mod ;
- fonctionner comme un cheat : aucune téléportation arbitraire, aucun claim/unclaim depuis le client, aucune donnée que le serveur n’aurait pas déjà envoyée à ce joueur ;
- s’exécuter côté serveur (le mod déclare `"environment": "client"`) ;
- viser les joueurs Bedrock / Geyser (pas de mods Java).

### 1.4 Principes directeurs

1. **Client-only.** Le JAR ne s’installe que dans `mods/` du client.
2. **Dégradation silencieuse.** Sans plugin Cantale, ou avec `map.client-mod.enabled: false`, la carte affiche le terrain seul. Aucune erreur, aucun crash, aucun spam de logs.
3. **Vanilla ignoré.** Le serveur n’envoie rien tant que le client n’a pas fait le handshake. Un joueur sans le mod n’est pas impacté.
4. **Anti-triche.** Les TP passent exclusivement par les commandes serveur déjà autorisées : `warp <nom>`, `home <nom>`, `f spawn`.
5. **Filtrage serveur.** Claims secrets, relations, warps inactifs : calculés **sur le serveur pour ce joueur**. Le client n’invente pas de visibilité.

---

## 2. Stack technique

Les versions ci-dessous sont **figées** par `ClientMod/gradle.properties` et `ClientMod/build.gradle`. Le système doit les respecter.

| Composant | Valeur exigée |
|---|---|
| Minecraft | `1.21.11` (`minecraft_version`) |
| Java | 21 (`sourceCompatibility` / `targetCompatibility` / `options.release = 21`) |
| Fabric Loader | `0.19.3` (`loader_version`) — dépendance `fabricloader >= 0.19.3` |
| Fabric API | `0.141.6+1.21.11` (`fabric_api_version`) |
| Mappings | Yarn `1.21.11+build.6` |
| Loom | `1.17.19` |
| Gradle (wrapper) | `9.5.1` |
| Groupe Maven | `fr.cantale` |
| Artifact | `cantale-client` |
| Version du mod | `1.0.0` |
| Encodage sources | UTF-8 |
| Canal réseau | `cantale:map` (`Identifier.of("cantale", "map")`) |
| Protocole | **v1** (`PROTOCOL_VERSION = 1` côté client et serveur) |
| Point d’entrée | `fr.cantale.client.CantaleMod` (entrypoint Fabric `client`) |
| Licence | Propriétaire — réservé au serveur Cantale |

Le plugin serveur est Paper 1.21.11 (`CantalePlugin`). Le pont est `ClientMapBridge`, activé par défaut (`map.client-mod.enabled: true`).

### 2.1 Structure du code client (référence)

```
ClientMod/src/main/java/fr/cantale/client/
├── CantaleMod.java                 entrée, touches M/N, tick
├── net/
│   ├── CantaleMapPayload.java      payload brut (octets UTF-8)
│   └── CantaleNetworkHandler.java  HELLO, file, décodage JSON
├── data/
│   ├── ServerDataStore.java        claims / marqueurs / mondes (thread-safe)
│   └── WaypointManager.java        waypoints perso + filtres persistés
├── render/
│   ├── TerrainSampler.java         couleurs top-down, cache par chunk
│   └── MapCanvas.java              texture + overlays
└── ui/
    ├── MapScreen.java              écran plein écran (M)
    └── MinimapHud.java             overlay HUD (N)
```

Langues : `assets/cantale/lang/fr_fr.json` et `en_us.json`.

---

## 3. Acteurs et cas d’usage

### 3.1 Acteurs

| Acteur | Description | Features spécifiques |
|---|---|---|
| **Joueur Cantale (moddé)** | Client Fabric + API + ce mod, connecté au serveur Cantale avec bridge actif | Carte complète : terrain + claims + marqueurs serveur + waypoints perso + TP commandes |
| **Joueur hors Cantale** | Même mod, serveur sans plugin ou bridge désactivé | Terrain + waypoints perso locaux uniquement. Pas de claims, pas de warps/homes/FW serveur |
| **Joueur vanilla** | Pas de mod | Hors produit. Le serveur n’envoie rien. Aucune exigence UI. |
| **Staff** | Op / admin / modo | **Aucune feature obligatoire distincte dans le mod.** Le code client ne branche aucun permission staff, aucun overlay admin, aucun outil de modération. Les actions staff restent les commandes plugin habituelles. |

L’inhibiteur de minimaps tierces (plugin, à la connexion de **tout** joueur Java) n’est **pas** une feature du mod client ; il est hors livrable de ce CDC mais fait partie du contexte serveur.

### 3.2 Cas d’usage principaux

| ID | Acteur | Cas | Résultat attendu |
|---|---|---|---|
| UC-01 | Joueur Cantale | Rejoint le serveur | Handshake `HELLO 1` ; log client « Serveur Cantale détecté » ; overlays serveur disponibles |
| UC-02 | Joueur Cantale | Appuie sur **M** | Carte plein écran centrée sur sa position |
| UC-03 | Joueur Cantale | Appuie sur **N** | Minimap HUD haut-droite affichée / masquée |
| UC-04 | Joueur Cantale | Consulte les claims | Territoires colorés par sa relation (OWN/ALLY/WAR/ENEMY/PASDIC/OTHER), contours de zone |
| UC-05 | Joueur Cantale | Clique un warp / home / FW | Dialogue de confirmation, puis commande serveur, puis fermeture de la carte |
| UC-06 | Joueur Cantale | Clic droit sur la carte | Création d’un waypoint personnel au point visé |
| UC-07 | Joueur Cantale | Marche dans le monde | Waypoints in-world (faisceau, HUD, distance, bords d’écran) |
| UC-08 | Joueur hors Cantale | Ouvre M | Terrain + waypoints perso ; badge « Terrain seul » ; aucun crash |
| UC-09 | Joueur vanilla | Joue sur Cantale | Aucun trafic `cantale:map` ; jeu inchangé |
| UC-10 | Joueur Cantale | Faction adverse en mode secret | Claims de cette faction **absents** (sauf s’il en est membre) |
| UC-11 | Joueur Cantale | Change de monde | Claims du nouveau contexte renvoyés (`claims_reset` + pages) |
| UC-12 | Joueur Cantale | Claim / unclaim / diplomatie / PASDIC / warp / home / setspawn | Carte mise à jour sans reconnexion |

---

## 4. Fonctionnalités — état actuel vs cible

Légende des couleurs claims (code `MapCanvas`, cohérent avec le README client) :

| Relation | Code hex | Rendu |
|---|---|---|
| OWN | `#3FD23F` | Vert — faction du joueur |
| ALLY | `#3FB4D2` | Bleu — alliance |
| WAR | `#E69B3A` | Orange — guerre mutuelle (`DiplomacyManager.isAtWar`) |
| ENEMY | `#D23F3F` | Rouge — **réservé** au protocole (guerre unilatérale future). Le pont **n’émet pas** `ENEMY` aujourd’hui |
| PASDIC | `#B04FD8` | Violet — prioritaire sur toute autre relation |
| OTHER | `#8A8A96` | Gris — reste |

Couleurs marqueurs (`MapCanvas`) :

| Type | Hex | Badge |
|---|---|---|
| Warp public | `#35E0D0` | `W` |
| Warp event | `#FFD24A` | `W` |
| Home | `#7CFC3F` | `H` |
| Spawn faction (FW) | `#FF6E40` | `S` |
| Waypoint perso | palette cyclique (voir §6) | `P` |

---

### 4.A Carte plein écran (touche M) — **LIVRÉ** (écarts mineurs §4.G / §4.H / §4.I)

Le système doit ouvrir un écran `MapScreen` (titre i18n `screen.cantale.map.title` : « Carte du monde — Cantale ») lorsque le joueur appuie sur la touche configurée (`key.cantale.map`, défaut **M**), s’il a un monde et un joueur chargés.

Exigences figées **déjà satisfaites** :

1. Le jeu **ne doit pas** se mettre en pause (`shouldPause() == false`).
2. Réappui sur **M** (ou fermeture vanilla) doit fermer l’écran.
3. À l’ouverture, le centre de vue **doit** être la position X/Z du joueur.
4. Layout :
   - barre supérieure de 22 px : `Cantale  ·  <X> / <Z>  ·  zoom <n> b/px` à gauche ; à droite `Serveur Cantale` (`screen.cantale.map.connected`) si handshake reçu, sinon `Terrain seul` (`screen.cantale.map.offline`) ;
   - zone carte à gauche ;
   - panneau latéral droit 220 px (18 px replié) ;
   - légende claims bas-gauche si serveur Cantale **et** filtre Claims coché.
5. Niveaux de zoom **uniquement** : 1, 2, 4, 8, 16 blocs/pixel. Index initial : **2** (4 b/px).
6. Coordonnées curseur : si la souris est sur la carte, X/Z du bloc visé (floor) ; sinon X/Z du centre (round).
7. Le système doit reconstruire la texture si la taille de la zone carte change (resize, repli du panneau).
8. Les touches sont reconfigurables dans *Options → Contrôles → catégorie Cantale* (`key.categories.cantale.main`).

Écart à corriger (lié aux dialogues) : pendant la saisie du nom d’un waypoint, **M** ferme actuellement **tout** l’écran. Le système **doit** ignorer M tant qu’un dialogue modal est ouvert (overlay nom, confirmation TP, menu contextuel). **À FAIRE** (comportement cible).

---

### 4.B Minimap (touche N) — **LIVRÉ**

Le système doit afficher une minimap HUD via `HudElementRegistry` (`cantale:minimap`).

Exigences figées **déjà satisfaites** :

1. Touche `key.cantale.minimap`, défaut **N**, bascule affichage / masquage.
2. **Active par défaut** au lancement du client.
3. Taille **120×120** px GUI ; marge **6** px ; ancrée **haut-droite**.
4. Zoom **fixe 4 blocs/px** ; toujours centrée sur le joueur.
5. Même moteur `MapCanvas` que le plein écran (terrain, claims, marqueurs, flèche joueur).
6. **Pas** de labels de marqueurs sur la minimap (`showLabels = false`).
7. Cadre sombre `0xCC14141C` + 2 px ; coordonnées `X Z` du joueur sous la carte, fond identique.
8. Masquée si l’écran `MapScreen` est ouvert (pas de doublon).
9. Masquée si le HUD de debug **F3** est visible.

Écarts (non bloquants pour la v1 sauf mention) :

- L’état on/off **n’est pas persisté** (redevient actif à chaque lancement). **Hors exigence figée v1** ; évolution possible (§10).
- Pas de zoom, pas de clic, pas de déplacement de la minimap. **Hors exigence figée v1**.

---

### 4.C Terrain (`TerrainSampler`) — **LIVRÉ**

Le système doit peindre une vue top-down du `ClientWorld` :

1. Pour chaque colonne 16×16 d’un chunk **chargé** (`ChunkStatus.FULL`) : hauteur `MOTION_BLOCKING`, couleur du bloc de surface.
2. Teinte biome via `BlockColors.getColor` si un provider existe (`!= -1`) ; sinon `BlockState.getMapColor`.
3. Ombrage de relief : comparaison de hauteur avec le voisin **ouest** ; facteur `1 + 0,07 × clamp(dy, -3, +3)` (plus haut = plus clair).
4. Chunks non chargés : couleur `#26262E` (`UNKNOWN`), **non mis en cache** (nouvel essai à la frame suivante).
5. Cache par chunk (`ConcurrentHashMap`) ; invalidation totale au changement de monde.
6. Rafraîchissement périodique : toutes les **40** ticks client, rayon **2** chunks autour du joueur, si l’échantillon a plus de **10 000 ms**. Objectif : suivre les constructions proches.
7. `MapCanvas` ne repeint la texture que si le centre a dérivé de plus de **16 px** écran, ou si zoom / monde / version données / version terrain changent. Un décalage (shift) assure un pan fluide entre deux repeintes.

Le système **ne doit pas** échantillonner le serveur ni demander des chunks hors view distance vanilla.

---

### 4.D Claims (contours de zone) — **LIVRÉ**

Le système doit afficher les claims reçus pour le **monde serveur courant** (mapping dimension → nom Bukkit, voir §7).

Règles serveur (pont, identiques à l’ancienne map in-game) :

1. Une faction en **mode secret** est **exclue** pour tout le monde sauf ses membres.
2. Relation **pour le joueur destinataire** uniquement, dans `relationFor` :
   - si le claim est PASDIC → `PASDIC` (prioritaire) ;
   - si le joueur n’a pas de faction → `OTHER` ;
   - si même faction → `OWN` ;
   - sinon alliance → `ALLY` ;
   - sinon guerre mutuelle → `WAR` ;
   - sinon → `OTHER`.
3. `ENEMY` est dans l’enum protocole et coloré côté client, mais **non produit** par le pont v1.

Règles de rendu client (figées, déjà en place) :

1. Le filtre **Claims** doit pouvoir masquer tout l’overlay.
2. **Interdiction de la grille chunk visible** (plus de quadrillage 16×16 systématique). Chaque chunk claimé a un **fill léger** alpha `0x3A` et un **contour** alpha `0xE6` **uniquement** sur les bords où le voisin a une relation différente **ou** n’est pas claimé. Épaisseur du trait : 2 px si le rectangle chunk fait au moins 6×6 px écran, sinon 1 px.
3. Le résultat visuel doit être des **territoires d’un seul tenant** par relation, pas un damier de chunks.
4. Un chunk PASDIC au milieu d’un territoire OWN **doit** garder un contour (relation différente).
5. Légende bas-gauche : pastilles OWN / ALLY / WAR / ENEMY / PASDIC / OTHER, uniquement si serveur Cantale + filtre Claims.

---

### 4.E Warps, homes, FW, waypoints personnels — **LIVRÉ** (carte 2D) / **À FAIRE** (monde 3D, voir 4.J)

#### Warps — **LIVRÉ**

Le serveur n’envoie que les warps **actifs** avec monde et location non nuls. Champ `event` (booléen) : or si vrai, cyan sinon.

Le client doit :

- les filtrer au monde courant ;
- respecter le filtre catégorie **Warps** et la case individuelle `warp:<nom>` ;
- les dessiner en pin + badge `W` + nom (selon zoom / survol) ;
- permettre le TP via la commande `warp <nom>` **après confirmation** (§4.G).

#### Homes — **LIVRÉ**

Le serveur envoie les homes **du joueur connecté** uniquement (`HomeManager.ensureHomesLoaded`).

Même logique : filtre **Homes**, clé `home:<nom>`, badge `H`, commande `home <nom>` après confirmation.

#### Spawn de faction (FW) — **LIVRÉ**

Si le joueur a une faction **avec spawn défini**, le serveur envoie un objet `fspawn` (`name` = nom de faction, world, x, y, z). Sinon `fspawn: null`.

Clé de case individuelle stable : `"fw"` (indépendante du nom de faction). Commande : `f spawn`. Badge `S`. Filtre catégorie **FW** séparé des warps et des homes.

#### Waypoints personnels — **LIVRÉ** (carte 2D et persistance)

Le système doit permettre des waypoints **locaux** (jamais envoyés au serveur) :

- création : clic droit sur la carte **au point visé**, ou bouton « + Waypoint » **au centre de vue** ;
- overlay de saisie du nom (max **32** caractères) ; préréglage `WP <x>,<z>` ; Entrée / OK valide, Échap / Annuler abandonne ; nom vide → le préréglage ;
- Y = Y du joueur au moment de la création, ou **64** si joueur absent ;
- couleur : palette cyclique de 6 teintes selon le nombre de waypoints déjà présents ;
- id : `custom_<epochMillis>` ;
- affichage : pin + badge `P` + couleur personnelle ; **pas** de commande TP (un waypoint perso n’est pas un warp) ;
- suppression et case afficher/masquer : panneau (et, à livrer, menu contextuel §4.H).

---

### 4.F Filtres cases à cocher persistés — **LIVRÉ**

Le système doit persister dans `config/cantale_waypoints.json` (voir §6) :

| Filtre UI (i18n) | Champ JSON | Défaut |
|---|---|---|
| Claims | `filters.claims` | `true` |
| Warps | `filters.warps` | `true` |
| Homes | `filters.homes` | `true` |
| FW | `filters.factionSpawn` | `true` |
| Waypoints | `filters.waypoints` | `true` |

Chaque **item** de la liste (home, warp, FW, waypoint perso) a une case propre. Les marqueurs serveur décochés sont stockés dans `disabledServerMarkers` (`warp:<nom>`, `home:<nom>`, `fw`). Les waypoints perso utilisent le booléen `enabled` de l’objet.

Toute bascule **doit** déclencher une sauvegarde disque immédiate.

Compatibilité **LIVRÉE** : un ancien fichier qui n’est qu’un tableau JSON de waypoints doit encore se charger ; les clés alternatives `showClaims` / `showPublicWarps` / etc. doivent encore être lues.

---

### 4.G Interactions : pan, zoom, clic droit, TP — **PARTIEL**

| Interaction | Statut | Règle |
|---|---|---|
| Pan clic-gauche + drag sur **toute** la zone carte | **LIVRÉ** | Le centre se déplace de `deltaPx × zoom` blocs. Seuil **4 px** pour distinguer clic et drag. |
| Zoom molette **vers le curseur** | **LIVRÉ** | Le point monde sous le curseur reste sous le curseur après changement d’index de zoom. Molette sur le panneau : scroll de la liste (18 px / cran). Molette pendant un overlay : ignorée. |
| Double-clic gauche sur la carte | **LIVRÉ** | Recentre sur le joueur. |
| Clic droit sur la carte | **LIVRÉ** | Ouvre l’overlay de **création** de waypoint au X/Z visé. |
| Clic gauche (sans drag) sur un marqueur TP-able | **PARTIEL** | Aujourd’hui : envoi **immédiat** de la commande et fermeture de l’écran. **Cible figée** : **confirmation Oui / Non** avant tout envoi. |
| Bouton TP du panneau | **PARTIEL** | Idem : confirmation obligatoire. |

#### Confirmation de téléportation — **À FAIRE** (exigence figée)

Le système **doit**, avant tout `sendChatCommand` :

1. Afficher un dialogue modal centré, fond assombri, même langage visuel que l’overlay nom de waypoint.
2. Texte : nom du marqueur, type (warp / home / spawn de faction), coordonnées X/Z, commande cible en clair (ex. `/warp Spawn`).
3. Boutons **Oui** et **Non** (i18n dédiées, FR et EN). Entrée = Oui, Échap = Non.
4. **Oui** : envoie **uniquement** `warp <nom>`, `home <nom>` ou `f spawn` via `ClientPlayNetworkHandler.sendChatCommand` (sans slash, API vanilla) ; puis ferme la carte.
5. **Non** : ferme le dialogue, **reste** sur la carte, n’envoie rien.
6. Interdiction d’empiler plusieurs confirmations ; un seul modal à la fois.
7. Les waypoints personnels **n’ont pas** de TP : un clic dessus ne doit pas proposer de téléportation.

État actuel à remplacer : `MapScreen.teleport(String)` envoie la commande et `close()` sans dialogue.

---

### 4.H Menu contextuel waypoint — **PARTIEL** / **À FAIRE**

**Déjà livré** (panneau uniquement) :

- case à cocher → afficher / cacher (`enabled` ou `disabledServerMarkers`) ;
- bouton **×** sur un waypoint perso → suppression immédiate (sans confirmation aujourd’hui).

**Cible figée — À FAIRE** : menu contextuel au **clic droit sur un pin de waypoint personnel** (pas sur warp/home/FW : le clic droit carte reste « créer un waypoint » **si aucun pin n’est sous le curseur** ; si un pin perso est dans le rayon de hit-test **10 px**, le clic droit ouvre le menu).

Le menu **doit** proposer :

| Action | Comportement |
|---|---|
| Renommer | Champ texte max 32 caractères, OK / Annuler ; persistance immédiate |
| Couleur | Choix dans la palette des 6 couleurs déjà utilisées à la création |
| Afficher / Cacher | Même effet que la case du panneau |
| Supprimer | Confirmation Oui/Non (éviter un clic droit malheureux) ; puis retrait + save |

Le système **doit** exposer les mutateurs manquants aujourd’hui (`CustomWaypoint` n’a pas de `setName` / `setColor` publics utilisés). La suppression depuis le panneau **doit** passer par la même confirmation que le menu.

Warps / homes / FW : pas de rename/couleur/suppression (données serveur). Clic droit sur leur pin : **ne doit pas** créer un second waypoint par-dessus ; le menu n’applique pas. Comportement cible : tooltip uniquement, ou menu réduit « Centrer » / « Téléporter… » (ce dernier ouvre la confirmation §4.G).

---

### 4.I Marqueur joueur (flèche) — **PARTIEL**

**Livré** : sprite **5×5** en **8 directions**, nord en haut de carte. Conversion yaw Minecraft (0 = sud) :  
`direction = floor(((yaw % 360 + 360 + 180 + 22.5) % 360) / 45) & 7`. Fond sombre 7×7 sous la flèche, pixels blancs.

**Cible figée** : la flèche **doit** rester immédiatement lisible à tous les zooms et sur terrain chargé/clair :

1. Forme de **chevrons / triangle** avec contour sombre (`#101018`) et remplissage blanc, taille minimale **9×9** px GUI (plus le 5×5 actuel).
2. Orientation : au moins **16 secteurs** (22,5°) **ou** rotation continue du yaw ; le nord carte reste en haut.
3. Léger halo / ombre pour contraster sur neige, sable, claims verts.
4. Identique en plein écran et sur la minimap (même primitive de dessin).

---

### 4.J Waypoints in-world façon JourneyMap — **À FAIRE** (exigence figée majeure)

Aucun rendu monde 3D n’existe aujourd’hui (`ClientMod` n’enregistre pas de `WorldRenderEvents` / overlay in-game hors minimap).

Le système **doit**, lorsque le joueur n’a **pas** l’écran carte ouvert, pour chaque marqueur **visible** (mêmes filtres et cases que la carte 2D, **même monde**) :

1. **Faisceau (beam)**  
   Colonne verticale dans la couleur du marqueur, de Y bas monde (ou Y du point) vers le ciel, visible à distance, alpha modéré, sans masquer excessivement le terrain. Désactivable avec le filtre de la catégorie (décocher Warps masque aussi les faisceaux warps).

2. **Icône HUD**  
   Icône / pin 2D projetée à la position monde (ou équivalent billboard), même code couleur et même lettre (W/H/S/P) que la carte.

3. **Nom**  
   Libellé du marqueur près de l’icône, fond sombre + filet de couleur (comme les labels carte).

4. **Distance**  
   Distance horizontale (ou 3D, au choix unique et documenté dans l’UI) en blocs, entière, ex. `342m`, à côté du nom.

5. **Indicateurs de bord d’écran**  
   Si le marqueur est hors champ caméra mais dans le même monde, une pastille / flèche **doit** se coller au bord du viewport dans la direction du marqueur, avec couleur + distance, sans recouvrir la hotbar ni la minimap (décalage sous la minimap haut-droite et au-dessus de la hotbar).

Règles transverses :

- F3 debug visible : les indicateurs HUD in-world **doivent** se masquer (comme la minimap), les faisceaux monde **peuvent** rester (cible : masquer tout overlay Cantale en F3 pour ne pas polluer le debug).
- Distance max d’affichage du nom/distance : le faisceau peut rester plus loin que le texte (éviter le spam). Valeur cible : texte/icône jusqu’à **256** blocs, faisceau jusqu’à **512** blocs, indicateurs de bord **sans limite de distance** dans le même monde (ou plafond 2 000 blocs si perf).
- Waypoints perso `enabled: false` : invisibles in-world.
- Performance : pas plus d’un passage de rendu par frame ; pas de lightmap / entity spawn ; rendu client only.

---

### 4.K Réseau / handshake HELLO — **LIVRÉ**

Voir le détail protocolaire au **§7**. Synthèse d’exigences déjà satisfaites :

1. À `JOIN`, reset du store, envoi `HELLO 1` (texte brut UTF-8).
2. Si aucun `welcome` après **100** ticks client : **un** second HELLO.
3. À `DISCONNECT` : reset store + file.
4. Réception Netty → file bornée **512** messages → application sur le **tick client**.
5. Parsing JSON intégralement en try/catch : aucune donnée malformée ne doit crasher le client.
6. Si `"v"` du message **>** `PROTOCOL_VERSION` du client : ignore + log debug.
7. Type inconnu : log debug, pas d’exception.
8. Codec : **octets bruts** (pas de préfixe VarInt `byteArray`) — exigence de compatibilité Bukkit `sendPluginMessage`.

---

## 5. UX détaillée

### 5.1 Raccourcis globaux

| Action | Effet | Notes |
|---|---|---|
| Touche carte (défaut M) | Ouvre `MapScreen` si in-game et pas déjà ouvert ; sinon ferme | Ignorer si un modal interne est ouvert (**cible**) |
| Touche minimap (défaut N) | Bascule le HUD minimap | Fonctionne même carte fermée |
| Échap | Ferme l’écran carte, ou le modal interne s’il est ouvert | Vanilla `Screen` |
| F3 | Masque minimap (+ overlays in-world, **cible**) | |

Les bindings vivent dans *Options → Contrôles → Cantale*.

### 5.2 Écran carte — zone terrain

| Action | Effet |
|---|---|
| Clic gauche + glisser ≥ 4 px | Pan (le terrain suit la souris) |
| Clic gauche relâché < 4 px, sur un pin warp/home/FW | Ouvre la **confirmation TP** (**cible** ; aujourd’hui TP immédiat) |
| Clic gauche relâché < 4 px, hors pin | Rien |
| Double-clic gauche | Recentre sur le joueur |
| Molette haut | Zoom avant (index --), ancré sur le curseur |
| Molette bas | Zoom arrière (index ++), ancré sur le curseur |
| Clic droit, aucun pin perso sous le curseur | Overlay **nouveau waypoint** au X/Z visé |
| Clic droit, pin perso sous le curseur | Menu contextuel (§4.H) **cible** |
| Survol d’un pin | Halo blanc, tooltip (nom, type i18n, X/Z, mention TP si applicable) |
| Labels pins | Toujours si zoom ∈ {1, 2, 4} (`LABEL_ZOOM_THRESHOLD = 8`) ; sinon seulement au survol |

Tooltip types (i18n livrées) :

- `screen.cantale.map.tooltip.warp` — « Warp public »
- `screen.cantale.map.tooltip.home` — « Home »
- `screen.cantale.map.tooltip.fw` — « Spawn de faction  ·  /f spawn »
- `screen.cantale.map.tooltip.waypoint` — « Waypoint personnel »
- `screen.cantale.map.tooltip.tp` — « Clic gauche pour se téléporter » (à aligner sur la confirmation : le texte **doit** indiquer qu’une confirmation suivra, une fois §4.G livré)

Astuce panneau : `screen.cantale.map.hint` — « Glisser · Molette · Clic droit waypoint · Clic TP ».

### 5.3 Barre supérieure

| Zone | Contenu |
|---|---|
| Gauche | `Cantale  ·  X / Z  ·  zoom N b/px` |
| Droite | `Serveur Cantale` ou `Terrain seul` |

### 5.4 Panneau latéral (220 px)

| Zone | Action | Effet |
|---|---|---|
| Chevron « « » (haut-gauche panneau) | Clic | Replie à 18 px, agrandit la carte |
| Panneau replié « » » | Clic n’importe où sur la bande | Déplie |
| Case + pastille **Claims** | Clic | Toggle overlay territoires + légende |
| Case **Warps** / **Homes** / **FW** / **Waypoints** | Clic | Toggle catégorie entière |
| Bouton **Me recentrer** | Clic | Centre = joueur |
| Bouton **+ Waypoint** | Clic | Overlay nom au **centre de vue** (pas au curseur) |
| En-têtes de liste (Homes / Warps / Spawn faction / Waypoints) | — | Affichés seulement si au moins un item du **monde courant** |
| Case d’une ligne | Clic | Affiche / masque cet item |
| Nom d’une ligne | Clic | Centre la carte sur X/Z de l’item |
| Pastille **TP** | Clic | Confirmation puis commande (**cible**) |
| Pastille **×** (waypoint perso seulement) | Clic | Confirmation puis suppression (**cible** ; aujourd’hui suppression directe) |
| Molette sur le panneau | Scroll | 18 px / cran ; ascenceur 2 px à droite si contenu > viewport |
| Zone hint bas (28 px) | — | Texte d’aide wrappé |

Lignes hors monde courant : **absentes** de la liste (warps/homes/FW filtrés par `markersIn` ; waypoints via `listCustomWaypointsInWorld`). Un waypoint dont `world` est `null` **doit** apparaître dans tous les mondes (code actuel).

### 5.5 Overlay « Nom du waypoint » — **LIVRÉ**

- Boîte 240×78, centrée, voile `0x88000000`.
- Champ texte + **OK** + **Annuler**.
- Entrée = OK, Échap = Annuler.
- Focus clavier sur le champ à l’ouverture.

### 5.6 Dialogue confirmation TP — **À FAIRE**

| Action | Effet |
|---|---|
| Ouverture | Voile + boîte ; focus sur **Non** (évite un Entrée accidentel) **ou** sur Oui si convention « Entrée = confirmer » — **décision figée** : Entrée = Oui, focus initial sur **Non** |
| Oui / Entrée | Commande serveur + fermeture carte |
| Non / Échap / clic voile | Ferme le dialogue, carte inchangée |
| M / N pendant le dialogue | Ignorés |

Textes i18n à ajouter (FR et EN), exemples imposés en FR :

- titre : « Se téléporter ? »
- corps : « Aller à {nom} ({type}) ? » + ligne `/warp …` ou équivalent
- Oui / Non

### 5.7 Menu contextuel waypoint — **À FAIRE**

Menu ancré près du pin, fermé par Échap, clic hors menu, ou après une action. Ne pause pas le jeu. Ne doit pas dépasser l’écran.

### 5.8 Minimap HUD

Aucune interaction souris. Pure lecture. Coordonnées sous le cadre.

### 5.9 Monde 3D — **À FAIRE**

Pas d’écran dédié. Overlays sur le gameplay (voir §4.J). Aucun clic requis en v1 (les indicateurs de bord sont informatifs). Evolution possible : clic indicateur → ouvrir la carte centrée (hors v1).

### 5.10 Internationalisation

Le système **doit** afficher les chaînes via les clés existantes (`fr_fr` / `en_us`). Toute nouvelle UI (confirmation TP, menu contextuel, in-world) **doit** ajouter les deux langues. Les libellés de légende claims (OWN, ALLY, …) restent des codes techniques courts.

---

## 6. Données et persistance

Fichier : `{runDirectory}/config/cantale_waypoints.json`  
Création du dossier `config/` si absent. Échec de lecture/écriture : log warn `[Cantale] Impossible de charger/sauvegarder cantale_waypoints.json`, **pas de crash**.

### 6.1 Schéma actuel (écriture) — **LIVRÉ**

```json
{
  "filters": {
    "claims": true,
    "warps": true,
    "homes": true,
    "waypoints": true,
    "factionSpawn": true
  },
  "waypoints": [
    {
      "id": "custom_1730000000000",
      "name": "WP 100,-20",
      "world": "world",
      "x": 100.4,
      "y": 64.0,
      "z": -20.1,
      "color": 14708987,
      "enabled": true
    }
  ],
  "disabledServerMarkers": [
    "warp:Spawn",
    "home:maison",
    "fw"
  ]
}
```

| Champ | Type | Règle |
|---|---|---|
| `filters.*` | boolean | Cinq clés ci-dessus ; défaut `true` si absent |
| `waypoints[].id` | string | Unique ; généré `custom_` + `currentTimeMillis` |
| `waypoints[].name` | string | 1–32 caractères utiles |
| `waypoints[].world` | string | Nom **serveur** (ex. `world`), pas `minecraft:overworld` |
| `waypoints[].x/y/z` | number | Doubles monde |
| `waypoints[].color` | int | RGB 24 bits (Gson : décimal, ex. `0xE040FB` → `14708987`) |
| `waypoints[].enabled` | boolean | Défaut `true` |
| `disabledServerMarkers` | string[] | `warp:<nom>`, `home:<nom>`, `fw` |

Palette de création (cycle `size % 6`) :

`0xE040FB`, `0x40C4FF`, `0xFFD54F`, `0x69F0AE`, `0xFF8A65`, `0x82B1FF`

### 6.2 Lecture rétrocompatible — **LIVRÉ**

1. Racine **tableau** → liste de waypoints uniquement, filtres aux défauts.
2. Racine **objet** : `filters` imbriqué **ou** booléens à la racine avec clés courtes **ou** `showClaims` / `showPublicWarps` / `showPrivateHomes` / `showCustomWaypoints` / `showFactionSpawn`.

### 6.3 Données non persistées (volontaire v1)

| Donnée | Stockage |
|---|---|
| Claims, warps, homes, fspawn | Mémoire `ServerDataStore` ; refill au handshake / deltas |
| Mapping dimension → monde | `welcome.worlds` ; défauts `overworld→world`, `the_nether→world_nether`, `the_end→world_the_end` |
| État minimap on/off | Volatile, défaut `true` |
| Centre / zoom de la carte | Réinitialisés à chaque ouverture (joueur, zoom 4) |
| File réseau | Vidée à la déconnexion |

### 6.4 Évolutions de schéma autorisées (si §4.H / §4.J)

Ajouter des champs **optionnels** avec défaut sûr (ex. `showInWorld: true`) sans casser la lecture des fichiers existants. Pretty-print Gson conservé.

---

## 7. Protocole réseau `cantale:map` v1

Canal Minecraft custom payload / plugin message : **`cantale:map`**.  
Corps : **JSON UTF-8** (sauf handshake client texte).  
Taille : le serveur pagine pour rester **sous ~32 Ko** (limite historique Bukkit). Le codec client lit **tous les octets restants** du buffer (capacité utile 64 Ko).  
Champ obligatoire serveur : `"v": 1` et `"type": "<…>"`.  
Config serveur : `map.client-mod.enabled` (défaut `true`). Si `false`, le canal n’est pas enregistré : le client reste en « Terrain seul ».

### 7.1 Handshake (C→S)

Le client **doit** envoyer le texte brut :

```
HELLO 1
```

Le serveur accepte aussi (non utilisé par le client actuel, mais figé côté pont) :

- `HELLO` seul → protocole 1 ;
- `HELLO <n>` ;
- JSON `{"v":1,"type":"hello","protocol":N}`.

Tout autre payload est **ignoré silencieusement** (pas d’erreur pour un vanilla qui parlerait sur le canal).

À la première réception valide : le joueur entre dans `moddedPlayers` ; log serveur `[MapBridge] <nom> utilise le mod client Cantale (protocole vN).`  
Protocole négocié : `min(client, PROTOCOL_VERSION serveur)` = 1 aujourd’hui.

Puis envoi **dans cet ordre** : `welcome` → `claims_reset` → pages `claims` → `markers`.

Retry client : second HELLO à 100 ticks si pas de `welcome`.

### 7.2 `welcome` (S→C) — **LIVRÉ**

```json
{
  "v": 1,
  "type": "welcome",
  "protocol": 1,
  "server": "Cantale",
  "worlds": [
    { "name": "world", "dim": "overworld" },
    { "name": "world_nether", "dim": "the_nether" },
    { "name": "world_the_end", "dim": "the_end" }
  ]
}
```

`dim` : `overworld` / `the_nether` / `the_end`, ou le nom Bukkit pour un environnement atypique.  
Le client indexe **`dim → name`**. Le monde courant côté UI est `world.getRegistryKey().getValue().getPath()` puis lookup (défaut : le path lui-même).

Effet client : `serverSupportsMod = true`, log info  
`[Cantale] Serveur Cantale détecté (protocole v{n}) — carte synchronisée.`

### 7.3 `claims_reset` (S→C) — **LIVRÉ**

```json
{ "v": 1, "type": "claims_reset" }
```

Vide **tous** les claims et pages en cours. Émis avant tout renvoi complet, et au **changement de monde** du joueur.

### 7.4 `claims` (S→C, paginé) — **LIVRÉ**

```json
{
  "v": 1,
  "type": "claims",
  "world": "world",
  "page": 0,
  "pages": 2,
  "chunks": [
    { "x": 12, "z": -5, "rel": "OWN" }
  ]
}
```

- `x`, `z` : indices de **chunk** (pas blocs).
- `rel` ∈ `OWN | ALLY | WAR | ENEMY | OTHER | PASDIC`.
- Pagination réelle du pont : **400 chunks par page** (`CHUNKS_PER_PAGE`). (La mention « 500 » dans `TODO.md` est obsolète vis-à-vis du code.)
- Groupement : **un flux de pages par monde Bukkit**.
- Le client **n’affiche** le monde que lorsque **toutes** les pages `0 .. pages-1` sont reçues (commit atomique). Trou de séquence : attente (pas d’état partiel).

### 7.5 `claim_add` / `claim_remove` (S→C) — **LIVRÉ**

```json
{ "v": 1, "type": "claim_add", "world": "world", "x": 13, "z": -5, "rel": "ALLY" }
{ "v": 1, "type": "claim_remove", "world": "world", "x": 13, "z": -5 }
```

Hooks : `FactionManager.addClaim` / `removeClaim`. Secret : un add/remove n’est pas envoyé aux non-membres.  
PASDIC on/off : ré-émission via `onClaimAdded` (donc `claim_add` avec nouveau `rel`).

### 7.6 `markers` (S→C) — **LIVRÉ**

```json
{
  "v": 1,
  "type": "markers",
  "warps": [
    { "name": "Spawn", "world": "world", "x": -67.0, "y": 65.0, "z": -144.0, "event": false }
  ],
  "homes": [
    { "name": "maison", "world": "world", "x": 10.0, "y": 70.0, "z": 20.0 }
  ],
  "fspawn": {
    "name": "LesDragons",
    "world": "world",
    "x": 0.0,
    "y": 80.0,
    "z": 0.0
  }
}
```

`fspawn` peut être JSON `null`. Les homes n’ont pas `event`. Les warps inactifs ou sans monde sont omis.

Remplace **toute** la liste warps/homes/fspawn côté client (pas de delta item).

Rafraîchi pour : création/suppression/(dés)activation warp (tous les clients moddés) ; home du joueur ; setspawn (membres de la faction) ; join/leave faction.

### 7.7 Événements serveur qui déclenchent un renvoi

| Événement | Messages |
|---|---|
| Handshake | welcome + claims_reset + claims + markers |
| Changement de monde joueur | claims_reset + claims |
| Diplomatie (guerre / paix / alliance) | claims_reset + claims (tous les moddés) |
| Mode secret on/off | claims_reset + claims (tous les moddés) |
| Join / leave / kick faction | claims_reset + claims + markers (ce joueur) |
| Claim add/remove / PASDIC | claim_add ou claim_remove (ciblé) |
| Warp / home / setspawn | markers |

Déconnexion : UUID retiré de `moddedPlayers`.

### 7.8 Règles d’or (figées)

- Échec d’envoi serveur : log `fine`, jamais d’exception vers le joueur.
- Client : file ≤ 512 ; surplus jeté (anti-flood).
- Messages `v` trop récente : ignorés, carte terrain toujours utilisable.
- Le client **n’envoie aucun** JSON métier en v1 (pas de demande de TP, pas de claim). Uniquement HELLO.

---

## 8. Contraintes

### 8.1 Client-only

- `fabric.mod.json` : `"environment": "client"`.
- Interdiction d’un jar serveur Fabric / d’un mixin serveur.
- Interdiction de modifier `config.yml` du plugin depuis ce livrable.

### 8.2 Anti-triche / pas de cheat

Le système **doit** se limiter à :

| Autorisé | Interdit |
|---|---|
| Afficher ce que le pont a envoyé à **ce** joueur | Inventer claims / warps d’autrui |
| `sendChatCommand("warp …" \| "home …" \| "f spawn")` | `tp`, `/minecraft:tp`, paquets de position, baritone, etc. |
| Waypoints locaux disque | Envoyer les waypoints au serveur sans protocole dédié (inexistant en v1) |
| Terrain des chunks **déjà chargés** par le client vanilla | Forcer le chargement de chunks, x-ray, cave map |

Les commandes restent soumises aux règles plugin (permissions, warmup, combat-tag, cooldown). Le mod **ne doit pas** contourner ces règles.

### 8.3 Performance

- Cache terrain par chunk ; refresh localisé (rayon 2, 10 s, toutes les 40 ticks).
- Repeinture texture carte seulement si dérive ≥ 16 px ou invalidation de version.
- Claims : parcours des chunks **visibles** dans le viewport, pas de tout le monde.
- File réseau bornée.
- Overlays in-world (§4.J) : budgets de distance ; pas d’entités.

### 8.4 Qualité / plateforme

- Java 21, Minecraft 1.21.11, Fabric 0.19.3, Fabric API `0.141.6+1.21.11`.
- Encodage UTF-8.
- **Interdiction de supprimer** un fichier ou dossier du dépôt (règle projet).
- Ne pas modifier les fichiers de configuration serveur sans demande explicite.
- i18n FR + EN pour toute chaîne joueur.
- `gradlew build` doit produire le JAR sans erreur.

### 8.5 Compatibilité

| Situation | Comportement obligatoire |
|---|---|
| Serveur sans pont | Terrain + waypoints perso ; badge « Terrain seul » |
| Pont `enabled: false` | Identique |
| Client vanilla sur Cantale | Aucun message `cantale:map` |
| Protocole serveur > client | Messages trop récents ignorés |
| Geyser / Bedrock | Hors produit |
| Changement de dimension | Mapping `welcome.worlds` ; claims renvoyés |
| Reload plugin | `shutdown()` désenregistre le canal ; le client peut renvoyer HELLO au retry si encore connecté (comportement best-effort) |

---

## 9. Critères d’acceptation (recette)

Chaque ligne est testable en jeu. **OK** = exigence déjà livrée à vérifier en non-régression. **KO actuel** = à implémenter puis retester.

### 9.1 Installation et handshake

| # | Test | Attendu | Statut recette |
|---|---|---|---|
| R01 | Fabric 1.21.11 + Fabric API + JAR dans `mods/` | Le jeu démarre ; log `[Cantale] Mod client initialisé` | OK à vérifier |
| R02 | Connexion serveur Cantale (pont on) | Log `[Cantale] Serveur Cantale détecté (protocole v1) — carte synchronisée.` | OK à vérifier |
| R03 | Log serveur | `[MapBridge] <joueur> utilise le mod client Cantale` | OK à vérifier |
| R04 | Client vanilla même serveur | Pas de log MapBridge pour ce joueur ; pas d’envoi | OK à vérifier |
| R05 | Serveur sans plugin | Carte « Terrain seul », aucun crash, aucun spam | OK à vérifier |
| R06 | `map.client-mod.enabled: false` | Identique à R05 | OK à vérifier |

### 9.2 Carte et minimap

| # | Test | Attendu | Statut recette |
|---|---|---|---|
| R10 | Touche M | Plein écran, centre = joueur, zoom 4 b/px | OK à vérifier |
| R11 | Réappui M / Échap | Ferme la carte | OK à vérifier |
| R12 | Le monde continue (mobs, jour) | Pas de pause | OK à vérifier |
| R13 | Drag gauche | Pan fluide | OK à vérifier |
| R14 | Molette sur un biome reconnaissable | Zoom ancré sur le curseur | OK à vérifier |
| R15 | Double-clic | Recentre joueur | OK à vérifier |
| R16 | Touche N | Minimap 120 px haut-droite on/off | OK à vérifier |
| R17 | Ouvrir M avec minimap on | Minimap disparaît, revient à la fermeture | OK à vérifier |
| R18 | F3 | Minimap absente | OK à vérifier |
| R19 | Rebind M/N dans Contrôles | Les nouvelles touches fonctionnent | OK à vérifier |

### 9.3 Terrain

| # | Test | Attendu | Statut recette |
|---|---|---|---|
| R20 | Overworld chargé | Herbe / eau teintées biome, relief ombré | OK à vérifier |
| R21 | Zone hors view distance | Gris `#26262E` | OK à vérifier |
| R22 | Poser un bloc près du joueur, attendre ≤ 10 s + déplacement minimap | Couleur mise à jour | OK à vérifier |
| R23 | Nether / End | Terrain de la dimension ; claims filtrés au monde mappé | OK à vérifier |

### 9.4 Claims

| # | Test | Attendu | Statut recette |
|---|---|---|---|
| R30 | Membre sur son claim | Vert OWN, **contour de zone** pas grille | OK à vérifier |
| R31 | Alliance | Bleu ALLY | OK à vérifier |
| R32 | Guerre mutuelle | Orange WAR | OK à vérifier |
| R33 | Neutre | Gris OTHER | OK à vérifier |
| R34 | Chunk PASDIC | Violet, contour vs voisins non-PASDIC | OK à vérifier |
| R35 | Faction secrète, compte non-membre | Claims **invisibles** | OK à vérifier |
| R36 | Compte membre de la secrète | Claims visibles OWN | OK à vérifier |
| R37 | `/f claim` / unclaim en live | Delta sans relog | OK à vérifier |
| R38 | Paix / guerre | Recolor sans relog | OK à vérifier |
| R39 | Décocher Claims | Overlay + légende absents | OK à vérifier |

### 9.5 Marqueurs et panneau

| # | Test | Attendu | Statut recette |
|---|---|---|---|
| R40 | Warps actifs | Pins cyan (or si event), liste, filtre | OK à vérifier |
| R41 | Homes du joueur | Pins verts, pas les homes d’autrui | OK à vérifier |
| R42 | Membre avec `/f setspawn` | Pin FW orange, commande `f spawn` | OK à vérifier |
| R43 | Sans faction / sans spawn | Pas de section FW | OK à vérifier |
| R44 | Clic nom dans la liste | Carte centrée sur le marqueur | OK à vérifier |
| R45 | Clic droit carte | Overlay nom, waypoint créé au point visé | OK à vérifier |
| R46 | + Waypoint | Création au centre de vue | OK à vérifier |
| R47 | Relancer le client | Filtres + waypoints + cases individuelles restaurés | OK à vérifier |
| R48 | Décocher un warp | Pin et ligne masqués (ligne : case off, pin absent) | OK à vérifier |

### 9.6 Téléportation (cible figée)

| # | Test | Attendu | Statut recette |
|---|---|---|---|
| R50 | Clic pin warp | **Dialogue Oui/Non**, aucune commande tant que Non | **À FAIRE** |
| R51 | Oui sur warp `Spawn` | Envoie `warp Spawn` ; le plugin téléporte selon ses règles | **À FAIRE** (aujourd’hui sans dialogue) |
| R52 | Non / Échap | Reste sur la carte, pas de TP | **À FAIRE** |
| R53 | TP home / FW | Idem avec `home <nom>` / `f spawn` | **À FAIRE** (dialogue) |
| R54 | Clic waypoint perso | Pas de TP | OK à vérifier |
| R55 | Packet sniff / log | Aucun paquet de position client-initié hors chat commande | OK à vérifier |

### 9.7 Menu waypoint (cible)

| # | Test | Attendu | Statut recette |
|---|---|---|---|
| R60 | Clic droit pin perso | Menu rename / couleur / hide / supprimer | **À FAIRE** |
| R61 | Renommer | Nom mis à jour carte + JSON | **À FAIRE** |
| R62 | Couleur | Pin + JSON mis à jour | **À FAIRE** |
| R63 | Supprimer | Confirmation puis disparition | **À FAIRE** |

### 9.8 Flèche joueur (cible « soignée »)

| # | Test | Attendu | Statut recette |
|---|---|---|---|
| R70 | Tourner sur place, carte ouverte | Flèche suit le regard, nord en haut | PARTIEL (8 dir 5×5) |
| R71 | Lisibilité sur neige / claim vert | Contour visible, taille ≥ 9 px | **À FAIRE** |
| R72 | Minimap | Même flèche | PARTIEL |

### 9.9 In-world JourneyMap (cible)

| # | Test | Attendu | Statut recette |
|---|---|---|---|
| R80 | WP perso dans le même monde, carte fermée | Faisceau coloré à la position | **À FAIRE** |
| R81 | Approcher | Icône + nom + distance | **À FAIRE** |
| R82 | Marqueur derrière le joueur | Pastille au bord d’écran, direction + distance | **À FAIRE** |
| R83 | Décocher la catégorie | Faisceau + HUD + bord disparaissent | **À FAIRE** |
| R84 | Autre dimension | Rien pour les marqueurs de l’overworld | **À FAIRE** |
| R85 | F3 | Overlays Cantale masqués | **À FAIRE** |

### 9.10 Non-régression technique

| # | Test | Attendu | Statut recette |
|---|---|---|---|
| R90 | `./gradlew build` dans `ClientMod/` | JAR `build/libs/cantale-client-1.0.0.jar` | OK à vérifier |
| R91 | Connexion (codec) | Pas de kick / crash Netty (payload sans VarInt) | OK à vérifier |
| R92 | JSON tronqué / type inconnu | Ignore, log debug | OK à vérifier |
| R93 | Changement de monde | Claims du nouvel overworld/nether cohérents | OK à vérifier |
| R94 | 400+ chunks claimés | Plusieurs pages, pas d’affichage partiel, pas de dépassement 32 Ko | OK à vérifier |

---

## 10. Hors scope et évolutions futures

### 10.1 Hors scope figé (v1)

- Forge / NeoForge / Quilt / Fabric versions ≠ 1.21.11.
- Réactivation `/carte` in-game (tuiles map vanilla).
- Claim / unclaim / PASDIC / RTP au clic (c’était la map plugin dormante).
- Features staff dans le mod (vanish, voir toutes les factions secrètes, TP admin).
- Clients Bedrock.
- Partage de waypoints entre joueurs ou vers le serveur.
- Cave map, mobs, chunks non chargés « explorés une fois » style JourneyMap full.
- Zoom / drag / clic sur la minimap.
- Sélecteur multi-mondes dans l’UI (la carte montre **la dimension courante** ; les autres mondes restent en mémoire claims mais ne s’affichent pas tant qu’on n’y est pas).
- Radar joueurs / entités (cheat-adjacent).
- Modification du protocole au-delà de v1 sans versionnement (`v: 2`).

### 10.2 Évolutions possibles (non exigées)

- Persistance on/off de la minimap et position/taille.
- Sélecteur de monde / dimension dans le panneau.
- Import / export JSON de waypoints ; partage party/faction.
- Clic sur indicateur de bord → ouvrir la carte centrée.
- Distance 3D vs horizontale, réglage utilisateur.
- Groupement visuel des pins superposés.
- `ENEMY` réellement émis si diplomatie unilatérale un jour.
- Pagination claims : le TODO historique citait 500/page ; le code est à 400 — toute hausse doit rester &lt; 32 Ko.

---

## 11. Livrable

| Élément | Exigence |
|---|---|
| Artefact | `ClientMod/build/libs/cantale-client-1.0.0.jar` (`archives_base_name` + `mod_version`) |
| Build | `cd ClientMod && ./gradlew build` (JDK 21+) |
| Installation joueur | 1) Fabric Loader **1.21.11** 2) `mods/cantale-client-1.0.0.jar` 3) `mods/` **Fabric API** `0.141.6+1.21.11` (ou compatible `fabric-api: *` déclaré, version de référence = gradle.properties) |
| Serveur | Plugin Cantale avec `map.client-mod.enabled: true` (défaut) ; Paper 1.21.11 |
| Distribution | Modpack / launcher du serveur (voir TODO « Distribution ») — hors compilation du JAR |
| Documentation associée | Ce fichier ; `ClientMod/README.md` (vue joueur courte) |

Le JAR sources / `-dev` Loom ne sont **pas** le livrable joueur.

---

## 12. Matrice d’exigences (vue d’ensemble)

| ID | Thème | Statut |
|---|---|---|
| 4.A | Carte plein écran M | LIVRÉ (+ ignore M si modal : À FAIRE) |
| 4.B | Minimap N | LIVRÉ |
| 4.C | Terrain + cache | LIVRÉ |
| 4.D | Claims contours par relation | LIVRÉ |
| 4.E | Warps / homes / FW / WP perso (carte 2D) | LIVRÉ |
| 4.F | Filtres persistés | LIVRÉ |
| 4.G | Pan, zoom curseur, clic droit création | LIVRÉ |
| 4.G | Confirmation TP Oui/Non | **À FAIRE** |
| 4.H | Menu rename / couleur / hide / supprimer | **À FAIRE** |
| 4.I | Flèche joueur soignée | **PARTIEL** |
| 4.J | Waypoints in-world (beam, HUD, distance, bords) | **À FAIRE** |
| 4.K | Handshake + protocole v1 | LIVRÉ |
| 3 | Pas de feature staff client | LIVRÉ (aucune à ajouter) |
| 8 | Client-only, anti-cheat, Java 21 | LIVRÉ (à préserver) |

---

*Fin du cahier des charges. Toute implémentation ultérieure doit se conformer à ce document ; en cas d’écart volontaire, mettre à jour ce CDC avant le code.*
