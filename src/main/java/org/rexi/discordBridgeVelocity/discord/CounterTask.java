package org.rexi.discordBridgeVelocity.discord;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class CounterTask {

    private final DiscordBridgeVelocity plugin;
    private final ProxyServer server;
    private ScheduledTask task;

    private int lastPlayerCount = -1;
    private int lastMaxCount = -1;
    private int maxPlayers = -1;

    private long lastConnectedUpdate = 0;
    private long lastMaxUpdate = 0;
    private static final long RENAME_COOLDOWN_MS = 10 * 60 * 1000; // 10 minutes

    public CounterTask(DiscordBridgeVelocity plugin, ProxyServer server) {
        this.plugin = plugin;
        this.server = server;
    }

    public void start() {
        cancel();
        if (!plugin.getConfig("discord_counter.enabled", false)) return;
        getMaxPlayersFromDB();

        lastPlayerCount = -1;
        lastMaxCount = -1;

        plugin.logger.info("⏱️ Counter every 10 minutes...");

        this.task = server.getScheduler()
                .buildTask(plugin, () -> runAsync(null, null))
                .repeat(RENAME_COOLDOWN_MS, TimeUnit.MILLISECONDS)
                .schedule();
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void runAsync(BiConsumer<Integer, Integer> progressCallback, Runnable onFinish) {
        server.getScheduler().buildTask(plugin, () -> {
            String guildId = plugin.getConfig("link.guild-id", "123456789123456789");
            var guild = plugin.getJDA().getGuildById(guildId);
            if (guild == null) {
                plugin.logger.warn("⚠️ No Discord server found as: " + guildId);
                return;
            }

            if (plugin.getConfig("discord_counter.channels.connected.enabled", false)) {
                int playerCount = server.getPlayerCount();
                if (lastPlayerCount == playerCount) return;

                long now = System.currentTimeMillis();
                if (now - lastConnectedUpdate < RENAME_COOLDOWN_MS) return;

                lastPlayerCount = playerCount;
                lastConnectedUpdate = now;

                String channelId = plugin.getConfig("discord_counter.channels.connected.channel_id", "");
                String text = plugin.getConfig("discord_counter.channels.connected.text", "Players Online: {count}");
                var channel = guild.getGuildChannelById(channelId);
                if (channel != null) {
                    String name = text.replace("{count}", String.valueOf(playerCount));
                    channel.getManager().setName(name).queue(
                            success -> { if (progressCallback != null) progressCallback.accept(playerCount, 0); },
                            error -> plugin.logger.warn("⚠️ Could not update connected channel: " + error.getMessage())
                    );
                } else {
                    plugin.logger.warn("⚠️ Connected channel not found: " + channelId);
                }
            }

            if (plugin.getConfig("discord_counter.channels.max.enabled", false)) {
                if (lastMaxCount == maxPlayers) return;

                long now = System.currentTimeMillis();
                if (now - lastMaxUpdate < RENAME_COOLDOWN_MS) return;

                lastMaxCount = maxPlayers;
                lastMaxUpdate = now;

                String channelId = plugin.getConfig("discord_counter.channels.max.channel_id", "");
                String text = plugin.getConfig("discord_counter.channels.max.text", "Max Players: {count}");
                var channel = guild.getGuildChannelById(channelId);
                if (channel != null) {
                    String name = text.replace("{count}", String.valueOf(maxPlayers));
                    channel.getManager().setName(name).queue(
                            success -> { if (onFinish != null) onFinish.run(); },
                            error -> plugin.logger.warn("⚠️ Could not update max channel: " + error.getMessage())
                    );
                } else {
                    plugin.logger.warn("⚠️ Max channel not found: " + channelId);
                }
            }
        }).schedule(); // async
    }

    @Subscribe
    public void onJoin(ServerPostConnectEvent event) {
        int playerCount = server.getPlayerCount();
        if (playerCount > maxPlayers) {
            maxPlayers = playerCount;
            plugin.getDatabase().updateMaxPlayers(maxPlayers);
        }
    }

    private void getMaxPlayersFromDB() {
        maxPlayers = plugin.getDatabase().getMaxPlayers();
    }
}
