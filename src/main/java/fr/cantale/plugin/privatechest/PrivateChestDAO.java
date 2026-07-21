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

    public void saveChest(UUID ownerUuid, ChestType type, ItemStack[] contents) {
        String sql = plugin.getDatabaseManager().isSQLite()
                ? "INSERT OR REPLACE INTO private_chests (owner_uuid, chest_type, contents, size, updated_at) VALUES (?, ?, ?, ?, ?)"
                : "INSERT INTO private_chests (owner_uuid, chest_type, contents, size, updated_at) VALUES (?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE contents = VALUES(contents), size = VALUES(size), updated_at = VALUES(updated_at)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ownerUuid.toString());
            stmt.setString(2, type.getKey());
            stmt.setString(3, itemStackArrayToBase64(contents));
            stmt.setInt(4, contents.length);
            stmt.setInt(5, (int) (System.currentTimeMillis() / 1000));
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur saveChest " + type + ": " + e.getMessage());
        }
    }

    public ChestSnapshot loadChest(UUID ownerUuid, ChestType type, int defaultSize) {
        String sql = "SELECT contents, size FROM private_chests WHERE owner_uuid = ? AND chest_type = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ownerUuid.toString());
            stmt.setString(2, type.getKey());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String data = rs.getString("contents");
                ItemStack[] contents = itemStackArrayFromBase64(data, defaultSize);
                int size = rs.getInt("size");
                return new ChestSnapshot(ownerUuid, type, size, contents);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur loadChest " + type + ": " + e.getMessage());
        }
        return new ChestSnapshot(ownerUuid, type, defaultSize, new ItemStack[defaultSize]);
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
            plugin.getLogger().severe("Erreur sérialisation coffre: " + e.getMessage());
            return "";
        }
    }

    private ItemStack[] itemStackArrayFromBase64(String data, int defaultSize) {
        if (data == null || data.isEmpty()) {
            return new ItemStack[defaultSize];
        }
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
            plugin.getLogger().severe("Erreur désérialisation coffre: " + e.getMessage());
            return new ItemStack[defaultSize];
        }
    }
}
