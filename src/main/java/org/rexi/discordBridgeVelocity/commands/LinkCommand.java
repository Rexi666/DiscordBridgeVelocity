package org.rexi.discordBridgeVelocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;
import org.rexi.discordBridgeVelocity.utils.LinkManager;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class LinkCommand implements SimpleCommand {

    private final DiscordBridgeVelocity plugin;

    public LinkCommand(DiscordBridgeVelocity plugin) {
        this.plugin = plugin;
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

        Object rawColor = plugin.getConfig("discord_messages.info-not-linked.color", 214000203);
        int color;

        if (rawColor instanceof Number) {
            color = ((Number) rawColor).intValue();
        } else {
            color = Integer.parseInt(rawColor.toString());
        }

        Optional<String> recovery_code_raw = plugin.getDatabase().getRecoveryCode(player.getUniqueId().toString());
        String recovery_code = recovery_code_raw.orElse("N/A");

        MessageEmbed embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(String.join("\n", description).replace("{username}", player.getUsername()).replace("{recovery_code}", recovery_code))
                .setColor(color)
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
                        member.modifyNickname(player.getUsername())
                                .queue(
                                        error -> plugin.logger.error("Error trying to change nickname")
                                );
                    }, error -> {
                        plugin.logger.error("Member couldnt be found: " + error.getMessage());
                    });
        }

        if (!guildId.equals("123456789123456789") && giveRole) {
            String role = plugin.getConfig("link.give_role.role_id", "123456789123456789");
            plugin.getJDA().getGuildById(guildId).retrieveMemberById(discordId)
                    .queue(member -> {
                        member.getGuild().addRoleToMember(member, member.getGuild().getRoleById(role))
                                .queue(
                                        error -> plugin.logger.error("Error trying to assing role")
                                );
                    }, error -> {
                        plugin.logger.error("Member couldnt be found: " + error.getMessage());
                    });
        }
    }
}
