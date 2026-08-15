# Setup des caisses Cantale

Guide complet pour faire fonctionner les 5 types de caisses (items + interactions + textures).

---

## 1. Les 5 caisses

| Type | Item | CMD | Interaction |
|------|------|-----|-------------|
| **VOTE** | Cadeau du Roi (paper) | 100 | Mailbox : tonneau / métier à tisser |
| **RARE** | Trésor Public (tripwire hook) | 101 | Coffre réel configuré au spawn |
| **EPIC** | Médaille du Tournoi (sunflower) | 102 | Autel : beacon / table d'enchantement |
| **MYTHIC** | Pièce Mythique (gold nugget) | 103 | Fontaine : jeter (Q) dans l'eau d'une zone cubique |
| **LEGENDARY** | Ticket Légendaire (writable book) | 104 | Ticket Discord (automatique) |

---

## 2. `crate.yml` — configurer les emplacements

Fichier : `plugins/CantalePlugin/crate.yml` (créé au premier démarrage).

```yaml
# Emplacements d'interaction. Si une liste est vide → comportement legacy
# (n'importe quel bloc du bon type / n'importe quelle eau).

# RARE : coffre(s) au spawn
rare-chests:
  - world: "world"
    x: 100
    y: 64
    z: 200

# VOTE : mailbox (tonneau ou loom)
vote-mailboxes:
  - world: "world"
    x: 105
    y: 64
    z: 205

# EPIC : autel (beacon ou enchanting table)
epic-altars:
  - world: "world"
    x: 110
    y: 64
    z: 210

# MYTHIC : fontaine = zone cubique d'eau (2 coins)
mythic-fountains:
  - world: "world"
    corner1: { x: 120, y: 60, z: 220 }
    corner2: { x: 126, y: 66, z: 226 }
```

### Comment trouver les coordonnées

En jeu, regarde le bloc et appuie sur **F3** : les coordonnées `Block: x y z` sont affichées.  
Pour la fontaine mythique, note les **2 coins opposés** de la zone d'eau.

### Recharger

```
/crate reload
```

---

## 3. Textures (resource pack)

Les items ont des placeholders vanilla tant que tu n'as pas fait les PNG.

### À faire dans Blockbench

| Fichier | CMD | Style |
|---------|-----|-------|
| `crate_vote.png` | 100 | Cadeau / sceau royal |
| `crate_rare.png` | 101 | Clef / coffre public (bleu) |
| `crate_epic.png` | 102 | Médaille pourpre |
| `crate_mythic.png` | 103 | Pièce rose / mythique |
| `crate_legendary.png` | 104 | Ticket or / parchemin |

Chemin : `Cantale-ResourcePack-full/assets/cantale/textures/item/`

Après export, dans chaque `assets/cantale/models/item/crate_*.json`, remplacer :
```json
"layer0": "minecraft:item/paper"
```
par :
```json
"layer0": "cantale:item/crate_vote"
```
(idem pour les 4 autres)

### Rebuild + upload

```bash
cd Cantale-ResourcePack-full
zip -r ../cantale-resourcepack.zip .
shasum -a 1 ../cantale-resourcepack.zip
```

Puis push le zip sur le repo `Annonnyx/Cantale-resourcepack` (branche `main`) et mets le nouveau SHA dans `server.properties`.

---

## 4. Donner une caisse en jeu

```
/crate give <joueur> <type> [quantité]
```

Types : `vote`, `rare`, `epic`, `mythic`, `legendary`

Les caisses Rare / Épique / Mythique / Légendaire peuvent aussi donner un **spawner Cantale** (chances config `spawners.crate.*` : 5 % / 3 % / 1,5 % / 1,5 %). Pas sur Vote.

Exemple :
```
/crate give Anox26 rare 1
```

---

## 5. Vérifications rapides

| Test | Attendu |
|------|---------|
| `/crate give` + clic droit sur mailbox/coffre/autel/fontaine | Animation + récompense |
| `/crate reload` | Config rechargée sans erreur |
| Item dans la main | Texture custom si pack actif |
| Fontaine mythique | Jeter la pièce (Q) dans l'eau de la zone → récompense |

---

## 6. Problèmes fréquents

| Problème | Cause | Fix |
|----------|-------|-----|
| « Ce n'est pas le bon endroit » | Liste vide dans `crate.yml` ou mauvaises coords | Remplir la section correspondante |
| Texture vanilla | PNG pas exporté / `layer0` pas changé | Voir §3 |
| Fontaine ne marche pas | Zone cubique mal définie | Vérifier corner1/corner2 (F3) |
| `/crate reload` erreur | YAML mal formé | Vérifier indentation (2 espaces) |

---

*Voir aussi : `docs/BlockbenchArtGuide.md` pour toutes les textures à produire.*
