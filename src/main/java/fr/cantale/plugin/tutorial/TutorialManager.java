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
     * Retourne le message du tutoriel selon la progression
     */
    private String getTutorialMessage(int progress) {
        switch (progress) {
            case 0:
                return "&eBienvenue sur Cantale !\n&7Ce serveur est un serveur de factions avec des mécaniques uniques.\n&7Créez une faction avec /f create <nom> pour commencer.";
            case 10:
                return "&eLes Factions\n&7Les factions vous permettent de claim des terres et de vous battre pour le territoire.\n&7Utilisez /f claim pour claim votre chunk actuel.";
            case 20:
                return "&eLe Pouvoir\n&7Le pouvoir de votre faction détermine vos limites (claims, fhomes, warps).\n&7Plus vous avez de membres actifs, plus votre pouvoir augmente.";
            case 30:
                return "&eLes Vies\n&7Vous avez 3 vies au départ. Si vous perdez toutes vos vies, vous serez banni !\n&7Gagnez des vies en tuant des mobs ou en recevant des vies d'autres joueurs.";
            case 40:
                return "&eL'Économie\n&7La monnaie est le Cantox. Gagnez-en en vendant des ressources ou en minant.\n&7Utilisez /balance pour voir votre solde.";
            case 50:
                return "&eLes Warps\n&7Utilisez /warp <nom> pour vous téléporter à des points d'intérêt.\n&7Utilisez /setwarp <nom> pour créer vos propres warps.";
            case 60:
                return "&eLe RTP\n&7Utilisez /rtp pour vous téléporter aléatoirement dans le monde.\n&7Attention : cooldown de 1 minute !";
            case 70:
                return "&eL'Auction House\n&7Utilisez /ah pour acheter et vendre des objets aux autres joueurs.\n&7C'est un excellent moyen de gagner des Cantox.";
            case 80:
                return "&eLa Protection\n&7Le spawn et les warps sont protégés : impossible de claim ou build autour.\n&7Respectez les zones protégées !";
            case 90:
                return "&eRègles Importantes\n&7- Pas de griefing en dehors des guerres\n&7- Pas de cheats ou hacks\n&7- Respectez les autres joueurs\n\n&eVous y êtes presque !";
            default:
                return "&eFélicitations !";
        }
    }
}
