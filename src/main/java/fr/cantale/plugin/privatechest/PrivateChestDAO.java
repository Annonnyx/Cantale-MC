package fr.cantale.plugin.privatechest;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.util.Base64;
import java.util.UUID;

public class PrivateChestDAO {

    private final CantalePlugin plugin;

    public PrivateChestDAO(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void savePrivateChest(UUID ownerUuid, ItemStack[] contents) {
        String sql = "INSERT OR REPLACE INTO private_chests (owner_uuid, contents, size) VALUES (?, ?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ownerUuid.toString());
            stmt.setString(2, itemStackArrayToBase64(contents));
            stmt.setInt(3, contents.length);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur savePrivateChest: " + e.getMessage());
        }
    }

    public PrivateChest loadPrivateChest(UUID ownerUuid) {
        String sql = "SELECT * FROM private_chests WHERE owner_uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ownerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String data = rs.getString("contents");
                ItemStack[] contents = itemStackArrayFromBase64(data);
                return new PrivateChest(ownerUuid, contents);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur loadPrivateChest: " + e.getMessage());
        }
        return new PrivateChest(ownerUuid, 27);
    }

    private String itemStackArrayToBase64(ItemStack[] items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private ItemStack[] itemStackArrayFromBase64(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int length = dataInput.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            dataInput.close();
            return items;
        } catch (Exception e) {
            return new ItemStack[0];
        }
    }
}
