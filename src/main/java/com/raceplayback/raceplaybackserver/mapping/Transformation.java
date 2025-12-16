package com.raceplayback.raceplaybackserver.mapping;

import net.minestom.server.coordinate.Pos;

/**
 * Represents a 2D transformation (scale, rotation, translation) for mapping
 * F1 telemetry coordinates to Minecraft track coordinates.
 *
 * The transformation is applied in this order:
 * 1. Scale
 * 2. Rotation
 * 3. Translation
 */
public record Transformation(
    double scale,
    double rotationRadians,
    Pos offset,
    double errorMetric
) {
    /**
     * Applies this transformation to a 2D point.
     *
     * @param point The point to transform (Y coordinate is preserved)
     * @return The transformed point
     */
    public Pos apply(Pos point) {
        // Step 1: Scale
        double scaledX = point.x() * scale;
        double scaledZ = point.z() * scale;

        // Step 2: Rotation
        double cos = Math.cos(rotationRadians);
        double sin = Math.sin(rotationRadians);
        double rotatedX = scaledX * cos - scaledZ * sin;
        double rotatedZ = scaledX * sin + scaledZ * cos;

        // Step 3: Translation
        return new Pos(
            rotatedX + offset.x(),
            offset.y(), // Y from offset
            rotatedZ + offset.z()
        );
    }

    /**
     * Gets the rotation in degrees.
     */
    public double getRotationDegrees() {
        return Math.toDegrees(rotationRadians);
    }

    @Override
    public String toString() {
        return String.format("Transformation{scale=%.4f, rotation=%.2f°, offset=(%.2f, %.2f, %.2f), error=%.2f}",
            scale, getRotationDegrees(), offset.x(), offset.y(), offset.z(), errorMetric);
    }
}
