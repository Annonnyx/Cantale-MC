package fr.cantale.plugin.skills;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.world.WorldTopology;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Artisan : enclume moins chère (jamais gratuite) et potions plus longues.
 * Chevalier : dégâts et résistance seulement dans {@code world_warzone}, plafonnés.
 */
public final class SkillEffectListener implements Listener {

    private final CantalePlugin plugin;
    private final Map<String, UUID> lastBrewer = new ConcurrentHashMap<>();

    public SkillEffectListener(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (victim.getWorld() == null || !WorldTopology.WARZONE.equals(victim.getWorld().getName())) {
            return;
        }
        SkillService skills = plugin.getSkillService();
        if (skills == null) {
            return;
        }
        double damage = event.getDamage();
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker != null && !attacker.getUniqueId().equals(victim.getUniqueId())) {
            damage *= SkillFormulas.knightOutgoingMultiplier(
                    skills.countFactionOf(attacker.getUniqueId(), Skill.CHEVALIER));
        }
        damage *= SkillFormulas.knightIncomingMultiplier(
                skills.countFactionOf(victim.getUniqueId(), Skill.CHEVALIER));
        event.setDamage(damage);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnvil(PrepareAnvilEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvil)) {
            return;
        }
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        SkillService skills = plugin.getSkillService();
        if (skills == null) {
            return;
        }
        int base = anvil.getRepairCost();
        int next = SkillFormulas.artisanRepairCost(base,
                skills.countFactionOf(player.getUniqueId(), Skill.ARTISAN));
        if (next < base) {
            anvil.setRepairCost(next);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrewOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory() instanceof BrewerInventory inventory)) {
            return;
        }
        if (inventory.getLocation() == null || inventory.getLocation().getWorld() == null) {
            return;
        }
        lastBrewer.put(key(inventory.getLocation()), player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        SkillService skills = plugin.getSkillService();
        if (skills == null) {
            return;
        }
        int artisans = 0;
        for (org.bukkit.entity.HumanEntity viewer : event.getContents().getViewers()) {
            if (viewer instanceof Player player) {
                artisans = Math.max(artisans, skills.countFactionOf(player.getUniqueId(), Skill.ARTISAN));
            }
        }
        if (artisans == 0 && event.getBlock() != null) {
            UUID last = lastBrewer.get(key(event.getBlock().getLocation()));
            if (last != null) {
                artisans = skills.countFactionOf(last, Skill.ARTISAN);
            }
        }
        int bonus = SkillFormulas.artisanDurationBonusTicks(artisans);
        if (bonus <= 0) {
            return;
        }
        extend(event.getResults(), bonus);
    }

    private static void extend(List<ItemStack> results, int bonusTicks) {
        if (results == null) {
            return;
        }
        for (ItemStack stack : results) {
            if (stack == null || !(stack.getItemMeta() instanceof PotionMeta meta)) {
                continue;
            }
            List<PotionEffect> effects = new ArrayList<>(meta.getCustomEffects());
            if (effects.isEmpty() && meta.getBasePotionType() != null) {
                effects.addAll(meta.getBasePotionType().getPotionEffects());
            }
            boolean changed = false;
            for (PotionEffect effect : effects) {
                if (effect == null || effect.getType() == null) {
                    continue;
                }
                meta.addCustomEffect(new PotionEffect(
                        effect.getType(),
                        effect.getDuration() + bonusTicks,
                        effect.getAmplifier(),
                        effect.isAmbient(),
                        effect.hasParticles(),
                        effect.hasIcon()
                ), true);
                changed = true;
            }
            if (changed) {
                stack.setItemMeta(meta);
            }
        }
    }

    private static String key(org.bukkit.Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX()
                + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private static Player resolveAttacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player source) {
            return source;
        }
        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
    }
}
