package fr.cantale.plugin.world;

/**
 * Filons déterministes. Le cycle change les positions : un reset repose des minerais
 * sur la pierre et l'air, pas sur les coffres ou les constructions.
 */
public final class HarvestLayout {

    public static final int RESOURCE_MIN_Y = 16;
    public static final int RESOURCE_MAX_Y = 48;
    public static final int RARE_MIN_Y = 8;
    public static final int RARE_MAX_Y = 28;

    private HarvestLayout() {
    }

    /**
     * @return nom de matériau Bukkit, ou {@code null} si ce bloc n'est pas un filon pour ce cycle
     */
    public static String nodeId(int x, int y, int z, long cycle, boolean rare) {
        if (rare) {
            if (y < RARE_MIN_Y || y > RARE_MAX_Y) {
                return null;
            }
        } else if (y < RESOURCE_MIN_Y || y > RESOURCE_MAX_Y) {
            return null;
        }
        long hash = mix(x, y, z, cycle);
        int modulus = rare ? 409 : 181;
        if (Math.floorMod(hash, modulus) != 0) {
            return null;
        }
        int kind = (int) Math.floorMod(hash / modulus, rare ? 3 : 4);
        if (rare) {
            return switch (kind) {
                case 0 -> "DIAMOND_ORE";
                case 1 -> "EMERALD_ORE";
                default -> "ANCIENT_DEBRIS";
            };
        }
        return switch (kind) {
            case 0 -> "IRON_ORE";
            case 1 -> "COAL_ORE";
            case 2 -> "COPPER_ORE";
            default -> "GOLD_ORE";
        };
    }

    private static long mix(int x, int y, int z, long cycle) {
        long hash = 0x9E3779B97F4A7C15L;
        hash ^= x * 0xBF58476D1CE4E5B9L;
        hash = Long.rotateLeft(hash, 17);
        hash ^= y * 0x94D049BB133111EBL;
        hash = Long.rotateLeft(hash, 13);
        hash ^= z * 0xD1B54A32D192ED03L;
        hash ^= cycle * 0x2545F4914F6CDD1DL;
        hash ^= hash >>> 32;
        return hash;
    }
}
