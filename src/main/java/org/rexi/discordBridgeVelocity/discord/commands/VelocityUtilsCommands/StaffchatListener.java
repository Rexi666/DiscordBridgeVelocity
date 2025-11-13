package org.rexi.discordBridgeVelocity.discord.commands.VelocityUtilsCommands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;
import org.rexi.velocityUtils.api.VelocityUtilsAPI;

import java.util.List;
import java.util.Optional;

public class StaffchatListener extends ListenerAdapter {

    private final DiscordBridgeVelocity plugin;
    private final VelocityUtilsAPI velocityUtils;

    public StaffchatListener(DiscordBridgeVelocity plugin, VelocityUtilsAPI velocityUtils) {
        this.plugin = plugin;
        this.velocityUtils = velocityUtils;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("staffchat")) return;

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

        if (plugin.getConfig("velocity_utils_commands.enabled", false) && plugin.getConfig("velocity_utils_commands.staffchat.enabled", true)) {
            event.reply(plugin.getConfig("discord_messages.velocity_utils_commands_disabled", "❌ This command is disabled")).setEphemeral(true).queue();
            return;
        }

        Optional<String> minecraftNameOpt = plugin.getDatabase().getMinecraftName(executor.getId());

        if (minecraftNameOpt.isEmpty()) {
            event.reply(plugin.getConfig("discord_messages.staff_admin_chat_no_linked", "❌ You need to link your account to use that command."))
                    .setEphemeral(true).queue();
            return;
        }

        String message = event.getOption("message") != null ? event.getOption("message").getAsString() : null;
        if (message == null || message.isEmpty()) {
            event.reply(plugin.getConfig("discord_messages.staff_chat_usage", "Usage: `/staffchat <message>`"))
                    .setEphemeral(true).queue();
            return;
        }

        velocityUtils.sendStaffChatMessage(minecraftNameOpt.get(), message, "discord");
        event.reply(plugin.getConfig("discord_messages.staff_admin_chat_sent", "✅ Message sent to the minecraft chat."))
                .setEphemeral(true).queue();
    }
}