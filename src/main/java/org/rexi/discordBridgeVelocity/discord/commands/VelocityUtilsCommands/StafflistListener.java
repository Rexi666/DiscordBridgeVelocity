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

public class StafflistListener extends ListenerAdapter {

    private final DiscordBridgeVelocity plugin;
    private final Object velocityUtils;

    public StafflistListener(DiscordBridgeVelocity plugin, Object velocityUtils) {
        this.plugin = plugin;
        this.velocityUtils = velocityUtils;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("stafflist")) return;

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

        if (!plugin.getConfig("velocity_utils_commands.enabled", false) || !plugin.getConfig("velocity_utils_commands.stafflist.enabled", true)) {
            event.reply(plugin.getConfig("discord_messages.velocity_utils_commands_disabled", "❌ This command is disabled")).setEphemeral(true).queue();
            return;
        }

        Map<String, String[]> staffList;

        try {
            staffList = (Map<String, String[]>) velocityUtils.getClass()
                    .getMethod("getStaffList")
                    .invoke(velocityUtils);
        } catch (Exception e) {
            e.printStackTrace();
            event.reply(plugin.getConfig("discord_messages.velocity_utils_commands_disabled", "❌ This command is disabled"))
                    .setEphemeral(true).queue();
            return;
        }

        if (staffList.isEmpty()) {
            event.reply(plugin.getConfig("discord_messages.stafflist.no_staff", "❌ There are no staff members connected.")).setEphemeral(true).queue();
            return;
        }

        int totalStaff = staffList.size();

        List<String> descriptionFallback = List.of(
                "**There are a total of {total} staff connected**",
                "",
                "{staff_list}"
        );

        List<String> staffListFormatted = new ArrayList<>();
        String format = plugin.getConfig("discord_messages.stafflist.staff_list", "- {player} ({rank} - {server})");
        for (var entry : staffList.entrySet()) {
            String player = entry.getKey();
            String rank = entry.getValue()[0];
            String server = entry.getValue()[1];
            staffListFormatted.add(format
                    .replace("{player}", "`"+player+"`")
                    .replace("{rank}", rank)
                    .replace("{server}", server));
        }

        List<String> description = plugin.getConfig("discord_messages.stafflist.message", descriptionFallback);

        MessageEmbed embed = new EmbedBuilder()
                .setTitle(plugin.getConfig("discord_messages.stafflist.title", "📋 StaffList"))
                .setDescription(String.join("\n", description)
                        .replace("{staff_list}", String.join("\n", staffListFormatted))
                        .replace("{total}", String.valueOf(totalStaff)))
                .setColor(Integer.parseInt(plugin.getConfig("discord_messages.stafflist.color", "7202C7"), 16))
                .build();

        event.replyEmbeds(embed).setEphemeral(true).queue();
    }
}
