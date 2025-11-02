package org.rexi.discordBridgeVelocity.discord.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.util.List;
import java.util.Optional;

public class GetPlayerListener extends ListenerAdapter {

    private final DiscordBridgeVelocity plugin;

    public GetPlayerListener(DiscordBridgeVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("getplayer")) return;

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
            event.reply(plugin.getConfig("discord_messages.no_permission", "🚫 You don't have permission to use that command.")).setEphemeral(true).queue();
            return;
        }

        // Obtener el argumento <nombre de Minecraft>
        String playerName = event.getOption("name") != null ? event.getOption("name").getAsString() : null;
        if (playerName == null || playerName.isEmpty()) {
            event.reply(plugin.getConfig("discord_messages.getplayer_usage", "Usage: `/getplayer <MinecraftName>`")).setEphemeral(true).queue();
            return;
        }

        // Buscar UUID a partir del nombre
        Optional<String> uuidOpt = plugin.getDatabase().getMinecraftUUID(playerName);
        if (uuidOpt.isEmpty()) {
            event.reply(plugin.getConfig("discord_messages.getplayer_no_linked", "❌ Minecraft player `{player}` is not linked.")
                            .replace("{player}", playerName))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String uuid = uuidOpt.get();
        Optional<String> discordIdOpt = plugin.getDatabase().getDiscordId(uuid);

        if (discordIdOpt.isPresent()) {
            String discordId = discordIdOpt.get();

            plugin.getJDA().retrieveUserById(discordId).queue(user -> {
                List<String> descriptionfallback = List.of(
                        "**Player Information:**",
                        "",
                        "In-Game Username: `{username}`",
                        "In-Game UUID: `{uuid}`",
                        "Linked Discord: {discord_tag}",
                        "Discord ID: `{discord_id}`",
                        "Link Date: {link_date}",
                        "Recovery Code: `{recovery_code}`"
                );

                Optional<Long> linkDateOpt = plugin.getDatabase().getLinkDate(discordId);
                String linkDateStr = linkDateOpt
                        .map(ts -> "<t:" + (ts / 1000L) + ":F>")
                        .orElse("Unknown");

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle(plugin.getConfig("discord_messages.getplayer.title", "🎮 Player Information"))
                        .setThumbnail(user.getEffectiveAvatarUrl())
                        .setColor(Integer.parseInt(plugin.getConfig("discord_messages.getplayer.color", "D600CB"), 16))
                        .setDescription(String.join("\n", plugin.getConfig("discord_messages.getplayer.message", descriptionfallback))
                                .replace("{username}", playerName)
                                .replace("{uuid}", uuid)
                                .replace("{discord_tag}", "<@"+discordId+">")
                                .replace("{discord_id}", discordId)
                                .replace("{link_date}", linkDateStr)
                                .replace("{recovery_code}", plugin.getDatabase().getRecoveryCode(uuid).orElse("Unknown")));

                event.replyEmbeds(embed.build()).setEphemeral(true).queue();
            }, failure -> {
                event.reply(plugin.getConfig("discord_messages.getplayer_no_linked", "❌ Minecraft player `{player}` is not linked.")
                                .replace("{player}", playerName))
                        .setEphemeral(true)
                        .queue();
            });
        } else {
            event.reply(plugin.getConfig("discord_messages.getplayer_no_linked", "❌ Minecraft player `{player}` is not linked.")
                            .replace("{player}", playerName))
                    .setEphemeral(true)
                    .queue();
        }
    }
}
