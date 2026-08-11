# TODO Cantale

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
  sources pouvoir (65 création, +5/membre, PvP ±5, bank ×10 exposant, events top, `/admin addpower`),
  quotas claims/fhomes/warps, rangs, wilderness/PASDIC, règles stockage/explosifs.
- [x] Support `WikiTable` dans `wiki-content.ts` + rendu page article + index recherche.
- [x] Alignement `faq.yml` (création, claim/pouvoir, warps, homes, banque) sur les mêmes formules.
- [ ] Redeploy site + redeploy `faq.yml` serveur si besoin.

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
- [x] Page `/carte` : carte unifiée Leaflet (tuiles Squaremap via `/map-provider` + overlays claims/PASDIC/warps), APIs `/api/map/claims` + `/api/map/markers`, factions secrètes exclues en SQL, rafraîchissement 30s, plein écran, lien externe Squaremap, recherche faction + champs X/Z, centre spawn (−67 · −144) ; repli canvas si pas de `MAP_PROVIDER_URL`
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
| **Ops** | Carte unifiée `/carte` ✓ 2026-08-12 : Leaflet + tuiles Squaremap (proxy) + overlays Cantale, fullscreen, X/Z, focus faction, spawn −67/−144 — déployer `Annonnyx/Cantale` (voir commit carte). Optionnel : HTTPS dédié `map.cantale.world` |
| **Ops** | Déploiement + DNS `cantale.world` / `cantale.fun` — fix Vercel 404 (Framework Preset → Next.js, redeploy prod) ✓ 2026-08-10 |
| **Décision humaine** | Valider les prix boutique (1,99/3,49/5,99/9,99 €) + paiement (cantale.store) **avant** d'activer `SHOP_ENABLED` |

- Référence : plan détaillé validé (découpage T0.1 → T10.4) — ne jamais dupliquer une donnée du plugin, lecture seule sur les tables existantes

## Fait
- [x] Carte `/carte` : vue unifiée Squaremap+overlays ; centre /spawn blocs **-67 · -144** (jamais 0,0) ; plein écran ; lien Squaremap ; recherche faction + champs X/Z + Aller ; canvas en repli sans provider.
- [x] Carte `/carte` (antérieur) : canvas recentre spawn + touche `0` — conservé comme fallback.
- [x] Fix « Depuis le — » sur la liste membres faction (site) : `FactionDAO.addMember` n’écrivait jamais `joined_at` (défaut 0 → tiret). Insert avec timestamp Unix sec ; backfill leaders via `factions.created_at` au démarrage plugin ; autres grades historiques restent « — » (pas de date stockée). Affichage FR déjà via `Intl` (`12 août 2026`).
- [x] Fix skin 3D `/connexion` : Crafatar (HTTP 521) → mc-heads.net puis **proxy same-origin** `/api/minecraft/skin|avatar` (Mojang → textures.minecraft.net, repli crafthead/mc-heads) — viewer + classements/la-liste/factions. **Ops : redeploy Vercel** (prod servait encore le bundle crafatar au 2026-08-11 ; `/joueur/[uuid]` 404 tant que non déployé)
- [x] `/link` (Minecraft) : clic sur le code → copie presse-papiers (`ClickEvent.copyToClipboard` + hover « Clique pour copier »)
- [x] Commande admin `/linkforce <Pseudo MC> <ID Discord>` (Minecraft + Discord) — upsert `discord_links`, permission `cantale.admin` / Discord ADMINISTRATOR, gestion des conflits
- [x] Exclusion publique **Anox26** (owner) : Cantox/vies en circulation + tous les classements (site `public-ranking-exclusions.ts`, API legacy, `/leaderboard` jeu, Discord `/leaderboard` + daily LB, tops votes) — liste centrale réutilisable (UUID optionnel)
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
- [x] **Fix noms salons tickets illisibles** : Discord normalisait `[OPEN]`/`[CLOSED]` → les
  renommages concaténaient au lieu de remplacer. Convention `open-<raison>-<demandeur>` /
  `closed-…` avec réécriture complète à chaque close/reopen (site web aligné `open-…`).
- [x] **Rôles Discord par faction** : à la création MC, rôle Discord au nom de la fac
  (`discord_role_id` en DB) ; attribué aux comptes liés membres ; retiré au leave ; rôle
  supprimé à la dissolution. Backfill au démarrage du bot pour les facs existantes.

## Anti-cheat — chantier à venir
- [x] FP Speed : grâce 3s post-TP, exemptions élytra/riptide/véhicules/vol, tolérances eau/dolphin/soul speed
- [x] Aim/Rotation assouplis (buffer + seuils) ; Reach œil→AABB + ping
- [x] Ban automatique à trust 0 (Paper ban API) ; warn/kick si `auto-punish`
- [ ] Tester les détections en jeu et retoucher les seuils si besoin

## Respawn & claims
- [x] Invulnérabilité 10s au respawn (anti spawn-kill)
- [x] Stockage (liste `claim-protection.storage-blocks`) posable uniquement dans les claims de sa faction
- [x] Exception wilderness : `CRAFTING_TABLE` + fours (`FURNACE`, `BLAST_FURNACE`, `SMOKER`) posables hors claim (`ClaimListener`) ; autres ateliers/stockage inchangés ; casse storage en claim inchangée
- [x] Hors claim : coffres/conteneurs ouvrables par tous (structures) ; pose stockage interdite (sauf table de craft + fours)
- [x] PvP claims : safe dans le claim de sa fac ; on peut tuer les non-membres présents dans son claim ; PASDIC = no PvP
- [x] `/f bank power [n]` : achat de pouvoir via banque (officiers/chef), prix exponentiel (`cost = floor(base * growth^(power_purchased * 10))`)
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
  - `afk-clearlag` (nouvel article) : AFK 10/30/60/∞ par grade (`AfkListener`) ; clear-lag 180 s + warnings 30/10/5/3/2/1 (`ClearLagTask`)
  - `claims-territoire` + `discord-site` : page `/carte` (claims, PASDIC, warps, secret exclu, refresh 30 s)
  - Compteur wiki : **25 articles** / 7 catégories
- [x] **Slice Commandes / grades / liaison** (`wiki-content.ts`) depuis code only :
  - `commandes-joueur` : essentiels hors `/f` (éco, coffres, TP aperçu, vies, classements, link) + renvoi Factions
  - `teleportation` : spawn/home cooldown 20 s, TPA 60 s, homes par grade, précision Chèvre ≠ bypass cooldown 20 s
  - `grades-permissions` : tableaux PlayerRank / RankManager / PrivateChestService / Feed / RTP / vol claim
  - `discord-site` : flux `/link` MC (copie clic, 10 min) → Discord `/link` ; `/linkforce` admin ; compte site OAuth `/connexion` ; `/web link` distinct
  - `coffres-inventaires` : 1/2/3 coffres, 27→54 slots
- [x] Redeploy site pour publier le wiki mis à jour (`Annonnyx/Cantale` `1328221` / Vercel `site-cantale` prod Ready)

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
