# TODO Cantale

## Fait
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

## Fait
- [x] Bot FAQ : matching basé sur les mots de contenu (stop-words ignorés) + seuil 70 + IP = play.cantale.fun
- [x] Bot FAQ : questions explicites uniquement (`require-explicit-question`)
- [x] Vanish : fake quit/join, hidePlayer Paper, retrait tab
- [x] Tickets légendaires numérotés + salon Discord auto
- [x] /profile autre joueur (résolution online → DB → UUID)
- [x] /status live : durée 30 min + try/catch anti-crash scheduler

## Plan — Migration Paper 1.21.4 → 1.21.11
- [x] Bump `pom.xml` → `paper-api:1.21.11-R0.1-SNAPSHOT`
- [x] Mise à jour FAQ / AGENTS (version affichée)
- [x] Compiler + corriger si besoin (OK, 0 erreur)
- [ ] Smoke test sur serveur Paper 1.21.11
- [ ] Déployer le JAR + maj `faq.yml` serveur

## En cours / à valider
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
- [ ] **Action manuelle requise** : héberger le nouveau `resourcepack.zip` (racine du repo) à l'URL configurée dans `config.yml` (`resource-pack.url`) et mettre à jour `resource-pack.sha1` avec le nouveau hash (calculé via `shasum -a 1 resourcepack.zip`). Non fait automatiquement (fichier de config).
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
- [ ] **Tester en jeu** : achat/vente de chaque catégorie, plafonds quotidiens, reset à minuit, Shift-clic,
  `/shop reload`, comportement inventaire plein à l'achat.
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
  ajout d'un callback d'erreur sur l'envoi (message d'échec au lieu de rester bloqué) et garde-fou si le
  salon n'est pas un `TextChannel`. À tester en jeu/Discord pour confirmer.

## Événements & Armure du Garde (août 2026)
- [x] **Armure du Garde** : 4 pièces `GARDE_HELMET/CHESTPLATE/LEGGINGS/BOOTS` via `/customitem`, netherite,
  enchants (Prot 9, Thorns 5, Unbreaking 10, Mending + extras), CMD 9001–9004, bonus set via
  `AttributeModifier` joueur (cumulatif 25%/pièce → +12 HP / +30% speed / +25% jump). Listener
  `GardeArmorListener`.
- [x] **Réaction chat** : scramble FR + premier à casser un bloc, max 8–9/jour, 200–500 Cantox,
  colonne DB `chat_reactions`, leaderboard MC + Discord + daily task (salon optionnel).
- [x] **Événements faction** : HARVEST/MINING/PVP, 3–4×/semaine, 1h, 14h–22h Europe/Paris, sidebar
  scoreboard, `/recolte` + UI dépôt, top 5 power + Cantox.
- [x] **Drop clé hebdo** : 1 joueur online aléatoire, clé RARE/EPIC/MYTHIC (1/3), annonce serveur.
- [x] Config dédiée `events.yml` (pas de modif config.yml).
- [x] **Manuel** : ajouter `discord.channels.lb-chat-reactions` dans `config.yml` (`1534941401496686762`).
- [x] **Resource pack** : dispatch CMD 9001–9004 ajouté (`netherite_*` items + modèles `garde_*` placeholder vanilla).
- [ ] **Manuel** : remplacer les textures placeholder par les PNG custom `garde_helmet/chestplate/leggings/boots.png` dans le pack, régénérer `resourcepack.zip` + SHA-1.
- [ ] Tester en jeu les 4 systèmes (équipement armure, réactions, event faction, drop clé).
