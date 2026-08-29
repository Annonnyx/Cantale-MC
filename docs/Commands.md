Commands (référence détaillée)

Résumé rapide : voir le fichier COMMANDS.md à la racine pour la liste complète. Cette page complète la documentation avec des exemples d’usage, des règles de fonctionnement et un mapping des permissions.

Catégories de commandes

1. Utilitaires
- /cantale — affiche les infos du serveur et les commandes utiles.
- /profile — affiche le profil du joueur.
- /day — affiche le jour et l’heure Minecraft de l’overworld (`fullTime / 24000`, persisté dans le monde).
- /daily — réclame la récompense quotidienne.
- /parrain <joueur> — remercie le joueur qui t’a invité (une seule fois) ; le parrain gagne +1 vie.

2. Téléportation
- /home [set|delete|del] [nom] — gère les homes personnels.
- /adminhome check <joueur> — (staff) liste les homes d'un joueur.
- /adminhome tp <joueur> <nom> — (staff) se téléporte à ce home (forme courte : /adminhome <joueur> <nom>).
- /adminhome delete <joueur> <nom> — (staff) supprime ce home (en ligne ou hors ligne, log console).
- /warp [nom|set|delete|toggle] — téléporte vers un warp public ou gère les warps.
- /spawn — téléporte au spawn.
- /rtp — téléporte aléatoirement.

3. Économie et progression
- /pay <joueur> <montant> — payer un joueur en Cantox.
- /balance [joueur] — consulter son solde.
- /ah — hôtel des ventes.
- /trade <joueur> — envoie une demande d'échange (expire après 60 s ; le receveur accepte via les boutons cliquables du chat ou /trade accept, refuse via /trade decline). Une fois acceptée, une fenêtre d'échange s'ouvre : dépôt d'items (clic avec item en main ou shift-clic), bouton lingot d'or pour proposer des Cantox (montant tapé dans le chat), bouton de confirmation des deux côtés, puis compte à rebours de 5 s avant l'échange. Toute modification d'offre réinitialise les confirmations ; fermer la fenêtre ou déconnecter annule l'échange et rend les items (restitués au prochain login si hors ligne).
- /trade — rouvre la fenêtre d'échange en cours (ou le sélecteur de joueur).
- /trade money <montant> — définit directement le montant proposé.
- /vote [claim|stats|top|help] — consulter les sites de vote et récompenses.
- /quetes [fac] — quêtes solo (jour / semaine / saison) ou courses de faction.
- /collections — collections long terme (paliers à réclamer).
- /saison — infos de la saison en cours (`/saison start` : admin).

4. Contenu custom et inventaires
- /customitem <type> — donne un item custom (admin).
- /pc — coffre privé virtuel.
- /pc2 — second coffre privé (grade Chèvre).
- /ec — ouvrir l’Ender Chest.
- /adminec <joueur> — (staff) voir/éditer l’EC d’un joueur (live si en ligne ; hors ligne = legacy DB seulement).
- /adminpc <joueur> [1|2|3] — (staff) voir/éditer les PC (DB, en ligne ou hors ligne).

5. Modération
- /moderation <kick|mute|unmute|tp|tphere|vanish> — outils modération.
- /vanish — activer/désactiver le vanish.
- /modhelp — aide rapide pour les modérateurs.
- /wanted [add|list] — gérer les primes.
- /link — lier son compte Discord.
- /unlink confirm — délier son propre compte Discord (et ancien lien site).
- /linkforce <Pseudo MC> <ID Discord> — (admin) forcer la liaison Discord.
- /forceunlink <joueur> — (admin) délier Discord d'un joueur.

6. Administration
- /admin — liste les commandes admin/modération.
- /perm <set|remove|check|list> — gérer les permissions.
- /rank <set|remove|info> <joueur> [grade] — gérer les grades.
- /optimize [stats|cleanup|clearall|save|chunks|villagers|gc|on|off|reload> — optimiser le serveur.
- /pasdic set <true|false> — gérer le mode PASDIC sur un chunk (échoue s'il y a des spawners ou du stockage).
- /spawner give <joueur> <mob> [tier] — donner un spawner Cantale (admin).

Exemples d’usage courants
- /warp spawn — téléporte au warp public spawn.
- /home base — téléporte au home nommé base.
- /pc2 — ouvre le coffre privé secondaire.
- /link — génère un code de liaison Discord.
- /unlink confirm — délie son propre Discord.
- /forceunlink <joueur> — (admin) délie le Discord d'un joueur.
- /vanish — active ou désactive le vanish.

Permissions et mapping
- cantale.admin — accès complet aux commandes d’administration et d’optimisation (inclut cantale.moderator).
- cantale.moderator — accès aux commandes de modération non-OP + /adminhome, /adminec, /adminpc.
- cantale.givevie — autorise /givevie.

Mapping simple par rôle
- Owner / Admin : cantale.admin
- Modérateur : cantale.moderator
- VIP / Chèvre : accès métiers et téléports standards, sans accès admin
- Joueur standard : permissions de base uniquement

Notes d’implémentation
- Les commandes qui accèdent à la base de données (link, unlink, forceunlink, pc2, vote, tags, ranks) sont exécutées de manière asynchrone lorsque c’est possible pour éviter de bloquer le thread principal.
- Les téléportations préchargent le chunk cible peu avant l’arrivée afin de limiter les latences et les échecs de chargement.
- Si la base de données est indisponible, certaines commandes renvoient un message explicite ou sont différées jusqu’à la reconnexion.

