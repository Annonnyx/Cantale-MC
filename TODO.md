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

## Fait
- [x] Bot FAQ automatique (Minecraft + Discord) avec moteur de matching local, faq.yml, commandes /faq et /faq-admin, réponses privées en jeu et embeds Discord
- [x] Correction salon chat Discord (redirection des messages MC vers 1521952439052402838)
- [x] Support `/profile <joueur>` pour afficher le profil de n'importe quel joueur
- [x] Système de chat de faction Minecraft (`/f c` et `/fc`)
- [x] Fix faux-positifs AntiCheat (Timer Check : seuil à 28 pkts/s + tampon de lissage)
- [x] Système Anti-AFK par grade (10m Joueur, 30m Aventurier, 1h VIP, illimité pour Chèvre/Staff)
- [x] Fix MultiCantool (outil virtuel, Haste fluide, respect des protections de claims/spawn)

## Fait
- [x] Bot FAQ : matching basé sur les mots de contenu (stop-words ignorés) + seuil 70 + IP = play.cantale.fun
- [x] Bot FAQ : questions explicites uniquement (`require-explicit-question`)
- [x] Vanish : fake quit/join, hidePlayer Paper, retrait tab
- [x] Tickets légendaires numérotés + salon Discord auto
- [x] /profile autre joueur (résolution online → DB → UUID)
- [x] /status live : durée 30 min + try/catch anti-crash scheduler

## Plan — Migration Paper 1.21.4 → 1.21.11
- [x] Bump `pom.xml` → `paper-api:1.21.11-R0.1-SNAPSHOT`
- [x] Mise à jour FAQ / AGENTS (version affichée)
- [x] Compiler + corriger si besoin (OK, 0 erreur)
- [ ] Smoke test sur serveur Paper 1.21.11
- [ ] Déployer le JAR + maj `faq.yml` serveur

## En cours / à valider
- [x] Fix Multi-Cantool : retire le blocage spawn/warp unique + réadaptation réelle de l'outil + casse 3x3/arbre
- [x] Migration auto schéma `private_chests` (legacy items/location → chest_type/contents)
- [ ] Tester le cache des coffres privés en jeu (ouverture, fermeture, stockage depuis une caisse)
- [ ] Vérifier les IDs CustomModelData du resource pack après suppression des IDs 22 et 24
- [ ] Surveillance des performances du stockage PC suite au changement
- [ ] Tester les détections anti-cheat en jeu et ajuster les seuils dans config.yml si besoin
- [ ] Tester en jeu : /f map, /f go, détection claims, vote, multicantool, PC navigation, commandes owner, kills pouvoir, création faction
- [ ] Déployer nouveau JAR + **mettre à jour** `plugins/CantalePlugin/faq.yml` sur le serveur (sinon l'ancien fichier garde le vieux matching)