Discord Integration

Activation : config `discord.enabled` et `discord.token`.

Fonctionnalités :
- Verification (liaison Minecraft ↔ Discord)
- Chat bridge (MC ↔ Discord)
- Moderation panel (via panels/slash)
- Daily leaderboard and bump reminders
- **Salon compteur** (`discord.channels.counting`)

Bump Reminder :
- La tâche `BumpReminderTask` poste un rappel et reschedule le prochain rappel après un bump détecté afin d'éviter les pings toutes les 2h fixes.

Salon compteur :
- Salon configuré via `discord.channels.counting` (défaut `1537112048314491040`).
- Si le message contient un chiffre : le nombre du compteur doit être **précédent + 1** (nombre en tête de message, ou unique entier du message). Sinon le message est supprimé (permission Manage Messages) et un court avertissement FR est posté puis auto-supprimé.
- Si le message **ne contient aucun chiffre** : chat libre, le compteur n'avance pas.
- Compteur persisté dans `plugins/Cantale/discord-counting.yml` ; au démarrage, si le fichier est vide/absent, reprise depuis l'historique récent du salon.
- Permission bot recommandée : Manage Messages (+ lire l'historique).

Setup rapide :
- Créez une application Discord et un bot, récupérez le token.
- Autorisez les intents nécessaires (`GUILD_MESSAGES`, `MESSAGE_CONTENT`, etc.).
- Configurez les IDs de guild et salons dans `config.yml`.

Sécurité et limites :
- Ne pas automatiser l'exécution de `/bump` (risque de sanction). Le plugin rappelle simplement et detecte le bump effectué par un membre.
