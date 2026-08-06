package fr.cantale.plugin.discord;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration centralisée du bot Discord CANTALE.
 * Lit les valeurs depuis config.yml section "discord".
 */
public class DiscordConfig {

    private final JavaPlugin plugin;

    private final boolean enabled;
    private final String token;
    private final long guildId;
    private final String clientId;

    // Salons
    private final long verificationChannelId;
    private final long welcomeChannelId;
    private final long rulesChannelId;
    private final long roleSelectionChannelId;
    private final long announcementsChannelId;
    private final long supportChannelId;
    private final long modLogsChannelId;
    private final long minecraftStatusChannelId;
    private final long partnershipsChannelId;
    private final long mcChatChannelId;
    private final long lbKillsChannelId;
    private final long lbMoneyChannelId;
    private final long lbWantedChannelId;
    private final long lbFactionsChannelId;
    private final long lbChatReactionsChannelId;
    private final long mcDeathsChannelId;
    private final long mcFactionsChannelId;
    private final long hostingAnnouncementsChannelId;
    private final long eventsChannelId;
    private final long antiBotChannelId;
    private final long faqChannelId;

    // Rôles
    private final long unverifiedRoleId;
    private final long verifiedRoleId;
    private final List<Long> staffRoleIds;
    private final long announcementsPingRoleId;
    private final long partnerRoleId;
    private final long serverStatusPingRoleId;
    private final long eventPingRoleId;
    private final long tournamentPingRoleId;
    private final long updatePingRoleId;
    private final long moderatorPingRoleId;
    private final long wantedPingRoleId;

    // Supabase
    private final String supabaseUrl;
    private final String supabaseKey;
    private final int syncIntervalMinutes;

    // Vérification
    private final String verificationMethod;
    private final String welcomeMessage;
    private final boolean cleanupVerification;

    // Modération (mutable via /modconfig)
    private final List<String> bannedWords;

    // Anti-raid
    private boolean antiRaid;
    private int raidJoinThreshold;
    private int raidJoinWindowSeconds;
    private boolean raidLockdown;
    private int raidLockdownDurationSeconds;

    // Anti-ping massif
    private boolean antiPing;
    private int maxMentions;
    private boolean muteOnPing;
    private int muteOnPingDurationSeconds;

    // Anti-caps
    private boolean antiCaps;
    private int capsPercent;

    // Anti-liens / anti-invites
    private boolean antiLinks;
    private final List<String> allowedLinks;
    private boolean antiInvites;

    // Escalade sanctions auto
    private boolean escalationEnabled;
    private int warnThreshold;
    private int muteThreshold;
    private int kickThreshold;

    // Message cache
    private int messageCacheSize;

    // Chat bridge
    private final boolean chatBridgeEnabled;
    private final String mcToDiscordFormat;
    private final String discordToMcFormat;
    private final boolean ignoreBots;

    public DiscordConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection discord = cfg.getConfigurationSection("discord");

        if (discord == null) {
            this.enabled = false;
            this.token = "";
            this.guildId = 0L;
            this.clientId = "";
            this.verificationChannelId = 0L;
            this.welcomeChannelId = 0L;
            this.rulesChannelId = 0L;
            this.roleSelectionChannelId = 0L;
            this.announcementsChannelId = 0L;
            this.supportChannelId = 0L;
            this.modLogsChannelId = 0L;
            this.minecraftStatusChannelId = 0L;
            this.partnershipsChannelId = 0L;
            this.mcChatChannelId = 0L;
            this.lbKillsChannelId = 0L;
            this.lbMoneyChannelId = 0L;
            this.lbWantedChannelId = 0L;
            this.lbFactionsChannelId = 0L;
            this.lbChatReactionsChannelId = 0L;
            this.mcDeathsChannelId = 0L;
            this.mcFactionsChannelId = 0L;
            this.unverifiedRoleId = 0L;
            this.verifiedRoleId = 0L;
            this.staffRoleIds = new ArrayList<>();
            this.announcementsPingRoleId = 0L;
            this.partnerRoleId = 0L;
            this.serverStatusPingRoleId = 0L;
            this.eventPingRoleId = 0L;
            this.tournamentPingRoleId = 0L;
            this.updatePingRoleId = 0L;
            this.moderatorPingRoleId = 0L;
            this.wantedPingRoleId = 0L;
            this.hostingAnnouncementsChannelId = 0L;
            this.eventsChannelId = 0L;
            this.antiBotChannelId = 0L;
            this.faqChannelId = 0L;
            this.supabaseUrl = "";
            this.supabaseKey = "";
            this.syncIntervalMinutes = 0;
            this.verificationMethod = "button";
            this.welcomeMessage = "";
            this.cleanupVerification = true;
            this.bannedWords = new ArrayList<>();
            this.antiRaid = false;
            this.raidJoinThreshold = 10;
            this.raidJoinWindowSeconds = 10;
            this.raidLockdown = true;
            this.raidLockdownDurationSeconds = 300;
            this.antiPing = false;
            this.maxMentions = 5;
            this.muteOnPing = true;
            this.muteOnPingDurationSeconds = 3600;
            this.antiCaps = false;
            this.capsPercent = 70;
            this.antiLinks = false;
            this.allowedLinks = new ArrayList<>();
            this.antiInvites = true;
            this.escalationEnabled = true;
            this.warnThreshold = 3;
            this.muteThreshold = 2;
            this.kickThreshold = 2;
            this.messageCacheSize = 500;
            this.chatBridgeEnabled = false;
            this.mcToDiscordFormat = "";
            this.discordToMcFormat = "";
            this.ignoreBots = true;
            return;
        }

        this.enabled = discord.getBoolean("enabled", false);
        this.token = discord.getString("token", "");
        this.guildId = parseLong(discord.getString("guild-id", "0"));
        this.clientId = discord.getString("client-id", "");

        ConfigurationSection channels = discord.getConfigurationSection("channels");
        if (channels != null) {
            this.verificationChannelId = parseLong(channels.getString("verification", "0"));
            this.welcomeChannelId = parseLong(channels.getString("welcome", "0"));
            this.rulesChannelId = parseLong(channels.getString("rules", "0"));
            this.roleSelectionChannelId = parseLong(channels.getString("role-selection", "0"));
            this.announcementsChannelId = parseLong(channels.getString("announcements", "0"));
            this.supportChannelId = parseLong(channels.getString("support", "0"));
            this.modLogsChannelId = parseLong(channels.getString("mod-logs", "0"));
            this.minecraftStatusChannelId = parseLong(channels.getString("minecraft-status", "0"));
            this.partnershipsChannelId = parseLong(channels.getString("partnerships", "0"));
            this.mcChatChannelId = parseLong(channels.getString("mc-chat", "0"));
            this.lbKillsChannelId = parseLong(channels.getString("lb-kills", "0"));
            this.lbMoneyChannelId = parseLong(channels.getString("lb-money", "0"));
            this.lbWantedChannelId = parseLong(channels.getString("lb-wanted", "0"));
            this.lbFactionsChannelId = parseLong(channels.getString("lb-factions", "0"));
            this.lbChatReactionsChannelId = parseLong(channels.getString("lb-chat-reactions", "0"));
            this.mcDeathsChannelId = parseLong(channels.getString("mc-deaths", "0"));
            this.mcFactionsChannelId = parseLong(channels.getString("mc-factions", "0"));
            this.hostingAnnouncementsChannelId = parseLong(channels.getString("hosting-announcements", "0"));
            this.eventsChannelId = parseLong(channels.getString("events", "1504901579130798121"));
            this.antiBotChannelId = parseLong(channels.getString("anti-bot", "0"));
            this.faqChannelId = parseLong(channels.getString("faq", "0"));
        } else {
            this.verificationChannelId = this.welcomeChannelId = this.rulesChannelId = this.roleSelectionChannelId = 0L;
            this.announcementsChannelId = this.supportChannelId = this.modLogsChannelId = 0L;
            this.minecraftStatusChannelId = this.partnershipsChannelId = this.mcChatChannelId = 0L;
            this.lbKillsChannelId = this.lbMoneyChannelId = this.lbWantedChannelId = this.lbFactionsChannelId = 0L;
            this.lbChatReactionsChannelId = 0L;
            this.mcDeathsChannelId = this.mcFactionsChannelId = this.hostingAnnouncementsChannelId = 0L;
            this.eventsChannelId = this.antiBotChannelId = this.faqChannelId = 0L;
        }

        ConfigurationSection roles = discord.getConfigurationSection("roles");
        if (roles != null) {
            this.unverifiedRoleId = parseLong(roles.getString("unverified", "0"));
            this.verifiedRoleId = parseLong(roles.getString("verified", "0"));
            this.staffRoleIds = roles.getStringList("staff").stream().map(this::parseLong).toList();
            this.announcementsPingRoleId = parseLong(roles.getString("announcements-ping", "0"));
            this.partnerRoleId = parseLong(roles.getString("partner", "0"));
            this.serverStatusPingRoleId = parseLong(roles.getString("server-status-ping", "0"));
            this.eventPingRoleId = parseLong(roles.getString("event-ping", "0"));
            this.tournamentPingRoleId = parseLong(roles.getString("tournament-ping", "0"));
            this.updatePingRoleId = parseLong(roles.getString("update-ping", "0"));
            this.moderatorPingRoleId = parseLong(roles.getString("moderator-ping", "0"));
            this.wantedPingRoleId = parseLong(roles.getString("wanted-ping", "0"));
        } else {
            this.unverifiedRoleId = this.verifiedRoleId = this.announcementsPingRoleId = this.partnerRoleId = 0L;
            this.serverStatusPingRoleId = this.eventPingRoleId = this.tournamentPingRoleId = 0L;
            this.updatePingRoleId = this.moderatorPingRoleId = this.wantedPingRoleId = 0L;
            this.staffRoleIds = new ArrayList<>();
        }

        ConfigurationSection supabase = discord.getConfigurationSection("supabase");
        if (supabase != null) {
            this.supabaseUrl = supabase.getString("url", "");
            this.supabaseKey = supabase.getString("service-key", "");
            this.syncIntervalMinutes = supabase.getInt("sync-interval", 0);
        } else {
            this.supabaseUrl = "";
            this.supabaseKey = "";
            this.syncIntervalMinutes = 0;
        }

        ConfigurationSection verification = discord.getConfigurationSection("verification");
        if (verification != null) {
            this.verificationMethod = verification.getString("method", "button");
            this.welcomeMessage = verification.getString("welcome-message", "Bienvenue !");
            this.cleanupVerification = verification.getBoolean("cleanup", true);
        } else {
            this.verificationMethod = "button";
            this.welcomeMessage = "Bienvenue !";
            this.cleanupVerification = true;
        }

        ConfigurationSection moderation = discord.getConfigurationSection("moderation");
        if (moderation != null) {
            this.bannedWords = moderation.getStringList("banned-words");

            this.antiRaid = moderation.getBoolean("anti-raid", false);
            this.raidJoinThreshold = moderation.getInt("raid-join-threshold", 10);
            this.raidJoinWindowSeconds = moderation.getInt("raid-join-window", 10);
            this.raidLockdown = moderation.getBoolean("raid-lockdown", true);
            this.raidLockdownDurationSeconds = moderation.getInt("raid-lockdown-duration", 300);

            this.antiPing = moderation.getBoolean("anti-ping", false);
            this.maxMentions = moderation.getInt("max-mentions", 5);
            this.muteOnPing = moderation.getBoolean("mute-on-ping", true);
            this.muteOnPingDurationSeconds = moderation.getInt("mute-on-ping-duration", 3600);

            this.antiCaps = moderation.getBoolean("anti-caps", false);
            this.capsPercent = moderation.getInt("caps-percent", 70);

            this.antiLinks = moderation.getBoolean("anti-links", false);
            this.allowedLinks = moderation.getStringList("allowed-links");
            this.antiInvites = moderation.getBoolean("anti-invites", true);

            this.escalationEnabled = moderation.getBoolean("escalation", true);
            this.warnThreshold = moderation.getInt("warn-threshold", 3);
            this.muteThreshold = moderation.getInt("mute-threshold", 2);
            this.kickThreshold = moderation.getInt("kick-threshold", 2);
            this.messageCacheSize = moderation.getInt("message-cache-size", 500);
        } else {
            this.bannedWords = new ArrayList<>();
            this.antiRaid = false;
            this.raidJoinThreshold = 10;
            this.raidJoinWindowSeconds = 10;
            this.raidLockdown = true;
            this.raidLockdownDurationSeconds = 300;
            this.antiPing = false;
            this.maxMentions = 5;
            this.muteOnPing = true;
            this.muteOnPingDurationSeconds = 3600;
            this.antiCaps = false;
            this.capsPercent = 70;
            this.antiLinks = false;
            this.allowedLinks = new ArrayList<>();
            this.antiInvites = true;
            this.escalationEnabled = true;
            this.warnThreshold = 3;
            this.muteThreshold = 2;
            this.kickThreshold = 2;
            this.messageCacheSize = 500;
        }

        ConfigurationSection chatBridge = discord.getConfigurationSection("chat-bridge");
        if (chatBridge != null) {
            this.chatBridgeEnabled = chatBridge.getBoolean("enabled", false);
            this.mcToDiscordFormat = chatBridge.getString("mc-to-discord-format", "**%player%**: %message%");
            this.discordToMcFormat = chatBridge.getString("discord-to-mc-format", "&9[Discord] &r%name%: %message%");
            this.ignoreBots = chatBridge.getBoolean("ignore-bots", true);
        } else {
            this.chatBridgeEnabled = false;
            this.mcToDiscordFormat = "";
            this.discordToMcFormat = "";
            this.ignoreBots = true;
        }
    }

    private long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public boolean isEnabled() { return enabled; }
    public String getToken() { return token; }
    public long getGuildId() { return guildId; }
    public String getClientId() { return clientId; }

    public long getVerificationChannelId() { return verificationChannelId; }
    public long getWelcomeChannelId() { return welcomeChannelId; }
    public long getRulesChannelId() { return rulesChannelId; }
    public long getRoleSelectionChannelId() { return roleSelectionChannelId; }
    public long getAnnouncementsChannelId() { return announcementsChannelId; }
    public long getSupportChannelId() { return supportChannelId; }
    public long getModLogsChannelId() { return modLogsChannelId; }
    public long getMinecraftStatusChannelId() { return minecraftStatusChannelId; }
    public long getPartnershipsChannelId() { return partnershipsChannelId; }
    public long getMcChatChannelId() { return mcChatChannelId; }
    public long getLbKillsChannelId() { return lbKillsChannelId; }
    public long getLbMoneyChannelId() { return lbMoneyChannelId; }
    public long getLbWantedChannelId() { return lbWantedChannelId; }
    public long getLbFactionsChannelId() { return lbFactionsChannelId; }
    public long getLbChatReactionsChannelId() { return lbChatReactionsChannelId; }
    public long getMcDeathsChannelId() { return mcDeathsChannelId; }
    public long getMcFactionsChannelId() { return mcFactionsChannelId; }

    public long getUnverifiedRoleId() { return unverifiedRoleId; }
    public long getVerifiedRoleId() { return verifiedRoleId; }
    public List<Long> getStaffRoleIds() { return staffRoleIds; }
    public long getAnnouncementsPingRoleId() { return announcementsPingRoleId; }
    public long getPartnerRoleId() { return partnerRoleId; }
    public long getServerStatusPingRoleId() { return serverStatusPingRoleId; }
    public long getEventPingRoleId() { return eventPingRoleId; }
    public long getTournamentPingRoleId() { return tournamentPingRoleId; }
    public long getUpdatePingRoleId() { return updatePingRoleId; }
    public long getModeratorPingRoleId() { return moderatorPingRoleId; }
    public long getWantedPingRoleId() { return wantedPingRoleId; }
    public long getHostingAnnouncementsChannelId() { return hostingAnnouncementsChannelId; }
    public long getEventsChannelId() { return eventsChannelId; }
    public long getAntiBotChannelId() { return antiBotChannelId; }
    public long getFaqChannelId() { return faqChannelId; }

    public String getSupabaseUrl() { return supabaseUrl; }
    public String getSupabaseKey() { return supabaseKey; }
    public int getSyncIntervalMinutes() { return syncIntervalMinutes; }
    public boolean isSupabaseEnabled() { return !supabaseUrl.isEmpty() && !supabaseKey.isEmpty() && syncIntervalMinutes > 0; }

    public String getVerificationMethod() { return verificationMethod; }
    public String getWelcomeMessage() { return welcomeMessage; }
    public boolean isCleanupVerification() { return cleanupVerification; }

    // ── Setters modération ──
    public void setAntiRaid(boolean v) { this.antiRaid = v; }
    public void setRaidJoinThreshold(int v) { this.raidJoinThreshold = v; }
    public void setRaidJoinWindowSeconds(int v) { this.raidJoinWindowSeconds = v; }
    public void setRaidLockdown(boolean v) { this.raidLockdown = v; }
    public void setRaidLockdownDurationSeconds(int v) { this.raidLockdownDurationSeconds = v; }
    public void setAntiPing(boolean v) { this.antiPing = v; }
    public void setMaxMentions(int v) { this.maxMentions = v; }
    public void setMuteOnPing(boolean v) { this.muteOnPing = v; }
    public void setMuteOnPingDurationSeconds(int v) { this.muteOnPingDurationSeconds = v; }
    public void setAntiCaps(boolean v) { this.antiCaps = v; }
    public void setCapsPercent(int v) { this.capsPercent = v; }
    public void setAntiLinks(boolean v) { this.antiLinks = v; }
    public void setAntiInvites(boolean v) { this.antiInvites = v; }
    public void setEscalationEnabled(boolean v) { this.escalationEnabled = v; }
    public void setWarnThreshold(int v) { this.warnThreshold = v; }
    public void setMuteThreshold(int v) { this.muteThreshold = v; }
    public void setKickThreshold(int v) { this.kickThreshold = v; }
    public void setMessageCacheSize(int v) { this.messageCacheSize = v; }

    // ── Getters modération ──
    public List<String> getBannedWords() { return bannedWords; }

    public boolean isAntiRaid() { return antiRaid; }
    public int getRaidJoinThreshold() { return raidJoinThreshold; }
    public int getRaidJoinWindowSeconds() { return raidJoinWindowSeconds; }
    public boolean isRaidLockdown() { return raidLockdown; }
    public int getRaidLockdownDurationSeconds() { return raidLockdownDurationSeconds; }

    public boolean isAntiPing() { return antiPing; }
    public int getMaxMentions() { return maxMentions; }
    public boolean isMuteOnPing() { return muteOnPing; }
    public int getMuteOnPingDurationSeconds() { return muteOnPingDurationSeconds; }

    public boolean isAntiCaps() { return antiCaps; }
    public int getCapsPercent() { return capsPercent; }

    public boolean isAntiLinks() { return antiLinks; }
    public List<String> getAllowedLinks() { return allowedLinks; }
    public boolean isAntiInvites() { return antiInvites; }

    public boolean isEscalationEnabled() { return escalationEnabled; }
    public int getWarnThreshold() { return warnThreshold; }
    public int getMuteThreshold() { return muteThreshold; }
    public int getKickThreshold() { return kickThreshold; }
    public int getMessageCacheSize() { return messageCacheSize; }

    /**
     * Sauvegarde la section discord.moderation dans config.yml
     */
    public void save() {
        if (plugin == null) return;
        FileConfiguration cfg = plugin.getConfig();
        String p = "discord.moderation.";
        cfg.set(p + "banned-words", new ArrayList<>(bannedWords));
        cfg.set(p + "anti-raid", antiRaid);
        cfg.set(p + "raid-join-threshold", raidJoinThreshold);
        cfg.set(p + "raid-join-window", raidJoinWindowSeconds);
        cfg.set(p + "raid-lockdown", raidLockdown);
        cfg.set(p + "raid-lockdown-duration", raidLockdownDurationSeconds);
        cfg.set(p + "anti-ping", antiPing);
        cfg.set(p + "max-mentions", maxMentions);
        cfg.set(p + "mute-on-ping", muteOnPing);
        cfg.set(p + "mute-on-ping-duration", muteOnPingDurationSeconds);
        cfg.set(p + "anti-caps", antiCaps);
        cfg.set(p + "caps-percent", capsPercent);
        cfg.set(p + "anti-links", antiLinks);
        cfg.set(p + "allowed-links", new ArrayList<>(allowedLinks));
        cfg.set(p + "anti-invites", antiInvites);
        cfg.set(p + "escalation", escalationEnabled);
        cfg.set(p + "warn-threshold", warnThreshold);
        cfg.set(p + "mute-threshold", muteThreshold);
        cfg.set(p + "kick-threshold", kickThreshold);
        cfg.set(p + "message-cache-size", messageCacheSize);
        plugin.saveConfig();
    }

    public boolean isChatBridgeEnabled() { return chatBridgeEnabled; }
    public String getMcToDiscordFormat() { return mcToDiscordFormat; }
    public String getDiscordToMcFormat() { return discordToMcFormat; }
    public boolean isIgnoreBots() { return ignoreBots; }
}
