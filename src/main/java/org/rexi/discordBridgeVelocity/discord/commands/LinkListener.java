package org.rexi.discordBridgeVelocity.discord.commands;

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

            MessageEmbed errorEmbed = new EmbedBuilder()
                    .setTitle(titleError)
                    .setDescription(descriptionError.replace("{username}", plugin.getDatabase().getMinecraftName(userId).orElse("Unknown")))
                    .setColor(Integer.parseInt(plugin.getConfig("discord_messages.already-linked.color", "FA0000"), 16))
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

        MessageEmbed embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(String.join("\n", description).replace("{code}", code))
                .setColor(Integer.parseInt(plugin.getConfig("discord_messages.link-message.color", "D600CB"), 16))
                .build();

        plugin.getJDA().retrieveUserById(userId)
                .flatMap(user -> user.openPrivateChannel())
                .flatMap(channel -> channel.sendMessageEmbeds(embed))
                .queue(
                        success -> {
                            String reply = plugin.getConfig("discord_messages.dm", "✅ Message sent via DM.");
                            event.reply(reply).setEphemeral(true).queue();
                        },
                        error -> {
                            String titleError = plugin.getConfig("discord_messages.dm-error.title", "⚠\uFE0F Unable to send DM");
                            String descriptionError = plugin.getConfig("discord_messages.dm-error.message", "Please, enable your DMs to receive messages from the bot.");

                            MessageEmbed errorEmbed = new EmbedBuilder()
                                    .setTitle(titleError)
                                    .setDescription(descriptionError)
                                    .setColor(Integer.parseInt(plugin.getConfig("discord_messages.dm-error.color", "FA0000"), 16))
                                    .build();

                            event.replyEmbeds(errorEmbed).setEphemeral(true).queue();
                        }
                );

    }
}
