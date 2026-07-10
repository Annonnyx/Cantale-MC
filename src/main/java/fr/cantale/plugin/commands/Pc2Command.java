package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class Pc2Command implements CommandExecutor {

    private final CantalePlugin plugin;

    public Pc2Command(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommande réservée aux joueurs.");
            return true;
        }

        PlayerRank rank = plugin.getRankManager().getRank(player);

        if (!rank.hasSecondChest()) {
            player.sendMessage("§8[§6Cantale§8] §cLe 2ème coffre privé (/pc2) est réservé au grade §eChèvre§c.");
            player.sendMessage("§8[§6Cantale§8] §7Achetez le grade sur la boutique : §ecantale.store");
            return true;
        }

        // Open a virtual second private chest (54 slots for Chevre)
        int slots = rank.getPc2Slots();
        Inventory pc2 = Bukkit.createInventory(null, slots, "§6Coffre Privé §7#2");

        // Load saved contents if any
        String sql = "SELECT contents FROM private_chests WHERE owner_uuid = ? AND chest_id = 2";
        try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, player.getUniqueId().toString());
            java.sql.ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String data = rs.getString("contents");
                if (data != null && !data.isEmpty()) {
                    org.bukkit.inventory.ItemStack[] contents = deserializeItems(data);
                    if (contents != null) {
                        pc2.setContents(contents);
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Erreur load pc2: " + e.getMessage());
        }

        player.openInventory(pc2);
        player.sendMessage("§8[§6Cantale§8] §72ème coffre privé ouvert §7(§e" + slots + " slots§7)");
        return true;
    }

    private org.bukkit.inventory.ItemStack[] deserializeItems(String data) {
        try {
            java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(data));
            org.bukkit.util.io.BukkitObjectInputStream dataInput = new org.bukkit.util.io.BukkitObjectInputStream(inputStream);
            int length = dataInput.readInt();
            org.bukkit.inventory.ItemStack[] items = new org.bukkit.inventory.ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (org.bukkit.inventory.ItemStack) dataInput.readObject();
            }
            dataInput.close();
            return items;
        } catch (Exception e) {
            return null;
        }
    }
}
