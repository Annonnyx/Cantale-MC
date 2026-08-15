# Installation & rodage — Grim AC + Cantale AntiCheat

Ce guide couvre la mise en place serveur (Phase 0) et le rodage en 3 semaines
(Phase 4) de la refonte anti-cheat. Le code Cantale (bridge, fallback, preuves)
est déjà en place — cette partie est **manuelle** et se fait sur le serveur.

## 1. Installation (Phase 0)

Les JARs vérifiés pour Minecraft 1.21.11 (Paper) sont dans `server-plugins/` du repo :

| Plugin | Fichier | Version | Source |
|---|---|---|---|
| PacketEvents (dépendance de Grim) | `packetevents-spigot-2.13.0.jar` | 2.13.0 | Modrinth |
| Grim AC | `grimac-bukkit-2.3.74.jar` | 2.3.74 | Modrinth |

1. Copier les deux JARs dans `plugins/` du serveur.
2. Redémarrer (un simple reload ne suffit pas pour PacketEvents).
3. Les configs sont générées dans `plugins/GrimAC/` et `plugins/packetevents/`.
4. Le plugin Cantale détecte Grim automatiquement au démarrage :
   - `[AntiCheat] Grim détecté (x.y.z) — bridge actif` → OK, les doublons natifs
     (Speed/Fly/NoFall/Step/Timer/Reach/KillAura/Aim/Rotation) restent désactivés.
   - `[AntiCheat] … fallback : checks natifs … réactivés` → Grim absent, le natif
     reprend tout en charge automatiquement.

> Maintenir PacketEvents et Grim **à jour ensemble** lors des montées de version 1.21.x.

## 2. Grim en log-only (rodage)

Dans `plugins/GrimAC/punishments.yml`, ne garder que `[log]` (console) et `[alert]`
(staff en jeu). **Aucune commande kick/ban.** Exemple complet prêt à coller :

```yaml
Punishments:
  Simulation:
    remove-violations-after: 300
    checks: ["Simulation", "GroundSpoof", "Timer", "TimerLimit", "NoFall"]
    commands:
      - "20:20 [alert]"
      - "1:1 [log]"
  Knockback:
    remove-violations-after: 300
    checks: ["Knockback", "Explosion"]
    commands:
      - "5:5 [alert]"
      - "1:1 [log]"
  Post:
    remove-violations-after: 300
    checks: ["Post"]
    commands:
      - "20:20 [alert]"
      - "1:1 [log]"
  BadPackets:
    remove-violations-after: 300
    checks: ["BadPackets", "PacketOrder", "Crash"]
    commands:
      - "20:20 [alert]"
      - "1:1 [log]"
  Reach:
    remove-violations-after: 300
    checks: ["Reach"]
    commands:
      - "5:5 [alert]"
      - "1:1 [log]"
  Hitboxes:
    remove-violations-after: 300
    checks: ["Hitboxes"]
    commands:
      - "5:3 [alert]"
      - "1:1 [log]"
  Misc:
    remove-violations-after: 300
    checks: ["Vehicle", "NoSlow", "Sprint", "MultiActions", "Place", "Baritone", "Break", "TransactionOrder", "Elytra", "Chat", "Exploit"]
    commands:
      - "10:5 [alert]"
      - "1:1 [log]"
  Combat:
    remove-violations-after: 300
    checks: ["Interact", "Aim"]
    commands:
      - "20:40 [alert]"
      - "1:1 [log]"
  Autoclicker:
    remove-violations-after: 300
    checks: ["Autoclicker"]
    commands:
      - "20:40 [alert]"
      - "1:1 [log]"
  # FastBreak DÉSACTIVÉ côté Grim (le « ! » devant le nom désactive le check,
  # méthode officielle Grim). Grim ne connaît pas les outils custom Cantale
  # (vitesses de minage modifiées) → faux positifs constants. Le FastBreak
  # NATIF Cantale, lui, exempte les outils custom : il reste le seul à le gérer.
  Disabled:
    remove-violations-after: 300
    checks: ["!FastBreak"]
    commands:
      - "1:1 [log]"
```

> **Redémarrage requis** après modification de `punishments.yml` (le `!check`
> n'est pris en compte qu'au boot). Sans la ligne `!FastBreak`, les flags
> FastBreak de Grim remontent dans le bridge Cantale (alertes staff + Discord)
> même sans sanction — c'est le spam « détecte tout le temps ».

Le reste de la config Grim (`config.yml`) reste **par défaut** : les seuils Grim
sont production-ready, pas de tuning agressif.

Côté Cantale (`config.yml` section `anticheat`) : déjà configuré —
`grim-integration.enabled: true`, alertes Discord activées, sanctions natives
progressives avec `ban-auto: false`.

## 3. Rodage en 3 semaines (Phase 4)

| Semaine | Action | Critère de passage |
|---|---|---|
| 1 | Grim log-only + natif durci (état actuel). Surveiller les alertes Discord #mod-logs et `/ac info <joueur>`. | Faux positifs ≈ 0 sur population réelle |
| 2 | Activer les **setbacks** Grim (téléportation au dernier point valide, sans kick) : dans `config.yml` de Grim, vérifier que les checks mouvement ont le setback actif (défaut). | Aucun joueur légitime ne se plaint de téléportations intempestives |
| 3 | Activer les kicks natifs (déjà actifs via `auto-punish: true` + cooldown 5 min) et surveiller les récidives. Bans **toujours manuels**. | Kicks uniquement sur vrais cheats |
| Après | Activer le tempban auto sur récidive : `anticheat.punishments.ban-auto: true` (3 kicks/24 h → tempban 30 min). Jamais de ban instantané. | FP ≈ 0 validé pendant le rodage |

## 4. Seuils finaux documentés

Côté Cantale (`config.yml` → `anticheat`) :

| Paramètre | Valeur | Rôle |
|---|---|---|
| `trust.default` / `recovery-per-minute` | 100 / 1 | Score de départ, regen |
| `trust.warn-threshold` / `kick-threshold` | 50 / 25 | Paliers warn → kick |
| `punishments.kick-cooldown-minutes` | 5 | 1 kick max / joueur / 5 min |
| `punishments.max-trust-loss-per-minute` | 10 | Anti-cascade (plus grosse pénalité/tick) |
| `punishments.ban-auto` / `kicks-for-tempban` | false / 3 | Tempban auto = récidive uniquement |
| `lag.max-ping-ms` / `lag.min-tps` | 350 / 18.5 | Suspension des checks mouvement si dépassé |
| `lag.velocity-exempt-ms` | 500 | Grâce après knockback/explosion |
| `grim-integration.default-trust-penalty` | 2 | Pénalité par flag Grim (par check : simulation 3, timer 4, badpackets 3, killaura 5, reach 4, scaffold 3) |

## 5. Vérifications après installation

- [ ] `/plugins` liste `packetevents` et `GrimAC` en vert
- [ ] `punishments.yml` contient bien `checks: ["!FastBreak"]` → plus aucun flag FastBreak (le FastBreak natif Cantale, qui connaît les outils custom, reste actif)
- [ ] Log au démarrage : `Grim détecté … bridge actif`
- [ ] `/ac info <joueur>` affiche « Moteur : Grim détecté » + ping/TPS/violations BDD
- [ ] Une alerte Grim apparaît en jeu (staff) et dans #mod-logs Discord
- [ ] Table `anticheat_violations` remplie (via `/ac info` ou requête SQL)
- [ ] TPS stables (~20) avec Grim actif (async, impact faible)

## 6. Dépannage

- **Grim ne charge pas** → vérifier que PacketEvents est présent et à jour.
- **Double-flag Grim + natif** → ne devrait pas arriver (détection auto) ; vérifier
  que `grim-integration.enabled: true` et que le log « bridge actif » apparaît.
- **Grim désinstallé** → le natif repasse en mode complet automatiquement au
  prochain démarrage (log « fallback »).
- **Faux positif identifié** → `/ac toggle <check>` pour désactiver à chaud le
  check natif concerné, puis ajuster le seuil dans `config.yml`.
