package org.rexi.discordBridgeVelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bstats.velocity.Metrics;
import org.rexi.discordBridgeVelocity.commands.DiscordBridgeCommand;
import org.rexi.discordBridgeVelocity.commands.LinkCommand;
import org.rexi.discordBridgeVelocity.discord.DiscordChatListener;
import org.rexi.discordBridgeVelocity.discord.DiscordDailyRewardsTask;
import org.rexi.discordBridgeVelocity.discord.RankSyncTask;
import org.rexi.discordBridgeVelocity.discord.commands.*;
import org.rexi.discordBridgeVelocity.discord.commands.VelocityUtilsCommands.*;
import org.rexi.discordBridgeVelocity.discord.listeners.DiscordRoleRewardsListener;
import org.rexi.discordBridgeVelocity.utils.DBManager;
import org.rexi.velocityUtils.api.VelocityUtilsAPI;
import org.rexi.velocityUtils.api.VelocityUtilsProvider;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "discord_bridge_velocity",
        name = "Discord Bridge Velocity",
        version = BuildConstants.VERSION,
        authors = {"Rexi666"},
        dependencies = {@Dependency(id = "luckperms", optional = true),
                @Dependency(id = "litebans", optional = true),
        @Dependency(id = "velocityutils", optional = true)})
public class DiscordBridgeVelocity {

    public final ProxyServer server;
    private JDA jda;
    private final Path dataDirectory;
    private DBManager dbManager;
    private LuckPerms luckPerms = null;
    private VelocityUtilsAPI velocityUtils = null;
    private RankSyncTask rankSyncTask;

    private Map<String, String> configValues = new HashMap<>();
    private final Map<String, String> linkedChannels = new HashMap<>();
    private final Map<String, String> linkedRanks = new HashMap<>();
    private final List<String> allRanks = new ArrayList<>();
    @Inject
    public DiscordBridgeVelocity(ProxyServer server, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    @Inject public Logger logger;
    @Inject private Metrics.Factory metricsFactory;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();
        loadLinkedChannels();
        loadLinkedRanks();
        initializeDatabase();

        try {
            this.luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            this.luckPerms = null;
        }

        try {
            this.velocityUtils = VelocityUtilsProvider.get();
        } catch (IllegalStateException e) {
            this.velocityUtils = null;
        }

        initializeBot();

        this.rankSyncTask = new RankSyncTask(this, server, luckPerms);
        this.rankSyncTask.start();

        server.getCommandManager().register("discordbridge", new DiscordBridgeCommand(this));
        server.getCommandManager().register("link", new LinkCommand(this, luckPerms));

        Metrics metrics = metricsFactory.make(this, 27858);

        long initialDelay = getInitialDailyRewardDelaySeconds();

        server.getScheduler().buildTask(this,
                        new DiscordDailyRewardsTask(this)
                )
                .delay(initialDelay, TimeUnit.SECONDS)
                .repeat(24, TimeUnit.HOURS)
                .schedule();

        logger.info("Discord daily rewards scheduled in " + formatDuration(initialDelay));

        server.sendMessage(legacy("&aThe plugin DiscordBridgeVelocity has been enabled!"));
        server.sendMessage(legacy("&bThank you for using Rexi666 plugins :D"));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        shutdownBot();
        server.sendMessage(legacy("&cThe plugin DiscordBridgeVelocity has been disabled!"));
        server.sendMessage(legacy("&bThank you for using Rexi666 plugins :D"));
    }

    private void initializeDatabase() {
        try {
            String type = getConfig("database.type", "SQLITE");
            String sqliteFile = dataDirectory.resolve(getConfig("database.sqlite-file", "database.db")).toString();

            String host = getConfig("database.mysql.host", "localhost");
            int port = getConfig("database.mysql.port", 3306);
            String dbName = getConfig("database.mysql.database", "discordbridge");
            String user = getConfig("database.mysql.username", "user");
            String pass = getConfig("database.mysql.password", "pass");

            dbManager = new DBManager(type, sqliteFile, host, port, dbName, user, pass);
        } catch (Exception e) {
            logger.error("❌ Error trying to connect to database", e);
        }
    }

    public DBManager getDatabase() {
        return dbManager;
    }
    public RankSyncTask getRankSyncTask() {
        return rankSyncTask;
    }

    public void initializeBot() {
        try {
            String token = getConfig("token", "DISCORD_BOT_TOKEN");
            if (token.equals("DISCORD_BOT_TOKEN")) {
                logger.warn("No valid Discord bot token found in configuration. Discord bot will not be initialized.");
                return;
            }

            logger.info("Starting Discord Bot...");

            String statusType = getConfig("statusType", "PLAYING");
            String statusText = getConfig("statusText", "My Velocity Bot");
            String statusMode = getConfig("statusMode", "ONLINE");

            Activity activity;
            if (statusText == null || statusText.isEmpty()) {
                activity = null;
            } else if (statusType == null) {
                activity = Activity.customStatus(statusText);
            } else {
                switch (statusType.toUpperCase()) {
                    case "LISTENING":
                        activity = Activity.listening(statusText);
                        break;
                    case "WATCHING":
                        activity = Activity.watching(statusText);
                        break;
                    case "COMPETING":
                        activity = Activity.competing(statusText);
                        break;
                    case "PLAYING":
                        activity = Activity.playing(statusText);
                        break;
                    default:
                        activity = Activity.customStatus(statusText);
                        break;
                }
            }

            OnlineStatus status = OnlineStatus.ONLINE;
            if (statusMode != null) {
                switch (statusMode.toUpperCase()) {
                    case "IDLE":
                        status = OnlineStatus.IDLE;
                        break;
                    case "DND":
                        status = OnlineStatus.DO_NOT_DISTURB;
                        break;
                    case "INVISIBLE":
                        status = OnlineStatus.INVISIBLE;
                        break;
                    default:
                        status = OnlineStatus.ONLINE;
                        break;
                }
            }

            jda = JDABuilder.createDefault(token,
                            EnumSet.of(
                                    GatewayIntent.GUILD_MESSAGES,
                                    GatewayIntent.DIRECT_MESSAGES,
                                    GatewayIntent.MESSAGE_CONTENT, // 🔹 necesario para leer el texto de los mensajes
                                    GatewayIntent.GUILD_MEMBERS    // 🔹 si usas información de miembros (roles, nombres, etc.)
                            ))
                    .setActivity(activity)
                    .setStatus(status)
                    .addEventListeners(
                            new LinkListener(this),
                            new InfoListener(this),
                            new UserInfoListener(this),
                            new ForceUnlinkListener(this),
                            new UnlinkListener(this),
                            new GetPlayerListener(this),
                            new DiscordChatListener(this),
                            new ReloadRanksListener(this, luckPerms),
                            new SyncRanksListener(this),
                            new AlertListener(this, velocityUtils),
                            new StafflistListener(this, velocityUtils),
                            new VlistListener(this, velocityUtils),
                            new StaffchatListener(this, velocityUtils),
                            new AdminchatListener(this, velocityUtils),
                            new DiscordRoleRewardsListener(this)
                    )
                    .disableCache(
                            CacheFlag.VOICE_STATE,
                            CacheFlag.EMOJI,
                            CacheFlag.STICKER,
                            CacheFlag.SCHEDULED_EVENTS
                    )
                    .setAutoReconnect(true)
                    .build();

            jda.awaitReady();

            jda.updateCommands().addCommands(
                    Commands.slash("link", "Link your Discord account with Minecraft"),
                    Commands.slash("info", "Get information about your linked account"),
                    Commands.slash("unlink", "Unlinks your account"),
                    Commands.slash("reloadranks", "Reloads your linked ranks"),
                    Commands.slash("syncranks", "Syncs ranks for all linked users"),
                    Commands.slash("userinfo", "Shows user information")
                            .addOption(OptionType.STRING, "user", "ID or mention", true),
                    Commands.slash("forceunlink", "Unlinks account for other players")
                            .addOption(OptionType.STRING, "user", "ID or mention", true),
                    Commands.slash("getplayer", "Gets Links information for a Minecraft Player")
                            .addOption(OptionType.STRING, "name", "Minecraft Name", true),
                    // VelocityUtils Commands
                    Commands.slash("stafflist", "See the list of online staff members"),
                    Commands.slash("staffchat", "Send a message to the minecraft staff chat")
                            .addOption(OptionType.STRING, "message", "Message to send", true),
                    Commands.slash("adminchat", "Send a message to the minecraft admin chat")
                            .addOption(OptionType.STRING, "message", "Message to send", true),
                    Commands.slash("vlist", "See the list of online players")
                            .addOption(OptionType.BOOLEAN, "rank", "Do you want to list by rank?", true),
                    Commands.slash("alert", "Send alert to the entire network")
                            .addOption(OptionType.INTEGER, "amount", "Number of times to repeat the alert", true)
                            .addOption(OptionType.STRING, "message", "Alert message", true)
            ).queue();

            logger.info("✅ Discord bot initialized: " + jda.getSelfUser().getName());

        } catch (Exception e) {
            logger.error("Error trying to initialize discord bot:", e);
        }
    }

    public void sendBroadcastToServer(String serverName, String message) {
        server.getServer(serverName).ifPresent(server ->
                server.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message))
        );
    }

    public void shutdownBot() {
        if (jda != null) {
            try {
                logger.info("Shutting down Discord bot...");
                jda.shutdownNow();
                jda = null;
            } catch (Exception e) {
                logger.error("Error while shutting down Discord bot:", e);
            }
        }
    }

    public JDA getJDA() {
        return jda;
    }

    public <T> T getConfig(String path, T fallback) {
        String[] keys = path.split("\\.");
        Object current = configValues;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) return fallback;
            current = map.get(key);
            if (current == null) return fallback;
        }
        try {
            return (T) current;
        } catch (ClassCastException e) {
            return fallback;
        }
    }

    public void loadConfig() {
        try {
            File folder = dataDirectory.toFile();
            if (!folder.exists()) folder.mkdirs();

            File configFile = new File(folder, "config.yml");

            if (!configFile.exists()) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath());
                    } else {
                        logger.error("Error trying to read or create config.yml");
                    }
                } catch (IOException e) {
                    logger.error("Error trying to read or create config.yml", e);
                }
            }

            // Cargar config
            Yaml yaml = new Yaml(new SafeConstructor());
            configValues = yaml.load(new FileInputStream(configFile));
        } catch (IOException e) {
            logger.error("Error trying to read or create config.yml", e);
        }
    }

    public void loadLinkedChannels() {
        linkedChannels.clear();
        if (!getConfig("messaging.enabled", false)) return;
        Map<String, Map<String, Object>> messaging = getConfig("messaging.channels", Map.of());
        for (Map.Entry<String, Map<String, Object>> entry : messaging.entrySet()) {
            String serverName = entry.getKey();
            Object idObj = entry.getValue().get("channel_id");
            if (idObj != null && (idObj != "123456789123456789" && idObj != "987654321987654321")) {
                linkedChannels.put(String.valueOf(idObj), serverName);
            }
        }
        logger.info("Loaded " + linkedChannels.size() + " linked Discord channels.");
    }

    public Map<String, String> getLinkedChannels() {
        return linkedChannels;
    }

    public void loadLinkedRanks() {
        linkedRanks.clear();
        allRanks.clear();
        if (!getConfig("link.luckperms.enabled", false)) return;
        Map<String, Map<String, Object>> messaging = getConfig("link.luckperms.roles", Map.of());
        for (Map.Entry<String, Map<String, Object>> entry : messaging.entrySet()) {
            String rankName = entry.getKey();
            Object idObj = entry.getValue().get("discord_role");
            if (idObj != null && (idObj != "123456789123456789" && idObj != "987654321987654321")) {
                linkedRanks.put(String.valueOf(idObj), rankName);
                allRanks.add(String.valueOf(idObj));
            }
        }
        logger.info("Loaded " + linkedRanks.size() + " linked Ranks.");
    }

    public Map<String, String> getLinkedRanks() {
        return linkedRanks;
    }
    public List<String> getAllRanks() {
        return allRanks;
    }

    public Map<String, Map<String, List<String>>> getDiscordRankRewards() {
        return getConfig("link.discord_ranks_rewards.roles", Map.of());
    }

    public long getInitialDailyRewardDelaySeconds() {
        int rewardHour = getConfig("link.discord_ranks_rewards.daily_hour", 12);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now
                .withHour(rewardHour)
                .withMinute(1)
                .withSecond(0);

        if (now.compareTo(nextRun) >= 0) {
            nextRun = nextRun.plusDays(1);
        }

        return Duration.between(now, nextRun).getSeconds();
    }

    public static String formatDuration(long seconds) {
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();

        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");

        return sb.toString().trim();
    }


    public static final LegacyComponentSerializer LEGACY_HEX_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors() // Habilita el soporte de hex
            .useUnusualXRepeatedCharacterHexFormat() // Soporta &x&r&r&g&g&b&b
            .build();
    public Component legacy(String s) {
        return LEGACY_HEX_SERIALIZER.deserialize(s);
    }
}
