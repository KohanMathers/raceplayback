package com.raceplayback.raceplaybackserver.commands;

import com.raceplayback.raceplaybackserver.RacePlaybackServer;
import com.raceplayback.raceplaybackserver.data.DataModelType;
import com.raceplayback.raceplaybackserver.data.SessionType;
import com.raceplayback.raceplaybackserver.data.TelemetryPoint;
import com.raceplayback.raceplaybackserver.data.TrackName;
import com.raceplayback.raceplaybackserver.mapping.AutomaticCoordinateMapper;
import com.raceplayback.raceplaybackserver.network.F1ApiClient;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.arguments.number.ArgumentInteger;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.BlockDisplayMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Debug visualization command for automatic track mapping.
 * Usage: /visualizeauto <track> <year> <driver> <lap>
 * Shows 3 colored visualizations:
 * - EMERALD: Detected track centerline
 * - GOLD: Transformed racing line (mapped telemetry)
 * - DIAMOND: Original telemetry (for comparison)
 */
public class VisualizeAutoCommand extends Command {
    private final RacePlaybackServer server = RacePlaybackServer.getInstance();
    private final Instance instance;
    private static List<Entity> visualizationEntities = new ArrayList<>();

    public VisualizeAutoCommand(Instance instance) {
        super("visualizeauto");
        this.instance = instance;

        ArgumentWord trackArg = ArgumentType.Word("track");
        ArgumentInteger yearArg = ArgumentType.Integer("year");
        ArgumentWord driverArg = ArgumentType.Word("driver");
        ArgumentInteger lapArg = ArgumentType.Integer("lap");
        ArgumentWord clearArg = ArgumentType.Word("clear");

        trackArg.setSuggestionCallback((sender, context, suggestion) -> {
            for (TrackName track : TrackName.values()) {
                suggestion.addEntry(new SuggestionEntry(track.name().toLowerCase()));
            }
        });

        // /visualizeauto clear - removes all visualization
        addSyntax((sender, context) -> {
            clearVisualization();
            sender.sendMessage("§a✓ Visualization cleared");
        }, clearArg);

        // /visualizeauto <track> <year> <driver> <lap>
        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use this command!");
                return;
            }

            String trackName = context.get(trackArg);
            int year = context.get(yearArg);
            String driver = context.get(driverArg).toUpperCase();
            int lap = context.get(lapArg);

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

            // Clear previous visualization
            clearVisualization();

            player.sendMessage("§7Auto-detecting track and computing best-fit transformation...");
            player.sendMessage("§7This may take up to 20 seconds on first run...");

            try {
                // Fetch telemetry
                List<TelemetryPoint> telemetry = fetchTelemetry(year, track, SessionType.R, driver, lap);

                if (telemetry == null || telemetry.isEmpty()) {
                    player.sendMessage("§cNo telemetry data found for " + driver + " lap " + lap);
                    return;
                }

                player.sendMessage("§7Fetched " + telemetry.size() + " telemetry points");

                // Create automatic mapper (this does detection + best-fit)
                long startTime = System.currentTimeMillis();
                AutomaticCoordinateMapper mapper = AutomaticCoordinateMapper.create(
                    instance,
                    player.getPosition(),
                    500, // search radius
                    telemetry,
                    track
                );
                long totalTime = System.currentTimeMillis() - startTime;

                // Display results
                player.sendMessage("");
                player.sendMessage("§a✓ Track Detection Complete");
                player.sendMessage("§7  Detected: " + mapper.getDetectedCenterline().size() + " track blocks");
                double trackLength = calculateLength(mapper.getDetectedCenterline());
                player.sendMessage("§7  Centerline: " + String.format("%.1f", trackLength) + " blocks long");

                player.sendMessage("");
                player.sendMessage("§a✓ Best-Fit Transformation");
                player.sendMessage("§7  Scale: " + String.format("%.4f", mapper.getTransformation().scale()));
                player.sendMessage("§7  Rotation: " + String.format("%.2f", mapper.getTransformation().getRotationDegrees()) + "°");
                player.sendMessage("§7  Error: " + String.format("%.2f", mapper.getTransformation().errorMetric()) + " (lower is better)");
                player.sendMessage("§7  Time: " + totalTime + "ms");

                // Visualize!
                player.sendMessage("");
                player.sendMessage("§7Drawing visualization...");

                visualizeDetectedTrack(mapper.getDetectedCenterline(), Block.EMERALD_BLOCK);
                visualizeMappedRacingLine(mapper, Block.GOLD_BLOCK);
                visualizeOriginalTelemetry(telemetry, Block.DIAMOND_BLOCK, player.getPosition());

                player.sendMessage("");
                player.sendMessage("§a✓ Visualization Complete!");
                player.sendMessage("§7  §aEMERALD §7= Detected track centerline");
                player.sendMessage("§7  §6GOLD §7= Mapped racing line (transformed)");
                player.sendMessage("§7  §bDIAMOND §7= Original telemetry (untransformed)");
                player.sendMessage("§7Use §e/visualizeauto clear §7to remove");

            } catch (Exception e) {
                player.sendMessage("§cError: " + e.getMessage());
                server.getLogger().error("Visualization error", e);
            }

        }, trackArg, yearArg, driverArg, lapArg);

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage("§cUsage: /visualizeauto <track> <year> <driver> <lap>");
            sender.sendMessage("§7Example: §e/visualizeauto silverstone 2024 VER 1");
            sender.sendMessage("§7Clear: §e/visualizeauto clear");
        });
    }

    @SuppressWarnings("unchecked")
    private List<TelemetryPoint> fetchTelemetry(int year, TrackName track, SessionType sessionType,
                                                String driver, int lap) {
        String endpoint = String.format("telemetry/%s/%d", driver, lap);

        F1ApiClient client = new F1ApiClient(
            "https://raceplayback.com/api/v1/sessions",
            year,
            track,
            sessionType,
            endpoint,
            DataModelType.TELEMETRY_POINT
        );

        return (List<TelemetryPoint>) client.getData();
    }

    private void visualizeDetectedTrack(List<Pos> centerline, Block blockType) {
        for (Pos pos : centerline) {
            Entity blockDisplay = new Entity(EntityType.BLOCK_DISPLAY);
            BlockDisplayMeta meta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
            meta.setBlockState(blockType);
            meta.setScale(new Vec(0.3, 0.3, 0.3));
            meta.setHasNoGravity(true);

            blockDisplay.setInstance(instance, pos.withY(pos.y() + 1)); // Raise slightly above track
            visualizationEntities.add(blockDisplay);
        }
        server.getLogger().info("Drew {} EMERALD blocks (track centerline)", centerline.size());
    }

    private void visualizeMappedRacingLine(AutomaticCoordinateMapper mapper, Block blockType) {
        List<Pos> mappedPoints = mapper.getAllMappedPoints();
        for (Pos pos : mappedPoints) {
            Entity blockDisplay = new Entity(EntityType.BLOCK_DISPLAY);
            BlockDisplayMeta meta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
            meta.setBlockState(blockType);
            meta.setScale(new Vec(0.25, 0.25, 0.25));
            meta.setHasNoGravity(true);

            blockDisplay.setInstance(instance, pos.withY(pos.y() + 1.5)); // Raise more to distinguish
            visualizationEntities.add(blockDisplay);
        }
        server.getLogger().info("Drew {} GOLD blocks (mapped racing line)", mappedPoints.size());
    }

    private void visualizeOriginalTelemetry(List<TelemetryPoint> telemetry, Block blockType, Pos playerPos) {
        // Show original telemetry at player position (no transformation)
        // This shows what the raw data looks like without mapping
        for (TelemetryPoint point : telemetry) {
            double x = point.x().doubleValue() * 0.1 + playerPos.x(); // Scale down and offset
            double z = point.y().doubleValue() * 0.1 + playerPos.z();

            Pos pos = new Pos(x, playerPos.y(), z);

            Entity blockDisplay = new Entity(EntityType.BLOCK_DISPLAY);
            BlockDisplayMeta meta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
            meta.setBlockState(blockType);
            meta.setScale(new Vec(0.2, 0.2, 0.2));
            meta.setHasNoGravity(true);

            blockDisplay.setInstance(instance, pos);
            visualizationEntities.add(blockDisplay);
        }
        server.getLogger().info("Drew {} DIAMOND blocks (original telemetry)", telemetry.size());
    }

    private void clearVisualization() {
        for (Entity entity : visualizationEntities) {
            entity.remove();
        }
        visualizationEntities.clear();
        server.getLogger().info("Cleared visualization entities");
    }

    private double calculateLength(List<Pos> points) {
        if (points.size() < 2) {
            return 0;
        }

        double length = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            length += points.get(i).distance(points.get(i + 1));
        }
        // Add closure distance
        if (points.size() > 2) {
            length += points.get(points.size() - 1).distance(points.get(0));
        }
        return length;
    }
}
