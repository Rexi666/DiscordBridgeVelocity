package org.rexi.discordBridgeVelocity.discord.commands;

import com.velocitypowered.api.proxy.Player;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

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

        boolean unlinkEnabled = plugin.getConfig("unlink.enabled", true);
        if (!unlinkEnabled) {
            event.reply(plugin.getConfig("discord_messages.unlink_disabled", "🚫 The unlink command is currently disabled, contact and administrator to unlink your account."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String discordId = event.getUser().getId();
        Optional<String> minecraftName = plugin.getDatabase().getMinecraftName(discordId);

        if (minecraftName.isEmpty()) {
            event.reply(plugin.getConfig("discord_messages.unlink_no_linked", "⚠️ You don't have any linked Minecraft account."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(plugin.getConfig("discord_messages.unlink_confirm.title", "⚠️ Confirm Unlink"))
                .setDescription(plugin.getConfig("discord_messages.unlink_confirm.message", "Are you sure you want to unlink your Minecraft account `{username}`?")
                        .replace("{username}", minecraftName.get()))
                .setColor(Integer.parseInt(plugin.getConfig("discord_messages.unlink_confirm.color", "DBB000"), 16));

        event.replyEmbeds(embed.build())
                .addActionRow(
                        net.dv8tion.jda.api.interactions.components.buttons.Button.success("unlink_yes:" + discordId, plugin.getConfig("discord_messages.button_yes", "✅ Yes")),
                        net.dv8tion.jda.api.interactions.components.buttons.Button.danger("unlink_no:" + discordId, plugin.getConfig("discord_messages.button_no", "❌ No"))
                )
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getButton().getId();

        if (buttonId == null || !buttonId.startsWith("unlink_")) {
            return;
        }

        String[] idParts = event.getButton().getId().split(":");
        if (idParts.length != 2) return;

        String action = idParts[0];
        String userId = idParts[1];

        if (!event.getUser().getId().equals(userId)) {
            event.reply("⚠️ This confirmation is not for you.").setEphemeral(true).queue();
            return;
        }

        if (action.equals("unlink_yes")) {
            boolean requireCode = plugin.getConfig("unlink.require_code", true);

            if (requireCode) {
                TextInput codeInput = TextInput.create("security_code", plugin.getConfig("discord_messages.unlink_require_code", "Enter your security code"), TextInputStyle.SHORT)
                        .setPlaceholder("A1B2C3D4")
                        .setRequired(true)
                        .build();

                Modal modal = Modal.create("unlink_modal:" + userId, plugin.getConfig("discord_messages.unlink_confirm_button", "✅ Confirm Unlink with Security Code"))
                        .addActionRow(codeInput)
                        .build();

                event.replyModal(modal).queue();
            } else {
                Optional<String> minecraftName = plugin.getDatabase().getMinecraftName(userId);
                if (minecraftName.isPresent()) {
                    plugin.getDatabase().unlinkUser(userId);
                    UnlinkDiscord(userId);
                    event.reply(plugin.getConfig("discord_messages.unlink_success", "✅ Your Minecraft account `{username}` has been unlinked.")
                                    .replace("{username}", minecraftName.get()))
                            .setEphemeral(true)
                            .queue();
                } else {
                    event.reply(plugin.getConfig("discord_messages.unlink_no_linked", "⚠️ You don't have any linked Minecraft account."))
                            .setEphemeral(true)
                            .queue();
                }
            }
        } else if (action.equals("unlink_no")) {
            event.reply(plugin.getConfig("discord_messages.unlink_cancelled", "❌ Unlink process cancelled."))
                    .setEphemeral(true)
                    .queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith("unlink_modal:")) return;

        String userId = event.getModalId().split(":")[1];
        String enteredCode = event.getValue("security_code").getAsString();

        Optional<String> realCode = plugin.getDatabase().getRecoveryCode(userId);
        if (realCode.isEmpty()) {
            event.reply(plugin.getConfig("discord_messages.unlink_no_linked", "⚠️ You don't have any linked Minecraft account."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (!realCode.get().equalsIgnoreCase(enteredCode.trim())) {
            event.reply(plugin.getConfig("discord_messages.unlink_wrong_code", "❌ Incorrect security code."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Optional<String> minecraftName = plugin.getDatabase().getMinecraftName(userId);
        if (minecraftName.isPresent()) {
            plugin.getDatabase().unlinkUser(userId);
            UnlinkDiscord(userId);
            event.reply(plugin.getConfig("discord_messages.unlink_success", "✅ Your Minecraft account `{username}` has been unlinked.")
                            .replace("{username}", minecraftName.get()))
                    .setEphemeral(true)
                    .queue();
        } else {
            event.reply(plugin.getConfig("discord_messages.unlink_no_linked", "⚠️ You don't have any linked Minecraft account."))
                    .setEphemeral(true)
                    .queue();
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
        boolean useLuckperms = plugin.getConfig("link.luckperms.enabled", false);

        if (!guildId.equals("123456789123456789") && (changeName || giveRole || useLuckperms)) {
            plugin.getJDA().getGuildById(guildId).retrieveMemberById(discordId)
                    .queue(member -> {
                        if (member.getGuild().getSelfMember().canInteract(member)) {
                            if (changeName) {
                                member.modifyNickname(null).queue();
                            }
                            if (giveRole) {
                                String role = plugin.getConfig("link.give_role.role_id", "123456789123456789");
                                member.getGuild().removeRoleFromMember(member, member.getGuild().getRoleById(role)).queue();
                            }
                            if (useLuckperms) {
                                for (String rank : plugin.getAllRanks()) {
                                    if (rank != null) {
                                        member.getGuild().removeRoleFromMember(member, member.getGuild().getRoleById(rank)).queue();
                                    }
                                }
                            }
                        } else {
                            plugin.logger.warn("Error trying to modify user appearance: Player has higher role: "+discordId);
                        }
                    }, error -> {
                        plugin.logger.error("Member couldnt be found: " + error.getMessage());
                    });
        }
    }

}