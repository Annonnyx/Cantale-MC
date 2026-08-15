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
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Listener pour les checks d'inventaire.
 *
 * Écoute les clics dans les contenants VANILLA uniquement (coffres, barrels,
 * shulkers — jamais les GUIs Cantale ni l'ender chest), l'équipement d'armure,
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

        // ── ChestStealer : contenants vanilla uniquement ──
        if (isVanillaContainer(event.getInventory().getHolder())) {
            // Un clic utile = un item réellement présent dans la case cliquée
            if (event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {
                Check chestCheck = manager.getCheckRegistry().get(CheckType.CHEST_STEALER);
                if (chestCheck != null && chestCheck.isEnabled() && chestCheck instanceof ChestStealerCheck cs) {
                    PlayerData data = manager.getPlayerData(player.getUniqueId());
                    CheckResult result = cs.checkClick(player, data);
                    if (result.isViolated()) {
                        manager.handleViolation(player, result, chestCheck);
                    }
                }
            }
        }

        // ── AutoArmor ──
        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && isArmor(clicked.getType()) && isVanillaContainerOrPlayerInv(event.getInventory().getHolder())) {
            Check armorCheck = manager.getCheckRegistry().get(CheckType.AUTO_ARMOR);
            if (armorCheck != null && armorCheck.isEnabled() && armorCheck instanceof AutoArmorCheck aa) {
                PlayerData data = manager.getPlayerData(player.getUniqueId());
                CheckResult result = aa.checkEquip(player, data);
                if (result.isViolated()) {
                    manager.handleViolation(player, result, armorCheck);
                }
            }
        }
    }

    /**
     * Début de consommation : mémorise le timestamp pour FastUse.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsumeStart(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !item.getType().isEdible()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || manager.shouldBypass(player)) {
            return;
        }
        if (!manager.getConfig().isEnabled()) {
            return;
        }

        Check fastUseCheck = manager.getCheckRegistry().get(CheckType.FAST_USE);
        if (fastUseCheck != null && fastUseCheck.isEnabled()) {
            manager.getPlayerData(player.getUniqueId()).setConsumeStartTime(System.currentTimeMillis());
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
            PlayerData data = manager.getPlayerData(player.getUniqueId());
            CheckResult result = fu.checkConsume(player, data);
            if (result.isViolated()) {
                manager.handleViolation(player, result, fastUseCheck);
            }
        }
    }

    /**
     * true si le holder est un contenant vanilla à état de bloc (coffre, barrel,
     * shulker, double coffre). Exclut les GUIs Cantale (holders custom ou null).
     */
    private boolean isVanillaContainer(InventoryHolder holder) {
        return holder instanceof org.bukkit.block.Container
                || holder instanceof org.bukkit.block.DoubleChest;
    }

    /**
     * AutoArmor : contenants vanilla + inventaire joueur (holder = le joueur lui-même).
     */
    private boolean isVanillaContainerOrPlayerInv(InventoryHolder holder) {
        return holder == null
                || holder instanceof Player
                || isVanillaContainer(holder);
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
