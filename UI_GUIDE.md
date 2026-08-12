# Cantale — Guide des UIs Inventory

> Document à jour avec le code actuel.  
> **Paper API 1.21.11** — pack_format 75.

---

## 1. Architecture

### BaseUI (`src/main/java/fr/cantale/plugin/ui/inventory/BaseUI.java`)

Toutes les UIs héritent de `BaseUI`.

```java
public abstract class BaseUI implements Listener {
    protected final CantalePlugin plugin;
    protected final Player player;
    protected final Inventory inventory;
    protected final String title;
    protected final int size; // rows * 9

    public BaseUI(CantalePlugin plugin, Player player, String title, int rows) { ... }

    public void open() {
        init();                          // remplit l'inventaire
        if (!registered) {               // enregistre le listener une seule fois
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        player.openInventory(inventory);
    }

    protected void setItem(int slot, Material material, String name, String... lore) { ... }
    protected void fillEmpty() { fillEmpty(Material.BLACK_STAINED_GLASS_PANE); }
    protected void addNavigationBar(boolean showBack, boolean showRefresh) {
        // slot size-9 : Retour (ARROW)
        // slot size-5 : Fermer (BARRIER)
        // slot size-1 : Actualiser (COMPASS)
    }

    protected abstract void init();
    protected abstract void handleClick(int slot);
}
```

### NavigationManager

```java
plugin.getUIManager().getNavigationManager().openUI(player, new XxxUI(plugin, player));
plugin.getUIManager().goBack(player); // retour
```

### Slots standard (6 lignes = 54)

```
[0]  [1]  [2]  [3]  [4]  [5]  [6]  [7]  [8]
[9]  [10] [11] [12] [13] [14] [15] [16] [17]
[18] [19] [20] [21] [22] [23] [24] [25] [26]
[27] [28] [29] [30] [31] [32] [33] [34] [35]
[36] [37] [38] [39] [40] [41] [42] [43] [44]
[45] [46] [47] [48] [49] [50] [51] [52] [53]
```

- `size-9` (45) : Retour
- `size-5` (49) : Fermer
- `size-1` (53) : Actualiser

### Couleurs

| Usage | Code |
|-------|------|
| Titre | `§6` |
| Sous-titre | `§7` |
| Succès | `§a` |
| Erreur | `§c` |
| Valeur | `§e` |
| Lien | `§b` |
| Admin | `§4` |

---

## 2. UIs existantes (code réel)

### MainMenuUI — `/cantale`

**Titre :** `§8[§6Cantale§8] §7Menu Principal`  
**Taille :** 6 lignes

| Slot | Item | Action |
|------|------|--------|
| 4 | NETHER_STAR | Titre |
| 10 | EMERALD | **Boutique Admin** (`/shop`) |
| 11 | SHIELD | Factions (`/f`) |
| 12 | GOLD_NUGGET | Économie (`/balance`) |
| 13 | EMERALD | Auction House (`/ah`) |
| 14 | RED_BED | Homes (`/home`) |
| 15 | ENDER_PEARL | Warps (`/warp`) |
| 16 | TOTEM_OF_UNDYING | Vies (`/dropvie`) |
| 20 | PLAYER_HEAD | Profil (`/profile`) |
| 21 | BOOK | Leaderboards (`/leaderboard kills`) |
| 22 | PAPER | Vote (`/vote`) |
| 23 | SKELETON_SKULL | Wanted (`/wanted list`) |
| 24 | BOOK | Trade (`/trade`) |
| 25 | DIAMOND_PICKAXE | Items Custom (`/customitem`) |
| 29 | CHEST | Coffres Privés (`/pc`) |
| 30 | DIAMOND_SWORD | Modération (`/moderation`) — permission |
| 31 | COMMAND_BLOCK | Admin (`/admin`) — permission |
| 32 | NETHER_STAR | Ranks (info) |
| 33 | REPEATER | Permissions (`/perm list`) |
| 34 | COMPASS | Web / Link (`/link`) |
| 49 | BARRIER | Fermer |

### FactionMainUI — `/f`

**Titre :** `§8[§6Cantale§8] §7Faction`  
**Taille :** 6 lignes

| Slot | Item | Action |
|------|------|--------|
| 4 | SHIELD | Infos faction |
| 11 | PLAYER_HEAD | Membres |
| 12 | GRASS_BLOCK | Claims |
| 13 | GOLD_INGOT | Banque |
| 14 | BOOK | Permissions |
| 15 | ENDER_PEARL | Warp / Spawn |
| 16 | MAP | Carte |
| 17 | ENDER_PEARL | Warps faction |
| 18 | RED_BED | Homes faction |
| 20 | LIME_WOOL | Inviter |
| 21 | ANVIL | Promouvoir |
| 22 | RED_WOOL | Quitter |
| 23 | TNT | Supprimer |
| 45 | ARROW | Retour |
| 49 | BARRIER | Fermer |

### FactionMembersUI — `/f members`

**Titre :** `§7Membres de {faction}`  
**Taille :** 6 lignes

- Slots 0-35 : PLAYER_HEAD par membre → `MemberActionUI`
- Slot 45 : Retour
- Slot 49 : Fermer
- Slot 50 : Inviter

### FactionClaimsUI — `/f claim`

**Titre :** `§7Claims — {faction}`  
**Taille :** 5 lignes

- Slots 0-35 : GRASS_BLOCK / REDSTONE_BLOCK par claim
- Slot 36 : Claim ici
- Slot 37 : Unclaim
- Slot 38 : AutoClaim
- Slot 45 : Retour
- Slot 49 : Fermer

### FactionBankUI — `/f bank`

**Titre :** `§7Banque — {faction}`  
**Taille :** 5 lignes

| Slot | Item | Action |
|------|------|--------|
| 4 | GOLD_BLOCK | Solde |
| 11–15 | dépôts | Déposer montants |
| 20–24 | retraits | Retirer montants |
| 29 | NETHER_STAR | Ouvre FactionPowerShopUI |
| 31 | BOOK | Ouvre FactionBankHistoryUI |
| 36 | ARROW | Retour |
| 40 | BARRIER | Fermer |

### FactionBankHistoryUI — depuis `/f bank` → Historique

**Titre :** `§7Historique — {faction}`  
**Taille :** 6 lignes

| Slot | Item | Action |
|------|------|--------|
| 4 | BOOK | Résumé (solde, nb entrées, page) |
| 9–36 | EMERALD / GOLD_NUGGET / NETHER_STAR / … | Entrées (qui, type, montant, solde après, date) |
| 45 | ARROW | Retour banque |
| 48 | PAPER | Page précédente |
| 49 | BARRIER | Fermer |
| 50 | PAPER | Page suivante |
| 53 | COMPASS | Actualiser |

Types logués : `DEPOSIT`, `WITHDRAW`, `POWER_BUY`, `ADMIN_ADD`, `ADMIN_REMOVE` (table `faction_bank_log`).

### FactionPowerShopUI — `/f power` · `/f bank power`

**Titre :** `§8[§6Cantale§8] §7Pouvoir — {faction}`  
**Taille :** 6 lignes

| Slot | Item | Action |
|------|------|--------|
| 4 | NETHER_STAR | Pouvoir, quotas, solde, prochain prix |
| 11 | GRASS_BLOCK | Claims + palier suivant |
| 13 | RED_BED | F-homes + palier suivant |
| 15 | ENDER_PEARL | Warps + palier suivant |
| 22 | PAPER | Explication pouvoir |
| 29–33 | teintures / améthyste | Achat 1 / 2 / 3 / 5 / max unités (1 → +10 pouvoir ; confirm) |
| 40 | BOOK | Formule de prix (+ rappel 1 unité → +10 pouvoir) |
| 45 | ARROW | Retour |
| 49 | BARRIER | Fermer |
| 53 | COMPASS | Actualiser |

### EconomyUI — `/balance`

**Titre :** `§8[§6Cantale§8] §7Économie`  
**Taille :** 4 lignes

| Slot | Item | Action |
|------|------|--------|
| 4 | GOLD_BLOCK | Solde + rank |
| 11 | GOLD_NUGGET | Payer un joueur |
| 13 | EMERALD | Auction House |
| 15 | BOOK | Transactions |
| 27 | ARROW | Retour |
| 31 | BARRIER | Fermer |

### AuctionUI — `/ah`

**Titre :** `§6Hôtel des Ventes`  
**Taille :** 4 lignes

| Slot | Item | Action |
|------|------|--------|
| 4 | EMERALD | Titre |
| 11 | CHEST | Toutes les annonces |
| 13 | LIME_WOOL | Vendre |
| 15 | BOOK | Mes annonces |
| 27 | ARROW | Retour |
| 31 | BARRIER | Fermer |

### TradeUI — `/trade`

**Titre :** `§6Échange avec §e{player}`  
**Taille :** 6 lignes

- Zone de trade + boutons accepter/refuser

### LifeUI — `/dropvie`

**Titre :** `§8[§6Cantale§8] §7Système de Vies`  
**Taille :** 4 lignes

| Slot | Item | Action |
|------|------|--------|
| 4 | TOTEM_OF_UNDYING | Vies restantes |
| 11 | RED_WOOL | Sacrifier |
| 13 | GOLDEN_APPLE | Donner |
| 15 | TOTEM_OF_UNDYING | Utiliser item Vie |
| 27 | ARROW | Retour |
| 31 | BARRIER | Fermer |

### ProfileUI — `/profile`

**Titre :** `§8[§6Cantale§8] §7Profil de {player}`  
**Taille :** 5 lignes

| Slot | Item | Action |
|------|------|--------|
| 4 | PLAYER_HEAD | Infos joueur |
| 11 | BOOK | Statistiques |
| 13 | TOTEM_OF_UNDYING | Vies |
| 15 | GOLD_NUGGET | Économie |
| 17 | SHIELD | Faction |
| 27 | RED_BED | Homes |
| 29 | ENDER_PEARL | Warps |
| 31 | SKELETON_SKULL | Wanted |
| 35 | NAME_TAG | Tags |
| 36 | ARROW | Retour |
| 40 | BARRIER | Fermer |

### LeaderboardUI — `/leaderboard`

**Titre :** `§7Classements`  
**Taille :** 3 lignes

| Slot | Item | Action |
|------|------|--------|
| 10 | GOLD_BLOCK | Richesse |
| 12 | DIAMOND_SWORD | Kills |
| 14 | TOTEM_OF_UNDYING | Vies |
| 16 | RED_BED | Playtime |
| 18 | SHIELD | Factions |
| 22 | ARROW | Retour |
| 26 | BARRIER | Fermer |

### WantedUI — `/wanted`

**Titre :** `§6Tableau des Primes`  
**Taille :** 5 lignes

- Slots 10-34 : PLAYER_HEAD des recherchés
- Slot 38 : Ajouter une prime
- Slot 36 : Retour
- Slot 40 : Fermer

### ShopMainUI — `/shop`

**Titre :** `§6Boutique Admin`  
**Taille :** 6 lignes

- Catégories d’items à acheter/vendre

### ShopCategoryUI — `/shop <catégorie>`

**Titre :** `§6Boutique — {catégorie}`  
**Taille :** 6 lignes

- Liste des items avec prix

### CrateOpenUI — `/crate open`

**Titre :** `§6Ouverture de caisse`  
**Taille :** 3 lignes

- Animation d’ouverture + récompense

### HarvestDepositUI — `/recolte`

**Titre :** `§6Dépôt de récoltes`  
**Taille :** 6 lignes

- Déposer des récoltes pendant un event HARVEST

### TagCustomizationUI — `/tag`

**Titre :** `§7Personnalisation du tag`  
**Taille :** 5 lignes

- Choisir couleur / icône du tag

### StatisticsUI — `/profile` → Statistiques

**Titre :** `§7Statistiques`  
**Taille :** 5 lignes

- Kills, morts, playtime, etc.

### LeaderboardCategoryUI — `/leaderboard <catégorie>`

**Titre :** `§7Classement — {catégorie}`  
**Taille :** 5 lignes

- Top 10 de la catégorie

### FactionHomesUI — `/f homes`

**Titre :** `§7Homes — {faction}`  
**Taille :** 5 lignes

### FactionWarpsUI — `/f warps`

**Titre :** `§7Warps — {faction}`  
**Taille :** 5 lignes

### MemberActionUI — `/f members` → clic membre

**Titre :** `§7Actions — {membre}`  
**Taille :** 3 lignes

- Promouvoir, rétrograder, expulser

### MyAuctionListingsUI — `/ah my`

**Titre :** `§7Mes annonces`  
**Taille :** 5 lignes

### PlayerSelectorUI — réutilisable

**Titre :** `§7Sélectionner un joueur`  
**Taille :** 5 lignes

- Slots 0-34 : joueurs en ligne
- Slot 36 : Retour
- Slot 40 : Fermer

### ConfirmDialogUI — réutilisable

**Titre :** `§7Confirmer`  
**Taille :** 3 lignes

- Slot 11 : LIME_WOOL Confirmer
- Slot 15 : RED_WOOL Annuler

---

## 3. Grades

| Grade | Affichage | Avantages |
|-------|-----------|-----------|
| NONE | Aventurier | 3 homes, 10 Cantox/jour |
| VIP | VIP | 5 homes, 50 Cantox/jour, /pc 54 slots |
| CHEVRE | Chèvre | 7 homes, 100 Cantox/jour, /pc2, vol claims |
| MODERATOR | **Modo** | Staff, /pc3, pas de cooldown TP |
| ADMIN | Admin | Staff complet |
| OWNER | Owner | Tout |

---

## 4. Checklist qualité

- [ ] Titre avec `§` (pas de RGB)
- [ ] Taille multiple de 9
- [ ] `event.setCancelled(true)` sur clic et drag
- [ ] Listener unregister dans `onClose` (déjà géré par `BaseUI`)
- [ ] Slots vides remplis avec `BLACK_STAINED_GLASS_PANE`
- [ ] Boutons Retour / Fermer / Actualiser via `addNavigationBar()`
- [ ] Messages d’erreur en `§c`, succès en `§a`
- [ ] Données rafraîchies dans `init()`

---

*Document synchronisé avec le code — Paper 1.21.11*
