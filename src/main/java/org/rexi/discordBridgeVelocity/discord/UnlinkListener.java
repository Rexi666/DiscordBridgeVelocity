package org.rexi.discordBridgeVelocity.discord;


import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.awt.*;
import java.util.List;
import java.util.Optional;

public class UnlinkListener extends ListenerAdapter {

    private final DiscordBridgeVelocity plugin;

    public UnlinkListener(DiscordBridgeVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("unlink")) return;

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

        // Obtener argumento <id/@>
        String arg = event.getOption("user") != null ? event.getOption("user").getAsString() : null;
        if (arg == null || arg.isEmpty()) {
            event.reply(plugin.getConfig("discord_messages.unlink_usage", "Usage: `/unlink <id or mention>`"))
                    .setEphemeral(true).queue();
            return;
        }

        String userId = arg.replaceAll("[^0-9]", ""); // eliminar caracteres no numéricos
        plugin.getJDA().retrieveUserById(userId).queue(user -> {
            if (user == null) {
                event.reply(plugin.getConfig("discord_messages.no_user_found", "❌ No user could be found with id: `{userID}`.")
                                .replace("{userID}", userId))
                        .setEphemeral(true)
                        .queue();
                return;
            }

            // Verificamos si el usuario tiene un vínculo
            Optional<String> minecraftName = plugin.getDatabase().getMinecraftName(userId);
            if (minecraftName.isEmpty()) {
                event.reply(plugin.getConfig("discord_messages.unlink_no_linked", "⚠️ User `{discordTag}` is not linked.")
                                .replace("{discordTag}", "<@" + userId + ">"))
                        .setEphemeral(true)
                        .queue();
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getConfig("discord_messages.unlink_confirm.title", "⚠️ Confirm Unlink"))
                    .setColor(plugin.getConfig("discord_messages.unlink_confirm.color", 220235200))
                    .setDescription(plugin.getConfig("discord_messages.unlink_confirm.message", "Are you sure you want to unlink the Discord account {userId} from Minecraft player `{username}`?")
                            .replace("{userId}", "<@" + userId + ">")
                            .replace("{username}", minecraftName.get()));

            // Enviar mensaje con botones
            event.replyEmbeds(embed.build())
                    .addActionRow(
                            net.dv8tion.jda.api.interactions.components.buttons.Button.success("unlink_yes:" + userId, "Yes"),
                            net.dv8tion.jda.api.interactions.components.buttons.Button.danger("unlink_no:" + userId, "No")
                    )
                    .setEphemeral(true)
                    .queue();

        }, failure -> {
            event.reply(plugin.getConfig("discord_messages.no_user_found", "❌ No user could be found with id: `{userID}`.")
                            .replace("{userID}", userId))
                    .setEphemeral(true)
                    .queue();
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String[] idParts = event.getButton().getId().split(":");
        if (idParts.length != 2) return;

        String action = idParts[0];
        String userId = idParts[1];

        if (action.equals("unlink_yes")) {
            Optional<String> minecraftName = plugin.getDatabase().getMinecraftName(userId);
            if (minecraftName.isPresent()) {
                plugin.getDatabase().unlinkUser(userId);
                event.editMessage(plugin.getConfig("discord_messages.unlink_confirm.message_successful", "✅ Successfully unlinked Discord account {userId} from Minecraft player `{username}`.")
                        .replace("{userId}", "<@" + userId + ">")
                        .replace("{username}", minecraftName.get())).setComponents().queue();
            } else {
                event.editMessage(plugin.getConfig("discord_messages.unlink_no_linked", "⚠\uFE0F User `{discordTag}` is not linked.")
                        .replace("{discordTag}", "<@" + userId + ">")).setComponents().queue();
            }
        } else if (action.equals("unlink_no")) {
            event.editMessage(plugin.getConfig("discord_messages.unlink_confirm.message_cancelled", "❌ Unlinking process cancelled.")).setComponents().queue();
        }
    }
}
