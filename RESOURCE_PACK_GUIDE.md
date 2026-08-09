# Cantale — Guide Complet du Resource Pack

> Pack **obligatoire** pour jouer. Détaille chaque item, UI, Blockbench, et sécurité.

## ✅ PACK PRÊT — STRUCTURE COMPLÈTE

**Le resource pack est entièrement configuré.** Tous les fichiers JSON sont créés et prêts. Tu n'as plus qu'à :

1. **Créer les textures PNG** dans Blockbench (16×16 pixels)
2. **Les exporter** dans `assets/cantale/textures/item/`
3. **Créer les textures GUI** (optionnel)
4. **Zipper et uploader**

### 📁 Emplacement du pack configuré
```
/Users/Noe/Cantale/Cantale-ResourcePack/
├── pack.mcmeta ✓
├── assets/minecraft/models/item/ ✓ (modèles de base, fallback)
├── assets/minecraft/items/ ✓ (dispatch réel 1.21.4+, un fichier par matériel vanilla)
├── assets/cantale/models/item/ ✓ (25 fichiers custom)
└── [à créer] assets/cantale/textures/item/ (14 PNG)
```

---

## 1. Architecture

```
Cantale-ResourcePack/
├── pack.mcmeta                    # format 75 (1.21.11)
├── pack.png                       # 64×64
├── assets/
│   ├── minecraft/
│   │   ├── models/item/           # Overrides custom_model_data
│   │   │   ├── iron_pickaxe.json
│   │   │   ├── diamond_pickaxe.json
│   │   │   ├── netherite_pickaxe.json
│   │   │   ├── iron_axe.json
│   │   │   ├── diamond_axe.json
│   │   │   ├── netherite_axe.json
│   │   │   ├── shears.json
│   │   │   ├── elytra.json
│   │   │   ├── netherite_sword.json
│   │   │   ├── enchanted_book.json
│   │   │   └── totem_of_undying.json   # Item Vie
│   │   └── textures/
│   │       ├── item/              # Textures items custom
│   │       │   ├── pickantaxe_iron.png
│   │       │   ├── pickantaxe_diamond.png
│   │       │   ├── pickantaxe_netherite.png
│   │       │   ├── cantaxe_iron.png
│   │       │   ├── cantaxe_diamond.png
│   │       │   ├── cantaxe_netherite.png
│   │       │   ├── multicantool_iron.png
│   │       │   ├── multicantool_diamond.png
│   │       │   ├── multicantool_netherite.png
│   │       │   ├── statio_lytras.png
│   │       │   ├── cantalame.png
│   │       │   ├── rune_fortification.png
│   │       │   └── vie.png
│   │       └── gui/
│   │           ├── container/
│   │           │   ├── generic_54.png      # /pc, /pc2, /ah, /trade, /wanted, /f
│   │           │   └── enderchest.png      # /ec
│   │           └── title/
│   │               ├── minecraft.png       # Logo → CANTALE
│   │               └── edition.png         # "Hardcore Edition"
│   └── cantale/
│       ├── models/item/           # Modèles custom Cantale (25 fichiers)
│       └── textures/item/         # Miroir des textures
└── README.md
```

## 2. pack.mcmeta

```json
{
  "pack": {
    "pack_format": 75,
    "description": "§6Cantale §7— Items Custom & UI"
  }
}
```

> ⚠️ **Depuis Minecraft 1.21.4**, le système d'`overrides` dans `assets/minecraft/models/item/*.json` est **ignoré par le client**.
> Il faut désormais fournir un fichier `assets/minecraft/items/<item_vanilla>.json` par matériel de base, utilisant
> `minecraft:range_dispatch` sur la propriété `minecraft:custom_model_data`. Les fichiers `models/item/*.json` restent utiles
> uniquement comme modèle de base (parent + texture vanilla), référencé en `fallback`. Voir `assets/minecraft/items/` pour les
> fichiers déjà à jour (un par matériel : `iron_pickaxe`, `diamond_pickaxe`, `netherite_pickaxe`, `iron_axe`, `diamond_axe`,
> `netherite_axe`, `iron_shovel`, `diamond_shovel`, `netherite_shovel`, `shears`, `netherite_sword`, `totem_of_undying`,
> `paper`, `tripwire_hook`, `sunflower`, `gold_nugget`, `writable_book`, `netherite_helmet/chestplate/leggings/boots`).

## 3. Table des CustomModelData (à jour avec `CustomItemManager#getCustomModelData` + `CrateManager`)

| CMD  | Item                          | Matériel de base       | Modèle cantale                     |
|------|-------------------------------|-------------------------|-------------------------------------|
| 100  | Caisse Vote                   | `PAPER`                | `crate_vote.json`                 |
| 101  | Caisse Rare                   | `TRIPWIRE_HOOK`        | `crate_rare.json`                 |
| 102  | Caisse Épique                 | `SUNFLOWER`            | `crate_epic.json`                 |
| 103  | Caisse Mythique               | `GOLD_NUGGET`          | `crate_mythic.json`               |
| 104  | Caisse Légendaire             | `WRITABLE_BOOK`        | `crate_legendary.json`            |
| 1001 | Pickantaxe Fer 1×1            | `IRON_PICKAXE`         | `pickantaxe_iron_1x1.json`        |
| 1002 | Pickantaxe Fer 3×3            | `IRON_PICKAXE`         | `pickantaxe_iron_3x3.json`        |
| 1003 | Pickantaxe Fer 5×5            | `IRON_PICKAXE`         | `pickantaxe_iron_5x5.json`        |
| 1004 | Pickantaxe Fer 7×7            | `IRON_PICKAXE`         | `pickantaxe_iron_7x7.json`        |
| 1011 | Pickantaxe Diamant 1×1        | `DIAMOND_PICKAXE`      | `pickantaxe_diamond_1x1.json`     |
| 1012 | Pickantaxe Diamant 3×3        | `DIAMOND_PICKAXE`      | `pickantaxe_diamond_3x3.json`     |
| 1013 | Pickantaxe Diamant 5×5        | `DIAMOND_PICKAXE`      | `pickantaxe_diamond_5x5.json`     |
| 1014 | Pickantaxe Diamant 7×7        | `DIAMOND_PICKAXE`      | `pickantaxe_diamond_7x7.json`     |
| 1021 | Pickantaxe Netherite 1×1      | `NETHERITE_PICKAXE`    | `pickantaxe_netherite_1x1.json`   |
| 1022 | Pickantaxe Netherite 3×3      | `NETHERITE_PICKAXE`    | `pickantaxe_netherite_3x3.json`   |
| 1023 | Pickantaxe Netherite 5×5      | `NETHERITE_PICKAXE`    | `pickantaxe_netherite_5x5.json`   |
| 1024 | Pickantaxe Netherite 7×7      | `NETHERITE_PICKAXE`    | `pickantaxe_netherite_7x7.json`   |
| 2001 | Cantaxe Fer                   | `IRON_AXE`             | `cantaxe_iron.json`               |
| 2002 | Cantaxe Diamant               | `DIAMOND_AXE`          | `cantaxe_diamond.json`            |
| 2003 | Cantaxe Netherite             | `NETHERITE_AXE`        | `cantaxe_netherite.json`          |
| 3001 | Multi-Cantool Fer 1×1         | `IRON_PICKAXE` (+ hache/pelle/cisailles selon visée) | `multicantool_iron_1x1.json`      |
| 3002 | Multi-Cantool Fer             | `IRON_PICKAXE` (+ hache/pelle/cisailles selon visée) | `multicantool_iron.json`          |
| 3011 | Multi-Cantool Diamant 1×1     | `DIAMOND_PICKAXE` (+ hache/pelle/cisailles)          | `multicantool_diamond_1x1.json`   |
| 3012 | Multi-Cantool Diamant         | `DIAMOND_PICKAXE` (+ hache/pelle/cisailles)          | `multicantool_diamond.json`       |
| 3021 | Multi-Cantool Netherite 1×1   | `NETHERITE_PICKAXE` (+ hache/pelle/cisailles)        | `multicantool_netherite_1x1.json` |
| 3022 | Multi-Cantool Netherite       | `NETHERITE_PICKAXE` (+ hache/pelle/cisailles)        | `multicantool_netherite.json`     |
| 5001 | Cantalame                     | `NETHERITE_SWORD`      | `cantalame.json`                  |
| 6001 | Rune de Fortification (legacy)| `PAPER`                | `rune_fortification.json`         |
| 7001 | Vie                           | `TOTEM_OF_UNDYING`     | `vie.json`                        |
| 8001 | Cantox (legacy)               | `GOLD_NUGGET`          | `cantox_coin.json`                |
| 9001–9004 | Armure du Garde          | `NETHERITE_*`          | `garde_*.json`                    |

> Le Multi-Cantool garde le **même CMD** quel que soit l'outil visuel affiché (pioche/hache/pelle/cisailles) : chaque matériel de
> base (`iron_axe`, `iron_shovel`, `shears`, ...) a donc son propre fichier `assets/minecraft/items/*.json` déclarant les mêmes
> CMD 3001/3002/3011/3012/3021/3022. `Statio-Lytras` et `Rune de Fortification` ont été retirés du plugin (voir TODO.md) ; leurs
> modèles restent présents dans le pack mais ne sont plus utilisés.
>
> **Caisses (CMD 100–104)** : stubs prêts — modèles `cantale:item/crate_*` avec `layer0` = texture **vanilla** (pas de PNG
> custom tant qu’ils n’existent pas). Quand les PNG Blockbench sont prêts : placer
> `assets/cantale/textures/item/crate_*.png`, remplacer `layer0` par `cantale:item/crate_*` dans chaque
> `assets/cantale/models/item/crate_*.json`, puis **rezipper** le pack. Voir aussi
> `assets/cantale/textures/item/README.md`.
>
> **Fusion CMD** : `paper.json` contient 100 (caisse) **et** 6001 (rune legacy) ; `gold_nugget.json` contient 24 (rune
> legacy), 103 (caisse) **et** 8001 (cantox legacy).

## 4. Modèles JSON — Exemples

### Modèle de base (`assets/minecraft/models/item/iron_pickaxe.json`) — parent + texture vanilla, sert de `fallback`

```json
{
  "parent": "item/handheld",
  "textures": { "layer0": "minecraft:item/iron_pickaxe" }
}
```

### Item Model Definition 1.21.4+ (`assets/minecraft/items/iron_pickaxe.json`) — dispatch réel côté client

```json
{
  "model": {
    "type": "minecraft:range_dispatch",
    "property": "minecraft:custom_model_data",
    "index": 0,
    "fallback": { "type": "minecraft:model", "model": "minecraft:item/iron_pickaxe" },
    "entries": [
      { "threshold": 1001, "model": { "type": "minecraft:model", "model": "cantale:item/pickantaxe_iron_1x1" } },
      { "threshold": 1002, "model": { "type": "minecraft:model", "model": "cantale:item/pickantaxe_iron_3x3" } },
      { "threshold": 1003, "model": { "type": "minecraft:model", "model": "cantale:item/pickantaxe_iron_5x5" } },
      { "threshold": 1004, "model": { "type": "minecraft:model", "model": "cantale:item/pickantaxe_iron_7x7" } }
    ]
  }
}
```

### Item Model Definition — Vie (`assets/minecraft/items/totem_of_undying.json`)

```json
{
  "model": {
    "type": "minecraft:range_dispatch",
    "property": "minecraft:custom_model_data",
    "index": 0,
    "fallback": { "type": "minecraft:model", "model": "minecraft:item/totem_of_undying" },
    "entries": [
      { "threshold": 7001, "model": { "type": "minecraft:model", "model": "cantale:item/vie" } }
    ]
  }
}
```

### Modèle custom Cantale (`cantale/models/item/vie.json`)

```json
{
  "parent": "minecraft:item/generated",
  "textures": { "layer0": "cantale:item/vie" },
  "display": {
    "thirdperson_righthand": { "rotation": [0, 0, 45], "translation": [0, 3.5, 0], "scale": [0.5, 0.5, 0.5] },
    "gui": { "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6] }
  }
}
```

### Modèle custom — Statio-Lytras (`cantale/models/item/statio_lytras.json`)

```json
{
  "parent": "minecraft:item/generated",
  "textures": { "layer0": "cantale:item/statio_lytras" },
  "display": {
    "thirdperson_righthand": { "scale": [1, 1, 1] },
    "gui": { "translation": [0, 1, 0] }
  }
}
```

---

## 5. Textures des Interfaces (UI)

### 5.1 Inventaires du plugin

| Commande  | Titre inventaire              | Type    | Texture vanilla override     |
|-----------|-------------------------------|---------|------------------------------|
| `/pc`     | `§6Coffre Privé §7#1`        | 27/54   | `gui/container/generic_54.png` |
| `/pc2`    | `§6Coffre Privé §7#2`        | 54      | `gui/container/generic_54.png` |
| `/ec`     | Ender Chest vanilla           | 27      | `gui/container/enderchest.png` |
| `/ah`     | `§6Hôtel des Ventes`          | 54      | `gui/container/generic_54.png` |
| `/trade`  | `§6Échange avec §e<player>`  | 54      | `gui/container/generic_54.png` |
| `/wanted` | `§6Tableau des Primes`        | 27      | `gui/container/generic_54.png` |
| `/f`      | `§6Faction: §e<nom>`         | 54      | `gui/container/generic_54.png` |

> **Note** : Minecraft ne différencie pas les textures par titre d'inventaire. Toutes les UIs 54 slots partagent `generic_54.png`. Pour distinguer, le plugin place des items décoratifs (verre gris) en bordure.

### 5.2 Spécifications GUI

- **`generic_54.png`** : 256×256, fond noir profond (#0A0A0A), bordures rouge Cantale (#B01020), accents or (#F0B84A), logo Cantale en filigrane coins
- **`enderchest.png`** : 128×128, particules ender + logo Cantale subtil
- **`minecraft.png`** (écran titre) : "CANTALE" en pixel art style Minecraft, 256×256
- **`edition.png`** : "HARDCORE EDITION" en petit sous le logo

### 5.3 Créer les textures GUI

1. Extraire la texture vanilla depuis `minecraft.jar/assets/minecraft/textures/gui/container/`
2. Ouvrir dans Aseprite/Photoshop/GIMP
3. Modifier : fond → #0A0A0A, bordures → #B01020, accents → #F0B84A
4. Ajouter logo Cantale en filigrane
5. Exporter en PNG

---

## 6. Guide Blockbench — Tu n'as qu'à faire les textures !

> **Les fichiers JSON sont déjà créés.** Tu n'as besoin que d'exporter les textures PNG depuis Blockbench.

### 6.1 Installation

1. Télécharger Blockbench sur [blockbench.net](https://blockbench.net)
2. Aucun plugin requis pour les items simples

### 6.2 Workflow simplifié — 3 étapes

#### Étape 1 : Créer l'item dans Blockbench

1. **File → New → Minecraft Item**
   - Nom : ex `pickantaxe_iron`
   - Texture size : **16×16**
   - Cocher "Create Texture"

2. **Dessiner** (onglet Paint)
   - Palette : View → Palette → Minecraft (couleurs vanilla)
   - Style : pixel art 16×16, inspire-toi des items vanilla
   - **Palette Cantale** : rouge `#B01020`, or `#F0B84A`, fond sombre `#1A1A1A`

#### Étape 2 : Exporter uniquement la texture PNG

**Tu n'as PAS besoin d'exporter le JSON !** Il est déjà créé.

1. **File → Export → Export Texture**
2. Choisir l'emplacement : `Cantale-ResourcePack/assets/cantale/textures/item/`
3. Nommer correctement selon la liste ci-dessous

#### Étape 3 : Répéter pour les 14 items

| # | Item à dessiner | Nom de fichier PNG | Inspiration |
|---|-----------------|-------------------|-------------|
| 1 | Pickantaxe Fer | `pickantaxe_iron.png` | Pioche fer + zone rouge 3×3 sur la tête |
| 2 | Pickantaxe Diamant | `pickantaxe_diamond.png` | Pioche diamant + lueur cyan/rouge |
| 3 | Pickantaxe Netherite | `pickantaxe_netherite.png` | Pioche netherite sombre + lueur |
| 4 | Cantaxe Fer | `cantaxe_iron.png` | Hache fer + motif tronc sur manche |
| 5 | Cantaxe Diamant | `cantaxe_diamond.png` | Hache diamant + accents cyan |
| 6 | Cantaxe Netherite | `cantaxe_netherite.png` | Hache netherite sombre |
| 7 | Multi-Cantool Fer | `multicantool_iron.png` | Cisailles + 4 têtes d'outils |
| 8 | Multi-Cantool Diamant | `multicantool_diamond.png` | Idem, accents cyan |
| 9 | Multi-Cantool Netherite | `multicantool_netherite.png` | Idem, accents sombres |
| 10 | Statio-Lytras | `statio_lytras.png` | Élytre mécanique, ailes métalliques |
| 11 | Cantalame | `cantalame.png` | Épée netherite + rune évolutive |
| 12 | Rune de Fortification | `rune_fortification.png` | Livre enchanté + symbole bouclier |
| 13 | Vie | `vie.png` | Totem avec cœur rouge, lueur or, marque "C" |

### 6.3 Item animé — Statio-Lytras (optionnel)

Pour une lueur pulsante sur les ailes :

1. Créer l'item `statio_lytras` dans Blockbench
2. **Paint → Animation → Create Animation**
3. Dessiner 6 frames (16×16 chacune) empilées verticalement → texture finale 16×96
4. Exporter en `statio_lytras.png`
5. Créer le fichier `statio_lytras.png.mcmeta` à côté :
```json
{
  "animation": {
    "frametime": 4,
    "frames": [0, 1, 2, 3, 4, 5, 4, 3, 2, 1]
  }
}
```

### 6.4 Textures GUI (optionnel)

Si tu veux customiser les interfaces :

1. **File → New → Paint Mode** (pas modèle 3D)
2. Taille : 256×256 pour `generic_54.png` (coffres 54 slots)
3. Importer la texture vanilla comme référence
4. Recolorer avec les couleurs Cantale
5. Exporter dans `assets/minecraft/textures/gui/container/`

### 6.5 Logo titre (optionnel)

1. **File → New → Paint Mode**, 256×256
2. Dessiner "CANTALE" en pixel art
3. Exporter en `assets/minecraft/textures/title/minecraft.png`

---

## 7. Sécurité Anti-Fake

### Double couche

| Couche | Côté | Mécanisme | Contre |
|--------|------|-----------|--------|
| PDC `cantale:custom_item` | Serveur | NamespacedKey | Renommage vanilla |
| PDC `cantale:auth` | Serveur | SHA-256(type + secret) | NBT editing /give |
| CustomModelData | Client | Texture uniquement | Client-side, pas d'effet |

### Flux de vérification

```
Item dans l'inventaire
  ├─ CLIENT (resource pack) :
  │    custom_model_data → modèle JSON override → texture custom
  │    Sans pack : texture vanilla (TOTEM normal, pioche normale, etc.)
  │
  └─ SERVEUR (plugin) :
       customItemManager.getCustomItemType(itemStack)
         → PDC "cantale:custom_item" existe ? Non → null
         → PDC "cantale:auth" = SHA256(type + secret) ? Non → null (falsifié)
         → Oui → return CustomItemType (authentique)
```

### Ancien système OBSOLÈTE (supprimé)

L'ancien "Vie" utilisait un **NETHER_STAR renommé** `§c§l❤ Vie` sans PDC. **Obsolète** — remplacé par :
- Matériel : `TOTEM_OF_UNDYING` + CustomModelData 25
- PDC : `cantale:custom_item` = `VIE` + `cantale:auth` = hash SHA-256
- `LifeItemListener` est `@Deprecated` et ne fait plus rien

> Si des joueurs ont d'anciens NETHER_STAR "Vie", ils ne fonctionnent plus. Remplacement admin via `/customitem vie`.

---

## 8. Configuration Serveur — Pack Obligatoire

### server.properties

```properties
resource-pack=https://cantale.fr/resources/CantalePack.zip
resource-pack-sha1=<SHA1_DU_ZIP>
require-resource-pack=true
resource-pack-prompt=§6Cantale §7requiert le resource pack pour jouer.
```

### Calculer le SHA-1

```bash
# Mac/Linux
shasum -a 1 CantalePack.zip

# Windows
certutil -hashfile CantalePack.zip SHA1
```

### Hébergement

1. Zipper le contenu de `Cantale-ResourcePack/` (pas le dossier parent)
2. Héberger sur le même serveur que le site : `https://cantale.fr/resources/CantalePack.zip`
3. Mettre à jour le SHA-1 dans `server.properties` à chaque modification du pack
4. Les joueurs reçoivent le pack automatiquement à la connexion
5. `require-resource-pack=true` : les joueurs qui refusent sont kickés

---

## 9. Spécifications des Textures Items

| Item | Taille | Style | Description |
|------|--------|-------|-------------|
| Pickantaxe Fer | 16×16 | Pixel art | Pioche fer + lueur rouge, marquage zone sur la tête |
| Pickantaxe Diamant | 16×16 | Pixel art | Pioche diamant + lueur rouge cyan |
| Pickantaxe Netherite | 16×16 | Pixel art | Pioche netherite + lueur rouge sombre |
| Cantaxe Fer | 16×16 | Pixel art | Hache fer + motif tronc sur le manche |
| Cantaxe Diamant | 16×16 | Pixel art | Hache diamant + lueur cyan |
| Cantaxe Netherite | 16×16 | Pixel art | Hache netherite + lueur sombre |
| Multi-Cantool Fer | 16×16 | Pixel art | Cisailles + 4 têtes d'outils (pioche/hache/pelle/cisailles) |
| Multi-Cantool Diamant | 16×16 | Pixel art | Idem, accents cyan |
| Multi-Cantool Netherite | 16×16 | Pixel art | Idem, accents sombres |
| Statio-Lytras | 16×16 | Animé | Élytre + ailes mécaniques, lueur rouge pulsante |
| Cantalame | 16×16 | Pixel art | Épée netherite + rune évolutive |
| Rune de Fortification | 16×16 | Pixel art | Livre enchanté + symbole bouclier |
| Vie | 16×16 | Pixel art | Totem modifié : cœur rouge, lueur or, marque "C" |

Palette Cantale : rouge principal `#B01020`, or `#F0B84A`, fond sombre `#1A1A1A`

---

## 10. Checklist de Création

- [ ] Créer `pack.mcmeta` (format 75)
- [ ] Créer `pack.png` (logo 64×64)
- [ ] Dessiner les 14 textures d'items dans Blockbench
- [ ] Créer les 25 modèles JSON dans `assets/cantale/models/item/`
- [ ] Créer les 11 modèles de base avec overrides dans `assets/minecraft/models/item/`
- [ ] Dessiner `generic_54.png` (UI 54 slots)
- [ ] Dessiner `enderchest.png` (UI ender chest)
- [ ] Dessiner `minecraft.png` + `edition.png` (écran titre)
- [ ] Ajouter le CMD `7001` pour VIE dans `assets/minecraft/items/totem_of_undying.json`
- [ ] Vérifier que `CustomItemManager.getCustomModelData()` retourne 25 pour VIE
- [ ] Zipper le pack
- [ ] Calculer SHA-1 du zip
- [ ] Configurer `server.properties` avec `require-resource-pack=true`
- [ ] Tester en jeu : chaque item custom affiche sa texture
- [ ] Tester sans le pack : les items fonctionnent mais ont le look vanilla
- [ ] Vérifier que les anciens NETHER_STAR "Vie" ne fonctionnent plus
