package org.rexi.discordBridgeVelocity.utils;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.TimeUnit;

public class UpdateChecker {

    private final ProxyServer server;
    private final DiscordBridgeVelocity plugin;
    private final String currentVersion;
    private final String updateUrl;

    public UpdateChecker(ProxyServer server, DiscordBridgeVelocity plugin, String currentVersion, String updateUrl) {
        this.server = server;
        this.plugin = plugin;
        this.currentVersion = currentVersion;
        this.updateUrl = updateUrl;
    }

    public void checkForUpdates() {
        server.getScheduler().buildTask(plugin, () -> {
            try {
                URL url = new URL(updateUrl);
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
                String latestVersion = reader.readLine().trim();
                reader.close();

                if (!latestVersion.equalsIgnoreCase(currentVersion)) {
                    String message = plugin.getConfig("messages.new_version", "&c⚠ A new version of DiscordBridgeVelocity is available: {version}! Download it from: {url}")
                            .replace("{version}", latestVersion).replace("{url}", "https://www.spigotmc.org/resources/discordbridgevelocity.130647/");
                    server.getConsoleCommandSource().sendMessage(legacy(message));
                }
            } catch (IOException e) {
                server.getConsoleCommandSource().sendMessage(
                        Component.text("§6[DiscordBridgeVelocity] §cError validating updates."));
            }
        }).delay(3, TimeUnit.SECONDS).schedule();
    }

    public void checkForUpdatesPlayer(Player player) {
        server.getScheduler().buildTask(plugin, () -> {
            try {
                URL url = new URL(updateUrl);
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
                String latestVersion = reader.readLine().trim();
                reader.close();

                if (!latestVersion.equalsIgnoreCase(currentVersion)) {
                    String message = plugin.getConfig("messages.new_version", "&c⚠ A new version of DiscordBridgeVelocity is available: {version}! Download it from: {url}")
                            .replace("{version}", latestVersion).replace("{url}", "https://www.spigotmc.org/resources/discordbridgevelocity.130647/");
                    Component tpLine = legacy(message)
                            .clickEvent(ClickEvent.openUrl("https://www.spigotmc.org/resources/discordbridgevelocity.130647/"));
                    player.sendMessage(tpLine);
                }
            } catch (IOException e) {
                player.sendMessage(
                        Component.text("§6[DiscordBridgeVelocity] §cError validating updates."));
            }
        }).delay(3, TimeUnit.SECONDS).schedule();
    }

    private Component legacy(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }
}
