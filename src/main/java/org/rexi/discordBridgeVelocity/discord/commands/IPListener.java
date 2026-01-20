package org.rexi.discordBridgeVelocity.discord.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.util.List;

public class IPListener extends ListenerAdapter {

    private final DiscordBridgeVelocity plugin;

    public IPListener(DiscordBridgeVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("ip")) return;

        List<String> fallback = List.of(
                "**IP:** test.example.com",
                "**Online Players:** {online_players}",
                "**Website:** www.example.com"
        );

        String title = plugin.getConfig("discord_messages.ip.title", "🎮 Server Information");
        List<String> description = plugin.getConfig("discord_messages.ip.message", fallback);

        int totalPlayers = plugin.server.getAllPlayers().size();

        MessageEmbed embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(String.join("\n", description).replace("{online_players}", String.valueOf(totalPlayers)))
                .setColor(Integer.parseInt(plugin.getConfig("discord_messages.ip.color", "D600CB"), 16))
                .build();

        event.replyEmbeds(embed).queue();
    }
}
