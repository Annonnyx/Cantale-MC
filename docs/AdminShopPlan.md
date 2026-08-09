# Plan — Admin Shop (Cantox)

> **Statut : implémenté.** Commande `/shop` (aliases `/adminshop`, `/boutique`), UI complète
> (`ShopMainUI` → catégories → `ShopCategoryUI` paginée, achat/vente au clic), catalogue et
> **prix réels** dans `src/main/resources/shop.yml`. Ce document garde la philosophie et les
> décisions de design ; le catalogue chiffré fait foi dans `shop.yml` (modifiable + `/shop reload`,
> sans recompiler).

## 1. Rôle du shop dans l'économie Cantale

Cantale a déjà un marché joueur-à-joueur complet : `/ah` (hôtel des ventes), `/trade` (échange direct),
`/pay` (transfert), `/balance` (Cantox). L'admin shop **ne remplace pas** ce marché. Son rôle est strictement :

1. **Plancher de prix garanti** : utile aux nouveaux joueurs et aux joueurs solo qui n'ont personne à qui
   vendre. Le shop achète toujours au joueur, même si `/ah` est vide ou saturé.
2. **Sink économique** : absorber le Cantox généré par le minage/la récolte (particulièrement accéléré par
   les Pickantaxes 1x1→7x7 et les Cantaxes one-shot) pour éviter l'inflation.
3. **Logistique de guerre** : permettre à un joueur de se rééquiper rapidement après une mort (3 vies
   définitives) sans pour autant rendre la mort insignifiante.

Le shop ne doit jamais devenir plus avantageux que `/ah` pour un vendeur actif — sinon il tue le marché
joueur-à-joueur existant. Les prix de vente du shop doivent rester un filet de sécurité "en dessous" du
marché réel.

## 2. Principes économiques (structure, sans chiffrage)

- **Faucet vs Sink** : tout ce que le shop *achète* aux joueurs crée du Cantox (faucet). Tout ce qu'il
  *vend* en détruit (sink). Objectif : plus de sinks générés que de faucets sur la durée — donc plus de
  catégories "achat seulement" (sink pur) que de catégories "vente illimitée".
- **Achat toujours > Vente**, avec un ratio fixe configurable par catégorie (~x1.5 à x1.8, à calibrer). Un
  ratio insuffisant permet l'arbitrage achat/revente en boucle = argent gratuit.
- **Rareté progressive** : early game (quasi gratuit / infini), mid game (accessible mais plafonné), end
  game (cher et strictement plafonné). Voir §3.
- **Dégressivité sur les ressources à fort volume** (diamant, netherite, bois de masse) : le prix de vente
  et/ou le volume acceptable diminuent au fur et à mesure qu'un joueur (ou le serveur) vend beaucoup en peu
  de temps, avec reset quotidien. Voir §4 pour le mécanisme technique.
- **Impact direct des outils custom sur le pricing** : les Pickantaxes (zones 1x1 à 7x7 sur Fer/Diamant/
  Netherite) et les Cantaxes (arbre entier en un coup) multiplient le débit de récolte par rapport au
  vanilla. Toute ressource minée/récoltée en masse via ces outils doit être en catégorie "plafonnée" (§3/§4),
  jamais en vente libre illimitée.

## 3. Catégories (structure produit, sans prix)

| Catégorie | Politique par défaut | Exemples (non exhaustif) |
|---|---|---|
| Blocs de construction basiques | Vente libre non plafonnée (valeur nulle/quasi nulle) | Terre, sable, gravier, pierre/cobble, netherrack |
| Bois & dérivés | Vente **plafonnée/dégressive** (Cantaxe = récolte de masse) | Bûches, planches |
| Blocs déco (achat seulement) | Achat seulement, pas de rachat | Verre, laine, terracotta, béton |
| Minerais communs (charbon, cuivre, fer, or, redstone, lapis) | Vente plafonnée modérée | — |
| Minerais rares (diamant, émeraude, éclat de netherite, quartz) | Vente **fortement plafonnée/dégressive** — sensibles aux Pickantaxes 7x7 | — |
| Nether/End | Vente plafonnée, certains achat seulement (œil d'Ender, carapace de Shulker) | Tige de Blaze, perle d'Ender, fruit de Chorus |
| Agriculture & nourriture | Vente libre modérée — revenu de base nouveaux joueurs/solo | Blé, carotte, patate, betterave, melon, canne, cacao, nether wart, viande cuite |
| Drops de mobs & élevage | Vente libre modérée | Cuir, laine, plume, œuf, ficelle, os, poudre à canon, membrane de Phantom |
| Redstone & mécanismes | **Achat seulement** (pas une source de farm) | Répéteur, comparateur, piston, observateur |
| Guerre & PvP | Mix achat seulement / vente ciblée — cœur économique du sink de guerre | TNT, obsidienne, flèches, bouclier, potions |

Les catégories "vente plafonnée/dégressive" et "achat seulement" sont celles qui absorbent le plus de Cantox
et protègent contre l'inflation générée par les outils custom — elles doivent être marquées comme telles dès
la configuration initiale, indépendamment des montants choisis plus tard.

## 4. Anti-inflation — mécanisme technique (structure, valeurs à définir plus tard)

Plafond dégressif par joueur et par item, avec reset quotidien :

1. Chaque vente au shop enregistre `(uuid, item, quantité, timestamp)` dans une table dédiée
   (`shop_sell_log` ou compteur agrégé `shop_daily_sales`).
2. Un volume de vente cumulé (par joueur, par item, sur la fenêtre du jour en cours) déclenche une
   dégressivité du prix de vente au-delà d'un seuil configurable (`soft-cap`), et un blocage total
   au-delà d'un plafond dur (`hard-cap`), les deux définis en config, pas en dur dans le code.
3. Reset automatique à minuit (tâche planifiée, comme le `Vote Party` ou le reset `/daily` existants).
4. Le même mécanisme peut s'appliquer à l'échelle **serveur** (pas seulement par joueur) sur les ressources
   les plus sensibles (diamant, netherite) pour empêcher qu'une grosse faction ne fasse fondre le Cantox à
   elle seule avec plusieurs joueurs équipés de Pickantaxe 7x7 en parallèle.
5. Tous les seuils (ratio achat/vente, soft-cap, hard-cap, catégories concernées) sont des **paramètres de
   configuration**, pas des valeurs codées en dur — permet de rescaler sans recompiler.

## 5. Liste noire — jamais achetable ni vendable

À exclure du shop, quelle que soit la mise à jour future des prix (risques d'exploit ou de duplication de
progression) :

- Bedrock, barrières, command blocks (+ chain/repeating), structure block/void, jigsaw
- Debug stick, knowledge book, end portal frame, dragon egg
- Livres écrits/inscriptibles (`written_book`, `writable_book`)
- Tous les **items custom Cantale** : Pickantaxe, Cantaxe, Cantalame, Multi-Cantool, Rune de Fortification —
  ils sont explicitement non-craftables et doivent le rester en dehors d'une vraie boutique/quête/craft
  dédié ; les vendre en Cantox dévaluerait leur statut

Cette liste doit être encodée en **hardblacklist** dans le code (pas seulement absente de la config), pour
qu'un admin ne puisse pas l'ajouter par erreur au catalogue plus tard (voir §7, `ShopManager#isBlacklisted`).

## 6. Décisions de design à trancher par le propriétaire du serveur

Ces choix ne sont **pas** des questions de prix — ils déterminent si l'item apparaît dans le shop du tout.
Recommandation par défaut ci-dessous, à confirmer/modifier :

| Item | Recommandation par défaut | Raison |
|---|---|---|
| Totem d'Immortalité | **Hors shop admin** (loot rare : donjon, boss, vote) | Sur un serveur à 3 vies définitives, un totem peut sauver la dernière vie — le rendre achetable librement réduit la tension du concept "3 vies" |
| Pomme dorée enchantée | **Hors shop** (récompense rare) | Trop forte en PvP (regen + résistance + absorption) ; achetable librement déséquilibre les combats |
| Élytra vanilla | **Hors shop** | Les Statio-Lytras sont déjà l'item de progression prévu pour ça ; un élytra vanilla achetable court-circuite cette progression |
| Spawners | **Hors shop par défaut** — sauf si les Pickantaxes permettent le Silk Touch dessus, auquel cas : item ultra-cher, **achat seulement** (sink de fin de partie) | Normalement inobtenables en vanilla |

Ces décisions doivent être un simple **flag booléen par item dans la config** (`enabled: true/false`), pas
une contrainte codée en dur, pour rester ajustables sans recompiler — sauf la liste noire du §5 qui reste
protégée en dur.

## 7. Architecture technique proposée

Suit les conventions déjà en place dans le plugin (voir `EconomyManager`, `AuctionDAO`/`AuctionManager`,
`ui/inventory/BaseUI`, `config.yml`).

### 7.1 Configuration (implémenté)
- `src/main/resources/shop.yml` : `categories` (id, display-name, icon), `patterns` (règles par sous-chaîne
  du nom de Material — bois, laines, béton/terracotta, lits — pour éviter d'énumérer chaque variante), et
  `items` (entrées exactes, prioritaires sur les patterns). Chaque entrée : `buy`, `sell`, `buy-enabled`,
  `sell-enabled`, `daily-sell-cap`, `daily-buy-cap`.
- `ShopConfig` charge ce fichier et résout, pour **chaque** `Material` du jeu, s'il est négociable (exact >
  pattern > absent = non négociable). Rechargement à chaud via `/shop reload` (`cantale.admin`).

### 7.2 Domaine / persistance (implémenté)
- `ShopItem` (POJO) : material, catégorie, prix achat/vente, flags, plafonds.
- `ShopManager` : `buy(player, material, quantity)` / `sell(...)`, applique les plafonds quotidiens et
  renvoie un `ShopResult` (succès + quantité/montant réellement traités, ou message d'échec).
- `ShopDAO` (table `shop_daily_limits` : uuid, material, date, sold_qty, bought_qty — reset automatique dès
  que la date stockée diffère d'aujourd'hui, même motif que `RankManager#last_cantox_claim`).
- Toutes les transactions passent par `EconomyManager` existant (`withdraw`/`deposit`) — aucun nouveau
  système de solde.
- Liste noire dure (`ShopConfig.HARD_BLACKLIST`) : bedrock, barrières, command blocks (+ chain/repeating +
  minecart), structure block/void, jigsaw, debug stick, knowledge book, end portal frame, dragon egg, livres
  écrits/inscriptibles, spawner — exclue **avant** toute lecture de `shop.yml`, im possible à contourner par
  la config.

### 7.3 Commandes (implémenté)
- `/shop` (aliases `/adminshop`, `/boutique`) : ouvre l'UI.
- `/shop buy <item> <quantité>`, `/shop sell <item|hand> <quantité|all>` : variante commande.
- `/shop reload` : recharge `shop.yml` (réservé `cantale.admin`).
- Chaque transaction est journalisée via `ActionLogger#logAction("SHOP_BUY"/"SHOP_SELL", ...)`.

### 7.4 Interface (implémenté)
- `ShopMainUI` (sélection de catégorie) → `ShopCategoryUI` (grille paginée, 36 items/page), héritant de
  `BaseUI` comme `AuctionUI`/`FactionMainUI`.
- Clic gauche = acheter 1, Shift+clic gauche = acheter 64, clic droit = vendre 1, Shift+clic droit = vendre
  tout (jusqu'au plafond/disponible). Lore : prix achat/vente courants + quantité restante avant plafond du
  jour.
- Entrée "Boutique Admin" ajoutée dans `MainMenuUI` (slot 10, à côté de Factions/AH/Trade).

### 7.5 Sécurité & anti-exploit (implémenté)
- Toute transaction revalide côté serveur : solde réel avant achat, quantité réelle en inventaire avant
  vente (`ShopManager#countSellable`), remboursement automatique si l'inventaire ne peut pas tout accueillir
  à l'achat.
- `ShopManager#isSellableStack` exclut les items custom Cantale (`CustomItemManager#isCustomItem`) et tout
  item renommé — double sécurité en plus de la liste noire, si jamais un admin ajoutait par erreur un
  material utilisé par un item custom dans `shop.yml`.
- Log de chaque transaction via `ActionLogger` (montant, item, joueur) pour un futur rapport faucet/sink.

## 8. Phasage de mise en œuvre

1. ✅ **Squelette** : commande `/shop`, `ShopManager`/`ShopConfig`/`ShopDAO`, catalogue `shop.yml`.
2. ✅ **Économie** : branchement `EconomyManager`, plafonds quotidiens par joueur/item, reset automatique.
3. ✅ **UI** : `ShopMainUI`/`ShopCategoryUI` + entrée dans `MainMenuUI`.
4. ✅ **Peuplement** : catégories du §3 remplies avec de vrais prix (voir §10), décisions du §6 respectées
   (Totem/Gapple/Élytra/Spawners/items custom hors shop).
5. ⏳ **Calibrage réel** : les prix de `shop.yml` sont une V1 cohérente entre eux (voir méthodologie §10),
   pas encore validée en jeu. Ajustable sans recompiler (`shop.yml` + `/shop reload`).
6. ⏳ **Suivi** : pas encore de rapport faucet/sink automatisé — à construire à partir des logs
   `ActionLogger` (`SHOP_BUY`/`SHOP_SELL`) une fois le shop testé en conditions réelles.

## 9. Ce que cette V1 ne couvre pas volontairement

- **Potions, pommes dorées enchantées, boucliers enchantés** : non inclus dans `shop.yml` (le modèle actuel
  ne gère que des `Material` bruts sans NBT/effets ; gérer des potions avec niveau/durée demanderait un
  second modèle de données — à faire en v2 si besoin).
- Aucune modification du fonctionnement de `/ah`, `/trade`, `/pay` — le shop vient en complément.
- Aucune vente des items custom Cantale (Pickantaxe, Cantaxe, Cantalame, Multi-Cantool, Rune de
  Fortification) — restent réservés à la vraie boutique (argent réel) ou à un craft/quête dédié.
- **Les bonus de Cantox des grades payants ne sont pas modifiés** (voir §10 — décision volontairement
  laissée à l'administrateur, car liée à des montants annoncés sur le site pour des abonnements payants).

## 10. Grille de prix V1 — méthodologie et calibrage

### 10.1 Repères économiques utilisés
- Solde de départ : **10 000 Cantox** (`config.yml: economy.start-balance`).
- `/daily` (`/journalier`) : **1000 Cantox de base** pour tout le monde, + bonus de grade **payant** :
  Aventurier (2,99€/mois) +2500, VIP (6,99€/mois) +10000, Chèvre (13,99€/mois) +100000. Ces bonus sont
  **annoncés tels quels sur le site boutique** ("10 000 Cantox/jour", "100 000 Cantox/jour — 10× plus que le
  VIP") — je ne les ai **pas** modifiés automatiquement, ce serait changer un avantage payant sans validation.
- Vote : 100 à 1500 Cantox par vote (tirage pondéré 60/25/12/3%).
- Caisses (Vote Party, montées en rareté) : de quelques dizaines à 50 000+ Cantox sur les jackpots de caisse
  Épique/Mythique/Légendaire.

### 10.2 Le problème que les prix seuls ne peuvent pas résoudre
Un Chèvre (ou un joueur multi-comptes VIP) touche **jusqu'à ~101 000 Cantox par simple connexion
quotidienne**, sans miner ni voter. Aucun prix d'achat raisonnable ne peut à lui seul empêcher ce joueur de
vider le stock "logique" d'un item rare en une seule session. C'est pourquoi la V1 combine **prix élevés
ET plafonds quotidiens par joueur** (`daily-sell-cap`/`daily-buy-cap`) plutôt que de compter sur le prix
seul — et pourquoi les items vraiment rares (Shulker, Netherite block/debris) combinent **prix
d'achat massifs** (centaines de milliers de Cantox) **et** `daily-buy-cap` serrés : un Chèvre peut
encore en acheter quelques-uns, mais pas vider le sink en une session.

**Recommandation (à valider par toi, pas appliquée automatiquement)** : si tu veux que le shop reste
pertinent aussi pour les gros comptes payants, il faudra à un moment revoir `PlayerRank` (`dailyCantox`)
à la baisse — par exemple Aventurier +500 (1500/j), VIP +2000 (3000/j), Chèvre +6000 (7000/j) — et mettre à
jour la page grades du site en conséquence. Je ne l'ai pas fait ici : c'est un changement d'avantage payant,
je préfère que tu valides le nouveau montant et le nouveau texte marketing avant que je touche à
`PlayerRank.java`/au site.

### 10.3 Logique de prix (voir `shop.yml` pour les valeurs exactes)
- **Blocs infinis** (terre, sable, gravier, cobble, pierre, netherrack) : achat quasi gratuit,
  **vente désactivée** — sinon un générateur AFK = argent infini garanti.
- **Bois** (pattern `_LOG`/`_WOOD`/`_STEM`) : vente basse + plafond quotidien serré — la Cantaxe abat un
  arbre entier en un coup, il ne faut pas que ça devienne une génératrice de Cantox.
- **Minerais communs** (charbon, cuivre, fer, or, redstone, lapis, quartz) : vente modeste, plafonnée.
- **Minerais rares** (diamant, émeraude) : vente correcte mais achat très cher (x8), plafond quotidien serré
  (64 diamants/jour, 32 émeraudes/jour) — cohérent avec une grosse session de minage Pickantaxe 7x7 sans
  permettre un farm illimité.
- **Netherite** : achat massif (scrap 75k, ingot 250k, debris 120k, bloc 850k) + `daily-buy-cap`
  serré — sink Cantox endgame, pas un raccourci trivial vers l'équipement top-tier.
- **Shulker Shell / Box** : achat possible mais prohibitif (350k / 500k) + caps — pas d'End sur ce
  serveur, le shop reste un sink trophée, pas une source de farm.
- **Agriculture/nourriture** : prix bas, vente activée — revenu de base pour nouveaux joueurs/solo.
- **Drops de mobs** : prix bas à modéré, vente activée.
- **Redstone/mécanismes** : **achat seulement**, jamais de vente — pas une source de farm légitime.
- **Guerre/PvP** : TNT/flèches/bouclier achat seulement (logistique de raid) ; obsidienne vendable mais
  plafonnée (128/jour) car minable en masse avec la Pickantaxe près d'un lac de lave.
- **Hors shop, quels que soient les prix futurs** (décisions §6, non négociables en V1) : Totem
  d'Immortalité, Pomme dorée enchantée, Élytra vanilla, Spawners, tous les items custom Cantale, et la liste
  noire dure du §5.
