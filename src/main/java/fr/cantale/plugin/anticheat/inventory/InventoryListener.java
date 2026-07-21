package fr.cantale.plugin.anticheat.inventory;

import fr.cantale.plugin.anticheat.AntiCheatManager;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener pour les checks d'inventaire.
 *
 * Écoute les clics dans les coffres, l'équipement d'armure,
 * et la consommation d'items.
 */
public class InventoryListener implements Listener {

    private final AntiCheatManager manager;

    public InventoryListener(AntiCheatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (manager.shouldBypass(player)) {
            return;
        }

        if (!manager.getConfig().isEnabled()) {
            return;
        }

        PlayerData data = manager.getPlayerData(player.getUniqueId());

        // ── ChestStealer Check ──
        if (event.getInventory().getType() == InventoryType.CHEST
                || event.getInventory().getType() == InventoryType.ENDER_CHEST
                || event.getInventory().getType() == InventoryType.BARREL) {

            Check chestCheck = manager.getCheckRegistry().get(CheckType.CHEST_STEALER);
            if (chestCheck != null && chestCheck.isEnabled() && chestCheck instanceof ChestStealerCheck cs) {
                CheckResult result = cs.checkClick(player, data);
                if (result.isViolated()) {
                    manager.handleViolation(player, result, chestCheck);
                }
            }
        }

        // ── AutoArmor Check ──
        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && isArmor(clicked.getType())) {
            Check armorCheck = manager.getCheckRegistry().get(CheckType.AUTO_ARMOR);
            if (armorCheck != null && armorCheck.isEnabled() && armorCheck instanceof AutoArmorCheck aa) {
                CheckResult result = aa.checkEquip(player, data);
                if (result.isViolated()) {
                    manager.handleViolation(player, result, armorCheck);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (manager.shouldBypass(player)) return;
        if (!manager.getConfig().isEnabled()) return;

        Check fastUseCheck = manager.getCheckRegistry().get(CheckType.FAST_USE);
        if (fastUseCheck != null && fastUseCheck.isEnabled() && fastUseCheck instanceof FastUseCheck fu) {
            CheckResult result = fu.checkConsume(player);
            if (result.isViolated()) {
                manager.handleViolation(player, result, fastUseCheck);
            }
        }
    }

    /**
     * Vérifie si un matériau est une pièce d'armure.
     */
    private boolean isArmor(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }
}
