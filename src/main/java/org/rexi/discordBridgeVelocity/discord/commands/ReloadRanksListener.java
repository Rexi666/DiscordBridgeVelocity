package org.rexi.discordBridgeVelocity.discord.commands;

import com.velocitypowered.api.proxy.Player;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ReloadRanksListener extends ListenerAdapter {

    private final DiscordBridgeVelocity plugin;
    private final LuckPerms luckPerms;

    public ReloadRanksListener(DiscordBridgeVelocity plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("reloadranks")) return;

        event.deferReply(true).queue(hook -> {
            boolean useLuckperms = plugin.getConfig("link.luckperms.enabled", false);
            String guildId = plugin.getConfig("link.guild-id", "123456789123456789");
            if (!useLuckperms || guildId.equals("123456789123456789")) {
                hook.sendMessage(plugin.getConfig("discord_messages.reloadranks_disabled", "❌ The LuckPerms integration is disabled"))
                        .setEphemeral(true).queue();
                return;
            }

            Member executor = event.getMember();
            String uuidString = plugin.getDatabase().getMinecraftUUID(executor.getId()).get();
            if (uuidString.isEmpty()) {
                hook.sendMessage(plugin.getConfig("discord_messages.unlink_no_linked", "⚠️ You don't have any linked Minecraft account.")).setEphemeral(true).queue();
                return;
            }
            UUID uuid = UUID.fromString(uuidString);
            Guild guild = plugin.getJDA().getGuildById(guildId);
            guild.retrieveMemberById(executor.getId()).queue(member -> {
                getGroup(uuid).thenAccept(rank -> {
                    if (rank == null) {
                        hook.sendMessage(plugin.getConfig("discord_messages.reloadranks_error", "❌ Error: Trying to update your rank.")).setEphemeral(true).queue();
                        plugin.logger.warn("Error trying to check user rank");
                    } else {
                        String role = plugin.getLinkedRanks().entrySet().stream()
                                .filter(entry -> entry.getValue().equalsIgnoreCase(rank))
                                .map(entry -> entry.getKey())
                                .findFirst()
                                .orElse(null);
                        if (role != null) {
                            if (member.getRoles().stream().anyMatch(roles -> roles.getId().equals(role))) {
                                hook.sendMessage(plugin.getConfig("discord_messages.reloadranks_current", "❌ You have your current rank already.")).setEphemeral(true).queue();
                            } else {
                                for (String roles : plugin.getAllRanks()) {
                                    if (roles != null) {
                                        guild.removeRoleFromMember(member, guild.getRoleById(roles)).queue();
                                    }
                                }
                                Role finalrole = guild.getRoleById(role);
                                if (guild.getSelfMember().canInteract(finalrole)) {
                                    guild.addRoleToMember(member, finalrole).queue();
                                    hook.sendMessage(plugin.getConfig("discord_messages.reloadranks_success", "✅ Your Discord roles have been updated according to your in-game rank.")).setEphemeral(true).queue();
                                } else {
                                    plugin.logger.warn("Error trying to modify user: Bot cannot interact with role: "+role);
                                    hook.sendMessage(plugin.getConfig("discord_messages.reloadranks_error", "❌ Error: Trying to update your rank.")).setEphemeral(true).queue();
                                }
                            }
                        } else {
                            hook.sendMessage(plugin.getConfig("discord_messages.reloadranks_no_role", "❌ No Discord role is set for your in-game rank.")).setEphemeral(true).queue();
                        }
                    }
                }).exceptionally(ex -> {
                    ex.printStackTrace();
                    plugin.logger.warn("Error trying to check user rank");
                    hook.sendMessage(plugin.getConfig("discord_messages.reloadranks_error", "❌ Error: Trying to update your rank.")).setEphemeral(true).queue();
                    return null;
                });
            }, error -> {
                plugin.logger.error("Member couldnt be found: " + error.getMessage());
                hook.sendMessage(plugin.getConfig("discord_messages.reloadranks_error", "❌ Error: Trying to update your rank.")).setEphemeral(true).queue();
            });
        });
    }

    private CompletableFuture<String> getGroup(UUID uuid) {
        UserManager userManager = luckPerms.getUserManager();
        return userManager.loadUser(uuid)
                .thenApply(User::getPrimaryGroup);
    }
}
