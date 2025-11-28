package com.racereplay.racereplayserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.racereplay.racereplayserver.data.SessionType;
import com.racereplay.racereplayserver.data.TrackName;
import com.racereplay.racereplayserver.network.F1ApiClient;

import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.*;
import net.minestom.server.instance.block.Block;
import net.minestom.server.coordinate.Pos;

public class RaceReplayServer {

    private static RaceReplayServer instance;

    private static final Logger logger = LoggerFactory.getLogger(RaceReplayServer.class);

    public RaceReplayServer() {
        instance = this;
    }

    public static RaceReplayServer getInstance() {
        return instance;
    }

    public Logger getLogger() {
        return logger;
    }

    public static void main(String[] args) {
        new RaceReplayServer();
        MinecraftServer minecraftServer = MinecraftServer.init(new Auth.Online());

        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instanceContainer = instanceManager.createInstanceContainer();

        instanceContainer.setGenerator(unit -> unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK));

        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            final Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            player.setRespawnPoint(new Pos(0, 42, 0));
        });

        testApiClients();

        minecraftServer.start("0.0.0.0", 25565);
    }

    private static void testApiClients() {
        F1ApiClient client = new F1ApiClient("https://racereplay.kmathers.co.uk/api/v1/sessions", 2024, TrackName.SILVERSTONE, SessionType.R, "drivers");
        if (client != null) {
            logger.info("F1 API Client is working!");
        }
    }
}