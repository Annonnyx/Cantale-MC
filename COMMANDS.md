Cantale - Commandes (extrait de plugin.yml)

Format : /commande — usage — permission (si présente)

- /cantale — Affiche les informations du serveur et les commandes utiles
- /admin — Liste toutes les commandes admin et modérateur — permission : cantale.admin
  - /admin f rename <faction> <nouveau_nom> — Renommer une faction (+ tag / Discord)
- /dropvie — Sacrifier une vie
- /givevie <joueur> — Donner une vie — permission : cantale.givevie
- /addlife <joueur> <nombre> — Ajouter des vies (admin) — permission : cantale.admin
- /removelife <joueur> <nombre> — Retirer des vies (admin) — permission : cantale.admin
- /f (alias /faction) — Commandes de faction
  - /f create <nom> — Créer une faction (donne 3 jours de secret gratuit)
  - /f rename <nouveau_nom> — Renommer (chef seulement ; alias /f name, /f renommer)
  - /f claim / /f unclaim / /f autoclaim — Gestion des claims
  - /f map — Carte interactive des claims. Shift + clic gauche sur un claim de sa faction : démarrer la conversion PASDIC.
  - /f pasdic [confirm] (alias : /f protect) — Convertir le claim actuel en PASDIC ou le retirer. Nécessite confirmation et paiement via la banque de faction (Officier+).
  - /f raid — Déclarer un raid sur le claim ennemi actuel (Officier+ ; la guerre n'est PAS requise). Durée/cooldown/protection selon le pouvoir de la défenseur.
  - /f war <faction> (alias /f enemy) — Déclarer la guerre (Officier+). Mutuel : la 1re déclaration reste « en attente » (15 min par défaut) et la guerre ne démarre que si l'autre faction déclare aussi. Le démarrage rompt l'alliance existante.
  - /f peace <faction> — Proposer la paix (Officier+). Mutuel : la guerre ne prend fin que si l'autre faction propose aussi la paix.
  - /f surrender <faction> — Capituler (Officier+) : fin immédiate de la guerre, annonce publique de défaite et réparations Cantox banque → banque (10 000 par défaut, transfert partiel si banque insuffisante).
  - /f warend <facA> <facB> — Terminer une guerre de force (staff) — permission : cantale.faction.bypass. Log console d'audit.
  - /f ally <faction> — S'allier à une faction (Officier+). Annule la guerre si elle existait.
  - /f secret — Activer/désactiver le mode secret. Taxe journalière de 1 000 Cantox par intervalle (20 min par défaut) prélevée sur la banque. Gratuit pendant 3 jours pour les nouvelles factions et après un raid subi.
  - /f spawn / /f setspawn / /f warp / /f setwarp / /f delwarp — Gestion des points de téléportation
  - /f bank — Gestion de la banque de faction
  - /f power / /f pouvoir — Achat de pouvoir
  - /f promote / /f demote — Gestion des grades
  - /f members — Liste des membres
  - /f perms [grade] — Permissions de faction
  - /f chat <message> — Canal de faction
  - /f bypass — Active/désactive le bypass admin : ignore toutes les protections de claims (build, conteneurs, spawners, PASDIC…). Session uniquement, coupé à la déco. Permission cantale.faction.bypass.
- /profile — Affiche le profil du joueur
- /link — Génère un code pour lier son compte Discord — utilise la base de données
- /unlink confirm — Délie son propre compte Discord (et ancien lien site s'il reste)
- /linkforce <Pseudo MC> <ID Discord> — Force la liaison Discord ↔ Minecraft (admin) — permission : cantale.admin
- /forceunlink <joueur> — Force la suppression de la liaison Discord d'un joueur (admin) — permission : cantale.admin
- /pay <joueur> <montant> — Payer un joueur
- /balance [joueur] (aliases : /bal, /money) — Afficher le solde
- /ah (aliases : /auction, /hdv) — Hôtel des ventes
- /trade <joueur> | /trade accept | /trade decline — Échange sécurisé : demande (60 s, boutons cliquables) → GUI (dépôt d'items, bouton argent avec saisie du montant, confirmation des deux côtés) → compte à rebours 5 s → échange. Annulation = items rendus (même hors ligne).
- /customitem <type> — Donner un item custom (admin) — permission : cantale.admin
- /home [set|delete|del] [nom] — Gérer ses homes personnels (TP : cooldown 30 s, warmup 3 s)
- /adminhome check <joueur> | /adminhome tp <joueur> <nom> | /adminhome delete <joueur> <nom> (aliases : ahome, seehome) — Lister / TP vers / supprimer les homes d'un joueur (online/offline) — permission : cantale.moderator (admin inclus)
- /adminec <joueur> (aliases : aec, seeec) — Voir/éditer l'ender chest d'un joueur — permission : cantale.moderator
- /adminpc <joueur> [1|2|3] (aliases : apc, sepc) — Voir/éditer les PC d'un joueur — permission : cantale.moderator
- /warp [nom|set|delete|toggle] — Téléportation aux warps publics (cooldown 30 s, warmup 3 s)
- /spawn — Téléportation au spawn (cooldown 30 s, warmup 3 s)
- /rtp — Téléportation aléatoire (cooldown 5 min + 30 s global, warmup 3 s)
- /moderation <kick|mute|tp|tphere|vanish> — Outils de modération — permission : cantale.moderator
- /vanish — Activer/désactiver le vanish — permission : cantale.moderator
- /modhelp — Aide pour les commandes de modération — permission : cantale.moderator
- /wanted [add|list] (aliases : /prime, /primes)
- /pc — Coffre privé virtuel
- /quetes [fac] (alias /quests) — Quêtes solo / faction
- /quete reroll <solo|fac> <daily|weekly|seasonal> — Régénérer un pool — permission : cantale.admin
- /quete give <joueur> <daily|weekly|seasonal|rare|epic|mythic|legendary> — Donner une récompense de quête — permission : cantale.admin
- /collections — Collections long terme (paliers)
- /saison [start] — Infos saison ; start = nouvelle saison (admin)

- /spawner give <joueur> <mob> [tier] — Donner un spawner Cantale (paliers 1–8) — permission : cantale.admin
- /perm <set|remove|check|list> — Gérer les permissions des joueurs
- /unbanip <ip> — Débannir une IP
- /leaderboard <type> [limite] (aliases : top, lb...)
- /lastdeath [joueur] — Date + coords de la dernière mort (online/offline)
- /optimize [stats|hotspots [n]|governor|mode <auto|normal|degraded|critical>|cleanup|clearall [named]|save|villagers <on|off|remove>|clearlag [secondes]|on|off|reload] — optimisation serveur (gouverneur de perf, diagnostic, clearlag) — permission : cantale.admin
- /ec — Ouvrir l’Ender Chest
- /pc2 — 2ème coffre privé (grade Chèvre)
- /rank <set|remove|info> <joueur> [AVENTURIER|VIP|CHEVRE|NONE] — permission : cantale.admin
- /day — Afficher le jour et l’heure Minecraft de l’overworld (horloge vanilla du monde)
- /daily (aliases : /journalier) — Réclame la récompense quotidienne
- /parrain <joueur> — Remercie ton parrain (une seule fois) ; il gagne +1 vie — permission : cantale.parrain (défaut true)
- /vote [claim|stats|top|help] — Système de vote
- /web (aliases : website, site) — Obsolète : affiche le lien https://www.cantale.world/connexion (connexion Discord OAuth). Liaison Discord : /link · délier : /unlink confirm
- /faq <reload|add|remove> — Gérer la base de connaissances du bot FAQ (recharger avec /faq reload) — permission : cantale.admin
- /anticheat (alias /ac) — Administration anti-cheat — permission : cantale.admin
  - /ac toggle [check] — Activer/désactiver l'anti-cheat global ou un check (runtime, non persisté)
  - /ac reload — Recharger la config anti-cheat (les toggles runtime sont conservés)
  - /ac info <joueur> — Moteur actif (Grim/fallback), trust, ping, TPS, kicks 24 h, violations session + BDD
  - /ac alerts — Activer/désactiver ses alertes anti-cheat en jeu (par session)
  - /ac clear <joueur> — Réinitialiser violations et trust d'un joueur
- /tag <create|delete|add|remove|list> — Gérer les tags — permission : cantale.moderator
- /discord <message|joueur> [message] — Envoyer un message sur Discord depuis Minecraft
- /pasdic set <true|false> — Administration : activer/désactiver un PASDIC manuellement — permission : cantale.admin

Notes :
- Certaines commandes nécessitent un accès à la base de données (link, unlink, forceunlink, pc2, vote, ranks, tags, etc.). En cas d’indisponibilité DB, elles renvoient un message d’erreur ou sont différées selon l’implémentation.
- Les téléportations (home, warp, spawn, rtp, tpa, /f spawn, /f go, /f warp, /events, claims GUI) : cooldown global **30 s** entre deux TP ; **/rtp** attend aussi **5 min** (le plus long des deux). Warmup **3 s** immobile avec préchargement du chunk (annulé si tu bouges ou subis des dégâts). Combat-tag bloque. Exemptés : `cantale.admin` et mode trace `/moderation`. VIP / Chèvre ne sont pas exemptés.
- Mapping rapide des permissions : owner/admin → cantale.admin ; modérateur → cantale.moderator ; joueur standard → permissions de base.

## Systèmes de faction revus

### PASDIC
- Plus de mode Adventure. Un PASDIC est un claim renforcé : seuls les membres de la faction peuvent y poser/casser.
- Interdit d’y placer des conteneurs de stockage (coffres, trapped chest, barrel, ender chest, shulkers). Les fours, fumoirs, hauts fourneaux, hoppers, droppers et dispensers restent autorisés.
- Les spawners (Cantale et vanilla) peuvent être posés et fonctionnent dans un PASDIC par les membres de la faction.
- Conversion : `/f pasdic` dans un claim de sa faction → confirmation → paiement via la banque de faction. Shift + clic gauche sur `/f map` déclenche aussi la commande.
- Les runes de fortification ne peuvent pas être appliquées dans un PASDIC.

### Mode secret
- `/f secret` active/désactive un masquage de la faction sur la carte.
- Coût : 1 000 Cantox par intervalle (20 min par défaut), prélevé sur la banque de faction. Continu jusqu’à désactivation ou banque à 0.
- Avertissement aux officiers (MC + MP Discord si lié) si la banque ne couvre pas le prochain prélèvement.
- 3 jours gratuits pour les nouvelles factions et après chaque raid subi.

### Raids et guerre
- `/f raid` dans un claim ennemi démarre un raid (Officier+) — la guerre n'est PAS requise. Pendant le raid, les assaillants peuvent casser, exploser, poser des blocs (TNT, cristaux, etc.) et utiliser seaux d’eau/lave.
- Hors raid et hors guerre, les étrangers ne peuvent rien poser/casser dans un claim ennemi.
- `/f war <faction>` enregistre une déclaration de guerre : la guerre ne démarre que si l'autre faction déclare aussi (déclaration mutuelle, expiration 15 min par défaut). Broadcast serveur + Discord au début et à la fin.
- En guerre, les protections de claims des deux belligérants tombent en permanence entre eux (comme un raid actif, dans les deux sens) : casse, pose, TNT, seaux… Les PASDIC restent intouchables, y compris en guerre.
- En guerre : toute mort d'un membre fait perdre ×2 de pouvoir à sa faction (5 × multiplicateur) et un kill sur l'ennemi rapporte ×2 (config `factions.war.*`).
- Fin de guerre : `/f peace` mutuel (même expiration), `/f surrender` unilatéral (réparations Cantox banque → banque, 10 000 par défaut), ou `/f warend <facA> <facB>` (staff, permission cantale.faction.bypass, log console).
- Durée, protection progressive et nombre de raids simultanés subis dépendent du pouvoir de la faction défenseur ; cooldown et nombre de raids simultanés menés dépendent du pouvoir de l’attaquant (config.yml).

### Claim principal
- Le claim `/f setspawn` (f-spawn) n’a plus d’immunité explosion spéciale. Il est traité comme un claim normal.

### Items custom
- Les items custom ne peuvent pas être désenchantés à la table de désenchantement ni modifiés à l’enclume.

### Dernière vie
- À la perte de la dernière vie, le stuff (inventaire + armure + offhand) est dropé au sol et l’inventaire est vidé avant le bannissement. L’EC et le PC ne sont pas touchés.

## Anti-cheat (Grim + module natif)

- **Grim AC** (packet-based, compensation ping/TPS native) est le moteur principal : mouvement (Speed, Fly, NoFall, Step, Timer, Jesus, NoSlow…), combat (Reach, KillAura, Aim), rotations, BadPackets. Voir `docs/AntiCheat-Grim-Setup.md` pour l'installation (JARs dans `server-plugins/`).
- Le **module natif** garde la valeur ajoutée Cantale : FastBreak (exemptions outils custom), AutoClicker (seuil haut), AutoArmor/ChestStealer/FastUse (corrigés, GUIs vanilla uniquement).
- Si Grim est absent au démarrage, les checks natifs mouvement/combat/rotation sont **réactivés automatiquement** (fallback — jamais de trou de protection).
- **Trust** : 100 au départ ; violations (Grim + natif) retirent des points (plafond 1 pénalité/tick, 10 pts/min max) ; regen 1 pt/min.
- **Sanctions progressives** : warn (≤ 50) → kick (≤ 25, cooldown 5 min) → trust 0 = alerte staff prioritaire + kick. **Jamais de ban auto** ; tempban auto optionnel uniquement sur récidive (`punishments.ban-auto`, 3 kicks/24 h).
- **Preuves** : chaque violation est loguée en BDD (`anticheat_violations` : source, check, VL, trust, ping, TPS, détails) + alertes Discord #mod-logs.
