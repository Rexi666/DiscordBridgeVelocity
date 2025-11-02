package org.rexi.discordBridgeVelocity.discord.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.util.List;
import java.util.Optional;

public class UserInfoListener extends ListenerAdapter {

    private final DiscordBridgeVelocity plugin;

    public UserInfoListener(DiscordBridgeVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("userinfo")) return;

        Member executor = event.getMember();
        if (executor == null) {
            event.reply(plugin.getConfig("discord_messages.only_server", "❌ This command can only be used on a server.")).setEphemeral(true).queue();
            return;
        }

        // Comprobamos si tiene permisos de admin o roles configurados
        List<String> allowedRoles = plugin.getConfig("admin_commands.allowed_roles", List.of());
        boolean hasPermission = executor.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)
                || executor.getRoles().stream().anyMatch(role -> allowedRoles.contains(role.getId()));

        if (!hasPermission) {
            event.reply(plugin.getConfig("discord_messages.no_permission", "🚫 You dont have permission to use that command.")).setEphemeral(true).queue();
            return;
        }

        // Obtener el argumento <id/@>
        String arg = event.getOption("user") != null ? event.getOption("user").getAsString() : null;
        if (arg == null || arg.isEmpty()) {
            event.reply(plugin.getConfig("discord_messages.userinfo_usage", "Usage: `/userinfo <id or mention>`")).setEphemeral(true).queue();
            return;
        }

        String userId = arg.replaceAll("[^0-9]", ""); // quitar caracteres no numéricos
        plugin.getJDA().retrieveUserById(userId).queue(user -> {
            if (user == null) {
                event.reply(plugin.getConfig("discord_messages.no_user_found", "❌ No user could be found with id: `{userID}`.")
                                .replace("{userID}", userId))
                        .setEphemeral(true)
                        .queue();
                return;
            }

            Optional<String> minecraftName = plugin.getDatabase().getMinecraftName(userId);

            if (minecraftName.isPresent()) {
                List<String> descriptionfallback = List.of(
                        "**User Information:**",
                        "",
                        "Discord Tag: {discord_tag}",
                        "Discord ID: `{discord_id}`",
                        "In-Game Username: `{username}`",
                        "In-Game UUID: `{uuid}`",
                        "Link Date: `{link_date}`",
                        "Recovery Code: `{recovery_code}`"
                );

                String mcuuid = plugin.getDatabase().getMinecraftUUID(userId).orElse("Unknown");

                Optional<Long> linkDateOpt = plugin.getDatabase().getLinkDate(userId);
                String linkDateStr = linkDateOpt
                        .map(ts -> "<t:" + (linkDateOpt.get() / 1000L) + ":F>")
                        .orElse("Unknown");

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle(plugin.getConfig("discord_messages.userinfo.title", "👤 User Information"))
                        .setThumbnail(user.getEffectiveAvatarUrl())
                        .setColor(Integer.parseInt(plugin.getConfig("discord_messages.userinfo.color", "D600CB"), 16))
                        .setDescription(String.join("\n", plugin.getConfig("discord_messages.userinfo.message", descriptionfallback))
                                .replace("{discord_tag}", "<@"+userId+">")
                                .replace("{discord_id}", userId)
                                .replace("{username}", minecraftName.get())
                                .replace("{uuid}", mcuuid)
                                .replace("{link_date}", linkDateStr)
                                .replace("{recovery_code}", plugin.getDatabase().getRecoveryCode(mcuuid).orElse("Unknown")));

                event.replyEmbeds(embed.build()).setEphemeral(true).queue();
            } else {
                List<String> descriptionfallback = List.of(
                        "**User Information:**",
                        "",
                        "Discord Tag: {discord_tag}",
                        "Discord ID: `{discord_id}`",
                        "",
                        "⚠️ Account Not Linked"
                );

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle(plugin.getConfig("discord_messages.userinfo_no_linked.title", "👤 User Information"))
                        .setThumbnail(user.getEffectiveAvatarUrl())
                        .setColor(Integer.parseInt(plugin.getConfig("discord_messages.userinfo_no_linked.color", "FA0000"), 16))
                        .setDescription(String.join("\n", plugin.getConfig("discord_messages.userinfo_no_linked.message", descriptionfallback))
                                .replace("{discord_tag}", "<@"+userId+">")
                                .replace("{discord_id}", userId));

                event.replyEmbeds(embed.build()).setEphemeral(true).queue();
            }
        }, failure -> {
            event.reply(plugin.getConfig("discord_messages.no_user_found", "❌ No user could be found with id: `{userID}`.")
                            .replace("{userID}", userId))
                    .setEphemeral(true)
                    .queue();
        });
    }
}
