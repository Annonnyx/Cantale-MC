---
name: github-404-error-handling
description: Gestion des erreurs 404 lors de la récupération de fichiers depuis GitHub
source: auto-skill
extracted_at: '2026-07-10T18:01:19.395Z'
---

## Procédure de gestion des erreurs 404 GitHub

Lorsque vous rencontrez une erreur 404 lors de la tentative de récupération d'un fichier depuis GitHub, suivez cette procédure :

### Étapes à suivre

1. **Vérifier l'URL du dépôt**
   - Confirmer que l'URL est correcte et complète
   - Vérifier qu'elle pointe vers un dépôt existant

2. **Vérifier l'existence du fichier**
   - Naviguer manuellement dans le dépôt GitHub pour confirmer que le fichier existe
   - Vérifier le chemin exact du fichier (nom de fichier, dossier, etc.)

3. **Vérifier les permissions**
   - Si le dépôt est privé, s'assurer d'avoir les permissions nécessaires
   - Vérifier si l'authentification est requise

4. **Vérifier le nom de la branche**
   - Confirmer que le nom de la branche spécifié est correct
   - S'assurer que la branche existe dans le dépôt

5. **Utilisation de l'API GitHub**
   - Si vous utilisez l'API, vous pouvez tester l'accès via :
     ```
     curl -I https://api.github.com/repos/owner/repo/contents/path/to/file
     ```

### Erreurs fréquentes

- **URL incorrecte** : L'URL ne pointe pas vers le bon dépôt ou fichier
- **Fichier inexistant** : Le fichier a été supprimé ou déplacé
- **Permissions insuffisantes** : Accès refusé pour un dépôt privé
- **Nom de branche incorrect** : La branche spécifiée n'existe pas

### Solutions alternatives

1. Utiliser le navigateur web pour explorer le dépôt
2. Télécharger manuellement le fichier si possible
3. Vérifier les paramètres de configuration dans votre environnement de développement
4. Utiliser une commande git local pour cloner et inspecter le dépôt