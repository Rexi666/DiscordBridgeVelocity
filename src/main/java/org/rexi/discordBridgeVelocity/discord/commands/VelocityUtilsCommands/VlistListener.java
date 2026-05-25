package org.rexi.discordBridgeVelocity.discord.commands.VelocityUtilsCommands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VlistListener extends ListenerAdapter {

    private final DiscordBridgeVelocity plugin;
    private final Object velocityUtils;

    public VlistListener(DiscordBridgeVelocity plugin, Object velocityUtils) {
        this.plugin = plugin;
        this.velocityUtils = velocityUtils;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("vlist")) return;

        if (velocityUtils == null) {
            event.reply(plugin.getConfig("discord_messages.velocity_utils_commands_disabled", "❌ This command is disabled"))
                    .setEphemeral(true).queue();
            return;
        }

        Member executor = event.getMember();
        if (executor == null) {
            event.reply(plugin.getConfig("discord_messages.only_server", "❌ This command can only be used on a server."))
                    .setEphemeral(true).queue();
            return;
        }

        // Comprobamos permisos
        List<String> allowedRoles = plugin.getConfig("admin_commands.allowed_roles", List.of());
        boolean hasPermission = executor.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)
                || executor.getRoles().stream().anyMatch(role -> allowedRoles.contains(role.getId()));

        if (!hasPermission) {
            event.reply(plugin.getConfig("discord_messages.no_permission", "🚫 You don't have permission to use this command."))
                    .setEphemeral(true).queue();
            return;
        }

        if (!plugin.getConfig("velocity_utils_commands.enabled", false) || !plugin.getConfig("velocity_utils_commands.vlist.enabled", true)) {
            event.reply(plugin.getConfig("discord_messages.velocity_utils_commands_disabled", "❌ This command is disabled")).setEphemeral(true).queue();
            return;
        }

        Boolean byrank = event.getOption("rank") != null ? event.getOption("rank").getAsBoolean() : null;
        if (byrank == null) {
            event.reply(plugin.getConfig("discord_messages.vlist.usage", "Usage: `/vlist true/false` (true = group by rank, false = group by server)"))
                    .setEphemeral(true).queue();
            return;
        }

        Map<String, List<String>> vlist;

        try {
            vlist = (Map<String, List<String>>) velocityUtils.getClass()
                    .getMethod("getList", Boolean.class)
                    .invoke(velocityUtils, byrank);
        } catch (Exception e) {
            e.printStackTrace();
            event.reply(plugin.getConfig("discord_messages.velocity_utils_commands_disabled", "❌ This command is disabled"))
                    .setEphemeral(true).queue();
            return;
        }

        if (vlist.isEmpty()) {
            event.reply(plugin.getConfig("discord_messages.vlist.no_players", "❌ There are no players connected.")).setEphemeral(true).queue();
            return;
        }

        int totalPlayers = plugin.server.getAllPlayers().size();

        List<String> descriptionFallback = List.of(
                "**There are a total of {total} players connected**",
                "",
                "{player_list}"
        );

        List<String> vlistFormatted = new ArrayList<>();
        String format = plugin.getConfig("discord_messages.vlist.player_list", "- {identifier} ({num}) - {players}");
        for (var entry : vlist.entrySet()) {
            String identifier = entry.getKey();
            List<String> players = entry.getValue();
            vlistFormatted.add(format
                    .replace("{identifier}", identifier)
                    .replace("{num}", String.valueOf(players.size()))
                    .replace("{players}", "`"+String.join("`, `", players)+"`"));
        }

        List<String> description = plugin.getConfig("discord_messages.vlist.message", descriptionFallback);

        MessageEmbed embed = new EmbedBuilder()
                .setTitle(plugin.getConfig("discord_messages.vlist.title", "📋 vList"))
                .setDescription(String.join("\n", description)
                        .replace("{player_list}", String.join("\n", vlistFormatted))
                        .replace("{total}", String.valueOf(totalPlayers)))
                .setColor(Integer.parseInt(plugin.getConfig("discord_messages.vlist.color", "7202C7"), 16))
                .build();

        event.replyEmbeds(embed).setEphemeral(true).queue();
    }
}
