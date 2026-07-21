# 📢 Annonce de mise à jour — Cantale Network

```
§8§m--------------------------------------------------
§6§l  Mise à jour majeure de Cantale
§8§m--------------------------------------------------
```

## 🎮 Serveur & Performance

- **Passage sous Paper 1.21.4** et Java 21 pour plus de stabilité et de performances.
- Le `/rtp` a été revu : distance réduite et génération asynchrone des chunks pour éviter les freezes du serveur.

## 🎁 Caisses & Récompenses

- **Amélioration des caisses** : Vote → Rare → Épique → Mythique → Légendaire, étape par étape.
- Le rang **Chèvre** n'est obtenu qu'en atteignant la caisse Légendaire depuis une caisse Vote.
- Correction des récompenses `/wanted` : le chasseur reçoit bien la prime en tuant sa cible.

## 🗳️ Système de vote

- Intégration complète avec les sites de vote.
- Récompenses selon le cooldown du site : 24h = 3 caisses, 3h = 2 caisses, 1h = 1 caisse.
- Anti-abus par IP.
- **Vote Party**, paliers de votes, classement mensuel et rappels de vote.
- Compatibilité corrigée avec **NuVotifier** en 1.21.4.

## 🏷️ Tags & Personnalisation

- Le `/profile` dispose désormais d'un onglet **Statistiques** (kills, morts, ratio, kill streak, playtime…).
- Les modérateurs peuvent donner des tags avec `/tag add <joueur> <id>`.
- Les administrateurs peuvent modifier l'affichage d'un tag avec `/tag color <id> <couleur>`.
- Lorsque vous obtenez un tag, un message vous indique comment l'activer dans `/profile`.

## 👑 Grades & Permissions

- Unification des grades : tout passe par `PlayerRank`.
- Nouveau grade **Admin** distinct de **Owner**.
- Les **Admins** et **Owners** ont les mêmes permissions et avantages (vol, pas de cooldown TP, homes illimités, etc.).
- Les commandes `/rank` et `/perm` sont maintenant synchronisées : utiliser l'une met à jour l'autre.
- Correction des préfixes : Admin = `§c[Admin]`, Owner = `§4[Owner]`, Modérateur = `§9[Modérateur]`.

## 📘 Autres correctifs

- Couleurs du tutoriel corrigées pour être lisibles.
- Effet visuel du **Multi-Cantool** adapté à la 1.21.4.
- Build Maven stabilisé avec JUnit 5.
- Les codes couleur Minecraft (`§`) sont automatiquement retirés des messages envoyés sur Discord (morts, join/quit, etc.).

```
§8§m--------------------------------------------------
§7Merci de jouer sur §6Cantale Network §7!
§8§m--------------------------------------------------
```

---

*Message généré automatiquement pour diffusion en jeu via `/broadcast` ou sur Discord.*
