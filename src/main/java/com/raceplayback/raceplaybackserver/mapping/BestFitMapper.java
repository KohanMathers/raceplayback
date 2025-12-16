package com.raceplayback.raceplaybackserver.mapping;

import com.raceplayback.raceplaybackserver.data.TelemetryPoint;
import net.minestom.server.coordinate.Pos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds optimal transformation between F1 racing line and Minecraft track using Procrustes analysis.
 * Computes scale, rotation, and translation to best fit telemetry data to track geometry.
 */
public class BestFitMapper {
    private static final Logger logger = LoggerFactory.getLogger(BestFitMapper.class);

    /**
     * Finds the best-fit transformation from racing line to track centerline.
     *
     * @param telemetry List of telemetry points (F1 racing line)
     * @param trackCenterline Detected track centerline (Minecraft)
     * @return Optimal transformation
     */
    public static Transformation findBestFit(List<TelemetryPoint> telemetry, List<Pos> trackCenterline) {
        logger.info("Finding best-fit transformation...");
        logger.info("  Racing line points: {}", telemetry.size());
        logger.info("  Track centerline points: {}", trackCenterline.size());

        if (telemetry.isEmpty() || trackCenterline.isEmpty()) {
            logger.error("Cannot compute best fit with empty data!");
            return new Transformation(1.0, 0.0, new Pos(0, 64, 0), Double.MAX_VALUE);
        }

        // Step 1: Convert telemetry to 2D positions
        List<Pos> racingLine = extractRacingLine(telemetry);

        // Step 2: Normalize both lines to start at origin
        Pos racingOrigin = racingLine.get(0);
        Pos trackOrigin = trackCenterline.get(0);

        List<Pos> normalizedRacing = normalize(racingLine, racingOrigin);
        List<Pos> normalizedTrack = normalize(trackCenterline, trackOrigin);

        // Step 3: Calculate scale ratio
        double racingLength = calculateTotalLength(normalizedRacing);
        double trackLength = calculateTotalLength(normalizedTrack);
        double scale = trackLength / racingLength;

        logger.info("  Racing line length: {:.2f} units", racingLength);
        logger.info("  Track length: {:.2f} blocks", trackLength);
        logger.info("  Computed scale: {:.4f}", scale);

        // Step 4: Find optimal rotation
        logger.info("  Searching for optimal rotation...");
        double bestRotation = findBestRotation(normalizedRacing, normalizedTrack, scale);
        logger.info("  Best rotation: {:.2f}°", Math.toDegrees(bestRotation));

        // Step 5: Calculate translation offset
        // After scaling and rotation, align the first points
        Pos transformedFirst = applyScaleAndRotation(racingLine.get(0), scale, bestRotation, racingOrigin);
        Pos targetFirst = trackCenterline.get(0);

        Pos offset = new Pos(
            targetFirst.x() - transformedFirst.x(),
            targetFirst.y(), // Use track Y height
            targetFirst.z() - transformedFirst.z()
        );

        // Step 6: Compute final error metric
        double finalError = computeError(racingLine, trackCenterline, scale, bestRotation, offset, racingOrigin);
        logger.info("  Final error metric: {:.2f}", finalError);

        return new Transformation(scale, bestRotation, offset, finalError);
    }

    /**
     * Extracts 2D racing line from telemetry points.
     */
    private static List<Pos> extractRacingLine(List<TelemetryPoint> telemetry) {
        List<Pos> racingLine = new ArrayList<>();
        for (TelemetryPoint point : telemetry) {
            racingLine.add(new Pos(
                point.x().doubleValue(),
                0,
                point.y().doubleValue()
            ));
        }
        return racingLine;
    }

    /**
     * Normalizes a list of positions to start at origin.
     */
    private static List<Pos> normalize(List<Pos> points, Pos origin) {
        List<Pos> normalized = new ArrayList<>();
        for (Pos point : points) {
            normalized.add(new Pos(
                point.x() - origin.x(),
                0,
                point.z() - origin.z()
            ));
        }
        return normalized;
    }

    /**
     * Calculates total path length.
     */
    private static double calculateTotalLength(List<Pos> points) {
        if (points.size() < 2) {
            return 0;
        }

        double totalLength = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            totalLength += distance2D(points.get(i), points.get(i + 1));
        }
        return totalLength;
    }

    /**
     * Finds optimal rotation angle by testing all angles from 0-360°.
     * First pass: 1° increments
     * Second pass: 0.1° increments around best angle
     */
    private static double findBestRotation(List<Pos> racing, List<Pos> track, double scale) {
        double bestAngle = 0;
        double minError = Double.MAX_VALUE;

        // First pass: Coarse search (1° increments)
        for (int degree = 0; degree < 360; degree++) {
            double radians = Math.toRadians(degree);
            double error = computeErrorNormalized(racing, track, scale, radians);

            if (error < minError) {
                minError = error;
                bestAngle = radians;
            }
        }

        // Second pass: Fine search (0.1° increments around best)
        double coarseBest = bestAngle;
        for (int i = -10; i <= 10; i++) {
            double radians = coarseBest + Math.toRadians(i * 0.1);
            double error = computeErrorNormalized(racing, track, scale, radians);

            if (error < minError) {
                minError = error;
                bestAngle = radians;
            }
        }

        return bestAngle;
    }

    /**
     * Computes error for normalized (origin-centered) points.
     */
    private static double computeErrorNormalized(List<Pos> racing, List<Pos> track, double scale, double rotation) {
        List<Pos> transformed = new ArrayList<>();

        for (Pos point : racing) {
            // Scale
            double scaledX = point.x() * scale;
            double scaledZ = point.z() * scale;

            // Rotate
            double cos = Math.cos(rotation);
            double sin = Math.sin(rotation);
            double rotatedX = scaledX * cos - scaledZ * sin;
            double rotatedZ = scaledX * sin + scaledZ * cos;

            transformed.add(new Pos(rotatedX, 0, rotatedZ));
        }

        // Compute sum of squared distances to nearest track points
        return computeDistanceError(transformed, track);
    }

    /**
     * Computes error for full transformation (with offset).
     */
    private static double computeError(List<Pos> racing, List<Pos> track, double scale, double rotation,
                                       Pos offset, Pos racingOrigin) {
        List<Pos> transformed = new ArrayList<>();

        for (Pos point : racing) {
            Pos transformedPoint = applyScaleAndRotation(point, scale, rotation, racingOrigin);
            transformed.add(new Pos(
                transformedPoint.x() + offset.x(),
                0,
                transformedPoint.z() + offset.z()
            ));
        }

        return computeDistanceError(transformed, track);
    }

    /**
     * Applies scale and rotation to a point (relative to origin).
     */
    private static Pos applyScaleAndRotation(Pos point, double scale, double rotation, Pos origin) {
        // Translate to origin
        double x = point.x() - origin.x();
        double z = point.z() - origin.z();

        // Scale
        double scaledX = x * scale;
        double scaledZ = z * scale;

        // Rotate
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        double rotatedX = scaledX * cos - scaledZ * sin;
        double rotatedZ = scaledX * sin + scaledZ * cos;

        return new Pos(rotatedX, 0, rotatedZ);
    }

    /**
     * Computes sum of squared distances from transformed points to nearest track points.
     */
    private static double computeDistanceError(List<Pos> transformed, List<Pos> track) {
        double totalError = 0;

        for (Pos transformedPoint : transformed) {
            // Find nearest track point
            double minDist = Double.MAX_VALUE;
            for (Pos trackPoint : track) {
                double dist = distance2D(transformedPoint, trackPoint);
                if (dist < minDist) {
                    minDist = dist;
                }
            }
            totalError += minDist * minDist; // Squared distance
        }

        return totalError;
    }

    /**
     * Calculates 2D distance (ignoring Y).
     */
    private static double distance2D(Pos a, Pos b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
