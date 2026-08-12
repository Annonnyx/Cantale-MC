package fr.cantale.plugin.anticheat.world;

import fr.cantale.plugin.anticheat.AntiCheatManager;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Listener pour le check FastBreak / Nuker (blocs solides uniquement).
 */
public class BlockBreakListener implements Listener {

    private final AntiCheatManager manager;

    public BlockBreakListener(AntiCheatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (manager.shouldBypass(player) || !manager.getConfig().isEnabled()) {
            return;
        }

        Check check = manager.getCheckRegistry().get(CheckType.FAST_BREAK);
        if (check == null || !check.isEnabled() || !(check instanceof FastBreakCheck fastBreak)) {
            return;
        }

        PlayerData data = manager.getPlayerData(player.getUniqueId());
        CheckResult result = fastBreak.checkBreak(player, event.getBlock(), data);
        if (result.isViolated()) {
            manager.handleViolation(player, result, check);
        }
    }
}
