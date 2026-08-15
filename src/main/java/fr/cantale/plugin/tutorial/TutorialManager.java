package fr.cantale.plugin.tutorial;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TutorialManager {

    private final CantalePlugin plugin;
    private final PlayerDAO playerDAO;
    private final Map<UUID, Integer> tutorialProgress;

    public TutorialManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.playerDAO = new PlayerDAO(plugin);
        this.tutorialProgress = new HashMap<>();
    }

    /**
     * Vérifie si le joueur a besoin du tutoriel
     */
    public boolean needsTutorial(UUID uuid) {
        return playerDAO.getTutorialProgress(uuid) < 100; // 100 = terminé
    }

    /**
     * Démarre le tutoriel pour un joueur
     */
    public void startTutorial(Player player) {
        UUID uuid = player.getUniqueId();
        int progress = playerDAO.getTutorialProgress(uuid);
        tutorialProgress.put(uuid, progress);
        
        sendTutorialStep(player, progress);
    }

    /**
     * Passe au step suivant du tutoriel
     */
    public void nextStep(Player player) {
        UUID uuid = player.getUniqueId();
        int currentProgress = tutorialProgress.getOrDefault(uuid, 0);
        int nextProgress = currentProgress + 10;
        
        if (nextProgress > 100) {
            completeTutorial(player);
            return;
        }
        
        tutorialProgress.put(uuid, nextProgress);
        playerDAO.setTutorialProgress(uuid, nextProgress);
        sendTutorialStep(player, nextProgress);
    }

    /**
     * Complète le tutoriel
     */
    public void completeTutorial(Player player) {
        UUID uuid = player.getUniqueId();
        tutorialProgress.put(uuid, 100);
        playerDAO.setTutorialProgress(uuid, 100);
        
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&a&l✓ Tutoriel terminé !"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&eVous avez reçu &61000 Cantox &eet &610 vies &epour avoir complété le tutoriel !"));
        
        // Récompenses
        plugin.getEconomyManager().deposit(player, 1000);
        plugin.getLifeManager().addLives(uuid, 10);
    }

    /**
     * Envoie le message du step actuel
     */
    private void sendTutorialStep(Player player, int progress) {
        String message = getTutorialMessage(progress);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&6=== TUTORIEL CANTALE ==="));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&eTapez /tutoriel pour continuer"));
    }

    /**
     * Retourne le message du tutoriel selon la progression.
     * Les couleurs sont choisies pour être lisibles sur le fond clair du chat Minecraft.
     * Pas de jaune (&e) sur blanc, on utilise du bleu foncé (&1/&3), du rouge (&c) et du gris (&8).
     */
    private String getTutorialMessage(int progress) {
        switch (progress) {
            case 0:
                return "&1&lBienvenue sur Cantale !\n&8Un serveur Hardcore Faction avec des mécaniques uniques.\n&8Crée ta faction avec &3/f create <nom> &8pour commencer.";
            case 10:
                return "&1&lLes Factions\n&8Les factions te permettent de claim des terres et de te battre pour le territoire.\n&8Utilise &3/f claim &8pour claim ton chunk actuel.";
            case 20:
                return "&1&lLe Pouvoir\n&8Le pouvoir de ta faction détermine tes limites : claims, warps, hoppers.\n&8Chaque membre apporte +5 de pouvoir (création : 60 de base + 5 pour le chef).";
            case 30:
                return "&c&lLes Vies\n&8Tu as 3 vies au départ. Si tu perds toutes tes vies, tu es banni !\n&8Gagne des vies en tuant des mobs, en votant ou via d'autres joueurs.";
            case 40:
                return "&1&lL'Économie\n&8La monnaie est le &3Cantox&8. Gagne-en en vendant des ressources, en minant ou en votant.\n&8Utilise &3/balance &8pour voir ton solde.";
            case 50:
                return "&1&lLes Warps\n&8Utilise &3/warp <nom> &8pour te téléporter à un point d'intérêt.\n&8Utilise &3/setwarp <nom> &8pour créer tes propres warps.";
            case 60:
                return "&1&lLe RTP\n&8Utilise &3/rtp &8pour te téléporter aléatoirement dans le monde.\n&cAttention : cooldown de quelques minutes !";
            case 70:
                return "&1&lL'Hôtel des Ventes\n&8Utilise &3/ah &8pour acheter et vendre des objets aux autres joueurs.\n&8C'est un excellent moyen de gagner des Cantox.";
            case 80:
                return "&1&lLa Protection\n&8Le spawn et les warps sont protégés : impossible de claim ou build autour.\n&8Respecte les zones protégées !";
            case 90:
                return "&c&lRègles Importantes\n&8- Pas de griefing en dehors des guerres\n&8- Pas de cheats ou hacks\n&8- Respecte les autres joueurs\n\n&3Tu y es presque !";
            default:
                return "&3&lFélicitations !";
        }
    }
}
