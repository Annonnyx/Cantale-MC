# Plan du module AntiCheat

## Objectif
Créer un système modulaire d’anti-cheat pour Cantale, capable de détecter les comportements suspects sans bloquer le serveur ni nécessiter une réécriture complète du plugin.

## Philosophie
- Vérifications par catégories : Movement, Combat, Packets, Inventory, PlayerData, Alerts.
- Score de confiance par joueur : chaque détection réduit un score, les joueurs sains le récupèrent lentement.
- Priorité aux détections de mouvement et de combat, qui sont les plus fiables et les plus utiles sur un serveur PvP.
- Implémentation progressive : commencer par un MVP puis étendre.

## 1. Movement Check
Objectif : détecter speed, fly, nofall, jesus, spider, step, timer.

### Données disponibles
- position du joueur
- vitesse
- effets (Speed, Slowness)
- état dans l’eau
- statut en vol/elytres
- blocs autour du joueur

### Vérifications prioritaires
- Speed : comparer distance parcourue par tick à la vitesse maximale autorisée.
- Fly : détecter les déplacements impossibles en l’air sans saut ou sans mouvement attendu.
- NoFall : repérer les chutes avec dégâts annulés ou trop faibles pour la hauteur.
- Step : détecter un franchissement anormal de blocs.
- Jesus : marcher sur l’eau sans vraie physique.
- Timer : variation de tick trop rapide par rapport au comportement humain.

### Implémentation conseillée
- Stocker un historique court par joueur (10 à 20 ticks).
- Calculer des métriques sur chaque PlayerMoveEvent.
- Seuils adaptatifs selon le mode de jeu et le biome.

## 2. Combat Check
Objectif : détecter reach, killauras, autoclicker, aim assist.

### Données à enregistrer
- CPS
- précision du mouvement de souris
- angle de rotation
- distance d’attaque
- délai entre coups
- ordre des paquets et timings

### Vérifications prioritaires
- Reach : signaler les distances d’attaque impossibles.
- KillAura : détecter les attaques sur plusieurs cibles très rapidement ou des rotations trop parfaites.
- AutoClicker : chercher une régularité anormale des clics.
- Aim : repérer des rotations trop stables ou trop rapides.

## 3. Rotation Check
Objectif : identifier les mouvements de caméra trop parfaits ou trop réguliers.

### Signaux à surveiller
- rotations multiples à 45°, 90°, 135°, 180°
- valeurs flottantes parfaitement régulières
- changements de pitch/yaw trop stables

## 4. Packet Check
Objectif : analyser les paquets si ProtocolLib est ajouté plus tard.

### Détections envisagées
- Blink
- Timer abuse
- Packet flood
- Bad packets
- Paquets impossibles

## 5. Inventory Check
Objectif : détecter les automatisations d’inventaire.

### Détections envisagées
- AutoArmor
- InventoryMove
- ChestStealer
- FastEat
- FastPlace

## 6. PlayerData et confiance
### Structure proposée
- ViolationManager : centralise les violations et les seuils.
- BufferManager : stocke l’historique court et les fenêtres de détection.
- TrustManager : calcule un score de confiance par joueur.

### Règles de base
- Trust = 100 au départ.
- Chaque violation retire entre 2 et 8 points.
- Si le joueur est calme, le score remonte lentement.
- Plus le score est bas, plus les vérifications deviennent strictes.

## 7. Alerts et sanctions
- Alerte staff via logs et messages Discord si nécessaire.
- Sanctions graduelles : avertissement, délai, mute, ban temporaire.
- Journaliser chaque violation avec joueur, type, preuve et seuil.

## 8. Structure de code proposée
AntiCheat/
├── Movement/
│   ├── SpeedCheck
│   ├── FlyCheck
│   ├── NoFallCheck
│   ├── StepCheck
│   └── TimerCheck
├── Combat/
│   ├── ReachCheck
│   ├── KillAuraCheck
│   ├── AutoClickerCheck
│   └── AimCheck
├── Packets/
│   ├── BlinkCheck
│   ├── BadPacketsCheck
│   └── TimerPacketsCheck
├── Inventory/
│   ├── ChestStealerCheck
│   ├── AutoArmorCheck
│   └── FastUseCheck
├── PlayerData/
│   ├── ViolationManager
│   ├── BufferManager
│   └── TrustManager
└── Alerts/
    ├── StaffAlert
    ├── Logs
    └── Punishments

## 9. Plan de livraison MVP
1. Ajouter la structure de packages et les managers de base.
2. Implémenter SpeedCheck et FlyCheck.
3. Ajouter le système de score de confiance.
4. Connecter les alertes staff et la journalisation.
5. Étendre ensuite aux checks de combat et d’inventaire.
