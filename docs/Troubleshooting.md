Troubleshooting

Problèmes courants :

1) Échec de connexion MySQL (`Access denied`)
- Vérifier `config.yml` (host, port, username, password).
- Vérifier les privilèges MySQL (`GRANT ALL ON cantale.* TO 'user'@'host';`), et l'hôte source.
- Si la connexion échoue au démarrage, le plugin différera certains chargements et réessaiera (voir logs pour `scheduleReconnect`).

2) NPE dans AuctionDAO ou autre DAO au démarrage
- Cause fréquente : code qui assume une connexion DB disponible à `onEnable()`.
- Solution : redémarrer après avoir corrigé la DB, ou attendre la reconnexion automatique. Pour une correction durable, s'assurer que les chargements DB se font après `DatabaseManager.isConnected()`.

3) Téléportations lentes / échecs
- Vérifier les logs pour blocage de chunk load. Le plugin a été modifié pour précharger les chunks avant le TP.

4) Bump Discord qui ping trop souvent
- Résolu : `BumpReminderTask` reschedule après détection du bump réel.

Logs utiles :
- `plugins/Cantale/logs` (si présent) ou la console du serveur — rechercher `DatabaseManager`, `Discord`, `BumpReminder`.
