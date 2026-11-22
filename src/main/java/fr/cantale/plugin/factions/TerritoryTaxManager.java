package fr.cantale.plugin.factions;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.FactionBalanceRepository;
import fr.cantale.plugin.database.FactionDAO;
import fr.cantale.plugin.database.FortifiedChunkDAO;
import fr.cantale.plugin.skills.Skill;
import fr.cantale.plugin.skills.SkillFormulas;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.List;
import java.util.UUID;

public class TerritoryTaxManager {

    private final CantalePlugin plugin;
    private final FactionManager factionManager;
    private final FactionDAO factionDAO;
    private final FortifiedChunkDAO fortifiedChunkDAO;

    // Taxe par claim (en Cantox) - uniquement pour les factions avec plus de 15 claims
    private static final double TAX_PER_CLAIM = 5.0;
    private static final int MIN_CLAIMS_FOR_TAX = 15;
    // Surcoût par chunk fortifié (rune) — appliqué à toutes les factions, indépendamment de MIN_CLAIMS_FOR_TAX
    private static final double TAX_PER_FORTIFIED_CHUNK = 50.0;

    public TerritoryTaxManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
        this.factionDAO = new FactionDAO(plugin);
        this.fortifiedChunkDAO = plugin.getFortifiedChunkDAO();
    }

    public void collectTaxes() {
        List<Faction> factions = factionManager.getAllFactions();

        java.util.Map<Integer, Integer> fortifiedByFaction = fortifiedChunkDAO.countAllByFaction();

        for (Faction faction : factions) {
            int claimCount = factionDAO.getClaimCount(faction.getId());
            int fortifiedCount = fortifiedByFaction.getOrDefault(faction.getId(), 0);

            // Taxe de base : seulement si > 15 claims
            double claimTax = claimCount > MIN_CLAIMS_FOR_TAX ? claimCount * TAX_PER_CLAIM : 0.0;
            // Taxe runes : toujours appliquée, même pour les petites factions
            double fortifiedTax = fortifiedCount * TAX_PER_FORTIFIED_CHUNK;
            double rawTax = claimTax + fortifiedTax;
            int negociants = plugin.getSkillService() == null
                    ? 0
                    : plugin.getSkillService().count(faction, Skill.NEGOCIANT);
            double totalTax = SkillFormulas.discountedAmount(rawTax, negociants);

            if (totalTax <= 0) continue; // Rien à prélever

            double currentBalance = faction.getBalance();
            boolean paid = false;
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                if (conn != null && FactionBalanceRepository.applyDelta(conn, faction.getId(), -totalTax)) {
                    faction.setBalance(FactionBalanceRepository.readBalance(conn, faction.getId()));
                    paid = true;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Taxe non prélevée pour " + faction.getName() + " : " + e.getMessage());
            }

            if (paid) {
                plugin.getLogger().info("Taxe prélevée pour " + faction.getName() + ": " + totalTax
                        + " Cantox (" + claimCount + " claims + " + fortifiedCount + " runes)");

                UUID leaderUuid = faction.getLeaderUuid();
                if (leaderUuid != null) {
                    org.bukkit.entity.Player leader = plugin.getServer().getPlayer(leaderUuid);
                    if (leader != null && leader.isOnline()) {
                        leader.sendMessage("§6[Taxe] §7Taxe de territoire : §e" + totalTax + " Cantox");
                        leader.sendMessage("§6[Taxe] §7  • §e" + claimCount + " §7claims = §e" + claimTax + " Cantox");
                        leader.sendMessage("§6[Taxe] §7  • §e" + fortifiedCount + " §7runes = §e" + fortifiedTax + " Cantox");
                    }
                }
            } else {
                // Pas assez d'argent : retirer d'abord des chunks fortifiés, puis claims
                plugin.getLogger().warning(faction.getName() + " n'a pas assez de Cantox pour la taxe (" + currentBalance + "/" + totalTax + ").");

                if (fortifiedCount > 0) {
                    plugin.getLogger().warning("  → Suppression de toutes les runes de " + faction.getName() + ".");
                    fortifiedChunkDAO.removeAllForFaction(faction.getId());
                }

                if (claimTax > 0) {
                    int claimsToRemove = (int) Math.ceil((claimTax - currentBalance) / TAX_PER_CLAIM);
                    factionDAO.removeOldestClaims(faction.getId(), claimsToRemove);
                    plugin.getLogger().warning("  → " + claimsToRemove + " claims supprimés.");
                }

                UUID leaderUuid = faction.getLeaderUuid();
                if (leaderUuid != null) {
                    org.bukkit.entity.Player leader = plugin.getServer().getPlayer(leaderUuid);
                    if (leader != null && leader.isOnline()) {
                        leader.sendMessage("§c[Taxe] §7Votre faction n'a pas payé la taxe ! §eRunes & claims supprimés.");
                    }
                }
            }
        }
    }

    public double getTaxPerClaim() {
        return TAX_PER_CLAIM;
    }

    public double getTaxPerFortifiedChunk() {
        return TAX_PER_FORTIFIED_CHUNK;
    }

    public double calculateTax(int claimCount) {
        return claimCount * TAX_PER_CLAIM;
    }

    public double calculateTotalTax(int claimCount, int fortifiedCount) {
        double claimTax = claimCount > MIN_CLAIMS_FOR_TAX ? claimCount * TAX_PER_CLAIM : 0.0;
        return claimTax + (fortifiedCount * TAX_PER_FORTIFIED_CHUNK);
    }
}
