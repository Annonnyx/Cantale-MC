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

5) Votes reçus sur Top-Serveurs mais silence sur Serveur-Prive / ServeurListe
- **Serveur-Prive (site 2)** : récompense via **polling API**, pas Votifier. Au boot tu dois voir `[Vote] Serveur-Prive API : polling…`. Token dashboard (onglet Paramètres) → `vote.tokens.serveur-prive.net` dans `plugins/CantalePlugin/config.yml`. Détail : [docs/Votes.md](Votes.md) §3.2.
- **ServeurListe (site 3)** : récompense via **polling API** (`token_api` → `vote.tokens.serveurliste.com`). Au boot : `[Vote] ServeurListe API : polling…`. Le joueur doit être en ligne avec la même IP publique. « Connexion impossible » Votifier = TCP (souvent `host: 0.0.0.0` + port 8192 ouvert) — **ignorable** si l’API tourne. Détail : [docs/Votes.md](Votes.md) §3.3.
- **Top-Serveurs** : Cantale ne voit un vote que si **NuVotifier** reçoit le paquet et fire `VotifierEvent` → log `[Vote] VotifierEvent reçu`.
- Sur le panneau Votifier (Top-Serveurs, optionnel ServeurListe) : IP publique + port NuVotifier (souvent `8192`), token NuVotifier **ou** clé `rsa/public.key` — **pas** les `vote.tokens` / `token_api` de Cantale.
- Checklist détaillée : [docs/Votes.md](Votes.md).

Logs utiles :
- `plugins/Cantale/logs` (si présent) ou la console du serveur — rechercher `DatabaseManager`, `Discord`, `BumpReminder`, `[Vote]`.
