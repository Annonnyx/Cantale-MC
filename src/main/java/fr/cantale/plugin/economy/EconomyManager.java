package fr.cantale.plugin.economy;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import org.bukkit.entity.Player;

import java.util.UUID;

public class EconomyManager {

    private final CantalePlugin plugin;
    private final PlayerDAO playerDAO;

    public EconomyManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.playerDAO = new PlayerDAO(plugin);
    }

    public double getBalance(Player player) {
        return getBalance(player.getUniqueId());
    }

    public double getBalance(UUID uuid) {
        return playerDAO.getBalance(uuid);
    }

    public void setBalance(Player player, double amount) {
        playerDAO.setBalance(player.getUniqueId(), amount);
    }

    public boolean hasEnough(Player player, double amount) {
        return getBalance(player) >= amount;
    }

    public boolean hasEnough(UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }

    public boolean deposit(Player player, double amount) {
        if (amount < 0) return false;
        playerDAO.addBalance(player.getUniqueId(), amount);
        return true;
    }

    public boolean withdraw(Player player, double amount) {
        if (amount < 0 || !hasEnough(player, amount)) return false;
        playerDAO.removeBalance(player.getUniqueId(), amount);
        return true;
    }

    public boolean transfer(Player from, Player to, double amount) {
        if (!withdraw(from, amount)) return false;
        deposit(to, amount);
        return true;
    }

    public boolean transfer(UUID from, UUID to, double amount) {
        if (!hasEnough(from, amount)) return false;
        playerDAO.removeBalance(from, amount);
        playerDAO.addBalance(to, amount);
        return true;
    }

    public void addBalance(UUID uuid, double amount) {
        playerDAO.addBalance(uuid, amount);
    }

    public void removeBalance(UUID uuid, double amount) {
        playerDAO.removeBalance(uuid, amount);
    }

    public void resetBalance(Player player) {
        setBalance(player, plugin.getConfigManager().getStartBalance());
    }
}
