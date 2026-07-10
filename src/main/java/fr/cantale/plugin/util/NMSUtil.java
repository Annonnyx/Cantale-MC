package fr.cantale.plugin.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;

public class NMSUtil {
    private static final String CB_PACKAGE;
    private static Class<?> craftPlayerClass;
    private static Class<?> craftItemStackClass;
    private static Class<?> packetSetSlotClass;
    private static Class<?> nmsItemStackClass;
    private static Method getHandleMethod;
    private static Method asNMSCopyMethod;
    private static Field connectionField;
    private static Field containerMenuField;
    private static Field containerIdField;
    private static Method getStateIdMethod;
    private static Method incrementStateIdMethod;
    private static Method sendMethod;
    private static boolean available = false;

    static {
        CB_PACKAGE = Bukkit.getServer().getClass().getPackage().getName();
        try {
            craftPlayerClass = Class.forName(CB_PACKAGE + ".entity.CraftPlayer");
            craftItemStackClass = Class.forName(CB_PACKAGE + ".inventory.CraftItemStack");
            ClassLoader serverClassLoader = Bukkit.getServer().getClass().getClassLoader();
            packetSetSlotClass = Class.forName("net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket", true, serverClassLoader);
            nmsItemStackClass = Class.forName("net.minecraft.world.item.ItemStack", true, serverClassLoader);

            getHandleMethod = craftPlayerClass.getMethod("getHandle");
            asNMSCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);

            Class<?> serverPlayerClass = getHandleMethod.getReturnType();
            connectionField = serverPlayerClass.getField("connection");
            containerMenuField = serverPlayerClass.getField("containerMenu");

            Class<?> abstractContainerMenuClass = containerMenuField.getType();
            getStateIdMethod = abstractContainerMenuClass.getMethod("getStateId");
            incrementStateIdMethod = abstractContainerMenuClass.getDeclaredMethod("incrementStateId");
            incrementStateIdMethod.setAccessible(true);

            try {
                containerIdField = abstractContainerMenuClass.getDeclaredField("containerId");
                containerIdField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                for (Field f : abstractContainerMenuClass.getDeclaredFields()) {
                    if (f.getType() == int.class) {
                        f.setAccessible(true);
                        containerIdField = f;
                        break;
                    }
                }
            }

            Class<?> connectionClass = connectionField.getType();
            for (Method m : connectionClass.getMethods()) {
                if (m.getName().equals("send") && m.getParameterCount() == 1) {
                    Class<?> param = m.getParameterTypes()[0];
                    if (param.getName().contains("Packet")) {
                        sendMethod = m;
                        break;
                    }
                }
            }

            available = sendMethod != null;
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[Cantale] NMS reflection failed, visual adapter disabled: " + e.getMessage());
            available = false;
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static void sendVisualSlot(Player player, int slot, ItemStack item) {
        if (!available) return;
        try {
            Object serverPlayer = getHandleMethod.invoke(player);
            Object connection = connectionField.get(serverPlayer);
            Object containerMenu = containerMenuField.get(serverPlayer);
            int stateId = (Integer) getStateIdMethod.invoke(containerMenu);

            Object nmsItem;
            if (item == null || item.getType() == org.bukkit.Material.AIR) {
                nmsItem = asNMSCopyMethod.invoke(null, new ItemStack(org.bukkit.Material.AIR));
            } else {
                nmsItem = asNMSCopyMethod.invoke(null, item);
            }

            int containerId = containerIdField != null ? (Integer) containerIdField.get(containerMenu) : 0;
            Constructor<?> ctor = packetSetSlotClass.getConstructor(int.class, int.class, int.class, nmsItemStackClass);
            Object packet = ctor.newInstance(containerId, stateId, slot, nmsItem);
            sendMethod.invoke(connection, packet);

            // Increment server stateId to keep client in sync
            incrementStateIdMethod.invoke(containerMenu);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[Cantale] Failed to send visual slot packet", e);
        }
    }
}
