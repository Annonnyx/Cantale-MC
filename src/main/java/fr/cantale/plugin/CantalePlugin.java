package fr.cantale.plugin;

import org.bukkit.Bukkit;
import fr.cantale.plugin.config.ConfigManager;
import fr.cantale.plugin.database.DatabaseManager;
import fr.cantale.plugin.lives.LifeManager;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.economy.EconomyManager;
import fr.cantale.plugin.economy.BankManager;
import fr.cantale.plugin.auction.AuctionManager;
import fr.cantale.plugin.trade.TradeManager;
import fr.cantale.plugin.custom.CustomItemManager;
import fr.cantale.plugin.custom.CustomItemListener;
import fr.cantale.plugin.ui.UIManager;
import fr.cantale.plugin.ui.map.MapUI;
import fr.cantale.plugin.teleport.HomeManager;
import fr.cantale.plugin.teleport.WarpManager;
import fr.cantale.plugin.listeners.ClaimListener;
import fr.cantale.plugin.listeners.PlayerListener;
import fr.cantale.plugin.listeners.ResourcePackStatusListener;
import fr.cantale.plugin.listeners.ChatListener;
import fr.cantale.plugin.listeners.AfkListener;
import fr.cantale.plugin.discord.DiscordBot;
import fr.cantale.plugin.discord.DiscordConfig;
import fr.cantale.plugin.tasks.AfkCheckTask;
import fr.cantale.plugin.commands.CantaleCommand;
import fr.cantale.plugin.commands.LifeCommand;
import fr.cantale.plugin.commands.ListCommand;
import fr.cantale.plugin.commands.AuctionCommand;
import fr.cantale.plugin.commands.TradeCommand;
import fr.cantale.plugin.commands.PayCommand;
import fr.cantale.plugin.commands.BalanceCommand;
import fr.cantale.plugin.commands.FactionCommand;
import fr.cantale.plugin.commands.ProfileCommand;
import fr.cantale.plugin.commands.CustomItemCommand;
import fr.cantale.plugin.commands.HomeCommand;
import fr.cantale.plugin.commands.WarpCommand;
import fr.cantale.plugin.commands.EventsCommand;
import fr.cantale.plugin.commands.SpawnCommand;
import fr.cantale.plugin.commands.RtpCommand;
import fr.cantale.plugin.commands.ModerationCommand;
import fr.cantale.plugin.commands.WantedCommand;
import fr.cantale.plugin.commands.PrivateChestCommand;
import fr.cantale.plugin.commands.AdminCommand;
import fr.cantale.plugin.commands.PasdicCommand;
import fr.cantale.plugin.commands.ModHelpCommand;
import fr.cantale.plugin.commands.VanishCommand;
import fr.cantale.plugin.commands.PermissionCommand;
import fr.cantale.plugin.commands.UnbanIpCommand;
import fr.cantale.plugin.commands.LeaderboardCommand;
import fr.cantale.plugin.commands.LastDeathCommand;
import fr.cantale.plugin.commands.OptimizeCommand;
import fr.cantale.plugin.rank.RankManager;
import fr.cantale.plugin.rank.PlayerRank;
import fr.cantale.plugin.commands.RankCommand;
import fr.cantale.plugin.commands.EnderChestCommand;
import fr.cantale.plugin.commands.Pc2Command;
import fr.cantale.plugin.commands.DayCommand;
import fr.cantale.plugin.serialkiller.SerialKillerManager;
import fr.cantale.plugin.map.MapIntegrationListener;
import fr.cantale.plugin.cooldown.CooldownManager;
import fr.cantale.plugin.tasks.ClearLagTask;
import fr.cantale.plugin.tasks.TerritoryTaxTask;
import fr.cantale.plugin.protection.SpawnProtection;
import fr.cantale.plugin.moderation.ActionLogger;
import fr.cantale.plugin.performance.ChunkLoader;
import fr.cantale.plugin.performance.AsyncTaskManager;
import fr.cantale.plugin.tutorial.TutorialManager;
import fr.cantale.plugin.npc.NPCManager;
import fr.cantale.plugin.diplomacy.DiplomacyManager;
import fr.cantale.plugin.cache.RedisCache;
import fr.cantale.plugin.permissions.PermissionManager;
import fr.cantale.plugin.optimization.ServerOptimizer;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;

public class CantalePlugin extends JavaPlugin {

    private static CantalePlugin instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private LifeManager lifeManager;
    private FactionManager factionManager;
    private EconomyManager economyManager;
    private AuctionManager auctionManager;
    private BankManager bankManager;
    private TradeManager tradeManager;
    private HomeManager homeManager;
    private WarpManager warpManager;
    private MapUI mapUI;
    private SpawnCommand spawnCommand;
    private RtpCommand rtpCommand;
    private SpawnProtection spawnProtection;
    private AfkListener afkListener;
    private ActionLogger actionLogger;
    private ChunkLoader chunkLoader;
    private AsyncTaskManager asyncTaskManager;
    private TutorialManager tutorialManager;
    private NPCManager npcManager;
    private DiplomacyManager diplomacyManager;
    private RedisCache redisCache;
    private CooldownManager cooldownManager;
    private AdminCommand adminCommand;
    private ModerationCommand moderationCommand;
    private WantedCommand wantedCommand;
    private PrivateChestCommand privateChestCommand;
    private PasdicCommand pasdicCommand;
    private VanishCommand vanishCommand;
    private PermissionManager permissionManager;
    private RankManager rankManager;
    private UIManager uiManager;
    private fr.cantale.plugin.commands.WelcomeCommand welcomeCommand;
    private fr.cantale.plugin.crate.CrateManager crateManager;
    private fr.cantale.plugin.crate.LegendaryTicketManager legendaryTicketManager;
    private CustomItemManager customItemManager;
    private ServerOptimizer serverOptimizer;
    private fr.cantale.plugin.database.FortifiedChunkDAO fortifiedChunkDAO;
    private fr.cantale.plugin.vote.VoteDAO voteDAO;
    private fr.cantale.plugin.vote.VoteRewardManager voteRewardManager;
    private fr.cantale.plugin.vote.VoteListener voteListener;
    private DiscordBot discordBot;
    private fr.cantale.plugin.chatbot.faq.FaqCore faqCore;
    private fr.cantale.plugin.tag.TagManager tagManager;
    private fr.cantale.plugin.tasks.ClearLagTask clearLagTask;
    private fr.cantale.plugin.teleport.TeleportDelayManager teleportDelayManager;
    private fr.cantale.plugin.anticheat.AntiCheatManager antiCheatManager;
    private fr.cantale.plugin.combat.CombatTagManager combatTagManager;
    private fr.cantale.plugin.shop.ShopManager shopManager;
    private fr.cantale.plugin.events.EventSystemsManager eventSystemsManager;
    private fr.cantale.plugin.ui.bossbar.RotatingBossBarManager rotatingBossBarManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.factionManager = new FactionManager(this);
        this.databaseManager.init();
        // Si la DB n'est pas disponible au démarrage, planifier des reconnexions asynchrones
        if (!this.databaseManager.isConnected()) {
            getLogger().warning("DB indisponible — planification des tentatives de reconnexion automatiques.");
            this.databaseManager.scheduleReconnect(12, 20L * 5, 20L * 10);
        }

        this.lifeManager = new LifeManager(this);
        this.economyManager = new EconomyManager(this);
        this.bankManager = new BankManager(this);
        this.auctionManager = new AuctionManager(this);
        // Charger les listings d'enchères uniquement si la BDD est disponible.
        if (this.databaseManager.getConnection() != null) {
            this.auctionManager.loadListings();
        } else {
            getLogger().warning("Base de données non disponible — chargement des listings d'enchères différé.");
            // Réessayer périodiquement (toutes les 10s) jusqu'à succès (max 12 tentatives = 2 minutes)
            new BukkitRunnable() {
                private int attempts = 0;

                @Override
                public void run() {
                    attempts++;
                    if (databaseManager.getConnection() != null) {
                        try {
                            auctionManager.loadListings();
                            getLogger().info("Listings d'enchères chargés après reconnexion DB.");
                        } catch (Exception e) {
                            getLogger().warning("Erreur lors du chargement des listings après reconnexion : " + e.getMessage());
                        }
                        this.cancel();
                        return;
                    }
                    if (attempts >= 12) {
                        getLogger().severe("Impossible de charger les listings d'enchères : la BDD reste inaccessible après plusieurs tentatives.");
                        this.cancel();
                    }
                }
            }.runTaskTimerAsynchronously(this, 20L * 5, 20L * 10);
        }
        this.tradeManager = new TradeManager(this);
        this.homeManager = new HomeManager(this);
        this.warpManager = new WarpManager(this);
        if (this.databaseManager.getConnection() != null) {
            this.warpManager.loadWarps();
        } else {
            getLogger().warning("DB inaccessible — chargement des warps différé.");
            new BukkitRunnable() {
                private int attempts = 0;
                @Override
                public void run() {
                    attempts++;
                    if (databaseManager.getConnection() != null) {
                        try {
                            warpManager.loadWarps();
                            getLogger().info("Warps chargés après reconnexion DB.");
                        } catch (Exception e) {
                            getLogger().warning("Erreur lors du chargement des warps après reconnexion : " + e.getMessage());
                        }
                        this.cancel();
                        return;
                    }
                    if (attempts >= 12) {
                        getLogger().severe("Impossible de charger les warps : la BDD reste inaccessible.");
                        this.cancel();
                    }
                }
            }.runTaskTimerAsynchronously(this, 20L * 5, 20L * 10);
        }
        this.mapUI = new MapUI(this);
        this.cooldownManager = new CooldownManager();
        this.spawnCommand = new SpawnCommand(this);
        this.rtpCommand = new RtpCommand(this);
        this.spawnProtection = new SpawnProtection(this);
        this.spawnProtection.loadProtectedZones();
        this.afkListener = new AfkListener(this);
        this.actionLogger = new ActionLogger(this);
        this.chunkLoader = new ChunkLoader(this);
        this.asyncTaskManager = new AsyncTaskManager(this);
        this.tutorialManager = new TutorialManager(this);
        this.npcManager = new NPCManager(this);
        if (this.databaseManager.getConnection() != null) {
            this.npcManager.loadNPCs();
        } else {
            getLogger().warning("DB inaccessible — chargement des NPC différé.");
            new BukkitRunnable() {
                private int attempts = 0;
                @Override
                public void run() {
                    attempts++;
                    if (databaseManager.getConnection() != null) {
                        try {
                            npcManager.loadNPCs();
                            getLogger().info("NPC chargés après reconnexion DB.");
                        } catch (Exception e) {
                            getLogger().warning("Erreur lors du chargement des NPC après reconnexion : " + e.getMessage());
                        }
                        this.cancel();
                        return;
                    }
                    if (attempts >= 12) {
                        getLogger().severe("Impossible de charger les NPC : la BDD reste inaccessible.");
                        this.cancel();
                    }
                }
            }.runTaskTimerAsynchronously(this, 20L * 5, 20L * 10);
        }
        this.diplomacyManager = new DiplomacyManager(this);
        if (this.databaseManager.getConnection() != null) {
            this.diplomacyManager.loadDiplomacy();
        } else {
            getLogger().warning("DB inaccessible — chargement de la diplomatie différé.");
            new BukkitRunnable() {
                private int attempts = 0;
                @Override
                public void run() {
                    attempts++;
                    if (databaseManager.getConnection() != null) {
                        try {
                            diplomacyManager.loadDiplomacy();
                            getLogger().info("Diplomatie chargée après reconnexion DB.");
                        } catch (Exception e) {
                            getLogger().warning("Erreur lors du chargement de la diplomatie après reconnexion : " + e.getMessage());
                        }
                        this.cancel();
                        return;
                    }
                    if (attempts >= 12) {
                        getLogger().severe("Impossible de charger la diplomatie : la BDD reste inaccessible.");
                        this.cancel();
                    }
                }
            }.runTaskTimerAsynchronously(this, 20L * 5, 20L * 10);
        }
        this.redisCache = new RedisCache(this);
        this.redisCache.init();
        this.adminCommand = new AdminCommand(this);
        this.moderationCommand = new ModerationCommand(this);
        if (this.databaseManager.getConnection() != null) {
            this.moderationCommand.getMuteManager().loadMutes();
        } else {
            getLogger().warning("DB inaccessible — chargement des mutes différé.");
            new BukkitRunnable() {
                private int attempts = 0;
                @Override
                public void run() {
                    attempts++;
                    if (databaseManager.getConnection() != null) {
                        try {
                            moderationCommand.getMuteManager().loadMutes();
                            getLogger().info("Mutes chargés après reconnexion DB.");
                        } catch (Exception e) {
                            getLogger().warning("Erreur lors du chargement des mutes après reconnexion : " + e.getMessage());
                        }
                        this.cancel();
                        return;
                    }
                    if (attempts >= 12) {
                        getLogger().severe("Impossible de charger les mutes : la BDD reste inaccessible.");
                        this.cancel();
                    }
                }
            }.runTaskTimerAsynchronously(this, 20L * 5, 20L * 10);
        }
        this.wantedCommand = new WantedCommand(this);
        if (this.databaseManager.getConnection() != null) {
            this.wantedCommand.getWantedManager().loadWanted();
        } else {
            getLogger().warning("DB inaccessible — chargement des primes (wanted) différé.");
            new BukkitRunnable() {
                private int attempts = 0;
                @Override
                public void run() {
                    attempts++;
                    if (databaseManager.getConnection() != null) {
                        try {
                            wantedCommand.getWantedManager().loadWanted();
                            getLogger().info("Primes chargées après reconnexion DB.");
                        } catch (Exception e) {
                            getLogger().warning("Erreur lors du chargement des primes après reconnexion : " + e.getMessage());
                        }
                        this.cancel();
                        return;
                    }
                    if (attempts >= 12) {
                        getLogger().severe("Impossible de charger les primes : la BDD reste inaccessible.");
                        this.cancel();
                    }
                }
            }.runTaskTimerAsynchronously(this, 20L * 5, 20L * 10);
        }
        this.privateChestCommand = new PrivateChestCommand(this);
        this.pasdicCommand = new PasdicCommand(this);
        this.vanishCommand = new VanishCommand(this.moderationCommand.getVanishManager());
        this.permissionManager = new PermissionManager(this);
        this.rankManager = new RankManager(this);
        this.welcomeCommand = new fr.cantale.plugin.commands.WelcomeCommand(this);
        this.tagManager = new fr.cantale.plugin.tag.TagManager(this);
        this.teleportDelayManager = new fr.cantale.plugin.teleport.TeleportDelayManager(this);
        this.uiManager = new UIManager(this);
        this.customItemManager = new CustomItemManager();
        this.crateManager = new fr.cantale.plugin.crate.CrateManager(this);
        this.legendaryTicketManager = new fr.cantale.plugin.crate.LegendaryTicketManager(this);
        this.fortifiedChunkDAO = new fr.cantale.plugin.database.FortifiedChunkDAO(this);
        this.shopManager = new fr.cantale.plugin.shop.ShopManager(this);
        this.voteDAO = new fr.cantale.plugin.vote.VoteDAO(this);
        this.voteRewardManager = new fr.cantale.plugin.vote.VoteRewardManager(this);
        this.voteListener = new fr.cantale.plugin.vote.VoteListener(this);
        this.serverOptimizer = new ServerOptimizer(this);
        this.serverOptimizer.startTasks();
        this.antiCheatManager = new fr.cantale.plugin.anticheat.AntiCheatManager(this);
        this.combatTagManager = new fr.cantale.plugin.combat.CombatTagManager(this);

        // Bot FAQ
        saveResource("faq.yml", false);
        this.faqCore = new fr.cantale.plugin.chatbot.faq.FaqCore(this);

        // Systèmes d'événements (chat reactions, faction events, weekly key)
        saveResource("events.yml", false);
        this.eventSystemsManager = new fr.cantale.plugin.events.EventSystemsManager(this);
        this.rotatingBossBarManager = new fr.cantale.plugin.ui.bossbar.RotatingBossBarManager(this);

        // Démarrer le bot Discord (envoie le message de startup automatiquement une fois connecté)
        DiscordConfig discordConfig = new DiscordConfig(this);
        this.discordBot = new DiscordBot(this, discordConfig, faqCore);
        this.discordBot.start();

        registerCommands();
        registerListeners();
        registerPluginMessageChannels();

        // Démarrer ClearLag (tick/seconde, clear toutes les 3 minutes, countdown dès T-30s)
        this.clearLagTask = new ClearLagTask(this);
        this.clearLagTask.runTaskTimer(this, 20L, 20L);

        // Démarrer AFK check (toutes les minutes)
        new AfkCheckTask(this, afkListener).runTaskTimer(this, 20L * 60, 20L * 60);

        // Démarrer Taxe de territoire (tous les jours = 24 heures)
        new TerritoryTaxTask(this).runTaskTimer(this, 20L * 60 * 5, 20L * 60 * 60 * 24);

        // Démarrer Wanted Tracker (toutes les 5 min pour palier 5M+)
        new fr.cantale.plugin.discord.tasks.WantedTrackerTask(this).schedule();

        // Démarrer les planificateurs d'événements
        this.eventSystemsManager.start();

        // BossBar infos joueur (rotation)
        this.rotatingBossBarManager.start();

        getLogger().info("Cantale v" + getDescription().getVersion() + " activé avec succès !");
    }

    @Override
    public void onDisable() {
        if (rotatingBossBarManager != null) {
            rotatingBossBarManager.stop();
        }
        if (eventSystemsManager != null) {
            eventSystemsManager.stop();
        }
        if (discordBot != null && discordBot.isConnected()) {
            discordBot.sendServerStatus("🛑 **Le serveur Minecraft s'arrête...**", "#ED4245",
                "Le serveur Cantale redémarre ou s'arrête. À bientôt !", true);
            discordBot.stop();
        } else if (discordBot != null) {
            discordBot.stop();
        }
        if (asyncTaskManager != null) {
            asyncTaskManager.shutdown();
        }
        if (redisCache != null) {
            redisCache.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (antiCheatManager != null) {
            antiCheatManager.shutdown();
        }
        unregisterPluginMessageChannels();
        getLogger().info("Cantale désactivé.");
    }

    private void registerCommands() {
        getCommand("cantale").setExecutor(new CantaleCommand(this));
        getCommand("admin").setExecutor(adminCommand);
        getCommand("dropvie").setExecutor(new LifeCommand(this));
        getCommand("givevie").setExecutor(new LifeCommand(this));
        getCommand("addlife").setExecutor(new LifeCommand(this));
        getCommand("removelife").setExecutor(new LifeCommand(this));
        FactionCommand factionCommand = new FactionCommand(this);
        getCommand("f").setExecutor(factionCommand);
        getCommand("fc").setExecutor(new fr.cantale.plugin.commands.FactionChatCommand(this, factionCommand));
        getCommand("ah").setExecutor(new AuctionCommand(this));
        getCommand("trade").setExecutor(new TradeCommand(this));
        getCommand("customitem").setExecutor(new CustomItemCommand(this, customItemManager));
        getCommand("home").setExecutor(new HomeCommand(this));
        getCommand("warp").setExecutor(new WarpCommand(this));
        getCommand("spawn").setExecutor(spawnCommand);
        getCommand("rtp").setExecutor(rtpCommand);
        getCommand("moderation").setExecutor(moderationCommand);
        getCommand("vanish").setExecutor(vanishCommand);
        getCommand("modhelp").setExecutor(new ModHelpCommand(this));
        getCommand("wanted").setExecutor(wantedCommand);
        getCommand("pc").setExecutor(privateChestCommand);
        getCommand("pasdic").setExecutor(pasdicCommand);
        getCommand("pay").setExecutor(new PayCommand(this));
        getCommand("balance").setExecutor(new BalanceCommand(this));
        getCommand("perm").setExecutor(new PermissionCommand(this));
        getCommand("unbanip").setExecutor(new UnbanIpCommand(this));
        getCommand("leaderboard").setExecutor(new LeaderboardCommand(this));
        getCommand("lastdeath").setExecutor(new LastDeathCommand(this));
        getCommand("optimize").setExecutor(new OptimizeCommand(this));
        getCommand("rank").setExecutor(new RankCommand(this));
        getCommand("ec").setExecutor(new EnderChestCommand(this));
        getCommand("pc2").setExecutor(new Pc2Command(this));
        getCommand("pc3").setExecutor(new Pc2Command(this));
        getCommand("day").setExecutor(new DayCommand(this));
        getCommand("daily").setExecutor(new fr.cantale.plugin.commands.DailyCommand(this));
        getCommand("bvn").setExecutor(welcomeCommand);
        fr.cantale.plugin.commands.CrateCommand crateCommand = new fr.cantale.plugin.commands.CrateCommand(this);
        getCommand("crate").setExecutor(crateCommand);
        getCommand("crate").setTabCompleter(crateCommand);
        getCommand("profile").setExecutor(new ProfileCommand(this));
        getCommand("listemorts").setExecutor(new ListCommand(this));
        getCommand("vote").setExecutor(new fr.cantale.plugin.commands.VoteCommand(this));
        getCommand("vote").setTabCompleter(new fr.cantale.plugin.commands.VoteCommand(this));
        getCommand("web").setExecutor(new fr.cantale.plugin.commands.WebCommand(this));
        getCommand("web").setTabCompleter(new fr.cantale.plugin.commands.WebCommand(this));
        getCommand("link").setExecutor(new fr.cantale.plugin.commands.LinkCommand(this));
        getCommand("events").setExecutor(new EventsCommand(this));
        getCommand("tag").setExecutor(new fr.cantale.plugin.tag.TagCommand(this));
        getCommand("tag").setTabCompleter(new fr.cantale.plugin.tag.TagCommand(this));
        getCommand("discord").setExecutor(new fr.cantale.plugin.commands.DiscordCommand(this));
        getCommand("discord").setTabCompleter(new fr.cantale.plugin.commands.DiscordCommand(this));
        getCommand("say").setExecutor(new fr.cantale.plugin.commands.OwnerSayCommand(this));
        getCommand("anticheat").setExecutor(new fr.cantale.plugin.anticheat.AntiCheatCommand(this.antiCheatManager));
        getCommand("anticheat").setTabCompleter(new fr.cantale.plugin.anticheat.AntiCheatCommand(this.antiCheatManager));
        getCommand("faq").setExecutor(new fr.cantale.plugin.commands.FaqCommand(this, faqCore));
        getCommand("shop").setExecutor(new fr.cantale.plugin.commands.ShopCommand(this));
        getCommand("shop").setTabCompleter(new fr.cantale.plugin.commands.ShopCommand(this));
        getCommand("recolte").setExecutor(new fr.cantale.plugin.commands.RecolteCommand(this));
        getCommand("feed").setExecutor(new fr.cantale.plugin.commands.FeedCommand(this));
        getCommand("db").setExecutor(new fr.cantale.plugin.commands.DbCommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ClaimListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ResourcePackStatusListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(afkListener, this);
        getServer().getPluginManager().registerEvents(new CustomItemListener(this), this);
        getServer().getPluginManager().registerEvents(new fr.cantale.plugin.moderation.ModerationListener(moderationCommand.getMuteManager(), moderationCommand.getVanishManager()), this);
        getServer().getPluginManager().registerEvents(new fr.cantale.plugin.listeners.LifeItemListener(this), this);
        getServer().getPluginManager().registerEvents(new fr.cantale.plugin.listeners.TotemBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new fr.cantale.plugin.custom.RuneFortificationListener(this), this);
        getServer().getPluginManager().registerEvents(new MapIntegrationListener(this), this);
        getServer().getPluginManager().registerEvents(new fr.cantale.plugin.optimization.VillagerSpawnBlocker(this), this);
        // Vote system : enregistre PlayerJoinEvent + tente de cabler VotifierEvent par réflexion
        getServer().getPluginManager().registerEvents(voteListener, this);
        boolean votifierOk = voteListener.tryRegisterVotifier();
        if (votifierOk) {
            getLogger().info("[Vote] Système de vote activé avec Votifier.");
        } else {
            getLogger().warning("[Vote] Votifier non détecté — les votes entrants ne seront pas traités.");
        }
        getServer().getPluginManager().registerEvents(teleportDelayManager, this);
        getServer().getPluginManager().registerEvents(new fr.cantale.plugin.privatechest.PrivateChestListener(this, privateChestCommand.getChestService()), this);
        getServer().getPluginManager().registerEvents(new fr.cantale.plugin.crate.CrateListener(this, crateManager), this);
        getServer().getPluginManager().registerEvents(new fr.cantale.plugin.chatbot.FaqChatListener(this, faqCore), this);
        getServer().getPluginManager().registerEvents(new fr.cantale.plugin.combat.CombatTagListener(this, combatTagManager), this);
        getServer().getPluginManager().registerEvents(new fr.cantale.plugin.custom.GardeArmorListener(this), this);
        if (eventSystemsManager != null) {
            getServer().getPluginManager().registerEvents(
                    new fr.cantale.plugin.events.ChatReactionListener(eventSystemsManager.getChatReactionManager()), this);
            getServer().getPluginManager().registerEvents(
                    new fr.cantale.plugin.events.FactionEventListener(eventSystemsManager.getFactionEventManager()), this);
        }
        if (rotatingBossBarManager != null) {
            getServer().getPluginManager().registerEvents(rotatingBossBarManager, this);
        }
    }

    private void registerPluginMessageChannels() {
        String[] outgoing = {
            "journeymap:waypoint",
            "journeymap:region",
            "xaerominimap:waypoint",
            "xaerominimap:region"
        };
        for (String channel : outgoing) {
            try {
                getServer().getMessenger().registerOutgoingPluginChannel(this, channel);
            } catch (Exception e) {
                getLogger().warning("[PluginMessage] Impossible d'enregistrer le canal " + channel + ": " + e.getMessage());
            }
        }
    }

    private void unregisterPluginMessageChannels() {
        String[] outgoing = {
            "journeymap:waypoint",
            "journeymap:region",
            "xaerominimap:waypoint",
            "xaerominimap:region"
        };
        for (String channel : outgoing) {
            try {
                getServer().getMessenger().unregisterOutgoingPluginChannel(this, channel);
            } catch (Exception ignored) {
            }
        }
    }

    public static CantalePlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public FactionManager getFactionManager() {
        return factionManager;
    }

    public LifeManager getLifeManager() {
        return lifeManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }

    public MapUI getMapUI() {
        return mapUI;
    }

    public SpawnProtection getSpawnProtection() {
        return spawnProtection;
    }

    public ActionLogger getActionLogger() {
        return actionLogger;
    }

    public ChunkLoader getChunkLoader() {
        return chunkLoader;
    }

    public ServerOptimizer getServerOptimizer() {
        return serverOptimizer;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public AsyncTaskManager getAsyncTaskManager() {
        return asyncTaskManager;
    }

    public TutorialManager getTutorialManager() {
        return tutorialManager;
    }

    public NPCManager getNPCManager() {
        return npcManager;
    }

    public DiplomacyManager getDiplomatieManager() {
        return diplomacyManager;
    }

    public RedisCache getRedisCache() {
        return redisCache;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public TradeManager getTradeManager() {
        return tradeManager;
    }

    public BankManager getBankManager() {
        return bankManager;
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }

    public WarpManager getWarpManager() {
        return warpManager;
    }

    public ModerationCommand getModerationCommand() {
        return moderationCommand;
    }

    public fr.cantale.plugin.database.PlayerDAO getPlayerDAO() {
        return new fr.cantale.plugin.database.PlayerDAO(this);
    }

    public fr.cantale.plugin.commands.WelcomeCommand getWelcomeCommand() {
        return welcomeCommand;
    }

    public UIManager getUIManager() {
        return uiManager;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public fr.cantale.plugin.shop.ShopManager getShopManager() {
        return shopManager;
    }

    public fr.cantale.plugin.events.EventSystemsManager getEventSystemsManager() {
        return eventSystemsManager;
    }

    public fr.cantale.plugin.events.FactionEventManager getFactionEventManager() {
        return eventSystemsManager != null ? eventSystemsManager.getFactionEventManager() : null;
    }

    public CustomItemManager getCustomItemManager() {
        return customItemManager;
    }

    public fr.cantale.plugin.crate.CrateManager getCrateManager() {
        return crateManager;
    }

    public fr.cantale.plugin.crate.LegendaryTicketManager getLegendaryTicketManager() {
        return legendaryTicketManager;
    }

    public fr.cantale.plugin.privatechest.PrivateChestService getPrivateChestService() {
        return privateChestCommand.getChestService();
    }

    public fr.cantale.plugin.database.FortifiedChunkDAO getFortifiedChunkDAO() {
        return fortifiedChunkDAO;
    }

    public fr.cantale.plugin.vote.VoteDAO getVoteDAO() {
        return voteDAO;
    }

    public fr.cantale.plugin.vote.VoteRewardManager getVoteRewardManager() {
        return voteRewardManager;
    }

    public fr.cantale.plugin.vote.VoteListener getVoteListener() {
        return voteListener;
    }

    public SerialKillerManager getSerialKillerManager() {
        return new SerialKillerManager(this);
    }

    public DiscordBot getDiscordBot() {
        return discordBot;
    }

    public fr.cantale.plugin.chatbot.faq.FaqCore getFaqCore() {
        return faqCore;
    }

    public fr.cantale.plugin.wanted.WantedManager getWantedManager() {
        return wantedCommand != null ? wantedCommand.getWantedManager() : null;
    }

    public fr.cantale.plugin.tag.TagManager getTagManager() {
        return tagManager;
    }

    public fr.cantale.plugin.tasks.ClearLagTask getClearLagTask() {
        return clearLagTask;
    }

    public fr.cantale.plugin.teleport.TeleportDelayManager getTeleportDelayManager() {
        return teleportDelayManager;
    }

    public fr.cantale.plugin.anticheat.AntiCheatManager getAntiCheatManager() {
        return antiCheatManager;
    }

    public fr.cantale.plugin.combat.CombatTagManager getCombatTagManager() {
        return combatTagManager;
    }

    public fr.cantale.plugin.ui.bossbar.RotatingBossBarManager getRotatingBossBarManager() {
        return rotatingBossBarManager;
    }
}