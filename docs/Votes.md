# Votes Cantale — NuVotifier, API Serveur-Prive / ServeurListe & sites FR

Guide propriétaire : quoi chercher / modifier **sur les sites de vote** quand un vote ne produit **rien** dans la console.

Sites configurés dans `config.yml` → `vote.sites` :

| Affichage `/vote` | URL | Cooldown | Canal de récompense |
|-------------------|-----|----------|---------------------|
| Top-Serveurs.net | `https://top-serveurs.net/minecraft/vote/cantale` | 3 h | NuVotifier |
| Serveur-Prive.net | `https://serveur-prive.net/minecraft/cantale/vote` | 1 h | **API polling** (`serverVotesList`) |
| ServeurListe.com | `https://www.serveurliste.com/fr/minecraft/cantale/vote` | 1 h | **API polling** (vérif IP `token_api`) |

Serveur-Prive et ServeurListe **ne dépendent pas** de Votifier : Cantale interroge leur API. Un silence `[Vote] VotifierEvent reçu` après un vote sur ces sites est normal.

Top-Serveurs reste sur NuVotifier : si tu ne vois **jamais** `[Vote] VotifierEvent reçu` après un vrai vote sur **ce** site → le listing n’a **pas** contacté NuVotifier (ou le port est fermé).

Le `token_api` ServeurListe n’est **pas** un token Votifier : c’est le jeton de l’API de vérif. Ne change pas de listing : le polling API suffit même si le panneau affiche « connexion impossible » sur Votifier.

---

## 1. Côté serveur (vérifs rapides)

1. **NuVotifier** installé et activé (`plugins/NuVotifier.jar` ou équivalent) — au boot Cantale doit loguer :
   - `[Vote] Votifier détecté — listener enregistré…`
   - et **pas** `[Vote] Votifier non détecté…`
   (nécessaire pour Top-Serveurs ; optionnel pour ServeurListe si le polling API tourne)
2. Fichiers NuVotifier (chemins typiques) :
   - `plugins/Votifier/config.yml` — champs `host`, `port` (souvent `8192`), parfois `tokens:` (protocole v2)
   - `plugins/Votifier/rsa/public.key` — **clé publique** (protocole v1)
3. `host` : en général `0.0.0.0` (écoute toutes les interfaces).
4. Port Votifier **ouvert en TCP** sur le firewall / panel hébergeur / box (ce n’est **pas** le port Minecraft `25565`).
5. Ne colle **jamais** la clé privée (`private.key`) sur un site.

Les entrées `vote.tokens` dans le `config.yml` **Cantale** (ex. clés `serveurliste.com`, `serveur-prive.net`) sont des jetons **API / vérif site** pour le plugin — ce ne sont **pas** la clé publique NuVotifier à coller dans le panneau Votifier du site. Le `token_api` ServeurListe = `vote.tokens.serveurliste.com`.

---

## 2. Valeurs à coller sur les sites (depuis TON serveur)

Ouvre les fichiers **sur la machine de jeu**, copie les valeurs **réelles** (ne les invente pas, ne les commit pas) :

| Champ site (libellés courants) | Où le lire |
|--------------------------------|------------|
| **IP du serveur** / Host Votifier / Adresse | IP publique ou hostname du serveur (sans `:25565`) |
| **Port** (Votifier / plugin de vote) | `port:` dans `plugins/Votifier/config.yml` |
| **Clé publique** / Public key / RSA | contenu entier de `plugins/Votifier/rsa/public.key` (lignes `BEGIN`/`END` incluses si présentes) |
| **Token** / Clé secrète (protocole **v2**) | sous `tokens:` dans `plugins/Votifier/config.yml` (souvent `default:`) |
| **Protocol** / Version | **v1** = clé publique RSA ; **v2** = token. Choisis ce que le site propose et ce que NuVotifier attend |

Mots-clés UI à chercher dans le panneau du listing (FR/EN) :

- `Votifier`, `NuVotifier`, `Plugin de vote`, `Notifications de vote`
- `Activer` / `Enable` / `Enable Votifier`
- `IP du serveur`, `Host`, `Adresse Votifier`
- `Port`, `Port Votifier`
- `Clé publique`, `Public key`, `RSA`
- `Token`, `Secret`, `Clé`
- `Protocol`, `v1`, `v2`, `Legacy`

---

## 3. Checklist par site (sites Cantale)

### 3.1 Top-Serveurs.net (NuVotifier)

1. [top-serveurs.net](https://top-serveurs.net/) → espace propriétaire du serveur.
2. Chercher **Votifier** / **paramètres** / **options avancées**.
3. Activer, renseigner IP + port NuVotifier + clé publique (ou token v2).
4. Sauvegarder → voter via l’URL `/vote` du site.

### 3.2 Serveur-Prive.net (API officielle — pas Votifier)

Serveur-Prive expose une API REST en lecture seule. Pas de webhook / endpoint « claim » documenté
(`Allow: GET, HEAD` sur `/votes`). Cantale **poll** la liste officielle (`serverVotesList`).

Références :

- [Developers — `serverVotesList`](https://developers.serveur-prive.net/getting-started#/operations/serverVotesList)
- [Documentation APIs publique](https://serveur-prive.net/documentation)

#### Endpoints utilisés

| Usage | Méthode | URL |
|-------|---------|-----|
| Liste des votes du serveur (`serverVotesList`) | `GET` | `{api-url}/v1/servers/{token}/votes?page=N` |
| Vérif vote par IP (optionnel, souvent 404) | `GET` | `{api-url}/v1/servers/{token}/votes/{ip}` |

Le check IP n’est **pas** dans l’OpenAPI developers. En live il répond **404** : au premier 404 Cantale **désactive** ce chemin pour la session (un log INFO, plus de WARN/URL toutes les 60 s) et s’en tient à la liste.

**Auth :** le token va **dans le chemin**, pas en header `Authorization: Bearer`.  
Il n’y a **pas** de `server-id` séparé : le token dashboard **est** l’identifiant du serveur.

Champs JSON attendus (liste ou objet `data`) : `username` (ou `pseudo` / `player`), `voted_at` (timestamp Unix ou ISO), éventuellement `id` / `ip`. Les votes trop vieux (`max-age-minutes`) sont ignorés pour ne pas rejouer l’historique.

#### Où prendre le token

1. Connexion sur [serveur-prive.net](https://serveur-prive.net/) → **Mon serveur** / fiche **Cantale**.
2. Onglet **Paramètres**.
3. Copier le **Token** associé au serveur (pas la clé NuVotifier, pas le token OTP).

#### Clés à coller dans `plugins/CantalePlugin/config.yml` (serveur live)

```yaml
vote:
  tokens:
    serveur-prive.net: "COLLER_ICI_LE_TOKEN_PARAMETRES"

  serveur-prive:
    enabled: true
    api-url: "https://serveur-prive.net/api"
    api-token: ""          # vide = utilise vote.tokens.serveur-prive.net
    poll-interval-seconds: 60
    check-online-ips: false
    max-age-minutes: 120
```

| Clé | Rôle |
|-----|------|
| `vote.tokens.serveur-prive.net` | Token dashboard (Paramètres). **À remplir sur le live.** |
| `vote.serveur-prive.enabled` | `true` pour activer le polling |
| `vote.serveur-prive.api-url` | Base API, défaut `https://serveur-prive.net/api` |
| `vote.serveur-prive.api-token` | Override du token. Laisser vide si tu utilises `vote.tokens` |
| `vote.serveur-prive.poll-interval-seconds` | Période (min. 30 s), alignée sur le cooldown 1 h du site |
| `vote.serveur-prive.check-online-ips` | Optionnel. Si `true` et que `/votes/{ip}` 404, Cantale coupe ce chemin tout seul |
| `vote.serveur-prive.max-age-minutes` | Ignore les votes plus vieux (anti-dump historique) |

Pas de `server-id` à renseigner. Ne commite pas le token réel.

#### Vérif après déploiement du JAR

Au boot : `[Vote] Serveur-Prive API : polling toutes les 60s…`  
Après un vote (pseudo Minecraft renseigné sur le site, ou joueur en ligne avec la même IP publique) :

- `[Vote] … a voté sur Serveur-Prive.net` — récompense = 1× Cadeau du Roi (cooldown 1 h)
- Token manquant : `[Vote] Serveur-Prive API : token manquant…`

Les votes déjà récompensés sont mémorisés en BDD (`vote_external_ids` + cooldown site) : pas de double cadeau si Votifier se mettait aussi à tirer.

### 3.3 ServeurListe.com (API officielle — Votifier optionnel)

ServeurListe documente une API de **vérification par IP** (FAQ, pas de OpenAPI publique) :

- [FAQ FR](https://www.serveurliste.com/fr/faq)
- [FAQ EN](https://www.serveurliste.com/en/faq)

Pas de webhook, pas d’endpoint liste de votes (`GET /api/votes` → 404). Cantale **poll** donc l’IP publique de chaque joueur en ligne, comme le check IP Serveur-Prive.

#### Endpoint utilisé

| Usage | Méthode | URL |
|-------|---------|-----|
| État de vote d’une IP | `GET` | `{api-url}?ip_address={ip}&api_token={token_api}` |

Défaut `api-url` : `https://www.serveurliste.com/api/vote` (avec `www` — sans www le site répond 301).

**Auth :** `api_token` en **query**. Le champ dashboard s’appelle souvent **`token_api`**. Ce n’est **pas** le token NuVotifier v2.

Réponse observée : `{ "data": { "voted": false, "nextvote": null } }`. Si `voted: true`, la FAQ ajoute pseudo, délai avant le prochain vote, date du dernier vote. Intervalle de vote du site : **1 h 30**.

Le joueur doit être **en ligne** avec la **même IP publique** que celle utilisée pour voter (VPN / IP privée / proxy → le poller ignore l’IP).

> **Limite structurelle de la vérif IP :** un joueur qui vote depuis son téléphone en 4G (ou une box différente, un VPN, de l’IPv6) ne sera **jamais** détecté — l’IP du vote ≠ l’IP de connexion Minecraft. C’est la cause n°1 des « votes site 3 qui ne donnent rien ».
>
> **Solution durable recommandée :** configurer **Votifier** dans le dashboard ServeurListe (section Votifier de la fiche serveur — voir « Votifier connexion impossible » ci-dessous). Le vote arrive alors instantanément via NuVotifier, comme Top-Serveurs, sans aucune comparaison d’IP. Le polling API reste actif en fallback (dédup BDD = pas de double cadeau).

#### Où prendre le token_api

1. [serveurliste.com](https://www.serveurliste.com/) → **Mes serveurs** / fiche **Cantale**.
2. Copier **`token_api`** (UUID) — pas la clé RSA Votifier, pas le token NuVotifier `tokens.default`.

#### Clés à coller dans `plugins/CantalePlugin/config.yml` (serveur live)

```yaml
vote:
  tokens:
    serveurliste.com: "COLLER_ICI_LE_TOKEN_API"

  serveur-liste:
    enabled: true
    api-url: "https://www.serveurliste.com/api/vote"
    api-token: ""          # vide = utilise vote.tokens.serveurliste.com
    poll-interval-seconds: 60
    max-age-minutes: 120
```

| Clé | Rôle |
|-----|------|
| `vote.tokens.serveurliste.com` | `token_api` dashboard. **À remplir sur le live.** |
| `vote.serveur-liste.enabled` | `true` pour activer le polling |
| `vote.serveur-liste.api-url` | Endpoint vote, défaut ci-dessus |
| `vote.serveur-liste.api-token` | Override. Laisser vide si tu utilises `vote.tokens` |
| `vote.serveur-liste.poll-interval-seconds` | Période (min. 30 s) |
| `vote.serveur-liste.max-age-minutes` | Ignore les votes trop vieux (anti-rejeu) |

Ne commite pas le token réel. **Ne retire pas** le site 3 de `vote.sites`.

#### Vérif après déploiement du JAR

Au boot : `[Vote] ServeurListe API : polling toutes les 60s…`  
Après un vote (joueur **en ligne**, même IP publique) :

- `[Vote] … a voté sur ServeurListe.com` — récompense = 1× Cadeau du Roi
- Token manquant : `[Vote] ServeurListe API : token manquant…`
- Token faux : `[Vote] ServeurListe API : HTTP 401 (token_api invalide ?)`

Dédup BDD (`vote_external_ids` + cooldown site) : pas de double cadeau si Votifier se mettait aussi à tirer.

#### Votifier « connexion impossible » (le rendre fonctionnel = chemin fiable)

Le panneau ServeurListe teste un **TCP** vers NuVotifier. Échec fréquent ≠ bug Cantale :

| Champ ServeurListe | Valeur attendue (NuVotifier) |
|--------------------|------------------------------|
| IP / host Votifier | IP **publique** du serveur de jeu (pas `127.0.0.1`) |
| Port | `port:` dans `plugins/Votifier/config.yml` (souvent `8192`, **pas** `25565`) |
| Clé publique / RSA (protocole **v1**) | contenu de `plugins/Votifier/rsa/public.key` |
| Token (protocole **v2**) | `tokens.default` (ou le nom du service) dans le même `config.yml` NuVotifier — **pas** `token_api` |

Sur **ton** serveur, NuVotifier doit écouter toutes les interfaces :

```yaml
host: 0.0.0.0
port: 8192
```

`host: 127.0.0.1` → le site ne peut pas se connecter (« connexion impossible »). Ouvre le port **TCP** sur le firewall / panel hébergeur. Protocole : v1 = clé RSA, v2 = token NuVotifier. Un mélange v1/v2 fait échouer le test.

Dès que le test Votifier du panneau ServeurListe passe au vert, les votes site 3 arrivent **sans condition d’IP** — c’est le fonctionnement le plus fiable. Le polling API reste un simple fallback (IP 4G/VPN non couvertes sinon).

*(Si un jour tu ajoutes Serveur-Minecraft / autre listing : même logique — API si elle existe, sinon Votifier + IP + port + clé/token.)*

---

## 4. Vérification (logs attendus)

Après un vote réel (ou un test site / `nuvotifier test` selon la version) :

| Log | Signification |
|-----|----------------|
| `[Vote] Serveur-Prive API : polling toutes les …s` | Polling site 2 actif au boot |
| `[Vote] ServeurListe API : polling toutes les …s` | Polling site 3 (vérif IP) actif au boot |
| `[Vote] VotifierEvent reçu` | Top-Serveurs (ou test / ServeurListe si Votifier TCP OK) a joint NuVotifier |
| `[Vote] Extrait — username=…, site=…` | Payload Votifier lu |
| `[Vote] … a voté sur …` | Récompense traitée (Votifier **ou** API) |
| `[Vote] Site Votifier non reconnu : "…"` | Event OK mais `serviceName` à aligner (alias dans `vote.sites`) |
| `[Vote] Vote ignoré … déjà récompensé` | Anti-doublon (API + Votifier, ou re-poll) |
| **Aucun** `[Vote] VotifierEvent reçu` sur Top-Serveurs | Site → NuVotifier n’a pas connecté (config site, port, clé, `host: 0.0.0.0`) |

Au démarrage, si NuVotifier est OK, Cantale rappelle aussi de consulter ce guide si les votes **Votifier** restent silencieux.

---

## 5. Alias `serviceName` (si event reçu mais site « inconnu »)

Cantale matche le `serviceName` NuVotifier contre `vote.sites` (nom, hôte URL, alias, clés `vote.tokens`).

Alias déjà prévus (extraits) :

- Top-Serveurs : `top-serveurs`, `TopServeurs`, …
- Serveur-Prive : `serveur-prive`, `ServeurPrive`, …
- ServeurListe : `serveurliste`, `ServeurListe`, …

Si le log montre un autre nom (ex. `Top-Serveurs.net`), ajoute-le en 4ᵉ champ alias de la ligne `vote.sites` correspondante, puis `/reload` ou redémarrage selon ta procédure.

---

## Ressources

- API Serveur-Prive (liste) : https://developers.serveur-prive.net/getting-started#/operations/serverVotesList
- API Serveur-Prive (vérif IP / top voteurs) : https://serveur-prive.net/documentation
- API ServeurListe (vérif IP, FAQ) : https://www.serveurliste.com/fr/faq
- NuVotifier : https://github.com/NuVotifier/NuVotifier
- Ancien brouillon générique (sites EN, VotingPlugin) : `VOTE_SITES_GUIDE.md` — **préférer ce fichier** pour Cantale.
