package com.raceplayback.raceplaybackserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.raceplayback.raceplaybackserver.commands.SessionTestCommand;
import com.raceplayback.raceplaybackserver.commands.SuggestionCommand;
import com.raceplayback.raceplaybackserver.commands.SessionDebugCommand;
import com.raceplayback.raceplaybackserver.commands.DebugNextCommand;
import com.raceplayback.raceplaybackserver.commands.FeedbackCommand;
import com.raceplayback.raceplaybackserver.commands.ReportBugCommand;
import com.raceplayback.raceplaybackserver.commands.ScanTrackCommand;
import com.raceplayback.raceplaybackserver.commands.VisualizeCenterlineCommand;
import com.raceplayback.raceplaybackserver.data.DataModelType;
import com.raceplayback.raceplaybackserver.data.Session;
import com.raceplayback.raceplaybackserver.data.SessionType;
import com.raceplayback.raceplaybackserver.data.TrackName;
import com.raceplayback.raceplaybackserver.network.F1ApiClient;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.server.ServerListPingEvent;
import net.minestom.server.instance.*;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.ping.Status;
import net.minestom.server.ping.Status.PlayerInfo;
import net.minestom.server.ping.Status.VersionInfo;
import net.minestom.server.instance.anvil.AnvilLoader;
import net.minestom.server.coordinate.Pos;

import org.everbuild.blocksandstuff.blocks.BlockPlacementRuleRegistrations;

import java.util.Base64;
import java.util.List;

import org.everbuild.blocksandstuff.blocks.BlockBehaviorRuleRegistrations;
import org.everbuild.blocksandstuff.blocks.PlacedHandlerRegistration;

public class RacePlaybackServer {

    private static RacePlaybackServer instance;
    private static final Logger logger = LoggerFactory.getLogger(RacePlaybackServer.class);
    private static List<Session> uniqueSessions = List.of();
    private static List<TrackName> uniqueTracks = List.of();

    public RacePlaybackServer() {
        instance = this;
    }

    public static RacePlaybackServer getInstance() {
        return instance;
    }

    public Logger getLogger() {
        return logger;
    }

    public static void main(String[] args) {
        new RacePlaybackServer();
        MinecraftServer minecraftServer = MinecraftServer.init(new Auth.Offline());

        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instanceContainer = instanceManager.createInstanceContainer();

        instanceContainer.setChunkLoader(new AnvilLoader("world"));
        instanceContainer.setChunkSupplier(LightingChunk::new);

        BlockPlacementRuleRegistrations.registerDefault();
        BlockBehaviorRuleRegistrations.registerDefault();
        PlacedHandlerRegistration.registerDefault();

        CommandManager commandManager = MinecraftServer.getCommandManager();
        commandManager.register(new SessionTestCommand(instanceContainer));
        commandManager.register(new SessionDebugCommand(instanceContainer));
        commandManager.register(new DebugNextCommand());
        commandManager.register(new ScanTrackCommand(instanceContainer));
        commandManager.register(new VisualizeCenterlineCommand(instanceContainer));
        commandManager.register(new ReportBugCommand());
        commandManager.register(new SuggestionCommand());
        commandManager.register(new FeedbackCommand());

        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            final Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            player.setRespawnPoint(new Pos(-255, 64, -47));
            player.setGameMode(GameMode.CREATIVE);
        });

        globalEventHandler.addListener(PlayerChatEvent.class, event -> {
            if (event.getRawMessage().equals("Hannah")) {
                event.getPlayer().getInventory().addItemStack(ItemStack.of(Material.COD).withCustomName(Component.text("The fish from Hitman", NamedTextColor.GREEN)));
            }
        });

        globalEventHandler.addListener(ServerListPingEvent.class, event -> {
            int protocolVersion = event.getConnection().getProtocolVersion() < 110 ? 0 : event.getConnection().getProtocolVersion();
            VersionInfo versionInfo = new VersionInfo("1.9-1.21.11", protocolVersion);
            PlayerInfo playerInfo = new PlayerInfo(MinecraftServer.getConnectionManager().getOnlinePlayerCount(), MinecraftServer.getConnectionManager().getOnlinePlayerCount() + 1);
            byte[] favicon = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAAXNSR0IArs4c6QAABDxJREFUeJzlWztrVEEYPXP3/o9YaGofEEQiJJVK1JB6MVhKUIKNWKiIjQgxjWgTES20NETRhASSKNFCIsFYRAQFf0ksdu/dmbnfzHwz97V39zS7O7M7M+fO+R7zWBG9WjqMhEBLAOqrQGQoS+uQLeu80mVJOxEsdURZr06gBc44DXXEeOPKyQughUDyab8e45TrkK2LTT9UGyDqwCBqIgPqoWtEqQkhyDvHaSCfjC1mz5pG3mvWiFkIUh5sRP3NLjUBL/KSHXqTV0gwJFs4eXVMQjYBNhkwiRrIsyWr18FHqdqYQJMHNCfI96S+Toh2fkWaXQh5ALICwsJIueSzDz2vz5HJSwpgkiE9KdM2CTss3ey08erkuwpgkmGHPb4TMpGhyQdED9jJdxXAnMng7K3nwLxifoFmZyJPKMAwk8Fhj3ZCbLPzyja1OgZ5TQFcJ+RHhs768vgcBvmuYl3kJQU45Iww8nTWV7TPIdTIJN9VQB4n5CADz1krxOx4xCUFAAfTba8fhWLu2wfjTD48fq70/l//28mUxZHnE8sDW6SoC7GvZPLA5kPqQlRl5zYHWhcqVcDiqQvp+zs/1hXnVwXaI+Pp+8QfVKoAGbrnrwtRXZ3rYa8uOKPA5a23/ASmm7w8HZtydqzHdxOe//mUifkuyFJ3IbZ13hmoRz4Ovpz1kMjpvwylOBXAJ8+fIUBft7v7L8tMGApg5uOAU84y9BWjrf8rR86mnxPvncicyu58QDrBme1laaDmfJwi760A2PP3SAiFZHtkXLHx5LOP3Svt6wOe2V5WSJS1GPHNBDkzHfIQMiagy/jN+LR3oxzo630TfEn5fj+jgGRm2jsrXg35QneudSHjA3qyFLj69X15HUM2rToTIa1AnpmXZy4V2tmT31tZB1rhYojyI4QCyiHfadu8UVoXYt3+yrRJ7mlNlUiXw7Nf3inJTRmgdnrrJA9IqbAe3xPM735Myx6fPM9qdPHXpnkTFX7k82Z6LqR5gJ7cJAjZtsp7WlMlNAVkFx4hmxZ5T2uqRJTM9tLpi4pkb35f7XxBInN3fx339zfw4OeGtdEbo5OYOzaBa0cnMivHfiIPaJngs7EpxV5v7a0pM8lJXXWkMR/9Rx4gNkW5pzVc1J3puZDmAfO7q+Rpzb399Qz5lhBYONhUEpnro5NkB7PEWr6fkCpAJvPoRC/cqet28zK4qcjkATJ5ABnypvjeVKR5wIIhyeGeEL/4+zmtk2UvgzqYqBvuTdGAi1FNQsCmqD1SNA3i9t7aoZWg82Z3c8kDxDU5haiBPBUNmgr7XWHw7gY1GdEwkwdsd4VhD3uDQB4w3RU23s4cLPKA5ARV+Q8HeYC6K2y5nTlo5AHqpiiGhzyg/2MEdMzvx42MohDrnn+YyAPdPGBYyQOyAvrotKZKxP14WlMl4pDTmkFC3K8HFlUhGmbyAPAfirV6198YpbUAAAAASUVORK5CYII=");
            String motdMini =
                "<gradient:#00a19b:#a4e0c3>RacePlayback</gradient>\n" +
                "<#F7DA47>%d players online</#F7DA47> <white>|</white> " +
                "<#80D35D>%d live races</#80D35D> <white>|</white> " +
                "<#5BB1EF>%d active tracks</#5BB1EF>";
            Component motd = MiniMessage.miniMessage().deserialize(
                String.format(
                    motdMini,
                    MinecraftServer.getConnectionManager().getOnlinePlayerCount(),
                    uniqueSessions.size(),
                    uniqueTracks.size()
                )
            );
            Status status = new Status(motd, favicon, versionInfo, playerInfo, true);
            event.setStatus(status);
        });

        minecraftServer.start("0.0.0.0", 25565);

//        testApiClients();
    }

    private static void testApiClients() {
        F1ApiClient client = new F1ApiClient(
            "https://raceplayback.com/api/v1/sessions", 
            2024, 
            TrackName.SILVERSTONE, 
            SessionType.R, 
            "info", 
            DataModelType.SESSION
        );
        if (client != null) {
            logger.info("F1 API Client is working!");
        }
    }
}