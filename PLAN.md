# Plan de Développement - Plugin Cantale (Minecraft 1.20.4)

## Architecture Technique

### API & Dépendances
- **Paper API 1.20.4** - Base du plugin
- **ProtocolLib** - Pour les UI custom (maps interactives)
- **Vault** - Intégration économie
- **HikariCP** - Pool de connexions MySQL/SQLite
- **LuckPerms API** - Pour les permissions avancées (optionnel)

### Structure des Packages
```
fr.cantale.plugin/
├── CantalePlugin.java              # Point d'entrée
├── config/                         # Gestion configuration
├── database/                       # Couche persistence
│   ├── DatabaseManager.java
│   ├── FactionDAO.java
│   ├── PlayerDAO.java
│   └── EconomyDAO.java
├── factions/                       # Système de factions
│   ├── Faction.java
│   ├── FactionManager.java
│   ├── FactionRank.java
│   ├── Claim.java
│   └── permissions/
├── lives/                          # Système de vies Hardcore
│   ├── LifeManager.java
│   └── LifeTransaction.java
├── economy/                        # Économie Cantox
│   ├── EconomyManager.java
│   ├── BankManager.java
│   └── AuctionHouse.java
├── items/                          # Items custom
│   ├── CustomItem.java
│   ├── ItemManager.java
│   ├── Pickantaxe.java
│   ├── Cantaxe.java
│   ├── MultiCantool.java
│   └── StatioLytra.java
├── ui/                             # UI Custom (MAPS)
│   ├── UIManager.java
│   ├── MapRenderer.java
│   ├── FactionUI.java
│   ├── AuctionUI.java
│   ├── TradeUI.java
│   └── ProfileUI.java
├── teleport/                       # Téléportation
│   ├── HomeManager.java
│   ├── WarpManager.java
│   └── SpawnManager.java
├── moderation/                     # Modération
│   ├── ModerationManager.java
│   ├── VanishManager.java
│   └── LogManager.java
├── wanted/                         # Système de primes
│   ├── WantedManager.java
│   └── WantedBoard.java
└── utils/                          # Utilitaires
    ├── ChunkUtils.java
    ├── ItemBuilder.java
    └── MessageUtils.java
```

---

## Phase 1 : Fondations (Semaine 1-2)

### 1.1 Structure du Projet
- [ ] Setup Gradle/Maven avec Paper API 1.20.4
- [ ] Configuration plugin.yml
- [ ] Système de configuration YAML
- [ ] Connexion base de données (SQLite pour dev, MySQL pour prod)

### 1.2 Système de Vies (Hardcore)
**Règle :** 3 vies au départ, 0 vie = ban IP

- [ ] Table `players` : uuid, username, lives (default: 3), deaths, last_death
- [ ] Commandes : `/dropvie`, `/givevie [joueur]`
- [ ] Listener : onPlayerDeath → décrémenter vies → ban si 0 vies
- [ ] Système de transaction de vies entre joueurs
- [ ] Prix de rachat d'une vie (Cantox ou boutique)
- [ ] Gains de vies : events, concours, achat boutique, don d'ami

---

## Phase 2 : Factions

### 2.1 Modèle de Données
- [ ] Table `factions` : id, name, tag, description, leader_uuid, created_at, balance, power
- [ ] Table `faction_members` : faction_id, player_uuid, rank, joined_at
- [ ] Table `claims` : id, faction_id, world, x, z, chunk_x, chunk_z
- [ ] Table `faction_warps` : id, faction_id, name, world, x, y, z
- [ ] Table `faction_homes` : id, faction_id, player_uuid, name, world, x, y, z
- [ ] Table `faction_permissions` : faction_id, rank, permission, value

### 2.2 Core Factions
- [ ] Création faction : minimum 8 joueurs, 60 chunks de départ
- [ ] Grades : Recrue → Membre → Officier → Chef
- [ ] Invitation `/f invite`, Acceptation `/f accept`
- [ ] Système de claims (chunk by chunk)
- [ ] Système de power = chunks claimables
- [ ] `/f claim auto` pour claim automatiquement en marchant

### 2.3 PASDIC (Claims protégés)
- [ ] Table `pasdic_claims` : claim_id, approved_by, approved_at
- [ ] Mode Adventure forcé dans les PASDIC
- [ ] Vote du conseil pour approbation
- [ ] Protection totale (no grief, no PvP, no storage)

### 2.4 Pays & Empires
- [ ] Promotion automatique à 50+ membres = Pays
- [ ] Promotion à 100+ membres = Empire
- [ ] Alliance d'Empires (150+ membres total)
- [ ] Révolution possible uniquement dans les Empires

### 2.5 Commandes Factions
```
/f create [nom]          - Créer (min 8 joueurs)
/f invite [pseudo]       - Inviter
/f accept [pseudo]       - Accepter une invitation
/f kick [pseudo]         - Expulser
/f ban [pseudo]          - Bannir
/f claim [auto]          - Claim un chunk
/f unclaim [auto]        - Unclaim
/f sethome [nom]         - Set un home de faction
/f home [nom]            - TP au home
/f setwarp [nom]         - Set un warp (officier+)
/f warp [nom]            - TP au warp
/f bank add/take [nb]    - Gérer la banque
/f upgrade/downgrade [p] - Changer le grade
/f role [grade] add/remove [p] - Gérer les rôles
/f perms [grade] set [perm] [val] - Permissions
/f map                   - Voir la carte des claims
/f info [nom]            - Infos sur une faction
/f leave                 - Quitter
/f give [pseudo]         - Transférer le lead
/f settings             - UI paramètres faction
/f admin                - UI admin faction (chef/officier)
/f list                 - Liste des factions
/f join [nom]           - Rejoindre une faction (si invitation ouverte)
/f help                 - Aide des commandes
```

---

## Phase 3 : UI Custom

### 3.1 Technologie : Map-based UI
Utiliser les **Item Maps** avec ProtocolLib pour créer de vraies interfaces:
- Canvas pixel-perfect
- Boutons cliquables avec hit detection
- Navigation entre pages
- Pas de limitation d'inventaire

### 3.2 UI Faction (`/f`)
**Page d'accueil :**
- Nom, tag, description
- Nombre de membres (max et actuel)
- Nombre de claims (utilisés/total)
- Points de puissance
- Argent en banque

**Navigation :**
- Bouton "Banque" → détail transactions
- Bouton "Warps" → liste avec TP
- Bouton "Claims" → carte visualisation
- Bouton "Permissions" → panel config (officier only)
- Bouton "Membres" → liste avec grades

### 3.3 UI Auction House (`/ah`)
- Grille d'items en vente
- Filtres par catégorie
- Historique des prix (graphiques)
- Panel de stats (ventes, moyennes)
- Déposer items avec prix

### 3.4 UI Trade (`/trade`)
- Deux zones d'échange
- Zone pour argent Cantox
- Bouton confirmer
- Sécurité anti-scam (verrou après confirmation)

### 3.5 UI Profile (`/profile`)
- Monnaie actuelle
- Nombre de vies restantes
- Nombre de roses
- Stats de jeu
- Liste des homes personnels

---

## Phase 4 : Économie

### 4.1 Cantox
- [ ] Solde joueur en base de données
- [ ] **Départ : 10k Cantox** pour chaque nouveau joueur
- [ ] Commandes `/pay`, `/balance`
- [ ] Intégration Vault

### 4.2 Banque de Faction
- [ ] Dépôt/retrait avec permissions
- [ ] Historique des transactions (qui, quoi, quand)
- [ ] Visualisation selon permissions

### 4.3 Hôtel des Ventes

**Commande :** `/ah sell [quantité|all] [prix]`
- Item concerné = celui dans la main au moment de la commande
- Quantité max = total présent dans l'inventaire (y compris les shulkers)
- `all` = vide l'inventaire complet de cet item (shulkers comprises)
- Shulkers vidées proportionnellement si quantité partielle

**Limites :**
- Max 3 types d'items en vente simultanément par joueur (peu importe la quantité)
- Exemple autorisé : 64 blé + 32 fer + 128 or
- Exemple refusé : blé + fer + or + diamant (4 types)

**Fonctionnalités :**
- [ ] Déposer items avec quantité et prix
- [ ] Scan automatique des shulkers pour calcul du stock total
- [ ] Vidage intelligent (inventaire puis shulkers)
- [ ] Commission serveur (5-10%)
- [ ] Récupération items non-vendus
- [ ] Notification vente réussie
- [ ] `/ah claim` - Récupérer argent/items
- [ ] Historique des ventes pour stats de prix

### 4.4 Trading
- [ ] UI sécurisé avec confirmation
- [ ] Échange items + argent
- [ ] Protection anti-scam

---

## Phase 5 : Items Custom

### 5.1 Pickantaxe (Pioche multifonction)
| Variante | Zone | Enchantements |
|----------|------|---------------|
| Fer | 1x1 | Unbreaking 5, Efficiency 5 |
| Fer | 3x3 | Unbreaking 5, Efficiency 5 |
| Fer | 5x5 | Unbreaking 5, Efficiency 5 |
| Fer | 7x7 | Unbreaking 5, Efficiency 5 |
| Diamant | 1x1 | Unbreaking 7, Efficiency 7 |
| Diamant | 3x3 | Unbreaking 7, Efficiency 7 |
| Diamant | 5x5 | Unbreaking 7, Efficiency 7 |
| Diamant | 7x7 | Unbreaking 7, Efficiency 7 |
| Netherite | 1x1 | Unbreaking X (infini), Efficiency 10 |
| Netherite | 3x3 | Unbreaking X (infini), Efficiency 10 |
| Netherite | 5x5 | Unbreaking X (infini), Efficiency 10 |
| Netherite | 7x7 | Unbreaking X (infini), Efficiency 10 |

**Mécanique de minage :**
- Minage au niveau du joueur
- 5x5 = 3 au-dessus, 2 en dessous (centre inclus)
- 7x7 = 5 au-dessus, 2 en dessous
- Non-enchantable (stats fixes)
- Non-craftable (rare, events/staff)

### 5.2 Cantaxe (Hache universelle)
- One-shot tous les arbres (logs tombent direct)
- Mine comme une efficacité max : stone, cobble, etc.
- Enchantable (Silk Touch, Unbreaking)
- Versions Fer/Diamant/Netherite avec mêmes stats que Pickantaxe

### 5.3 Multi-Cantool
- **Craft :** Pickantaxe + Cantaxe + Pelle (même matériau)
- Les 3 outils doivent être **neufs** (unused)
- Adaptation automatique au block visé
- Apparence ciseaux pour feuilles/laine
- Hérite des stats des composants

### 5.4 Statio-Lytras
- Elytra avec vol créatif (pas de boost nécessaire)
- **Restriction :** Uniquement dans les claims de sa faction
- Même durabilité/enchantements qu'une elytra normale
- Non-craftable (events/staff)

### 5.5 Extension Hauteur
- Modifier la limite de build à 512 blocs (vanilla = 320)
- Gestion des chunks extended

---

## Phase 6 : Téléportation 

### 6.1 Homes
- `/home [nom]` - TP à un home personnel
- Limite 3 homes de base (hors faction)
- Homes de faction selon power/permissions

### 6.2 Warps
- `/warp [nom]` - TP à un warp public
- Warps d'événements (activables/désactivables)
- Warps de faction (selon power)

### 6.3 Autres
- `/spawn` - Spawn serveur
- `/tpa [joueur]` - Demande de TP
- `/tpaaccept [joueur]` - Accepter une demande de TP
- `/tpahere [joueur]` - Demander à un joueur de se TP à soi
- `/rtp` - Random TP (1x/jour)
- `/f warp [nom]` - Warp faction
- `/f home [nom]` - Home faction
- `/pc` - Coffre privé
- `/cantale` - Infos serveur
- 

**Règle : PvP désactivé dans tous les warps puliques**

---

## Phase 7 : Modération

### 7.1 Commandes Basiques
- `/ban [pseudo] [motif]`
- `/banip [ip] [motif]`
- `/kick [pseudo]`
- `/mute [pseudo] [temps]`
- `/unban [pseudo]`
- `/unmute [pseudo]`
- `/tp [pseudo]` - Téléportation simple
- `/tphere [pseudo]` - Téléporter un joueur à soi

### 7.2 Mode Modération (`/moderation`)
**Activation :**
- Clear inventaire modérateur
- Gamemode Survival forcé
- Trace activée : blocks cassés, commandes, items
- Log toutes les 10 secondes

**Désactivation :**
- TP à la position d'origine
- Restoration inventaire
- Arrêt des logs

### 7.3 Vanish
- `/vanish` - Invisible avec effets
- Caché des commandes/tab/liste

### 7.4 Protection Modérateur
- `/kill` par un modo → ban automatique du modo
- Justification requise aux admins
- Détection auto pour review

### 7.5 Warp Event
- `/setwarp [nom] [radius]` - Radius en chunks
- `/warp event [nom] set true/false` - Activer/désactiver

---

## Phase 8 : Features Additionnelles

### 8.1 Système Wanted
- `/wanted add [pseudo] [prix]` - Mettre une prime
- `/wantedpanel add [x] [y] [z]` - Ajouter un panneau d'affichage (admin)
- Affiches dans tous les warps (NPC ou ArmorStands cliquables)
- Click droit = accepter la chasse
- Récompense automatique au kill
- Tête du joueur dans `/ec` (enderchest) ou `/pc`

### 8.2 Private Chest (`/pc`)
- Coffre privé virtuel
- Stockage sécurisé
- Accès uniquement au propriétaire
- Items non perdus a la mort du joueur

### 8.3 Commandes Info
- `/cantale` - Infos serveur, commandes utiles, TP spawn, events, warps publics

### 8.4 Annuaire Modérateur
- Commandes pour répertorier : builds, guerres
- Génération mensuelle

---

## Phase 9 : Permissions

### 9.1 Permissions Faction
Toutes les permissions sont sous forme `Cantale.[permission]`

| Permission | Description | Valeur |
|------------|-------------|--------|
| `Cantale.addclaims` | Ajouter des claims | true/false |
| `Cantale.removeclaims` | Retirer des claims | true/false |
| `Cantale.setperms` | Modifier permissions | true/false |
| `Cantale.faccept` | Accepter membres | true/false |
| `Cantale.fkick` | Expulser membres | true/false |
| `Cantale.ban` | Bannir membres | true/false |
| `Cantale.unban` | Débannir membres | true/false |
| `Cantale.bankadd` | Ajouter à la banque | true/false |
| `Cantale.banktake` | Retirer de la banque | true/false |
| `Cantale.cultures` | Détruire cultures | true/false |
| `Cantale.fhome` | Nombre de homes perso | nombre |
| `Cantale.fwarp` | Modifier warps | true/false |
| `Cantale.infobank` | Voir détail banque | true/false |

**Version admin :** `admin.[permission]` pour bypass

---

## Phase 10 : Polish & Déploiement

### 10.1 Optimisation
- [ ] Cache mémoire pour claims
- [ ] Cache pour factions
- [ ] Async database operations
- [ ] Lazy loading des données joueurs

### 10.2 Tests
- [ ] Tests unitaires core
- [ ] Tests d'intégration factions
- [ ] Stress test (100+ joueurs simultanés)

### 10.3 Documentation
- [ ] Wiki joueur
- [ ] Documentation admin
- [ ] Guide modération

### 10.4 Monétisation
- [ ] Intégration boutique (vies, Cantox, grades)
- [ ] Grades avec abonnement mensuel
- [ ] Avantages grades (à définir)

---

## Dépendances Externes Requises

| Plugin | Usage |
|--------|-------|
| Vault | API Économie |
| ProtocolLib | UI Maps, packets |
| LuckPerms | Permissions avancées (optionnel) |
| PlaceholderAPI | Placeholders (optionnel) |

---

## Technologies UI Alternatives à Considérer

1. **Maps (recommandé)** : Vrai rendu 2D, fluide, natif
2. **Books** : Interface texte avec boutons
3. **Signs** : Input utilisateur
4. **Chat interactif** : Composants click/hover

---

## Notes Importantes

### Performance
- Claims : utiliser une HashMap<ChunkCoord, Faction>
- Mise à jour UI : rate limiting (max 20Hz)
- Database : requêtes async uniquement

### Sécurité
- Toutes les commandes vérifient les permissions
- Validation des inputs (SQL injection, etc.)
- Anti-duplication d'items custom (NBT tags uniques)

### Compatibilité
- Paper 1.20.4+ uniquement
- Java 17+
- Pas de support Spigot/CraftBukkit

---

*Dernière mise à jour : Avril 2026*
