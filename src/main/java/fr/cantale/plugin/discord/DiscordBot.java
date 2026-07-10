package fr.cantale.plugin.discord;

import fr.cantale.plugin.CantalePlugin;
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
    private fr.cantale.plugin.discord.commands.SlashCommandManager slashCommandManager;

    public DiscordBot(CantalePlugin plugin, DiscordConfig config) {
        this.plugin = plugin;
        this.config = config;
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
                    slashCommandManager = new SlashCommandManager(plugin, config);
                    slashCommandManager.register(guild);
                    jda.addEventListener(slashCommandManager);

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
     */
    public void sendEmbedToChannel(long channelId, java.awt.Color color, String title, String description) {
        if (jda == null) return;
        net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) return;
        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
            .setColor(color)
            .setTitle(title)
            .setDescription(description)
            .setTimestamp(java.time.Instant.now());
        channel.sendMessageEmbeds(embed.build()).queue();
    }

    public boolean isConnected() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    public void sendFileToChannel(long channelId, java.io.File file, String description) {
        if (jda == null || !isConnected()) return;
        net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = jda.getTextChannelById(channelId);
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
        if (jda == null) return;
        net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = jda.getTextChannelById(config.getMinecraftStatusChannelId());
        if (channel == null) return;

        long pingRoleId = config.getServerStatusPingRoleId();
        String ping = pingRoleId > 0 ? "<@&" + pingRoleId + "> " : "";

        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
            .setTitle("🖥️ État du serveur Minecraft")
            .setDescription(description)
            .setColor(java.awt.Color.decode(colorHex))
            .setTimestamp(java.time.Instant.now());

        var action = channel.sendMessage(ping + status)
            .setEmbeds(embed.build());

        if (blocking) {
            action.complete(); // attendre l'envoi synchrone (pour onDisable)
        } else {
            action.queue();     // asynchrone (normal)
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
}
