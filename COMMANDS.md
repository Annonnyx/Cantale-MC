Cantale - Commandes (extrait de plugin.yml)

Format : /commande — usage — permission (si présente)

- /cantale — Affiche les informations du serveur et les commandes utiles
- /admin — Liste toutes les commandes admin et modérateur — permission : cantale.admin
- /dropvie — Sacrifier une vie
- /givevie <joueur> — Donner une vie — permission : cantale.givevie
- /addlife <joueur> <nombre> — Ajouter des vies (admin) — permission : cantale.admin
- /removelife <joueur> <nombre> — Retirer des vies (admin) — permission : cantale.admin
- /f (alias /faction) — Commandes de faction
- /profile — Affiche le profil du joueur
- /link — Génère un code pour lier son compte Discord — utilise la base de données
- /pay <joueur> <montant> — Payer un joueur
- /balance [joueur] (aliases : /bal, /money) — Afficher le solde
- /ah (alias /auction) — Hôtel des ventes
- /trade <joueur|accept|decline> — Échanger
- /customitem <type> — Donner un item custom (admin) — permission : cantale.admin
- /home [set|delete|del] [nom] — Gérer ses homes personnels
- /warp [nom|set|delete|toggle] — Téléportation aux warps publics
- /spawn — Téléportation au spawn
- /rtp — Téléportation aléatoire
- /moderation <kick|mute|unmute|tp|tphere|vanish> — Outils de modération — permission : cantale.moderator
- /vanish — Activer/désactiver le vanish — permission : cantale.moderator
- /modhelp — Aide pour les commandes de modération — permission : cantale.moderator
- /wanted [add|list] (aliases : /prime, /primes)
- /pc — Coffre privé virtuel
- /pasdic set <true|false> — Activer/désactiver PASDIC sur un chunk — permission : cantale.admin
- /perm <set|remove|check|list> — Gérer les permissions des joueurs
- /unbanip <ip> — Débannir une IP
- /leaderboard <type> [limite] (aliases : top, lb...)
- /lastdeath [joueur]
- /optimize [stats|cleanup|clearall|save|chunks|villagers|gc|on|off|reload] — permission : cantale.admin
- /ec — Ouvrir l’Ender Chest
- /pc2 — 2ème coffre privé (grade Chèvre)
- /rank <set|remove|info> <joueur> [AVENTURIER|VIP|CHEVRE|NONE] — permission : cantale.admin
- /day — Afficher le jour et l’heure Minecraft
- /daily (aliases : /journalier) — Réclame la récompense quotidienne
- /vote [claim|stats|top|help] — Système de vote
- /web link <code> (aliases : website, site) — Lier son compte Minecraft au site Cantale
- /tag <create|delete|add|remove|list> — Gérer les tags — permission : cantale.moderator
- /discord <message|joueur> [message] — Envoyer un message sur Discord depuis Minecraft

Notes :
- Certaines commandes nécessitent un accès à la base de données (link, pc2, vote, ranks, tags, etc.). En cas d’indisponibilité DB, elles renvoient un message d’erreur ou sont différées selon l’implémentation.
- Les téléportations préchargent le chunk cible quelques ticks avant l’arrivée afin de limiter les latences.
- Mapping rapide des permissions : owner/admin → cantale.admin ; modérateur → cantale.moderator ; joueur standard → permissions de base.
