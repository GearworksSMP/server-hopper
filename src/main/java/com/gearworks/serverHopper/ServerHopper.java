package com.gearworks.serverHopper;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Plugin(id = "server-hopper", name = "serverHopper", version = BuildConstants.VERSION, url = "https://www.uberswe.com", authors = {"uberswe"})
public class ServerHopper {

    private final ProxyServer server;
    private final Set<String> restrictedServers;
    private final String lobbyName = "lobby"; // The name of the lobby server

    @Inject
    public ServerHopper(ProxyServer server) {
        this.server = server;
        // Define restricted servers (case-insensitive comparison used)
        restrictedServers = new HashSet<>();
        restrictedServers.add("mining");
        restrictedServers.add("northeast");
        restrictedServers.add("southeast");
        restrictedServers.add("southwest");
        restrictedServers.add("northwest");
        restrictedServers.add("staging");
    }

    @Subscribe(order = PostOrder.LATE)
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Unregister default /server command if it exists and re-register with our wrapper
        // Since Velocity doesn't let us directly unregister built-ins easily, we can register a command that overrides it.
        server.getCommandManager().register(server.getCommandManager().metaBuilder("server").build(), new ServerCommandWrapper());
    }

    private class ServerCommandWrapper implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            if (!(invocation.source() instanceof Player)) {
                invocation.source().sendMessage(Component.text("This command can only be used by players."));
                return;
            }

            Player player = (Player) invocation.source();
            String[] args = invocation.arguments();

            if (args.length < 1) {
                player.sendMessage(Component.text("Usage: /server <serverName>"));
                return;
            }

            String targetServerName = args[0].toLowerCase(Locale.ROOT);
            boolean targetIsRestricted = restrictedServers.contains(targetServerName);

            // Get player's current server (if any)
            String currentServerName = player.getCurrentServer()
                    .map(cs -> cs.getServerInfo().getName().toLowerCase(Locale.ROOT))
                    .orElse("");

            boolean currentIsRestricted = restrictedServers.contains(currentServerName);

            // If player is on a restricted server and trying to go to another restricted server (not lobby)
            if (currentIsRestricted && targetIsRestricted && !targetServerName.equalsIgnoreCase(lobbyName)) {
                player.sendMessage(Component.text("You need to jump to lobby first to let your inventory have time to sync."));
                return;
            }

            // Attempt to connect to the specified server
            server.getServer(targetServerName).ifPresentOrElse(
                    srv -> player.createConnectionRequest(srv).connect().thenAccept(result -> {
                        if (!result.isSuccessful()) {
                            player.sendMessage(Component.text("Could not connect to " + targetServerName));
                        }
                    }),
                    () -> player.sendMessage(Component.text("That server does not exist."))
            );
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            // Everyone can attempt to use the command
            return true;
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            String[] args = invocation.arguments();
            // If no arguments yet, suggest all server names
            if (args.length == 0) {
                return server.getAllServers().stream()
                        .map(si -> si.getServerInfo().getName())
                        .collect(Collectors.toList());
            }

            // If the user started typing something, filter the server list
            String partial = args[0].toLowerCase(Locale.ROOT);
            return server.getAllServers().stream()
                    .map(si -> si.getServerInfo().getName())
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .collect(Collectors.toList());
        }
    }
}
