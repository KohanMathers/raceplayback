package com.raceplayback.raceplaybackserver.mapping;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Automatically detects track surfaces by scanning for road blocks.
 * CRITICAL: Only uses the TOPMOST road block per (X, Z) coordinate to avoid underground blocks.
 */
public class AutomaticTrackScanner {
    private static final Logger logger = LoggerFactory.getLogger(AutomaticTrackScanner.class);

    /**
     * Default road blocks to detect
     */
    public static final Set<Block> DEFAULT_ROAD_BLOCKS = Set.of(
        Block.BLUE_ICE,
        Block.PACKED_ICE,
        Block.ICE,
        Block.WATER,
        Block.SOUL_SAND
    );

    /**
     * Detects track surface blocks and computes centerline.
     *
     * @param instance The Minecraft instance to scan
     * @param center Center point to start scanning from (player position)
     * @param radius Radius to scan (in blocks)
     * @param roadBlocks Set of blocks to consider as road surface
     * @return Detected track bounds and centerline
     */
    public static TrackBounds detectTrack(Instance instance, Pos center, int radius, Set<Block> roadBlocks) {
        return detectTrack(instance, center, radius, roadBlocks, null);
    }

    /**
     * Detects track with player position and yaw for path-following start.
     */
    public static TrackBounds detectTrack(Instance instance, Pos center, int radius, Set<Block> roadBlocks, Float playerYaw) {
        logger.info("Starting track detection at ({}, {}, {}) with radius {}",
            center.x(), center.y(), center.z(), radius);

        List<Pos> detectedBlocks = new ArrayList<>();
        int centerX = (int) center.x();
        int centerZ = (int) center.z();

        // Calculate chunk boundaries
        int minChunkX = (centerX - radius) >> 4; // Divide by 16 to get chunk coordinate
        int maxChunkX = (centerX + radius) >> 4;
        int minChunkZ = (centerZ - radius) >> 4;
        int maxChunkZ = (centerZ + radius) >> 4;

        // Load all required chunks first
        logger.info("Loading chunks in area (chunks {},{} to {},{})", minChunkX, minChunkZ, maxChunkX, maxChunkZ);
        int chunksToLoad = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        logger.info("Total chunks to load: {}", chunksToLoad);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                // Load chunk synchronously if not loaded
                if (!instance.isChunkLoaded(chunkX, chunkZ)) {
                    instance.loadChunk(chunkX, chunkZ).join(); // Wait for chunk to load
                }
            }
        }

        logger.info("All chunks loaded, starting block scan...");

        // Scan in a square around the center point
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                // Find topmost road block at this (X, Z) coordinate
                Pos roadBlock = findTopmostRoadBlock(instance, x, z, roadBlocks);
                if (roadBlock != null) {
                    detectedBlocks.add(roadBlock);
                }
            }
        }

        logger.info("Detected {} road blocks", detectedBlocks.size());

        if (detectedBlocks.isEmpty()) {
            logger.warn("No road blocks found! Check if you're near a track.");
            return new TrackBounds(List.of(), 0.0, 0.0, 0.0, 0.0);
        }

        // Compute centerline from detected blocks, using player position/yaw if provided
        List<Pos> centerline = computeCenterline(detectedBlocks, center, playerYaw, instance, roadBlocks);
        logger.info("Computed centerline with {} points", centerline.size());

        // Compute bounds
        double minX = detectedBlocks.stream().mapToDouble(Pos::x).min().orElse(0);
        double maxX = detectedBlocks.stream().mapToDouble(Pos::x).max().orElse(0);
        double minZ = detectedBlocks.stream().mapToDouble(Pos::z).min().orElse(0);
        double maxZ = detectedBlocks.stream().mapToDouble(Pos::z).max().orElse(0);

        return new TrackBounds(centerline, minX, maxX, minZ, maxZ);
    }

    /**
     * Detects track using default road blocks.
     */
    public static TrackBounds detectTrack(Instance instance, Pos center, int radius) {
        return detectTrack(instance, center, radius, DEFAULT_ROAD_BLOCKS);
    }

    /**
     * CRITICAL: Finds the TOPMOST road block at (x, z) by scanning from Y=255 downward.
     * This ensures we don't pick up underground road blocks.
     *
     * @param instance The instance to scan
     * @param x X coordinate
     * @param z Z coordinate
     * @param roadBlocks Set of blocks to consider as road
     * @return The topmost road block position, or null if none found
     */
    private static Pos findTopmostRoadBlock(Instance instance, int x, int z, Set<Block> roadBlocks) {
        // Scan from top to bottom
        for (int y = 255; y >= 0; y--) {
            Block block = instance.getBlock(x, y, z);
            if (roadBlocks.contains(block)) {
                return new Pos(x, y, z);
            }
        }
        return null; // No road block in this column
    }

    /**
     * Computes a centerline path from detected road blocks using path-following algorithm.
     * This ensures continuity and prevents jumping to pit lanes or side tracks.
     *
     * @param detectedBlocks All detected road surface blocks
     * @param playerPos Player position to use as starting point (or null for auto-detect)
     * @param playerYaw Player yaw to use as initial direction (or null for auto-detect)
     * @param instance The Minecraft instance for checking blocks
     * @param roadBlocks Set of blocks to consider as road surface
     * @return Ordered list of centerline points following the track
     */
    private static List<Pos> computeCenterline(List<Pos> detectedBlocks, Pos playerPos, Float playerYaw,
                                               Instance instance, Set<Block> roadBlocks) {
        if (detectedBlocks.isEmpty()) {
            return List.of();
        }

        logger.info("Computing centerline with path-following algorithm...");

        // Create a set for fast lookup
        Set<Pos> roadBlockSet = new HashSet<>(detectedBlocks);

        // Start from player position if provided, otherwise pick an arbitrary point
        Pos startPoint;
        if (playerPos != null) {
            // Find nearest road block to player position
            startPoint = detectedBlocks.stream()
                .min(Comparator.comparingDouble(p -> p.distance(playerPos)))
                .orElse(detectedBlocks.get(0));
            logger.info("Using player position as starting point, nearest road block: ({}, {}, {})",
                startPoint.x(), startPoint.y(), startPoint.z());
        } else {
            startPoint = detectedBlocks.stream()
                .min(Comparator.comparingDouble(p -> p.x() + p.z()))
                .orElse(detectedBlocks.get(0));
            logger.info("Auto-detected starting point: ({}, {}, {})",
                startPoint.x(), startPoint.y(), startPoint.z());
        }

        List<Pos> centerline = new ArrayList<>();
        Set<Pos> visited = new HashSet<>();
        Pos current = startPoint;

        // Initial direction from player yaw if provided
        double directionX;
        double directionZ;

        if (playerYaw != null) {
            // Convert Minecraft yaw to direction vector
            // Minecraft yaw: 0° = South (+Z), 90° = West (-X), 180° = North (-Z), 270° = East (+X)
            double yawRadians = Math.toRadians(playerYaw);
            directionX = -Math.sin(yawRadians);
            directionZ = Math.cos(yawRadians);
            logger.info("Using player yaw {}° as initial direction: ({}, {})",
                playerYaw, String.format("%.2f", directionX), String.format("%.2f", directionZ));
        } else {
            // Auto-detect direction from nearest neighbor
            directionX = 1.0;
            directionZ = 0.0;

            Pos nearestNeighbor = findNearestUnvisited(current, roadBlockSet, visited, 5);
            if (nearestNeighbor != null) {
                directionX = nearestNeighbor.x() - current.x();
                directionZ = nearestNeighbor.z() - current.z();
                double length = Math.sqrt(directionX * directionX + directionZ * directionZ);
                if (length > 0.001) {
                    directionX /= length;
                    directionZ /= length;
                }
            }
            logger.info("Auto-detected initial direction: ({}, {})",
                String.format("%.2f", directionX), String.format("%.2f", directionZ));
        }

        logger.info("Starting path following from ({}, {}, {}) heading ({}, {})",
            current.x(), current.y(), current.z(),
            String.format("%.2f", directionX), String.format("%.2f", directionZ));

        int maxIterations = detectedBlocks.size();
        int sampleInterval = 3; // Sample every 3 blocks to reduce point density

        for (int iter = 0; iter < maxIterations; iter++) {
            visited.add(current);

            if (iter % sampleInterval == 0) {
                // Center the point between left and right edges before adding
                Pos centeredPoint = centerBetweenEdges(current, directionX, directionZ, roadBlockSet, instance);
                centerline.add(centeredPoint);
            }

            // Try to continue in the current direction first
            Pos next = findNextInDirection(current, directionX, directionZ, roadBlockSet, visited);

            if (next == null) {
                // If we can't continue straight, try turning (sweep left to right)
                next = findNextByTurning(current, directionX, directionZ, roadBlockSet, visited);
            }

            if (next == null) {
                // No more road blocks to follow - we've completed the loop or reached a dead end
                logger.info("Path following complete after {} iterations, {} centerline points",
                    iter, centerline.size());
                break;
            }

            // Update direction based on movement
            double newDirX = next.x() - current.x();
            double newDirZ = next.z() - current.z();
            double length = Math.sqrt(newDirX * newDirX + newDirZ * newDirZ);

            if (length > 0.001) {
                // Smooth direction change (blend old and new direction)
                // Increased smoothing from 0.7/0.3 to 0.85/0.15 to reduce zig-zagging
                directionX = 0.85 * directionX + 0.15 * (newDirX / length);
                directionZ = 0.85 * directionZ + 0.15 * (newDirZ / length);

                // Renormalize
                double normLength = Math.sqrt(directionX * directionX + directionZ * directionZ);
                directionX /= normLength;
                directionZ /= normLength;
            }

            current = next;
        }

        logger.info("Centerline computed with {} points", centerline.size());
        return centerline;
    }

    /**
     * Centers a point between the left and right track edges.
     * CRITICAL: Only uses CONTINUOUS road blocks - stops at first gap to avoid pit lane.
     */
    private static Pos centerBetweenEdges(Pos current, double dirX, double dirZ,
                                         Set<Pos> roadBlockSet, Instance instance) {
        // Calculate perpendicular direction (left/right of travel direction)
        double perpX = -dirZ;  // Perpendicular to direction
        double perpZ = dirX;

        // Search for left and right edges (CONTINUOUS ONLY - stop at first gap)
        Pos leftEdge = null;
        Pos rightEdge = null;
        int maxSearchDist = 20;

        // Search left - STOP AT FIRST GAP (this prevents jumping to pit lane)
        boolean foundGapLeft = false;
        for (int dist = 1; dist <= maxSearchDist; dist++) {
            int x = (int) Math.round(current.x() + perpX * dist);
            int z = (int) Math.round(current.z() + perpZ * dist);

            // Check multiple Y levels for the road block (handle elevation)
            boolean foundAtThisDistance = false;
            for (int dy = -2; dy <= 2; dy++) {
                Pos candidate = new Pos(x, current.y() + dy, z);
                if (roadBlockSet.contains(candidate)) {
                    foundAtThisDistance = true;
                    break;
                }
            }

            if (!foundAtThisDistance) {
                // Found a gap - this is the edge
                if (dist > 1 && !foundGapLeft) {
                    leftEdge = new Pos(
                        current.x() + perpX * (dist - 1),
                        current.y(),
                        current.z() + perpZ * (dist - 1)
                    );
                }
                foundGapLeft = true;
                break; // CRITICAL: Stop at first gap, don't continue to pit lane
            }
        }

        // Search right - STOP AT FIRST GAP
        boolean foundGapRight = false;
        for (int dist = 1; dist <= maxSearchDist; dist++) {
            int x = (int) Math.round(current.x() - perpX * dist);
            int z = (int) Math.round(current.z() - perpZ * dist);

            // Check multiple Y levels for the road block (handle elevation)
            boolean foundAtThisDistance = false;
            for (int dy = -2; dy <= 2; dy++) {
                Pos candidate = new Pos(x, current.y() + dy, z);
                if (roadBlockSet.contains(candidate)) {
                    foundAtThisDistance = true;
                    break;
                }
            }

            if (!foundAtThisDistance) {
                // Found a gap - this is the edge
                if (dist > 1 && !foundGapRight) {
                    rightEdge = new Pos(
                        current.x() - perpX * (dist - 1),
                        current.y(),
                        current.z() - perpZ * (dist - 1)
                    );
                }
                foundGapRight = true;
                break; // CRITICAL: Stop at first gap, don't continue to pit lane
            }
        }

        // If we found both edges, return midpoint
        if (leftEdge != null && rightEdge != null) {
            double centerX = (leftEdge.x() + rightEdge.x()) / 2.0;
            double centerZ = (leftEdge.z() + rightEdge.z()) / 2.0;

            // Use the Y level from current position (will be updated by elevation tracking)
            return new Pos(centerX, current.y(), centerZ);
        }

        // If we couldn't find edges, just return current position
        return current;
    }

    /**
     * Tries to find the next road block by continuing in the current direction.
     * Searches in a cone ahead of the current direction.
     * NOW HANDLES Y-LEVEL CHANGES by searching up/down as well.
     * CRITICAL: Only considers CONNECTED blocks (no jumping across gaps to pit lane).
     */
    private static Pos findNextInDirection(Pos current, double dirX, double dirZ,
                                           Set<Pos> roadBlocks, Set<Pos> visited) {
        int searchRadius = 8;
        int ySearchRange = 3; // Search up to 3 blocks up or down
        double bestScore = -1;
        Pos best = null;

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                if (dx == 0 && dz == 0) continue;

                // Search at different Y levels
                for (int dy = -ySearchRange; dy <= ySearchRange; dy++) {
                    Pos candidate = new Pos(
                        current.x() + dx,
                        current.y() + dy,
                        current.z() + dz
                    );

                    if (!roadBlocks.contains(candidate) || visited.contains(candidate)) {
                        continue;
                    }

                    // CRITICAL: Check if candidate is CONNECTED to current position
                    // This prevents jumping across gaps to pit lane
                    if (!isConnected(current, candidate, roadBlocks)) {
                        continue;
                    }

                    // Calculate alignment with current direction (XZ plane only)
                    double distXZ = Math.sqrt(dx * dx + dz * dz);
                    if (distXZ < 0.001) continue;

                    double candidateDirX = dx / distXZ;
                    double candidateDirZ = dz / distXZ;

                    // Dot product: how well aligned is this candidate with current direction?
                    double alignment = candidateDirX * dirX + candidateDirZ * dirZ;

                    // Prefer candidates that are:
                    // 1. Well aligned with current direction (higher alignment is better)
                    // 2. Close by in XZ plane (lower distXZ is better)
                    // 3. Close by in Y (prefer gradual elevation changes)
                    // Only consider candidates within 60° cone ahead (alignment > 0.5)
                    if (alignment > 0.5) {
                        double score = alignment * 10.0 - distXZ * 0.5 - Math.abs(dy) * 0.3;

                        if (score > bestScore) {
                            bestScore = score;
                            best = candidate;
                        }
                    }
                }
            }
        }

        return best;
    }

    /**
     * Checks if two positions are connected by continuous road blocks.
     * This prevents the path-following from jumping across large gaps to reach pit lane.
     * Allows crossing small gaps (1-2 blocks, like white lines) but not large gaps (3+ blocks, like walls).
     */
    private static boolean isConnected(Pos from, Pos to, Set<Pos> roadBlocks) {
        // Calculate the direction and distance
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // If very close (adjacent), consider connected
        if (distance < 2.5) {
            return true;
        }

        // Walk along the line from 'from' to 'to' and check for gaps
        int steps = (int) Math.ceil(distance);
        int consecutiveGaps = 0;
        int maxConsecutiveGaps = 2; // Allow gaps up to 2 blocks (white lines) but not 3+ (walls)

        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            double x = from.x() + dx * t;
            double y = from.y() + dy * t;
            double z = from.z() + dz * t;

            // Check if there's a road block near this point (within 1 block radius, ±2 Y levels)
            boolean foundNearby = false;
            for (int checkX = -1; checkX <= 1; checkX++) {
                for (int checkZ = -1; checkZ <= 1; checkZ++) {
                    for (int checkY = -2; checkY <= 2; checkY++) {
                        Pos checkPos = new Pos(
                            Math.round(x) + checkX,
                            Math.round(y) + checkY,
                            Math.round(z) + checkZ
                        );
                        if (roadBlocks.contains(checkPos)) {
                            foundNearby = true;
                            break;
                        }
                    }
                    if (foundNearby) break;
                }
                if (foundNearby) break;
            }

            if (!foundNearby) {
                consecutiveGaps++;
                // If we hit too many consecutive gaps, it's a wall - not connected
                if (consecutiveGaps > maxConsecutiveGaps) {
                    return false;
                }
            } else {
                // Found a road block, reset gap counter
                consecutiveGaps = 0;
            }
        }

        return true;
    }

    /**
     * Measures the track width at a given position by searching perpendicular to travel direction.
     * Returns the total width in blocks (distance from left edge to right edge).
     * STOPS AT FIRST GAP to only measure continuous track width.
     */
    private static double measureTrackWidth(Pos position, double dirX, double dirZ, Set<Pos> roadBlocks) {
        // Calculate perpendicular direction
        double perpX = -dirZ;
        double perpZ = dirX;

        int maxSearchDist = 25;
        int leftDist = 0;
        int rightDist = 0;

        // Search left - stop at first gap
        for (int dist = 1; dist <= maxSearchDist; dist++) {
            int x = (int) Math.round(position.x() + perpX * dist);
            int z = (int) Math.round(position.z() + perpZ * dist);

            boolean found = false;
            for (int dy = -2; dy <= 2; dy++) {
                Pos candidate = new Pos(x, position.y() + dy, z);
                if (roadBlocks.contains(candidate)) {
                    found = true;
                    leftDist = dist;
                    break;
                }
            }

            if (!found) {
                break; // Hit a gap, stop searching
            }
        }

        // Search right - stop at first gap
        for (int dist = 1; dist <= maxSearchDist; dist++) {
            int x = (int) Math.round(position.x() - perpX * dist);
            int z = (int) Math.round(position.z() - perpZ * dist);

            boolean found = false;
            for (int dy = -2; dy <= 2; dy++) {
                Pos candidate = new Pos(x, position.y() + dy, z);
                if (roadBlocks.contains(candidate)) {
                    found = true;
                    rightDist = dist;
                    break;
                }
            }

            if (!found) {
                break; // Hit a gap, stop searching
            }
        }

        // Total width = left + right + 1 (for current block)
        return leftDist + rightDist + 1;
    }

    /**
     * Tries to find the next road block by turning.
     * Only called when continuing straight didn't work.
     * NOW HANDLES Y-LEVEL CHANGES.
     * CRITICAL: Only considers CONNECTED blocks (no jumping across gaps to pit lane).
     */
    private static Pos findNextByTurning(Pos current, double dirX, double dirZ,
                                         Set<Pos> roadBlocks, Set<Pos> visited) {
        int searchRadius = 10;
        int ySearchRange = 3; // Search up to 3 blocks up or down
        double bestScore = -1;
        Pos best = null;

        // Try angles from -90° to +90° relative to current direction
        for (int angleDeg = -90; angleDeg <= 90; angleDeg += 15) {
            double angleRad = Math.toRadians(angleDeg);
            double cos = Math.cos(angleRad);
            double sin = Math.sin(angleRad);

            // Rotate direction vector
            double rotDirX = dirX * cos - dirZ * sin;
            double rotDirZ = dirX * sin + dirZ * cos;

            // Search in this direction at various Y levels
            for (int dist = 2; dist <= searchRadius; dist++) {
                int dx = (int) Math.round(rotDirX * dist);
                int dz = (int) Math.round(rotDirZ * dist);

                for (int dy = -ySearchRange; dy <= ySearchRange; dy++) {
                    Pos candidate = new Pos(
                        current.x() + dx,
                        current.y() + dy,
                        current.z() + dz
                    );

                    if (!roadBlocks.contains(candidate) || visited.contains(candidate)) {
                        continue;
                    }

                    // CRITICAL: Check if candidate is CONNECTED to current position
                    // This prevents jumping across gaps to pit lane
                    if (!isConnected(current, candidate, roadBlocks)) {
                        continue;
                    }

                    // Prefer closer candidates with smaller turn angles and gradual Y changes
                    double score = 10.0 / (Math.abs(angleDeg) + 1) - dist * 0.3 - Math.abs(dy) * 0.5;

                    if (score > bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }

        return best;
    }

    /**
     * Finds the nearest unvisited road block within a radius.
     */
    private static Pos findNearestUnvisited(Pos current, Set<Pos> roadBlocks,
                                           Set<Pos> visited, int radius) {
        Pos nearest = null;
        double minDist = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue;

                Pos candidate = new Pos(current.x() + dx, current.y(), current.z() + dz);

                if (!roadBlocks.contains(candidate) || visited.contains(candidate)) {
                    continue;
                }

                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = candidate;
                }
            }
        }

        return nearest;
    }

    /**
     * Container for track detection results
     */
    public record TrackBounds(
        List<Pos> centerline,
        double minX,
        double maxX,
        double minZ,
        double maxZ
    ) {
        public double getWidth() {
            return maxX - minX;
        }

        public double getHeight() {
            return maxZ - minZ;
        }

        public Pos getCenter() {
            return new Pos(
                (minX + maxX) / 2,
                centerline.isEmpty() ? 64 : centerline.get(0).y(),
                (minZ + maxZ) / 2
            );
        }

        /**
         * Calculates total length of centerline
         */
        public double getTotalLength() {
            if (centerline.size() < 2) {
                return 0;
            }

            double totalLength = 0;
            for (int i = 0; i < centerline.size() - 1; i++) {
                totalLength += centerline.get(i).distance(centerline.get(i + 1));
            }
            if (centerline.size() > 2) {
                totalLength += centerline.get(centerline.size() - 1).distance(centerline.get(0));
            }
            return totalLength;
        }
    }
}
