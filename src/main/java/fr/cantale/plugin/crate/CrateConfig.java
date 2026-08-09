package fr.cantale.plugin.crate;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Charge {@code crate.yml} — emplacements d'interaction des caisses.
 * <p>
 * Si la liste de locations / zones d'un type est non vide → uniquement ces positions.
 * Si elle est vide → comportement legacy (détection par type de bloc / eau partout).
 */
public class CrateConfig {

    public record CrateLocation(String world, int x, int y, int z, String label) {
        public boolean matchesBlock(Block block) {
            if (block == null) {
                return false;
            }
            World w = block.getWorld();
            if (w == null || !w.getName().equals(world)) {
                return false;
            }
            return block.getX() == x && block.getY() == y && block.getZ() == z;
        }

        public boolean isNear(Location loc, double radius) {
            if (loc == null || loc.getWorld() == null) {
                return false;
            }
            if (!loc.getWorld().getName().equals(world)) {
                return false;
            }
            double cx = x + 0.5;
            double cy = y + 0.5;
            double cz = z + 0.5;
            return loc.distanceSquared(new Location(loc.getWorld(), cx, cy, cz)) <= radius * radius;
        }
    }

    /**
     * Zone cubique fontaine (2 coins → min/max auto, ordre indifférent).
     */
    public record FountainZone(String world, int minX, int minY, int minZ,
                               int maxX, int maxY, int maxZ, String label) {

        public static FountainZone of(String world, int x1, int y1, int z1,
                                      int x2, int y2, int z2, String label) {
            return new FountainZone(
                    world,
                    Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                    Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2),
                    label != null ? label : ""
            );
        }

        public boolean contains(Location loc) {
            if (loc == null || loc.getWorld() == null) {
                return false;
            }
            if (!loc.getWorld().getName().equals(world)) {
                return false;
            }
            int bx = loc.getBlockX();
            int by = loc.getBlockY();
            int bz = loc.getBlockZ();
            return bx >= minX && bx <= maxX
                    && by >= minY && by <= maxY
                    && bz >= minZ && bz <= maxZ;
        }
    }

    private final CantalePlugin plugin;

    private final List<CrateLocation> mailboxLocations = new ArrayList<>();
    private final Set<Material> mailboxBlocks = EnumSet.noneOf(Material.class);

    private final List<CrateLocation> chestLocations = new ArrayList<>();
    private final Set<Material> chestBlocks = EnumSet.noneOf(Material.class);

    private final List<FountainZone> fountainZones = new ArrayList<>();
    private final List<CrateLocation> fountainLocations = new ArrayList<>();
    private double fountainRadius = 3.0;

    private final List<CrateLocation> altarLocations = new ArrayList<>();
    private final Set<Material> altarBlocks = EnumSet.noneOf(Material.class);

    public CrateConfig(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResource("crate.yml", false);
        File file = new File(plugin.getDataFolder(), "crate.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        loadStation(yaml.getConfigurationSection("mailbox"), mailboxLocations, mailboxBlocks,
                List.of(Material.BARREL, Material.LOOM));
        loadStation(yaml.getConfigurationSection("chest"), chestLocations, chestBlocks,
                List.of(Material.CHEST, Material.TRAPPED_CHEST));
        loadFountain(yaml.getConfigurationSection("fountain"));
        loadStation(yaml.getConfigurationSection("altar"), altarLocations, altarBlocks,
                List.of(Material.BEACON, Material.ENCHANTING_TABLE));

        plugin.getLogger().info("[Crate] Config chargée — mailbox=" + mailboxLocations.size()
                + " chest=" + chestLocations.size()
                + " fountainZones=" + fountainZones.size()
                + " fountainLegacy=" + fountainLocations.size()
                + " altar=" + altarLocations.size());
    }

    private void loadStation(ConfigurationSection section,
                             List<CrateLocation> locations,
                             Set<Material> blocks,
                             List<Material> defaults) {
        locations.clear();
        blocks.clear();
        if (section == null) {
            blocks.addAll(defaults);
            return;
        }

        List<Material> parsed = parseMaterials(section.getStringList("blocks"));
        if (parsed.isEmpty()) {
            blocks.addAll(defaults);
        } else {
            blocks.addAll(parsed);
        }
        locations.addAll(parseLocations(section.getMapList("locations")));
    }

    private void loadFountain(ConfigurationSection section) {
        fountainZones.clear();
        fountainLocations.clear();
        fountainRadius = 3.0;
        if (section == null) {
            return;
        }

        fountainZones.addAll(parseFountainZones(section.getMapList("zones")));

        // Legacy point + radius (utilisé seulement si aucune zone)
        fountainRadius = Math.max(0.5, section.getDouble("radius", 3.0));
        fountainLocations.addAll(parseLocations(section.getMapList("locations")));
    }

    private List<FountainZone> parseFountainZones(List<Map<?, ?>> maps) {
        List<FountainZone> result = new ArrayList<>();
        if (maps == null) {
            return result;
        }
        for (Map<?, ?> map : maps) {
            if (map == null) {
                continue;
            }
            Object worldObj = map.get("world");
            if (worldObj == null) {
                continue;
            }
            String world = String.valueOf(worldObj).trim();
            if (world.isEmpty()) {
                continue;
            }
            Integer x1 = toInt(map.get("x1"));
            Integer y1 = toInt(map.get("y1"));
            Integer z1 = toInt(map.get("z1"));
            Integer x2 = toInt(map.get("x2"));
            Integer y2 = toInt(map.get("y2"));
            Integer z2 = toInt(map.get("z2"));
            if (x1 == null || y1 == null || z1 == null || x2 == null || y2 == null || z2 == null) {
                plugin.getLogger().warning("[Crate] Zone fontaine invalide (x1/y1/z1/x2/y2/z2) world=" + world);
                continue;
            }
            Object labelObj = map.get("label");
            String label = labelObj != null ? String.valueOf(labelObj) : "";
            result.add(FountainZone.of(world, x1, y1, z1, x2, y2, z2, label));
        }
        return result;
    }

    private List<Material> parseMaterials(List<String> names) {
        List<Material> result = new ArrayList<>();
        if (names == null) {
            return result;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Material mat = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (mat != null && mat.isBlock()) {
                result.add(mat);
            } else {
                plugin.getLogger().warning("[Crate] Matériau inconnu dans crate.yml: " + name);
            }
        }
        return result;
    }

    private List<CrateLocation> parseLocations(List<Map<?, ?>> maps) {
        List<CrateLocation> result = new ArrayList<>();
        if (maps == null) {
            return result;
        }
        for (Map<?, ?> map : maps) {
            if (map == null) {
                continue;
            }
            Object worldObj = map.get("world");
            if (worldObj == null) {
                continue;
            }
            String world = String.valueOf(worldObj).trim();
            if (world.isEmpty()) {
                continue;
            }
            Integer x = toInt(map.get("x"));
            Integer y = toInt(map.get("y"));
            Integer z = toInt(map.get("z"));
            if (x == null || y == null || z == null) {
                plugin.getLogger().warning("[Crate] Location invalide (x/y/z manquant) world=" + world);
                continue;
            }
            Object labelObj = map.get("label");
            String label = labelObj != null ? String.valueOf(labelObj) : "";
            result.add(new CrateLocation(world, x, y, z, label));
        }
        return result;
    }

    private static Integer toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return (int) Math.floor(Double.parseDouble(s.trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    // ─── Mailbox (VOTE) ───

    public boolean hasMailboxLocations() {
        return !mailboxLocations.isEmpty();
    }

    public boolean isValidMailbox(Block block) {
        if (block == null || !mailboxBlocks.contains(block.getType())) {
            return false;
        }
        if (mailboxLocations.isEmpty()) {
            return true;
        }
        return matchesAny(block, mailboxLocations);
    }

    // ─── Chest (RARE / Trésor Public) ───

    public boolean hasChestLocations() {
        return !chestLocations.isEmpty();
    }

    public boolean isTreasureChestMaterial(Block block) {
        return block != null && chestBlocks.contains(block.getType());
    }

    /** Coffre configuré (matériau + position). False si aucune location (legacy). */
    public boolean isConfiguredTreasureChest(Block block) {
        if (!hasChestLocations() || !isTreasureChestMaterial(block)) {
            return false;
        }
        return matchesAny(block, chestLocations);
    }

    /**
     * Ouverture RARE autorisée sur ce bloc.
     * Locations vides → legacy (le handler accepte tout clic).
     */
    public boolean isValidTreasureChest(Block block) {
        if (!hasChestLocations()) {
            return true;
        }
        return isConfiguredTreasureChest(block);
    }

    // ─── Fountain (MYTHIC) ───

    public boolean hasFountainZones() {
        return !fountainZones.isEmpty();
    }

    /** Restriction active (zones ou legacy point+radius). */
    public boolean hasFountainRestriction() {
        return !fountainZones.isEmpty() || !fountainLocations.isEmpty();
    }

    public double getFountainRadius() {
        return fountainRadius;
    }

    /**
     * Position valide pour une offrande MYTHIC (après détection eau côté handler).
     * Priorité : zones cubiques → legacy point+radius → toute eau.
     */
    public boolean isValidFountainLocation(Location loc) {
        if (!fountainZones.isEmpty()) {
            for (FountainZone zone : fountainZones) {
                if (zone.contains(loc)) {
                    return true;
                }
            }
            return false;
        }
        if (!fountainLocations.isEmpty()) {
            for (CrateLocation crateLoc : fountainLocations) {
                if (crateLoc.isNear(loc, fountainRadius)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    // ─── Altar (EPIC) ───

    public boolean hasAltarLocations() {
        return !altarLocations.isEmpty();
    }

    public boolean isValidAltar(Block block) {
        if (block == null || !altarBlocks.contains(block.getType())) {
            return false;
        }
        if (altarLocations.isEmpty()) {
            return true;
        }
        return matchesAny(block, altarLocations);
    }

    private static boolean matchesAny(Block block, List<CrateLocation> locations) {
        for (CrateLocation loc : locations) {
            if (loc.matchesBlock(block)) {
                return true;
            }
        }
        return false;
    }

    public List<CrateLocation> getMailboxLocations() {
        return Collections.unmodifiableList(mailboxLocations);
    }

    public List<CrateLocation> getChestLocations() {
        return Collections.unmodifiableList(chestLocations);
    }

    public List<FountainZone> getFountainZones() {
        return Collections.unmodifiableList(fountainZones);
    }

    public List<CrateLocation> getFountainLocations() {
        return Collections.unmodifiableList(fountainLocations);
    }

    public List<CrateLocation> getAltarLocations() {
        return Collections.unmodifiableList(altarLocations);
    }
}
