package com.raceplayback.raceplaybackserver.commands;

import com.raceplayback.raceplaybackserver.RacePlaybackServer;
import com.raceplayback.raceplaybackserver.data.SessionType;
import com.raceplayback.raceplaybackserver.data.TrackName;
import com.raceplayback.raceplaybackserver.playback.DebugPlaybackController;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.arguments.number.ArgumentInteger;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

public class SessionDebugCommand extends Command {
    private final RacePlaybackServer server = RacePlaybackServer.getInstance();
    private static DebugPlaybackController activeController = null;

    public SessionDebugCommand(Instance instance) {
        super("sessiondebug");

        ArgumentWord trackArg = ArgumentType.Word("track");
        ArgumentInteger yearArg = ArgumentType.Integer("year");
        ArgumentWord driverArg = ArgumentType.Word("driver");

        trackArg.setSuggestionCallback((sender, context, suggestion) -> {
            for (TrackName track : TrackName.values()) {
                suggestion.addEntry(new SuggestionEntry(track.name().toLowerCase()));
            }
        });

        yearArg.setCallback((sender, exception) -> {
            sender.sendMessage("§cYear must be between 2018 and 2024!");
        });

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use this command!");
                return;
            }

            String trackName = context.get(trackArg);
            int year = context.get(yearArg);
            String driver = context.get(driverArg).toUpperCase();

            if (year < 2018 || year > 2024) {
                player.sendMessage("§cYear must be between 2018 and 2024!");
                return;
            }

            TrackName track;
            try {
                track = TrackName.valueOf(trackName.toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage("§cInvalid track: " + trackName);
                return;
            }

            if (activeController != null) {
                activeController.stop();
            }

            player.sendMessage("§aStarting debug session for §b" + driver + " §aat §e" + track.name() + " " + year);
            player.sendMessage("§7Use /debugnext to step through each telemetry point");

            try {
                double rotationOffset = track == TrackName.SILVERSTONE ? -137.0 : 0.0;

                activeController = new DebugPlaybackController(
                    year,
                    track,
                    SessionType.R,
                    driver,
                    player.getPosition(),
                    rotationOffset,
                    instance
                );

                activeController.initialize(instance);

            } catch (Exception e) {
                player.sendMessage("§cError starting debug session: " + e.getMessage());
                server.getLogger().error("Debug session error", e);
            }

        }, trackArg, yearArg, driverArg);

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage("§cUsage: /sessiondebug <track> <year> <driver>");
            sender.sendMessage("§7Example: /sessiondebug silverstone 2024 VER");
        });
    }

    public static DebugPlaybackController getActiveController() {
        return activeController;
    }
}
