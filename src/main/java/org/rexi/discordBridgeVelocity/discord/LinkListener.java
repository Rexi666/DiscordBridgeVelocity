package org.rexi.discordBridgeVelocity.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;
import org.rexi.discordBridgeVelocity.utils.LinkManager;

import java.util.List;

public class LinkListener extends ListenerAdapter {

    private final DiscordBridgeVelocity plugin;

    public LinkListener(DiscordBridgeVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("link")) return;

        String userId = event.getUser().getId();

        if (plugin.getDatabase().getMinecraftUUID(userId).isPresent()) {
            String titleError = plugin.getConfig("discord_messages.already-linked.title", "⚠\uFE0F Account Already Linked");
            String descriptionError = plugin.getConfig("discord_messages.already-linked.message", "Your Discord account is already linked with an in-game account: {username}");

            Object rawColor = plugin.getConfig("discord_messages.already-linked.color", "250000000");
            int colorError;

            if (rawColor instanceof Number) {
                colorError = ((Number) rawColor).intValue();
            } else {
                colorError = Integer.parseInt(rawColor.toString());
            }

            MessageEmbed errorEmbed = new EmbedBuilder()
                    .setTitle(titleError)
                    .setDescription(descriptionError.replace("{username}", plugin.getDatabase().getMinecraftName(userId).orElse("Unknown")))
                    .setColor(colorError)
                    .build();

            event.replyEmbeds(errorEmbed).setEphemeral(true).queue();
            return;
        }

        String code = LinkManager.generateCode(userId);

        List<String> descriptionfallback = List.of(
                "**Link your Discord account with your in-game account!**",
                "",
                "1. Join the Minecraft Server: www.example.com",
                "2. Run the command: `/link {code}` in-game"
        );

        String title = plugin.getConfig("discord_messages.link-message.title", "🔗 Link Your Account");
        List<String> description = plugin.getConfig("discord_messages.link-message.message", descriptionfallback);

        Object rawColor = plugin.getConfig("discord_messages.link-message.color", 214000203);
        int color;

        if (rawColor instanceof Number) {
            color = ((Number) rawColor).intValue();
        } else {
            color = Integer.parseInt(rawColor.toString());
        }

        MessageEmbed embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(String.join("\n", description).replace("{code}", code))
                .setColor(color)
                .build();

        plugin.getJDA().getUserById(userId).openPrivateChannel()
                .flatMap(channel -> channel.sendMessageEmbeds(embed))
                .queue(
                        success -> {
                            String reply = plugin.getConfig("discord_messages.dm", "✅ Message sent via DM.");
                            event.reply(reply).setEphemeral(true).queue();
                        },
                        error -> {
                            String titleError = plugin.getConfig("discord_messages.dm-error.title", "⚠\uFE0F Unable to send DM");
                            String descriptionError = plugin.getConfig("discord_messages.dm-error.message", "Please, enable your DMs to receive messages from the bot.");

                            Object rawColorError = plugin.getConfig("discord_messages.dm-error.color", 250000000);
                            int colorError;

                            if (rawColorError instanceof Number) {
                                colorError = ((Number) rawColorError).intValue();
                            } else {
                                colorError = Integer.parseInt(rawColorError.toString());
                            }

                            MessageEmbed errorEmbed = new EmbedBuilder()
                                    .setTitle(titleError)
                                    .setDescription(descriptionError)
                                    .setColor(colorError)
                                    .build();

                            event.replyEmbeds(errorEmbed).setEphemeral(true).queue();
                        }
                );

    }
}
