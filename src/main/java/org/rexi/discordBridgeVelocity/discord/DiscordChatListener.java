package org.rexi.discordBridgeVelocity.discord;

import com.velocitypowered.api.proxy.ProxyServer;
import litebans.api.Database;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DiscordChatListener extends ListenerAdapter {

    private final DiscordBridgeVelocity plugin;
    private final ProxyServer server;

    public DiscordChatListener(DiscordBridgeVelocity plugin, ProxyServer server) {
        this.plugin = plugin;
        this.server = server;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) return;
        if (!plugin.getConfig("messaging.enabled", false)) return;

        String channelId = event.getChannel().getId();

        String serverName = plugin.getLinkedChannels().get(channelId);
        if (serverName == null) return;

        String discordId = event.getAuthor().getId();
        Optional<String> mcNameOpt = plugin.getDatabase().getMinecraftName(discordId);
        Optional<String> mcUUIDOpt = plugin.getDatabase().getMinecraftUUID(discordId);

        if (mcNameOpt.isEmpty() || mcUUIDOpt.isEmpty()) {
            event.getMessage().delete().queue();
            event.getChannel().sendMessage(
                    event.getAuthor().getAsMention() + " " + plugin.getConfig("discord_messages.messaging_no_linked", "❌ Link your minecraft account to send messages on this channel.")
            ).queue(msg -> {
                msg.delete().queueAfter(5, TimeUnit.SECONDS);
            });
            return;
        }

        String mcName = mcNameOpt.get();

        if (plugin.getConfig("messaging.litebans", false)) {
            boolean banned = Database.get().isPlayerBanned(UUID.fromString(mcUUIDOpt.get()), null);
            boolean muted = Database.get().isPlayerMuted(UUID.fromString(mcUUIDOpt.get()), null);

            if (banned || muted) {
                event.getMessage().delete().queue();
                event.getChannel().sendMessage(
                        event.getAuthor().getAsMention() + " " + plugin.getConfig("discord_messages.messaging_litebans_banned_or_muted", "❌ You have an active punish ingame, you cannot talk.")
                ).queue(msg -> {
                    msg.delete().queueAfter(5, TimeUnit.SECONDS);
                });
                return;
            }
        }

        String message = event.getMessage().getContentDisplay();

        String format = plugin.getConfig("messages.messaging", "&9[Discord] &f{player} &7» &r{message}");

        String formatted = format
                .replace("{player}", mcName)
                .replace("{message}", message);

        server.getServer(serverName).ifPresent(server ->
                server.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(formatted))
        );

        plugin.logger.info("[DiscordChat] (" + serverName + ") " + mcName + ": " + message);
    }
}
