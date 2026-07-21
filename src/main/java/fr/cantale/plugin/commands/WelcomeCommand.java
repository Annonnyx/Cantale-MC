package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WelcomeCommand implements CommandExecutor {

    private static final double WELCOME_REWARD = 100.0;
    private static final int WELCOME_WINDOW_SECONDS = 300; // 5 minutes après la première connexion

    private final CantalePlugin plugin;
    private final ConcurrentHashMap<UUID, Long> firstJoinTimestamps = new ConcurrentHashMap<>();
    private final List<String> welcomeMessages;
    private final SecureRandom random = new SecureRandom();

    public WelcomeCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.welcomeMessages = List.of(
                "&eBienvenue à toi, &b%player%&e ! Profite bien de ton aventure sur Cantale !",
                "&aSalut &b%player%&a ! Que la chance soit avec toi dans les terres de Cantale !",
                "&6%player% &7vient de poser le pied sur Cantale, souhaitons-lui la bienvenue !",
                "&b%player%&3 a rejoint la partie. Le monde de Cantale s'agrandit !",
                "&dBienvenue &b%player%&d ! Prépare ton stuff, l'aventure commence maintenant !",
                "&7Un nouveau héros arrive... &eBienvenue %player%&7 !",
                "&c%player% &7a rejoint Cantale. Faites attention, il a l'air déterminé !",
                "&aBienvenue &e%player%&a ! N'hésite pas à rejoindre notre Discord pour de l'aide !",
                "&6%player% &7fait son entrée en scène. Applaudissez bien fort !",
                "&eBienvenue &b%player%&e ! Ici, chaque jour est une nouvelle bataille !"
        );
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player welcomer)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length < 1) {
            welcomer.sendMessage("§8[§6Cantale§8] §cUsage: /bvn <pseudo>");
            return true;
        }

        Player newcomer = Bukkit.getPlayerExact(args[0]);
        if (newcomer == null || !newcomer.isOnline()) {
            welcomer.sendMessage("§8[§6Cantale§8] §cCe joueur n'est pas en ligne.");
            return true;
        }

        if (newcomer.equals(welcomer)) {
            welcomer.sendMessage("§8[§6Cantale§8] §cTu ne peux pas te souhaiter la bienvenue toi-même !");
            return true;
        }

        if (!canBeWelcomed(newcomer.getUniqueId())) {
            welcomer.sendMessage("§8[§6Cantale§8] §cCe joueur n'est plus considéré comme nouveau (plus de 5 minutes après sa première connexion).");
            return true;
        }

        if (hasAlreadyWelcomed(welcomer.getUniqueId(), newcomer.getUniqueId())) {
            welcomer.sendMessage("§8[§6Cantale§8] §cTu as déjà souhaité la bienvenue à ce joueur.");
            return true;
        }

        // Envoyer un message aléatoire de bienvenue
        String rawMessage = welcomeMessages.get(random.nextInt(welcomeMessages.size()));
        String message = ChatColor.translateAlternateColorCodes('&', rawMessage.replace("%player%", newcomer.getName()));
        Bukkit.broadcastMessage("§8[§6Bienvenue§8] " + message);

        // Récompenser le joueur qui souhaite la bienvenue
        plugin.getEconomyManager().deposit(welcomer, WELCOME_REWARD);
        welcomer.sendMessage("§8[§6Cantale§8] §aTu as gagné §e" + (int) WELCOME_REWARD + " Cantox §apour avoir souhaité la bienvenue à §e" + newcomer.getName() + "§a.");

        markWelcomed(welcomer.getUniqueId(), newcomer.getUniqueId());
        return true;
    }

    /**
     * Enregistre qu'un joueur vient de rejoindre le serveur pour la première fois.
     */
    public void registerNewcomer(UUID uuid) {
        firstJoinTimestamps.put(uuid, System.currentTimeMillis());
    }

    /**
     * Vérifie si le joueur est encore dans la fenêtre de bienvenue.
     */
    public boolean canBeWelcomed(UUID uuid) {
        Long joinTime = firstJoinTimestamps.get(uuid);
        if (joinTime == null) return false;
        return System.currentTimeMillis() - joinTime <= WELCOME_WINDOW_SECONDS * 1000L;
    }

    /**
     * Supprime un joueur du suivi des nouveaux (déconnexion ou fin de fenêtre).
     */
    public void unregisterNewcomer(UUID uuid) {
        firstJoinTimestamps.remove(uuid);
        welcomedPairs.entrySet().removeIf(entry -> entry.getKey().endsWith("_" + uuid.toString()));
    }

    private final ConcurrentHashMap<String, Boolean> welcomedPairs = new ConcurrentHashMap<>();

    private String pairKey(UUID welcomer, UUID newcomer) {
        return welcomer.toString() + "_" + newcomer.toString();
    }

    private boolean hasAlreadyWelcomed(UUID welcomer, UUID newcomer) {
        return welcomedPairs.containsKey(pairKey(welcomer, newcomer));
    }

    private void markWelcomed(UUID welcomer, UUID newcomer) {
        welcomedPairs.put(pairKey(welcomer, newcomer), true);
    }
}
