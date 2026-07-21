# TODO Cantale

## Fait
- [x] Mise à jour des URLs de vote et suppression des sites obsolètes (config.yml)
- [x] Onglet statistiques dans `/profile`
- [x] Message d'obtention de tag invitant à activer dans `/profile`
- [x] Commande admin `/tag color`
- [x] Unification rank / permissions + ajout du grade `ADMIN`
- [x] Fix `/rtp` asynchrone (World#getChunkAtAsync)
- [x] Fix `/discord mp` (compatible nom Discord ET nom MC)
- [x] Commandes `/daily` / `/journalier` sync Discord/Minecraft
- [x] Retrait des codes couleur `§` des messages Discord
- [x] Coffre privé : cache en mémoire pour éviter la perte d'items
- [x] Caisse : surplus de récompenses stocké dans le coffre privé (PC)
- [x] Caisse : annonce publique uniquement pour les caisses Mythique et Légendaire
- [x] Rééquilibrage des tables de butin (moins de vies/Cantalames, plus d'outils/ressources/nourriture, récompenses "pas ouf")
- [x] Suppression des `Statio-Lytras` et `Rune de Fortification` du plugin (items, lore, listener d'utilisation)
- [x] Confirmation : `/crate` est bien réservée aux admins (`cantale.admin`)
- [x] Système anti-cheat modulable (Speed, Fly, NoFall, Step, Timer, Reach, KillAura, AutoClicker, Aim, Rotation, Inventory Checks)

## En cours / à valider
- [ ] Tester le cache des coffres privés en jeu (ouverture, fermeture, stockage depuis une caisse)
- [ ] Vérifier les IDs CustomModelData du resource pack après suppression des IDs 22 et 24
- [ ] Surveillance des performances du stockage PC suite au changement
- [ ] Tester les détections anti-cheat en jeu et ajuster les seuils dans config.yml si besoin
- [x] Extensions Discord : /balance, /leaderboard, /fleaderboard, /giveaway, /stats live
- [x] Commandes owner Discord : /say, /off + message lancement bot
- [x] Commande owner Minecraft : /say
- [x] /f map refonte (nord en haut)
- [x] /f go (spawn sans cooldown)
- [x] Détection entrée/sortie claim (anti-spam + metadata)
- [x] Bloquer dernière rangée PC (navigation)
- [x] Vote : clé Cadeau du Roi seule
- [x] Fix MultiCantool : casser tous les blocs avec outil virtuel
- [x] Gain de pouvoir faction par kill (+5)
- [x] Pouvoir initial de faction (60) et minimum 5 claims
- [x] Fix menus /cantale (BaseUI anti-doublon listeners + protection shift-clic)
- [ ] Tester en jeu : /f map, /f go, détection claims, vote, multicantool, PC navigation, commandes owner, kills pouvoir, création faction
- je voudrais aussi une commande reservée a moi seul (voici mon id discord : 1122092101459517481) /say pour pouvoir faire dire ce que je veux au bot, si je l'utilise sur minecraft le bot envoie un message sur minecraft si je l'utilise sur discord le bot envoie un message dans le salon courrant. Toujours réservée pour moi une commande /off : qui envoie un message ici 1504900607591452852 avec un ping 1504910790996525247 pour dire que le serveur est off et si possible fermer le serveur juste apres. Pareil pour un message quand le bot ce lance. 