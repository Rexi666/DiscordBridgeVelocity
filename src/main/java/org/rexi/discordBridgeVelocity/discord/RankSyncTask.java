package org.rexi.discordBridgeVelocity.discord;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.dv8tion.jda.api.entities.Role;
import net.luckperms.api.LuckPerms;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class RankSyncTask {

    private final DiscordBridgeVelocity plugin;
    private final ProxyServer server;
    private final LuckPerms luckPerms;
    private ScheduledTask task;

    public RankSyncTask(DiscordBridgeVelocity plugin, ProxyServer server, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.server = server;
        this.luckPerms = luckPerms;
    }

    public void start() {
        cancel();
        if (!plugin.getConfig("link.luckperms.enabled", false) || !plugin.getConfig("link.luckperms.sync_interval.enabled", true)) return;
        long intervalHours = plugin.getConfig("link.rank_sync_interval_hours", 6L);

        plugin.logger.info("⏱️ Rank sync every " + intervalHours + " hours...");

        this.task = server.getScheduler()
                .buildTask(plugin, () -> runAsync(null, null))
                .repeat(intervalHours, TimeUnit.HOURS)
                .schedule();
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void runAsync(BiConsumer<Integer, Integer> progressCallback, Runnable onFinish) {
        server.getScheduler().buildTask(plugin, () -> {
            plugin.logger.info("🔄 Syncing ranks...");

            String guildId = plugin.getConfig("link.guild-id", "123456789123456789");
            var guild = plugin.getJDA().getGuildById(guildId);
            if (guild == null) {
                plugin.logger.warn("⚠️ No Discord server found as: " + guildId);
                return;
            }

            var allLinks = plugin.getDatabase().getAllLinkedPlayers();
            int total = allLinks.size();
            AtomicInteger done = new AtomicInteger(0);

            allLinks.forEach(link -> {
                String minecraftUuid = link.get("uuid");
                String discordId = link.get("discord_id");

                luckPerms.getUserManager().loadUser(UUID.fromString(minecraftUuid)).thenAccept(user -> {
                    String group = user.getPrimaryGroup();
                    String roleId = plugin.getLinkedRanks().entrySet().stream()
                            .filter(entry -> entry.getValue().equalsIgnoreCase(group))
                            .map(java.util.Map.Entry::getKey)
                            .findFirst()
                            .orElse(null);

                    if (roleId != null) {
                        var role = guild.getRoleById(roleId);
                        if (role != null) {
                            guild.retrieveMemberById(discordId).queue(member -> {
                                if (member.getRoles().contains(role)) {
                                    return;
                                }

                                plugin.getLinkedRanks().keySet().forEach(oldRoleId -> {
                                    if (oldRoleId.equals(role.getId())) return;

                                    Role oldRole = guild.getRoleById(oldRoleId);
                                    if (oldRole != null && member.getRoles().contains(oldRole)) {
                                        guild.removeRoleFromMember(member, oldRole).queue();
                                    }
                                });
                                guild.addRoleToMember(member, role).queue();
                            });
                        }
                    }

                    int finished = done.incrementAndGet();
                    if (progressCallback != null) progressCallback.accept(finished, total);
                    if (finished >= total && onFinish != null) {
                        plugin.logger.info("🔄 Ranks Synced...");
                        onFinish.run();
                    }
                });
            });
        }).schedule(); // async
    }
}
