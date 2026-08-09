# Cantale — Guide art Blockbench & Resource Pack

Tout ce qu’il faut refaire / peaufiner côté **textures**, **modèles 3D** et **interfaces**.  
Pack actuel : `Cantale-ResourcePack-full/` · format **75** (Minecraft **1.21.11**).

> Après chaque lot de textures :  
> `cd Cantale-ResourcePack-full && zip -r ../cantale-resourcepack.zip .`  
> puis `shasum -a 1 ../cantale-resourcepack.zip` → maj SHA dans `server.properties`.

---

## 1. Specs Blockbench (règles générales)

| Élément | Format | Dimensions | Notes |
|---------|--------|------------|-------|
| **Item 2D** (generated) | PNG | **16×16** | Opaque ou alpha. Style pixel. |
| **Item 3D** (Blockbench model) | `.bbmodel` → export JSON + PNG | Texture **16×16** ou **32×32** | Parent `item/handheld` (outils) ou `item/generated` (objets) |
| **Armure (pièce)** | PNG | **16×16** (icône inventaire) | Pour l’instant icône item seulement (pas d’armor trim 3D) |
| **pack.png** | PNG | **64×64** | Icône du pack dans la liste |
| **HUD sprite** | PNG | **8×8** ou **9×9** | Vies custom, etc. |
| **Hotbar** | PNG | **182×22** (vanilla) | Remplacer proprement |
| **Hotbar selection** | PNG | **24×23** (vanilla) | — |
| **Cœurs / faim** | PNG | **9×9** | `heart/full`, `heart/empty`, `food/full`, `food/empty` |
| **XP bar** | PNG | **182×5** | background + progress |
| **Crosshair** | PNG | **15×15** | — |
| **GUI coffre 54 slots** | PNG | **176×222** | `generic_54.png` |
| **GUI inventaire joueur** | PNG | **176×166** | `inventory.png` |
| **GUI crafting** | PNG | **176×166** | `crafting_table.png` |
| **GUI enderchest** | PNG | **176×166** | **à créer** |
| **Widgets** | PNG | **256×256** | boutons vanilla |
| **Logo titre** | PNG | **1024×256** approx. | `title/minecraft.png` |
| **Panorama** | PNG ×6 | **1024×1024** / face | `panorama_0` … `_5` + overlay |

### Palette Cantale (recommandée)

| Rôle | Hex |
|------|-----|
| Rouge sang / hardcore | `#B01020` |
| Or Cantox | `#F0B84A` |
| Noir / ombre | `#1A1A1A` |
| Ivoire UI | `#E8E0D0` |
| Accent rare | `#3B82F6` |
| Accent épique | `#A855F7` |
| Accent mythique | `#EC4899` |

### Workflow Blockbench

1. Nouveau projet **Java Block/Item** (ou *Free model* pour 3D).
2. Grille **16×16** pour items plats.
3. Export :
   - texture → `assets/cantale/textures/item/<nom>.png`
   - modèle → `assets/cantale/models/item/<nom>.json` (si 3D custom ; sinon garder `parent: item/generated` + `layer0`)
4. Pour un item **plat** simple, pas besoin de modèle 3D : juste le PNG + JSON :
   ```json
   {
     "parent": "minecraft:item/generated",
     "textures": { "layer0": "cantale:item/<nom>" }
   }
   ```
5. Le routing CMD est déjà dans `assets/minecraft/items/<materiel>.json` — **ne pas** remettre d’`overrides` dans `models/item/`.

---

## 2. Items custom à texturer (priorité)

### 2.1 Priorité HAUTE — manquants (placeholders vanilla aujourd’hui)

| # | Fichier PNG | CMD | Matériel | Nom in-game | Style / note |
|---|-------------|-----|----------|-------------|--------------|
| 1 | `crate_vote.png` | 100 | Paper | Cadeau du Roi | Cadeau / sceau royal, sobre |
| 2 | `crate_rare.png` | 101 | Tripwire hook | Trésor Public | Clef / coffre public, bleu |
| 3 | `crate_epic.png` | 102 | Sunflower | Médaille du Tournoi | Médaille pourpre |
| 4 | `crate_mythic.png` | 103 | Gold nugget | Pièce Mythique | Pièce rose / mythique |
| 5 | `crate_legendary.png` | 104 | Writable book | Ticket Légendaire | Ticket or / parchemin |
| 6 | `garde_helmet.png` | 9001 | Netherite helmet | Casque du Garde | Set cohérent « garde » |
| 7 | `garde_chestplate.png` | 9002 | Netherite chestplate | Plastron du Garde | Idem |
| 8 | `garde_leggings.png` | 9003 | Netherite leggings | Jambières du Garde | Idem |
| 9 | `garde_boots.png` | 9004 | Netherite boots | Bottes du Garde | Idem |

Après export PNG : dans chaque `assets/cantale/models/item/crate_*.json` et `garde_*.json`, remplacer  
`"layer0": "minecraft:item/..."` → `"layer0": "cantale:item/<nom>"`.

---

### 2.2 Priorité MOYENNE — présents mais « moches » / à refaire

Tous en **16×16**. Une texture partagée pour plusieurs CMD quand indiqué.

#### Pickantaxes (12 CMD → **3 PNG**)

| Texture | CMD | Matériel |
|---------|-----|----------|
| `pickantaxe_iron.png` | 1001–1004 | Iron pickaxe |
| `pickantaxe_diamond.png` | 1011–1014 | Diamond pickaxe |
| `pickantaxe_netherite.png` | 1021–1024 | Netherite pickaxe |

Variants tailles **1×1 / 3×3 / 5×5 / 7×7** : même PNG pour l’instant.  
Optionnel plus tard : 4 PNG par tier (badge zone sur la tête de pioche).

#### Cantaxes (3)

| Texture | CMD | Matériel |
|---------|-----|----------|
| `cantaxe_iron.png` | 2001 | Iron axe |
| `cantaxe_diamond.png` | 2002 | Diamond axe |
| `cantaxe_netherite.png` | 2003 | Netherite axe |

→ Hache « coupe arbre one-shot », silhouette reconnaissable.

#### Multi-Cantool (6 CMD → **3 PNG**)

| Texture | CMD | Matériel de base |
|---------|-----|------------------|
| `multicantool_iron.png` | 3001–3002 | Iron pickaxe (+ axe/shovel/shears via adapter) |
| `multicantool_diamond.png` | 3011–3012 | Diamond … |
| `multicantool_netherite.png` | 3021–3022 | Netherite … |

Le client change le **matériel affiché** (pioche/hache/pelle/cisailles) mais garde le **même CMD** → une seule texture multi-outil par tier suffit.  
Doit rester lisible sur les 4 silhouettes vanilla.

#### Arme / conso

| Texture | CMD | Matériel | Note |
|---------|-----|----------|------|
| `cantalame.png` | 5001 | Netherite sword | Épée évolutive (1 seule texture) |
| `vie.png` | 7001 | Totem of undying | +1 vie, icône « cœur / totem Cantale » |
| `cantox.png` | 8001 | Gold nugget (dispatch) | Pièce or (icône UI / économie) |

---

### 2.3 Priorité BASSE — legacy (plus dans le plugin, garder ou ignorer)

| Texture | CMD pack | Note |
|---------|----------|------|
| `rune_fortification.png` | 6001 / 24 | Item retiré |
| `statio_lytras.png` | (elytra orphelin) | Item retiré ; anim 16×96 optionnelle abandonnée |

---

## 3. Table complète CMD → fichier

| CMD | Enum / type | Fichier texture | Modèle JSON |
|-----|-------------|-----------------|-------------|
| 100 | VOTE crate | `crate_vote.png` | `crate_vote.json` |
| 101 | RARE crate | `crate_rare.png` | `crate_rare.json` |
| 102 | EPIC crate | `crate_epic.png` | `crate_epic.json` |
| 103 | MYTHIC crate | `crate_mythic.png` | `crate_mythic.json` |
| 104 | LEGENDARY crate | `crate_legendary.png` | `crate_legendary.json` |
| 1001–1004 | Pickantaxe Fer | `pickantaxe_iron.png` | `pickantaxe_iron_{1x1,3x3,5x5,7x7}.json` |
| 1011–1014 | Pickantaxe Diamant | `pickantaxe_diamond.png` | idem diamond |
| 1021–1024 | Pickantaxe Netherite | `pickantaxe_netherite.png` | idem netherite |
| 2001–2003 | Cantaxe | `cantaxe_{iron,diamond,netherite}.png` | `cantaxe_*.json` |
| 3001–3002 | Multi Fer | `multicantool_iron.png` | `multicantool_iron(_1x1).json` |
| 3011–3012 | Multi Diamant | `multicantool_diamond.png` | idem |
| 3021–3022 | Multi Netherite | `multicantool_netherite.png` | idem |
| 5001 | Cantalame | `cantalame.png` | `cantalame.json` |
| 7001 | Vie | `vie.png` | `vie.json` |
| 8001 | Cantox coin | `cantox.png` | `cantox_coin.json` |
| 9001–9004 | Armure du Garde | `garde_{helmet,chestplate,leggings,boots}.png` | `garde_*.json` |

Chemin textures : `Cantale-ResourcePack-full/assets/cantale/textures/item/`  
Chemin modèles : `Cantale-ResourcePack-full/assets/cantale/models/item/`

---

## 4. HUD & police (déjà câblés)

| Fichier | Dim | Usage |
|---------|-----|--------|
| `textures/gui/sprites/hud/cantale_life_full.png` | 8×8 | Char font `\uE000` |
| `textures/gui/sprites/hud/cantale_life_empty.png` | 8×8 | Char font `\uE001` |
| `textures/gui/sprites/hud/heart/full.png` | 9×9 | Cœur plein |
| `textures/gui/sprites/hud/heart/empty.png` | 9×9 | Cœur vide |
| `textures/gui/sprites/hud/food/full.png` | 9×9 | Faim |
| `textures/gui/sprites/hud/food/empty.png` | 9×9 | Faim vide |
| `textures/gui/sprites/hud/hotbar.png` | 182×22 | Hotbar |
| `textures/gui/sprites/hud/hotbar_selection.png` | 24×23 | Sélection |
| `textures/gui/sprites/hud/crosshair.png` | 15×15 | Viseur |
| `textures/gui/sprites/hud/experience_bar_*.png` | 182×5 | XP |
| `font/default.json` | — | Bitmap vies + référence vanilla |

À refaire si tu veux un HUD « propre » Cantale (actuellement placeholders légers).

**Ne jamais** mettre `textures/` dans le champ `file` du font JSON (sinon double chemin → crash reload).

---

## 5. Interfaces inventaire (menus plugin)

Les menus sont des **inventaires chest** Paper (`BaseUI`).  
Ils utilisent surtout des items vanilla + vitres. L’art « UI » passe par :

1. Texture globale **`generic_54.png`** (tous les menus 54 slots + PC)
2. Optionnel : items custom comme **icônes de boutons** (CMD dédiés plus tard)
3. `enderchest.png` pour `/ec`

### 5.1 Liste des UIs

| UI | Titre (approx.) | Slots | Fond / art |
|----|-----------------|-------|------------|
| `MainMenuUI` | Menu Principal | 54 (6×9) | `generic_54` + icônes hub |
| `ProfileUI` | Profil | 45 (5×9) | Totem Vie, Cantox, BossBar |
| `StatisticsUI` | Statistiques | 45 | — |
| `TagCustomizationUI` | Gestion des Tags | 36 (4×9) | — |
| `LifeUI` | Système de Vies | 36 | Item Vie 7001 |
| `EconomyUI` | Économie | 36 | Icône Cantox |
| `ShopMainUI` | Boutique Admin | 36 | Catégories |
| `ShopCategoryUI` | Shop — … | 54 | Pagination |
| `AuctionUI` | Hôtel des Ventes | 36 | — |
| `MyAuctionListingsUI` | Mes Ventes | 36 | — |
| `TradeUI` | Trade | 27 (3×9) | — |
| `WantedUI` | Primes Wanted | 45 | Têtes |
| `LeaderboardUI` | Classements | 27 | — |
| `LeaderboardCategoryUI` | Classement: … | 45 | — |
| `PlayerSelectorUI` | (dynamique) | 45 | Têtes joueurs |
| `ConfirmDialogUI` | (dynamique) | 27 | Vert / rouge |
| `FactionMainUI` | Faction | 54 | — |
| `FactionMembersUI` | Membres | 54 | — |
| `MemberActionUI` | Actions joueur | 27 | — |
| `FactionClaimsUI` | Claims | 54 | — |
| `FactionHomesUI` | Homes | 54 | — |
| `FactionWarpsUI` | Warps | 54 | — |
| `FactionBankUI` | Banque | 45 | — |
| `CrateOpenUI` | Trésor Public | 27 | Anim ouverture rare |
| `HarvestDepositUI` | [Récolte] … | 36 | Dépôt event faction |

### 5.2 Coffres privés (vrais inventaires)

| Type | Titre | Texture GUI |
|------|-------|-------------|
| PC1 / PC2 / PC3 | Coffre Privé #1–3 | `generic_54.png` **176×222** |
| EC | Ender Chest | `enderchest.png` **176×166** ← **à créer** |

### 5.3 Écrans titre (optionnel)

| Fichier | Dim | Rôle |
|---------|-----|------|
| `gui/title/minecraft.png` | ~1024×256 | Logo → **CANTALE** |
| `gui/title/edition.png` | variable | Sous-titre « Trois vies… » |
| `gui/title/background/panorama_0..5.png` | 1024×1024 | Menu principal immersif |
| `gui/widgets.png` | 256×256 | Boutons menu |

---

## 6. Checklist de production (ordre conseillé)

### Sprint A — jouable / identifiable
- [ ] 5 PNG caisses (`crate_*`)
- [ ] 4 PNG Armure du Garde (`garde_*`)
- [ ] Brancher `layer0` → `cantale:item/...`
- [ ] Rezip + SHA + push hébergement

### Sprint B — outils lisibles
- [ ] Refaire 3 pickantaxes
- [ ] Refaire 3 cantaxes
- [ ] Refaire 3 multi-cantools
- [ ] Refaire cantalame + vie + cantox

### Sprint C — HUD / ambiance
- [ ] HUD (hotbar, cœurs, faim, XP, crosshair)
- [ ] Icônes vies font `\uE000` / `\uE001`
- [ ] `generic_54.png` + `enderchest.png` thème Cantale
- [ ] Logo titre + panorama (si tu veux le splash screen)

### Sprint D — polish optionnel
- [ ] Variants PNG par taille pickantaxe (1×1…7×7)
- [ ] Modèles 3D Blockbench (outils handheld avec relief)
- [ ] Icônes CMD dédiées pour boutons de menus (shop, faction, etc.)

---

## 7. Récap quantités

| Catégorie | À produire |
|-----------|------------|
| PNG items **manquants** | **9** (5 caisses + 4 garde) |
| PNG items **à refaire** | **14** (outils + vie + cantox + cantalame) |
| PNG HUD | **~12** fichiers |
| PNG GUI menus | **2–4** (`generic_54`, `enderchest`, inventaire, crafting) |
| Titre / panorama | **1 logo + 6 faces + overlay** |
| **Total art « propre »** | ~40–45 fichiers |

---

## 8. Tests après export

1. Activer le pack **en local** (Options → Resource Packs) — doit charger sans « Resource reload failed ».
2. `/customitem` / give caisses → vérifier chaque CMD.
3. Multi-Cantool : viser bois / terre / laine → forme change, texture multi reste.
4. Armure du Garde complète → 4 pièces custom.
5. Ouvrir `/menu`, `/shop`, `/pc`, `/ec` → GUI OK.
6. Si OK : upload zip + maj `resource-pack-sha1` serveur.

---

## 9. Hébergement pack (rappel)

```properties
resource-pack=https\://raw.githubusercontent.com/Annonnyx/Cantale-resourcepack/585bbda/cantale-resourcepack.zip
resource-pack-sha1=<NOUVEAU_SHA_APRES_REZIP>
```

Plugin `config.yml` :
```yaml
resource-pack:
  send-on-join: false
  url: ""
  sha1: ""
```

---

*Doc générée pour la prod art Cantale — à mettre à jour si nouveaux CMD / UIs.*
