# TODO Cantale

## Ajouts & Correctifs Plugin (fin août 2026)
- [x] **`/fw` warps de faction publics (fusion check + local)** : commande top-level `/fw` (aliases `/fwarp` `/fwarps`). Live = table historique `faction_warps` (données existantes, quota pouvoir, create dans un claim, UI faction, permission Officier+ `FWARP`, noms uniques globaux). `/fw` liste serveur, `/fw <nom>` TP public (warmup `/warp`), `/fw create` / `/fw delete`. Alias `/f setwarp` / `/f warp` / `/f delwarp` conservés. Spawn fac (`/f setspawn`) non touché. Le commit `check` apportait une 2e table `fwarps` (sans quota ni claim) — classes conservées, non branchées sur `/fw`.
- [x] **Shop Update (Loots de Mobs)** : Ajout de la chair putréfiée, slimeball, œil d'araignée, crème de magma, larme de ghast, etc. dans `MOB_DROPS`. SAND et GUNPOWDER restent absents (anti-farm TNT).
- [x] **Fix Repas en Claim Ennemi** : consommation de nourriture (Pomme Dorée, Steak, Potion…) tout en ciblant un bloc dans un claim ennemi.
- [x] **Cap Global 3 Vies & BossBar** : limite stricte à 3 vies ingérées, restitution des excédents à la connexion.
- [x] **Blocage Chorus Fruit in Claims** : interdiction des TP Chorus vers ou depuis un claim ennemi/protégé.
- [x] **Mode Combattant (`/combattant <Joueur>`)** : sauvegarde/restitution d'inventaire, bypass PvP, blocage `/pc` `/ec` `/tp`, logs `logs/combattant`.
- [x] **Moderation Lock (`/lock <Joueur>`)** : gel complet (immobilité, TP/interactions/commandes, persistance).
- [x] **Admin Inv (`/admininv <Joueur>`)** : inspecter/éditer l'inventaire d'un joueur connecté.

## Phase 1 — sécurité, HikariCP, économie, EULA, factions (2026-11-07)

- [x] **1.1 Secrets** : `config.yml` ne contient plus les identifiants réels (MySQL, token bot, secret OAuth, tokens de vote). Lecture prioritaire de `CANTALE_DB_HOST`, `CANTALE_DB_PORT`, `CANTALE_DB_NAME`, `CANTALE_DB_USER`, `CANTALE_DB_PASS`, `CANTALE_DISCORD_TOKEN`, `CANTALE_DISCORD_CLIENT_SECRET`, `CANTALE_SUPABASE_SERVICE_KEY`, `CANTALE_VOTE_SERVEURLISTE_TOKEN`, `CANTALE_VOTE_SERVEURPRIVE_TOKEN`, `CANTALE_REDIS_PASSWORD`. Repli sur le YAML si la variable est absente. Placeholder `${CANTALE_...}` = valeur absente, log sans secret, pas de crash.
- [x] **1.2 HikariCP 5.1.0** : pool à la place du proxy JDBC. MySQL 10 connexions (borné 10–20 via `database.mysql.pool-size`), fuite 5 s, vie max 30 min, timeout 10 s. SQLite : 1 connexion (écritures fichier). `close()` ferme le pool au `onDisable`. Les contrôles `getConnection() != null` qui ne rendaient pas la connexion passent par `isConnected()`.
- [x] **1.3 Économie** : `UPDATE players SET balance = balance + ?` avec refus si le solde deviendrait négatif. Banque de faction : même `UPDATE` atomique (`balance = balance + ?`, garde anti-négatif) dans la même transaction que le solde joueur. Achat HDV verrouillé (un seul gagnant, surplus d'inventaire déposé au sol). Items `/trade` hors ligne dans `trade_pending_returns` : claim `pending` → `delivering`, don, accusé `handed_off`, puis suppression. Un crash après le don ne redonne pas ; un don qui n'a pas eu lieu est réessayé. Rechargés au démarrage et au login.
- [x] **1.4 EULA** : `canStealInClaims()` et `hasUnlimitedFlight()` retournent toujours false (`@Deprecated`). Plus de vol ni de pillage de claim lié au grade. `/daily` : base 1 000 + Aventurier 500, VIP 1 500, Chèvre 2 500 (totaux 1 500 / 2 500 / 3 500). L'ancien bonus Chèvre de 100 000 a été retiré : il valait dix vies ou deux claims PASDIC par jour, au-dessus du solde de départ (10 000) et des votes (100–1 500). Le message de ban n'invite plus à acheter une vie.
- [x] **1.5 Factions** : `min-members-create` = 2 (YAML et défaut Java). `/f create` exige 2 joueurs sans faction dans un rayon de 32 blocs, créateur inclus. Pendant un raid actif, `ClaimListener` laisse les assaillants (`isAttackingInActiveRaid`) blesser les défenseurs dans leur claim. Une guerre déclarée sans raid n'ouvre pas ce coup (la casse/pose en guerre reste). Hors raid, la protection reste. PASDIC bloque le PvP avant cette exception.
- [ ] **Ops** : faire tourner les mots de passe et tokens qui étaient dans le dépôt, puis les poser dans l'environnement du serveur. Le `config.yml` déjà copié dans `plugins/CantalePlugin/` n'est pas fusionné par `saveDefaultConfig()` : y remplacer les secrets et passer `min-members-create` à 2, sinon l'ancienne valeur 8 reste en vigueur.
- [x] **4.1 Velocity, Redis central, base partagée** : mode proxy éteint tant que `CANTALE_VELOCITY_SECRET` est absent. S'il est défini, Paper doit avoir le forwarding proxy (`paper-global.yml`) ; sinon chaque login est refusé, sans contournement et sans écrire le secret. Identité `CANTALE_SERVER_ID` (défaut `paper-1`) sur `cantale:chat:*` et `cantale:admin:actions` : un nœud ignore son propre écho admin et affiche le chat d'un autre nœud sans le republier. Le site, sans server-id, reste relayé. Un même daily, un même KOTH et une même action admin `pending` ne sont crédités qu'une fois (UPDATE conditionnel, pas un second pool). Exemple `proxy/velocity.toml.example` sans secret.
- [x] **4.2 i18n** : `messages_fr.yml` / `messages_en.yml`, `MessageService`, colonne `players.lang` (défaut `fr`), `/lang fr|en`. Repli français puis la clé. Textes joueur migrés : daily, ban, vies, création / renommage / revive / métier / vulnérabilité / bannière de siège, claims et sanctuaire, KOTH, pay, balance, HDV, trade. Écarts encore en dur : le reste de `/f` (invite, banque, guerre, aide hors les quatre lignes de phase), caisses, shop, Discord, anticheat, votes, quêtes (la ligne « quête terminée »), modération, téléportations, tags, events, titres de GUI, libellés de métiers (`Skill.label()`), chrome `[Web]`.
- [x] **4.3 Pass cosmétique** : tables saison / paliers / progression / cosmétiques. XP de quête solo (+40) et de `/daily` réussi (+25). Paliers 1–6 : titres, auras, capes. `/pass equip` revérifie la possession. Aucun Cantox, aucune vie, aucun pouvoir, aucun vol, aucun claim. Paquet serveur → client `cosmetics` sur `cantale:map` (le client n'émet toujours que `hello` / `inspect`).
- [ ] **Ops phase 4** : si `plugins/CantalePlugin/config.yml` existe déjà, y copier les blocs `network` et `redis` (`saveDefaultConfig` ne fusionne pas). Le secret Velocity reste dans l'environnement et dans `forwarding.secret` / `paper-global.yml`, hors dépôt. `CANTALE_SERVER_ID` doit être unique par nœud.

## Phase 3 — mondes, sièges, métiers (2026-11-07)

- [x] **3.1 Mondes** : `world_hub` (sanctuaire, pas de PvP ni de faim), `world_factions` (citadelles, claims), `world_resources` (aucun claim, filons reposés toutes les 6 h sur les blocs naturels, camp central épargné), `world_warzone` (aucun claim, PvP forcé, gisements rares toutes les 12 h, KOTH 3 min, +400 Cantox de banque, 4 prises/faction/jour, 45 min entre deux). Création au démarrage sans effacer un monde déjà là. Relief amplifié pour citadelles et warzone (pas de datapack versionné dans le dépôt). `/claim` refusé hors `world_factions` et l'overworld historique `world`. `/spawn` va au sanctuaire. `/rtp` ne dépose pas en warzone depuis un autre monde. Warp, home, tpa et spawn de faction sont des destinations choisies.
- [x] **3.2 Sièges** : bannière (`/f banniere`) posée par un officier dans un claim ennemi. Son serveur + Discord (log explicite si le bot ou le salon factions manque). Créneau par faction, défaut 18:00–22:00 (`/f vuln`). Hors créneau, le raid ne démarre pas et le grief de siège ne passe pas. Un seul siège actif par attaquant et par défenseur, y compris tant que la régénération n'est pas finie. Blocs cassés ou posés dans `siege_block_changes`. 30 min après la fin, restauration progressive. Un redémarrage reprend la file. Le PvP de claim pendant le siège actif (dans le créneau) reprend la règle du raid. Une guerre seule ne l'ouvre pas. PASDIC bloque avant.
- [x] **3.3 Métiers** : un choix par membre (`/f metier`), table `player_skills`. Premier choix gratuit, puis 2 500 Cantox (débit atomique) et 6 h, refusé en combat. Plafond 5 membres. Architecte : jusqu'à 4 tentatives pour casser un bloc de siège. Négociant : jusqu'à −20 % sur la taxe de territoire et l'achat de pouvoir (pas de monnaie créée). Artisan : enclume jamais sous 1 niveau, potions +15 s par artisan jusqu'à +60 s. Chevalier : en `world_warzone` seulement, +3 %/membre jusqu'à +15 % infligés, −2 %/membre jusqu'à −10 % reçus.

## Phase 2 — Redis, spawners async, vies 2.0 (2026-11-07)

- [x] **2.1 Redis Pub/Sub** : worker Jedis sur `cantale:chat:web2mc`, `cantale:chat:mc2web`, `cantale:admin:actions`. Le chat web et les actions admin arrivent par message Redis (thread principal pour Bukkit). Plus de SELECT toutes les 2 s sur `web_chat_outbox` / `web_admin_actions`. Les INSERT / UPDATE d'archivage restent. Redis absent : log explicite, pas de crash, pas de polling SQL de secours. Abonnement fermé dans `onDisable` avant le pool. Le site publie après l'INSERT (`CANTALE_REDIS_HOST` / `PORT` / `PASSWORD`, aucun secret dans les logs).
- [x] **2.2 HUD Fabric** : HUD vies, barre de combat tag et boussole de territoire (`StatusHud`). Clic sur un claim : tooltip nom, chef, relation, puis fiche revalidée par `claim_info`. Le client n'émet que `hello` / `inspect` ; tout autre type est ignoré. `CantalePlugin` appelle `ClientMapBridge.register()` (carte + HUD). Vies en cache 5 s. Textes reconstruits seulement quand la valeur change.
- [x] **2.3 SpawnerManager** : itération, cooldowns et chunks chargés (copie `monde|chunkX|chunkZ`) hors du thread principal. `spawnEntity` et les lectures de blocs sont regroupés sur le thread principal. La tâche est annulée au disable ; un compteur de génération ignore un tick ou un reload en cours. Mêmes conditions et cooldowns qu'avant.
- [x] **2.4 Vies 2.0** : 0 vie = ban profil 2 h, plus de ban IP. `/f revive <joueur>` (officier ou chef, même faction) coûte 15 de pouvoir, débit `UPDATE factions SET power = power - ? WHERE power >= ?`. Le joueur revient avec 1 vie ; un second appel ne la duplique pas. Butin de mort protégé 5 min pour son propriétaire (même clé `death_loot_until`). Pas de message « achetez une vie ».

- [ ] **Ops phase 3** : copier le bloc `siege` dans `plugins/CantalePlugin/config.yml` si le fichier live existe déjà (`saveDefaultConfig` ne fusionne pas). Les défauts Java restent 20 min / 30 min / 18:00–22:00.

## Collections : kills de mobs empilés (2026-08-18)
- [x] **Cause** : un coup létal sur un stack est absorbé (`tryAbsorbLethalHit`) — l'entité
  ne meurt pas, `EntityDeathEvent` ne part pas, Minecraft n'incrémente pas `KILL_ENTITY`.
  Collections et quêtes n'écoutaient que la vraie mort → 0 progrès en farm stackée.
- [x] **Fix** : à chaque absorption, crédit collections + quêtes + stats vanilla
  (`KILL_ENTITY` / `MOB_KILLS`). `/collections` ré-aligne aussi la catégorie mobs sur les
  stats vanilla (max avec le compteur plugin) pour rattraper l'historique des kills
  non-stackés. Dernier du stack = vraie mort, chemin `EntityDeathEvent` inchangé (pas de
  double compte).
- [ ] Déployer le JAR. Tester : tuer 10 vaches d'un stack ×12 → collection Vache +10
  (plus le 11e si tu continues) ; quête KILL_MOB vache avance ; `/collections` après
  des kills vanilla hors stack affiche au moins le chiffre des stats Minecraft.

## Clefs, spawners, shop sable/gunpowder, Cantalame (2026-08-18)
- [x] **Exploit clefs** : shift-clic droit / fermeture du GUI du Trésor Public délivrait
  la récompense immédiatement et levait le verrou `openingPlayers` → spam de toute une
  stack sans animation. Fix : le spin continue en arrière-plan, récompense au délai prévu ;
  `denyVanillaUse` (block + item DENY) pour empêcher l'insertion d'une stack dans le coffre.
- [x] **Spawners** : pool élargi (19 rare / 19 épique / 7 mythique). Ours, gardien, husk,
  piglins, hoglins, etc. Ghast et shulker restent non-obtenables (BDD → magma). Cadence ×2
  pour golem, ravageur, grand gardien, évocateurs, piglin brute.
- [x] **Shop** : SAND et GUNPOWDER retirés de `shop.yml` + blacklist code (le fichier live
  du serveur ne peut plus les vendre même sans copie du yml).
- [x] **Cantalame** : plus enchantable à la table / enclume (composant `enchantable` retiré,
  PrepareItemEnchant + EnchantItem + contrôle des *entrées* d'enclume). Les paliers de kills
  continuent d'ajouter les enchantements en code.
- [ ] Déployer le JAR (et recopier `shop.yml` / `faq.yml` si tu veux le disque à jour —
  la blacklist code suffit pour le shop). Tester : stack de clefs + shift-clic droit sur le
  coffre spawn = 1 ouverture + animation obligatoire ; Cantalame + livre à l'enclume = refus ;
  `/shop` sans sable ni poudre ; `/spawner give` husk/guardian/polar_bear/piglin/hoglin.

## Refonte complète du système `/trade` (2026-08-18)
- [x] **Problème** : `/trade` était un placeholder inutilisable — trade démarré d'office sans
  demande, aucune UI (juste du texte chat), **impossible de déposer des items** (`addItem` jamais
  appelé), sessions fantômes à la déconnexion (joueurs bloqués jusqu'au reboot), items perdus si
  inventaire plein, ordre deposit/withdraw bancal, `cleanupExpired` jamais schedulé.
- [x] **Refonte** (flux demandé) : `/trade <joueur>` envoie une **demande** (60 s, boutons
  cliquables [Accepter]/[Refuser] dans le chat) → acceptation → **GUI d'échange** 6 lignes
  (zone d'offre locale 4×6 à gauche, offre adverse en miroir à droite, colonne centrale :
  argent local/distant, statut, confirmer, annuler) → dépôt d'items par clic curseur ou
  shift-clic → **bouton lingot d'or** : saisie du montant dans le chat (plafonné au solde,
  « annuler »/30 s de timeout, `/trade money <montant>` conservé) → **confirmation des deux
  côtés** → **compte à rebours 5 s** (actionbar) → échange.
- [x] **Sécurités anti-arnaque/duplication** : toute modification d'offre réinitialise les deux
  confirmations et le countdown ; un joueur confirmé ne peut plus toucher son offre ; les offres
  vivent dans la session (jamais dans l'inventaire du GUI, re-rendu à chaque changement) ;
  double-clic/drag/shift-clic neutralisés ; fermeture du GUI (ÉCHAP) ou déconnexion = annulation
  avec retour des items (drop au sol si inventaire plein, restitution au prochain login si hors
  ligne) ; argent prélevé uniquement à la complétion après re-vérification des soldes ; timeout
  d'inactivité 3 min ; log console de chaque échange complété.
- [x] **Fichiers** : `trade/TradeSession.java` (refonte), `trade/TradeManager.java` (refonte),
  `trade/TradeListener.java` (nouveau : chat/close/quit/join),
  `ui/inventory/TradeSessionUI.java` (nouveau GUI), `ui/inventory/TradeUI.java` (menu adapté),
  `commands/TradeCommand.java` (refonte + TabComplete joueurs), `CantalePlugin.java`
  (enregistrement listener). Docs : COMMANDS.md, docs/Commands.md, wiki (2 sections).
- [x] Compilation OK (JAR 21,8 Mo).
- [ ] Déployer le JAR + redeploy le site wiki. Tester en jeu à deux comptes : demande → accept →
  dépôt items des deux côtés + argent → confirmations → countdown 5 s → échange ; puis cas
  d'annulation (ÉCHAP, déconnexion, modification après confirmation, solde insuffisant).

## Homes anti-intrusion (modos inclus) + `/adminhome delete` (2026-08-18)
- [x] **Pose de home interdite dans les claims étrangers** : `HomeManager.canSetHomeHere` — pose
  autorisée uniquement en wilderness ou dans le claim de sa propre faction ; exemptions réservées
  à op / `cantale.admin` / `AdminBypassManager` (`/f bypass`). Les modos suivent la norme joueur
  (plus de backdoor de raid via `/home set` en territoire ennemi).
- [x] **`/adminhome delete <joueur> <nom>`** (aliases del/remove) : supprime le home d'un joueur
  **en ligne ou hors ligne** (OfflinePlayer + hasPlayedBefore), log console, refresh carte si la
  cible est en ligne. Syntaxe staff complète : `check <joueur>` · `tp <joueur> <nom>` (forme
  courte `<joueur> <nom>` conservée) · `delete <joueur> <nom>`. TabComplete sur les noms de homes.
- [x] `/admin deletehome` délègue désormais à la même méthode (fonctionne hors ligne).
- [x] Docs : plugin.yml, /modhelp, COMMANDS.md, docs/Commands.md, wiki (adminhome delete + règle
  claim dans « Téléportation » + limites de homes corrigées : Joueur 5 · Aventurier 8 · VIP 10 ·
  Chèvre ∞ · Modo 5 · Admin/Owner ∞).
- [x] Compilation OK (JAR 21,7 Mo).
- [ ] Déployer le JAR + redeploy le site wiki. Vérifier en jeu : `/home set` dans claim étranger →
  refus (modo inclus) ; `/adminhome delete Yugo kiwifield` hors ligne → supprimé.

## Modo sans avantages VIP (2026-08-18)
- [x] **Cause** : `PlayerRank.MODERATOR` cumulait des avantages de grades payants — PC 54 slots +
  PC2/PC3 (`pcSlots`/`pc2Slots` 54, `getPrivateChestCount` = 3, `getPrivateChestSize` = 54),
  `/ah` 20 ventes, `/feed` (via `isAtLeastVIP`), `hasSecondChest`, `hasNoTpCooldown`.
- [x] **Fix** : le Modo est désormais traité comme un joueur **sans grade** pour les avantages :
  1 coffre privé de 27 slots (PC2/PC3 inaccessibles), `/ah` 3 ventes, plus de `/feed` ni avantages
  VIP (`isAtLeastVIP`/`hasSecondChest`/`hasNoTpCooldown` = grades payants + Admin/Owner seulement).
  Homes déjà à la norme joueur depuis le fix précédent. Outils de modération inchangés
  (`cantale.moderator` via `RankPermissionBridge`, `/moderation`, `/vanish`, `/adminpc`…).
  Admin/Owner conservent tous les avantages staff.
- [x] **Données préservées** : PC2/PC3 des modos restent en BDD (inaccessibles, pas wipés,
  récupérables via `/adminpc` ou si le joueur obtient un grade) ; un PC1 54 slots rouvert en 27
  dépose l'overflow dans l'inventaire/au sol (mécanisme `collectOverflow` existant).
- [x] Wiki `grades-permissions` mis à jour (Modo = aucun avantage payant ; Admin/Owner = tous).
- [ ] Déployer le JAR + redeploy le site wiki. Vérifier en jeu : un modo fait `/pc` (27 slots),
  `/pc2` (refus boutique), `/feed` (refus), `/ah` au-delà de 3 ventes (refus) ; `/rank info` d'un
  modo affiche les nouvelles valeurs.

## Hotfix — items au sol > 99 perdus à la sauvegarde (2026-08-18)
- [x] **Cause** : `ItemMergeManager` fusionnait les items au sol jusqu'à `max-stack: 512`,
  or le codec vanilla borne le count d'un ItemStack à **[1;99]** depuis les data components →
  Moonrise loguait « Failed to encode value … Value must be within range [1;99] » et
  **l'entité n'était jamais écrite sur disque** (loot perdu au chunk unload / redémarrage).
- [x] **Fix** : plafond clampé en dur à 99 dans le code (`MAX_SERIALIZED_STACK`, défaut 512 → 99) —
  la clé live `optimization.item-merge.max-stack: 512` est ignorée sans devoir toucher au
  config.yml ; réparation des entités oversized déjà en mémoire : re-découpées en stacks ≤ 99
  à la même position (balayage 15 s + fusion au drop), log console par entité réparée.
- [ ] Déployer le JAR. Vérifier : fin des warnings `ChunkEntitySlices` au déchargement des
  chunks de farm, items fusionnés plafonnés à 99, pickup remplit plusieurs stacks.

## Protection warps publics + commande `/fw` (août 2026)
- [x] **Zone protégée 16x16 chunks autour des warps publics** : `WarpManager.getProtectedWarpAt` /
  `isProtectedZone` (carré de 16 chunks de côté, de −8 à +7 chunks autour du chunk du warp, warps
  actifs seulement) + nouveau `WarpProtectionListener` : casse, pose, interactions blocs/entités
  (item frames, armor stands, tableaux), seaux, pose d'entités et explosions bloqués ; message
  « §eVous entrez dans une §6zone protégée§e. » / « §eVous quittez la zone protégée. » au
  changement de chunk (anti-spam 2 s, comme les claims). Exemptions : op / `cantale.admin` /
  `/f bypass`. PvP et mobs non touchés. **Les FW de faction n'ont pas cette zone** : seule
  la protection de claims s'applique (d'où l'obligation de créer le FW dans un claim).
- [x] **`/fw` live = `faction_warps`** (pas la table `fwarps` du commit `check`) : quota pouvoir,
  create Officier+ dans un claim, TP public, UI faction, alias `/f setwarp`. Classes `FWarp*`
  du commit `check` conservées (table `fwarps` toujours créée) mais `/fw` est `FwCommand`.
- [x] **Carte du mod client** : les FW `faction_warps` sont envoyés dans les marqueurs `warps`
  avec `faction: true` ; refresh à la création/suppression.
- [ ] Déployer le JAR. Vérifier en jeu : zone 16×16 des warps admin, `/fw create` (officier dans
  un claim), quota, `/fw <nom>` pour un joueur d'une autre faction, UI `/f` → Warps.

## Correctifs Plugin & Site Web (août 2026)
- [x] **Compteur joueurs en ligne site web** : initialisation avec état et affichage fluide (fallback 0).
- [x] **Liste des factions site web** : suppression du filtre parasite `"O"` dans `public-ranking-exclusions.ts` et `publicRankingExclusions.ts` (maintien de `Ø` / `∅`), et augmentation de la limite SQL à 500.
- [x] **Commande `/f secret` & Protection Automatique** : suppression de `/f secret` ; `isSecret()` calculé automatiquement (3 jours post-création + 24h post-raid).
- [x] **Runes de fortification** : désactivation de l'utilisation et des tirages dans les caisses.
- [x] **Commande `/f protect`** : scan du chunk pour interdire la protection si le claim contient du stockage (coffres, barrels, etc.).
- [x] **Commande `/raid`** : création de la commande autonome `/raid` (officiers+) ; alertes in-game (Chat/Title) et Discord aux défenseurs ; autorisations de poser/allumer la TNT et de poser de l'obsidienne durant le raid pour toute la faction attaquante.
- [x] **Interface quêtes (`/quests`)** : restructuration en 2 catégories principales (Quêtes Solo vs Quêtes de Faction) puis 3 périodes (Journalières, Hebdomadaires, Saisonnières).
- [x] **`/rtp` Nether** : scan des cavernes du Nether sous la bedrock ($Y \le 120$) pour éliminer les téléportations sur le toit du Nether.
- [x] **Cantalame** : évolution à 100 kills au total, avec plafond anti-farm de 3 kills max par victime et par jour civil.
- [x] **`/givevie`** : don d'1 vie depuis le stock illimité pour les administrateurs (op / `cantale.admin`).

## Factions secrètes sur le site (août 2026)
- [x] **Cause** : le site excluait la faction secrète ENTIÈRE (annuaire, page publique,
     roster, profils joueurs) via le filtre SQL `secret_until` dans
     `Site-Cantale/web/src/server/repo/factions.ts` (4 requêtes), alors que le comportement
     voulu = celui du jeu (/f list, /f info : faction visible, claims cachés).
- [x] **Fix site Next.js** : la faction `/f secret` reste visible partout (annuaire
     /factions, page, roster, profils, classements) ; seuls ses claims sont exclus de la
     carte web — filtre conservé dans `web/src/server/repo/map.ts` (/carte +
     /api/map/claims). Bonus : le leader d'une faction secrète peut à nouveau la gérer
     via le site (faction-guard la retrouve). Typecheck `tsc --noEmit` vert.
- [x] **Fix backend legacy Express** (`Site-Cantale/backend`) : `listFactions` + classement
     factions n'excluent plus les secrètes ; `GET /api/factions/:id` ne renvoie plus les
     coordonnées de claims d'une faction secrète (fuite de position corrigée, filtre SQL
     dans `getFactionClaims`). Typecheck vert.
- [x] **En jeu : inchangé** (déjà correct — /f map chat `FactionCommand`, map GUI
     `MapImageBuilder`, bridge mod `ClientMapBridge` filtraient déjà les claims pour les
     non-membres ; revérifié). Plugin Java non modifié → pas de rebuild nécessaire.
- [x] **Doc contrat** : `docs/SiteIntegration.md` (pipeline, sémantique secret, SQL de
     référence). Propagation : `/f secret` écrit `secret_until` immédiatement en BDD →
     visible côté site sous ~30 s (cache), sans reboot.
- [ ] **Vérif déploiement (utilisateur)** : redéployer le site Vercel (et
     `pm2 restart cantale-api` si le backend legacy tourne encore), puis vérifier qu'une
     faction secrète réapparaît dans /factions et sa page, que ses claims restent absents
     de /carte, et que la bascule `/f secret` se répercute sous ~30 s.

## Retrait map in-game + outils custom en utilisations (août 2026)
- [x] **Map in-game (tuiles FILLED_MAP) retirée du jeu, code conservé dormant** :
      commande `carte` (+ aliases `map`/`worldmap`) supprimée du `plugin.yml` et plus
      enregistrée dans `CantalePlugin` ; `MapUI` n'est instancié/démarré que si
      `map.enabled: true` (défaut passé à **false** dans `config.yml` ET dans le code) ;
      `MinimapHotkeyListener` (clic droit / F) n'est enregistré que si le module est actif ;
      log boot sobre `[Map] Module in-game désactivé (carte fournie par le mod client).` ;
      `onDisable` déjà null-safe. Intouchés : `ui/map/*` (dormant), `/f map` (carte chat
      faction, feature distincte), le bridge mod client `cantale:map` (toujours actif).
      `COMMANDS.md` nettoyé (`/carte`, `/carte mini`, raccourci minimap retirés).
- [x] **Outils custom en utilisations (Pickantaxe / Cantaxe / Multi-Cantool)** : compteur
      PDC `uses_left` (Fer 1000 / Diamant 2000 / Netherite 4000) posé à la création ;
      1 utilisation = 1 coup (BlockBreakEvent), quelle que soit la zone 3×3/5×5/7×7 ou
      l'arbre entier ; durabilité vanilla neutralisée (`PlayerItemDamageEvent` annulé,
      barre toujours pleine) ; lore vivante « Utilisations restantes : N » maj à chaque
      coup (Solidité/Unbreaking retiré des nouveaux outils) ; à 0 l'outil mine son dernier
      coup PUIS se brise (son `ITEM_BREAK` + particules + message). Migration douce : un
      outil legacy sans compteur démarre à son max à la 1re utilisation (lore nettoyée).
      Cantalame (incassable) et armure du Garde (durabilité combat vanilla) hors scope.
      `faq.yml` mis à jour (plus de mention Solidité/durabilité pour ces outils).
- [ ] **Déploiement** : redeploy le JAR. Note : les outils déjà distribués passent
      automatiquement en utilisations à leur 1re utilisation (compteur initialisé au max,
      aucune perte pour les joueurs).

## Mod client Cantale (vraie carte) — août 2026
- [x] **Bridge serveur** (`fr.cantale.plugin.map.ClientMapBridge`) : canal plugin `cantale:map`
      (out + in pour le handshake), handshake `HELLO <version>` → envoi de l'état initial
      (welcome + claims paginés + markers) uniquement aux clients moddés ; vanilla ignoré.
      Claims filtrés par joueur (factions secrètes exclues sauf membres — même logique que la
      map in-game), relation OWN/ALLY/ENEMY/WAR/OTHER/PASDIC calculée pour le viewer.
      Messages JSON UTF-8 < 32 Ko (`"v":1` partout, pagination 500 chunks/page).
      Deltas `claim_add`/`claim_remove` + renvoi complet (`claims_reset` + pages) aux
      changements de monde/diplomatie/secret/membre ; markers warps publics + homes perso +
      f spawn. Hooks : `FactionManager`, `PasdicService`, `DiplomacyManager`,
      `SecretTaxManager`, `WarpManager`, `HomeManager`, `FactionCommand` (setspawn).
      Config `map.client-mod.enabled` (défaut true). Log boot : canal enregistré.
- [x] **Mod Fabric** (`ClientMod/`, MC 1.21.11, Java 21) : touche **M** = carte plein écran
      (terrain top-down échantillonné depuis le ClientWorld avec teintes biome + ombrage
      relief, drag souris, molette zoom 1→16 blocs/px, centrée joueur à l'ouverture) ;
      touche **N** = minimap HUD haut-droite (zoom fixe 4 blocs/px, centrée joueur).
      Overlays : claims colorés par relation (vert OWN, bleu ALLY, orange WAR, rouge ENEMY,
      violet PASDIC, gris OTHER), warps (cyan, or si event), homes (vert), f spawn (orange),
      flèche joueur 8 directions. Réseau : handshake + réception `cantale:map` thread-safe
      (file → tick client), JSON Gson, tout le parsing en try/catch, version protocole vérifiée.
      Sans serveur Cantale : terrain seul, aucune erreur. `gradle build` vert
      (`ClientMod/build/libs/cantale-client-1.0.0.jar`). Voir `ClientMod/README.md`.
- [x] **Correctifs & Invalidation des minimaps tierces (Août 2026)** :
      - Shop : retrait de `SHIELD` et des lits colorés, conservation uniquement de `WHITE_BED` dans `shop.yml`.
      - Réseau : correction du codec Netty `CantaleMapPayload` (suppression du préfixe VarInt `byteArray`) éliminant le crash de connexion à l'arrivée sur le serveur.
      - Inhibiteur minimaps tierces : envoi automatique de paquets de désactivation à la connexion (`journeymap:common`, `xaerominimap:main`, `voxelmap:main`).
      - Minimap & Waypoints : Minimap HUD active par défaut. Nouveau `WaypointManager` (waypoints personnels sauvegardés dans `cantale_waypoints.json`).
      - Interface de carte `M` : barre de filtres (Claims, Warps, Homes, Perso), liste interactive avec TP instantané (`/home <nom>` / `/warp <nom>`), et ajout de waypoints custom.
- [x] **Carte client niveau JourneyMap** (août 2026) : confirmation Oui/Non avant tout TP (home/warp/FW/panneau) ; marqueur joueur en chevron vectoriel orienté au yaw (contour noir, fill or) ; menu clic-droit waypoint (renommer, supprimer avec confirmation, palette 12 couleurs persistée, afficher/cacher) ; clic droit vide = ajouter un waypoint ; clic droit warp/home/FW = téléporter (puis confirmation) + centrer ; icônes distinctes (losange/maison/étoile/drapeau) carte + minimap ; waypoints in-world (beams via `WorldRenderEvents.BEFORE_TRANSLUCENT`, HUD icône+nom+distance via `HudElementRegistry`, toggle « Beams / In-world » persisté). JAR : `ClientMod/build/libs/cantale-client-1.0.0.jar`.
- [x] **Cahier des charges mod client** : `ClientMod/CAHIER_DES_CHARGES.md` (exigences figées vs livré, protocole v1 réel, UX, persistance, recette).
- [ ] **Distribution** : build release + distribution launcher/modpack aux joueurs.
- [ ] **Tests en jeu** : handshake (log client « Serveur Cantale détecté »), M/N, pan/zoom,
      claims colorés par relation (dont WAR orange et PASDIC violet), secret invisible pour
      non-membre, deltas claim/unclaim live, changement de monde, warps/homes/f spawn,
      comportement sur serveur sans bridge (terrain seul) et avec client vanilla.

## Fix persistance violations anti-cheat + audit timestamps (août 2026)
- [x] **Cause racine** : `anticheat_violations.created_at` est `INTEGER` (secondes epoch,
      convention de TOUTES les tables du plugin) mais `ViolationLogger` insérait
      `System.currentTimeMillis()` (millis ≈ 1,7×10¹² > INT max 2,1×10⁹) → MySQL rejetait
      chaque insertion (« Out of range value »), aucune violation persistée.
- [x] **Direction retenue : secondes** (alignement sur la convention dominante : factions,
      bank_log, diplomacy, votes, mutes, auctions... toutes en secondes/INTEGER).
      `ViolationLogger` insère désormais `/1000` ; `/ac info` reconvertit en millis pour
      l'affichage (`Date`).
- [x] **Même bug corrigé** sur `players.referral_at` (`PlayerDAO.tryMarkReferralUsed`
      écrivait des millis → l'UPDATE /parrain échouait en prod MySQL).
- [x] **Migration tolérante au boot** (`DatabaseManager`) : conversion millis→secondes des
      éventuelles valeurs historiques (> 99 999 999 999) sur `anticheat_violations.created_at`
      et `players.referral_at` — idempotent, portable MySQL/SQLite, aucune perte.
- [x] **Audit global des colonnes epoch** : factions (`created_at`, `secret_until`,
      `secret_free_until`, `last_raid_at`), `faction_members.joined_at`, `claims.created_at`,
      `faction_bank_log.created_at` (dont `WAR_REPARATION`), `diplomacy.created_at`/`expires_at`,
      `auction_listings`, `homes`, `warps`, `mutes`, `wanted`, `private_chests`,
      `secret_cooldowns`, `faction_raids`, `votes`, `vote_stats`, `link_codes`,
      `discord_links`, `pending_rewards`, `legendary_tickets`, `seasons` → **toutes OK**
      (secondes). Tables `web_*`/`server_status` : vrais `TIMESTAMP` (par design, lus par le site).
- [ ] **Déployer le JAR** (redeploy ; la migration s'applique au boot).

## Pont grade → permissions Bukkit (août 2026)
- [x] **`RankPermissionBridge`** : sans plugin de permissions (pas de LuckPerms sur le serveur),
      `hasPermission("cantale.admin")` ne passait que pour les ops vanilla → le grade Owner/Admin
      Cantale ne donnait rien (`/f bypass` refusé, protections non contournables). Désormais un
      attachment Bukkit est posé au join selon le grade (Modo → `cantale.moderator`,
      Admin/Owner → `cantale.admin` + tous ses enfants plugin.yml dont `cantale.faction.bypass`),
      retiré à la déco, rafraîchi instantanément par `/rank set|remove`.
- [x] Warnings de migration « Duplicate column » au boot : filtre désormais insensible à la
      casse (MySQL écrit « Duplicate », SQLite « duplicate ») — logs de boot propres.
- [ ] Déployer le JAR. Vérifier : Owner non-op fait `/f bypass` → poser un spawner dans un
      claim étranger fonctionne ; `/optimize`, `/rank`, alertes AC accessibles au staff non-op.

## Audit durabilité items custom (août 2026)
- [x] **Durabilité minage à zone réparée** (`CustomItemListener`) : les blocs extra étaient
      GRATUITS (aucune durabilité) et le Multi-Cantool prenait un double dégât brut sans
      Solidité → chaque bloc coûte désormais 1 via l'API Paper (Unbreaking respecté),
      arrêt propre de la zone si l'outil casse. C'était la vraie cause du « ça dure trop ».
- [x] **Nerf Unbreaking** : 10→7, 7→5, 5→4 (3 inchangé) ; lores régénérées depuis les
      niveaux réels, `faq.yml` du bot alignée.
- [x] **Bug Cantalame** : celle des caisses n'évoluait jamais (mauvaise factory) et
      échappait aux protections → corrigé.
- [x] **Décisions validées** : Cantalame garde `setUnbreakable(true)` (argument premium) ;
      les outils Netherite restent FINIS (la « durabilité infinie » de la FAQ n'a jamais
      existé — doc alignée sur la réalité).
- [ ] Déployer le JAR.

## Limites de homes par grade + alias /f protect (août 2026)
- [x] **Norme = 5** (`teleport.max-homes`, défaut Java 5) suivie par NONE **et les modos**
      (`HomeManager.maxHomesFor`) — les modos n'ont **plus** 999 homes.
      Échelle : NONE 5, Aventurier 8 (était 5, doit rester > norme), VIP 10, Chèvre ∞,
      Admin/Owner ∞. Les homes existants au-delà de la limite sont conservés (la limite
      ne bloque que les NOUVEAUX sethome).
- [x] `/f protect` = alias de `/f pasdic` (même handler, tab-complete inclus).
- [ ] Déployer le JAR. Merger `teleport.max-homes: 5` dans le config live si la clé y
      existe encore avec l'ancienne valeur 3 (sinon défaut Java 5).

## Refonte guerre/raid — août 2026
- [x] **Raid sans guerre** : retrait du gate `isAtWar` dans `RaidManager.canStartRaid` —
      `/f raid` ne requiert plus la guerre (rang Officier+ toujours exigé, règles de
      cooldown/protection/concurrence inchangées).
- [x] **Guerre mutuelle** (`DiplomacyManager.declareWar`) : la 1re déclaration est
      `war_pending` (persistée, expiration `factions.war.declaration-expiry-minutes`, défaut
      15 min) ; la guerre ne démarre qu'à la déclaration réciproque (l'alliance est rompue
      au démarrage). Messages clairs aux deux factions à chaque étape.
- [x] **Pénétration sans /raid en guerre** : `RaidManager.isAttackingInRaid` accepte les
      belligérants (guerre mutuelle, tout chunk, dans les deux sens) et `isRaidActive`
      couvre les claims des factions en guerre pour les explosions — même chemin de code
      que le raid dans `ClaimListener` (casse, pose, conteneurs, TNT, seaux, cristaux).
      PASDIC toujours exclus (le raid ne les contourne pas non plus) ; bypass admin intact.
- [x] **Morts/kills amplifiés** (`PlayerListener.onPlayerDeath`) : perte victime
      5 × `factions.war.death-power-multiplier` (défaut 2.0 — toutes les morts en guerre,
      `death-multiplier-pvp-only: true` pour restreindre au PvP contre le belligérant) ;
      gain tueur 5 × `factions.war.kill-power-multiplier` (défaut 2.0) sur l'ennemi en guerre.
- [x] **Fin de guerre, 3 chemins** : `/f peace` mutuel (`peace_pending`, même expiration) ;
      `/f surrender` unilatéral (annonce « X capitule face à Y » + réparations banque→banque
      `surrender-reparations-cantox`, défaut 10 000, 0 = off, transfert partiel signalé si
      banque insuffisante, log `faction_bank_log` action `WAR_REPARATION`) ; `/f warend
      <facA> <facB>` staff (permission `cantale.faction.bypass`, log console d'audit).
- [x] **Annonces** : broadcast MC au début/fin de guerre + embeds Discord sur le salon
      factions (`mc-factions` de la config Discord) via `DiscordBot.sendEmbedToChannel`.
- [x] **Persistance** : colonne `diplomacy.expires_at` (migration ALTER tolérante) ; types
      `alliance`/`war` permanents (0), `war_pending`/`peace_pending` directionnels avec
      expiration ; purge des en-attente expirées au chargement. Les guerres actives
      persistées par l'ancien système restent valides (type `war` inchangé).
- [x] **UX** : erreurs claires (`/f war` déjà en guerre / déjà déclarée, `/f peace` sans
      guerre), `/f help` et tab-complete (`war`, `peace`, `surrender`, `warend` staff-only).
- [x] **Config** : section `factions.war:` (5 clés, commentaires FR + table de comportement)
      dans `config.yml` ; défauts Java identiques dans `ConfigManager`.
- [ ] **Déploiement** : déployer le JAR + merger la section `factions.war:` dans le
      `config.yml` live (sans merge, les défauts Java s'appliquent). Vérifier en jeu :
      déclaration → expiration, guerre mutuelle, pénétration claims en guerre, mort ×2,
      capitulation avec réparations partielles, `/f warend`.

## Spawners : cage vide + hologramme + spawn air/eau (août 2026)
- [x] **Cage vide** — cause : `SpawnerManager.applyBlockState` mettait `setRequiredPlayerRange(0)` →
      le client n'anime/rend le mob de la cage que si le spawner est « actif » (joueur dans ce rayon) ;
      0 = cage figée/vide. De plus l'état était appliqué pendant `BlockPlaceEvent` (le `BlockStateMeta`
      de l'item, appliqué après l'event, pouvait l'écraser) avec `update(true, false)` (paquet client
      non garanti). Fix : `RequiredPlayerRange = spawners.cage-activation-range` (défaut Java 16),
      `update(true, true)` (paquet block-entity émis → SpawnData reçu), et réapplication complète de
      l'état au tick suivant la pose. Spawn vanilla toujours impossible (delay MAX_VALUE, spawnCount 0,
      maxNearby 0 + `SpawnerSpawnEvent` annulé) ; réordonnancement max-avant-min delay conservé.
- [x] **Hologramme** — mécanisme TextDisplay conservé (tag PDC `spawner_holo`, spawn 1 tick après pose,
      restore restart/chunk-load/entities-load, suppression à la casse, anti-cull `cantale_keep`,
      non ramassable/collisionnable). Bug d'adjacence corrigé : `findHologram`/`removeOrphanHolograms`
      acceptaient TOUT TextDisplay taggé dans ±1,25/±1,75 → des spawners côte à côte (farms) se
      volaient/supprimaient leurs hologrammes. Appartenance désormais par position exacte attendue
      (rayon 0,75 — position déterministe x+0,5 / y+1,35 / z+0,5).
- [x] **Spawn air/eau** — cause : `findSpawnLocation` exigeait un sol solide et rejetait tout liquide.
      Fix : aucune exigence de sol ; position valide si pieds/tête (et y+2 pour les mobs hauts) ne sont
      PAS solides (suffocation) — air et eau acceptés. Pas de logique lumière dans ce spawn custom
      (rien à préserver). `spawnMob` : `SpawnReason.CUSTOM` → `SPAWNER` (allow-list PASDIC
      `ClaimListener` ; `MobStackListener` MONITOR ne filtre pas la reason — stacking inchangé,
      comptage cull `isSpawnerMob` inchangé).
- [ ] Déployer le JAR (`target/cantale-plugin-1.0.0-SNAPSHOT.jar`). Sans merge config live,
      `spawners.cage-activation-range` = 16 par défaut.

## Bypass admin (/f bypass) — août 2026
- [x] **`AdminBypassManager`** (`factions/AdminBypassManager.java`) : `Set<UUID>` en mémoire,
      `isBypassing` / `toggle` / `disable`. Session uniquement (jamais persisté) : `PlayerQuitEvent`
      retire l'entrée. Log console d'audit à chaque bascule (`[Bypass] X a activé/désactivé…`).
      Initialisé dans `CantalePlugin` + getter `getAdminBypassManager()` ; enregistré comme listener.
- [x] **`/f bypass`** (`FactionCommand.handleBypass`) : toggle, permission `cantale.faction.bypass`
      (ajoutée au `plugin.yml`, `default: op`, enfant de `cantale.admin`). Messages FR dédiés avec
      rappel « coupé à la déco » ; tab-complete + `/f help` n'affichent la commande qu'avec la permission.
- [x] **Câblage exhaustif des protections de territoire** : `ClaimListener` (casse/pose via
      `canInteractFor`, conteneurs interdits PASDIC, restriction stockage, PvP claim `onEntityDamage`,
      conteneurs étrangers `canAccessClaimStorage`, explosions sourcées joueur `shouldCancelExplosion`),
      `SpawnerManager.canPlayerPlace` + rattachement du spawner à la faction du claim en bypass
      (`SpawnerListener.onPlaceCommit`), `HopperLimitListener.onHopperPlace`,
      `RuneFortificationListener.onBlockBreak`, `CustomItemListener.canBreakExtraBlock`.
      Sans effet sur : pouvoir max, économie, cooldowns de téléportation, anti-cheat.
- [ ] Déployer le JAR + vérifier en jeu : `/f bypass` on → poser un spawner dans un claim étranger,
      ouvrir un coffre étranger, casser en PASDIC ; déco/reco → bypass bien coupé ; logs console présents.

## Spawns limités aux spawners dans les PASDIC (août 2026)
- [x] **Listener** : `ClaimListener.onCreatureSpawn` (NORMAL, ignoreCancelled) — si le chunk est un
      PASDIC (`FactionManager.getClaimAt` + `Claim.isPasdic`, cache O(1)) et que le `SpawnReason`
      n'est pas dans l'allow-list, le spawn est annulé. Sortie rapide avant tout lookup :
      feature désactivée ou reason autorisée → return immédiat (aucun coût hors PASDIC).
- [x] **Config** : `factions.pasdic.allowed-spawn-reasons` (défaut `[SPAWNER, CUSTOM]` — les
      spawners Cantale spawnent via `spawnEntity` = CUSTOM, le spawn vanilla du bloc étant
      neutralisé). Parsing robuste (uppercase/trim, warning sur valeur inconnue) ; liste vide
      = limitation désactivée avec log au boot. Lecture au boot (pas de reload faction existant).
- [ ] Déployer le JAR + merger `pasdic.allowed-spawn-reasons` dans le `config.yml` live
      (sans merge, les défauts Java s'appliquent : `[SPAWNER, CUSTOM]`).

## Stacking items au sol + raccourci carte (août 2026)
- [x] **ItemMergeManager** (`optimization.item-merge.*`) : fusion des items au sol similaires
      (`ItemStack#isSimilar`) — au drop (3 ticks après spawn pour laisser la fusion vanilla,
      rayon 4,5 blocs) + balayage des chunks chargés ≥ 6 items toutes les 15 s. Hôte = entité
      la plus ancienne, plafond 512/stack (l'excédent reste sur une entité séparée ; un Item
      de count > 64 est vanilla-safe, le pickup remplit plusieurs stacks d'inventaire).
      Jamais de fusion sur items non ramassables / pickup infini (32767) / invulnérables /
      nommés — protège hologrammes et décoratifs. Reload via `/optimize reload`.
- [x] **Raccourci carte** : clic droit ou touche F avec la minimap en main → ouvre la grande
      carte (la touche M est impossible côté serveur : le client vanilla n'envoie pas les
      touches lettres). `MapUI#isMinimap` expose le tag PDC `minimap` ; `MinimapHotkeyListener`
      (anti-spam 500 ms, ignoré si la carte est déjà ouverte, respecte `map.enabled`) ;
      lore de la minimap mise à jour.
- [ ] Déployer le JAR + merger `optimization.item-merge` dans le `config.yml` live
      (sans merge, les défauts Java s'appliquent : activé, rayon 4,5, sweep 15 s, max 512).

## Carte du monde interactive + minimap (août 2026)
- [x] **`/carte`** (aliases `map`, `worldmap`) : GUI 7×4 tuiles `FILLED_MAP` (aucune entité).
      Terrain réel rendu depuis les chunks (async + cache disque `plugins/CantalePlugin/mapcache/`,
      expiration `map.terrain-cache-days`). Claims colorés par relation (vert=toi, bleu=allié,
      rouge=guerre, orange=autre, violet=PASDIC) — **f secret masquée sauf pour ses membres**.
      Marqueurs : warps (cyan/or event), homes perso (vert), f spawn (orange), position joueur (live).
- [x] **Interactions** : clic gauche = téléportation au centre de la tuile (cooldown RTP 5 min +
      warmup 3 s + combat-tag via TeleportDelayManager) ; clic droit = claim (règles /f claim) ou
      menu PASDIC (Officier+, taxe banque + scan conteneurs du chunk, ConfirmDialogUI) ou infos faction ;
      Shift+clic droit = unclaim ; clic molette = recentrer. Zoom 1→16 blocs/px, toggles
      claims/marqueurs/terrain, légende intégrée, infos chunk cliqué.
- [x] **`/carte mini`** : minimap live (2 blocs/px, centrée joueur) tenue en main/secondaire.
      Vue MapView unique partagée + renderer contextuel (contenu par joueur, pas de fuite d'IDs :
      IDs persistés dans `map-views.yml`). Terrain+claims+filtre secret identiques à la carte.
- [x] Technique : `ui/map/` réécrit (ancien stub MapUI remplacé) — `MapUI` (service/sessions/vues/
      refresh 10 ticks via `Player#sendMap`), `TerrainCache`, `MapImageBuilder`, `WorldMapRenderer`,
      `MapSession`, `WorldMapUI`, `MapCommand`. Config `map.*`, onDisable flush cache.
- [ ] Déployer le JAR. Merger `config.yml` live (section `map:` — défauts Java sinon).
      Vérifier : ouvrir `/carte`, TP (respect cooldown RTP), claim/unclaim/PASDIC depuis la carte,
      minimap en main secondaire, secret bien invisible pour un compte non-membre.

## Votes — cooldown IP supprimé + fix site 3 · Collections densifiées (août 2026)
- [x] **Cooldown IP plugin supprimé** : chaque site rate-limite déjà ses votes — le filtre IP Cantale
      (`vote.ip-cooldown-hours`) bloquait les foyers à IP partagée et avalait des votes légitimes.
      Retiré de `VoteListener.handleVote`, `VoteDAO.hasRecentVoteFromIp` supprimé, clé retirée du `config.yml`.
      Reste : anti-doublon par site (cooldown `vote.sites`) + dédup atomique `vote_external_ids`.
- [x] **Fix site 3 (ServeurListe)** : le poller avait un pré-check cooldown de 2 h > intervalle réel du site
      (1 h 30) → tout vote entre 1 h 30 et 2 h était ignoré puis marqué « trop vieux » (définitivement perdu).
      Pré-check supprimé : la dédup par `external_id` + le cooldown site de `handleVote` (1 h) suffisent.
- [ ] **Ops site 3** : la vérif API est par IP → un vote en 4G / VPN / box différente n'est jamais détecté.
      Solution durable = configurer **Votifier dans le dashboard ServeurListe** (IP publique + port NuVotifier
      ouvert en TCP + clé publique/token) : votes instantanés sans condition d'IP, polling API en fallback.
      Procédure : `docs/Votes.md` §3.3. Sans ça, le site 3 restera partiellement muet.
- [x] **Collections densifiées** : paliers par défaut 7 → 12 (500 → 20 000 000) ; chat/votes/quêtes/argent/
      temps de jeu 7 → 10 paliers. **Spawners beaucoup plus tôt** : 1er spawner RARE au palier 6 par défaut
      (60 000, ex. cultures), EPIC en fin, MYTHIC au tout dernier palier. Vies conservées en haut de tableau.
      `spawner:` accepte désormais `true` (= RARE) ou une rareté exacte `RARE|EPIC|MYTHIC`
      (`CollectionReward` stocke la rareté ; `SpawnerManager.rollSpawnerOfRarity`).
      Note : claims stockés par index → les paliers déjà claimés ne sont pas re-claimables, les nouveaux
      paliers intermédiaires deviennent claimables dès que le seuil est atteint (pas de double récompense).
- [ ] Déployer le JAR + **recopier `collections.yml` live** (saveResource ne fusionne pas) + retirer
      `vote.ip-cooldown-hours` du `config.yml` live (sans effet si laissé).

## Refonte faction — PASDIC / Secret / Raids / Protections (août 2026)
- [x] **PASDIC revu** : plus de mode Adventure. Claim renforcé où seuls les membres peuvent poser/casser.
  Conteneurs de stockage interdits (coffres, trapped chest, barrel, ender chest, shulkers) ; fours, fumoirs, hauts fourneaux, hoppers, droppers, dispensers autorisés. Spawners autorisés pour les membres.
  Conversion via `/f pasdic` (confirmation + taxe banque de faction, Officier+) ou shift + clic gauche sur `/f map`.
- [x] **Mode secret** : `/f secret` devient une taxe journalière de 1 000 Cantox par intervalle (20 min par défaut) sur la banque de faction. Avertissement aux officiers (MC + MP Discord) si la banque ne couvre pas le prochain prélèvement. 3 jours gratuits pour les nouvelles factions et après chaque raid subi.
- [x] **Runes de fortification** : ne peuvent plus être appliquées dans un PASDIC. Rareté revue : uniquement caisses Épique, Mythique et Légendaire (Vote/Rare = 0). Config `runes.crate.*-chance-percent`.
- [x] **Claim principal** : `/f setspawn` n’a plus d’immunité explosion spéciale (`FactionManager#isExplosionImmune` = rune uniquement).
- [x] **Système de raids** : `/f raid` dans un claim ennemi démarre un raid (Officier+, en guerre). Pendant le raid : casser, exploser, poser blocs, seaux d’eau/lave autorisés. Hors raid : aucune pose/casse étrangère dans un claim ennemi. Durée, protection progressive et raids simultanés subis selon le pouvoir défenseur ; cooldown et raids simultanés menés selon le pouvoir attaquant (config.yml). Table `faction_raids` (index défenseur + attaquant) + `factions.last_raid_at`/`secret_free_until`. Exception Wither : explosions autorisées hors raid pour casser l’obsidienne (PASDIC et runes restent protégés).
- [x] **Protections progressives** : nouvelles factions et factions après raid bénéficient de 3 jours de secret gratuit. Protection post-raid et max raids simultanés selon le pouvoir.
- [x] **Guerre / alliances** : `/f war <faction>`, `/f peace <faction>`, `/f ally <faction>` (Officier+).
- [x] **Quêtes plus difficiles** : objectifs `daily` multipliés par 2,5 dans `quests.yml` ; distance `walk` passée à 5 000. Collections augmentées de 50 % (`default-tiers` + tiers spécifiques).
- [x] **Distance & téléportation** : le tracker de distance ignore les sauts de position dus aux téléportations (`PlayerTeleportEvent` → `QuestTrackListener`).
- [x] **Items custom** : blocage total à l’enclume et à la table de désenchantement (`PrepareAnvilEvent`, `PrepareGrindstoneEvent`, `InventoryClickEvent` grindstone).
- [x] **Dernière vie** : avant bannissement, l’inventaire (hors EC/PC) est dropé au sol puis vidé.
- [ ] Déployer le JAR Paper (`target/cantale-plugin-1.0.0-SNAPSHOT.jar`).
- [ ] Copier le nouveau bloc `factions.pasdic`, `factions.secret`, `factions.raid` dans le `config.yml` live (ou laisser les défauts Java si absent, mais les valeurs de config sont recommandées).
- [ ] Recopier `quests.yml` et `collections.yml` live (saveDefaultConfig ne fusionne pas les nouvelles valeurs).
- [x] Mettre à jour le wiki/FAQ : retrait du mode Adventure PASDIC, nouveau `/f secret`, `/f raid`, `/f war`, claim principal normal, runes Épique+.
- [x] FAQ bot : `src/main/resources/faq.yml` mis à jour (refonte faction + mécaniques + questions Minecraft de base). Rechargement via `/faq reload`.

## Claim f-spawn immunisé aux explosions (août 2026)
- [ ] Déployer le JAR (`target/cantale-plugin-1.0.0-SNAPSHOT.jar`). Redeploy site wiki + `faq.yml` live.
**Note :** l’immunité f-spawn a été supprimée dans la refonte ci-dessus. Le claim principal est désormais normal.
- [x] Le chunk du `/f spawn` a la même immunité explosions qu'une Rune de fortification
      (`FactionManager#isExplosionImmune` : rune **ou** spawn claim). TNT ne s'allume pas,
      explosions annulées, blocs immunisés depuis l'extérieur, dégâts d'explosion aux
      entités annulés. Casse/pose des membres inchangées ; PASDIC distinct. Suit `/f setspawn`.
- [ ] Déployer le JAR (`target/cantale-plugin-1.0.0-SNAPSHOT.jar`). Redeploy site wiki + `faq.yml` live.

## Fix hologrammes spawners (août 2026)
- [x] Pose / chunk load / restart : le nom FR + palier s'affichent au-dessus du bloc
      (ex. `Spawner à Cochon x3`). TextDisplay full-bright, fond lisible, restore 1 tick
      après ChunkLoad (getBlock AIR), réutilisation PDC, stacking inchangé.
- [ ] Déployer le JAR (`target/cantale-plugin-1.0.0-SNAPSHOT.jar`).

## Cooldowns / warmup téléportation (août 2026)
- [x] 30 s entre toute TP (home, warp, spawn, rtp, tpa, f spawn/go/warp, events, claims GUI).
      RTP : 5 min en plus (max des deux). Warmup 3 s immobile + preload chunk pour tous.
      Exempt : `cantale.admin` et mode trace `/moderation`. VIP / Chèvre non exemptés.
- [ ] Déployer le JAR (`target/cantale-plugin-1.0.0-SNAPSHOT.jar`). Pas de merge config live (valeurs en dur).


## Rune / spawners ghast / PvP claims / lits / têtes wither (août 2026)
- [x] Rune de fortification : aucune explosion dans le chunk (TNT, cristaux, wither, lits, ancres, minecarts TNT, creepers, fireballs…). Origine fortifiée = event annulé ; blocs fortifiés immunisés même depuis l'extérieur ; dégâts d'explosion aux entités annulés.
- [x] Rune de fortification dans les caisses : tirage extra (`CrateRewardTable#tryRollRune`) Rare 2 %, Épique 4 %, Mythique 6,5 % — config `runes.crate.*-chance-percent` (défauts Java si absent). Vote / Légendaire : 0. Wiki + faq.yml mis à jour. `saveResource` ne merge pas : ajouter la section `runes:` au `config.yml` live.
- [x] Ghast retiré des spawners obtenables (enum `GHAST` conservé pour BDD/items ; load/pose → cube de magma). Plus dans caisses, `/spawner give`. Quête `kill_ghast` = tuer le mob (pas de spawner).
- [x] PvP one-way dans les claims : étranger → membre chez lui **toujours** annulé (tag combat ou non). Membre/allié → étranger dans le claim : autorisé. PASDIC : pas de PvP. Wilderness inchangé. `ClaimListener` HIGHEST ; le tag combat ne contourne pas.
- [x] Plus de lits en pose/allumage raid (config.yml + défauts Java + strip `*_BED` même si le YAML live en a encore). Wither = seul moyen de casser l'obsidienne.
- [x] Têtes wither skeleton : plus de drop (EntityDeath + loot stack). `/shop` GUERRE **50 000** Cantox, sell off, daily-buy-cap 6. Pose raid des têtes inchangée.
- [ ] Déployer le JAR `target/cantale-plugin-1.0.0-SNAPSHOT.jar`. `shop.yml` : merge auto de `WITHER_SKELETON_SKULL` au load. Recopier `collections.yml` et `quests.yml` live. `config.yml` live : les lits listés sont ignorés par Java (pas besoin de merger pour ça). Redeploy site wiki.

## Raid claims — TNT / lodestone / explosifs (août 2026)
- [x] Étrangers : poser TNT, lodestone, cristaux, ancres, seaux, wither ; allumer TNT (briquet) ; vider seaux. PASDIC inchangé. (Lits retirés ensuite — voir section du haut.)
- [ ] Déployer le JAR (défauts Java, pas besoin de merger le config.yml live pour lodestone / briquet).


## F-homes retirés (août 2026)
- [x] Plus de `/f home` / `/f sethome` / bouton Homes dans `/f`. Quota pouvoir et perm FHOME retirés.
      Homes perso `/home` inchangés. Mobilité fac : `/f spawn`, `/f warp`.
      Table `faction_homes` laissée en BDD (pas de DROP). Fichier `FactionHomesUI.java` conservé (stub).
- [ ] Déployer le JAR.


## Quêtes + Collections + events 2–3/j (août 2026)
- [x] Events faction : 14–21/semaine, 10h–minuit, max 3/j, 2h mini entre (events.yml). Recopier en live.
- [x] Quêtes solo (1 daily + 1 weekly + 1 seasonal) + courses faction. YAML `quests.yml`.
- [x] Templates `quests.yml` élargis : 40 → 118 ids distincts (cultures, minerais dont or Nether, cuisson four, PvP, passifs, hostiles dont ghast naturel / warden, distance). Pools inchangés (solo 5/3/2, fac 12/6/3).
- [x] `QuestTrackListener` : kelp/bambou/cactus/chorus/champignons/fungi/glow berries (vines + clic droit), torchflower/pichet ; `NETHER_GOLD` séparé (plus fusionné dans GOLD) ; soie toujours ignorée.
- [x] Collections exponentielles `collections.yml` + `/collections`. Pic `/bal` = `balance_peak`. Clés cultures/minage/mobs alignées (GUI 45 max).
- [x] `/quetes`, `/quete reroll`, `/quete give`, `/saison [start]`.
- [x] Boutons Quêtes (slot 38) et Collections (slot 40) dans `/cantale` (`MainMenuUI` → `QuestMainUI` / `CollectionMainUI`).
- [x] Collection `quests` (Quêtes complétées) : +1 solo dans `QuestEngine.completeSolo`, +1 contributeurs fac gagnante. Bonus set ≠ 2e quête. Events reste hidden.
- [ ] Déployer JAR + copier `quests.yml`, `collections.yml` (catégorie `quests` absente du live — `saveResource` ne fusionne pas), et le bloc faction-events de `events.yml` en live.


## PASDIC depuis la carte site (août 2026)
- [x] Admins site (`ADMIN_DISCORD_ID`) : `/carte` → Mode admin → multi-sélection des claims → **Définir PASDIC**.
      Chunks avec spawners ou stockage (tile entities à inventaire) refusés ; les propres passent.
      File `web_admin_actions` type `set_pasdic` → `WebAdminBridge` + `PasdicService` (load async, scan, `claims.pasdic`).
      `/pasdic set` en jeu : même garde stockage + spawners.
- [x] Owner / Direction voient le bouton PASDIC (août 2026) : même garde que `/admin` —
      allowlist `ADMIN_DISCORD_ID(S)` + `OWNER_DISCORD_ID(S)` + `DIRECTION_DISCORD_ID(S)`,
      rôles Discord Direction (fondateur / co-fondateur / directeur) + owner guilde + rôle Admin,
      grade Minecraft `OWNER`/`ADMIN`. Pas modos / staff.
      Poussé `Annonnyx/Cantale` `74cc947` → Vercel prod Ready (www.cantale.world/carte).
      (`c905ba2` / `24ec579` avaient échoué : `};` en trop dans `territory-canvas.tsx`.)
- [ ] Déployer le JAR Paper (`target/cantale-plugin-1.0.0-SNAPSHOT.jar`) pour que le pont lise les jobs et élargisse payload/result en TEXT.
- [x] Site poussé sur `Annonnyx/Cantale` `c905ba2` → Vercel `site-cantale` (www.cantale.world/carte).

## TPA — texte et sens `/tpa` (août 2026)
- [x] `/tpa` : le demandeur se tp à la cible. Message cible : « X veut se tp à toi » + boutons Accepter/Refuser (hover qui dit qui bouge). `/tpahere` reste l’inverse. Combat-tag inchangé.

## Caisses — plus de « Pas de chance » (août 2026)
- [x] Retiré Miettes du Roi (Vote) et Aumône (Rare) ; les poids restants se renormalisent au roll. Spawners inchangés.

## Spawners Cantale + fix TAB (août 2026)
- [x] TAB/nametags : sidebar event faction sur le scoreboard **principal** (plus de board par joueur qui masquait les préfixes). Équipes UUID, `playerListName` pour le TAB, cache préfixe. Nametags tête inchangés (préfixe d'équipe).
- [x] Spawners 15 mobs (Rare 8 / Épique 4 / Mythique 3 dont golem ×2 intervalle). Items PDC palier 1–8, hologramme, pose claims only, interdit PASDIC, PASDIC échoue si spawners présents. Silk any pickaxe = 1 item du palier ; sans silk = rien. /ah ok, /shop blacklist. Stack clic droit même type, refuse si > 8. Cadence linéaire : N mobs / (8/N) s (T1 8 s, T8 cheat 8/1 s = 480/min). Stacker étendu aux mobs spawners (PDC) ; exclus du cull 15/chunk.
- [x] Caisses : chance extra 5 % / 3 % / 1,5 % (Rare / Épique / Mythique+Légendaire). `/spawner give`. Table `spawners`. Config `spawners:`.
- [ ] Déployer JAR + coller le bloc `spawners:` dans le `config.yml` live. Redeploy site wiki si besoin.

## Anti-AFK configurable par grade (août 2026)
- [x] Timeouts lus dans `config.yml` (`afk.timeout-seconds`) : none 600 s, aventurier 900, vip 1800, chevre 3600, moderator 3600. Admin (`cantale.admin` + ranks ADMIN/OWNER) jamais kick. Vanish skip. Join initialise l’activité (fix joueur immobile jamais kick). Copier le bloc dans le `config.yml` live (saveDefaultConfig ne merge pas).
- [ ] Déployer JAR + coller le bloc `afk:` dans `plugins/CantalePlugin/config.yml`.

## Perf plugin — 4 quick wins (août 2026)
- [x] Cache `RankManager.getRank` en mémoire ; invalidation `setRank` / `removeRank` / join / quit.
- [x] Sentinelles faction : wilderness (`WILDERNESS_SENTINEL`) et sans-faction (`-1`) comme FortifiedChunk ; invalidation claim/unclaim/join/leave/disband.
- [x] `ActionLogger` file + flush 1 s, flush `onDisable` (même contenu `actionlog.txt`).
- [x] Vol Chèvre : skip `PlayerMoveEvent` si même chunk (monde + chunkX + chunkZ ; claims = chunks).
- [ ] Déployer le JAR Paper : `target/cantale-plugin-1.0.0-SNAPSHOT.jar`

## Lag `/rtp` — génération de chunks sync (août 2026)
- [x] Cause : `RtpCommand.findLocation` tournait **sur le thread principal**. Jusqu’à 16
      `World#getHighestBlockYAt` / `getBlockAt` à 500–5000 blocs → **génération sync** des
      chunks (Paper `getChunkAt`). Pas de recherche de biome, mais le filtre lave/eau
      relance d’autres chunks. Freeze TPS le temps de 1–16 gens.
- [x] Fix : recherche **séquentielle async** via `World#getChunkAtAsync(..., gen=true)`,
      inspection safe (lave/eau/feu…) une fois le chunk chargé, warmup inchangé, TP sur
      le main. Re-check unsafe juste avant TP. Cap 16, timeout 15 s, pas de double-search.
- [ ] Déployer le JAR Paper.

## `/shop reload` console + MultiCantool spam (août 2026)
- [x] `/shop reload` accepté depuis la **console** (`ConsoleCommandSender`) et en jeu (`cantale.admin`).
      Buy/sell/UI restent joueur. Même `ShopManager.reload()` (merge shop.yml). Messages FR console sans MiniMessage.
- [x] MultiCantool : plus de `logger.info` à chaque morph visuel (`Lirfling → IRON_PICKAXE`). Passage en `FINE` uniquement.

## Fix /day = jour vanilla overworld (août 2026)
- [x] `/day` lisait le monde du joueur (Nether/End = autre horloge). Source de vérité =
      `World#getFullTime() / 24000` de l’overworld (`world` spawn, sinon premier NORMAL).
      Plus de compteur plugin : survit au restart (level.dat). `OverworldDay` pour tout affichage « jour N ».
- [ ] Déployer le JAR Paper.

## Perf site — chargement lent (août 2026)
- [x] Cause : pages data `force-dynamic` sans cache MySQL (Vercel → MariaDB distante) ;
      proxy skins `/api/minecraft/*` en série Mojang 8 s puis CDN (classements = N avatars
      serverless) ; header `/api/auth/me` + classements/profil appelaient l’API Discord
      guild à chaque HTML ; `/carte` SSR de tous les claims (JSON énorme).
- [x] Fix durable (site only) : cache court `unstable_cache` (15–60 s) sur lectures
      publiques ; proxy skins en parallèle timeout 2,5 s + ISR/CDN 6 h + cache 404 2 min ;
      session lite sans Discord pour pages publiques / header ; avatar 2D immédiat sur
      les profils ; claims carte en fetch client ; `loading.tsx` ; timeout MySQL 8 s.
- [x] Poussé `Annonnyx/Cantale` `8926d65` sur `main` (Vercel auto-deploy prod).
      **Pas de nouvelle env var.**

## Dernière vie en combat + tag 20 s (août 2026)
- [x] Dernière vie hors combat : ban sans drop (inchangé). Dernière vie **en tag combat** :
      drop monde (stockage + armure + offhand) **avant** le kick, sans double drop.
- [x] Durée tag combat : 20 s (`combat-tag-seconds`, défaut Java + `config.yml` JAR).
      Live : si la clé n'existe pas encore → 20. Si une ancienne valeur est déjà sur disque, elle gagne.
- [ ] Déployer le JAR. Si `plugins/CantalePlugin/config.yml` live a déjà `combat-tag-seconds`
      (autre que 20), le passer à 20. Clé absente = défaut 20, rien à faire.

## Votes ServeurListe API (août 2026)
- [x] Site 3 (ServeurListe.com) : polling `GET /api/vote?ip_address=&api_token=` (FAQ, pas de liste votes).
      Même récompense Cadeau du Roi ; dédup `vote_external_ids` + cooldown.
      Config `vote.serveur-liste.*` + token `vote.tokens.serveurliste.com` (`token_api`).
      Votifier « connexion impossible » documenté (TCP / `host: 0.0.0.0`) — ignorable si API OK.
      Docs `docs/Votes.md` §3.3. **Ne pas retirer le site 3.**
- [ ] Déployer le JAR + coller `token_api` dans le `config.yml` **live** si pas déjà
      `vote.tokens.serveurliste.com`.

## Fix /f secret SQL MariaDB (août 2026)
- [x] `SecretCooldownDAO` envoyait `INSERT OR REPLACE` (SQLite) à MariaDB → cooldown non persisté
      alors que le mode secret s’activait. Upsert dialect-aware (`DatabaseManager.upsertSql`) ;
      activation seulement si le cooldown est écrit. Même classe de bug corrigée sur
      `FactionDAO.setPermission` (`ON CONFLICT` SQLite envoyé à MariaDB).

## Votes Serveur-Prive API (août 2026)
- [x] Site 2 (Serveur-Prive.net) : polling `GET /api/v1/servers/{token}/votes` (`serverVotesList`)
      + vérif IP des joueurs en ligne ; même récompense Cadeau du Roi que les autres sites ;
      dédup BDD `vote_external_ids` + cooldown site (pas de double grant avec Votifier).
      Config `vote.serveur-prive.*` + token `vote.tokens.serveur-prive.net`. Docs `docs/Votes.md` §3.2.
- [x] `GET /votes/{ip}` 404 live : auto-disable du check IP après le 1er 404 (session),
      plus de WARN/URL toutes les 60 s ; récompenses via `serverVotesList` uniquement.
      HTTP déjà async. Optimizer urgence : plus de `System.gc()`, cooldown 2 min.
      Action bar hoppers : cache 5 s + chunks déjà chargés (plus de load de claims).
- [ ] Déployer le JAR + coller le token dashboard (Paramètres) dans le `config.yml` **live**
      si ce n’est pas déjà `vote.tokens.serveur-prive.net`.

## Fixes AH / banque / shop (août 2026)
- [x] **`/ah sell` AIR** : `setAmount(0)` transformait le stack en AIR → message « AIR ». Snapshot type + clone avant retrait ; clear main hand propre.
- [x] **`/ah` GUI** : prix visible (lore + nom) ; clic → `ConfirmDialogUI` (pas d'achat au 1er clic) ; mapping slot→listingId.
- [x] **`/f bank` historique** : résolution UUID→pseudo (`PlayerDAO` / online / OfflinePlayer) ; dates secondes/millis/TIMESTAMP (plus d'« ID » / « date inconnue » si timestamp valide).
- [x] **Shop** : `END_CRYSTAL` = catégorie `NETHER_END` (pas GUERRE/PvP) ; `HOPPER` = `REDSTONE` 150k cap4 — confirmé JAR `shop.yml`.
- [x] **`/shop reload` WARN** : `saveResource(shop.yml,false)` loguait un WARN Bukkit si le fichier
  existait déjà (bénin, reload + merge continuaient). Fix : `saveResource` seulement si absent ;
  merge non destructif + relecture disque inchangés (prix live préservés).

## Feature — /parrain (août 2026)
- [x] Commande `/parrain <joueur>` : l'invité remercie son parrain **une seule fois** ;
  le parrain gagne +1 vie (`LifeManager#addLife`). Persistance BDD
  (`players.referral_used` / `referred_by` / `referral_at`, marquage atomique).
- [x] Validation : pas soi-même, cible connue en BDD, tab-complete joueurs online.
- [x] `plugin.yml` + FAQ + wiki commandes / trois-vies + COMMANDS.md.
- [ ] Déployer le JAR Paper.

## Fixes joueurs — shop hoppers / mort Nether /lastdeath / compteur Discord (août 2026)
- [x] **/shop HOPPER** : `saveResource(shop.yml,false)` ne mettait pas à jour le fichier live →
  HOPPER absent après réintro. Fix durable : merge non destructif des `items.*` JAR→disque
  (`ShopConfig.mergeMissingItemsFromJar`) + parse Map flow-style. Catégorie REDSTONE, 150k, cap 4.
  **Merge live** : au prochain `/shop reload` ou restart, HOPPER est ajouté si manquant (prix JAR).
- [x] **Mort Nether / loot** : aucun clear de drops dans Cantale ; VIE retirée via `event.getDrops()`
  (plus de mutation inventaire pendant death). Protection feu/lave 90s sur items de mort
  (`DeathLootProtectListener`). Snapshot inv persisté pour staff. keepInventory staff non forcé.
  Si loot « disparu » hors lave/feu : vérifier gamerule `keepInventory` externe.
- [x] **/lastdeath inv staff** : colonne `last_death_inventory` (+ migration) ; GUI lecture seule.
  Admin toujours ; modo seulement en mode trace (`/moderation`). Pas de restore joueur.
- [x] **Compteur Discord** : parse strict entier seul ; anti double-post auteur ; prev+1 ;
  suppression messages invalides (y compris « r » / chat libre). État `last-author-id`.
- [ ] Déployer le JAR Paper + vérifier bot Manage Messages sur le salon counting.

## URGENT — crash récursion PrivateChest (août 2026)
- [x] **Cause** : `saveChestForOwner` appelait `closeInventory()` pendant `InventoryCloseEvent`
  (l’inventaire paraît encore ouvert) → re-entrée `onInventoryClose` → StackOverflow / serveur mort.
- [x] Fix durable : `ThreadLocal` garde `closeSaveGuard` — skip close réentrant ; fermeture des
  *autres* viewers sans re-save (`closeViewerWithoutResave`).
- [ ] **Déployer le JAR immédiatement** (serveur Paper en prod).

## Discord — salon compteur (août 2026)
- [x] Listener JDA `CountingListener` sur `discord.channels.counting`
  (`1537112048314491040`) : **strict** entier seul = prev+1 ; anti double-post ; invalides supprimés.
- [x] Persistance `discord-counting.yml` (`current` + `last-author-id`) + reprise historique.
- [x] Docs `docs/Discord.md` + FAQ mises à jour (plus de chat libre dans ce salon).
- [ ] Déployer le JAR plugin + vérifier permissions bot (Manage Messages).

## Fix /lastdeath coords (août 2026)
- [x] **Cause** : seule `players.last_death` (timestamp) était persistée ; `/lastdeath`
  affichait « Localisation : Non disponible » (TODO) et ne résolvait que les joueurs online.
- [x] Fix : colonnes `last_death_world/x/y/z` (+ migration), écrites à la mort et à la
  déco combat ; commande affiche les coords ; résolution offline via BDD / OfflinePlayer.
- [ ] Déployer le JAR.

## Équipe + alliances Discord (août 2026)
- [x] `/recrutement#equipe` : staff réel uniquement — MC `player_permissions` (Owner /
  Admin / Modo) + Discord direction + Développeur / Admin / Support / Builder / Monteur /
  Graphiste (IDs défauts `DISCORD_ROLES`, override env optionnel). Merge/dédup Discord ID.
- [x] `/partenariats#allies` : Partenaires actifs + Creators (rôles Discord), hors Équipe.
- [x] UI : grilles par grade (avatar MC ou Discord, lien profil, handle Discord).
- [ ] Ops : vérifier Server Members Intent sur l’appli bot si des rôles Discord manquent.
- [x] Deploy `Annonnyx/Cantale` `3a56c85` → Vercel `site-cantale` prod Ready
  (`site-cantale-b99as7cw4…`, `dpl_vgPQUzuR…`, aliases www.cantale.world). Monorepo `608d509`.

## Boutique fermée — hébergeur / S1 (août 2026)
- [x] Copy `/boutique` (SHOP_ENABLED off) : fermée à cause des conditions de l’hébergeur
  actuel ; ouverture pour la S1 après changement d’hébergeur. Stamp « Fermée ».
- [x] Aligné messages checkout 503 + HTML legacy (`boutique.html` / `cart.html`) + backend
  Express notice.
- [x] Deploy `Annonnyx/Cantale` `e6f2fac` → Vercel `site-cantale` prod Ready
  (`site-cantale-7hmqylwbq…`, aliases www.cantale.world). Live vérifié.

## Fix tickets recrutement Discord (août 2026)
- [x] Cause : `/api/recrutement` (et partenariats) lisaient la session via
  `getSessionUser().catch(() => null)` — échec MySQL/rôles → identité Discord
  perdue → embed « Compte Discord non connecté » alors que la page montrait
  l’utilisateur connecté ; nom de salon sans handle Discord.
- [x] Fix : `getSessionDiscordUser(request)` (cookie seul + fallback header),
  `credentials: "include"`, libellé FR, salon `open-recrutement-<poste>-<username>`.
- [x] Deploy `Annonnyx/Cantale` `79dfbc4` → Vercel `site-cantale` prod Ready
  (`site-cantale-oy68cti3r…`, aliases www.cantale.world). Monorepo `3e2d47b`.

## Fix scroll jump clics (août 2026)
- [x] Cause : `html { scroll-behavior: smooth }` + focus Leaflet (`focus()` puis
  `window.scrollTo`) / `href="#"` (zoom) / `scrollIntoView` chat → sauts vers le bas.
- [x] Fix durable : `scroll-behavior: auto`, `ScrollStability` (bloque `#`, smooth
  ancres in-page), `preventScroll` carte Leaflet + canvas, chat scroll interne.
- [x] Deploy `Annonnyx/Cantale` `9ec2097` → Vercel `site-cantale` prod Ready
  (`site-cantale-9xqif40li…`, aliases www.cantale.world). Monorepo `4cf10fe`.

## Conformité légale site (août 2026)
- [x] Pages FR pratiques : `/mentions-legales` (mentions LCEN + CGU, éditeur **Barneron Noé**),
  `/confidentialite` (RGPD : Discord OAuth identify, liaison MC, formulaires, Turnstile optionnel,
  droits CNIL), `/cookies` (liste réelle `cantale_session` / `cantale_oauth_state` — **pas**
  d’analytics inventés). Footer colonne Légal + liens bas de page. Bandeau cookies **informatif**
  (localStorage `cantale_cookie_notice`) — cookies strictement nécessaires → pas de mur de
  consentement marketing. Sitemap mis à jour.
- [x] Redeploy `Annonnyx/Cantale` `83e371c` → Vercel prod Ready (pages 200 sur cantale.world).
- [x] Vouvoiement pages légales + e-mail **contact@cantale.fun** (mailto) mentions / RGPD /
  cookies / footer. Deploy `Annonnyx/Cantale` `b0e7232` → Vercel `site-cantale` prod Ready
  (`site-cantale-qez1pf3if…`, aliases www.cantale.world). Monorepo `0aaa676`.

## Exemples « noe » → « onyx » (août 2026)
- [x] Remplacement des pseudos d’exemple `noe` / `Noé` → `onyx` / `Onyx` dans les placeholders
  site (recrutement + partenariats). Wiki / FAQ : aucun exemple nommé « noe » (déjà `<pseudo>` /
  `<joueur>`). Exceptions conservées : CGU / mentions légales (Barneron Noé), chemins
  `/Users/Noe/…`, secrets, historique git.
- [x] Sync + deploy : Cantale-MC `f941c80` · Annonnyx/Cantale `218dfba` → Vercel `site-cantale`
  prod Ready (`dpl_EamRwP3P…`, aliases www.cantale.world). Spot-check partenariats : Onyx/onyx.

## Wiki — Économie / shop / AH / daily / votes / caisses (août 2026)
- [x] Refonte articles wiki `economie/*` + `recompenses-regulieres` + `caisses-cles` :
  Cantox (start-balance 10k), `/shop` (clics, caps minuit, sinks endgame prix shop.yml),
  `/ah` (7 j, limites par grade, sous-commandes), `/daily` (montants par grade),
  votes (3 sites, crates, party 50, paliers, top mensuel), caisses (interactions + sources).
  Sources : `shop.yml`, `ShopConfig`/`ShopCategoryUI`, `Vote*`, `Crate*`, `DailyCommand`,
  `PlayerRank`, `config.yml` vote.*, `AuctionManager`.
- [x] Redeploy site pour publier le wiki économie (`Annonnyx/Cantale` `1328221` → Vercel prod Ready; Cantale-MC `4bdca15`).

## Wiki — Factions / claims / pouvoir / banque (août 2026)
- [x] Refonte articles wiki `factions/*` + récompenses events : tables depuis le code
  (`FactionManager`, `Faction`, `FactionPowerShop`, `FactionBankCommand`, `ClaimListener`,
  `FactionPermissions`, `config.yml` factions.*, `events.yml` rewards). Couvre toutes les `/f`,
  sources pouvoir (65 création, +5/membre, PvP ±5, bank 1 unité→+10 pouvoir + ×10 exposant, events top, `/admin addpower`),
  quotas claims/warps, rangs, wilderness/PASDIC, règles stockage/explosifs.
- [x] Support `WikiTable` dans `wiki-content.ts` + rendu page article + index recherche.
- [x] Alignement `faq.yml` (création, claim/pouvoir, warps, homes, banque) sur les mêmes formules.
- [x] Claims contigus : nouveau claim doit toucher N/S/E/O un claim existant (sauf 1er du monde) —
  `FactionManager#isAdjacentToOwnClaimOrFirst` + message FR `/f claim` ; wiki + faq.
- [x] `/f map` UX : grille chat 11×11 lisible (`+ # @ X = P -`), highlight ta fac, diplomatie
  allié/ennemi, PASDIC, secret masqué aux non-membres, légende dynamique, « Ici » + tags autour,
  moins de spam préfixe ; wiki + `docs/UI_GUIDE.md`.
- [x] `/f map` inventaire : GUI 9×5 chunks (`FactionMapUI`) — vert/lime/gris/rouge/bleu/orange/violet,
  clic gauche claim / clic droit unclaim (mêmes checks que `/f claim` `/f unclaim`), fermer/refresh ;
  `/f map chat` conserve l’ASCII 11×11 ; wiki + UI_GUIDE + faq.
- [x] Vol en claim : uniquement `PlayerRank.CHEVRE` — (1) vol créatif `hasUnlimitedFlight` dans
  ses claims ; (2) fouille conteneurs étrangers `canStealInClaims` (alliés/ennemis) via
  `ClaimListener` — plus d'ouverture libre des coffres alliés ; pas modos/admin/owner ;
  wiki grades + claims + faq + `CustomItemListener`.
- [x] Renommage faction : `/f rename` (chef) + `/admin f rename` (staff) — validation nom partagée
  avec create (3–16, lettres/chiffres/_/-), tag régénéré, DB + cache, nametags membres online,
  rôle Discord ; wiki + COMMANDS + plugin.yml.
- [ ] Redeploy site + redeploy `faq.yml` / JAR serveur si besoin.

## Refonte site web (août 2026) — `Site-Cantale/web/` (Next.js 16 full-stack, TS, Tailwind v4)
- [x] Scaffold app Next.js dans `Site-Cantale/web/` (App Router, TS strict, Tailwind v4, mysql2)
- [x] Polices locales intégrées via `next/font/local` : Zina (hero), Clash Display (titres), Cabinet Grotesk (texte), Nippo (tech/labels) — sources dans `Site-Cantale/polices/`
- [x] DA « Registre » adaptée : hero minimaliste (CANTALE géant en Zina sur fond ash), palette ash/iron/ember/gold/steel/bone, encoches de vie forgées, tampons, bandeau registre défilant
- [x] Layout global : header fixe (nav, compteur live, menu mobile), footer (réseaux, copie IP), `prefers-reduced-motion` respecté
- [x] Page d'accueil v1 : hero Zina, bandeau, concept 3 vies (LifeNotches), mécaniques, histoire, CTA rejoindre
- [x] Couche données : `src/server/db.ts` (pool mysql2, lecture seule) + repo `server-status` + route `/api/server/status` (503 tant que la table n'existe pas)
- [x] **Plugin** : `ServerStatusTask` — upsert `server_status` (online_count, max_players, tps, updated_at) toutes les 60s (alimente compteur header + page Stats)
- [x] **Plugin** : `WebFactionBridge` — file `web_faction_actions` (join/leave/set_rank/disband) consommée toutes les 15s via `FactionManager` sur le thread principal (statut done/failed + result + processed_at) ; le site n'écrit jamais dans `faction_members`, la sync du rôle Discord se déclenche via les événements existants
- [x] Repositories : players, factions (+claims, filtrage `secret_until` au SQL), votes, wanted, discord_links, snapshots, leaderboards, faction-settings, faction-actions
- [x] Page `/design` interne de preview des composants
- [x] Auth Discord OAuth2 maison (cookie HMAC, pas Auth.js — incompatible Next 16) + paliers anonymous/discord/linked/leader + rôles via bot token (cache 60s) + page `/connexion` 3 états (skin 3D skinview3d, stats, faction, panneau leader)
- [x] Pages livrées : Règlement (28 règles, sommaire sticky, tampons sévérité), Partenariats (formulaire contact + ticket Discord REST `partenariat-*`, même pipeline que recrutement), Recrutement (formulaire 4 étapes + ticket Discord REST compatible `/ticket close`), Wiki (25 articles / 7 catégories, recherche ⌘K), Items (13 entrées depuis `CustomItemType` — Rune de Fortification conservée car toujours active, Statio-Lytras exclus), La Liste (mémorial), Vote (3 sites, minuteurs live), Classements (métrique × période, snapshots `web_snapshots_daily` + cron `CRON_SECRET`, podium, « ta position »), Stats (compteurs live + charts CSS), Factions (annuaire, détail ★◆●○, candidatures `web_faction_applications`, panneau leader → file `web_faction_actions`), Boutique (catalogue complet, flag `SHOP_ENABLED` bloquant côté serveur, checkout 501 en attendant la décision cantale.store)
- [x] Wiki Items (slice pratique) : `items-forges`, `cantalame`, `item-vie`, `rune-de-fortification`, `armure-du-garde`, `caisses-cles` — tableaux obtention/durabilité/effets/CMD depuis plugin only (zéro lore inventée)
- [x] Build de production vert + ESLint 0 erreur sur tout `src`
- [x] Page `/carte` : carte unifiée Leaflet (tuiles Squaremap via `/map-provider` + overlays claims/PASDIC/warps), APIs `/api/map/claims` + `/api/map/markers`, factions secrètes exclues en SQL, rafraîchissement 30s, plein écran, lien externe Squaremap, recherche faction/tag/warp + champs X/Z, centre spawn (−67 · −144) ; pas de joueurs live (Leaflet + stub `players.json` proxy) ; repli canvas si pas de `MAP_PROVIDER_URL`
- [x] Polish : SEO (`sitemap.ts`, `robots.ts`, OG image Zina, `icon.svg`), perf (AuthEntry client → rendu statique restauré sur les vitrines), a11y (`steel-light` contraste AA), galerie accueil (lightbox, `null` si `public/screenshots/` vide)
- [x] Nav : liens Recrutement + Partenariats (hamburger sous 1280px)
- [x] Motion UI : système CSS centralisé (pressable / card-lift / card-soft / nav-link / chip / stamp-ink), reveal-on-scroll + stagger + fallback IntersectionObserver, transitions de page via `template.tsx`, `prefers-reduced-motion` étendu
- [x] Découverte : footer complété (Boutique, Stats, Connexion) ; menu mobile section « Plus » (Règlement, Boutique, Stats, La Liste) sans surcharger la nav desktop (8 liens) ; `sitemap.ts` élargi (factions, classements, stats, carte, vote, la-liste, boutique, recrutement) ; CTA rejoindre → lien règlement
- [x] Galerie : dossier `public/screenshots/` documenté (README + `.gitkeep`) — la section n'apparaît qu'avec de vraies captures
- [x] Micro-polish motion : `.chip` / `.pressable` / `.card-soft` alignés sur classements, la-liste, carte, stats
- [x] Hover hard-shadow 2D : ombre nette diagonale (`X Y 0 0`, blur 0) sur `.pressable` / `.card-lift` / `.card-soft` — translate haut-gauche + active compressé, easing `cubic-bezier(.16,1,.3,1)` ; pas de glow ember ni soft blur

### Code-done vs ops / humain (checklist)

| Statut | Item |
| --- | --- |
| **Code-done** | Pages, auth, factions, carte canvas, wiki, items, vote, boutique (flag off), classements, stats, SEO/a11y, motion, nav/footer/sitemap, dossier screenshots prêt |
| **Bloqué — contenu humain** | Captures dans `public/screenshots/` (galerie) |
| **Bloqué — contenu humain** | Section créateurs / builders sur l'accueil (noms, bios, crédits réels requis — aucun faux nom inventé) |
| **Ops** | Déployer le nouveau JAR (`ServerStatusTask` + `WebFactionBridge` + tables `server_status` / `web_faction_actions`) |
| **Ops** | `.env.local` web (Discord client/secret/bot token, `AUTH_SECRET`, `DATABASE_URL` ou `DB_*`, `CRON_SECRET`, optionnel `MAP_PROVIDER_URL`) + redirect OAuth `…/api/auth/discord/callback` + cron quotidien `/api/cron/snapshot` |
| **Ops — urgent** | Login Discord prod cassé (`echange_impossible`) : **ajouter `DISCORD_CLIENT_SECRET` sur Vercel Production** (absent au 2026-08-11) ; aligner `AUTH_URL=https://www.cantale.world` + Redirect URI Discord identique ; redeploy. Secret aussi stocké côté plugin : `discord.client-secret` dans `src/main/resources/config.yml` (OAuth app, ≠ token bot) |
| **Ops — urgent** | **Redeploy site** : skins profil cassés (prod sert encore Crafatar 521) ; code HEAD a le proxy `/api/minecraft/*` + `/joueur/[uuid]` — sans redeploy Anox26 et tous les profils restent en image brisée |
| **Ops** | Site prod : fix MySQL (`DB_*` fallback + env runtime) + menu desktop « Plus » ✓ 2026-08-11 (`Annonnyx/Cantale` `48396d8`) — stats/classements OK |
| **Ops** | Carte unifiée `/carte` ✓ 2026-08-12 (`Annonnyx/Cantale` `4a7e92c` → Vercel ; Cantale-MC `ad08cf7`) : Leaflet + tuiles Squaremap (proxy) + overlays, fullscreen, X/Z, focus faction, spawn −67/−144. Optionnel : HTTPS dédié `map.cantale.world` |
| **Ops** | Carte `/carte` warps search + hide players ✓ 2026-08-12 (`Annonnyx/Cantale` `e3f824a` → Vercel Ready ; Cantale-MC `aa475f8`) : recherche faction/tag/warp ; stub proxy `tiles/players.json` ; ops Squaremap `player-tracker.enabled: false` recommandé |
| **Ops** | Carte Spawn = warp ✓ 2026-08-12 (`Annonnyx/Cantale` `d7fd975` → Vercel Ready ; Cantale-MC `21bf081`) : marqueur « Spawn » (−67 · −144) dans `/api/map/markers` + recherche warps (`/spawn`) ; Chèvre creative / coffres alliés inchangés |
| **Ops** | Déploiement + DNS `cantale.world` / `cantale.fun` — fix Vercel 404 (Framework Preset → Next.js, redeploy prod) ✓ 2026-08-10 |
| **Décision humaine** | Valider les prix boutique (1,99/3,49/5,99/9,99 €) + paiement (cantale.store) **avant** d'activer `SHOP_ENABLED` |

- Référence : plan détaillé validé (découpage T0.1 → T10.4) — ne jamais dupliquer une donnée du plugin, lecture seule sur les tables existantes

## Fait
- [x] Carte `/carte` : vue unifiée Squaremap+overlays ; centre /spawn blocs **-67 · -144** (jamais 0,0) ; plein écran ; lien Squaremap ; recherche faction/tag/warp + champs X/Z + Aller ; **pas de joueurs live** (Leaflet + `/map-provider/tiles/players.json` → stub vide) ; canvas en repli sans provider.
- [x] Carte `/carte` (antérieur) : canvas recentre spawn + touche `0` — conservé comme fallback.
- [x] Fix « Depuis le — » sur la liste membres faction (site) : `FactionDAO.addMember` n’écrivait jamais `joined_at` (défaut 0 → tiret). Insert avec timestamp Unix sec ; backfill leaders via `factions.created_at` au démarrage plugin ; autres grades historiques restent « — » (pas de date stockée). Affichage FR déjà via `Intl` (`12 août 2026`).
- [x] Fix skin 3D `/connexion` : Crafatar (HTTP 521) → mc-heads.net puis **proxy same-origin** `/api/minecraft/skin|avatar` (Mojang → textures.minecraft.net, repli crafthead/mc-heads) — viewer + classements/la-liste/factions. **Ops : redeploy Vercel** (prod servait encore le bundle crafatar au 2026-08-11 ; `/joueur/[uuid]` 404 tant que non déployé)
- [x] `/link` (Minecraft) : clic sur le code → copie presse-papiers (`ClickEvent.copyToClipboard` + hover « Clique pour copier »)
- [x] Commande admin `/linkforce <Pseudo MC> <ID Discord>` (Minecraft + Discord) — upsert `discord_links`, permission `cantale.admin` / Discord ADMINISTRATOR, gestion des conflits
- [x] `/unlink confirm` (joueur) + `/forceunlink <joueur>` (admin `cantale.admin`) : délie Discord (`discord_links`) + restes `web_link_codes` ; slash Discord `/unlink` (confirmer) et `/forceunlink` ; rôles Joueur/faction retirés au mieux
- [x] Exclusion publique **Anox26** (owner) : Cantox/vies en circulation + tous les classements (site `public-ranking-exclusions.ts`, API legacy, `/leaderboard` jeu, Discord `/leaderboard` + daily LB, tops votes) — liste centrale réutilisable (UUID optionnel)
- [x] Exclusion publique faction **Ø** (noms `Ø` / `O` / `∅`, case-insensitive) : tops pouvoir/richesse/membres site + Discord `/fleaderboard` + daily LB + `/leaderboard` jeu — listes centrales `PUBLIC_RANKING_EXCLUDED_FACTION_NAMES` / `FACTION_NAMES` ; faction reste en jeu + détail slug OK
- [x] Deploy `Annonnyx/Cantale` `f550770` → Vercel `site-cantale` prod Ready (`site-cantale-3a54jc441…`). Plugin `mvn package` OK (redeploy JAR serveur à faire).
- [x] Fix playtime site : sync BDD à la connexion + toutes les 5 min (avant : uniquement au quit → stats à 0 / périmées)
- [x] Shop : Élytra à 2 000 000 Cantox (`NETHER_END`, sell off, daily-buy-cap 1) — ajouter manuellement si `shop.yml` serveur déjà généré
- [x] Fix vote : toujours 1–3 Cadeaux du Roi (plus de loot Rare/Épique au vote), matching Votifier souple (serveur-prive / serveurliste / top-serveurs), messages publics corrects, `/vote` sans liste Rare|Épique
- [x] Mise à jour des URLs de vote et suppression des sites obsolètes (config.yml)
- [x] Onglet statistiques dans `/profile`
- [x] Message d'obtention de tag invitant à activer dans `/profile`
- [x] Commande admin `/tag color`
- [x] Unification rank / permissions + ajout du grade `ADMIN`
- [x] Fix `/rtp` asynchrone (World#getChunkAtAsync)
- [x] `/rtp` : cooldown 2 min (joueurs / aventuriers) ; VIP+ sans cooldown
- [x] Fix `/discord mp` (compatible nom Discord ET nom MC)
- [x] Commandes `/daily` / `/journalier` sync Discord/Minecraft
- [x] Retrait des codes couleur `§` des messages Discord
- [x] Coffre privé : cache en mémoire pour éviter la perte d'items
- [x] Caisse : surplus de récompenses stocké dans le coffre privé (PC)
- [x] Caisse : annonce publique uniquement pour les caisses Mythique et Légendaire
- [x] Caisse : interactions lore distinctes par type (VOTE boîte aux lettres BARREL/LOOM sans anim inventaire ;
  RARE UI coffre animée ; MYTHIC jet dans l'eau/fontaine ; EPIC rituel balise/table d'enchantement ≠ Mythic ;
  LEGENDARY ticket Discord inchangé) — handlers séparés, lores FR d'usage sur chaque item
- [x] Swap interactions EPIC ↔ MYTHIC : Pièce Mythique = fontaine (THROW_WATER), Médaille du Tournoi = autel (RITUAL_ALTAR)
- [x] Rééquilibrage des tables de butin (moins de vies/Cantalames, plus d'outils/ressources/nourriture, récompenses "pas ouf")
- [x] Suppression des `Statio-Lytras` et `Rune de Fortification` du plugin (items, lore, listener d'utilisation)
- [x] Confirmation : `/crate` est bien réservée aux admins (`cantale.admin`)
- [x] Système anti-cheat modulable (Speed, Fly, NoFall, Step, Timer, Reach, KillAura, AutoClicker, Aim, Rotation, Inventory Checks)

## Fait
- [x] Bot FAQ automatique (Minecraft + Discord) avec moteur de matching local, faq.yml, commandes /faq et /faq-admin, réponses privées en jeu et embeds Discord
- [x] Correction salon chat Discord (redirection des messages MC vers 1521952439052402838)
- [x] Support `/profile <joueur>` pour afficher le profil de n'importe quel joueur
- [x] Système de chat de faction Minecraft (`/f c` et `/fc`)
- [x] Fix faux-positifs AntiCheat (Timer Check : seuil à 28 pkts/s + tampon de lissage)
- [x] Fix faux-positifs AntiCheat (AutoClicker : ban en maintenant clic sur herbe) — CPS dig soft/instant exclu ;
  fenêtre 2s + buffer VL ; FastBreak solide séparé (nuker) ; Multi-Cantool exempté
- [x] Système Anti-AFK par grade (config `afk.timeout-seconds` : 10m Joueur, 15m Aventurier, 30m VIP, 60m Chèvre/Modo ; illimité admin)
## Fait
- [x] Refonte Multi-Cantool : suppression de l'annulation d'événement (plus de desync client/serveur ni de blocs fantômes) + ajustement natif de la vitesse de minage via `PlayerBlockBreakSpeedEvent`
- [x] Bot Discord : sélection de rôles synchronisée sans doublons, catégorie Support `1504899461401546954` pour les tickets, renommage propre à la réouverture des tickets et logs d'arrivée instantanés sur Discord
- [x] Factions → Discord : attribution/retrait automatique du rôle `1504907203075903650` pour les joueurs liés (join/leave/kick/disband, reconnexion, resync au démarrage du bot)

## Fait
- [x] Bot FAQ : matching basé sur les mots de contenu (stop-words ignorés) + seuil 70 + IP = play.cantale.fun
- [x] Bot FAQ : questions explicites uniquement (`require-explicit-question`)
- [x] Vanish : fake quit/join, hidePlayer Paper, retrait tab
- [x] Fix vanish : items en main visibles — retrait de `setInvisible` (laisse équipement vanilla visible) + hidePlayer(plugin) au join / refresh hotbar-armure
- [x] Tickets légendaires numérotés + salon Discord auto
- [x] /profile autre joueur (résolution online → DB → UUID)
- [x] /status live : durée 30 min + try/catch anti-crash scheduler

## Plan — Migration Paper 1.21.4 → 1.21.11
- [x] Bump `pom.xml` → `paper-api:1.21.11-R0.1-SNAPSHOT`
- [x] Mise à jour FAQ / AGENTS (version affichée)
- [x] Compiler + corriger si besoin (OK, 0 erreur)
- [ ] Smoke test sur serveur Paper 1.21.11
- [ ] Déployer le JAR + maj `faq.yml` serveur

## Hotfix urgents (2026-08-10)
- [x] Giveaway Discord : menu « Item custom » cassé (28 options > limite Discord 25) → catégories Pickantaxe/Cantaxe/Multi-Cantool/Autres ; Cantalame via `CantalameManager` à la claim
- [x] Vies à la co : `getLives` fail-closed (plus de fallback fantôme à 3) ; ban PROFILE (UUID) + gate `AsyncPlayerPreLoginEvent` ; `setLives` upsert si 0 ligne
- [x] Staff : `/daily` sans bonus (Modo/Admin/Owner = base 1000 comme joueur normal) ; plus de vies mensuelles staff
- [x] TPA natif : `/tpa` `/tpahere` `/tpyes` `/tpno` (absent du plugin alors que FAQ le documentait)
- [x] `/pc` `/ec` : plus de wipe (resize au lieu d'égalité stricte, saves versionnées, `/ec` = vanilla + migration, nav PDC, vote via service)
- [ ] **Ops** : déployer le nouveau JAR + reparer les lignes stale (`lives > 0` alors que le joueur est ban « plus de vies »)

## Hotfix — ClearLag annonce toujours « 0 items » (2026-08-12)
- [x] **Cause** : pas un compteur cassé. `ServerOptimizer#cleanupEntities` faisait un
  clear silencieux des items ≥ `ground-item-max-age` (5 min) toutes les ~60s, **avant**
  le ClearLag (180s + warnings). Au moment de l’annonce, plus rien d’éligible → « 0 ».
- [x] Fix : l’âge périodique des items est **uniquement** `ClearLagTask` ; l’optimizer
  garde le cull par chunk (`max-items-per-chunk`) hors grâce. Message ClearLag honnête
  (nombre réel, ou « aucun ancien / X protégés », silence si rien au sol).
- [ ] Déployer le JAR.

## Hotfix — loot de mort qui disparaît (2026-08-12)
- [x] **Cause** : `ClearLagTask` supprimait TOUS les items sans grâce ; `ServerOptimizer`
  cullait les Item via `max-entities-per-chunk: 15` en priorisant les **plus récents**
  (loot de mort wipe) + `max-items-per-chunk: 20` sans protection.
  Aucun `setTicksLived` / despawn 40 ticks dans le plugin (si ~2s systématique →
  vérifier aussi `spigot.yml` → `item-despawn-rate`, défaut 6000 = 5 min).
- [x] Fix : grâce 5 min (`clearlag.protect-recent-seconds` /
  `optimization.protect-recent-item-seconds`), items exclus du cull entités/chunk,
  cull items seulement hors grâce, âge sol 5 min, ClearLag min intervalle 60s.
- [ ] Déployer le JAR + fusionner les clés `clearlag.*` / `protect-recent*` dans le
  `config.yml` serveur si l'existant n'a pas les nouvelles entrées.

## Hotfix — peu d'animaux passifs visibles (2026-08-12)
- [x] **Cause** : `ServerOptimizer#cleanupEntities` cullait les `Animals` adultes sous
  `max-entities-per-chunk: 15` (seuls les bébés étaient protégés). ClearLag ne touche
  pas aux mobs ; le spawn n'est pas annulé — c'était bien le cull par chunk.
- [x] Fix temporaire : exclure tous les `Animals` + `Tameable.isTamed()` du cull.
- [x] **Révision (même jour)** : les fermes trop denses (>>15 moutons/chunk) n'étaient
  plus réduites. Les `Animals` **comptent à nouveau** vers le plafond 15. Protégés :
  joueurs, villageois, items, nommés, `Tameable.isTamed()`, NPCs Cantale. Surplus =
  plus vieux d'abord. Helper `cullExcessEntitiesPerChunk` aussi appelé au cycle ClearLag
  (en plus de l'optimizer ~60s). Grâce items / loot de mort inchangée.
- [x] **Stacking passifs (même jour)** : au lieu de cull les Animals, empilement
  (`MobStackManager` / `MobStackListener`) — mouton/vache/poulet/cochon/lapin/champimeuh/chèvre,
  libellé `Mouton ×12`, kill = −1 + loot d'une mort. Animals **exclus** du plafond 15
  (le cap reste pour hostiles / armor stands / bateaux…). Config
  `optimization.mob-stacking.*`. Non empilés : bébés, nommés joueur, tamed, laisse.
  Wiki `afk-clearlag` mis à jour.
- [ ] Déployer le JAR + fusionner `optimization.mob-stacking` dans le `config.yml` serveur.

## En cours / à valider
- [x] ClearLag : intervalle 3 min (180s), premier warning à T-30s uniquement (paliers 30/10/5/3/2/1)
- [x] Fix Multi-Cantool : retire le blocage spawn/warp unique + réadaptation réelle de l'outil + casse 3x3/arbre
- [x] Migration auto schéma `private_chests` (legacy items/location → chest_type/contents)
- [x] Sécurité claims : Pickantaxe/Cantaxe respectent claims sur casses manuelles (+ cleanup code mort)
- [x] Faction : `/f create` claim automatiquement le chunk courant + pose le f-spawn à la position du joueur (bloqué en zone protégée spawn/warp ou déjà claim)
- [x] Multi-Cantool : insta-break étendu à tous les blocs minables (plus seulement bois/terre/laine), protection claims (stockage/culture) vérifiée dans `CustomItemListener#canBreakExtraBlock`
- [x] **Resource pack cassé (cause du bug « le Multi-Cantool ne change pas d'apparence »)** : le pack `Cantale-ResourcePack-full` utilisait encore l'ancien système d'`overrides` (`assets/minecraft/models/item/*.json`), **ignoré par le client depuis Minecraft 1.21.4**. En plus, plusieurs overrides étaient mal placés (Pickantaxe/Multi-Cantool Fer déclarés sur `diamond_pickaxe.json` au lieu d'`iron_pickaxe.json`, idem Cantaxe Fer sur `diamond_axe.json`, Cantalame sur `diamond_sword.json` au lieu de `netherite_sword.json`) et certaines références de modèle étaient cassées (`multicantool_*_3x3` inexistant, `vie_item` au lieu de `vie`, CMD `25` obsolète au lieu de `7001`). Corrections apportées :
  - `pack.mcmeta` : `pack_format` 15 → **75** (Minecraft 1.21.11)
  - Ajout de `assets/minecraft/items/*.json` (nouveau format 1.21.4+, `minecraft:range_dispatch` sur `minecraft:custom_model_data`) pour `iron_pickaxe`, `diamond_pickaxe`, `netherite_pickaxe`, `iron_axe`, `diamond_axe`, `netherite_axe`, `iron_shovel`, `diamond_shovel`, `netherite_shovel`, `shears`, `netherite_sword`, `totem_of_undying`
  - Nettoyage des `overrides` morts/mal placés dans les anciens `models/item/*.json`
  - `resourcepack.zip` régénéré à la racine (SHA-1 à mettre à jour dans `config.yml` → voir ci-dessous)
- [x] **Resource pack 1.21.11 régénéré** : `resourcepack.zip` + `cantale-resourcepack.zip` à la racine,
  `pack_format` 75, SHA-1 `b61a7a7db750352711075a10ab14cca42a12ceb0`
- [x] **Hébergement pack** : `server.properties` seul + URL raw GitHub.
  - Fix « Resource reload failed » (cause réelle) : `font/default.json` avait des chemins
    `minecraft:textures/gui/...` → Minecraft double en `textures/textures/...` →
    **Default font failed to load**. Corrigé en `minecraft:gui/sprites/hud/...`.
    SHA = `ebeb3b899e72e0dcdd37bf959cce6d41e25964c6`.
  - Plugin : `send-on-join: false` + url/sha1 **vides**.
- [ ] Tester le cache des coffres privés en jeu (ouverture, fermeture, stockage depuis une caisse)
- [ ] Surveillance des performances du stockage PC suite au changement
- [ ] Tester les détections anti-cheat en jeu et ajuster les seuils dans config.yml si besoin
- [ ] Tester en jeu : /f map, /f go, détection claims, vote, multicantool (apparence + casse tous blocs), PC navigation, commandes owner, kills pouvoir, création faction (claim auto + f-spawn)
- [ ] Déployer nouveau JAR + **mettre à jour** `plugins/CantalePlugin/faq.yml` sur le serveur (sinon l'ancien fichier garde le vieux matching)

## Admin Shop (Cantox)
- [x] Implémentation complète : `/shop` (aliases `/adminshop`, `/boutique`), UI (`ShopMainUI` → catégories →
  `ShopCategoryUI` paginée, achat clic gauche/vente clic droit, Shift = x64/tout), `ShopManager`/`ShopConfig`/
  `ShopDAO`, catalogue et prix réels dans `src/main/resources/shop.yml`. Voir [docs/AdminShopPlan.md](docs/AdminShopPlan.md)
  pour la méthodologie complète (§10 pour la grille de prix V1).
- [x] Fix bouton Retour shop : `BaseUI.onClose` remettait `registered=true` après unregister → `goBack` rouvrait
  le menu sans ré-enregistrer les clics (navigation morte). Fix : `registered = false` dans `onClose`.
- [x] Fix pagination shop (prev/next) : ouvrir un **nouveau** `ShopCategoryUI` par page enregistrait le
  listener puis `openInventory` fermait la page N — même titre → `onClose` sync pouvait
  `unregisterAll` la page venant d'ouvrir (page 0→1 OK visuellement, clics morts ensuite :
  suivante/retour HS). Le match par identité seule était incomplet. Fix durable :
  pagination **in-place** (`setPage` + `init`/`clear`, même inventaire/listener) → Retour =
  `ShopMainUI` depuis toute page ; `BaseUI.onClose` désenregistre au tick suivant seulement si
  l'inventaire n'est plus ouvert.
- [x] Fix catégorie **BOIS** (« Bois & Déco ») qui ne s'ouvrait pas : les patterns `_STEM` / `_WOOD` / etc.
  matchaient aussi des `Material` non-items (`MELON_STEM`, `ATTACHED_*_STEM`, `BIG_DRIPLEAF_STEM`) et
  `LEGACY_*` → `ItemStack` illégal dans `ShopCategoryUI.init()` (exception, menu qui ne s'ouvre pas).
  Fix : `ShopConfig.load()` ignore `material.isLegacy() || !material.isItem()`.
- [x] Hoppers retirés de la vente shop (`shop.yml`) — lag serveur si achat en masse.
- [x] **Hoppers anti-lag (août 2026)** : craft bloqué (`HopperLimitListener` — HOPPER +
  HOPPER_MINECART) ; `/shop` HOPPER **150 000** Cantox, sell off, `daily-buy-cap: 4` ;
  pose uniquement en claim de sa fac, plafond `max(0, pouvoir / 20)`
  (`factions.hopper-limit` dans `config.yml`). Place-only (pas de retrait à l'unclaim).
  Wiki économie + factions mis à jour. `/shop reload` + reload plugin pour appliquer.
- [x] **Hoppers action bar + faction obligatoire (août 2026)** : pose refusée hors faction ;
  action bar Paper tant qu'un hopper est en main (`Hoppers: x/max · restants: n`, sinon motif
  « rejoins une faction » / « claim de ta fac requis »). Formule inchangée.
- [x] **Guide votes sites FR** : `docs/Votes.md` (quoi activer sur Top-Serveurs / Serveur-Prive /
  ServeurListe : Votifier IP/port/clé|token) + logs boot VoteListener plus explicites.
- [x] **Action bar hoppers** : tenir un hopper en main → `Hoppers: X/Y (Z restants)` au-dessus
  de la barre d'XP ; sans faction → message court ; refresh slot/swap/inv/pose/casse + tick 1s.
  Docs vote sites 2&3 (Serveur-Prive / ServeurListe) : checklist Votifier dans `VOTE_SITES_GUIDE.md` §7.
- [x] Prix d'achat shop ×2 (100%+) sur toutes les catégories **sauf `MINERAIS`** (charbon→émeraude
  inchangés V1). Netherite (scrap/ingot/bloc/debris) et Shulker (shell/box) : achat à prix massifs +
  `daily-buy-cap` (voir `shop.yml`). Ventes inchangées. `/shop reload` pour appliquer.
- [x] Prix d'achat **Guerre & PvP** (`GUERRE`) ×12 (TNT, OBSIDIAN, ARROW, SHIELD + pattern `_BED`).
  Vente OBSIDIAN inchangée. MINERAIS / netherite / shulker non touchés. `/shop reload`.
- [x] Rééquilibrage post-×2 (`shop.yml`) : ventes farm / drops basiques plafonnées à **1 Cantox**
  (agriculture, laines, drops mobs, chorus) ; achats non-ultra baissés ~15–25% (netherite / shulker
  inchangés). `/shop reload` pour appliquer.
- [x] `END_CRYSTAL` ajouté au shop (`NETHER_END`) : buy **100 000**, sell désactivé, `daily-buy-cap: 8`.
  Pas de blacklist code (`ShopConfig.HARD_BLACKLIST`). `/shop reload` pour appliquer.
- [ ] **Tester en jeu** : achat/vente de chaque catégorie, plafonds quotidiens, reset à minuit, Shift-clic,
  `/shop reload`, comportement inventaire plein à l'achat, Retour catégories → navigation OK.
- [ ] **Décision à prendre (pas appliquée automatiquement)** : les bonus Cantox des grades payants sont très
  généreux au regard des prix du shop (`/daily` : base 1000 + Aventurier +2500, VIP +10000, Chèvre +100000 —
  ces montants sont annoncés tels quels sur le site boutique). Un Chèvre peut gagner ~101 000 Cantox/jour
  rien qu'en se connectant. Voir [docs/AdminShopPlan.md](docs/AdminShopPlan.md) (§10.2) pour la recommandation
  chiffrée — nécessite de mettre à jour `PlayerRank.java` **et** la page grades du site (les montants y sont
  affichés comme argument de vente), donc à valider avant toute modification.

## Discord — Bugs
- [x] **Fix `/giveaway` → « CANTALE n'a pas répondu à temps » au clic sur 🚀 Lancer** :
  `GiveawayManager#launchGiveaway` n'acquittait le clic bouton (`event.reply`) qu'à l'intérieur du callback
  de succès de `channel.sendMessage(...).queue(...)` — un second appel réseau Discord distinct. Si cet envoi
  dépassait la fenêtre de 3s imposée par Discord pour l'accusé de réception d'une interaction, l'interaction
  expirait avec cette erreur (indépendamment du contenu du giveaway configuré). Corrigé : `event.deferReply()`
  immédiat avant l'envoi du message public, puis `event.getHook().sendMessage(...)` pour la confirmation ;
  ajout d'un callback d'erreur sur l'envoi (message d'échec au lieu de rester bloqué).
- [x] **Fix `/giveaway` → « Ce salon ne permet pas de lancer un giveaway » dans les salons annonce** :
  `asTextChannel()` rejetait les `NewsChannel` (salons annonce Discord). Corrigé en acceptant
  `GuildMessageChannel` (TextChannel + NewsChannel). Pas de whitelist de salon trop restrictive.
- [x] **Fix noms salons tickets illisibles** : Discord normalisait `[OPEN]`/`[CLOSED]` → les
  renommages concaténaient au lieu de remplacer. Convention `open-<raison>-<demandeur>` /
  `closed-…` avec réécriture complète à chaque close/reopen (site web aligné `open-…`).
- [x] **Rôles Discord par faction** : à la création MC, rôle Discord au nom de la fac
  (`discord_role_id` en DB) ; attribué aux comptes liés membres ; retiré au leave ; rôle
  supprimé à la dissolution. Backfill au démarrage du bot pour les facs existantes.

## Anti-cheat — refonte Grim + natif (août 2026)
- [x] FP Speed : grâce 3s post-TP, exemptions élytra/riptide/véhicules/vol, tolérances eau/dolphin/soul speed
- [x] Aim/Rotation assouplis (buffer + seuils) ; Reach œil→AABB + ping
- [x] **Refonte complète (Grim + natif repositionné)** :
  - [x] Grim AC (packet-based) = moteur principal mouvement/combat/rotation/packets ; JARs vérifiés dans `server-plugins/` (PacketEvents 2.13.0 + Grim 2.3.74, MC 1.21.11)
  - [x] GrimBridge (`anticheat/grim/GrimBridge.java`) : flags Grim → trust/alertes/Discord/BDD natifs ; exemptions staff (admin/vanish/trace) annulent le flag Grim ; **fallback auto** : Grim absent → checks natifs mouvement/combat réactivés à chaud
  - [x] Fix FP natifs : NoFall réécrit (dégât de chute attendu, jamais `getFallDistance()` à l'impact ; exemptions MLG/slime/lit/hay/powder snow) ; Timer = packets position-only + compensation TPS/ping ; exemption vélocité 500 ms (knockback/explosion) pour Speed/Fly/Step
  - [x] Anti-cascade trust : 1 pénalité max/tick (la plus grosse gagne) + plafond 10 pts/min glissante ; **ban auto à trust 0 supprimé** (alerte staff prioritaire + kick cooldown 5 min) ; tempban auto optionnel sur récidive (`punishments.ban-auto: false` par défaut)
  - [x] Natif repositionné : Speed/Fly/NoFall/Step/Timer/Reach/KillAura/Aim/Rotation désactivés (doublons Grim) ; AutoArmor (4 équipements/500 ms), ChestStealer (6 prises/500 ms, contenants vanilla seuls), FastUse (état par joueur) corrigés, gardés off pendant rodage ; FastBreak conservé (outils custom)
  - [x] Robustesse : ViolationManager synchronisé + decay sync ; `/ac toggle <check>` runtime réel ; préférence alertes purgée au quit ; `getSeverityMultiplier` supprimé
  - [x] Preuves : table `anticheat_violations` (source/check/VL/trust/ping/TPS/détails) + `/ac info` enrichi (moteur, ping, TPS, kicks 24 h, violations BDD) ; alertes Discord activées
  - [x] Config : blocs `anticheat.lag`, `anticheat.punishments`, `anticheat.grim-integration` ; docs `docs/AntiCheatPlan.md` + `docs/AntiCheat-Grim-Setup.md` (rodage 3 semaines + seuils finaux)
- [ ] **Déploiement manuel** : copier `server-plugins/packetevents-spigot-2.13.0.jar` + `grimac-bukkit-2.3.74.jar` dans `plugins/`, configurer `plugins/GrimAC/punishments.yml` en log-only (modèle dans `docs/AntiCheat-Grim-Setup.md`), redémarrer
- [ ] Rodage 3 semaines : log-only → setbacks Grim → kicks natifs → `ban-auto: true` après validation FP ≈ 0 (voir guide)

## Optimisation serveur — refonte complète (août 2026)
- [x] **Assainissement** : `cleanupEntities()` en **1 seule passe** par monde (avant : 3–4 scans complets) ; tâche `optimizeChunks` no-op supprimée ; clés mortes retirées (`optimize-chunks`, `ground-item-max-age-seconds`, `tps-warning-threshold`) ; `/optimize on|off` **persisté** (`optimization.enabled`) ; `/optimize gc` (`System.gc()`, contre-productif avec G1GC) et `/optimize chunks` (no-op) retirés.
- [x] **PerformanceGovernor** (`optimization/PerformanceGovernor.java`) : échantillonne TPS 1 min + MSPT (Paper `getAverageTickTime()`) toutes les 5 s ; 3 modes **NORMAL / DEGRADED / CRITICAL** avec hystérésis (min 30 s par mode + 60 s sains pour remonter, un cran à la fois). Actions bornées et restaurées : `World#setSimulationDistance` (base−2/−3, min 4/3), `setViewDistance` (optionnel, CRITICAL seulement), `SpawnerManager.setRateMultiplier` (×1.5/×2.0 sur l'intervalle de spawn), cadence mob-stack 10→5 s, CRITICAL = `emergencyCleanup()` (cooldown 2 min) + alerte staff MC + embed Discord mod-logs (cooldown 5 min). Override manuel `/optimize mode <auto|normal|degraded|critical>`.
- [x] **ChunkPreloadCache fixé** : clé exacte (record monde/x/z — plus de collision hashCode) + map bornée 10 000 entrées (éviction FIFO, plus de fuite mémoire). `ChunkLoader` : rayon téléport configurable (`optimization.chunk-preload-radius`, défaut 2) + **préchauffage async du spawn au boot** (`spawn-prewarm-radius`, défaut 6) ; `maintainClaimChunks()` no-op remplacé. `AsyncTaskManager` : réellement utilisé (hotspots), shutdown avec attente.
- [x] **Diagnostic in-game** : `/optimize stats` enrichi (TPS 1m/5m/15m, MSPT, mode gouverneur, mémoire + GC via MXBeans, chunks/entités/tile-entities/distances par monde) ; `/optimize hotspots [n]` (snapshot sync → tri async → top N chunks par entités+tiles avec coords) ; log `[Santé]` une ligne toutes les 5 min.
- [x] **Templates configs serveur** (`server-config/`) : extraits commentés FR pour `server.properties` / `bukkit.yml` / `spigot.yml` / `paper-global.yml` / `paper-world-defaults.yml` (sources : guide YouHaveTrouble 1.21.11 + docs Paper officielles) + README (ordre d'application, options OPTIONNEL marquées, retour arrière).
- [x] **Docs** : `docs/ServerOptimization.md` (architecture, guide déploiement, **flags JVM Aikar G1GC**, Spark diagnostic, Chunky pré-génération, anti-patterns à ne pas installer) + `docs/Performance.md` réécrit (architecture réelle) + `COMMANDS.md` (`/optimize` à jour). Redis documenté dormant (mono-serveur).
- [ ] **Ops** : déployer le JAR ; merger le bloc `optimization.governor` dans le `config.yml` live (défauts Java sinon) ; backup puis fusion des `server-config/*.extra` dans les fichiers serveur (ordre du README, options OPTIONNEL ensuite) ; vérifier les flags JVM Aikar au démarrage ; vérifier au boot `[Governor] Actif` + `[ChunkLoader] Préchauffage spawn` ; en jeu `/optimize stats` / `governor` / `hotspots` ; mesurer 24–48 h (`/mspt`, Spark).

## Hotfix — pose spawner `IllegalArgumentException` min/max spawn delay (2026-08-15)
- [x] **Cause** : `SpawnerManager.applyBlockState` appelait `setMinSpawnDelay(MAX_VALUE)` alors
  que le max vanilla était encore 800 → validation CraftBukkit `min <= max` levait
  `IllegalArgumentException: Minimum Spawn Delay must be less than or equal to Maximum Spawn Delay`
  à chaque pose de spawner Cantale (BlockPlaceEvent, `place()`).
- [x] Fix : `setMaxSpawnDelay(Integer.MAX_VALUE)` **avant** `setMinSpawnDelay(Integer.MAX_VALUE)`.
- [ ] Déployer le JAR (`target/cantale-plugin-1.0.0-SNAPSHOT.jar`).

## Respawn & claims
- [x] Invulnérabilité 10s au respawn (anti spawn-kill)
- [x] Saturation au respawn : `PotionEffectType.SATURATION` amp 0, durée `lives.respawn-saturation-seconds` (défaut 150 ≈ 2,5 min) ; refresh à chaque respawn (PlayerListener)
- [x] Stockage (liste `claim-protection.storage-blocks`) posable uniquement dans les claims de sa faction
- [x] Exception wilderness : `CRAFTING_TABLE` + fours (`FURNACE`, `BLAST_FURNACE`, `SMOKER`) posables hors claim (`ClaimListener`) ; autres ateliers/stockage inchangés ; casse storage en claim inchangée
- [x] Hors claim : coffres/conteneurs ouvrables par tous (structures) ; pose stockage interdite (sauf table de craft + fours)
- [x] PvP claims : safe dans le claim de sa fac ; on peut tuer les non-membres présents dans son claim ; PASDIC = no PvP
- [x] `/f bank power [n]` : achat de pouvoir via banque (officiers/chef), prix exponentiel (`cost = floor(base * growth^(power_purchased * 10))`)
- [x] **GUI pouvoir** (`FactionPowerShopUI`) : `/f power`, `/f bank power` (sans n), bouton `/f` + `/f bank` —
  quotas claims/warps, paliers suivants, achat 1/2/3/5/max (confirm) ; chat `/f bank power <n>` conservé.
  Voir = membres ; acheter = officiers/chef/BANK_TAKE. Wiki + faq + `plugin.yml`.
- [x] **Historique banque** (`FactionBankHistoryUI`, août 2026) : bouton livre dans `/f bank` —
  table `faction_bank_log` (uuid/nom, type, montant, solde après, note, timestamp) ; logs dépôt/retrait
  (`BankManager`), achat pouvoir (`FactionPowerShop`), admin add/removebank ; GUI paginée (28/page).
- [x] **Achat pouvoir ×10** (août 2026) : même formule / prix listés ; 1 unité → **+10 pouvoir**,
  `power_purchased` += unités seulement (`power-per-unit: 10` dans config). GUI/lore/chat/faq/wiki
  clarifiés (« Acheter 1 → +10 pouvoir »). `FactionPowerShop`, `FactionPowerShopUI`, `FactionBankCommand`.
- [x] **Pouvoir par membre** : base création **60** + **+5** par membre (chef inclus → fac neuve = **65**) ;
  join (`FactionManager#addMember`) **+5** ; leave/kick (`#removeMember`) **-5** (plancher 0 + ajustement claims).
  Pas de double-compte à la création (leader via DAO, pas via `addMember`). `PowerManager` quotidien non branché.

## Chat web (août 2026)
- [x] Tables `web_chat_messages` + `web_chat_outbox` ; log MC/Discord → fil ; `WebChatBridge` (outbox → broadcast `[Web]`)
- [x] Site `/chat` + `GET/POST /api/chat` : lecture publique, envoi si compte Discord lié ; nav/footer/sitemap
- [x] Chat faction : `faction_id` sur messages/outbox ; onglet Faction sur `/chat` ; log depuis `sendFactionMessage` ; en jeu `/fc`
- [x] Tag faction auto 3 → 4 caractères à la création
- [x] Page `/admin` (env `ADMIN_DISCORD_ID`) : online JSON, money/vies/items/ban/unban/msg/DM, tickets Discord ; `web_admin_actions` + `WebAdminBridge`
- [x] Admin inspect : inventaire / armure / offhand / PC1–3 / EC / effets (+ contenu shulker/bundle) → `inspect_player` + `web_admin_snapshots`
- [x] Admin édition : clear conteneurs, remove slot (y compris dans shulker), vider shulker, give/clear effets (`clear_container` / `remove_slot` / `clear_shulker` / `give_effect` / `clear_effect`)
- [x] Admin liste **tous** les joueurs (table `players`, online + offline) : `GET /api/admin/players` (recherche + pagination), badges online via `server_status.online_json`, clic → profil
- [x] **Ops 2026-08-11** : liste admin « vide » en prod — Vercel `site-cantale` build **`Annonnyx/Cantale`** (standalone web, Root `.`), pas `Cantale-MC`. Prod bloquée sur ancien admin « En ligne only » (`34be1fe`). Fix poussé `Annonnyx/Cantale` `a3b0af2` (route `/api/admin/players` live → 401 sans cookie). UI affiche l’erreur si 404. `ADMIN_DISCORD_ID` déjà présent en Production.
- [x] Profil public `/joueur/[uuid]` (stats, vies, faction, online) ; si admin (`ADMIN_DISCORD_ID`) : panneau Direction (inspect/edit via le même pont admin). Liens joueur sur classements, stats, la-liste, factions, admin
- [ ] Déployer JAR (inspect + édition inventaires + bridges + ServerStatusTask online_json) ; hard-refresh `/admin` après login Discord admin
- [x] Carte : MySQL site (`DATABASE_URL` ou `DB_*`) pour claims/markers ; vue unifiée Leaflet + tuiles Squaremap (`MAP_PROVIDER_URL` + proxy `/map-provider/*`) + overlays ; fullscreen ; lien externe ; focus faction / X·Z ; spawn −67/−144
- [ ] Ops carte (optionnel) : HTTPS dédié Squaremap (`map.cantale.world` / Cloudflare) pour remplacer le proxy Vercel si latence tuiles ou limites edge

## Wiki — réécriture pratique (août 2026)
- [x] **Slice Combat & Vies** (`wiki-content.ts`) : `trois-vies` + `primes-wanted` + section PvP claims — chiffres depuis `LifeManager` / `config.yml` / `WantedManager` / `ClaimListener` / ranks / caisses (pas d'invention ; buy-price Cantox non branché documenté comme tel).
- [x] **Slice Events / TP / AFK / carte** (`wiki-content.ts`) depuis code only :
  - `events-faction` / `reactions-chat` / `recompenses-regulieres` ← `events.yml` + `FactionEventManager` / `ChatReactionManager` / `WeeklyKeyDropManager` / `/recolte` UI (récompenses top 5 exactes, calendriers, `/events` = warps staff ≠ events fac auto)
  - `teleportation` : `/rtp` cooldown 2 min (VIP+ / bypass), distance 500–5000, warmup 3 s ; warps cooldown 20 s
  - `afk-clearlag` : AFK 10/15/30/60 min par grade (`AfkListener` + `afk.timeout-seconds`) ; admin illimité ; clear-lag 180 s + warnings 30/10/5/3/2/1 (`ClearLagTask`)
  - `claims-territoire` + `discord-site` : page `/carte` (claims, PASDIC, warps, secret exclu, refresh 30 s)
  - Compteur wiki : **25 articles** / 7 catégories
- [x] **Slice Commandes / grades / liaison** (`wiki-content.ts`) depuis code only :
  - `commandes-joueur` : essentiels hors `/f` (éco, coffres, TP aperçu, vies, classements, link) + renvoi Factions
  - `teleportation` : spawn/home cooldown 20 s, TPA 60 s, homes par grade, précision Chèvre ≠ bypass cooldown 20 s
  - `grades-permissions` : tableaux PlayerRank / RankManager / PrivateChestService / Feed / RTP / vol claim
  - `discord-site` : flux `/link` MC (copie clic, 10 min) → Discord `/link` ; `/unlink confirm` + `/forceunlink` admin ; `/linkforce` admin ; compte site OAuth `/connexion` ; `/web link` obsolète (redir. URL)
  - `coffres-inventaires` : 1/2/3 coffres, 27→54 slots
- [x] Redeploy site pour publier le wiki mis à jour (`Annonnyx/Cantale` `1328221` / Vercel `site-cantale` prod Ready)

## Combat — Bugs
- [x] **Fix déco combat : message affiché mais vie non retirée** : `CombatTagManager#handleCombatLog`
  appelait `LifeManager#handleDeath`, qui exempte `cantale.admin` (`default: op`). Sur un serveur de test
  (amis OP), le broadcast « perd 1 vie » partait mais la vie n'était pas décrémentée. Corrigé via
  `LifeManager#handleCombatDisconnect` (pénalité toujours appliquée) + ban IP safe si adresse null au quit
  + mise à 0 des vies avant ban dernière vie. À tester en jeu.

## Combat — Zones no-PvP + tag (août 2026)
- [x] **Design** : pendant le tag combat (20 s, `combat-tag-seconds`), **interdiction d'entrer** en zone no-PvP
  (PASDIC / claim de sa propre fac) — mouvement, ender pearl, portails Nether/End.
  Impossible d'**initier** du PvP en PASDIC / claim de la victime (`ClaimListener`, inchangé + message PASDIC).
- [x] **Cas limite documenté** : déjà dans une zone no-PvP pendant un tag → tag continue,
  coups toujours annulés, sortie libre, ré-entrée bloquée (`CombatTagManager#isNoPvpZone` + wiki).
- [x] Wiki : `trois-vies` (tag), `claims-territoire` (PvP + PASDIC).
- [ ] Tester en jeu : fuite vers spawn/PASDIC / claim perso pendant tag ; pearl vers PASDIC ;
  initiation PvP en PASDIC ; sortie puis ré-entrée.

## Commandes VIP (août 2026)
- [x] `/feed` : restaure faim + saturation. Accès VIP+ via `PlayerRank#isAtLeastVIP()` (ou `cantale.feed` / `cantale.admin`). Cooldown VIP 3 min, Chèvre+ 1 min, `cantale.admin` sans cooldown.

## Dépréciation /web link (août 2026)
- [x] `/web link` obsolète : `WebCommand` affiche uniquement https://www.cantale.world/connexion (+ rappel `/link` Discord). Fichiers Java conservés (`WebCommand`, `WebLinkDAO`).
- [x] Docs / FAQ / wiki / `COMMANDS.md` / `login.html` / backend auth messages mis à jour. `/link` Discord inchangé.

## Commandes staff inspection (août 2026)
- [x] `/adminhome check|list <joueur>` — liste les homes ; `/adminhome <joueur> <nom>` ou `/adminhome tp <joueur> <nom>` — TP staff immédiat. Aliases `ahome`/`seehome`. Perm `cantale.moderator` (+ grade staff).
- [x] `/adminec <joueur>` — EC live si online ; hors ligne : pas d'API `OfflinePlayer#getEnderChest` (Paper) → legacy DB seulement. Aliases `aec`/`seeec`.
- [x] `/adminpc <joueur> [1|2|3]` — PC DB/cache éditable online/offline (`openForeignChest`). Aliases `apc`/`sepc`.
- [x] Docs : `plugin.yml`, `COMMANDS.md`, `docs/Commands.md`, wiki `grades-permissions` + `coffres-inventaires`, `/modhelp`.

## BossBar infos joueur (août 2026)
- [x] **BossBar rotative personnelle** : une barre par joueur qui cycle Cantox → Vies → Wanted
  (si prime) → Event faction (si actif) → Réaction chat (si active) → Combat (si tagué).
  Intervalle configurable dans `events.yml` → `player-info-bossbar` (défaut 5s). Classe
  `RotatingBossBarManager`, join/quit auto, cleanup `onDisable`. Messages FR style Cantale.
- [x] **Toggle BossBar depuis `/profile`** : colonne DB `boss_bar_enabled` (défaut 1), item
  beacon slot 27, attach/detach immédiat + respecté au join.
- [ ] Tester en jeu la rotation (solde, vies, prime, event, réaction, combat) + toggle profil.

## Événements & Armure du Garde (août 2026)
- [x] **Armure du Garde** : 4 pièces `GARDE_HELMET/CHESTPLATE/LEGGINGS/BOOTS` via `/customitem`, netherite,
  enchants (Prot 9, Thorns 5, Unbreaking 10, Mending + extras), CMD 9001–9004, bonus set via
  `AttributeModifier` joueur (cumulatif 25%/pièce → +12 HP / +30% speed / +25% jump). Listener
  `GardeArmorListener`.
- [x] **Réaction chat** : scramble FR + premier à casser un bloc, max **9**/jour (`events.yml`), 200–500 Cantox,
  colonne DB `chat_reactions`, leaderboard MC + Discord + daily task (salon optionnel).
- [x] **Événements faction** : HARVEST/MINING/PVP, 3–4×/semaine, 1h, 14h–22h Europe/Paris, sidebar
  scoreboard, `/recolte` + UI dépôt, top 5 power + Cantox. Chaque HARVEST/MINING cible **une**
  ressource tirée au sort (ex. « Récolte de blé », « Minage de diamant ») — pas tout le pool.
- [x] **Pacing events faction** : `min-hours-between-events: 8` + `max-events-per-day: 1` dans
  `events.yml` ; planning hebdo espace les créneaux (durée + gap) ; runtime refuse un départ trop tôt
  après la fin du précédent (plus de 2 events à quelques minutes d'intervalle).
- [x] **Drop clé hebdo** : 1 joueur online aléatoire, clé RARE/EPIC/MYTHIC (1/3), annonce serveur.
- [x] Config dédiée `events.yml` (pas de modif config.yml).
- [x] **Manuel** : ajouter `discord.channels.lb-chat-reactions` dans `config.yml` (`1534941401496686762`).
- [x] **Resource pack** : dispatch CMD 9001–9004 ajouté (`netherite_*` items + modèles `garde_*` placeholder vanilla).
- [ ] **Manuel art** : suivre `docs/BlockbenchArtGuide.md` (caisses, garde, outils, HUD, GUI) puis rezipper + SHA-1.
- [ ] Tester en jeu les 4 systèmes (équipement armure, réactions, event faction, drop clé).

## Caisses — Emplacements spawn (`crate.yml`) (août 2026)
- [x] Config `crate.yml` : mailbox / chest / altar (locations bloc) + fontaine **zones cubiques** (2 coins x1..z2, min/max auto) ; listes vides = legacy
- [x] `CrateConfig` + wiring `CrateManager` / `CrateInteractionHandler` : RARE = vrai coffre configuré (annule ouverture vanilla) ; VOTE/EPIC filtrés si locations ; MYTHIC = eau dans zone
- [x] Lores / hints FR (Trésor Public au spawn, fontaine au spawn) + messages si mauvais bloc
- [x] `/crate reload` (+ tab-complete)
- [x] **Resource pack stubs caisses** : CMD 100–104 (`paper` / `tripwire_hook` / `sunflower` / `gold_nugget` /
  `writable_book`) via `assets/minecraft/items/*.json` + modèles `cantale:item/crate_*` avec fallback texture
  **vanilla** (même pattern que garde). Fusion : paper += 6001 rune ; gold_nugget += 24 rune + 8001 cantox.
- [ ] **Manuel** : remplacer `layer0` vanilla par `cantale:item/crate_*` + PNG Blockbench dans
  `assets/cantale/textures/item/crate_*.png`, puis **rezipper** le pack (voir RESOURCE_PACK_GUIDE.md /
  `textures/item/README.md`).
- [ ] **Manuel** : remplir `plugins/CantalePlugin/crate.yml` avec les coords spawn (F3 Looking at / 2 coins fontaine) puis `/crate reload`
- [ ] Tester en jeu : clef sur coffre spawn, boîte aux lettres, autel, jet pièce dans zone fontaine uniquement
