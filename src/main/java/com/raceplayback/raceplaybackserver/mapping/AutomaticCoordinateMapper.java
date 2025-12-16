package com.raceplayback.raceplaybackserver.mapping;

import com.raceplayback.raceplaybackserver.data.TelemetryPoint;
import com.raceplayback.raceplaybackserver.data.TrackName;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main automatic coordinate mapper that uses track detection + best-fit transformation.
 * Replaces manual track marking with fully automatic mapping.
 */
public class AutomaticCoordinateMapper {
    private static final Logger logger = LoggerFactory.getLogger(AutomaticCoordinateMapper.class);

    private final Transformation transformation;
    private final List<Pos> detectedCenterline;
    private final List<TelemetryPoint> telemetry;
    private final Map<Integer, Pos> mappingCache;

    private AutomaticCoordinateMapper(Transformation transformation, List<Pos> detectedCenterline,
                                      List<TelemetryPoint> telemetry) {
        this.transformation = transformation;
        this.detectedCenterline = detectedCenterline;
        this.telemetry = telemetry;
        this.mappingCache = new HashMap<>();
    }

    /**
     * Creates an automatic mapper with caching.
     * Flow:
     * 1. Check cache first
     * 2. If not cached: detect track, find best fit, cache result
     * 3. Return mapper ready to use
     *
     * @param instance The Minecraft instance
     * @param trackCenter Center point to start track detection (player position)
     * @param searchRadius Radius to scan for track (blocks)
     * @param telemetry Telemetry data for the lap
     * @param trackName Track name for caching
     * @return Ready-to-use mapper
     */
    public static AutomaticCoordinateMapper create(Instance instance, Pos trackCenter, int searchRadius,
                                                   List<TelemetryPoint> telemetry, TrackName trackName) {
        logger.info("Creating automatic mapper for {} at player position ({}, {}, {}) yaw={}",
            trackName, trackCenter.x(), trackCenter.y(), trackCenter.z(), trackCenter.yaw());

        // Step 1: Check cache
        var cachedTransformation = TransformationCache.load(trackName);
        List<Pos> centerline;
        Transformation transformation;

        if (cachedTransformation.isPresent()) {
            logger.info("Using cached transformation for {}", trackName);
            transformation = cachedTransformation.get();

            // Still need to detect track for centerline (fast operation)
            // Use player position and yaw for path-following
            logger.info("Detecting track for centerline...");
            AutomaticTrackScanner.TrackBounds bounds = AutomaticTrackScanner.detectTrack(
                instance, trackCenter, searchRadius,
                AutomaticTrackScanner.DEFAULT_ROAD_BLOCKS,
                trackCenter.yaw()
            );
            centerline = bounds.centerline();

        } else {
            logger.info("No cache found, computing new transformation...");

            // Step 2: Detect track
            // Use player position and yaw for path-following
            logger.info("Detecting track surface...");
            long startTime = System.currentTimeMillis();
            AutomaticTrackScanner.TrackBounds bounds = AutomaticTrackScanner.detectTrack(
                instance, trackCenter, searchRadius,
                AutomaticTrackScanner.DEFAULT_ROAD_BLOCKS,
                trackCenter.yaw()
            );
            centerline = bounds.centerline();
            long detectTime = System.currentTimeMillis() - startTime;
            logger.info("Track detection completed in {}ms ({} blocks, {} centerline points)",
                detectTime, centerline.size(), centerline.size());

            if (centerline.isEmpty()) {
                logger.error("Track detection failed! No centerline found.");
                throw new IllegalStateException("No track detected. Make sure you're near a track.");
            }

            // Step 3: Find best-fit transformation
            logger.info("Computing best-fit transformation...");
            startTime = System.currentTimeMillis();
            transformation = BestFitMapper.findBestFit(telemetry, centerline);
            long fitTime = System.currentTimeMillis() - startTime;
            logger.info("Best-fit computed in {}ms: {}", fitTime, transformation);

            // Step 4: Cache the transformation
            TransformationCache.save(trackName, transformation);
        }

        return new AutomaticCoordinateMapper(transformation, centerline, telemetry);
    }

    /**
     * Creates a mapper without caching (for testing/visualization).
     */
    public static AutomaticCoordinateMapper createUncached(Instance instance, Pos trackCenter, int searchRadius,
                                                           List<TelemetryPoint> telemetry) {
        logger.info("Creating uncached automatic mapper");

        // Detect track
        AutomaticTrackScanner.TrackBounds bounds = AutomaticTrackScanner.detectTrack(
            instance, trackCenter, searchRadius
        );

        if (bounds.centerline().isEmpty()) {
            throw new IllegalStateException("No track detected");
        }

        // Find best fit
        Transformation transformation = BestFitMapper.findBestFit(telemetry, bounds.centerline());

        return new AutomaticCoordinateMapper(transformation, bounds.centerline(), telemetry);
    }

    /**
     * Maps a telemetry point to Minecraft coordinates.
     *
     * @param telemetryIndex Index of telemetry point
     * @return Minecraft position
     */
    public Pos mapTelemetryPoint(int telemetryIndex) {
        if (mappingCache.containsKey(telemetryIndex)) {
            return mappingCache.get(telemetryIndex);
        }

        if (telemetryIndex < 0 || telemetryIndex >= telemetry.size()) {
            logger.warn("Invalid telemetry index: {}", telemetryIndex);
            return transformation.offset();
        }

        TelemetryPoint point = telemetry.get(telemetryIndex);
        Pos telemetryPos = new Pos(
            point.x().doubleValue(),
            0,
            point.y().doubleValue()
        );

        Pos mapped = transformation.apply(telemetryPos);
        mappingCache.put(telemetryIndex, mapped);
        return mapped;
    }

    /**
     * Maps a telemetry point and optionally snaps to nearest centerline point.
     *
     * @param telemetryIndex Index of telemetry point
     * @param snapToCenterline If true, snap to nearest track centerline point
     * @return Minecraft position
     */
    public Pos mapTelemetryPoint(int telemetryIndex, boolean snapToCenterline) {
        Pos mapped = mapTelemetryPoint(telemetryIndex);

        if (!snapToCenterline || detectedCenterline.isEmpty()) {
            return mapped;
        }

        // Find nearest centerline point
        Pos nearest = detectedCenterline.get(0);
        double minDist = mapped.distance(nearest);

        for (Pos centerlinePoint : detectedCenterline) {
            double dist = mapped.distance(centerlinePoint);
            if (dist < minDist) {
                minDist = dist;
                nearest = centerlinePoint;
            }
        }

        return nearest;
    }

    /**
     * Calculates yaw angle for a telemetry point.
     */
    public float calculateYaw(int telemetryIndex) {
        if (telemetryIndex >= telemetry.size() - 1) {
            return 0f;
        }

        Pos current = mapTelemetryPoint(telemetryIndex);
        Pos next = mapTelemetryPoint(telemetryIndex + 1);

        double dx = next.x() - current.x();
        double dz = next.z() - current.z();

        if (Math.abs(dx) < 0.001 && Math.abs(dz) < 0.001) {
            return 0f;
        }

        double angle = Math.toDegrees(Math.atan2(dz, dx));
        float yaw = (float) (90 - angle);

        while (yaw > 180) yaw -= 360;
        while (yaw < -180) yaw += 360;

        return yaw;
    }

    /**
     * Gets all mapped telemetry points.
     */
    public List<Pos> getAllMappedPoints() {
        List<Pos> mapped = new ArrayList<>();
        for (int i = 0; i < telemetry.size(); i++) {
            mapped.add(mapTelemetryPoint(i));
        }
        return mapped;
    }

    /**
     * Gets the detected track centerline.
     */
    public List<Pos> getDetectedCenterline() {
        return new ArrayList<>(detectedCenterline);
    }

    /**
     * Gets the transformation being used.
     */
    public Transformation getTransformation() {
        return transformation;
    }

    /**
     * Gets the telemetry data.
     */
    public List<TelemetryPoint> getTelemetry() {
        return telemetry;
    }

    /**
     * Clears the mapping cache.
     */
    public void clearCache() {
        mappingCache.clear();
    }
}
