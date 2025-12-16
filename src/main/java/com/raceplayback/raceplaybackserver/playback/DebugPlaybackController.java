package com.raceplayback.raceplaybackserver.playback;

import com.raceplayback.raceplaybackserver.RacePlaybackServer;
import com.raceplayback.raceplaybackserver.data.Compound;
import com.raceplayback.raceplaybackserver.data.DataModelType;
import com.raceplayback.raceplaybackserver.data.Session;
import com.raceplayback.raceplaybackserver.data.SessionType;
import com.raceplayback.raceplaybackserver.data.TelemetryPoint;
import com.raceplayback.raceplaybackserver.data.TrackName;
import com.raceplayback.raceplaybackserver.entity.car.F1Car;
import com.raceplayback.raceplaybackserver.network.F1ApiClient;
import com.raceplayback.raceplaybackserver.util.CoordinateConverter;
import com.raceplayback.raceplaybackserver.mapping.AdaptiveCoordinateMapper;
import com.raceplayback.raceplaybackserver.mapping.AutomaticCoordinateMapper;
import com.raceplayback.raceplaybackserver.mapping.TrackCenterline;
import com.raceplayback.raceplaybackserver.mapping.TrackDataManager;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.BlockDisplayMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

import java.util.ArrayList;
import java.util.List;

public class DebugPlaybackController {
    private final RacePlaybackServer server = RacePlaybackServer.getInstance();

    private F1Car car;
    private CoordinateConverter converter;
    private AdaptiveCoordinateMapper adaptiveMapper;
    private AutomaticCoordinateMapper automaticMapper;
    private boolean useAdaptiveMapper = false;
    private boolean useAutomaticMapper = false;
    private List<TelemetryPoint> telemetry;
    private int currentIndex = 0;
    private List<Entity> visualizationEntities = new ArrayList<>();

    private int year;
    private TrackName track;
    private SessionType sessionType;
    private String driverCode;
    private int currentLap = 1;
    private int totalLaps;
    private double rotationOffset;
    private Pos playerPosition;

    public DebugPlaybackController(int year, TrackName track, SessionType sessionType, String driverCode, Pos startPosition, double rotationOffset, Instance instance) {
        this.year = year;
        this.track = track;
        this.sessionType = sessionType;
        this.driverCode = driverCode;
        this.rotationOffset = rotationOffset;
        this.playerPosition = startPosition;
        this.converter = new CoordinateConverter(startPosition, rotationOffset);

        F1ApiClient sessionClient = new F1ApiClient(
            "https://raceplayback.com/api/v1/sessions",
            year,
            track,
            sessionType,
            "info",
            DataModelType.SESSION
        );

        Session session = (Session) sessionClient.getData();
        this.totalLaps = session != null ? session.numberOfLaps() : 57;

        server.getLogger().info("Debug session has {} laps", totalLaps);
    }

    public void initialize(Instance instance) {
        server.getLogger().info("Loading debug session for driver {}...", driverCode);

        telemetry = fetchTelemetry(currentLap);

        if (telemetry == null || telemetry.isEmpty()) {
            server.getLogger().error("No telemetry data for lap 1!");
            return;
        }

        // Try automatic mapping first (best option)
        try {
            server.getLogger().info("Attempting automatic track mapping...");
            automaticMapper = AutomaticCoordinateMapper.create(
                instance,
                playerPosition,
                500, // search radius
                telemetry,
                track
            );
            useAutomaticMapper = true;
            server.getLogger().info("✓ Using AUTOMATIC COORDINATE MAPPING (best-fit transformation)");
            server.getLogger().info("  Transformation: {}", automaticMapper.getTransformation());
        } catch (Exception e) {
            server.getLogger().warn("⚠ Automatic mapping failed: {}", e.getMessage());
            server.getLogger().info("Falling back to manual track data...");

            // Fallback to manual adaptive mapper
            TrackCenterline centerline = TrackDataManager.loadTrackCenterline(track);
            if (centerline != null) {
                server.getLogger().info("✓ Loaded track centerline for {} (length: {} blocks)",
                    track, String.format("%.1f", centerline.getTotalLength()));

                adaptiveMapper = new AdaptiveCoordinateMapper(centerline, 66.0);
                adaptiveMapper.initializeWithTelemetry(telemetry);
                useAdaptiveMapper = true;

                server.getLogger().info("✓ Using ADAPTIVE COORDINATE MAPPING (arc-length parameterization)");
            } else {
                server.getLogger().warn("⚠ No track data found for {}. Using legacy linear mapping.", track);
                server.getLogger().warn("⚠ The automatic mapper couldn't detect the track.", track);
                useAdaptiveMapper = false;
            }
        }

        Compound compound = telemetry.get(0).compound();
        car = new F1Car(driverCode, compound);

        Pos startPos;
        if (useAutomaticMapper) {
            startPos = automaticMapper.mapTelemetryPoint(0);
        } else if (useAdaptiveMapper) {
            startPos = adaptiveMapper.mapTelemetryPoint(0);
        } else {
            startPos = converter.toMinecraftPos(
                telemetry.get(0).x(),
                telemetry.get(0).y(),
                66
            );
        }

        car.spawn(instance, startPos);

        server.getLogger().info("Debug session initialized with {} telemetry points", telemetry.size());

        drawLapVisualization(instance);

        server.getLogger().info("Use /debugnext to step through each point");
    }

    public void processNextPoint() {
        if (telemetry == null || telemetry.isEmpty()) {
            server.getLogger().warn("No telemetry data loaded!");
            return;
        }

        if (currentIndex >= telemetry.size()) {
            server.getLogger().info("Reached end of telemetry data for lap {}", currentLap);
            server.getLogger().info("Total points processed: {}", currentIndex);
            return;
        }

        TelemetryPoint current = telemetry.get(currentIndex);

        server.getLogger().info("========== DEBUG POINT {} ==========", currentIndex);
        server.getLogger().info("=== RAW API DATA ===");
        server.getLogger().info("  Telemetry X: {}", current.x());
        server.getLogger().info("  Telemetry Y: {}", current.y());
        server.getLogger().info("  Session Time: {}ms", current.sessionTime());
        server.getLogger().info("  Speed: {} km/h", current.speed());
        server.getLogger().info("  Throttle: {}%", current.throttle());
        server.getLogger().info("  Brake: {}", current.braking());
        server.getLogger().info("  Gear: {}", current.gear());
        server.getLogger().info("  DRS: {} (Open: {})", current.drs(), current.isDRS());
        server.getLogger().info("  RPM: {}", current.rpm());
        server.getLogger().info("  Compound: {}", current.compound());

        Pos position;
        float yaw = 0;
        float nextYaw = 0;

        if (useAutomaticMapper) {
            position = automaticMapper.mapTelemetryPoint(currentIndex);

            server.getLogger().info("=== AUTOMATIC COORDINATE MAPPING ===");
            server.getLogger().info("  Minecraft X: {}", position.x());
            server.getLogger().info("  Minecraft Y: {}", position.y());
            server.getLogger().info("  Minecraft Z: {}", position.z());
            server.getLogger().info("  Transformation: scale={}, rotation={}°",
                String.format("%.4f", automaticMapper.getTransformation().scale()),
                String.format("%.2f", automaticMapper.getTransformation().getRotationDegrees()));

            if (currentIndex < telemetry.size() - 1) {
                yaw = automaticMapper.calculateYaw(currentIndex);

                if (currentIndex < telemetry.size() - 2) {
                    nextYaw = automaticMapper.calculateYaw(currentIndex + 1);
                    server.getLogger().info("  Yaw: {}°", String.format("%.2f", yaw));
                    server.getLogger().info("  Next Yaw: {}°", String.format("%.2f", nextYaw));
                    server.getLogger().info("  Yaw Delta (turn rate): {}°", String.format("%.2f", nextYaw - yaw));
                }
            }
        } else if (useAdaptiveMapper) {
            position = adaptiveMapper.mapTelemetryPoint(currentIndex);

            server.getLogger().info("=== ADAPTIVE COORDINATE MAPPING ===");
            server.getLogger().info("  Minecraft X: {}", position.x());
            server.getLogger().info("  Minecraft Y: {}", position.y());
            server.getLogger().info("  Minecraft Z: {}", position.z());

            var mappingResult = adaptiveMapper.getMappingResult(currentIndex);
            server.getLogger().info("  Track Progress: {}%", String.format("%.2f", mappingResult.trackPercent * 100));
            server.getLogger().info("  Lateral Offset: {} blocks", String.format("%.2f", mappingResult.lateralOffset));

            if (currentIndex < telemetry.size() - 1) {
                yaw = adaptiveMapper.calculateYaw(currentIndex);

                if (currentIndex < telemetry.size() - 2) {
                    nextYaw = adaptiveMapper.calculateYaw(currentIndex + 1);
                    server.getLogger().info("  Yaw: {}°", String.format("%.2f", yaw));
                    server.getLogger().info("  Next Yaw: {}°", String.format("%.2f", nextYaw));
                    server.getLogger().info("  Yaw Delta (turn rate): {}°", String.format("%.2f", nextYaw - yaw));
                }
            }
        } else {
            position = converter.toMinecraftPos(
                current.x(),
                current.y(),
                66
            );

            server.getLogger().info("=== LEGACY COORDINATE CONVERSION ===");
            server.getLogger().info("  Minecraft X: {}", position.x());
            server.getLogger().info("  Minecraft Y: {}", position.y());
            server.getLogger().info("  Minecraft Z: {}", position.z());

            if (currentIndex < telemetry.size() - 1) {
                TelemetryPoint next = telemetry.get(currentIndex + 1);
                Pos nextPos = converter.toMinecraftPos(
                    next.x(),
                    next.y(),
                    66
                );

                yaw = converter.calculateYaw(position, nextPos);
                server.getLogger().info("  Calculated Yaw: {}°", yaw);

                if (currentIndex < telemetry.size() - 2) {
                    TelemetryPoint nextNext = telemetry.get(currentIndex + 2);
                    Pos nextNextPos = converter.toMinecraftPos(
                        nextNext.x(),
                        nextNext.y(),
                        66
                    );
                    nextYaw = converter.calculateYaw(nextPos, nextNextPos);
                    server.getLogger().info("  Yaw Delta (turn rate): {}°", nextYaw - yaw);
                }
            }
        }

        Pos posWithYaw = position.withYaw(yaw);

        server.getLogger().info("=== BEFORE CAR UPDATE ===");
        server.getLogger().info("  Car current position: {}", car.getPosition());
        server.getLogger().info("  New position to apply: ({}, {}, {}) with yaw {}°",
            posWithYaw.x(), posWithYaw.y(), posWithYaw.z(), posWithYaw.yaw());
        server.getLogger().info("  Expected movement direction: dx={}, dz={}",
            currentIndex < telemetry.size() - 1 ? telemetry.get(currentIndex + 1).x().subtract(current.x()) : 0,
            currentIndex < telemetry.size() - 1 ? telemetry.get(currentIndex + 1).y().subtract(current.y()) : 0);

        car.update(posWithYaw);
        car.setDRS(current.isDRS());

        float yawDelta = nextYaw - yaw;
        float steeringAngle = yawDelta * 10.0f;
        car.setSteeringAngle(steeringAngle);

        server.getLogger().info("=== FINAL APPLIED VALUES ===");
        server.getLogger().info("  Car position after update: {}", car.getPosition());
        server.getLogger().info("  Position: ({}, {}, {})", posWithYaw.x(), posWithYaw.y(), posWithYaw.z());
        server.getLogger().info("  Yaw: {}°", yaw);
        server.getLogger().info("  Next Yaw: {}°", nextYaw);
        server.getLogger().info("  Yaw Delta (turn rate): {}°", yawDelta);
        server.getLogger().info("  Steering Angle: {}°", steeringAngle);
        server.getLogger().info("  DRS Open: {}", current.isDRS());

        float[] cockpitRotation = car.getCockpitMiddle().getLeftRotation();
        float cockpitYaw = car.getCockpitMiddle().getYawFromRotation();
        server.getLogger().info("=== COCKPIT MIDDLE ROTATION ===");
        server.getLogger().info("  Quaternion (x,y,z,w): [{}, {}, {}, {}]",
            cockpitRotation[0], cockpitRotation[1], cockpitRotation[2], cockpitRotation[3]);
        server.getLogger().info("  Extracted Yaw: {}°", cockpitYaw);

        float[] steeringRotation = car.getSteeringWheel().getLeftRotation();
        server.getLogger().info("=== STEERING WHEEL ROTATION ===");
        server.getLogger().info("  Input Steering Angle: {}°", steeringAngle);
        server.getLogger().info("  Quaternion (x,y,z,w): [{}, {}, {}, {}]",
            steeringRotation[0], steeringRotation[1], steeringRotation[2], steeringRotation[3]);

        float[] leftWheelRot = car.getFrontLeftWheel().getLeftRotation();
        float[] rightWheelRot = car.getFrontRightWheel().getLeftRotation();
        server.getLogger().info("=== FRONT WHEEL ROTATIONS ===");
        server.getLogger().info("  Left Wheel Quat (x,y,z,w): [{}, {}, {}, {}]",
            leftWheelRot[0], leftWheelRot[1], leftWheelRot[2], leftWheelRot[3]);
        server.getLogger().info("  Right Wheel Quat (x,y,z,w): [{}, {}, {}, {}]",
            rightWheelRot[0], rightWheelRot[1], rightWheelRot[2], rightWheelRot[3]);
        server.getLogger().info("====================================");

        currentIndex++;

        server.getLogger().info("Point {}/{} processed. Use /debugnext for next point.",
            currentIndex, telemetry.size());
    }

    @SuppressWarnings("unchecked")
    private List<TelemetryPoint> fetchTelemetry(int lap) {
        String endpoint = String.format("telemetry/%s/%d", driverCode, lap);

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

    private void drawLapVisualization(Instance instance) {
        if (telemetry == null || telemetry.isEmpty()) {
            server.getLogger().warn("No telemetry data to visualize!");
            return;
        }

        server.getLogger().info("Drawing lap visualization with {} points...", telemetry.size());

        for (int i = 0; i < telemetry.size(); i++) {
            Pos pos;

            if (useAutomaticMapper) {
                pos = automaticMapper.mapTelemetryPoint(i).withY(65);
            } else if (useAdaptiveMapper) {
                pos = adaptiveMapper.mapTelemetryPoint(i).withY(65);
            } else {
                TelemetryPoint point = telemetry.get(i);
                pos = converter.toMinecraftPos(
                    point.x(),
                    point.y(),
                    65
                );
            }

            Entity blockDisplay = new Entity(EntityType.BLOCK_DISPLAY);
            BlockDisplayMeta meta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
            meta.setBlockState(Block.GOLD_BLOCK);
            meta.setScale(new Vec(0.25, 0.25, 0.25));
            meta.setHasNoGravity(true);

            blockDisplay.setInstance(instance, pos);
            visualizationEntities.add(blockDisplay);
        }

        server.getLogger().info("Lap visualization complete! Drew {} gold block displays", telemetry.size());
        if (useAutomaticMapper) {
            server.getLogger().info("Using AUTOMATIC mapping - best-fit transformation");
        } else if (useAdaptiveMapper) {
            server.getLogger().info("Using ADAPTIVE mapping - racing line follows track centerline");
        } else {
            server.getLogger().info("Using LEGACY mapping with rotation offset: {}°", rotationOffset);
        }
    }

    public void stop() {
        if (car != null) {
            car.remove();
        }

        for (Entity entity : visualizationEntities) {
            entity.remove();
        }
        visualizationEntities.clear();
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getTotalPoints() {
        return telemetry != null ? telemetry.size() : 0;
    }
}
