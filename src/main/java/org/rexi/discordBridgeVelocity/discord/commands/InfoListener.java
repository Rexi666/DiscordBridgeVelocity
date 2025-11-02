package org.rexi.discordBridgeVelocity.discord.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.util.List;

public class InfoListener extends ListenerAdapter {

    private final DiscordBridgeVelocity plugin;

    public InfoListener(DiscordBridgeVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("info")) return;

        String userId = event.getUser().getId();

        if (!plugin.getDatabase().getMinecraftUUID(userId).isPresent()) {
            List<String> fallback = List.of(
                    "Your Discord account is not linked with any in-game account.",
                    "To link your account, use `/link`"
            );

            String titleError = plugin.getConfig("discord_messages.info-not-linked.title", "⚠\uFE0F Account Not Linked");
            List<String> descriptionError = plugin.getConfig("discord_messages.info-not-linked.message", fallback);

            MessageEmbed errorEmbed = new EmbedBuilder()
                    .setTitle(titleError)
                    .setDescription(String.join("\n", descriptionError))
                    .setColor(Integer.parseInt(plugin.getConfig("discord_messages.info-not-linked.color", "FA0000"), 16))
                    .build();

            event.replyEmbeds(errorEmbed).setEphemeral(true).queue();
            return;
        }

        List<String> fallback = List.of(
                "**Your Discord account is linked with the following in-game account:**",
                "",
                "Username: `{username}`",
                ",",
                "To unlink your account, use `/unlink`"
        );

        String playerName = plugin.getDatabase().getMinecraftName(userId).orElse("Unknown");

        String titleError = plugin.getConfig("discord_messages.info.title", "\uD83D\uDD17 Linked Account Information");
        List<String> descriptionError = plugin.getConfig("discord_messages.info.message", fallback);

        MessageEmbed errorEmbed = new EmbedBuilder()
                .setTitle(titleError)
                .setDescription(String.join("\n", descriptionError).replace("{username}", playerName))
                .setColor(Integer.parseInt(plugin.getConfig("discord_messages.info.color", "D600CB"), 16))
                .build();

        event.replyEmbeds(errorEmbed).setEphemeral(true).queue();
    }
}
