package org.rexi.discordBridgeVelocity.discord.listeners;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DiscordRoleRewardsListener extends ListenerAdapter {

    private final DiscordBridgeVelocity plugin;

    public DiscordRoleRewardsListener(DiscordBridgeVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        if (!plugin.getConfig("link.discord_ranks_rewards.enabled", false)) return;

        String discordId = event.getUser().getId();
        Optional<String> playerOpt = plugin.getDatabase().getMinecraftName(discordId);
        if (playerOpt.isEmpty()) return;
        String player = playerOpt.get();

        for (Role role : event.getRoles()) {
            handleCommands(role.getId(), "received_commands", player);
        }
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        if (!plugin.getConfig("link.discord_ranks_rewards.enabled", false)) return;

        String discordId = event.getUser().getId();
        Optional<String> playerOpt = plugin.getDatabase().getMinecraftName(discordId);
        if (playerOpt.isEmpty()) return;

        String player = playerOpt.get();

        for (Role role : event.getRoles()) {
            handleCommands(role.getId(), "removed_commands", player);
        }
    }

    private void handleCommands(String roleId, String path, String player) {
        Map<String, Map<String, List<String>>> rewards = plugin.getDiscordRankRewards();
        if (!rewards.containsKey(roleId)) return;

        List<String> commands = rewards.get(roleId).get(path);
        if (commands == null) return;

        for (String cmd : commands) {
            executeCommand(cmd, player);
        }
    }

    private void executeCommand(String command, String player) {
        String parsed = command.replace("{player}", player);
        plugin.server.getCommandManager().executeAsync(
                plugin.server.getConsoleCommandSource(),
                parsed
        );
    }
}
