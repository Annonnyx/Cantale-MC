package fr.cantale.plugin.ui;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.ui.chat.ChatMenu;
import fr.cantale.plugin.ui.chat.FactionChatUI;
import fr.cantale.plugin.ui.chat.ProfileChatUI;
import fr.cantale.plugin.ui.inventory.*;
import org.bukkit.entity.Player;

public class UIManager {

    private final CantalePlugin plugin;
    private final ChatMenu chatMenu;
    private final NavigationManager navigationManager;

    public UIManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.chatMenu = new ChatMenu(plugin);
        this.navigationManager = new NavigationManager();
    }

    public NavigationManager getNavigationManager() {
        return navigationManager;
    }

    public void openMainMenu(Player player) {
        navigationManager.openUI(player, new MainMenuUI(plugin, player));
    }

    public void goBack(Player player) {
        navigationManager.goBack(player);
    }

    public void openFactionMenu(Player player) {
        navigationManager.openUI(player, new FactionMainUI(plugin, player));
    }

    public void openProfileMenu(Player player) {
        openProfileMenu(player, player);
    }

    public void openProfileMenu(Player viewer, org.bukkit.OfflinePlayer target) {
        navigationManager.openUI(viewer, new ProfileUI(plugin, viewer, target));
    }

    public void openQuestMenu(Player player) {
        navigationManager.openUI(player, new QuestMainUI(plugin, player));
    }

    public void openFactionQuests(Player player) {
        navigationManager.openUI(player, new QuestFactionUI(plugin, player));
    }

    public void openLeaderboardMenu(Player player) {
        new LeaderboardUI(plugin, player).open();
    }

    public void openAuctionMenu(Player player) {
        navigationManager.openUI(player, new AuctionUI(plugin, player));
    }

    public void openTradeMenu(Player player) {
        navigationManager.openUI(player, new TradeUI(plugin, player));
    }

    public ChatMenu getChatMenu() {
        return chatMenu;
    }
}
