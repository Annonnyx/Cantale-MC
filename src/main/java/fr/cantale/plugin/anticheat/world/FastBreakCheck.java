package fr.cantale.plugin.anticheat.world;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import fr.cantale.plugin.anticheat.util.InstantBreakUtil;
import fr.cantale.plugin.custom.CustomItemManager;
import fr.cantale.plugin.custom.CustomItemType;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Détection FastBreak / Nuker sur blocs solides uniquement.
 *
 * Ignore totalement les blocs soft/instant (herbe, fleurs, neige, etc.) et les outils
 * custom Cantale qui forcent l'insta-break (Multi-Cantool, Pickantaxe, Cantaxe).
 * Les seuils portent sur une fenêtre longue + buffer VL avant pénalité trust.
 */
public class FastBreakCheck extends Check {

    private final CustomItemManager customItemManager;

    public FastBreakCheck(AntiCheatConfig config, CustomItemManager customItemManager) {
        super(CheckType.FAST_BREAK, config);
        this.customItemManager = customItemManager;
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        return CheckResult.pass(type);
    }

    /**
     * Enregistre une casse de bloc et vérifie le débit sur blocs solides.
     */
    public CheckResult checkBreak(Player player, Block block, PlayerData data) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return CheckResult.pass(type);
        }

        if (isCantaleMiningTool(player)) {
            data.resetFastBreakBuffer();
            return CheckResult.pass(type);
        }

        // Herbe / fleurs / neige / instant avec outil → jamais flag
        if (InstantBreakUtil.isSoftOrInstant(player, block)) {
            return CheckResult.pass(type);
        }

        long now = System.currentTimeMillis();
        int windowMs = config.getFastBreakWindowMs();
        data.addSolidBreak(now, windowMs);

        int count = data.getSolidBreakCount(now, windowMs);
        int max = config.getMaxSolidBreaks();

        if (count > max) {
            data.incrementFastBreakBuffer();
            int buffer = config.getFastBreakBuffer();
            if (data.getFastBreakBuffer() < buffer) {
                return CheckResult.pass(type);
            }
            String details = String.format("solidBreaks=%d max=%d window=%dms", count, max, windowMs);
            return CheckResult.violation(type, count - max, details);
        }

        data.resetFastBreakBuffer();
        return CheckResult.pass(type);
    }

    private boolean isCantaleMiningTool(Player player) {
        if (customItemManager == null) {
            return false;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!customItemManager.isCustomItem(tool)) {
            return false;
        }
        CustomItemType type = customItemManager.getCustomItemType(tool);
        if (type == null) {
            return false;
        }
        return type.isMultiCantool() || type.isPickantise() || type.isCantaxe();
    }
}
