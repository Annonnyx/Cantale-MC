# TODO Cantale

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
- [x] Pages livrées : Règlement (28 règles, sommaire sticky, tampons sévérité), Partenariats (formulaire contact + ticket Discord REST `partenariat-*`, même pipeline que recrutement), Recrutement (formulaire 4 étapes + ticket Discord REST compatible `/ticket close`), Wiki (21 articles / 7 catégories, recherche ⌘K), Items (13 entrées depuis `CustomItemType` — Rune de Fortification conservée car toujours active, Statio-Lytras exclus), La Liste (mémorial), Vote (3 sites, minuteurs live), Classements (métrique × période, snapshots `web_snapshots_daily` + cron `CRON_SECRET`, podium, « ta position »), Stats (compteurs live + charts CSS), Factions (annuaire, détail ★◆●○, candidatures `web_faction_applications`, panneau leader → file `web_faction_actions`), Boutique (catalogue complet, flag `SHOP_ENABLED` bloquant côté serveur, checkout 501 en attendant la décision cantale.store)
- [x] Build de production vert + ESLint 0 erreur sur tout `src`
- [x] Page `/carte` : canvas interactif (claims + warps + zones PASDIC protégées), API `/api/map/claims` + `/api/map/markers`, factions secrètes exclues en SQL, rafraîchissement 30s, onglet Squaremap/BlueMap uniquement si `MAP_PROVIDER_URL` est défini
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
| **Ops** | `.env.local` web (Discord client/secret/bot token, `AUTH_SECRET`, `DATABASE_URL`, `CRON_SECRET`, optionnel `MAP_PROVIDER_URL`) + redirect OAuth `…/api/auth/discord/callback` + cron quotidien `/api/cron/snapshot` |
| **Ops** | Installer Squaremap/BlueMap sur le serveur et renseigner `MAP_PROVIDER_URL` (onglet « Carte du monde détaillée ») |
| **Ops** | Déploiement + DNS `cantale.world` / `cantale.fun` — fix Vercel 404 (Framework Preset → Next.js, redeploy prod) ✓ 2026-08-10 |
| **Décision humaine** | Valider les prix boutique (1,99/3,49/5,99/9,99 €) + paiement (cantale.store) **avant** d'activer `SHOP_ENABLED` |

- Référence : plan détaillé validé (découpage T0.1 → T10.4) — ne jamais dupliquer une donnée du plugin, lecture seule sur les tables existantes

## Fait
- [x] Fix vote : toujours 1–3 Cadeaux du Roi (plus de loot Rare/Épique au vote), matching Votifier souple (serveur-prive / serveurliste / top-serveurs), messages publics corrects, `/vote` sans liste Rare|Épique
- [x] Mise à jour des URLs de vote et suppression des sites obsolètes (config.yml)
- [x] Onglet statistiques dans `/profile`
- [x] Message d'obtention de tag invitant à activer dans `/profile`
- [x] Commande admin `/tag color`
- [x] Unification rank / permissions + ajout du grade `ADMIN`
- [x] Fix `/rtp` asynchrone (World#getChunkAtAsync)
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
- [x] Système Anti-AFK par grade (10m Joueur, 30m Aventurier, 1h VIP, illimité pour Chèvre/Staff)
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

## Combat — Bugs
- [x] **Fix déco combat : message affiché mais vie non retirée** : `CombatTagManager#handleCombatLog`
  appelait `LifeManager#handleDeath`, qui exempte `cantale.admin` (`default: op`). Sur un serveur de test
  (amis OP), le broadcast « perd 1 vie » partait mais la vie n'était pas décrémentée. Corrigé via
  `LifeManager#handleCombatDisconnect` (pénalité toujours appliquée) + ban IP safe si adresse null au quit
  + mise à 0 des vies avant ban dernière vie. À tester en jeu.

## Commandes VIP (août 2026)
- [x] `/feed` : restaure faim + saturation. Accès VIP+ via `PlayerRank#isAtLeastVIP()` (ou `cantale.feed` / `cantale.admin`). Cooldown VIP 3 min, Chèvre+ 1 min, `cantale.admin` sans cooldown.

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
- [x] **Réaction chat** : scramble FR + premier à casser un bloc, max 8–9/jour, 200–500 Cantox,
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
