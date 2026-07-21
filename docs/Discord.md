Discord Integration

Activation : config `discord.enabled` et `discord.token`.

Fonctionnalités :
- Verification (liaison Minecraft ↔ Discord)
- Chat bridge (MC ↔ Discord)
- Moderation panel (via panels/slash)
- Daily leaderboard and bump reminders

Bump Reminder :
- La tâche `BumpReminderTask` poste un rappel et reschedule le prochain rappel après un bump détecté afin d'éviter les pings toutes les 2h fixes.

Setup rapide :
- Créez une application Discord et un bot, récupérez le token.
- Autorisez les intents nécessaires (`GUILD_MESSAGES`, `MESSAGE_CONTENT`, etc.).
- Configurez les IDs de guild et salons dans `config.yml`.

Sécurité et limites :
- Ne pas automatiser l'exécution de `/bump` (risque de sanction). Le plugin rappelle simplement et detecte le bump effectué par un membre.
