package fr.cantale.plugin.cooldown;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final Map<UUID, Map<String, Long>> cooldowns;
    private final Map<UUID, Long> moderationCooldowns;

    public CooldownManager() {
        this.cooldowns = new HashMap<>();
        this.moderationCooldowns = new HashMap<>();
    }

    public boolean hasCooldown(Player player, String command, long cooldownMillis) {
        // Vérifier si le joueur a le cooldown bypass
        if (player.hasPermission("cantale.cooldown.bypass")) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        if (!cooldowns.containsKey(uuid)) {
            return false;
        }

        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (!playerCooldowns.containsKey(command)) {
            return false;
        }

        long lastUse = playerCooldowns.get(command);
        long remaining = cooldownMillis - (System.currentTimeMillis() - lastUse);
        return remaining > 0;
    }

    public long getRemainingCooldown(Player player, String command, long cooldownMillis) {
        UUID uuid = player.getUniqueId();
        if (!cooldowns.containsKey(uuid)) {
            return 0;
        }

        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (!playerCooldowns.containsKey(command)) {
            return 0;
        }

        long lastUse = playerCooldowns.get(command);
        long remaining = cooldownMillis - (System.currentTimeMillis() - lastUse);
        return Math.max(0, remaining);
    }

    public void setCooldown(Player player, String command) {
        UUID uuid = player.getUniqueId();
        cooldowns.computeIfAbsent(uuid, k -> new HashMap<>()).put(command, System.currentTimeMillis());
    }

    public void removeCooldown(Player player, String command) {
        UUID uuid = player.getUniqueId();
        if (cooldowns.containsKey(uuid)) {
            cooldowns.get(uuid).remove(command);
        }
    }

    // Cooldown spécifique pour /modération
    public boolean hasModerationCooldown(Player player, long cooldownMillis) {
        UUID uuid = player.getUniqueId();
        if (!moderationCooldowns.containsKey(uuid)) {
            return false;
        }

        long lastUse = moderationCooldowns.get(uuid);
        long remaining = cooldownMillis - (System.currentTimeMillis() - lastUse);
        return remaining > 0;
    }

    public long getRemainingModerationCooldown(Player player, long cooldownMillis) {
        UUID uuid = player.getUniqueId();
        if (!moderationCooldowns.containsKey(uuid)) {
            return 0;
        }

        long lastUse = moderationCooldowns.get(uuid);
        long remaining = cooldownMillis - (System.currentTimeMillis() - lastUse);
        return Math.max(0, remaining);
    }

    public void setModerationCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        moderationCooldowns.put(uuid, System.currentTimeMillis());
    }

    public void removeModerationCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        moderationCooldowns.remove(uuid);
    }
}
