package com.raceplayback.raceplaybackserver.util;

import net.minestom.server.coordinate.Pos;
import java.math.BigDecimal;

public class CoordinateConverter {
    private static final double SCALE = 0.082;

    private final double originX;
    private final double originZ;
    private final double rotationOffset;

    private Double firstTelemetryX = null;
    private Double firstTelemetryY = null;

    public CoordinateConverter(Pos trackOrigin, double rotationOffset) {
        this.originX = trackOrigin.x();
        this.originZ = trackOrigin.z();
        this.rotationOffset = rotationOffset;
    }

    public Pos toMinecraftPos(BigDecimal telemetryX, BigDecimal telemetryY, double height) {
        double telX = telemetryX.doubleValue();
        double telY = telemetryY.doubleValue();

        if (firstTelemetryX == null) {
            firstTelemetryX = telX;
            firstTelemetryY = telY;
        }

        double deltaX = (telX - firstTelemetryX) * SCALE;
        double deltaZ = -(telY - firstTelemetryY) * SCALE;

        double rotationRad = Math.toRadians(rotationOffset);
        double rotatedX = deltaX * Math.cos(rotationRad) - deltaZ * Math.sin(rotationRad);
        double rotatedZ = deltaX * Math.sin(rotationRad) + deltaZ * Math.cos(rotationRad);

        double mcX = originX + rotatedX;
        double mcZ = originZ + rotatedZ;

        return new Pos(mcX, height, mcZ);
    }

    public float calculateYaw(Pos from, Pos to) {
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();

        if (Math.abs(dx) < 0.001 && Math.abs(dz) < 0.001) {
            return 0f;
        }

        double angle = Math.toDegrees(Math.atan2(dz, dx));

        float yaw = (float) (90 - angle);

        while (yaw > 180) yaw -= 360;
        while (yaw < -180) yaw += 360;

        return yaw;
    }
}