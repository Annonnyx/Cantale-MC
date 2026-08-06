package fr.cantale.plugin.events;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.crate.CrateType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Une fois par semaine, un joueur en ligne reçoit une clé RARE/EPIC/MYTHIC (1/3 chacun).
 */
public class WeeklyKeyDropManager {

    private final CantalePlugin plugin;
    private final EventsConfig config;
    private BukkitTask task;
    private int lastDropWeek = -1;
    private ZonedDateTime plannedDrop;

    public WeeklyKeyDropManager(CantalePlugin plugin, EventsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        if (!config.isWeeklyEnabled()) {
            plugin.getLogger().info("[WeeklyKeyDrop] Désactivé dans events.yml");
            return;
        }
        planWeekIfNeeded();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 60, 20L * 60);
        plugin.getLogger().info("[WeeklyKeyDrop] Planificateur démarré — prochain drop : " + plannedDrop);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        planWeekIfNeeded();
        if (plannedDrop == null) return;
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Paris"));
        int weekKey = weekKey(now);
        if (weekKey == lastDropWeek) return;
        if (now.isBefore(plannedDrop)) return;
        // Fenêtre de 2 minutes pour éviter de rater le tick
        if (java.time.Duration.between(plannedDrop, now).toMinutes() > 5) {
            // Trop tard — replanifier n'a pas de sens, marquer comme raté et attendre semaine suivante
            lastDropWeek = weekKey;
            return;
        }
        performDrop();
        lastDropWeek = weekKey;
    }

    private void planWeekIfNeeded() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Paris"));
        int weekKey = weekKey(now);
        if (plannedDrop != null && weekKey(plannedDrop) == weekKey) return;

        ZonedDateTime monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        DayOfWeek day;
        if (config.isWeeklyRandomDay()) {
            day = DayOfWeek.of(ThreadLocalRandom.current().nextInt(1, 8));
        } else {
            int fixed = Math.max(1, Math.min(7, config.getWeeklyFixedDay()));
            day = DayOfWeek.of(fixed);
        }

        int hour;
        if (config.isWeeklyRandomHour()) {
            int start = config.getWeeklyStartHour();
            int end = Math.max(start, config.getWeeklyEndHour());
            hour = ThreadLocalRandom.current().nextInt(start, end + 1);
        } else {
            hour = config.getWeeklyFixedHour();
        }
        int minute = ThreadLocalRandom.current().nextInt(0, 4) * 15;

        plannedDrop = monday.with(TemporalAdjusters.nextOrSame(day))
                .withHour(hour).withMinute(minute).withSecond(0).withNano(0);

        if (plannedDrop.isBefore(now) && weekKey(plannedDrop) == weekKey) {
            // Déjà passé cette semaine — planifier semaine prochaine
            plannedDrop = plannedDrop.plusWeeks(1);
        }
    }

    private void performDrop() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            plugin.getLogger().info("[WeeklyKeyDrop] Aucun joueur en ligne — drop reporté.");
            // Remettre plannedDrop dans ~30 min pour retenter
            plannedDrop = ZonedDateTime.now(ZoneId.of("Europe/Paris")).plusMinutes(30);
            return;
        }

        Player winner = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        List<CrateType> types = config.getWeeklyKeyTypes();
        CrateType type = types.get(ThreadLocalRandom.current().nextInt(types.size()));

        ItemStack key = plugin.getCrateManager().createCrateItem(type);
        var leftover = winner.getInventory().addItem(key);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                winner.getWorld().dropItemNaturally(winner.getLocation(), drop);
            }
        }

        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&6Clé Hebdo&8] &e" + winner.getName()
                        + " &7a reçu une clé &f" + type.getDisplayName() + "&7 !"));
        winner.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&6Clé Hebdo&8] &aFélicitations ! Ouvre-la au spawn."));
        plugin.getLogger().info("[WeeklyKeyDrop] " + winner.getName() + " → " + type.name());
    }

    private int weekKey(ZonedDateTime dt) {
        return dt.get(WeekFields.ISO.weekBasedYear()) * 100
                + dt.get(WeekFields.ISO.weekOfWeekBasedYear());
    }
}
