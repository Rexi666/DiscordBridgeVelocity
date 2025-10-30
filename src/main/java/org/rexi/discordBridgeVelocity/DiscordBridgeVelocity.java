package org.rexi.discordBridgeVelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.rexi.discordBridgeVelocity.commands.DiscordBridgeCommand;
import org.rexi.discordBridgeVelocity.commands.LinkCommand;
import org.rexi.discordBridgeVelocity.discord.InfoListener;
import org.rexi.discordBridgeVelocity.discord.LinkListener;
import org.rexi.discordBridgeVelocity.discord.UserInfoListener;
import org.rexi.discordBridgeVelocity.utils.DBManager;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Plugin(id = "discord_bridge_velocity", name = "Discord Bridge Velocity", version = BuildConstants.VERSION, authors = {"Rexi666"})
public class DiscordBridgeVelocity {

    private final ProxyServer server;
    private JDA jda;
    private final Path dataDirectory;
    private DBManager dbManager;

    private Map<String, String> configValues = new HashMap<>();
    @Inject
    public DiscordBridgeVelocity(ProxyServer server, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    @Inject
    public Logger logger;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();
        initializeDatabase();
        initializeBot();

        server.getCommandManager().register("discordbridge", new DiscordBridgeCommand(this));
        server.getCommandManager().register("link", new LinkCommand(this));

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

            jda = JDABuilder.createDefault(token)
                    .setActivity(activity)
                    .setStatus(status)
                    .addEventListeners(new LinkListener(this))
                    .addEventListeners(new InfoListener(this))
                    .addEventListeners(new UserInfoListener(this))
                    .setAutoReconnect(true)
                    .build();

            jda.awaitReady();

            jda.updateCommands().addCommands(
                    Commands.slash("link", "Link your Discord account with Minecraft"),
                    Commands.slash("info", "Get information about your linked account"),
                    Commands.slash("userinfo", "Shows user information")
                            .addOption(OptionType.STRING, "user", "ID or mention", true)
            ).queue();

            jda.getGuildById("956988393647124510")
                    .updateCommands()
                    .addCommands(
                            Commands.slash("link", "Link your Discord account with Minecraft"),
                            Commands.slash("info", "Get information about your linked account"),
                            Commands.slash("userinfo", "Shows user information")
                                    .addOption(OptionType.STRING, "user", "ID or mention", true)
                    ).queue();

            logger.info("✅ Discord bot initialized: " + jda.getSelfUser().getName());

        } catch (Exception e) {
            logger.error("Error trying to initialize discord bot:", e);
        }
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

    public static final LegacyComponentSerializer LEGACY_HEX_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors() // Habilita el soporte de hex
            .useUnusualXRepeatedCharacterHexFormat() // Soporta &x&r&r&g&g&b&b
            .build();
    public Component legacy(String s) {
        return LEGACY_HEX_SERIALIZER.deserialize(s);
    }
}
