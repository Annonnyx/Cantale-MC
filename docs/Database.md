Database

Support : MySQL (recommandé en production) et SQLite (fallback pour tests/mono-serveur).

Initialisation :
- Le plugin tente la connexion au démarrage. En cas d'échec, il va différer certains chargements et retenter la connexion (voir `DatabaseManager.scheduleReconnect`).

Problèmes fréquents et résolutions :
- `Access denied for user 'user'@'host'` : vérifier l'utilisateur/host autorisé côté MySQL (`GRANT`), mot de passe, et que l'adresse source du serveur correspond.
- `NullPointerException` dans DAO au démarrage : causé par l'attente d'une connection non établie — le plugin maintenant défère les chargements DB-sensibles.

Bonnes pratiques :
- Évitez les opérations synchrones lourdes sur le main thread (déplacer vers `runTaskAsynchronously`).
- Utilisez une petite couche de cache pour les lectures fréquentes (ranks, warps, permissions).
