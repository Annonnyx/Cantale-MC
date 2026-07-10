# Cantale Resource Pack — Instructions

## ✅ État du pack

**Les fichiers JSON sont tous créés et configurés.** Tu n'as plus qu'à créer les textures PNG.

## 📁 Structure

```
Cantale-ResourcePack/
├── pack.mcmeta ✓
├── assets/minecraft/models/item/ ✓ (11 override files)
├── assets/cantale/models/item/ ✓ (25 custom models)
└── assets/cantale/textures/item/ ← [À CRÉER] 14 PNG files
```

## 🎨 À faire dans Blockbench

### 1. Créer les 13 textures d'items (16×16 pixels)

Exporter en PNG dans `assets/cantale/textures/item/` :

| Fichier | Description |
|---------|-------------|
| `pickantaxe_iron.png` | Pioche fer avec zone rouge |
| `pickantaxe_diamond.png` | Pioche diamant, lueur cyan/rouge |
| `pickantaxe_netherite.png` | Pioche netherite sombre |
| `cantaxe_iron.png` | Hache fer, motif tronc |
| `cantaxe_diamond.png` | Hache diamant, accents cyan |
| `cantaxe_netherite.png` | Hache netherite |
| `multicantool_iron.png` | Cisailles multi-outils fer |
| `multicantool_diamond.png` | Cisailles multi-outils diamant |
| `multicantool_netherite.png` | Cisailles multi-outils netherite |
| `statio_lytras.png` | Élytre mécanique (animable) |
| `cantalame.png` | Épée évolutive |
| `rune_fortification.png` | Livre enchanté + bouclier |
| `vie.png` | Totem avec cœur rouge |

### 2. Workflow Blockbench

1. **File → New → Minecraft Item**
   - Nom : ex `pickantaxe_iron`
   - Texture size : 16×16
   - Cocher "Create Texture"

2. **Dessiner** (onglet Paint)
   - Palette : View → Palette → Minecraft
   - Style : pixel art comme les items vanilla
   - Couleurs Cantale : rouge `#B01020`, or `#F0B84A`

3. **Exporter uniquement la texture**
   - File → Export → Export Texture
   - Destination : `assets/cantale/textures/item/`
   - **Tu n'as PAS besoin d'exporter le JSON** (déjà créé)

### 3. Tester le pack

1. Zipper le dossier `Cantale-ResourcePack/` (le contenu, pas le dossier lui-même)
2. Mettre dans `.minecraft/resourcepacks/`
3. Activer dans les options Minecraft
4. Rejoindre le serveur Cantale
5. Vérifier que les items custom ont les bonnes textures

## 🚀 Déploiement serveur

1. Héberger le zip sur le site : `https://cantale.fr/resources/CantalePack.zip`
2. Calculer le SHA-1 : `shasum -a 1 CantalePack.zip`
3. Configurer `server.properties` :
```properties
resource-pack=https://cantale.fr/resources/CantalePack.zip
resource-pack-sha1=<SHA1>
require-resource-pack=true
```

## 📖 Documentation complète

Voir `RESOURCE_PACK_GUIDE.md` à la racine du projet pour :
- Table des CustomModelData (1-25)
- Architecture détaillée
- Sécurité anti-fake
- Instructions GUI
