Configuration

Fichier principal : `src/main/resources/config.yml` (copier dans `plugins/Cantale/config.yml` après première exécution).

Clés importantes :
- `database.type` : `mysql` ou `sqlite`
- `database.mysql.host`, `port`, `database`, `username`, `password`
- `discord.enabled`, `discord.token`, `discord.guildId`, `discord.channels` (ids)
- `teleport.delay` : paramètres globaux (les permissions et ranks peuvent bypasser)

Sécurité :
- Ne commitez jamais le mot de passe DB en clair dans VCS.
- Privilèges MySQL : restreindre l'utilisateur au minimum nécessaire et limiter les hôtes autorisés.

Exemple minimal MySQL :
```
database:
  type: mysql
  mysql:
    host: 127.0.0.1
    port: 3306
    database: cantale
    username: cantale_user
    password: s3cret
```
