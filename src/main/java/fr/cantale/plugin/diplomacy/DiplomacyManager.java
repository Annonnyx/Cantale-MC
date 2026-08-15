package fr.cantale.plugin.diplomacy;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.economy.BankManager;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Diplomatie des factions : alliances (cosmétiques) et guerres.
 *
 * Guerre = déclaration MUTUELLE : la première faction qui fait {@code /f war} enregistre
 * une déclaration « en attente » (persistée, expiration configurable
 * {@code factions.war.declaration-expiry-minutes}) ; la guerre ne démarre que lorsque
 * l'autre faction déclare à son tour. La paix ({@code /f peace}) suit la même mécanique
 * mutuelle ; la capitulation ({@code /f surrender}) est unilatérale avec réparations.
 *
 * Persistance : table {@code diplomacy} (réécriture complète à chaque mutation, comme
 * historiquement). Types de lignes : {@code alliance} / {@code war} (permanents,
 * {@code expires_at = 0}), {@code war_pending} / {@code peace_pending} (directionnels,
 * avec {@code expires_at}). Les lignes en attente expirées sont purgées au chargement.
 */
public class DiplomacyManager {

    /** Résultat d'une demande de déclaration de guerre ({@code /f war}). */
    public enum WarDeclareResult {
        /** Déclaration enregistrée : en attente de la déclaration adverse. */
        PENDING,
        /** Déclaration réciproque trouvée : la guerre démarre. */
        STARTED,
        ALREADY_AT_WAR,
        ALREADY_DECLARED,
        INVALID
    }

    /** Résultat d'une proposition de paix ({@code /f peace}). */
    public enum PeaceResult {
        /** Proposition enregistrée : en attente de la proposition adverse. */
        PENDING,
        /** Proposition réciproque trouvée : la guerre prend fin. */
        PEACE,
        NOT_AT_WAR,
        ALREADY_PROPOSED,
        INVALID
    }

    private final CantalePlugin plugin;
    private final FactionManager factionManager;
    private final Map<Integer, Set<Integer>> alliances; // factionId -> Set<alliedFactionIds>
    private final Map<Integer, Set<Integer>> wars; // factionId -> Set<enemyFactionIds>
    /** Déclarations de guerre en attente : déclarant → (cible → expiration epoch s). */
    private final Map<Integer, Map<Integer, Long>> pendingWarDeclarations;
    /** Propositions de paix en attente : proposeur → (cible → expiration epoch s). */
    private final Map<Integer, Map<Integer, Long>> pendingPeaceProposals;

    public DiplomacyManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
        this.alliances = new ConcurrentHashMap<>();
        this.wars = new ConcurrentHashMap<>();
        this.pendingWarDeclarations = new ConcurrentHashMap<>();
        this.pendingPeaceProposals = new ConcurrentHashMap<>();
    }

    /**
     * Propose une alliance à une faction
     */
    public boolean proposeAlliance(int factionId, int targetFactionId) {
        if (factionId == targetFactionId) {
            return false;
        }

        Faction faction = factionManager.getFaction(factionId);
        Faction target = factionManager.getFaction(targetFactionId);

        if (faction == null || target == null) {
            return false;
        }

        if (isAllied(factionId, targetFactionId)) {
            return false;
        }

        // Pour l'instant, alliance directe (cosmétique)
        // Plus tard : système de proposition avec acceptation
        createAlliance(factionId, targetFactionId);
        return true;
    }

    /**
     * Crée une alliance entre deux factions
     */
    private void createAlliance(int factionId1, int factionId2) {
        alliances.computeIfAbsent(factionId1, k -> ConcurrentHashMap.newKeySet()).add(factionId2);
        alliances.computeIfAbsent(factionId2, k -> ConcurrentHashMap.newKeySet()).add(factionId1);

        // Annuler la guerre et les déclarations/propositions en attente entre les deux factions
        endWar(factionId1, factionId2);
        clearPendingBetween(factionId1, factionId2);

        saveDiplomacy();
    }

    /**
     * Déclare la guerre à une faction (mécanique mutuelle).
     * <ul>
     *   <li>Première déclaration : enregistrée « en attente » avec expiration ; les deux
     *       factions sont notifiées.</li>
     *   <li>Déclaration réciproque déjà en attente (non expirée) : la guerre démarre —
     *       l'alliance éventuelle est rompue à ce moment-là.</li>
     * </ul>
     */
    public synchronized WarDeclareResult declareWar(int factionId, int targetFactionId) {
        if (factionId == targetFactionId) {
            return WarDeclareResult.INVALID;
        }

        Faction faction = factionManager.getFaction(factionId);
        Faction target = factionManager.getFaction(targetFactionId);
        if (faction == null || target == null) {
            return WarDeclareResult.INVALID;
        }

        if (isAtWar(factionId, targetFactionId)) {
            return WarDeclareResult.ALREADY_AT_WAR;
        }

        long now = System.currentTimeMillis() / 1000;
        purgeExpiredPending(now);

        // Déclaration adverse en attente → la guerre démarre
        Long reciprocalExpiry = getPending(pendingWarDeclarations, targetFactionId, factionId);
        if (reciprocalExpiry != null) {
            removePending(pendingWarDeclarations, targetFactionId, factionId);
            endAlliance(factionId, targetFactionId);
            wars.computeIfAbsent(factionId, k -> ConcurrentHashMap.newKeySet()).add(targetFactionId);
            wars.computeIfAbsent(targetFactionId, k -> ConcurrentHashMap.newKeySet()).add(factionId);
            saveDiplomacy();
            announceWarStarted(faction, target);
            return WarDeclareResult.STARTED;
        }

        if (getPending(pendingWarDeclarations, factionId, targetFactionId) != null) {
            return WarDeclareResult.ALREADY_DECLARED;
        }

        long expiresAt = now + plugin.getConfigManager().getWarDeclarationExpiryMinutes() * 60L;
        pendingWarDeclarations.computeIfAbsent(factionId, k -> new ConcurrentHashMap<>())
                .put(targetFactionId, expiresAt);
        saveDiplomacy();

        int expiryMinutes = plugin.getConfigManager().getWarDeclarationExpiryMinutes();
        notifyFactionOnline(faction,
                "§eDéclaration de guerre envoyée à §c" + target.getName()
                        + "§e. La guerre démarre si " + target.getName() + " déclare aussi (§7/f war "
                        + faction.getName() + "§e) — expiration dans §6" + expiryMinutes + " min§e.");
        notifyFactionOnline(target,
                "§c" + faction.getName() + " §ea déclaré la guerre à votre faction ! Faites §7/f war "
                        + faction.getName() + " §epour accepter la guerre (expiration dans §6"
                        + expiryMinutes + " min§e).");
        return WarDeclareResult.PENDING;
    }

    /**
     * Propose la paix à une faction en guerre (mécanique mutuelle, même expiration
     * que les déclarations de guerre). La guerre ne prend fin que lorsque les deux
     * factions ont fait {@code /f peace}.
     */
    public synchronized PeaceResult proposePeace(int factionId, int targetFactionId) {
        if (factionId == targetFactionId) {
            return PeaceResult.INVALID;
        }

        Faction faction = factionManager.getFaction(factionId);
        Faction target = factionManager.getFaction(targetFactionId);
        if (faction == null || target == null) {
            return PeaceResult.INVALID;
        }

        if (!isAtWar(factionId, targetFactionId)) {
            return PeaceResult.NOT_AT_WAR;
        }

        long now = System.currentTimeMillis() / 1000;
        purgeExpiredPending(now);

        // Proposition adverse en attente → la paix est conclue
        Long reciprocalExpiry = getPending(pendingPeaceProposals, targetFactionId, factionId);
        if (reciprocalExpiry != null) {
            removePending(pendingPeaceProposals, targetFactionId, factionId);
            endWarInternal(factionId, targetFactionId);
            saveDiplomacy();
            announceWarEnded(faction, target,
                    "§a[Paix] §e" + faction.getName() + " §7et §e" + target.getName()
                            + " §7ont signé la §apaix§7.",
                    "Paix conclue",
                    "**" + faction.getName() + "** et **" + target.getName() + "** ont signé la paix.");
            return PeaceResult.PEACE;
        }

        if (getPending(pendingPeaceProposals, factionId, targetFactionId) != null) {
            return PeaceResult.ALREADY_PROPOSED;
        }

        long expiresAt = now + plugin.getConfigManager().getWarDeclarationExpiryMinutes() * 60L;
        pendingPeaceProposals.computeIfAbsent(factionId, k -> new ConcurrentHashMap<>())
                .put(targetFactionId, expiresAt);
        saveDiplomacy();

        int expiryMinutes = plugin.getConfigManager().getWarDeclarationExpiryMinutes();
        notifyFactionOnline(faction,
                "§eProposition de paix envoyée à §c" + target.getName() + "§e. La guerre prend fin si "
                        + target.getName() + " propose aussi la paix (§7/f peace " + faction.getName()
                        + "§e) — expiration dans §6" + expiryMinutes + " min§e.");
        notifyFactionOnline(target,
                "§e" + faction.getName() + " §apropose la paix§e. Faites §7/f peace " + faction.getName()
                        + " §epour mettre fin à la guerre (expiration dans §6" + expiryMinutes + " min§e).");
        return PeaceResult.PENDING;
    }

    /**
     * Capitulation unilatérale ({@code /f surrender}) : fin immédiate de la guerre,
     * annonce publique de défaite et réparations Cantox banque → banque
     * ({@code factions.war.surrender-reparations-cantox}, 0 = désactivé ; transfert
     * partiel si la banque du perdant est insuffisante).
     *
     * @return message d'erreur FR, ou {@code null} si la capitulation a eu lieu
     */
    public synchronized String surrender(int factionId, int targetFactionId) {
        if (factionId == targetFactionId) {
            return "Tu ne peux pas capituler face à ta propre faction.";
        }
        Faction loser = factionManager.getFaction(factionId);
        Faction winner = factionManager.getFaction(targetFactionId);
        if (loser == null || winner == null) {
            return "Faction introuvable.";
        }
        if (!isAtWar(factionId, targetFactionId)) {
            return "Vous n'êtes pas en guerre contre " + winner.getName() + ".";
        }

        endWarInternal(factionId, targetFactionId);

        // Réparations banque → banque (transfert partiel si fonds insuffisants)
        double configured = plugin.getConfigManager().getWarSurrenderReparationsCantox();
        double transferred = 0;
        boolean partial = false;
        if (configured > 0) {
            double available = loser.getBalance();
            transferred = Math.min(configured, Math.max(0, available));
            partial = transferred < configured;
            if (transferred > 0) {
                loser.setBalance(loser.getBalance() - transferred);
                winner.setBalance(winner.getBalance() + transferred);
                factionManager.saveFaction(loser);
                factionManager.saveFaction(winner);
                BankManager bankManager = plugin.getBankManager();
                if (bankManager != null) {
                    bankManager.logTransaction(loser.getId(), null, BankManager.ACTION_WAR_REPARATION,
                            transferred, loser.getBalance(), "Réparations de guerre vers " + winner.getName());
                    bankManager.logTransaction(winner.getId(), null, BankManager.ACTION_WAR_REPARATION,
                            transferred, winner.getBalance(), "Réparations de guerre de " + loser.getName());
                }
            }
        }
        saveDiplomacy();

        String mcMessage = "§4[Capitulation] §e" + loser.getName() + " §ccapitule face à §e"
                + winner.getName() + " §c!";
        if (configured > 0) {
            mcMessage += " §7Réparations : §6" + String.format("%.0f", transferred) + " Cantox§7"
                    + (partial ? " (banque insuffisante — transfert partiel)" : "") + ".";
        }
        Bukkit.broadcastMessage(mcMessage);

        String discordDesc = "**" + loser.getName() + "** capitule face à **" + winner.getName() + "**.";
        if (configured > 0) {
            discordDesc += "\nRéparations : " + String.format("%.0f", transferred) + " Cantox"
                    + (partial ? " (transfert partiel, banque insuffisante)" : "");
        }
        sendWarEmbed(java.awt.Color.ORANGE, "🏳️ Capitulation", discordDesc);
        return null;
    }

    /**
     * Fin de guerre forcée par le staff ({@code /f warend}). Log console d'audit.
     *
     * @return {@code true} si une guerre existait et a été terminée
     */
    public synchronized boolean forceEndWar(int factionId1, int factionId2, String staffName) {
        if (!isAtWar(factionId1, factionId2)) {
            return false;
        }
        Faction faction1 = factionManager.getFaction(factionId1);
        Faction faction2 = factionManager.getFaction(factionId2);
        endWarInternal(factionId1, factionId2);
        saveDiplomacy();

        String name1 = faction1 != null ? faction1.getName() : "#" + factionId1;
        String name2 = faction2 != null ? faction2.getName() : "#" + factionId2;
        plugin.getLogger().info("[Guerre] " + staffName + " a forcé la fin de la guerre entre "
                + name1 + " et " + name2 + ".");
        Bukkit.broadcastMessage("§8[§6Cantale§8] §7La guerre entre §e" + name1 + " §7et §e" + name2
                + " §7a été terminée par le staff.");
        sendWarEmbed(java.awt.Color.GRAY, "🕊️ Fin de guerre (staff)",
                "La guerre entre **" + name1 + "** et **" + name2 + "** a été terminée par le staff ("
                        + staffName + ").");
        return true;
    }

    /**
     * Termine une alliance
     */
    public boolean endAlliance(int factionId1, int factionId2) {
        if (!isAllied(factionId1, factionId2)) {
            return false;
        }

        Set<Integer> allies1 = alliances.get(factionId1);
        if (allies1 != null) allies1.remove(factionId2);
        Set<Integer> allies2 = alliances.get(factionId2);
        if (allies2 != null) allies2.remove(factionId1);

        saveDiplomacy();
        return true;
    }

    /**
     * Termine une guerre (paix) — bas niveau, sans annonce.
     * Utilisé par l'alliance ; les chemins joueurs passent par proposePeace/surrender.
     */
    public boolean endWar(int factionId1, int factionId2) {
        if (!isAtWar(factionId1, factionId2)) {
            return false;
        }
        endWarInternal(factionId1, factionId2);
        saveDiplomacy();
        return true;
    }

    /** Retrait interne de la guerre + purge des déclarations/propositions en attente entre les deux. */
    private void endWarInternal(int factionId1, int factionId2) {
        Set<Integer> enemies1 = wars.get(factionId1);
        if (enemies1 != null) enemies1.remove(factionId2);
        Set<Integer> enemies2 = wars.get(factionId2);
        if (enemies2 != null) enemies2.remove(factionId1);
        clearPendingBetween(factionId1, factionId2);
    }

    /** Supprime déclarations de guerre et propositions de paix en attente entre deux factions. */
    private void clearPendingBetween(int factionId1, int factionId2) {
        removePending(pendingWarDeclarations, factionId1, factionId2);
        removePending(pendingWarDeclarations, factionId2, factionId1);
        removePending(pendingPeaceProposals, factionId1, factionId2);
        removePending(pendingPeaceProposals, factionId2, factionId1);
    }

    /**
     * Vérifie si deux factions sont alliées
     */
    public boolean isAllied(int factionId1, int factionId2) {
        Set<Integer> allies = alliances.get(factionId1);
        return allies != null && allies.contains(factionId2);
    }

    /**
     * Vérifie si deux factions sont en guerre
     */
    public boolean isAtWar(int factionId1, int factionId2) {
        Set<Integer> enemies = wars.get(factionId1);
        return enemies != null && enemies.contains(factionId2);
    }

    /** {@code true} si la faction est engagée dans au moins une guerre. */
    public boolean hasAnyWar(int factionId) {
        Set<Integer> enemies = wars.get(factionId);
        return enemies != null && !enemies.isEmpty();
    }

    /** Cible de la déclaration de guerre en attente émise par cette faction, ou {@code null}. */
    public Integer getPendingWarDeclarationTarget(int factionId) {
        purgeExpiredPending(System.currentTimeMillis() / 1000);
        Map<Integer, Long> outgoing = pendingWarDeclarations.get(factionId);
        if (outgoing == null || outgoing.isEmpty()) return null;
        return outgoing.keySet().iterator().next();
    }

    /**
     * Liste les alliances d'une faction
     */
    public String listAlliances(int factionId) {
        Set<Integer> allies = alliances.get(factionId);
        if (allies == null || allies.isEmpty()) {
            return "&7Aucune alliance.";
        }

        StringBuilder sb = new StringBuilder();
        for (int allyId : allies) {
            Faction ally = factionManager.getFaction(allyId);
            if (ally != null) {
                sb.append(ChatColor.translateAlternateColorCodes('&',
                    "&a✓ " + ally.getName() + " &7[" + ally.getTag() + "]\n"));
            }
        }
        return sb.toString();
    }

    /**
     * Liste les guerres d'une faction
     */
    public String listWars(int factionId) {
        Set<Integer> enemies = wars.get(factionId);
        if (enemies == null || enemies.isEmpty()) {
            return "&7Aucune guerre.";
        }

        StringBuilder sb = new StringBuilder();
        for (int enemyId : enemies) {
            Faction enemy = factionManager.getFaction(enemyId);
            if (enemy != null) {
                sb.append(ChatColor.translateAlternateColorCodes('&',
                    "&c✗ " + enemy.getName() + " &7[" + enemy.getTag() + "]\n"));
            }
        }
        return sb.toString();
    }

    /** Lecture d'une entrée en attente (déclarant → cible), {@code null} si absente. */
    private Long getPending(Map<Integer, Map<Integer, Long>> pending, int fromFactionId, int toFactionId) {
        Map<Integer, Long> outgoing = pending.get(fromFactionId);
        return outgoing == null ? null : outgoing.get(toFactionId);
    }

    private void removePending(Map<Integer, Map<Integer, Long>> pending, int fromFactionId, int toFactionId) {
        Map<Integer, Long> outgoing = pending.get(fromFactionId);
        if (outgoing != null) {
            outgoing.remove(toFactionId);
            if (outgoing.isEmpty()) {
                pending.remove(fromFactionId, outgoing);
            }
        }
    }

    /** Purge en mémoire les déclarations/propositions expirées (elles sont aussi filtrées au chargement). */
    private void purgeExpiredPending(long nowSeconds) {
        purgeExpiredPending(pendingWarDeclarations, nowSeconds);
        purgeExpiredPending(pendingPeaceProposals, nowSeconds);
    }

    private void purgeExpiredPending(Map<Integer, Map<Integer, Long>> pending, long nowSeconds) {
        for (Map.Entry<Integer, Map<Integer, Long>> entry : pending.entrySet()) {
            entry.getValue().values().removeIf(expiresAt -> expiresAt <= nowSeconds);
            if (entry.getValue().isEmpty()) {
                pending.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private void announceWarStarted(Faction faction, Faction target) {
        Bukkit.broadcastMessage("§4[Guerre] §e" + faction.getName() + " §cet §e" + target.getName()
                + " §csont désormais en §4guerre§c ! Les protections de leurs claims tombent entre eux.");
        sendWarEmbed(java.awt.Color.RED, "⚔️ Guerre déclarée",
                "**" + faction.getName() + "** et **" + target.getName() + "** sont en guerre !");
    }

    private void announceWarEnded(Faction faction, Faction target, String mcMessage,
                                  String discordTitle, String discordDescription) {
        Bukkit.broadcastMessage(mcMessage);
        sendWarEmbed(java.awt.Color.GREEN, discordTitle, discordDescription);
    }

    /** Embed Discord sur le salon factions MC (même canal que les créations de factions). */
    private void sendWarEmbed(java.awt.Color color, String title, String description) {
        if (plugin.getDiscordBot() == null || !plugin.getDiscordBot().isConnected()) return;
        long channelId = plugin.getDiscordBot().getConfig().getMcFactionsChannelId();
        if (channelId == 0L) return;
        plugin.getDiscordBot().sendEmbedToChannel(channelId, color, title, description);
    }

    /** Message à tous les membres en ligne d'une faction. */
    private void notifyFactionOnline(Faction faction, String message) {
        String formatted = "§8[§6Cantale§8] " + message;
        for (UUID memberUuid : faction.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(formatted);
            }
        }
    }

    /**
     * Sauvegarde la diplomatie dans la base de données
     */
    private void saveDiplomacy() {
        // Toute mutation (guerre, paix, alliance) change les couleurs de relation des claims :
        // resync des cartes des clients du mod Cantale
        if (plugin.getClientMapBridge() != null) {
            plugin.getClientMapBridge().onDiplomacyChanged();
        }
        // Supprimer les anciennes relations
        try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM diplomacy");
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Erreur saveDiplomacy (delete) : " + e.getMessage());
        }

        String sql = "INSERT INTO diplomacy (faction_id, target_faction_id, type, expires_at) VALUES (?, ?, ?, ?)";
        try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Alliances et guerres : permanentes (expires_at = 0), une seule direction pour éviter les doublons
            for (Map.Entry<Integer, Set<Integer>> entry : alliances.entrySet()) {
                int factionId = entry.getKey();
                for (int allyId : entry.getValue()) {
                    if (factionId < allyId) {
                        insertDiplomacyRow(stmt, factionId, allyId, "alliance", 0);
                    }
                }
            }
            for (Map.Entry<Integer, Set<Integer>> entry : wars.entrySet()) {
                int factionId = entry.getKey();
                for (int enemyId : entry.getValue()) {
                    if (factionId < enemyId) {
                        insertDiplomacyRow(stmt, factionId, enemyId, "war", 0);
                    }
                }
            }

            // Déclarations / propositions en attente : directionnelles, avec expiration
            for (Map.Entry<Integer, Map<Integer, Long>> entry : pendingWarDeclarations.entrySet()) {
                for (Map.Entry<Integer, Long> pending : entry.getValue().entrySet()) {
                    insertDiplomacyRow(stmt, entry.getKey(), pending.getKey(), "war_pending", pending.getValue());
                }
            }
            for (Map.Entry<Integer, Map<Integer, Long>> entry : pendingPeaceProposals.entrySet()) {
                for (Map.Entry<Integer, Long> pending : entry.getValue().entrySet()) {
                    insertDiplomacyRow(stmt, entry.getKey(), pending.getKey(), "peace_pending", pending.getValue());
                }
            }
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Erreur saveDiplomacy : " + e.getMessage());
        }
    }

    private void insertDiplomacyRow(java.sql.PreparedStatement stmt, int factionId, int targetId,
                                    String type, long expiresAt) throws java.sql.SQLException {
        stmt.setInt(1, factionId);
        stmt.setInt(2, targetId);
        stmt.setString(3, type);
        stmt.setLong(4, expiresAt);
        stmt.executeUpdate();
    }

    /**
     * Charge la diplomatie depuis la base de données.
     * Les déclarations/propositions en attente déjà expirées sont ignorées et purgées de la table.
     */
    public void loadDiplomacy() {
        alliances.clear();
        wars.clear();
        pendingWarDeclarations.clear();
        pendingPeaceProposals.clear();

        long now = System.currentTimeMillis() / 1000;
        boolean purgedExpired = false;
        String sql = "SELECT faction_id, target_faction_id, type, expires_at FROM diplomacy";
        try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int factionId = rs.getInt("faction_id");
                int targetId = rs.getInt("target_faction_id");
                String type = rs.getString("type");
                long expiresAt = rs.getLong("expires_at");

                if ("alliance".equalsIgnoreCase(type)) {
                    alliances.computeIfAbsent(factionId, k -> ConcurrentHashMap.newKeySet()).add(targetId);
                    alliances.computeIfAbsent(targetId, k -> ConcurrentHashMap.newKeySet()).add(factionId);
                } else if ("war".equalsIgnoreCase(type)) {
                    wars.computeIfAbsent(factionId, k -> ConcurrentHashMap.newKeySet()).add(targetId);
                    wars.computeIfAbsent(targetId, k -> ConcurrentHashMap.newKeySet()).add(factionId);
                } else if ("war_pending".equalsIgnoreCase(type) || "peace_pending".equalsIgnoreCase(type)) {
                    // En attente expirée au chargement → purgée (pas restaurée)
                    if (expiresAt <= now) {
                        purgedExpired = true;
                        continue;
                    }
                    Map<Integer, Map<Integer, Long>> targetMap =
                            "war_pending".equalsIgnoreCase(type) ? pendingWarDeclarations : pendingPeaceProposals;
                    targetMap.computeIfAbsent(factionId, k -> new ConcurrentHashMap<>())
                            .put(targetId, expiresAt);
                }
            }

            plugin.getLogger().info("Diplomatie chargée : " + alliances.size() + " alliances, "
                    + wars.size() + " guerres, " + pendingWarDeclarations.size()
                    + " déclaration(s) de guerre en attente, " + pendingPeaceProposals.size()
                    + " proposition(s) de paix en attente.");
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Erreur loadDiplomacy : " + e.getMessage());
        }

        if (purgedExpired) {
            // Réécriture complète : retire physiquement les lignes expirées
            saveDiplomacy();
            plugin.getLogger().info("Diplomatie : déclarations/propositions expirées purgées au chargement.");
        }
    }
}
