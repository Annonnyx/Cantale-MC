# Cantale — Guide Technique Complet des UIs Inventory

> Document destiné à une IA générant les interfaces Minecraft (Bukkit Inventory GUI).
> Paper API 1.20.4 — pack_format: 15
> Chaque commande du plugin DOIT avoir une UI graphique (Inventory GUI).

---

## Table des matières

1. [Architecture du système d'UI](#1-architecture-du-système-dui)
2. [Conventions visuelles](#2-conventions-visuelles)
3. [Menu Principal `/cantale`](#3-menu-principal-cantale)
4. [Factions `/f`](#4-factions-f)
5. [Économie `/balance`, `/pay`](#5-économie)
6. [Auction House `/ah`](#6-auction-house-ah)
7. [Trade `/trade`](#7-trade)
8. [Homes `/home`](#8-homes)
9. [Warps `/warp`](#9-warps)
10. [Téléportation `/rtp`, `/spawn`](#10-téléportation)
11. [Vies & Hardcore `/dropvie`, `/givevie`](#11-vies--hardcore)
12. [Profil `/profile`](#12-profil)
13. [Leaderboards `/leaderboard`](#13-leaderboards)
14. [Vote `/vote`](#14-vote)
15. [Wanted `/wanted`](#15-wanted)
16. [Items Custom `/customitem`](#16-items-custom)
17. [Coffres Privés `/pc`, `/pc2`, `/ec`](#17-coffres-privés)
18. [Modération `/moderation`](#18-modération)
19. [Admin `/admin`](#19-admin)
20. [Ranks `/rank`](#20-ranks)
21. [Permissions `/perm`](#21-permissions)
22. [Web & Link `/web`, `/link`](#22-web--link)
23. [Spécifications techniques pour l'IA](#23-spécifications-techniques-pour-lia)

---

## 1. Architecture du système d'UI

### 1.1 Classes de base (à implémenter)

```java
// BaseUI.java — Classe abstraite mère de toutes les UIs
public abstract class BaseUI implements Listener {
    protected final CantalePlugin plugin;
    protected final Player player;
    protected final Inventory inventory;
    protected final String title;
    protected final int size; // 9, 18, 27, 36, 45, 54
    
    public BaseUI(CantalePlugin plugin, Player player, String title, int rows) {
        this.plugin = plugin;
        this.player = player;
        this.title = ChatColor.translateAlternateColorCodes('&', title);
        this.size = rows * 9;
        this.inventory = Bukkit.createInventory(null, size, this.title);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    public void open() { player.openInventory(inventory); }
    public void close() { player.closeInventory(); }
    
    protected void setItem(int slot, Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> loreList = new ArrayList<>();
        for (String line : lore) loreList.add(ChatColor.translateAlternateColorCodes('&', line));
        meta.setLore(loreList);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }
    
    protected void setItem(int slot, ItemStack item) { inventory.setItem(slot, item); }
    protected void fillEmpty(Material material) {
        for (int i = 0; i < size; i++) if (inventory.getItem(i) == null) setItem(i, material, " ");
    }
    
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(title) && event.getWhoClicked().equals(player)) {
            event.setCancelled(true);
            handleClick(event.getSlot());
        }
    }
    
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(title) && event.getPlayer().equals(player)) {
            HandlerList.unregisterAll(this);
        }
    }
    
    protected abstract void handleClick(int slot);
    protected abstract void init();
}
```

### 1.2 Navigation entre UIs

```java
// NavigationManager.java — Singleton gérant la navigation
public class NavigationManager {
    private final Map<UUID, Deque<BaseUI>> history = new HashMap<>();
    
    public void openUI(Player player, BaseUI ui) {
        history.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>()).push(ui);
        ui.open();
    }
    
    public void goBack(Player player) {
        Deque<BaseUI> stack = history.get(player.getUniqueId());
        if (stack != null && stack.size() > 1) {
            stack.pop(); // Ferme l'actuelle
            BaseUI previous = stack.peek();
            if (previous != null) previous.open();
        }
    }
    
    public void clearHistory(Player player) { history.remove(player.getUniqueId()); }
}
```

### 1.3 Slot Layout Standard (9×6 grille)

```
[0]  [1]  [2]  [3]  [4]  [5]  [6]  [7]  [8]   ← Ligne 1 (header)
[9]  [10] [11] [12] [13] [14] [15] [16] [17]  ← Ligne 2
[18] [19] [20] [21] [22] [23] [24] [25] [26]  ← Ligne 3
[27] [28] [29] [30] [31] [32] [33] [34] [35]  ← Ligne 4
[36] [37] [38] [39] [40] [41] [42] [43] [44]  ← Ligne 5
[45] [46] [47] [48] [49] [50] [51] [52] [53]  ← Ligne 6 (footer)
```

**Conventions de slots :**
- Slot 4 (ligne 1, centre) : Titre / icône principale de la section
- Slots 45-53 (ligne 6) : Barre de navigation (précédent, accueil, fermer)
- Slot 49 (ligne 6, centre) : Bouton FERMER (barrière)
- Slot 45 (ligne 6, gauche) : Bouton RETOUR (flèche)
- Slot 53 (ligne 6, droite) : Bouton ACTUALISER (boussole)

---

## 2. Conventions visuelles

### 2.1 Palette de couleurs (dans les lores ItemMeta)

| Élément | Code couleur | Usage |
|---------|-------------|-------|
| Titre principal | `§6` (OR) | Nom des items principaux |
| Sous-titre | `§7` (GRIS) | Descriptions |
| Info positive | `§a` (VERT) | Succès, validé, actif |
| Info négative | `§c` (ROUGE) | Erreur, invalide, inactif |
| Info neutre | `§e` (JAUNE) | Valeurs numériques, noms |
| Accent | `§b` (AQUA) | Liens, boutons interactifs |
| Admin | `§4` (ROUGE FONCÉ) | Actions admin |

### 2.2 Matériaux standardisés

| Fonction | Material | CustomModelData |
|----------|----------|----------------|
| Bouton retour | ARROW | — |
| Bouton fermer | BARRIER | — |
| Bouton actualiser | COMPASS | — |
| Bouton confirmer | LIME_WOOL | — |
| Bouton annuler | RED_WOOL | — |
| Slot vide décoratif | BLACK_STAINED_GLASS_PANE | — |
| Séparateur | GRAY_STAINED_GLASS_PANE | — |
| Info joueur | PLAYER_HEAD (Skull) | — |
| Monnaie | GOLD_NUGGET | 24 (rune_fortification) |
| Vie | TOTEM_OF_UNDYING | 25 (vie) |
| Faction | SHIELD | — |
| Home | RED_BED | — |
| Warp | ENDER_PEARL | — |
| Auction | EMERALD | — |
| Trade | BOOK | — |
| Modération | DIAMOND_SWORD | — |
| Paramètres | REPEATER | — |
| Danger | LAVA_BUCKET | — |

---

## 3. Menu Principal `/cantale`

**Déclencheur :** Commande `/cantale` ou clic sur le menu principal
**Titre :** `§8[§6Cantale§8] §7Menu Principal`
**Taille :** 6 lignes (54 slots)

### Layout (6×9)

```
[Ligne 1]  [VIDE] [VIDE] [VIDE] §6CANTALE [VIDE] [VIDE] [VIDE] [VIDE]
[Ligne 2]  [VIDE] FACTION ÉCONOMIE AUCTION HOMES WARPS VIES [VIDE]
[Ligne 3]  [VIDE] PROFILE LEADER VOTE WANTED TRADE ITEMS [VIDE]
[Ligne 4]  [VIDE] PC/EC MODERATION ADMIN RANK PERMS WEB [VIDE]
[Ligne 5]  [VIDE] [VIDE] [VIDE] [VIDE] [VIDE] [VIDE] [VIDE] [VIDE]
[Ligne 6]  [VIDE] [VIDE] [VIDE] [VIDE] FERMER [VIDE] [VIDE] [VIDE]
```

### Slots détaillés

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | NETHER_STAR | `§6§lCANTALE` | `§7Version 1.0.0
§7Paper 1.20.4` | — (décoratif) |
| 11 | SHIELD | `§eFactions` | `§7Gérer votre faction
§7Claims, membres, banque` | Ouvre FactionMainUI |
| 12 | GOLD_NUGGET | `§eÉconomie` | `§7Balance, paiements
§7Cantox: §6{balance}` | Ouvre EconomyUI |
| 13 | EMERALD | `§eAuction House` | `§7Acheter / Vendre
§7{active_listings} annonces` | Ouvre AuctionMainUI |
| 14 | RED_BED | `§eHomes` | `§7Téléportation personnelle
§7{homes_count}/{max_homes} homes` | Ouvre HomeUI |
| 15 | ENDER_PEARL | `§eWarps` | `§7Téléportation publique
§7{warp_count} warps` | Ouvre WarpUI |
| 16 | TOTEM_OF_UNDYING | `§eVies` | `§7Système Hardcore
§7Vies: §c{lives}` | Ouvre LifeUI |
| 20 | PLAYER_HEAD | `§eProfil` | `§7Votre profil complet
§7Classement: §e#{rank}` | Ouvre ProfileUI |
| 21 | BOOK | `§eLeaderboards` | `§7Classements
§7Top riches, kills, vies` | Ouvre LeaderboardUI |
| 22 | PAPER | `§eVote` | `§7Voter pour le serveur
§7Récompenses en attente: §e{pending}` | Ouvre VoteUI |
| 23 | SKELETON_SKULL | `§eWanted` | `§7Système de primes
§7Primes actives: §c{wanted_count}` | Ouvre WantedUI |
| 24 | BOOK | `§eTrade` | `§7Échanger avec un joueur` | Ouvre TradeSelectUI |
| 25 | DIAMOND_PICKAXE | `§eItems Custom` | `§7Pickantaxes, Cantaxes
§7Multi-Cantools, Statio-Lytras` | Ouvre CustomItemsInfoUI |
| 29 | CHEST | `§eCoffres Privés` | `§7/pc /pc2 /ec` | Ouvre PrivateChestUI |
| 30 | DIAMOND_SWORD | `§eModération` | `§7Outils modérateur
§7(permission requise)` | Ouvre ModerationUI |
| 31 | COMMAND_BLOCK | `§eAdmin` | `§7Panel administrateur
§7(permission requise)` | Ouvre AdminUI |
| 32 | NETHER_STAR | `§eRanks` | `§7Grades VIP / Chèvre` | Ouvre RankUI |
| 33 | REPEATER | `§ePermissions` | `§7Gérer les permissions` | Ouvre PermissionUI |
| 34 | COMPASS | `§eWeb / Link` | `§7Discord /link · Site /connexion` | Ouvre WebLinkUI |
| 49 | BARRIER | `§cFermer` | `§7Fermer le menu` | Ferme l'inventaire |

---

## 4. Factions `/f`

### 4.1 Faction Main UI
**Déclencheur :** `/f` (sans args)
**Titre :** `§8[§6Cantale§8] §7Faction`
**Taille :** 6 lignes

**Layout conditionnel :**
- **Si pas de faction** : Affiche les boutons CRÉER et REJOINDRE
- **Si dans une faction** : Affiche les boutons de gestion

#### Slots (joueur dans une faction)

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | SHIELD | `§6§l{FactionName}` | `§7Membres: §e{member_count}
§7Claims: §e{claim_count}
§7Banque: §6{bank_balance} §7Cantox
§7Power: §e{power}/{max_power}` | — |
| 11 | PLAYER_HEAD | `§eMembres` | `§7Voir / gérer les membres` | Ouvre FactionMembersUI |
| 12 | GRASS_BLOCK | `§eClaims` | `§7Voir la carte des claims
§7Claim actuel: {current_claim}` | Ouvre FactionClaimsUI |
| 13 | GOLD_INGOT | `§eBanque` | `§7Déposer / Retirer
§7Solde: §6{bank_balance}` | Ouvre FactionBankUI |
| 14 | BOOK | `§ePermissions` | `§7Gérer les permissions
§7de grade` | Ouvre FactionPermsUI |
| 15 | ENDER_PEARL | `§eWarp / Spawn` | `§7TP au spawn faction` | Téléporte au spawn faction |
| 16 | MAP | `§eCarte` | `§7Carte des claims autour` | Ouvre FactionMapUI (`/f map`) — grille inventaire 9×5 |
| 20 | LIME_WOOL | `§aInviter un joueur` | `§7Envoyer une invitation` | Ouvre FactionInviteUI |
| 21 | ANVIL | `§ePromouvoir / Rétrograder` | `§7Gérer les grades` | Ouvre FactionPromoteUI |
| 22 | RED_WOOL | `§cQuitter la faction` | `§7Attention ! Action définitive` | Ouvre FactionLeaveConfirmUI |
| 23 | TNT | `§cSupprimer la faction` | `§7Leader uniquement` | Ouvre FactionDeleteConfirmUI |
| 29 | SIGN | `§eDéfinir le spawn` | `§7Position actuelle` | /f setspawn |
| 30 | SIGN | `§eDéfinir le warp` | `§7Position actuelle` | /f setwarp |
| 45 | ARROW | `§7Retour` | | Retour au Menu Principal |
| 49 | BARRIER | `§cFermer` | | Ferme |

### 4.1b Faction Map UI (`FactionMapUI`)
**Titre :** `§8[§6Cantale§8] §7Carte des claims`
**Taille :** 6 lignes · commande `/f map` (alias chat : `/f map chat`)

Grille **9×5** chunks (lignes 0–4, N en haut), centrée sur le chunk du joueur. Ligne 5 = légende / infos / fermer / actualiser.

| Couleur (vitre) | Signification | Action |
|-----------------|---------------|--------|
| Lime | Position du joueur (centre) | Selon le type de case (lore) |
| Vert | Claim de ta faction | Clic droit = unclaim |
| Gris | Wilderness claimable | Clic gauche = claim |
| Rouge | Non claimable / ennemi | — |
| Bleu clair | Allié | — |
| Orange | Autre faction | — |
| Violet | PASDIC | — |

Mêmes contrôles que `/f claim` / `/f unclaim` : permission, quota, zone protégée, distance min 2, contiguïté N/S/E/O. Actualiser recentre sur la position actuelle.

### 4.2 Faction Members UI
**Titre :** `§7Membres de {faction_name}`
**Taille :** 6 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 0-35 | PLAYER_HEAD (une par membre) | `§e{pseudo}` | `§7Grade: §6{rank}
§7Power: §e{power}
§7En ligne: {status}` | Clic → FactionMemberActionsUI |
| 36-44 | — | (séparateurs) | | |
| 45 | ARROW | `§7Retour` | | Retour FactionMainUI |
| 49 | BARRIER | `§cFermer` | | |
| 50 | LIME_WOOL | `§aInviter` | | Ouvre FactionInviteUI |

### 4.3 Faction Claims UI
**Titre :** `§7Claims — {faction_name}`
**Taille :** 5 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 0-35 | GRASS_BLOCK (claim allié) / REDSTONE_BLOCK (claim ennemi) | `§e{world} {x},{z}` | `§7Chunk: {x},{z}
§7Voisins: {neighbor_count}` | Clic → Options du claim |
| 36 | LIME_WOOL | `§aClaim ici` | `§7Claim le chunk actuel` | /f claim |
| 37 | RED_WOOL | `§cUnclaim` | `§7Déclaim le chunk actuel` | /f unclaim |
| 38 | CLOCK | `§eAutoClaim` | `§7Marche = claim automatique` | Toggle autoclaim |
| 45 | ARROW | `§7Retour` | | |
| 49 | BARRIER | `§cFermer` | | |

### 4.4 Faction Bank UI
**Titre :** `§7Banque — {faction_name}`
**Taille :** 4 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | GOLD_BLOCK | `§6§lBanque Faction` | `§7Solde: §6{bank_balance} §7Cantox` | — |
| 11 | LIME_WOOL | `§aDéposer` | `§7Cliquez pour déposer
§7de l'argent` | Ouvre AnvilInput (montant) |
| 13 | RED_WOOL | `§cRetirer` | `§7Cliquez pour retirer
§7de l'argent` | Ouvre AnvilInput (montant) |
| 15 | BOOK | `§eHistorique` | `§7Voir les transactions` | Ouvre FactionBankHistoryUI |
| 27 | ARROW | `§7Retour` | | |
| 31 | BARRIER | `§cFermer` | | |

### 4.5 Faction Perms UI
**Titre :** `§7Permissions — {faction_name}`
**Taille :** 5 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | BOOK | `§6§lPermissions` | `§7Grade: §e{selected_rank}` | — |
| 11-15 | WOOL de couleur | `§e{rank_name}` | `§7Membres: {count}` | Sélectionne le grade |
| 20-24 | REPEATER (activé/désactivé) | `§e{permission_name}` | `§7{description}
§7État: {status}` | Toggle permission |
| 36 | ARROW | `§7Retour` | | |
| 40 | BARRIER | `§cFermer` | | |

### 4.6 Faction Create UI
**Titre :** `§7Créer une Faction`
**Taille :** 3 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | SHIELD | `§6§lNouvelle Faction` | `§7Coût: §6{cost} §7Cantox` | — |
| 11 | NAME_TAG | `§eNom` | `§7Cliquez pour définir` | AnvilInput → nom |
| 13 | LIME_WOOL | `§aCréer` | `§7Confirmer la création` | Valide + /f create |
| 15 | RED_WOOL | `§cAnnuler` | `§7Retour au menu` | Ferme |

---

## 5. Économie

### 5.1 Economy UI (`/balance`)
**Titre :** `§8[§6Cantale§8] §7Économie`
**Taille :** 4 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | GOLD_BLOCK | `§6§l{player_name}` | `§7Solde: §6{balance} §7Cantox
§7Rank: §e{rank_name}` | — |
| 11 | GOLD_NUGGET | `§ePayer un joueur` | `§7Transférer de l'argent` | Ouvre PayPlayerUI |
| 13 | EMERALD | `§eAuction House` | `§7Acheter / Vendre` | Ouvre AuctionMainUI |
| 15 | BOOK | `§eTransactions` | `§7Historique` | Ouvre TransactionHistoryUI |
| 27 | ARROW | `§7Retour` | | Menu Principal |
| 31 | BARRIER | `§cFermer` | | |

### 5.2 Pay Player UI
**Titre :** `§7Payer un joueur`
**Taille :** 3 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | GOLD_NUGGET | `§6§lTransfert` | `§7Votre solde: §6{balance}` | — |
| 11 | PLAYER_HEAD | `§eSélectionner joueur` | `§7Cliquez pour choisir` | Ouvre PlayerSelectorUI |
| 13 | GOLD_BLOCK | `§eMontant` | `§7Cliquez pour définir` | AnvilInput (nombre) |
| 15 | LIME_WOOL | `§aConfirmer` | `§7Envoyer §6{amount} §7à §e{target}` | Exécute /pay |
| 18 | ARROW | `§7Retour` | | |
| 22 | BARRIER | `§cFermer` | | |

---

## 6. Auction House `/ah`

### 6.1 Auction Main UI
**Titre :** `§8[§6Cantale§8] §7Auction House`
**Taille :** 6 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | EMERALD | `§6§lAuction House` | `§7{total_listings} annonces
§7Vos annonces: §e{my_listings}` | — |
| 11 | CHEST | `§eToutes les annonces` | `§7Parcourir tout` | Ouvre AuctionBrowseUI (page 1) |
| 13 | LIME_WOOL | `§aVendre un item` | `§7Créer une annonce` | Ouvre AuctionSellUI |
| 15 | BOOK | `§eMes annonces` | `§7Gérer vos ventes` | Ouvre AuctionMyListingsUI |
| 29 | ARROW | `§7Retour` | | Menu Principal |
| 33 | BARRIER | `§cFermer` | | |

### 6.2 Auction Browse UI
**Titre :** `§7Annonces — Page {page}/{max_page}`
**Taille :** 6 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 0-44 | ItemStack (les items en vente) | `§e{item_name}` | `§7Vendeur: §e{seller}
§7Prix: §6{price} §7Cantox
§7Expire: §7{time_left}` | Clic → AuctionBuyConfirmUI |
| 45 | ARROW | `§7Page précédente` | | Page -1 |
| 46 | RED_STAINED_GLASS_PANE | `§c` | | — |
| 49 | BARRIER | `§cFermer` | | |
| 52 | RED_STAINED_GLASS_PANE | `§c` | | — |
| 53 | ARROW | `§7Page suivante` | | Page +1 |

### 6.3 Auction Sell UI
**Titre :** `§7Vendre un item`
**Taille :** 3 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | EMERALD | `§6§lNouvelle annonce` | `§7Placez votre item ici` | — |
| 13 | (slot réservé) | — | `§7Déposez l'item` | Accepte item déposé |
| 11 | GOLD_BLOCK | `§ePrix` | `§7Cliquez pour définir` | AnvilInput |
| 15 | LIME_WOOL | `§aConfirmer` | `§7Mettre en vente` | Valide |
| 18 | ARROW | `§7Retour` | | |
| 22 | BARRIER | `§cFermer` | | |

---

## 7. Trade `/trade`

### 7.1 Trade Select UI
**Titre :** `§7Échanger avec...`
**Taille :** 3 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | BOOK | `§6§lTrade` | `§7Sélectionnez un joueur` | — |
| 10-16 | PLAYER_HEAD (joueurs proches) | `§e{player_name}` | `§7Distance: §e{distance}m` | Ouvre TradeRequestUI |
| 18 | ARROW | `§7Retour` | | |
| 22 | BARRIER | `§cFermer` | | |

### 7.2 Trade Request UI
**Titre :** `§7Demande d'échange`
**Taille :** 3 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | PLAYER_HEAD | `§6§l{target_name}` | `§7Envoyer une demande ?` | — |
| 11 | LIME_WOOL | `§aEnvoyer la demande` | `§7Attendre la réponse` | /trade {target} |
| 15 | RED_WOOL | `§cAnnuler` | `§7Retour` | Ferme |

---

## 8. Homes `/home`

### 8.1 Home UI
**Titre :** `§8[§6Cantale§8] §7Homes ({count}/{max})`
**Taille :** 5 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | RED_BED | `§6§lVos Homes` | `§7{count}/{max} homes
§7Cliquez pour vous téléporter` | — |
| 10-34 | RED_BED (un par home) | `§e{home_name}` | `§7Monde: §e{world}
§7Coords: §e{x} {y} {z}
§7Clic gauche: §aTéléporter
§7Clic droit: §cSupprimer` | Gauche: TP / Droit: confirm delete |
| 38 | LIME_WOOL | `§aNouveau Home` | `§7Créer un home ici
§7Nom: §e/home set <nom>` | Ouvre AnvilInput (nom) |
| 36 | ARROW | `§7Retour` | | |
| 40 | BARRIER | `§cFermer` | | |

---

## 9. Warps `/warp`

### 9.1 Warp UI
**Titre :** `§8[§6Cantale§8] §7Warps ({count})`
**Taille :** 5 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | ENDER_PEARL | `§6§lWarps Publics` | `§7{count} warps disponibles` | — |
| 10-34 | ENDER_PEARL (un par warp) | `§e{warp_name}` | `§7Monde: §e{world}
§7Coords: §e{x} {y} {z}
§7Créé par: §e{owner}` | Clic → TP au warp |
| 38 | LIME_WOOL | `§aNouveau Warp` | `§7(Admin)` | /warp set (admin) |
| 36 | ARROW | `§7Retour` | | |
| 40 | BARRIER | `§cFermer` | | |

---

## 10. Téléportation

### 10.1 RTP / Spawn
**Titre :** `§7Téléportation`
**Taille :** 3 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | ENDER_PEARL | `§6§lTéléportation` | `§7Choisissez une destination` | — |
| 11 | COMPASS | `§eTéléportation aléatoire` | `§7/RTP
§7Distance: §e+1000 blocs
§7Cooldown: §e20s` | /rtp |
| 13 | BEACON | `§eSpawn` | `§7/Spawn` | /spawn |
| 15 | RED_BED | `§eHomes` | `§7Voir vos homes` | Ouvre HomeUI |
| 18 | ARROW | `§7Retour` | | |
| 22 | BARRIER | `§cFermer` | | |

---

## 11. Vies & Hardcore

### 11.1 Life UI
**Titre :** `§8[§6Cantale§8] §7Système de Vies`
**Taille :** 4 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | TOTEM_OF_UNDYING | `§6§lVies` | `§7Vies restantes: §c{lives}
§7Statut: §e{status}` | — |
| 11 | RED_WOOL | `§cSacrifier une vie` | `§7/dropvie
§7Transforme 1 vie en item
§7Minimum: 2 vies requises` | /dropvie |
| 13 | GOLDEN_APPLE | `§eDonner une vie` | `§7/givevie <joueur>
§7Minimum: 2 vies requises` | Ouvre PlayerSelectorUI → /givevie |
| 15 | TOTEM_OF_UNDYING | `§eUtiliser une Vie (item)` | `§7Cliquez pour utiliser
§7+1 vie en DB` | Consomme item VIE |
| 27 | ARROW | `§7Retour` | | |
| 31 | BARRIER | `§cFermer` | | |

---

## 12. Profil `/profile`

### 12.1 Profile UI
**Titre :** `§8[§6Cantale§8] §7Profil de {player_name}`
**Taille :** 5 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | PLAYER_HEAD | `§6§l{player_name}` | `§7Grade: §e{rank}
§7Faction: §e{faction_name}
§7Vies: §c{lives}
§7Cantox: §6{balance}` | — |
| 11 | BOOK | `§eStatistiques` | `§7Kills: §e{kills}
§7Morts: §e{deaths}
§7Playtime: §e{playtime}` | — |
| 13 | TOTEM_OF_UNDYING | `§eVies` | `§7Vies: §c{lives}
§7Status: §e{alive_status}` | Ouvre LifeUI |
| 15 | GOLD_NUGGET | `§eÉconomie` | `§7Balance: §6{balance}
§7Transactions: §e{transaction_count}` | Ouvre EconomyUI |
| 17 | SHIELD | `§eFaction` | `§7{faction_name}
§7Grade: §e{faction_rank}` | Ouvre FactionMainUI |
| 29 | RED_BED | `§eHomes` | `§7{homes_count} homes` | Ouvre HomeUI |
| 31 | ENDER_PEARL | `§eWarps` | `§7{warp_count} warps` | Ouvre WarpUI |
| 33 | SKELETON_SKULL | `§eWanted` | `§7Primes: §c{wanted_status}` | Ouvre WantedUI |
| 36 | ARROW | `§7Retour` | | |
| 40 | BARRIER | `§cFermer` | | |

---

## 13. Leaderboards `/leaderboard`

### 13.1 Leaderboard UI
**Titre :** `§7Classements`
**Taille :** 5 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | BOOK | `§6§lClassements` | `§7Catégorie: §e{category}` | — |
| 10 | GOLD_BLOCK | `§eRichesse` | `§7Top Cantox` | Affiche top richesse |
| 12 | DIAMOND_SWORD | `§eKills` | `§7Top kills` | Affiche top kills |
| 14 | TOTEM_OF_UNDYING | `§eVies` | `§7Top vies restantes` | Affiche top vies |
| 16 | RED_BED | `§eTemps de jeu` | `§7Top playtime` | Affiche top playtime |
| 18 | SHIELD | `§eFactions` | `§7Top factions` | Affiche top factions |
| 28-34 | PLAYER_HEAD (top 1-3) | `§e#{rank} {name}` | `§7Valeur: §e{value}` | — |
| 36 | ARROW | `§7Retour` | | |
| 40 | BARRIER | `§cFermer` | | |

---

## 14. Vote `/vote`

### 14.1 Vote UI
**Titre :** `§8[§6Cantale§8] §7Vote`
**Taille :** 5 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | PAPER | `§6§lVote Cantale` | `§7Récompenses en attente: §e{pending}
§7Votes totaux: §e{total_votes}` | — |
| 10-16 | PAPER (un par site) | `§e{site_name}` | `§7Cliquez pour ouvrir le lien
§7{url_short}` | Ouvre lien clickable |
| 29 | GOLD_BLOCK | `§eRéclamer` | `§7/vote claim` | /vote claim |
| 31 | BOOK | `§eStats` | `§7/vote stats` | Ouvre VoteStatsUI |
| 33 | EMERALD | `§eTop Votants` | `§7/vote top` | Ouvre VoteTopUI |
| 36 | ARROW | `§7Retour` | | |
| 40 | BARRIER | `§cFermer` | | |

---

## 15. Wanted `/wanted`

### 15.1 Wanted UI
**Titre :** `§7Système de Primes`
**Taille :** 5 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | SKELETON_SKULL | `§6§lPrimes` | `§7Primes actives: §c{count}` | — |
| 10-34 | PLAYER_HEAD (joueurs recherchés) | `§c§l{target_name}` | `§7Mise: §6{amount} §7Cantox
§7Par: §e{setter}
§7Raison: §7{reason}` | Clic → WantedDetailsUI |
| 38 | LIME_WOOL | `§aAjouter une prime` | `§7/wanted add` | Ouvre WantedAddUI |
| 36 | ARROW | `§7Retour` | | |
| 40 | BARRIER | `§cFermer` | | |

---

## 16. Items Custom `/customitem`

### 16.1 Custom Items Info UI
**Titre :** `§7Items Custom Cantale`
**Taille :** 5 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | DIAMOND_PICKAXE | `§6§lItems Custom` | `§7Liste complète` | — |
| 10 | DIAMOND_PICKAXE | `§ePickantaxe` | `§7Pioche + Hache
§7Diamond: 3x3, 5x5, 7x7` | — |
| 12 | IRON_AXE | `§eCantaxe` | `§7Hache spéciale
§7One-shot logs` | — |
| 14 | SHEARS | `§eMulti-Cantool` | `§7Outil universel
§7Pioche/Hache/Pelle/Cisailles` | — |
| 16 | ELYTRA | `§eStatio-Lytras` | `§7Elytra infinie
§7Vol dans les claims` | — |
| 18 | NETHERITE_SWORD | `§eCantalame` | `§7Épée évolutive
§7100 kills = amélioration` | — |
| 20 | ENCHANTED_BOOK | `§eRune de Fortification` | `§7Enchantement spécial` | — |
| 22 | TOTEM_OF_UNDYING | `§eVie` | `§7+1 vie
§7Clic droit pour utiliser` | — |
| 36 | ARROW | `§7Retour` | | |
| 40 | BARRIER | `§cFermer` | | |

### 16.2 Custom Item Admin UI (permission admin)
**Titre :** `§7Donner un Item Custom`
**Taille :** 4 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | COMMAND_BLOCK | `§6§lItems Admin` | `§7Sélectionnez un item` | — |
| 10-22 | (tous les items custom) | `§e{item_name}` | `§7Cliquez pour donner` | /customitem {type} |
| 27 | ARROW | `§7Retour` | | |
| 31 | BARRIER | `§cFermer` | | |

---

## 17. Coffres Privés

### 17.1 Private Chest UI (`/pc`, `/pc2`, `/ec`)
**Titre :** `§7Coffres Privés`
**Taille :** 3 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | CHEST | `§6§lCoffres Privés` | `§7Choisissez un coffre` | — |
| 11 | CHEST | `§eCoffre Privé 1` | `§7/pc
§7{slot_count} slots` | Ouvre PC1 |
| 13 | ENDER_CHEST | `§eEnder Chest` | `§7/ec` | Ouvre EC |
| 15 | CHEST | `§eCoffre Privé 2` | `§7/pc2
§7Grade Chèvre requis` | Ouvre PC2 (si grade) |
| 18 | ARROW | `§7Retour` | | |
| 22 | BARRIER | `§cFermer` | | |

---

## 18. Modération `/moderation`

### 18.1 Moderation UI
**Titre :** `§4§lPanel Modération`
**Taille :** 4 lignes
**Permission :** `cantale.admin`

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | DIAMOND_SWORD | `§4§lModération` | `§7Mode Trace: §e{trace_status}` | — |
| 11 | LEVER | `§eMode Trace` | `§7/moderation
§7Activer/désactiver` | Toggle trace |
| 13 | DIAMOND_SWORD | `§eKick` | `§7Expulser un joueur` | Ouvre ModKickUI |
| 15 | BARRIER | `§eMute/Unmute` | `§7Gérer les mutes` | Ouvre ModMuteUI |
| 17 | ENDER_PEARL | `§eTP / TPHERE` | `§7Téléportation` | Ouvre ModTPUI |
| 21 | GLASS | `§eVanish` | `§7/vanish` | Toggle vanish |
| 23 | CLOCK | `§eCooldown` | `§7{remaining}s restantes` | Info |
| 27 | ARROW | `§7Retour` | | |
| 31 | BARRIER | `§cFermer` | | |

### 18.2 Mod Mute UI
**Titre :** `§7Mute Manager`
**Taille :** 4 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | BARRIER | `§6§lMute Manager` | `§7Joueurs mutés: {count}` | — |
| 10-16 | PLAYER_HEAD (mutés) | `§c{player_name}` | `§7Raison: §7{reason}
§7Durée: §e{duration}
§7Expire: §e{expires}` | Clic → Unmute |
| 20 | LIME_WOOL | `§aMute un joueur` | `§7/moderation mute` | Ouvre ModMutePlayerUI |
| 27 | ARROW | `§7Retour` | | |
| 31 | BARRIER | `§cFermer` | | |

---

## 19. Admin `/admin`

### 19.1 Admin UI
**Titre :** `§4§lPanel Admin`
**Taille :** 5 lignes
**Permission :** `cantale.admin`

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | COMMAND_BLOCK | `§4§lAdmin` | `§7Outils administrateur` | — |
| 10 | TOTEM_OF_UNDYING | `§eGérer les vies` | `§7/addlife /removelife` | Ouvre AdminLifeUI |
| 12 | GOLD_BLOCK | `§eÉconomie` | `§7/admin eco` | Ouvre AdminEcoUI |
| 14 | SHIELD | `§eFactions` | `§7/admin faction` | Ouvre AdminFactionUI |
| 16 | DIAMOND_SWORD | `§eItems Custom` | `§7/customitem` | Ouvre CustomItemAdminUI |
| 18 | REPEATER | `§eOptimisation` | `§7/optimize` | Ouvre OptimizeUI |
| 20 | BOOK | `§ePermissions` | `§7/perm` | Ouvre PermissionUI |
| 22 | COMPASS | `§eUnban IP` | `§7/unbanip` | Ouvre UnbanIPUI |
| 24 | MAP | `§ePASDIC` | `§7/pasdic` | Ouvre PasdicUI |
| 28 | NETHER_STAR | `§eRanks` | `§7/rank` | Ouvre RankAdminUI |
| 30 | PLAYER_HEAD | `§eJoueurs Bannis` | `§7/listemorts` | Ouvre BannedListUI |
| 36 | ARROW | `§7Retour` | | |
| 40 | BARRIER | `§cFermer` | | |

### 19.2 Admin Life UI
**Titre :** `§4§lGérer les Vies`
**Taille :** 3 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | TOTEM_OF_UNDYING | `§4§lGestion Vies` | `§7Sélectionner un joueur` | — |
| 11 | LIME_WOOL | `§aAjouter des vies` | `§7/addlife` | Ouvre AnvilInput → PlayerSelector |
| 13 | RED_WOOL | `§cRetirer des vies` | `§7/removelife` | Ouvre AnvilInput → PlayerSelector |
| 18 | ARROW | `§7Retour` | | |
| 22 | BARRIER | `§cFermer` | | |

---

## 20. Ranks `/rank`

### 20.1 Rank UI (joueur)
**Titre :** `§7Grades Cantale`
**Taille :** 4 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | NETHER_STAR | `§6§lVotre Grade` | `§7Grade actuel: §e{rank}
§7Avantages:` | — |
| 11 | IRON_INGOT | `§7Aventurier (Gratuit)` | `§7Homes: §e3
§7Cantox quotidien: §e10` | — |
| 13 | GOLD_INGOT | `§eVIP (4.99€/mois)` | `§7Homes: §e5
§7Cantox quotidien: §e50
§7PC: §e54 slots
§7Vol claims: §eoui` | Lien boutique |
| 15 | NETHERITE_INGOT | `§6Chèvre (9.99€/mois)` | `§7Homes: §e7
§7Cantox quotidien: §e100
§7PC2: §e54 slots
§7Vol claims: §eoui
§7EnderChest: §eoui` | Lien boutique |
| 27 | ARROW | `§7Retour` | | |
| 31 | BARRIER | `§cFermer` | | |

### 20.2 Rank Admin UI
**Titre :** `§4§lGestion des Ranks`
**Taille :** 4 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | COMMAND_BLOCK | `§4§lRanks Admin` | `§7Gérer les grades` | — |
| 11 | PLAYER_HEAD | `§eAttribuer un grade` | `§7/rank set` | Ouvre PlayerSelectorUI → RankSelect |
| 13 | RED_WOOL | `§cRetirer un grade` | `§7/rank remove` | Ouvre PlayerSelectorUI |
| 15 | BOOK | `§eInfos joueur` | `§7/rank info` | Ouvre PlayerSelectorUI → Info |
| 27 | ARROW | `§7Retour` | | |
| 31 | BARRIER | `§cFermer` | | |

---

## 21. Permissions `/perm`

### 21.1 Permission UI
**Titre :** `§7Permissions`
**Taille :** 5 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | REPEATER | `§6§lPermissions` | `§7Gestion des permissions` | — |
| 10 | PLAYER_HEAD | `§eJoueurs en ligne` | `§7Voir / modifier` | Ouvre PermPlayerListUI |
| 12 | BOOK | `§eGroupes` | `§7Gérer par groupe` | Ouvre PermGroupsUI |
| 14 | PAPER | `§eListe complète` | `§7Toutes les permissions` | Ouvre PermListUI |
| 36 | ARROW | `§7Retour` | | |
| 40 | BARRIER | `§cFermer` | | |

---

## 22. Web & Link `/web`, `/link`

### 22.1 WebLink UI
**Titre :** `§7Liaisons Externes`
**Taille :** 3 lignes

| Slot | Material | Nom | Lore | Action |
|------|----------|-----|------|--------|
| 4 | COMPASS | `§6§lLiaisons` | `§7Connectez vos comptes` | — |
| 11 | BOOK | `§eDiscord` | `§7/link
§7Générer un code
§7Statut: §e{discord_status}` | /link |
| 13 | COMPASS | `§eSite Web` | `§7cantale.world/connexion
§7OAuth Discord (plus de /web link)` | Message /web → URL site |
| 18 | ARROW | `§7Retour` | | |
| 22 | BARRIER | `§cFermer` | | |

> **Note (août 2026) :** `/web link` est obsolète. `/web` affiche uniquement le lien https://www.cantale.world/connexion. La liaison Discord reste `/link`.

---

## 23. Spécifications techniques pour l'IA

### 23.1 Contraintes Bukkit Inventory

- **Tailles valides uniquement :** 9, 18, 27, 36, 45, 54 (multiples de 9)
- **Titre max :** 32 caractères (Bukkit limite)
- **ItemStack :** Nécessite Material, pas de textures custom directement (sauf via CustomModelData)
- **Clics :** LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT — tous doivent être gérés
- **Drag & Drop :** Toujours annuler (`event.setCancelled(true)`)
- **Mise à jour :** Recréer l'inventaire ou utiliser `inventory.setItem()` pour refresh

### 23.2 Pattern de code pour chaque UI

```java
public class XxxUI extends BaseUI {
    public XxxUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Titre", 6);
        init();
        open();
    }
    
    @Override
    protected void init() {
        // Remplir les slots
        setItem(4, Material.NETHER_STAR, "§6§lTitre", "§7Description");
        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
    
    @Override
    protected void handleClick(int slot) {
        switch (slot) {
            case 4 -> { /* action */ }
            case 45 -> plugin.getUIManager().goBack(player);
            case 49 -> close();
        }
    }
}
```

### 23.3 Player Selector UI (réutilisable)

```java
// Ouvre un sélecteur de joueur, exécute un callback avec le Player sélectionné
public class PlayerSelectorUI extends BaseUI {
    private final Consumer<Player> onSelect;
    
    public PlayerSelectorUI(CantalePlugin plugin, Player player, String title, Consumer<Player> onSelect) {
        super(plugin, player, title, 5);
        this.onSelect = onSelect;
        init();
        open();
    }
    
    @Override
    protected void init() {
        Player[] online = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        for (int i = 0; i < Math.min(online.length, 35); i++) {
            Player target = online[i];
            setItem(i, Material.PLAYER_HEAD, "§e" + target.getName(), 
                "§7Clic pour sélectionner");
        }
        setItem(36, Material.ARROW, "§7Retour");
        setItem(40, Material.BARRIER, "§cFermer");
    }
    
    @Override
    protected void handleClick(int slot) {
        if (slot < 35) { /* sélectionner joueur */ }
        else if (slot == 36) plugin.getUIManager().goBack(player);
        else if (slot == 40) close();
    }
}
```

### 23.4 Anvil Input UI (réutilisable)

Utiliser `Player#openAnvilInventory` ou créer une UI dédiée avec un livre + sign :

```java
// Système d'input texte via chat (plus simple que Anvil)
public void requestChatInput(Player player, String prompt, Consumer<String> callback) {
    player.sendMessage("§8[§6Cantale§8] §7" + prompt);
    player.sendMessage("§7Tapez §cannuler §7pour annuler.");
    plugin.getUIManager().setChatInputListener(player, callback);
}
```

### 23.5 Pagination standard

```java
// Pour les listes longues (auction, membres, etc.)
protected void setupPagination(int currentPage, int maxPage, int prevSlot, int nextSlot) {
    if (currentPage > 1) setItem(prevSlot, Material.ARROW, "§7Page " + (currentPage - 1));
    if (currentPage < maxPage) setItem(nextSlot, Material.ARROW, "§7Page " + (currentPage + 1));
}
```

### 23.6 Checklist de qualité UI

Pour chaque UI implémentée, vérifier :
- [ ] Titre traduit avec `§` (pas de couleurs RGB — Bukkit 1.20.4)
- [ ] Taille valide (multiple de 9)
- [ ] `event.setCancelled(true)` dans `InventoryClickEvent`
- [ ] `event.setCancelled(true)` dans `InventoryDragEvent`
- [ ] Listener unregistered dans `onClose`
- [ ] Slots vides remplis avec `BLACK_STAINED_GLASS_PANE` (nom " ")
- [ ] Bouton RETOUR (slot 45) et FERMER (slot 49) présents
- [ ] Messages d'erreur en rouge (`§c`)
- [ ] Messages de succès en vert (`§a`)
- [ ] Données dynamiques rafraîchies à l'ouverture

---

## 24. Liste complète des UIs à implémenter

| # | Classe Java | Commande déclencheur | Titre | Lignes | Priorité |
|---|-------------|----------------------|-------|--------|----------|
| 1 | `MainMenuUI` | `/cantale` | Menu Principal | 6 | CRITIQUE |
| 2 | `FactionMainUI` | `/f` | Faction | 6 | CRITIQUE |
| 3 | `FactionMembersUI` | `/f members` | Membres | 6 | CRITIQUE |
| 4 | `FactionClaimsUI` | `/f claim` | Claims | 5 | CRITIQUE |
| 5 | `FactionBankUI` | `/f bank` | Banque | 5 | HAUTE |
| 5b | `FactionBankHistoryUI` | `/f bank` → Historique | Historique banque | 6 | MOYENNE |
| 6 | `FactionPermsUI` | `/f perms` | Permissions | 5 | HAUTE |
| 7 | `FactionCreateUI` | `/f create` | Créer | 3 | HAUTE |
| 8 | `FactionInviteUI` | `/f invite` | Inviter | 3 | MOYENNE |
| 9 | `FactionPromoteUI` | `/f promote` | Grades | 4 | MOYENNE |
| 10 | `EconomyUI` | `/balance` | Économie | 4 | CRITIQUE |
| 11 | `PayPlayerUI` | `/pay` | Payer | 3 | HAUTE |
| 12 | `AuctionMainUI` | `/ah` | Auction House | 6 | CRITIQUE |
| 13 | `AuctionBrowseUI` | `/ah list` | Annonces | 6 | CRITIQUE |
| 14 | `AuctionSellUI` | `/ah sell` | Vendre | 3 | HAUTE |
| 15 | `AuctionMyListingsUI` | `/ah my` | Mes annonces | 5 | HAUTE |
| 16 | `TradeSelectUI` | `/trade` | Trade | 3 | HAUTE |
| 17 | `HomeUI` | `/home` | Homes | 5 | CRITIQUE |
| 18 | `WarpUI` | `/warp` | Warps | 5 | CRITIQUE |
| 19 | `TeleportUI` | — (depuis menu) | Téléportation | 3 | HAUTE |
| 20 | `LifeUI` | `/dropvie` | Vies | 4 | CRITIQUE |
| 21 | `ProfileUI` | `/profile` | Profil | 5 | CRITIQUE |
| 22 | `LeaderboardUI` | `/leaderboard` | Classements | 5 | HAUTE |
| 23 | `VoteUI` | `/vote` | Vote | 5 | HAUTE |
| 24 | `WantedUI` | `/wanted` | Primes | 5 | MOYENNE |
| 25 | `CustomItemsInfoUI` | `/customitem` | Items | 5 | HAUTE |
| 26 | `CustomItemAdminUI` | `/customitem` (admin) | Items Admin | 4 | MOYENNE |
| 27 | `PrivateChestUI` | `/pc` | Coffres | 3 | HAUTE |
| 28 | `ModerationUI` | `/moderation` | Modération | 4 | HAUTE |
| 29 | `ModMuteUI` | `/moderation mute` | Mute | 4 | MOYENNE |
| 30 | `AdminUI` | `/admin` | Admin | 5 | MOYENNE |
| 31 | `AdminLifeUI` | `/addlife` | Gérer Vies | 3 | MOYENNE |
| 32 | `RankUI` | `/rank` | Grades | 4 | HAUTE |
| 33 | `RankAdminUI` | `/rank` (admin) | Ranks Admin | 4 | MOYENNE |
| 34 | `PermissionUI` | `/perm` | Permissions | 5 | MOYENNE |
| 35 | `WebLinkUI` | `/web` (redir. site) / `/link` | Liaisons | 3 | BASSE |
| 36 | `PlayerSelectorUI` | (réutilisable) | Sélection | 5 | CRITIQUE |
| 37 | `ConfirmDialogUI` | (réutilisable) | Confirmer | 3 | CRITIQUE |

---

*Document généré pour le projet Cantale — Paper 1.20.4*
*Ce document est conçu pour être envoyé à une IA génératrice d'UIs Minecraft.*
