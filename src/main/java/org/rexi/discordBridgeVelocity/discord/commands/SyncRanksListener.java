package org.rexi.discordBridgeVelocity.discord.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.awt.*;
import java.util.List;
import java.util.Optional;

public class SyncRanksListener extends ListenerAdapter {

    private final DiscordBridgeVelocity plugin;

    public SyncRanksListener(DiscordBridgeVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("syncranks")) return;

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

        event.deferReply(true).queue(hook -> {
            hook.editOriginalEmbeds(
                    new EmbedBuilder()
                            .setColor(Integer.parseInt(plugin.getConfig("discord_messages.rank_sync.starting.color", "DBB000"), 16))
                            .setTitle(plugin.getConfig("discord_messages.rank_sync.starting.title", "🔄 Syncing ranks..."))
                            .setDescription(plugin.getConfig("discord_messages.rank_sync.starting.message", "Starting synchronization..."))
                            .build()
            ).queue(message -> {
                plugin.getRankSyncTask().runAsync((done, total) -> {
                    hook.editOriginalEmbeds(
                            new EmbedBuilder()
                                    .setColor(Integer.parseInt(plugin.getConfig("discord_messages.rank_sync.process.color", "DBB000"), 16))
                                    .setTitle(plugin.getConfig("discord_messages.rank_sync.process.title", "🔄 Syncing ranks..."))
                                    .setDescription(plugin.getConfig("discord_messages.rank_sync.process.message", "Progress: **{done}/{total}**")
                                            .replace("{done}", String.valueOf(done)).replace("{total}", String.valueOf(total)))
                                    .build()
                    ).queue();
                }, () -> {
                    hook.editOriginalEmbeds(
                            new EmbedBuilder()
                                    .setColor(Integer.parseInt(plugin.getConfig("discord_messages.rank_sync.done.color", "DBB000"), 16))
                                    .setTitle(plugin.getConfig("discord_messages.rank_sync.done.title", "✅ Rank synchronization complete!"))
                                    .setDescription(plugin.getConfig("discord_messages.rank_sync.done.message", "All linked users have been updated successfully."))
                                    .build()
                    ).queue();
                });
            });
        });
    }
}
