package org.rexi.discordBridgeVelocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

public class DiscordBridgeCommand implements SimpleCommand {

    private final DiscordBridgeVelocity plugin;

    public DiscordBridgeCommand(DiscordBridgeVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (source instanceof Player) {
            source.sendMessage(Component.text("This command can only be executed from the console.").color(NamedTextColor.RED));
            return;
        }

        if (args.length == 0) {
            source.sendMessage(Component.text("Usage: /discordbridge reload").color(NamedTextColor.GREEN));
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            source.sendMessage(Component.text("Reloading configuration and Discord bot...").color(NamedTextColor.GREEN));

            plugin.shutdownBot();
            plugin.loadConfig();
            plugin.loadLinkedChannels();
            plugin.initializeBot();

            source.sendMessage(Component.text("✅ Discord Bridge reloaded correctly.").color(NamedTextColor.GREEN));
        } else {
            source.sendMessage(Component.text("Unknown command. Use: /discordbridge reload").color(NamedTextColor.RED));
        }
    }
}
