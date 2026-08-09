package fr.cantale.plugin.vote;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.crate.CrateManager;
import fr.cantale.plugin.crate.CrateType;
import fr.cantale.plugin.privatechest.ChestType;
import fr.cantale.plugin.privatechest.PrivateChestDAO;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Récompenses de vote : uniquement des Cadeaux du Roi (CrateType.VOTE).
 * La quantité (1, 2 ou 3) dépend du cooldown du site de vote.
 * Le butin rare/épique/etc. s'obtient en ouvrant le Cadeau du Roi, pas au vote.
 */
public class VoteRewardManager {

    private final CantalePlugin plugin;

    public VoteRewardManager(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Construit la récompense de vote (Cadeau(x) du Roi).
     * @param streakDays ignoré (conservé pour compatibilité d'appel)
     * @param site       site résolu ; si null → 1 Cadeau du Roi par défaut
     */
    public VoteReward roll(int streakDays, VoteSite site) {
        int count = site != null ? site.crateCount() : 1;
        return buildKeyReward(count);
    }

    /** @deprecated streak n'influence plus les récompenses de vote */
    public VoteReward roll(int streakDays) {
        return roll(streakDays, null);
    }

    /**
     * Construit une récompense de {@code count} Cadeau(x) du Roi.
     */
    public VoteReward buildKeyReward(int count) {
        int safeCount = Math.max(1, count);
        CrateManager crateManager = plugin.getCrateManager();
        List<ItemStack> items = new ArrayList<>();
        if (crateManager != null) {
            for (int i = 0; i < safeCount; i++) {
                items.add(crateManager.createCrateItem(CrateType.VOTE));
            }
        }
        String summary = formatKeySummary(safeCount);
        return new VoteReward(VoteReward.Tier.COMMUN, 0, items, summary);
    }

    public static String formatKeySummary(int count) {
        if (count <= 1) return "1× Cadeau du Roi";
        return count + "× Cadeau du Roi";
    }

    /**
     * Délivre une récompense au joueur (en ligne uniquement).
     * Ordre : inventaire → coffre privé → drop au sol.
     */
    public void deliverTo(Player player, VoteReward reward) {
        if (reward.getCantox() > 0) {
            plugin.getEconomyManager().addBalance(player.getUniqueId(), reward.getCantox());
        }

        for (ItemStack item : reward.getItems()) {
            if (item == null) continue;
            var leftoverInv = player.getInventory().addItem(item.clone());
            if (leftoverInv.isEmpty()) continue;

            boolean pcSuccess = false;
            try {
                PrivateChestDAO dao = new PrivateChestDAO(plugin);
                var snapshot = dao.loadChest(player.getUniqueId(), ChestType.PC1, 27);
                org.bukkit.inventory.Inventory pcInventory = org.bukkit.Bukkit.createInventory(null, snapshot.size(), ChestType.PC1.getTitle());
                if (snapshot.contents() != null && snapshot.contents().length == snapshot.size()) {
                    pcInventory.setContents(snapshot.contents());
                }
                var leftoverPc = pcInventory.addItem(leftoverInv.values().toArray(new ItemStack[0]));
                if (leftoverPc.isEmpty()) {
                    dao.saveChest(player.getUniqueId(), ChestType.PC1, pcInventory.getContents());
                    pcSuccess = true;
                    player.sendMessage("§8[§6Vote§8] §7Item envoyé dans votre §eCoffre Privé §7(faute de place).");
                } else {
                    dao.saveChest(player.getUniqueId(), ChestType.PC1, pcInventory.getContents());
                    leftoverInv = leftoverPc;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Vote] Erreur coffre privé pour " + player.getName() + ": " + e.getMessage());
            }

            if (!pcSuccess) {
                for (ItemStack drop : leftoverInv.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }
    }
}
