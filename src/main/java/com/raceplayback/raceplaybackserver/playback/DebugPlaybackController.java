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

    public DebugPlaybackController(int year, TrackName track, SessionType sessionType, String driverCode, Pos startPosition, double rotationOffset, Instance instance) {
        this.year = year;
        this.track = track;
        this.sessionType = sessionType;
        this.driverCode = driverCode;
        this.rotationOffset = rotationOffset;
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

        Compound compound = telemetry.get(0).compound();

        car = new F1Car(driverCode, compound);
        Pos startPos = converter.toMinecraftPos(
            telemetry.get(0).x(),
            telemetry.get(0).y(),
            66
        );
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

        Pos position = converter.toMinecraftPos(
            current.x(),
            current.y(),
            66
        );

        server.getLogger().info("=== AFTER COORDINATE CONVERSION ===");
        server.getLogger().info("  Minecraft X: {}", position.x());
        server.getLogger().info("  Minecraft Y: {}", position.y());
        server.getLogger().info("  Minecraft Z: {}", position.z());

        float yaw = 0;
        float nextYaw = 0;
        if (currentIndex < telemetry.size() - 1) {
            TelemetryPoint next = telemetry.get(currentIndex + 1);
            Pos nextPos = converter.toMinecraftPos(
                next.x(),
                next.y(),
                66
            );

            server.getLogger().info("=== NEXT POINT FOR YAW CALCULATION ===");
            server.getLogger().info("  Next Telemetry X: {}", next.x());
            server.getLogger().info("  Next Telemetry Y: {}", next.y());
            server.getLogger().info("  Next Minecraft X: {}", nextPos.x());
            server.getLogger().info("  Next Minecraft Z: {}", nextPos.z());

            yaw = converter.calculateYaw(position, nextPos);

            server.getLogger().info("=== YAW CALCULATION ===");
            server.getLogger().info("  Delta X: {}", nextPos.x() - position.x());
            server.getLogger().info("  Delta Z: {}", nextPos.z() - position.z());
            server.getLogger().info("  Calculated Yaw: {}°", yaw);

            if (currentIndex < telemetry.size() - 2) {
                TelemetryPoint nextNext = telemetry.get(currentIndex + 2);
                Pos nextNextPos = converter.toMinecraftPos(
                    nextNext.x(),
                    nextNext.y(),
                    66
                );
                nextYaw = converter.calculateYaw(nextPos, nextNextPos);

                server.getLogger().info("=== NEXT YAW FOR STEERING ===");
                server.getLogger().info("  Next Yaw: {}°", nextYaw);
                server.getLogger().info("  Yaw Delta (turn rate): {}°", nextYaw - yaw);
            }
        } else {
            server.getLogger().info("=== YAW CALCULATION ===");
            server.getLogger().info("  Last point - using yaw 0°");
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

        for (TelemetryPoint point : telemetry) {
            Pos pos = converter.toMinecraftPos(
                point.x(),
                point.y(),
                65
            );

            Entity blockDisplay = new Entity(EntityType.BLOCK_DISPLAY);
            BlockDisplayMeta meta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
            meta.setBlockState(Block.GOLD_BLOCK);
            meta.setScale(new Vec(0.25, 0.25, 0.25));
            meta.setHasNoGravity(true);

            blockDisplay.setInstance(instance, pos);
            visualizationEntities.add(blockDisplay);
        }

        server.getLogger().info("Lap visualization complete! Drew {} gold block displays", telemetry.size());
        server.getLogger().info("Track oriented with rotation offset: {}°", rotationOffset);
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
