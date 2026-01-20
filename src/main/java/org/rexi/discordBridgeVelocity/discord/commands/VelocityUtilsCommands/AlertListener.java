package org.rexi.discordBridgeVelocity.discord.commands.VelocityUtilsCommands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.lang.reflect.Method;
import java.util.List;

public class AlertListener extends ListenerAdapter {

    private final DiscordBridgeVelocity plugin;
    private final Object velocityUtils;

    public AlertListener(DiscordBridgeVelocity plugin, Object velocityUtils) {
        this.plugin = plugin;
        this.velocityUtils = velocityUtils;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("alert")) return;

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

        if (!plugin.getConfig("velocity_utils_commands.enabled", false) || !plugin.getConfig("velocity_utils_commands.alert.enabled", true)) {
            event.reply(plugin.getConfig("discord_messages.velocity_utils_commands_disabled", "❌ This command is disabled")).setEphemeral(true).queue();
            return;
        }

        // Obtener argumento
        String arg = event.getOption("message") != null ? event.getOption("message").getAsString() : null;
        Integer amount = event.getOption("amount") != null
                ? event.getOption("amount").getAsInt()
                : null;
        if (arg == null || arg.isEmpty() || amount == null || amount <= 0) {
            event.reply(plugin.getConfig("discord_messages.alert_usage", "Usage: `/alert <amount> <message>`"))
                    .setEphemeral(true).queue();
            return;
        }

        try {
            Method sendAlert = velocityUtils.getClass()
                    .getMethod("sendAlert", String.class);
            for (int i = 0; i < amount; i++) {
                sendAlert.invoke(velocityUtils, arg);
            }

            event.reply(plugin.getConfig("discord_messages.alert_sent", "✅ Alert sent to all players on the Velocity network."))
                    .setEphemeral(true).queue();
        } catch (Exception e) {
            e.printStackTrace();
            event.reply(plugin.getConfig("discord_messages.velocity_utils_commands_disabled", "❌ This command is disabled"))
                    .setEphemeral(true).queue();
        }
    }
}
