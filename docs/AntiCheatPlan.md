# Module AntiCheat Cantale — architecture (Grim + natif)

## Objectif
Détecter les comportements de triche de façon **fiable** (zéro faux ban) sur un
serveur PvP/faction, en combinant un moteur packet-based éprouvé (Grim) et un
module natif repositionné sur la valeur ajoutée Cantale.

## Architecture

```
Grim AC (externe, async, prédiction)          Module natif Cantale
┌──────────────────────────────┐             ┌────────────────────────────────┐
│ Simulation, Timer, NoFall,   │  FlagEvent  │ FastBreak (outils custom)      │
│ Reach, KillAura, Aim,        │────────────▶│ AutoClicker (seuil haut)       │
│ BadPackets, Jesus, NoSlow…   │  GrimBridge │ AutoArmor / ChestStealer /     │
└──────────────────────────────┘             │ FastUse (corrigés, rodage)     │
                                             │            │                   │
                                             │            ▼                   │
                                             │ TrustManager (agrégateur)      │
                                             │  • plafond 1 pénalité/tick     │
                                             │  • plafond X pts/min glissante │
                                             │            ▼                   │
                                             │ PunishmentManager progressif   │
                                             │  warn → kick (cooldown 5 min)  │
                                             │  → tempban sur récidive (opt.) │
                                             │  JAMAIS de ban auto à trust 0  │
                                             │            ▼                   │
                                             │ Alertes MC + Discord (#mod-logs)│
                                             │ + table anticheat_violations   │
                                             └────────────────────────────────┘
```

## Principes de fiabilité (anti faux positifs)

- **Compensation lag** : checks mouvement natifs suspendus si ping > 350 ms ou
  TPS < 18.5 (`anticheat.lag`). Timer compte uniquement les packets avec
  changement de position, seuil mis à l'échelle du TPS.
- **Exemption vélocité** : knockback / explosion / vélocité serveur → 500 ms
  de grâce pour Speed/Fly/Step/NoFall (`velocity-exempt-ms`).
- **NoFall** : détection par dégât de chute attendu (EntityDamageEvent FALL),
  jamais par lecture de `getFallDistance()` à l'atterrissage. Exemptions :
  eau (MLG), slime, lit, hay, powder snow, cobweb, échelles/lianes.
- **Cascade trust** : 1 seule pénalité par tick (la plus grosse), plafond de
  10 points/minute glissante.
- **Sanctions** : kick cooldown 5 min ; trust 0 = alerte staff prioritaire ;
  tempban auto uniquement si `ban-auto: true` ET ≥ 3 kicks/24 h.

## Répartition des checks

| Check | Moteur | État |
|---|---|---|
| Speed, Fly, NoFall, Step, Timer | Grim (natif désactivé) | fallback auto si Grim absent |
| Reach, KillAura, Aim, Rotation | Grim (natif désactivé) | fallback auto si Grim absent |
| Jesus, NoSlow, BadPackets, Sprint, Scaffold… | Grim uniquement | — |
| AutoClicker | Natif (complément, seuil haut) + Grim | actif |
| FastBreak / Nuker | **Natif uniquement** (connaît les outils Cantale) | actif |
| AutoArmor, ChestStealer, FastUse | Natif (corrigés, vanilla only) | inactif pendant rodage |
| Vol Chèvre / mécaniques custom | Natif (exemptions dans les checks) | — |

Le fallback est automatique : Grim absent au démarrage → les checks natifs
mouvement/combat/rotation sont réactivés à chaud (log « fallback »).

## Preuves et outillage staff

- Table `anticheat_violations` (uuid, nom, source NATIVE/GRIM, check, VL, trust,
  ping, TPS, détails, timestamp) — insertion asynchrone.
- `/ac info <joueur>` : moteur actif, trust, ping, TPS, kicks 24 h, violations
  session + 5 dernières violations BDD.
- `/ac toggle [check]` : toggle global ou par check (runtime, non persisté).
- `/ac reload`, `/ac alerts`, `/ac clear <joueur>`.
- Alertes Discord activées (`alerts.discord-enabled: true`, salon mod-logs).

## Installation & rodage

Voir [AntiCheat-Grim-Setup.md](AntiCheat-Grim-Setup.md) : installation des JARs,
config Grim log-only, rodage 3 semaines (log-only → setbacks → kicks → tempban
sur récidive), seuils finaux.
