package org.rexi.discordBridgeVelocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;
import org.rexi.discordBridgeVelocity.utils.LinkManager;

import java.util.List;
import java.util.Optional;

public class LinkCommand implements SimpleCommand {

    private final DiscordBridgeVelocity plugin;
    private final LuckPerms luckPerms;

    public LinkCommand(DiscordBridgeVelocity plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!(source instanceof Player)) {
            source.sendMessage(Component.text("This command can only be executed by players.").color(NamedTextColor.RED));
            return;
        }

        Player player = (Player) source;

        if (plugin.getDatabase().getDiscordId(player.getUniqueId().toString()).isPresent()) {
            player.sendMessage(plugin.legacy(plugin.getConfig("messages.already-linked","&c⚠\uFE0F Your account is already linked with a Discord account.")));
            return;
        }

        if (args.length == 0) {
            source.sendMessage(plugin.legacy(plugin.getConfig("messages.link-command","&cUsage: /link <code>. You can get the code in Discord by using /link on any channel.")));
            return;
        }

        String code = args[0].toUpperCase();

        String discordId = LinkManager.getDiscordId(code);
        if (discordId == null) {
            player.sendMessage(plugin.legacy(plugin.getConfig("messages.invalid-expired","&cInvalid or expired link code.")));
            return;
        }
        plugin.getDatabase().saveLink(player.getUniqueId().toString(), player.getUsername(), discordId);
        LinkManager.removeCode(code);
        player.sendMessage(plugin.legacy(plugin.getConfig("messages.link-success","&a✅ In-Game account successfully linked with Discord!")));

        List<String> descriptionfallback = List.of(
                "**You have successfully linked your account!**",
                "",
                "Your in-game username: `{username}`",
                "Recovery Code: `{recovery_code}`",
                "",
                "**Save this code to make changes to the link**",
                "**Do not share it with ANYONE**"
        );

        String title = plugin.getConfig("discord_messages.link-successfully.title", "🔗 Link Your Account");
        List<String> description = plugin.getConfig("discord_messages.link-successfully.message", descriptionfallback);

        Optional<String> recovery_code_raw = plugin.getDatabase().getRecoveryCode(player.getUniqueId().toString());
        String recovery_code = recovery_code_raw.orElse("N/A");

        MessageEmbed embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(String.join("\n", description).replace("{username}", player.getUsername()).replace("{recovery_code}", recovery_code))
                .setColor(Integer.parseInt(plugin.getConfig("discord_messages.link-successfully.color", "08C702"), 16))
                .build();

        plugin.getJDA().retrieveUserById(discordId)
                .flatMap(user -> user.openPrivateChannel())
                .flatMap(channel -> channel.sendMessageEmbeds(embed))
                .queue();

        String guildId = plugin.getConfig("link.guild-id", "123456789123456789");
        boolean changeName = plugin.getConfig("link.change_discord_name", false);
        boolean giveRole = plugin.getConfig("link.give_role.enabled", false);
        boolean useLuckperms = plugin.getConfig("link.luckperms.enabled", false);

        if (!guildId.equals("123456789123456789") && (changeName || giveRole || useLuckperms)) {
            plugin.getJDA().getGuildById(guildId).retrieveMemberById(discordId)
                    .queue(member -> {
                        if (member.getGuild().getSelfMember().canInteract(member)) {
                            if (changeName) {
                                member.modifyNickname(player.getUsername()).queue();
                            }
                            if (giveRole) {
                                String role = plugin.getConfig("link.give_role.role_id", "123456789123456789");
                                member.getGuild().addRoleToMember(member, member.getGuild().getRoleById(role)).queue();
                            }
                            if (useLuckperms) {
                                String group = luckPerms.getPlayerAdapter(Player.class).getUser(player).getPrimaryGroup();

                                String role = plugin.getLinkedRanks().get(group);
                                if (role != null) {
                                    Role finalrole = member.getGuild().getRoleById(role);
                                    if (member.getGuild().getSelfMember().canInteract(finalrole)) {
                                        member.getGuild().addRoleToMember(member, finalrole).queue();
                                    } else {
                                        plugin.logger.warn("Error trying to modify user: Bot cannot interact with role: "+role);
                                    }
                                }
                            }
                        } else {
                            plugin.logger.warn("Error trying to modify user: Player has higher role: "+discordId);
                        }
                    }, error -> {
                        plugin.logger.error("Member couldnt be found: " + error.getMessage());
                    });
        }
    }
}
