# Guide : Sites de Vote pour Serveur Minecraft CANTALE

> Comment lier ton serveur Paper 1.20.4 aux sites de vote et récompenser tes joueurs.

---

## 1. Les sites de vote principaux

| Site | URL | Popularite | Frequence |
|------|-----|-----------|-----------|
| **Planet Minecraft** | planetminecraft.com | Tres haute | 24h |
| **Minecraft-Server.net** | minecraft-server.net | Haute | 24h |
| **Minecraft MP** | minecraft-mp.com | Haute | 24h |
| **TopG** | topg.org | Moyenne | 24h |
| **MCSL** | minecraft-serverlist.com | Moyenne | 24h |
| **Servers-Minecraft** | servers-minecraft.com | Moyenne | 24h |

Conseil : Commence par les 3 premiers.

---

## 2. Prerequis cote serveur

### 2.1 Adresse IP publique & port

Ton serveur doit etre accessible depuis Internet :

```
IP:   ton-ip-publique ou ton-nom-de-domaine
Port: 25565 (par defaut)
```

Si tu es en local :
- Ouvre le port `25565` (TCP) sur ta box
- Utilise [No-IP](https://www.noip.com/) si tu n'as pas d'IP fixe

### 2.2 Verifier que le serveur est online

```bash
curl -s "https://api.mcsrvstat.us/2/TON_IP:25565" | python3 -m json.tool
```

Tu dois voir `"online": true`.

### 2.3 Plugin NuVotifier

Telecharge `NuVotifier.jar` depuis [SpigotMC](https://www.spigotmc.org/resources/nuvotifier.13449/) ou [GitHub](https://github.com/NuVotifier/NuVotifier/releases), place-le dans `plugins/` et redemarre.

Apres le premier demarrage, le dossier `plugins/Votifier/` est cree avec :

```
plugins/Votifier/
├── config.yml
├── rsa/
│   ├── public.key    <-- a copier sur les sites de vote
│   └── private.key
└── tokens.json
```

Edite `plugins/Votifier/config.yml` :

```yaml
host: 0.0.0.0
port: 8192
debug: false

forwarding:
  method: none
```

> Ouvre aussi le port `8192` (TCP) sur ta box.

Recupere la cle publique :
```bash
cat plugins/Votifier/rsa/public.key
```

---

## 3. Lier le serveur a chaque site

### 3.1 Planet Minecraft

1. Cree un compte sur [planetminecraft.com](https://www.planetminecraft.com/)
2. Account > Servers > Add Server
3. Remplis :
   - Server Name : CANTALE
   - Server Address : `ton-ip:25565`
4. Clique sur Verify Ownership (suivre les instructions)
5. Active le voting

### 3.2 Minecraft-Server.net

1. [minecraft-server.net](https://minecraft-server.net/) > Add Server
2. IP + Port 25565
3. Active Votifier dans les parametres avances
4. Colle ta cle publique
5. IP de callback : `ton-ip:8192`

### 3.3 Minecraft-MP.com

1. [minecraft-mp.com](https://minecraft-mp.com/) > Add Server
2. Section Votifier : coche Enable, colle la cle publique, port `8192`

### 3.4 Autres sites

Processus identique : Add Server > activer Votifier > coller cle publique > port `8192`.

---

## 4. Configurer les recompenses

### 4.1 Avec VotingPlugin (recommande)

Telecharge [VotingPlugin](https://www.spigotmc.org/resources/votingplugin.15358/), place dans `plugins/` et redemarre.

Configuration : `plugins/VotingPlugin/VoteSites.yml`

```yaml
VoteSites:
  PlanetMinecraft:
    Enabled: true
    ServiceSite: 'PlanetMinecraft.com'
    VoteURL: 'https://www.planetminecraft.com/server/cantale/vote/'
    VoteDelay: 24
    Rewards:
      Messages:
        Player: '&aMerci d''avoir vote ! +100 Cantox'
      Commands:
        Console:
          - 'cantox add %player% 100'
          - 'give %player% diamond 1'
```

### 4.2 Integration directe dans CANTALE

Ecoute l'event de NuVotifier dans ton plugin :

```java
package fr.cantale.plugin.vote;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class VoteListener implements Listener {

    private final EconomyManager economyManager;

    public VoteListener(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @EventHandler
    public void onVote(VotifierEvent event) {
        Vote vote = event.getVote();
        String playerName = vote.getUsername();
        String service = vote.getServiceName();

        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null || !player.isOnline()) return;

        economyManager.addCantox(player.getUniqueId(), 100);
        player.sendMessage("§aMerci d'avoir vote sur §e" + service + "§a! §6+100 Cantox");
    }
}
```

Enregistre dans `CantalePlugin.java` :
```java
getServer().getPluginManager().registerEvents(
    new VoteListener(economyManager), this);
```

Et ajoute dans `plugin.yml` :
```yaml
softdepend: [NuVotifier]
```

### 4.3 Recompenses par palier

| Votes/jour | Bonus Cantox | Bonus |
|-----------|-------------|-------|
| 1 vote | +100 | - |
| 3 votes | +150 | 1x Cle de vote |
| 5 votes | +250 | 1x Diamant |
| 7 votes | +500 | 1x Cantox Bonus Crate |

---

## 5. Verification & Tests

### 5.1 Tester NuVotifier

Dans la console du serveur :
```
votifier test NoobPlayer PlanetMinecraft.com
```

Tu dois voir :
```
[Votifier] Received vote from NoobPlayer on PlanetMinecraft.com
```

### 5.2 Tester via un site

1. Va sur un site ou tu as configure le serveur
2. Clique Vote, entre ton pseudo
3. Verifie dans la console que le vote est recu
4. Verifie que le joueur recoit sa recompense

### 5.3 Commandes utiles VotingPlugin

```
/vote - Ouvre le menu de vote
/votetotal <joueur> - Votes totaux
/votetop - Classement
/votereward - Donne une recompense manuellement
```

---

## 6. Astuces pour maximiser les votes

### 6.1 Incentives en jeu
- **Leaderboard vote** : Affiche le top votteurs
- **Recompenses exclusives** : Items custom, grades temporaires
- **Streak bonus** : +10% Cantox par jour consecutif
- **Milestone** : 50 votes = grade VIP 1 jour

### 6.2 Discord
- Poste automatiquement quand quelqu'un vote (webhook)
- Ping `@everyone` rappel vote (1x/jour max)
- Salon dedie `#votes`

### 6.3 Site web
- Page `/vote` avec liens directs vers tous les sites
- Compteur de votes du mois
- Bouton "Copier pseudo" pour faciliter le vote

### 6.4 Rappels auto
```
[VoteReminder] Il te reste 3 sites sur lesquels voter aujourd'hui !
```

Envoyer toutes les 6h si le joueur n'a pas vote sur tous les sites.

---

## Resume des ports a ouvrir

| Port | Protocole | Description |
|------|-----------|-------------|
| 25565 | TCP | Connexion Minecraft |
| 8192 | TCP | NuVotifier (reception votes) |

---

## Ressources

- [NuVotifier GitHub](https://github.com/NuVotifier/NuVotifier)
- [VotingPlugin Spigot](https://www.spigotmc.org/resources/votingplugin.15358/)
- [Test serveur online](https://mcsrvstat.us/)
- [Planet Minecraft](https://www.planetminecraft.com/)
- [Minecraft-Server.net](https://minecraft-server.net/)
- [Minecraft-MP](https://minecraft-mp.com/)
