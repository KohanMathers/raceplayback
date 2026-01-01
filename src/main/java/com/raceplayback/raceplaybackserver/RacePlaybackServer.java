package com.raceplayback.raceplaybackserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.raceplayback.raceplaybackserver.commands.SessionTestCommand;
import com.raceplayback.raceplaybackserver.commands.SuggestionCommand;
import com.raceplayback.raceplaybackserver.commands.SessionDebugCommand;
import com.raceplayback.raceplaybackserver.commands.DebugNextCommand;
import com.raceplayback.raceplaybackserver.data.DataModelType;
import com.raceplayback.raceplaybackserver.data.Session;
import com.raceplayback.raceplaybackserver.data.SessionType;
import com.raceplayback.raceplaybackserver.data.TrackName;
import com.raceplayback.raceplaybackserver.network.F1ApiClient;

import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.*;
import net.minestom.server.instance.block.Block;
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

        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            final Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            player.setRespawnPoint(new Pos(-255, 64, -47));
            player.setGameMode(GameMode.CREATIVE);
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