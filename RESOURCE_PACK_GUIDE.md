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
├── assets/minecraft/models/item/ ✓ (11 fichiers override)
├── assets/cantale/models/item/ ✓ (25 fichiers custom)
└── [à créer] assets/cantale/textures/item/ (14 PNG)
```

---

## 1. Architecture

```
Cantale-ResourcePack/
├── pack.mcmeta                    # format 15 (1.20.x)
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
    "pack_format": 15,
    "description": "§6Cantale §7— Items Custom & UI"
  }
}
```

## 3. Table des CustomModelData

| ID  | Item                          | Matériel de base       | Modèle cantale                     |
|-----|-------------------------------|------------------------|------------------------------------|
| 1   | Pickantaxe Fer 1×1            | `IRON_PICKAXE`         | `pickantaxe_iron_1x1.json`        |
| 2   | Pickantaxe Fer 3×3            | `IRON_PICKAXE`         | `pickantaxe_iron_3x3.json`        |
| 3   | Pickantaxe Fer 5×5            | `IRON_PICKAXE`         | `pickantaxe_iron_5x5.json`        |
| 4   | Pickantaxe Fer 7×7            | `IRON_PICKAXE`         | `pickantaxe_iron_7x7.json`        |
| 5   | Pickantaxe Diamant 1×1        | `DIAMOND_PICKAXE`      | `pickantaxe_diamond_1x1.json`     |
| 6   | Pickantaxe Diamant 3×3        | `DIAMOND_PICKAXE`      | `pickantaxe_diamond_3x3.json`     |
| 7   | Pickantaxe Diamant 5×5        | `DIAMOND_PICKAXE`      | `pickantaxe_diamond_5x5.json`     |
| 8   | Pickantaxe Diamant 7×7        | `DIAMOND_PICKAXE`      | `pickantaxe_diamond_7x7.json`     |
| 9   | Pickantaxe Netherite 1×1      | `NETHERITE_PICKAXE`    | `pickantaxe_netherite_1x1.json`   |
| 10  | Pickantaxe Netherite 3×3      | `NETHERITE_PICKAXE`    | `pickantaxe_netherite_3x3.json`   |
| 11  | Pickantaxe Netherite 5×5      | `NETHERITE_PICKAXE`    | `pickantaxe_netherite_5x5.json`   |
| 12  | Pickantaxe Netherite 7×7      | `NETHERITE_PICKAXE`    | `pickantaxe_netherite_7x7.json`   |
| 13  | Cantaxe Fer                   | `IRON_AXE`             | `cantaxe_iron.json`               |
| 14  | Cantaxe Diamant               | `DIAMOND_AXE`          | `cantaxe_diamond.json`            |
| 15  | Cantaxe Netherite             | `NETHERITE_AXE`        | `cantaxe_netherite.json`          |
| 16  | Multi-Cantool Fer 1×1         | `SHEARS`              | `multicantool_iron_1x1.json`      |
| 17  | Multi-Cantool Fer             | `SHEARS`              | `multicantool_iron.json`          |
| 18  | Multi-Cantool Diamant 1×1     | `SHEARS`              | `multicantool_diamond_1x1.json`   |
| 19  | Multi-Cantool Diamant         | `SHEARS`              | `multicantool_diamond.json`       |
| 20  | Multi-Cantool Netherite 1×1   | `SHEARS`              | `multicantool_netherite_1x1.json` |
| 21  | Multi-Cantool Netherite       | `SHEARS`              | `multicantool_netherite.json`     |
| 22  | Statio-Lytras                 | `ELYTRA`              | `statio_lytras.json`              |
| 23  | Cantalame                     | `NETHERITE_SWORD`      | `cantalame.json`                  |
| 24  | Rune de Fortification         | `ENCHANTED_BOOK`       | `rune_fortification.json`         |
| 25  | Vie                           | `TOTEM_OF_UNDYING`     | `vie.json`                        |

## 4. Modèles JSON — Exemples

### Modèle de base avec overrides (`iron_pickaxe.json`)

```json
{
  "parent": "minecraft:item/handheld",
  "textures": { "layer0": "minecraft:item/iron_pickaxe" },
  "overrides": [
    { "predicate": { "custom_model_data": 1 }, "model": "cantale:item/pickantaxe_iron_1x1" },
    { "predicate": { "custom_model_data": 2 }, "model": "cantale:item/pickantaxe_iron_3x3" },
    { "predicate": { "custom_model_data": 3 }, "model": "cantale:item/pickantaxe_iron_5x5" },
    { "predicate": { "custom_model_data": 4 }, "model": "cantale:item/pickantaxe_iron_7x7" }
  ]
}
```

### Modèle de base — Vie (`totem_of_undying.json`)

```json
{
  "parent": "minecraft:item/generated",
  "textures": { "layer0": "minecraft:item/totem_of_undying" },
  "overrides": [
    { "predicate": { "custom_model_data": 25 }, "model": "cantale:item/vie" }
  ]
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

- [ ] Créer `pack.mcmeta` (format 15)
- [ ] Créer `pack.png` (logo 64×64)
- [ ] Dessiner les 14 textures d'items dans Blockbench
- [ ] Créer les 25 modèles JSON dans `assets/cantale/models/item/`
- [ ] Créer les 11 modèles de base avec overrides dans `assets/minecraft/models/item/`
- [ ] Dessiner `generic_54.png` (UI 54 slots)
- [ ] Dessiner `enderchest.png` (UI ender chest)
- [ ] Dessiner `minecraft.png` + `edition.png` (écran titre)
- [ ] Ajouter `custom_model_data: 25` pour VIE dans `totem_of_undying.json`
- [ ] Vérifier que `CustomItemManager.getCustomModelData()` retourne 25 pour VIE
- [ ] Zipper le pack
- [ ] Calculer SHA-1 du zip
- [ ] Configurer `server.properties` avec `require-resource-pack=true`
- [ ] Tester en jeu : chaque item custom affiche sa texture
- [ ] Tester sans le pack : les items fonctionnent mais ont le look vanilla
- [ ] Vérifier que les anciens NETHER_STAR "Vie" ne fonctionnent plus
