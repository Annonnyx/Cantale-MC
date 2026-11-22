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
        return deposit(player.getUniqueId(), amount);
    }

    public boolean deposit(UUID uuid, double amount) {
        if (amount < 0) return false;
        if (!playerDAO.addBalance(uuid, amount)) {
            plugin.getLogger().severe("Crédit Cantox refusé pour " + uuid + ".");
            return false;
        }
        return true;
    }

    public boolean withdraw(Player player, double amount) {
        return withdraw(player.getUniqueId(), amount);
    }

    public boolean withdraw(UUID uuid, double amount) {
        if (amount < 0) return false;
        return playerDAO.removeBalance(uuid, amount);
    }

    public boolean transfer(Player from, Player to, double amount) {
        return transfer(from.getUniqueId(), to.getUniqueId(), amount);
    }

    public boolean transfer(UUID from, UUID to, double amount) {
        if (!playerDAO.transferBalance(from, to, amount)) {
            return false;
        }
        return true;
    }

    public void addBalance(UUID uuid, double amount) {
        deposit(uuid, amount);
    }

    public void removeBalance(UUID uuid, double amount) {
        withdraw(uuid, amount);
    }

    public void resetBalance(Player player) {
        setBalance(player, plugin.getConfigManager().getStartBalance());
    }
}
