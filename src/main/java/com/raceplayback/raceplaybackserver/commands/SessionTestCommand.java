package com.raceplayback.raceplaybackserver.commands;

import com.raceplayback.raceplaybackserver.RacePlaybackServer;
import com.raceplayback.raceplaybackserver.data.SessionType;
import com.raceplayback.raceplaybackserver.data.TrackName;
import com.raceplayback.raceplaybackserver.playback.PlaybackController;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.arguments.number.ArgumentInteger;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

public class SessionTestCommand extends Command {
    private final RacePlaybackServer server = RacePlaybackServer.getInstance();
    
    public SessionTestCommand(Instance instance) {
        super("sessiontest");
        
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
            
            player.sendMessage("§aStarting replay for §b" + driver + " §aat §e" + track.name() + " " + year);
            
            try {
                double rotationOffset = track == TrackName.SILVERSTONE ? -137.0 : 0.0;

                PlaybackController controller = new PlaybackController(
                    year,
                    track,
                    SessionType.R,
                    driver,
                    player.getPosition(),
                    rotationOffset,
                    instance
                );
                
                controller.initialize(instance);
                controller.startWithCountdown(instance);
                
            } catch (Exception e) {
                player.sendMessage("§cError starting replay: " + e.getMessage());
                server.getLogger().error("Replay error", e);
            }
            
        }, trackArg, yearArg, driverArg);
        
        setDefaultExecutor((sender, context) -> {
            sender.sendMessage("§cUsage: /sessiontest <track> <year> <driver>");
            sender.sendMessage("§7Example: /sessiontest silverstone 2024 VER");
        });
    }
}