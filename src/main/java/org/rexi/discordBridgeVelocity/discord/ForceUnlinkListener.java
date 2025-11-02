package org.rexi.discordBridgeVelocity.discord;


import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.util.List;
import java.util.Optional;

public class ForceUnlinkListener extends ListenerAdapter {

    private final DiscordBridgeVelocity plugin;

    public ForceUnlinkListener(DiscordBridgeVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("forceunlink")) return;

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
            event.reply(plugin.getConfig("discord_messages.forceunlink_usage", "Usage: `/unlink <id or mention>`"))
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
                event.reply(plugin.getConfig("discord_messages.forceunlink_no_linked", "⚠️ User `{discordTag}` is not linked.")
                                .replace("{discordTag}", "<@" + userId + ">"))
                        .setEphemeral(true)
                        .queue();
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getConfig("discord_messages.forceunlink_confirm.title", "⚠️ Confirm Unlink"))
                    .setColor(Integer.parseInt(plugin.getConfig("discord_messages.forceunlink_confirm.color", "DBB000"), 16))
                    .setDescription(plugin.getConfig("discord_messages.forceunlink_confirm.message", "Are you sure you want to unlink the Discord account {userId} from Minecraft player `{username}`?")
                            .replace("{userId}", "<@" + userId + ">")
                            .replace("{username}", minecraftName.get()));

            // Enviar mensaje con botones
            event.replyEmbeds(embed.build())
                    .addActionRow(
                            net.dv8tion.jda.api.interactions.components.buttons.Button.success("forceunlink_yes:" + userId, plugin.getConfig("discord_messages.button_yes", "✅ Yes")),
                            net.dv8tion.jda.api.interactions.components.buttons.Button.danger("forceunlink_no:" + userId, plugin.getConfig("discord_messages.button_no", "❌ No"))
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
        String buttonId = event.getButton().getId();

        if (buttonId == null || !buttonId.startsWith("forceunlink_")) {
            return;
        }

        String[] idParts = event.getButton().getId().split(":");
        if (idParts.length != 2) return;

        String action = idParts[0];
        String userId = idParts[1];

        if (action.equals("forceunlink_yes")) {
            Optional<String> minecraftName = plugin.getDatabase().getMinecraftName(userId);
            if (minecraftName.isPresent()) {
                plugin.getDatabase().unlinkUser(userId);
                UnlinkDiscord(userId);
                event.reply(plugin.getConfig("discord_messages.forceunlink_confirm.message_successful", "✅ Successfully unlinked Discord account {userId} from Minecraft player `{username}`.")
                        .replace("{userId}", "<@" + userId + ">")
                        .replace("{username}", minecraftName.get())).setEphemeral(true).queue();
            } else {
                event.reply(plugin.getConfig("discord_messages.forceunlink_no_linked", "⚠\uFE0F User `{discordTag}` is not linked.")
                        .replace("{discordTag}", "<@" + userId + ">")).setEphemeral(true).queue();
            }
        } else if (action.equals("forceunlink_no")) {
            event.reply(plugin.getConfig("discord_messages.forceunlink_confirm.message_cancelled", "❌ Unlinking process cancelled.")).setEphemeral(true).queue();
        }
    }

    private void UnlinkDiscord(String discordId) {
        List<String> descriptionfallback = List.of(
                "**Your Minecraft account has been unlinked from your Discord account.**",
                "",
                "If you wish to link again, use `/link`"
        );

        MessageEmbed embed = new EmbedBuilder()
                .setTitle(plugin.getConfig("discord_messages.dm-unlinked.title", "✅ Account Unlinked"))
                .setDescription(String.join("\n", plugin.getConfig("discord_messages.dm-unlinked.message", descriptionfallback)))
                .setColor(Integer.parseInt(plugin.getConfig("discord_messages.dm-unlinked.color", "08C702"), 16))
                .build();

        plugin.getJDA().retrieveUserById(discordId)
                .flatMap(user -> user.openPrivateChannel())
                .flatMap(channel -> channel.sendMessageEmbeds(embed))
                .queue();

        String guildId = plugin.getConfig("link.guild-id", "123456789123456789");
        boolean changeName = plugin.getConfig("link.change_discord_name", false);
        boolean giveRole = plugin.getConfig("link.give_role.enabled", false);

        if (!guildId.equals("123456789123456789") && changeName) {
            plugin.getJDA().getGuildById(guildId).retrieveMemberById(discordId)
                    .queue(member -> {
                        if (member.getGuild().getSelfMember().canInteract(member)) {
                            member.modifyNickname(null).queue();
                        } else {
                            plugin.logger.warn("Error trying to change name: Player has higher role: "+discordId);
                        }
                    }, error -> {
                        plugin.logger.error("Member couldnt be found: " + error.getMessage());
                    });
        }

        if (!guildId.equals("123456789123456789") && giveRole) {
            String role = plugin.getConfig("link.give_role.role_id", "123456789123456789");
            plugin.getJDA().getGuildById(guildId).retrieveMemberById(discordId)
                    .queue(member -> {
                        if (member.getGuild().getSelfMember().canInteract(member)) {
                            member.getGuild().removeRoleFromMember(member, member.getGuild().getRoleById(role)).queue();
                        } else {
                            plugin.logger.warn("Error trying to change role: Player has higher role: "+discordId);
                        }
                    }, error -> {
                        plugin.logger.error("Member couldnt be found: " + error.getMessage());
                    });
        }
    }
}
