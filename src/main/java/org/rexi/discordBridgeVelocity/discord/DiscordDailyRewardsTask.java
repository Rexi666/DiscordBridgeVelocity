package org.rexi.discordBridgeVelocity.discord;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DiscordDailyRewardsTask implements Runnable {

    private final DiscordBridgeVelocity plugin;

    public DiscordDailyRewardsTask(DiscordBridgeVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfig("link.discord_ranks_rewards.enabled", false)) return;

        Map<String, Map<String, List<String>>> rewards = plugin.getDiscordRankRewards();

        for (Map.Entry<String, Map<String, List<String>>> entry : rewards.entrySet()) {
            String roleId = entry.getKey();
            List<String> daily = entry.getValue().get("daily_rewards");
            if (daily == null) continue;

            Role role = plugin.getJDA().getRoleById(roleId);
            if (role == null) continue;

            for (Member member : role.getGuild().getMembersWithRoles(role)) {
                Optional<String> playerOpt = plugin.getDatabase().getMinecraftName(member.getId());
                if (playerOpt.isEmpty()) continue;

                String player = playerOpt.get();
                for (String cmd : daily) {
                    String parsed = cmd.replace("{player}", player);
                    plugin.server.getCommandManager().executeAsync(
                            plugin.server.getConsoleCommandSource(),
                            parsed
                    );
                }
            }
        }
    }
}
