package fr.cantale.plugin.discord;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.chatbot.faq.FaqCore;
import fr.cantale.plugin.discord.listeners.*;
import fr.cantale.plugin.discord.commands.SlashCommandManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Bot Discord CANTALE — intégré au plugin Minecraft.
 * Gère : vérification, modération, logs, sync chat MC↔Discord, Supabase.
 */
public class DiscordBot {

    private final CantalePlugin plugin;
    private final DiscordConfig config;
    private JDA jda;
    private SupabaseSync supabaseSync;
    private ChatBridgeListener chatBridgeListener;
    private FaqDiscordListener faqDiscordListener;
    private fr.cantale.plugin.discord.commands.SlashCommandManager slashCommandManager;
    private final FaqCore faqCore;

    // Messages live /stats /status à actualiser toutes les 10s (max 30min par message)
    private final List<LiveStatsMessage> liveStatsMessages = new CopyOnWriteArrayList<>();
    private final long liveStatsMaxAgeMs = TimeUnit.MINUTES.toMillis(30);
    private final long liveStatsIntervalMs = TimeUnit.SECONDS.toMillis(10);

    public DiscordBot(CantalePlugin plugin, DiscordConfig config, FaqCore faqCore) {
        this.plugin = plugin;
        this.config = config;
        this.faqCore = faqCore;
    }

    public void start() {
        startStatusRotation();
        if (!config.isEnabled() || config.getToken().isEmpty()) {
            plugin.getLogger().info("[Discord] Bot désactivé (pas de token ou disabled).");
            return;
        }

        // Démarrage JDA en async pour ne pas bloquer le main thread de Bukkit
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                WelcomeListener welcomeListener = new WelcomeListener();
                VerificationListener verificationListener = new VerificationListener(plugin, config, welcomeListener);
                TicketListener ticketListener = new TicketListener(config);

                jda = JDABuilder.createDefault(config.getToken())
                    .setEnabledIntents(EnumSet.of(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_MESSAGE_REACTIONS,
                        GatewayIntent.GUILD_VOICE_STATES,
                        GatewayIntent.GUILD_MODERATION,
                        GatewayIntent.GUILD_EMOJIS_AND_STICKERS,
                        GatewayIntent.SCHEDULED_EVENTS,
                        GatewayIntent.MESSAGE_CONTENT
                    ))
                    .addEventListeners(
                        verificationListener,
                        new ModListener(plugin, config),
                        new AntiSpamListener(config),
                        new RoleSelectionListener(config),
                        new GuildReadyListener(config, verificationListener),
                        ticketListener,
                        new MaintenanceRelayListener(config)
                    )
                    .build();

                jda.awaitReady();

                Guild guild = jda.getGuildById(config.getGuildId());
                if (guild != null) {
                    plugin.getLogger().info("[Discord] Guild trouvé: " + guild.getName() + " (ID: " + guild.getId() + ")");
                    slashCommandManager = new SlashCommandManager(plugin, config, faqCore);
                    slashCommandManager.register(guild);
                    jda.addEventListener(slashCommandManager);
                    startLiveStatsUpdater();

                    ticketListener.register(guild);

                    fr.cantale.plugin.discord.listeners.ModConfigPanelListener modConfigPanel =
                        new fr.cantale.plugin.discord.listeners.ModConfigPanelListener(config);
                    slashCommandManager.setModConfigPanel(modConfigPanel);
                    jda.addEventListener(modConfigPanel);
                } else {
                    plugin.getLogger().severe("[Discord] Guild non trouvé avec ID: " + config.getGuildId());
                }

                // Chat bridge
                this.chatBridgeListener = new ChatBridgeListener(plugin, config, jda);
                jda.addEventListener(chatBridgeListener);

                // FAQ bot
                this.faqDiscordListener = new FaqDiscordListener(plugin, faqCore, config);
                jda.addEventListener(faqDiscordListener);

                // Supabase sync
                this.supabaseSync = new SupabaseSync(plugin, config);
                supabaseSync.start();

                // Daily leaderboards
                new fr.cantale.plugin.discord.tasks.DailyLeaderboardTask(plugin, this).schedule();

                // Rappel de bump Disboard (toutes les 2h01)
                new fr.cantale.plugin.discord.tasks.BumpReminderTask(plugin, jda).start();

                plugin.getLogger().info("[Discord] Bot CANTALE connecté — " + jda.getSelfUser().getName());

                // Message de démarrage serveur
                sendServerStatus("✅ **Le serveur Minecraft est en ligne !**", "#57F287",
                    "Le serveur Cantale vient de démarrer. Rejoignez-nous !");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[Discord] Échec du démarrage du bot", e);
            }
        });
    }

    public void stop() {
        if (supabaseSync != null) {
            supabaseSync.stop();
        }
        if (jda != null) {
            jda.shutdownNow();
        }
    }

    public JDA getJda() { return jda; }
    public DiscordConfig getConfig() { return config; }
    public SupabaseSync getSupabaseSync() { return supabaseSync; }
    public ChatBridgeListener getChatBridgeListener() { return chatBridgeListener; }
    public FaqDiscordListener getFaqDiscordListener() { return faqDiscordListener; }

    /**
     * Envoie un message Minecraft vers Discord (utilisé par ChatListener).
     */
    public void sendMcMessage(String playerName, String message) {
        if (chatBridgeListener != null) {
            chatBridgeListener.sendToDiscord(playerName, message);
        }
    }

    /**
     * Envoie un message système Minecraft vers Discord (join, quit, death...).
     */
    public void sendSystemMessage(String formattedMessage) {
        if (chatBridgeListener != null) {
            chatBridgeListener.sendSystemToDiscord(formattedMessage);
        }
    }

    /**
     * Envoie un embed vers un salon Discord spécifique (par ID).
     * Les codes couleur Minecraft (§) sont automatiquement supprimés.
     */
    public void sendEmbedToChannel(long channelId, java.awt.Color color, String title, String description) {
        if (jda == null) return;
        net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel channel = jda.getChannelById(
            net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel.class, channelId);
        if (channel == null) return;
        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
            .setColor(color)
            .setTitle(stripMinecraftColors(title))
            .setDescription(stripMinecraftColors(description))
            .setTimestamp(java.time.Instant.now());
        channel.sendMessageEmbeds(embed.build()).queue();
    }

    private String stripMinecraftColors(String input) {
        if (input == null) return null;
        return input.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    public boolean isConnected() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    public void sendFileToChannel(long channelId, java.io.File file, String description) {
        if (jda == null || !isConnected()) return;
        net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel channel = jda.getChannelById(
            net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel.class, channelId);
        if (channel == null) return;
        if (!file.exists()) return;
        channel.sendMessage(description)
            .addFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(file))
            .queue();
    }

    public void sendServerStatus(String status, String colorHex, String description) {
        sendServerStatus(status, colorHex, description, false);
    }

    public void sendServerStatus(String status, String colorHex, String description, boolean blocking) {
        if (jda == null) {
            plugin.getLogger().warning("[Discord] sendServerStatus impossible : JDA non initialisé.");
            return;
        }
        long channelId = config.getMinecraftStatusChannelId();
        if (channelId <= 0) {
            plugin.getLogger().warning("[Discord] sendServerStatus impossible : salon minecraft-status non configuré.");
            return;
        }
        net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel channel = jda.getChannelById(
            net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel.class, channelId);
        if (channel == null) {
            plugin.getLogger().warning("[Discord] sendServerStatus impossible : salon " + channelId + " introuvable.");
            return;
        }

        long pingRoleId = config.getServerStatusPingRoleId();
        String ping = pingRoleId > 0 ? "<@&" + pingRoleId + "> " : "";

        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
            .setTitle("🖥️ État du serveur Minecraft")
            .setDescription(description)
            .setColor(java.awt.Color.decode(colorHex))
            .setTimestamp(java.time.Instant.now());

        var action = channel.sendMessage(ping + status)
            .setEmbeds(embed.build());

        try {
            if (blocking) {
                action.complete(); // attendre l'envoi synchrone (pour onDisable)
            } else {
                action.queue(
                    success -> plugin.getLogger().fine("[Discord] Message status envoyé dans " + channelId),
                    error -> plugin.getLogger().warning("[Discord] Échec envoi status : " + error.getMessage())
                );
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Discord] Exception lors de l'envoi du status : " + e.getMessage());
        }
    }

    private void startStatusRotation() {
        String[] statuses = {
            "🎮 %players% joueurs en ligne",
            "🌐 cantale.world",
            "⛏ play.cantale.world",
            "💪 L'effort crée les forts",
            "🌍 Le monde appartient à ceux qui savent le conserver"
        };

        org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            int index = 0;
            @Override
            public void run() {
                if (jda == null || jda.getStatus() != JDA.Status.CONNECTED) return;
                String status = statuses[index]
                    .replace("%players%", String.valueOf(org.bukkit.Bukkit.getOnlinePlayers().size()));
                jda.getPresence().setActivity(net.dv8tion.jda.api.entities.Activity.playing(status));
                index = (index + 1) % statuses.length;
            }
        }, 20L * 10, 20L * 30); // première fois après 10s, puis toutes les 30s
    }

    /**
     * Enregistre un message /stats ou /status pour actualisation live (10s, max 5min).
     */
    public void registerLiveStatsMessage(long channelId, long messageId) {
        liveStatsMessages.add(new LiveStatsMessage(channelId, messageId, System.currentTimeMillis()));
    }

    /**
     * Démarre le scheduler qui actualise les messages live toutes les 10 secondes.
     */
    private void startLiveStatsUpdater() {
        org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                if (jda == null || jda.getStatus() != JDA.Status.CONNECTED) return;

                long now = System.currentTimeMillis();
                liveStatsMessages.removeIf(msg -> now - msg.createdAt() > liveStatsMaxAgeMs);

                net.dv8tion.jda.api.EmbedBuilder embed = buildLiveStatsEmbed();
                for (LiveStatsMessage msg : liveStatsMessages) {
                    net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel channel = jda.getChannelById(
                    net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel.class, msg.channelId());
                    if (channel == null) continue;
                    try {
                        channel.editMessageEmbedsById(msg.messageId(), embed.build()).queue(
                            success -> {},
                            error -> plugin.getLogger().fine("[Discord] Échec maj live stats: " + error.getMessage())
                        );
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] Live stats updater error: " + e.getMessage());
            }
        }, 20L * 10, 20L * (liveStatsIntervalMs / 1000));
    }

    private net.dv8tion.jda.api.EmbedBuilder buildLiveStatsEmbed() {
        java.util.Map<String, Object> stats = plugin.getServerOptimizer().getStats();

        double tps = ((Number) stats.getOrDefault("tps", 20.0)).doubleValue();
        int online = ((Number) stats.getOrDefault("online_players", 0)).intValue();
        int max = ((Number) stats.getOrDefault("max_players", 0)).intValue();
        long usedMb = ((Number) stats.getOrDefault("used_memory_mb", 0)).longValue();
        long maxMb = ((Number) stats.getOrDefault("max_memory_mb", 0)).longValue();
        int chunks = ((Number) stats.getOrDefault("loaded_chunks", 0)).intValue();
        int entities = ((Number) stats.getOrDefault("total_entities", 0)).intValue();

        long uptimeMs = System.currentTimeMillis() - java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
        long upDays = uptimeMs / 86_400_000L;
        long upHours = (uptimeMs % 86_400_000L) / 3_600_000L;
        long upMin = (uptimeMs % 3_600_000L) / 60_000L;
        String uptime = (upDays > 0 ? upDays + "j " : "") + upHours + "h " + upMin + "min";

        String version = org.bukkit.Bukkit.getBukkitVersion().split("-")[0];

        java.awt.Color color;
        String tpsIcon;
        String tpsStatus;
        if (tps >= 19.0) { color = new java.awt.Color(0x2ECC71); tpsIcon = "🟢"; tpsStatus = "Excellent"; }
        else if (tps >= 17.0) { color = new java.awt.Color(0xF1C40F); tpsIcon = "🟡"; tpsStatus = "Stable"; }
        else if (tps >= 14.0) { color = new java.awt.Color(0xE67E22); tpsIcon = "🟠"; tpsStatus = "Lag léger"; }
        else { color = new java.awt.Color(0xE74C3C); tpsIcon = "🔴"; tpsStatus = "Surchargé"; }

        String onlineIcon = online > 0 ? "🟢" : "⚫";

        return new net.dv8tion.jda.api.EmbedBuilder()
            .setColor(color)
            .setTitle("📊 Statut Cantale — Live")
            .setDescription("**" + onlineIcon + " " + (online > 0 ? "En ligne" : "Personne connecté") + "**\n"
                + "Connecte-toi sur `play.cantale.world` pour jouer !")
            .addField("👥 Joueurs", "`" + online + " / " + max + "`", true)
            .addField(tpsIcon + " TPS", "`" + String.format("%.2f", Math.min(tps, 20.0)) + "` " + tpsStatus, true)
            .addField("⏱ Uptime", uptime, true)
            .addField("🌐 IP publique", "`play.cantale.world`", true)
            .addField("🎮 Version", version, true)
            .addField("💾 RAM", usedMb + " / " + maxMb + " MB", true)
            .addField("📦 Chunks chargés", String.valueOf(chunks), true)
            .addField("🐾 Entités", String.valueOf(entities), true)
            .addField("🛡 Optimisation", plugin.getServerOptimizer().isEnabled() ? "✅ Active" : "❌ Désactivée", true)
            .setFooter("Cantale — Trois vies. C'est tout.", null)
            .setTimestamp(java.time.Instant.now());
    }

    private record LiveStatsMessage(long channelId, long messageId, long createdAt) {}
}
