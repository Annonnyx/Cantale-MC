package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.rank.PlayerRank;
import fr.cantale.plugin.tag.TagManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TagCustomizationUI extends BaseUI {

    private final TagManager tagManager;
    private final Map<Integer, String> slotToTagId = new HashMap<>();

    public TagCustomizationUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Gestion des Tags", 4);
        this.tagManager = plugin.getTagManager();
    }

    @Override
    protected void init() {
        fillEmpty();
        slotToTagId.clear();

        boolean showRank = tagManager.isRankTagShown(player.getUniqueId());
        PlayerRank rank = plugin.getRankManager().getRank(player);

        // Option 1 : Affichage du grade
        setItem(10, Material.NAME_TAG, "§6§lAffichage du Grade",
                "§7Grade actuel: §e" + rank.getDisplayName(),
                "§7Statut: " + (showRank ? "§aAffiché" : "§cCaché"),
                "",
                "§eCliquez pour modifier");

        // Option 2 : Désactiver le tag customisé
        String activeCustom = tagManager.getActiveCustomTag(player.getUniqueId());
        setItem(16, Material.BARRIER, "§c§lDésactiver le Tag Customisé",
                "§7Retire votre tag de tournoi/événement actif.",
                "§7Statut: " + (activeCustom == null ? "§aAucun tag" : "§7Actif: " + ChatColor.translateAlternateColorCodes('&', tagManager.getCustomTagDisplayName(activeCustom))),
                "",
                "§eCliquez pour désactiver");

        // Titre pour les tags débloqués
        setItem(4, Material.BOOK, "§e§lVos Tags Débloqués",
                "§7Ci-dessous se trouvent les tags",
                "§7que vous avez gagnés lors d'événements.");

        // Liste des tags débloqués
        List<String> unlocked = tagManager.getUnlockedTags(player.getUniqueId());
        int slot = 19;

        if (unlocked.isEmpty()) {
            setItem(22, Material.PAPER, "§cAucun tag débloqué",
                    "§7Vous n'avez pas encore de tag customisé.",
                    "§7Participez à des tournois pour en gagner !");
        } else {
            for (String tagId : unlocked) {
                if (slot > 25) break; // Capping to slot 25 to fit navigation bar cleanly
                String displayName = tagManager.getCustomTagDisplayName(tagId);
                if (displayName == null) continue;

                boolean isActive = tagId.equalsIgnoreCase(activeCustom);
                Material mat = isActive ? Material.ENCHANTED_BOOK : Material.PAPER;
                String activeLabel = isActive ? "§a§lACTIF" : "§eCliquez pour équiper";

                setItem(slot, mat, ChatColor.translateAlternateColorCodes('&', displayName),
                        "§7ID: §b" + tagId,
                        "",
                        activeLabel);

                slotToTagId.put(slot, tagId);
                slot++;
            }
        }

        addNavigationBar(true, false);
    }

    @Override
    protected void handleClick(int slot) {
        if (slot == 10) {
            boolean current = tagManager.isRankTagShown(player.getUniqueId());
            tagManager.setRankTagShown(player.getUniqueId(), !current);
            tagManager.updatePlayerNametag(player);
            player.sendMessage("§8[§6Cantale§8] §aAffichage du grade modifié.");
            init();
        } else if (slot == 16) {
            tagManager.setActiveCustomTag(player.getUniqueId(), null);
            tagManager.updatePlayerNametag(player);
            player.sendMessage("§8[§6Cantale§8] §aTag customisé déséquipé.");
            init();
        } else if (slotToTagId.containsKey(slot)) {
            String tagId = slotToTagId.get(slot);
            tagManager.setActiveCustomTag(player.getUniqueId(), tagId);
            tagManager.updatePlayerNametag(player);
            player.sendMessage("§8[§6Cantale§8] §aTag équipé avec succès !");
            init();
        }
    }
}
