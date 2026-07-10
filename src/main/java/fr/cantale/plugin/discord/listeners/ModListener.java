package fr.cantale.plugin.discord.listeners;

import fr.cantale.plugin.discord.DiscordConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modération Discord ultra-complète.
 *
 * ─ Anti-spam (duplication, répétition caractères, rate limit)
 * ─ Anti-raid (détection burst d'arrivées, lockdown auto)
 * ─ Anti-ping massif (mentions excessives, mute auto)
 * ─ Anti-caps (pourcentage majuscules)
 * ─ Anti-liens / anti-invites
 * ─ Message cache (logs delete/edit avec contenu)
 * ─ Escalade sanctions (warn → mute → kick → ban)
 */
public class ModListener extends ListenerAdapter {

    private final JavaPlugin plugin;
    private final DiscordConfig config;

    // ── Anti-raid ──
    private final List<Long> recentJoins = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean lockdownActive = false;
    private volatile long lockdownUntil = 0;

    // ── Message cache (delete/edit logs) ──
    private final Map<Long, CachedMessage> messageCache;

    // ── Escalade sanctions ──
    private final Map<Long, List<Long>> infractionLog = new ConcurrentHashMap<>();

    // ── Patterns ──
    private static final Pattern URL_PATTERN = Pattern.compile(
        "https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern INVITE_PATTERN = Pattern.compile(
        "(discord\\.(?:gg|com/invite)|discordapp\\.com/invite)/([a-zA-Z0-9-]+)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern MENTION_PATTERN = Pattern.compile(
        "<@[!&]?\\d+>|@everyone|@here", Pattern.CASE_INSENSITIVE);

    private static class CachedMessage {
        final long id;
        final long authorId;
        final long channelId;
        final String content;
        final String authorName;
        final long timestamp;
        CachedMessage(long id, long authorId, long channelId, String content, String authorName, long ts) {
            this.id = id; this.authorId = authorId; this.channelId = channelId;
            this.content = content; this.authorName = authorName; this.timestamp = ts;
        }
    }

    public ModListener(JavaPlugin plugin, DiscordConfig config) {
        this.plugin = plugin;
        this.config = config;

        int cacheSize = config.getMessageCacheSize();
        this.messageCache = Collections.synchronizedMap(new LinkedHashMap<>(cacheSize, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, CachedMessage> eldest) {
                return size() > cacheSize;
            }
        });

        // Tâche périodique : cleanup
        org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
            this::cleanup, 6000L, 6000L); // toutes les 5 min
    }

    // ═════════════════════════════════════════════════════════════
    //  MESSAGE RECEIVED — toutes les protections auto
    // ═════════════════════════════════════════════════════════════

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getMember() == null) return;
        Member member = event.getMember();

        Message msg = event.getMessage();
        String content = msg.getContentRaw();
        long userId = member.getIdLong();
        long now = System.currentTimeMillis();

        // Cache le message pour logs delete/edit — AVANT le check staff, sinon les messages
        // du staff ne sont jamais mis en cache et leurs suppressions/modifications
        // remontent des logs vides (pas d'auteur ni de contenu affiché).
        messageCache.put(msg.getIdLong(), new CachedMessage(
            msg.getIdLong(), userId, msg.getChannel().getIdLong(),
            content, member.getEffectiveName(), now));

        if (isStaff(member)) return; // pas de modération auto sur le staff (mais le cache reste à jour au-dessus)
        if (lockdownActive && System.currentTimeMillis() < lockdownUntil) {
            event.getMessage().delete().reason("Lockdown raid").queue();
            return;
        }

        // ── 1. Mots interdits ──
        for (String word : config.getBannedWords()) {
            if (!word.isEmpty() && content.toLowerCase().contains(word.toLowerCase())) {
                sanction(msg, member, "Mot interdit : " + word, 1);
                return;
            }
        }

        // ── 2. Anti-invites ──
        if (config.isAntiInvites() && INVITE_PATTERN.matcher(content).find()) {
            sanction(msg, member, "Lien d'invitation Discord interdit", 1);
            return;
        }

        // ── 3. Anti-liens ──
        if (config.isAntiLinks()) {
            Matcher m = URL_PATTERN.matcher(content);
            if (m.find()) {
                String url = m.group();
                boolean allowed = false;
                for (String a : config.getAllowedLinks()) {
                    if (url.toLowerCase().contains(a.toLowerCase())) { allowed = true; break; }
                }
                if (!allowed) {
                    sanction(msg, member, "Lien externe interdit : " + url, 1);
                    return;
                }
            }
        }

        // ── 4. Anti-ping massif ──
        if (config.isAntiPing()) {
            int mentions = 0;
            Matcher m = MENTION_PATTERN.matcher(content);
            while (m.find()) mentions++;
            mentions += msg.getMentions().getMembers().size();
            if (mentions >= config.getMaxMentions()) {
                final int finalMentions = mentions;
                msg.delete().reason("Anti-ping massif").queue();
                if (config.isMuteOnPing()) {
                    member.timeoutFor(config.getMuteOnPingDurationSeconds(), TimeUnit.SECONDS)
                        .reason("Ping massif (" + finalMentions + " mentions)").queue(
                        s -> log(member.getGuild(), "🔇 Mute auto (ping)", Color.RED,
                            member.getAsMention() + " — " + finalMentions + " mentions", member.getUser()));
                }
                return;
            }
        }

        // ── 5. Anti-caps ──
        if (config.isAntiCaps() && content.length() >= 8) {
            int caps = 0, letters = 0;
            for (char c : content.toCharArray()) {
                if (Character.isLetter(c)) { letters++; if (Character.isUpperCase(c)) caps++; }
            }
            if (letters > 0 && (caps * 100 / letters) >= config.getCapsPercent()) {
                sanction(msg, member, "Trop de majuscules (" + (caps * 100 / letters) + "%)", 1);
                return;
            }
        }

        // Les protections de spam ont été supprimées du module actif.
    }

    // ═════════════════════════════════════════════════════════════
    //  ANTI-RAID — détection burst d'arrivées
    // ═════════════════════════════════════════════════════════════

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        long now = System.currentTimeMillis();

        // Log arrivée
        log(event.getGuild(), "📥 Arrivée", Color.GREEN,
            event.getUser().getAsMention() + " a rejoint le serveur.", event.getUser());

        // Anti-raid
        if (!config.isAntiRaid()) return;

        synchronized (recentJoins) {
            recentJoins.add(now);
            recentJoins.removeIf(t -> now - t > config.getRaidJoinWindowSeconds() * 1000L);

            if (recentJoins.size() >= config.getRaidJoinThreshold() && !lockdownActive) {
                lockdownActive = true;
                lockdownUntil = now + config.getRaidLockdownDurationSeconds() * 1000L;

                log(event.getGuild(), "🚨 RAID DÉTECTÉ", Color.RED,
                    recentJoins.size() + " arrivées en " + config.getRaidJoinWindowSeconds() + "s\n"
                    + "Lockdown activé pendant " + config.getRaidLockdownDurationSeconds() + "s.", null);

                // Appliquer lockdown
                if (config.isRaidLockdown()) {
                    event.getGuild().getTextChannels().forEach(ch -> {
                        try {
                            ch.upsertPermissionOverride(event.getGuild().getPublicRole())
                                .deny(Permission.MESSAGE_SEND)
                                .queue();
                        } catch (Exception ignored) {}
                    });

                    // Auto-désactiver
                    org.bukkit.Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                        lockdownActive = false;
                        event.getGuild().getTextChannels().forEach(ch -> {
                            try {
                                ch.getManager().removePermissionOverride(event.getGuild().getPublicRole()).queue();
                            } catch (Exception ignored) {}
                        });
                        log(event.getGuild(), "🔓 Lockdown terminé", Color.GREEN,
                            "Le serveur est rouvert.", null);
                    }, config.getRaidLockdownDurationSeconds() * 20L);
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  LOGS — arrivees, départs, rôles, messages
    // ═════════════════════════════════════════════════════════════

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        log(event.getGuild(), "📤 Départ", Color.RED,
            event.getUser().getAsMention() + " a quitté le serveur.", event.getUser());
    }

    @Override
    public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
        List<Role> roles = event.getRoles();
        if (roles.isEmpty()) return;
        log(event.getGuild(), "🏷️ Rôle ajouté", Color.CYAN,
            event.getUser().getAsMention() + " → " + roles.get(0).getAsMention(), event.getUser());
    }

    @Override
    public void onGuildMemberRoleRemove(@NotNull GuildMemberRoleRemoveEvent event) {
        List<Role> roles = event.getRoles();
        if (roles.isEmpty()) return;
        log(event.getGuild(), "🏷️ Rôle retiré", Color.ORANGE,
            event.getUser().getAsMention() + " ← " + roles.get(0).getAsMention(), event.getUser());
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        if (event.getGuild() == null) return;
        CachedMessage cm;
        synchronized (messageCache) { cm = messageCache.remove(event.getMessageIdLong()); }
        if (cm == null) {
            log(event.getGuild(), "🗑️ Message supprimé", Color.GRAY,
                "Salon: <#" + event.getChannel().getId() + ">\nID: " + event.getMessageIdLong(), null);
            return;
        }
        User dummy = event.getJDA().getUserById(cm.authorId);
        log(event.getGuild(), "🗑️ Message supprimé", Color.GRAY,
            "**Auteur:** <@" + cm.authorId + ">\n"
            + "**Salon:** <#" + cm.channelId + ">\n"
            + "**Contenu:** " + cm.content.substring(0, Math.min(500, cm.content.length())),
            dummy);
    }

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        if (event.getAuthor().isBot() || event.getGuild() == null) return;
        String oldContent = "(inconnu)";
        CachedMessage cm;
        synchronized (messageCache) { cm = messageCache.get(event.getMessageIdLong()); }
        if (cm != null) oldContent = cm.content;
        String newContent = event.getMessage().getContentRaw();
        synchronized (messageCache) {
            messageCache.put(event.getMessageIdLong(), new CachedMessage(
                event.getMessageIdLong(), event.getAuthor().getIdLong(),
                event.getChannel().getIdLong(), newContent, event.getAuthor().getName(),
                System.currentTimeMillis()));
        }

        // Discord envoie aussi un MessageUpdateEvent quand un embed/aperçu de lien se génère
        // automatiquement après coup, sans que le texte du message ait réellement changé.
        // Sans ce garde-fou, chaque lien posté générait un faux log "modifié" (avant == après).
        if (cm != null && oldContent.equals(newContent)) return;

        log(event.getGuild(), "✏️ Message modifié", Color.YELLOW,
            "**Auteur:** " + event.getAuthor().getAsMention() + "\n"
            + "**Salon:** <#" + event.getChannel().getId() + ">\n"
            + "**Avant:** " + oldContent.substring(0, Math.min(500, oldContent.length())) + "\n"
            + "**Après:** " + newContent.substring(0, Math.min(500, newContent.length())),
            event.getAuthor());
    }

    // ═════════════════════════════════════════════════════════════
    //  SANCTIONS & ESCALADE
    // ═════════════════════════════════════════════════════════════

    private void sanction(Message message, Member member, String reason, int severity) {
        message.delete().reason("Auto-mod: " + reason).queue();
        long userId = member.getIdLong();

        if (!config.isEscalationEnabled()) {
            // Pas d'escalade : simple avertissement
            sendAutoDelete(message.getChannel().asTextChannel(),
                "⚠️ " + member.getAsMention() + ", ton message a été supprimé : " + reason);
            log(message.getGuild(), "🛡️ Auto-mod", Color.ORANGE,
                "**Utilisateur:** " + member.getAsMention() + "\n**Raison:** " + reason, member.getUser());
            return;
        }

        // Escalade
        List<Long> hist = infractionLog.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()));
        hist.add(System.currentTimeMillis());
        // Garder les 24 dernières heures
        hist.removeIf(t -> System.currentTimeMillis() - t > 86400000);
        int count24h = hist.size();

        String action;

        if (severity >= 3 || count24h >= config.getKickThreshold() + 2) {
            action = "BAN";
            final Color banColor = Color.BLACK;
            member.ban(7, TimeUnit.DAYS).reason("Auto-mod escalation: " + reason).queue(
                s -> log(message.getGuild(), "🔨 Ban auto", banColor,
                    member.getAsMention() + " — " + reason + " (infractions 24h: " + count24h + ")", member.getUser()));
        } else if (count24h >= config.getKickThreshold()) {
            action = "KICK";
            final Color kickColor = Color.RED;
            member.kick().reason("Auto-mod escalation: " + reason).queue(
                s -> log(message.getGuild(), "👢 Kick auto", kickColor,
                    member.getAsMention() + " — " + reason + " (infractions 24h: " + count24h + ")", member.getUser()));
        } else if (count24h >= config.getMuteThreshold()) {
            action = "TIMEOUT 1h";
            final Color timeoutColor = Color.MAGENTA;
            member.timeoutFor(1, TimeUnit.HOURS).reason("Auto-mod escalation: " + reason).queue(
                s -> log(message.getGuild(), "⏱️ Timeout auto", timeoutColor,
                    member.getAsMention() + " — " + reason + " (infractions 24h: " + count24h + ")", member.getUser()));
        } else {
            action = "AVERTISSEMENT";
            sendAutoDelete(message.getChannel().asTextChannel(),
                "⚠️ " + member.getAsMention() + ", ton message a été supprimé : " + reason
                + "\n*Infractions 24h : " + count24h + "/" + config.getKickThreshold() + "*");
            log(message.getGuild(), "🛡️ Auto-mod", Color.ORANGE,
                "**Utilisateur:** " + member.getAsMention() + "\n**Raison:** " + reason
                + "\n**Infractions 24h:** " + count24h, member.getUser());
            return;
        }

        sendAutoDelete(message.getChannel().asTextChannel(),
            "🔨 " + member.getAsMention() + " a été sanctionné automatiquement : **" + action + "**\n"
            + "Raison : " + reason);
    }

    // ═════════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════════

    private boolean isStaff(Member member) {
        for (Role role : member.getRoles()) {
            if (config.getStaffRoleIds().contains(role.getIdLong())) return true;
        }
        return false;
    }

    private void sendAutoDelete(TextChannel ch, String text) {
        ch.sendMessage(text).queue(m -> m.delete().queueAfter(15, TimeUnit.SECONDS));
    }

    private void log(Guild guild, String title, Color color, String description, User user) {
        TextChannel logs = guild.getTextChannelById(config.getModLogsChannelId());
        if (logs == null) return;
        EmbedBuilder eb = new EmbedBuilder().setColor(color).setTitle(title)
            .setDescription(description).setTimestamp(Instant.now());
        if (user != null) eb.setFooter("ID: " + user.getId(), user.getEffectiveAvatarUrl());
        logs.sendMessageEmbeds(eb.build()).queue();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        synchronized (recentJoins) {
            recentJoins.removeIf(t -> now - t > 60000);
        }
    }
}
