# Cantale — Guide du Resource Pack

> Pack **obligatoire** pour jouer.  
> **Minecraft 1.21.11** — `min_format` / `max_format` 75.

---

## 1. Architecture réelle

```
Cantale-ResourcePack-full/
├── pack.mcmeta
├── pack.png
├── assets/
│   ├── minecraft/
│   │   ├── font/default.json              # Caractères custom (vies HUD)
│   │   ├── items/                         # Item Model Definitions 1.21.4+
│   │   │   ├── iron_pickaxe.json
│   │   │   ├── diamond_pickaxe.json
│   │   │   ├── netherite_pickaxe.json
│   │   │   ├── iron_axe.json
│   │   │   ├── diamond_axe.json
│   │   │   ├── netherite_axe.json
│   │   │   ├── iron_shovel.json
│   │   │   ├── diamond_shovel.json
│   │   │   ├── netherite_shovel.json
│   │   │   ├── shears.json
│   │   │   ├── netherite_sword.json
│   │   │   ├── totem_of_undying.json
│   │   │   ├── paper.json
│   │   │   ├── tripwire_hook.json
│   │   │   ├── sunflower.json
│   │   │   ├── gold_nugget.json
│   │   │   ├── writable_book.json
│   │   │   └── netherite_helmet/chestplate/leggings/boots.json
│   │   ├── models/item/                   # Modèles vanilla de base (fallback)
│   │   ├── textures/
│   │   │   ├── gui/
│   │   │   │   ├── container/
│   │   │   │   │   ├── generic_54.png     # /pc, /pc2, /ah, /trade, /wanted, /f, /shop
│   │   │   │   │   ├── crafting_table.png
│   │   │   │   │   └── inventory.png
│   │   │   │   ├── sprites/hud/
│   │   │   │   │   ├── cantale_life_full.png
│   │   │   │   │   ├── cantale_life_empty.png
│   │   │   │   │   ├── crosshair.png
│   │   │   │   │   ├── experience_bar_background.png
│   │   │   │   │   ├── experience_bar_progress.png
│   │   │   │   │   ├── food/
│   │   │   │   │   ├── heart/
│   │   │   │   │   ├── hotbar.png
│   │   │   │   │   └── hotbar_selection.png
│   │   │   │   └── title/
│   │   │   │       └── minecraft.png      # Logo CANTALE
│   │   │   └── widgets.png
│   │   └── lang/fr_fr.json
│   └── cantale/
│       ├── models/item/                   # 35 modèles custom
│       └── textures/item/                 # 14 textures PNG
└── docs/
    └── textures-item-README.md
```

---

## 2. pack.mcmeta

```json
{
  "pack": {
    "min_format": 75,
    "max_format": 75,
    "description": "CANTALE - Trois vies. C'est tout."
  }
}
```

> Depuis 1.21.4, `pack_format` est remplacé par `min_format` / `max_format`.

---

## 3. Table des CustomModelData

| CMD | Item | Matériel vanilla | Modèle cantale |
|-----|------|------------------|----------------|
| 100 | Caisse Vote | `PAPER` | `crate_vote.json` |
| 101 | Caisse Rare | `TRIPWIRE_HOOK` | `crate_rare.json` |
| 102 | Caisse Épique | `SUNFLOWER` | `crate_epic.json` |
| 103 | Caisse Mythique | `GOLD_NUGGET` | `crate_mythic.json` |
| 104 | Caisse Légendaire | `WRITABLE_BOOK` | `crate_legendary.json` |
| 1001–1004 | Pickantaxe Fer 1×1→7×7 | `IRON_PICKAXE` | `pickantaxe_iron_*.json` |
| 1011–1014 | Pickantaxe Diamant | `DIAMOND_PICKAXE` | `pickantaxe_diamond_*.json` |
| 1021–1024 | Pickantaxe Netherite | `NETHERITE_PICKAXE` | `pickantaxe_netherite_*.json` |
| 2001–2003 | Cantaxe Fer/Diamant/Netherite | `*_AXE` | `cantaxe_*.json` |
| 3001–3002 | Multi-Cantool Fer | `IRON_PICKAXE` + autres | `multicantool_iron*.json` |
| 3011–3012 | Multi-Cantool Diamant | `DIAMOND_PICKAXE` + autres | `multicantool_diamond*.json` |
| 3021–3022 | Multi-Cantool Netherite | `NETHERITE_PICKAXE` + autres | `multicantool_netherite*.json` |
| 5001 | Cantalame | `NETHERITE_SWORD` | `cantalame.json` |
| 6001 | Rune de Fortification (legacy) | `PAPER` | `rune_fortification.json` |
| 7001 | **Vie** | `TOTEM_OF_UNDYING` | `vie.json` |
| 8001 | Cantox (legacy) | `GOLD_NUGGET` | `cantox_coin.json` |
| 9001–9004 | Armure du Garde | `NETHERITE_*` | `garde_*.json` |

> **Vie = CMD 7001** (pas 25).  
> Multi-Cantool : même CMD sur pioche/hache/pelle/cisailles — chaque matériel a son propre `items/*.json`.

---

## 4. Item Model Definitions (1.21.4+)

Chaque matériel vanilla a un fichier `assets/minecraft/items/<item>.json` :

```json
{
  "model": {
    "type": "minecraft:range_dispatch",
    "property": "minecraft:custom_model_data",
    "index": 0,
    "fallback": { "type": "minecraft:model", "model": "minecraft:item/iron_pickaxe" },
    "entries": [
      { "threshold": 1001, "model": { "type": "minecraft:model", "model": "cantale:item/pickantaxe_iron_1x1" } }
    ]
  }
}
```

Les anciens `overrides` dans `models/item/*.json` sont **ignorés** par le client 1.21.4+.

---

## 5. Textures items

**14 PNG déjà présents** dans `assets/cantale/textures/item/` :

| Fichier | Item |
|---------|------|
| `pickantaxe_iron.png` | Pickantaxe Fer |
| `pickantaxe_diamond.png` | Pickantaxe Diamant |
| `pickantaxe_netherite.png` | Pickantaxe Netherite |
| `cantaxe_iron.png` | Cantaxe Fer |
| `cantaxe_diamond.png` | Cantaxe Diamant |
| `cantaxe_netherite.png` | Cantaxe Netherite |
| `multicantool_iron.png` | Multi-Cantool Fer |
| `multicantool_diamond.png` | Multi-Cantool Diamant |
| `multicantool_netherite.png` | Multi-Cantool Netherite |
| `statio_lytras.png` | Statio-Lytras (legacy) |
| `cantalame.png` | Cantalame |
| `rune_fortification.png` | Rune (legacy) |
| `vie.png` | Vie |
| `cantox.png` | Cantox |

**À créer** : `crate_vote.png`, `crate_rare.png`, `crate_epic.png`, `crate_mythic.png`, `crate_legendary.png`, `garde_helmet.png`, `garde_chestplate.png`, `garde_leggings.png`, `garde_boots.png`.

---

## 6. Textures GUI

### Inventaires

| Fichier | Usage |
|---------|-------|
| `gui/container/generic_54.png` | Toutes les UIs 54 slots |
| `gui/container/crafting_table.png` | Table de craft |
| `gui/container/inventory.png` | Inventaire joueur |

### HUD

| Fichier | Usage |
|---------|-------|
| `gui/sprites/hud/cantale_life_full.png` | Vie pleine |
| `gui/sprites/hud/cantale_life_empty.png` | Vie vide |
| `gui/sprites/hud/hotbar.png` | Barre d’inventaire |
| `gui/sprites/hud/hotbar_selection.png` | Sélection hotbar |
| `gui/sprites/hud/experience_bar_*.png` | Barre XP |
| `gui/sprites/hud/crosshair.png` | Viseur |
| `gui/sprites/hud/heart/` | Cœurs custom |
| `gui/sprites/hud/food/` | Nourriture custom |

### Titre

| Fichier | Usage |
|---------|-------|
| `gui/title/minecraft.png` | Logo CANTALE |
| `gui/title/background/` | Panorama titre |

> `enderchest.png` et `edition.png` sont **absents** du pack actuel.

---

## 7. Font custom

`assets/minecraft/font/default.json` ajoute les caractères :

| Caractère | Texture | Usage |
|-----------|---------|-------|
| `\uE000` | `cantale_life_full.png` | Vie pleine |
| `\uE001` | `cantale_life_empty.png` | Vie vide |

---

## 8. Workflow Blockbench

1. **File → New → Minecraft Item** — 16×16
2. Dessiner avec la palette Cantale : rouge `#B01020`, or `#F0B84A`, fond `#1A1A1A`
3. **File → Export → Export Texture** → `assets/cantale/textures/item/`
4. Mettre à jour le `layer0` du modèle JSON correspondant
5. Rezipper le pack

---

## 9. Sécurité

- PDC `cantale:custom_item` + `cantale:auth` (SHA-256) côté serveur
- CustomModelData côté client uniquement pour la texture
- Sans pack : items fonctionnent mais look vanilla

---

## 10. Configuration serveur

```properties
resource-pack=https://raw.githubusercontent.com/Annonnyx/Cantale-resourcepack/main/cantale-resourcepack.zip
resource-pack-sha1=<SHA1_DU_ZIP>
require-resource-pack=true
resource-pack-prompt=§6Cantale §7requiert le resource pack pour jouer.
```

```bash
# Calculer le SHA-1
shasum -a 1 cantale-resourcepack.zip
```

---

## 11. Checklist

- [x] `pack.mcmeta` avec `min_format`/`max_format` 75
- [x] `pack.png` 64×64
- [x] 14 textures items dans `assets/cantale/textures/item/`
- [x] 35 modèles JSON dans `assets/cantale/models/item/`
- [x] 21 Item Model Definitions dans `assets/minecraft/items/`
- [x] `generic_54.png`, `crafting_table.png`, `inventory.png`
- [x] HUD : vies, hotbar, XP, cœurs, nourriture
- [x] `minecraft.png` logo titre
- [x] `font/default.json` avec `\uE000` / `\uE001`
- [x] `lang/fr_fr.json`
- [ ] `enderchest.png` (absent)
- [ ] `edition.png` (absent)
- [ ] Textures crates + garde (à faire)
- [ ] Rezipper + SHA-1 + upload

---

*Document synchronisé avec le pack actuel — Minecraft 1.21.11*
