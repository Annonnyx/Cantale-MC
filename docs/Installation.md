Installation

Prérequis :
- Serveur Paper/Bukkit (version compatible 1.20+)
- Java 17+
- MySQL (optionnel) ou laisser SQLite (fallback)
- Maven pour compiler localement (ou builder CI)

Build local :

```bash
mvn clean package -DskipTests
```

Déploiement :
- Copier `target/Cantale-<version>.jar` dans `plugins/` du serveur.
- Placer `config.yml` dans `plugins/Cantale` (ou laisser le plugin générer un exemple).
- Redémarrer le serveur.

Notes :
- Si vous utilisez MySQL, vérifiez que l'utilisateur a les droits (SELECT/INSERT/UPDATE/DELETE) et que l'accès réseau permet la connexion depuis l'IP du serveur Minecraft.
- Les logs du plugin montrent les erreurs de connexion DB et une logique de reconnexion automatique si la base est indisponible au démarrage.
