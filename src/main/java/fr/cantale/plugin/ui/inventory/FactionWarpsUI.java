package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Liste des warps de faction. Clic = TP, Shift+Clic = delete (via menu d'action).
 */
public class FactionWarpsUI extends BaseUI {

    private final Faction faction;
    private final List<String> warpNames = new ArrayList<>();

    public FactionWarpsUI(CantalePlugin plugin, Player player, Faction faction) {
        super(plugin, player, "§8[§6Cantale§8] §7Warps — " + faction.getName(), 6);
        this.faction = faction;
    }

    @Override
    protected void init() {
        fillEmpty();
        warpNames.clear();

        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement st = c.prepareStatement("SELECT name FROM faction_warps WHERE faction_id = ? ORDER BY name")) {
            st.setInt(1, faction.getId());
            ResultSet rs = st.executeQuery();
            while (rs.next()) warpNames.add(rs.getString("name"));
        } catch (Exception ex) {
            player.sendMessage("§cErreur lors du chargement des warps : " + ex.getMessage());
        }

        setItem(4, Material.ENDER_PEARL, "§6§lWarps de Faction",
            "§7" + warpNames.size() + "/" + faction.getMaxWarps() + " warps",
            "§7Cliquez un warp pour vous y téléporter");

        int slot = 9;
        for (String name : warpNames) {
            if (slot >= 45) break;
            setItem(slot, Material.COMPASS, "§e" + name,
                "§7▶ Clic §7: Téléporter",
                "§7▶ Clic droit §7: Supprimer");
            slot++;
        }

        if (warpNames.isEmpty()) {
            setItem(22, Material.PAPER, "§7Aucun warp défini",
                "§7Utilisez §e/f setwarp <nom>§7 dans un claim",
                "§7pour créer un nouveau warp.");
        }

        setItem(48, Material.LIME_WOOL, "§a§l+ Nouveau warp", "§7/f setwarp <nom>", "§7Crée un warp à votre position");
        addNavigationBar(true, true);
    }

    @Override
    protected void handleClick(int slot) {
        if (slot == 45) { plugin.getUIManager().goBack(player); return; }
        if (slot == 49) { close(); return; }
        if (slot == 53) { init(); return; }
        if (slot == 48) {
            close();
            player.sendMessage("§8[§6Cantale§8] §7Pour créer un warp, tapez §e/f setwarp <nom>§7 dans un claim de votre faction.");
            return;
        }

        int index = slot - 9;
        if (index < 0 || index >= warpNames.size()) return;
        String name = warpNames.get(index);
        close();
        player.performCommand("f warp " + name);
    }
}
