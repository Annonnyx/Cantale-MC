package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class AdminInvCommand implements CommandExecutor, TabCompleter, Listener {

    public static class AdminInvHolder implements InventoryHolder {
        private final UUID targetUuid;
        private final String targetName;
        private Inventory inventory;

        public AdminInvHolder(UUID targetUuid, String targetName) {
            this.targetUuid = targetUuid;
            this.targetName = targetName;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public UUID getTargetUuid() {
            return targetUuid;
        }

        public String getTargetName() {
            return targetName;
        }
    }

    private final CantalePlugin plugin;

    public AdminInvCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cantale.moderator") && !sender.hasPermission("cantale.admin")) {
            sender.sendMessage("§8[§6Cantale§8] §cPermission refusée (cantale.moderator).");
            return true;
        }

        if (!(sender instanceof Player staff)) {
            sender.sendMessage("§8[§6Cantale§8] §cCette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length < 1) {
            staff.sendMessage("§8[§6Cantale§8] §cUsage : /" + label + " <joueur>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            staff.sendMessage("§8[§6Cantale§8] §cJoueur introuvable ou hors ligne : §e" + args[0]);
            staff.sendMessage("§7(L'inspection/édition d'inventaire requiert que le joueur soit connecté)");
            return true;
        }

        openAdminInv(staff, target);
        return true;
    }

    public static void openAdminInv(Player staff, Player target) {
        AdminInvHolder holder = new AdminInvHolder(target.getUniqueId(), target.getName());
        Inventory gui = Bukkit.createInventory(holder, 54, Component.text("§c[Staff] Inventaire — §e" + target.getName()));
        holder.setInventory(gui);

        // Slots 0..35 : inventaire principal (0..35)
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < Math.min(contents.length, 36); i++) {
            gui.setItem(i, contents[i] != null ? contents[i].clone() : null);
        }

        // Slots 36..39 : armure (Casque: 36, Plastron: 37, Jambières: 38, Bottes: 39)
        ItemStack[] armor = target.getInventory().getArmorContents();
        if (armor != null && armor.length >= 4) {
            gui.setItem(36, armor[3] != null ? armor[3].clone() : null); // Casque
            gui.setItem(37, armor[2] != null ? armor[2].clone() : null); // Plastron
            gui.setItem(38, armor[1] != null ? armor[1].clone() : null); // Jambières
            gui.setItem(39, armor[0] != null ? armor[0].clone() : null); // Bottes
        }

        // Slot 40 : main secondaire (Offhand)
        gui.setItem(40, target.getInventory().getItemInOffHand());

        // Separators slots 41..53
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.displayName(Component.text("§7[Séparateur — S.36-39: Armure | S.40: Offhand]", NamedTextColor.GRAY));
            glass.setItemMeta(glassMeta);
        }
        for (int i = 41; i < 54; i++) {
            gui.setItem(i, glass);
        }

        staff.openInventory(gui);
        staff.sendMessage("§8[§6Cantale§8] §aInventaire de §e" + target.getName() + " §aouvert (édition live).");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminInvHolder holder)) return;
        Player staff = (Player) event.getWhoClicked();
        Player target = Bukkit.getPlayer(holder.getTargetUuid());

        int rawSlot = event.getRawSlot();

        // Bloquer les clics sur les vitres décoratives 41..53
        if (rawSlot >= 41 && rawSlot < 54) {
            event.setCancelled(true);
            return;
        }

        // Synchronisation asynchrone / au tick suivant vers le joueur cible
        if (target != null && target.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> syncInventory(event.getInventory(), target));
        } else {
            staff.sendMessage("§8[§6Cantale§8] §cAttention : le joueur cible s'est déconnecté. Édition live interrompue.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminInvHolder holder)) return;
        Player target = Bukkit.getPlayer(holder.getTargetUuid());

        for (int slot : event.getRawSlots()) {
            if (slot >= 41 && slot < 54) {
                event.setCancelled(true);
                return;
            }
        }

        if (target != null && target.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> syncInventory(event.getInventory(), target));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminInvHolder holder)) return;
        Player target = Bukkit.getPlayer(holder.getTargetUuid());
        if (target != null && target.isOnline()) {
            syncInventory(event.getInventory(), target);
        }
    }

    private static void syncInventory(Inventory gui, Player target) {
        if (gui == null || target == null || !target.isOnline()) return;

        // Sync main inventory slots 0..35
        for (int i = 0; i < 36; i++) {
            target.getInventory().setItem(i, gui.getItem(i));
        }

        // Sync armor slots 36..39 -> (36=helmet, 37=chest, 38=leggings, 39=boots)
        target.getInventory().setHelmet(gui.getItem(36));
        target.getInventory().setChestplate(gui.getItem(37));
        target.getInventory().setLeggings(gui.getItem(38));
        target.getInventory().setBoots(gui.getItem(39));

        // Sync offhand slot 40
        target.getInventory().setItemInOffHand(gui.getItem(40));

        target.updateInventory();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cantale.moderator") && !sender.hasPermission("cantale.admin")) return Collections.emptyList();
        if (args.length != 1) return Collections.emptyList();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
