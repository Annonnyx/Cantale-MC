package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.Faction;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Liste des claims de la faction. Clic = TP au centre du chunk, Clic droit = unclaim.
 */
public class FactionClaimsUI extends BaseUI {

    private final Faction faction;
    private final List<Claim> claimList = new ArrayList<>();

    public FactionClaimsUI(CantalePlugin plugin, Player player, Faction faction) {
        super(plugin, player, "§8[§6Cantale§8] §7Claims — " + faction.getName(), 6);
        this.faction = faction;
    }

    @Override
    protected void init() {
        fillEmpty();
        claimList.clear();
        claimList.addAll(faction.getClaims());

        setItem(4, Material.GRASS_BLOCK, "§6§lClaims de Faction",
            "§7" + claimList.size() + "/" + faction.getMaxClaims() + " chunks claimés",
            "§7Power : §e" + faction.getPower(),
            "",
            "§7▶ Clic §7: Téléporter au chunk",
            "§7▶ Clic droit / Shift §7: Unclaim");

        int slot = 9;
        for (Claim claim : claimList) {
            if (slot >= 45) break;
            Material mat = claim.isPasdic() ? Material.OBSIDIAN : Material.GRASS_BLOCK;
            setItem(slot, mat, "§e" + claim.getWorld() + " §7(" + claim.getChunkX() + ", " + claim.getChunkZ() + ")",
                "§7Position : §e" + (claim.getChunkX() * 16) + ", " + (claim.getChunkZ() * 16),
                claim.isPasdic() ? "§5🔒 PASDIC actif" : "§7État : normal",
                "",
                "§7▶ Clic gauche §7: TP",
                "§7▶ Clic droit / Shift §7: Unclaim");
            slot++;
        }

        if (claimList.isEmpty()) {
            setItem(22, Material.PAPER, "§7Aucun claim",
                "§7Utilisez §e/f claim§7 pour claim",
                "§7le chunk où vous vous trouvez.");
        }

        setItem(48, Material.LIME_WOOL, "§a§l+ Claim ici", "§7/f claim", "§7Claim le chunk où vous êtes");
        setItem(50, Material.MAP, "§eVoir la carte", "§7/f map", "§7Carte inventaire (claim au clic)");
        addNavigationBar(true, true);
    }

    @Override
    protected void handleClick(int slot) {
        if (slot == 45) { plugin.getUIManager().goBack(player); return; }
        if (slot == 49) { close(); return; }
        if (slot == 53) { init(); return; }
        if (slot == 48) { close(); player.performCommand("f claim"); return; }
        if (slot == 50) {
            plugin.getUIManager().getNavigationManager()
                    .openUI(player, new FactionMapUI(plugin, player));
            return;
        }

        int index = slot - 9;
        if (index < 0 || index >= claimList.size()) return;
        Claim claim = claimList.get(index);
        close();

        // TP au centre du chunk
        World world = Bukkit.getWorld(claim.getWorld());
        if (world == null) {
            player.sendMessage("§cMonde introuvable : " + claim.getWorld());
            return;
        }
        int x = claim.getChunkX() * 16 + 8;
        int z = claim.getChunkZ() * 16 + 8;
        int y = world.getHighestBlockYAt(x, z) + 1;
        player.teleport(new Location(world, x + 0.5, y, z + 0.5));
        player.sendMessage("§8[§6Cantale§8] §aTéléporté au claim §e(" + claim.getChunkX() + ", " + claim.getChunkZ() + ")§a.");
    }
}
