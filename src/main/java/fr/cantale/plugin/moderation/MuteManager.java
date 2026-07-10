package fr.cantale.plugin.moderation;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MuteManager {

    private final CantalePlugin plugin;
    private final MuteDAO muteDAO;
    private final Map<UUID, Mute> activeMutes;

    public MuteManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.muteDAO = new MuteDAO(plugin);
        this.activeMutes = new HashMap<>();
    }

    public boolean mutePlayer(UUID uuid, String playerName, String reason, long durationSeconds) {
        long mutedAt = System.currentTimeMillis() / 1000;
        Mute mute = new Mute(uuid, playerName, mutedAt, reason, durationSeconds, mutedAt);
        int id = muteDAO.createMute(mute);
        if (id > 0) {
            activeMutes.put(uuid, mute);
            return true;
        }
        return false;
    }

    public boolean unmutePlayer(UUID uuid) {
        if (!activeMutes.containsKey(uuid)) return false;
        muteDAO.deleteMute(uuid);
        activeMutes.remove(uuid);
        return true;
    }

    public boolean isMuted(UUID uuid) {
        Mute mute = activeMutes.get(uuid);
        if (mute == null) return false;
        
        if (mute.isExpired()) {
            unmutePlayer(uuid);
            return false;
        }
        
        return true;
    }

    public Mute getMute(UUID uuid) {
        return activeMutes.get(uuid);
    }

    public void loadMutes() {
        List<Mute> mutes = muteDAO.getActiveMutes();
        for (Mute mute : mutes) {
            if (!mute.isExpired()) {
                activeMutes.put(mute.getPlayerUuid(), mute);
            } else {
                muteDAO.deleteMute(mute.getPlayerUuid());
            }
        }
    }

    public void cleanupExpired() {
        for (Mute mute : new HashMap<>(activeMutes).values()) {
            if (mute.isExpired()) {
                unmutePlayer(mute.getPlayerUuid());
            }
        }
    }
}
