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
import org.rexi.discordBridgeVelocity.commands.DiscordBridgeCommand;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Plugin(id = "discord_bridge_velocity", name = "Discord Bridge Velocity", version = BuildConstants.VERSION, authors = {"Rexi666"})
public class DiscordBridgeVelocity {

    private final ProxyServer server;
    private JDA jda;
    private final Path dataDirectory;

    private String token;

    private final Map<String, String> configValues = new HashMap<>();
    @Inject
    public DiscordBridgeVelocity(ProxyServer server, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    @Inject
    private Logger logger;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();
        initializeBot();

        server.getCommandManager().register("discordbridge", new DiscordBridgeCommand(this));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        shutdownBot();
    }

    public void initializeBot() {
        try {
            String token = getConfig("token", "DISCORD_BOT_TOKEN");
            if (token == null || token.isEmpty() || token.equals("DISCORD_BOT_TOKEN")) {
                logger.warn("No valid Discord bot token found in configuration. Discord bot will not be initialized.");
                return;
            }

            logger.info("Starting Discord Bot...");

            String statusType = getConfig("statusType", "PLAYING");
            String statusText = getConfig("statusText", "My Velocity Bot");
            String statusMode = getConfig("statusMode", "ONLINE");

            logger.info("Type: "+ statusType);
            logger.info("Mode: "+ statusMode);

            Activity activity;
            if (statusText == null || statusText.isEmpty()) {
                activity = null;
            } else if (statusType == null) {
                activity = Activity.customStatus(statusText);
            } else {
                switch (statusType.toUpperCase()) {
                    case "LISTENING":
                        activity = Activity.listening(statusText);
                        logger.info("1");
                        break;
                    case "WATCHING":
                        activity = Activity.watching(statusText);
                        logger.info("2");
                        break;
                    case "COMPETING":
                        activity = Activity.competing(statusText);
                        logger.info("3");
                        break;
                    case "PLAYING":
                        activity = Activity.playing(statusText);
                        logger.info("4");
                        break;
                    default:
                        activity = Activity.customStatus(statusText);
                        logger.info("5");
                        break;
                }
            }

            OnlineStatus status = OnlineStatus.ONLINE;
            if (statusMode != null) {
                switch (statusMode.toUpperCase()) {
                    case "IDLE":
                        status = OnlineStatus.IDLE;
                        logger.info("A");
                        break;
                    case "DND":
                        status = OnlineStatus.DO_NOT_DISTURB;
                        logger.info("B");
                        break;
                    case "INVISIBLE":
                        status = OnlineStatus.INVISIBLE;
                        logger.info("C");
                        break;
                    default:
                        status = OnlineStatus.ONLINE;
                        logger.info("D");
                        break;
                }
            }

            jda = JDABuilder.createDefault(token)
                    .setActivity(activity)
                    .setStatus(status)
                    //.addEventListeners(new DiscordListener())
                    .setAutoReconnect(true)
                    .build();

            jda.awaitReady();

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

    public String getConfig(String key, String fallback) {
        if (!configValues.containsKey(key)) {
            return fallback;
        }
        return configValues.get(key);
    }

    public void loadConfig() {
        try {
            File folder = dataDirectory.toFile();
            if (!folder.exists()) folder.mkdirs();

            File configFile = new File(folder, "config.yml");

            if (!configFile.exists()) {
                List<String> defaultConfig = List.of(
                        "# Discord bot token",
                        "token: \"DISCORD_BOT_TOKEN\"",
                        "",
                        "# Discord bot status",
                        "statusMode: ONLINE # ONLINE, IDLE, DND, INVISIBLE",
                        "statusType: WATCHING # Valid Types: PLAYING, LISTENING, WATCHING, COMPETING, NONE",
                        "statusText: \"My Velocity Bot\""
                );
                Files.write(configFile.toPath(), defaultConfig);
            }

            List<String> lines = Files.readAllLines(configFile.toPath());
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("#") || !line.contains(":")) continue;

                String[] parts = line.split(":", 2);
                String key = parts[0].trim();
                String value = parts[1].split("#", 2)[0].trim().replace("\"", "");
                configValues.put(key, value);
            }
        } catch (IOException e) {
            logger.error("Error trying to read or create config.yml", e);
        }
    }
}
