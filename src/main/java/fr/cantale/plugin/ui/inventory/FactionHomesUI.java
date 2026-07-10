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
 * Liste des homes de faction du joueur. Clic = TP.
 */
public class FactionHomesUI extends BaseUI {

    private final Faction faction;
    private final List<String> homeNames = new ArrayList<>();

    public FactionHomesUI(CantalePlugin plugin, Player player, Faction faction) {
        super(plugin, player, "§8[§6Cantale§8] §7Homes — " + faction.getName(), 6);
        this.faction = faction;
    }

    @Override
    protected void init() {
        fillEmpty();
        homeNames.clear();

        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT name FROM faction_homes WHERE faction_id = ? AND player_uuid = ? ORDER BY name")) {
            st.setInt(1, faction.getId());
            st.setString(2, player.getUniqueId().toString());
            ResultSet rs = st.executeQuery();
            while (rs.next()) homeNames.add(rs.getString("name"));
        } catch (Exception ex) {
            player.sendMessage("§cErreur lors du chargement des homes : " + ex.getMessage());
        }

        int maxHomes = Math.max(1, faction.getMaxFhomes());
        setItem(4, Material.RED_BED, "§6§lVos Homes de Faction",
            "§7" + homeNames.size() + "/" + maxHomes + " homes utilisés",
            "§7Cliquez un home pour vous y téléporter");

        int slot = 9;
        for (String name : homeNames) {
            if (slot >= 45) break;
            setItem(slot, Material.RED_BED, "§e" + name, "§7▶ Clic §7: Téléporter");
            slot++;
        }

        if (homeNames.isEmpty()) {
            setItem(22, Material.PAPER, "§7Aucun home défini",
                "§7Utilisez §e/f sethome <nom>§7 dans un claim",
                "§7pour créer un home.");
        }

        setItem(48, Material.LIME_WOOL, "§a§l+ Nouveau home", "§7/f sethome <nom>", "§7Crée un home à votre position");
        addNavigationBar(true, true);
    }

    @Override
    protected void handleClick(int slot) {
        if (slot == 45) { plugin.getUIManager().goBack(player); return; }
        if (slot == 49) { close(); return; }
        if (slot == 53) { init(); return; }
        if (slot == 48) {
            close();
            player.sendMessage("§8[§6Cantale§8] §7Pour créer un home, tapez §e/f sethome <nom>§7 dans un claim de votre faction.");
            return;
        }

        int index = slot - 9;
        if (index < 0 || index >= homeNames.size()) return;
        String name = homeNames.get(index);
        close();
        player.performCommand("f home " + name);
    }
}
