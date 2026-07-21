Development

Environnement recommandé :
- JDK 17
- Maven
- IDE (IntelliJ/VSCode)

Build & Run :
- `mvn clean package -DskipTests`
- Déployer le jar dans `plugins/` et redémarrer le serveur.

Debug local :
- Utiliser une instance Paper locale et attacher le débogueur remote.
- Charger une base SQLite locale pour tests rapides si MySQL distant n'est pas disponible.

Contributions :
- Respecter les conventions existantes dans le code (naming, pas de modifications de configuration sans consentement).
- Préférer les tâches asynchrones pour I/O et DB.
- Ajouter des tests unitaires pour la logique pure si possible.
