package com.raceplayback.raceplaybackserver.mapping;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.raceplayback.raceplaybackserver.data.TrackName;
import net.minestom.server.coordinate.Pos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;

/**
 * Caches transformations to disk to avoid recomputation.
 * Transformations are saved as JSON files in data/transformations/
 */
public class TransformationCache {
    private static final Logger logger = LoggerFactory.getLogger(TransformationCache.class);
    private static final Path CACHE_DIR = Paths.get("data", "transformations");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static {
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException e) {
            logger.error("Failed to create transformations cache directory", e);
        }
    }

    /**
     * Loads a cached transformation for a track.
     *
     * @param trackName The track name
     * @return The cached transformation, or empty if not found/invalid
     */
    public static Optional<Transformation> load(TrackName trackName) {
        Path cacheFile = getCacheFile(trackName);

        if (!Files.exists(cacheFile)) {
            logger.debug("No cached transformation found for {}", trackName);
            return Optional.empty();
        }

        try {
            String json = Files.readString(cacheFile);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);

            double scale = obj.get("scale").getAsDouble();
            double rotationRadians = obj.get("rotationRadians").getAsDouble();
            double offsetX = obj.get("offsetX").getAsDouble();
            double offsetY = obj.get("offsetY").getAsDouble();
            double offsetZ = obj.get("offsetZ").getAsDouble();
            double errorMetric = obj.get("errorMetric").getAsDouble();

            Pos offset = new Pos(offsetX, offsetY, offsetZ);
            Transformation transformation = new Transformation(scale, rotationRadians, offset, errorMetric);

            logger.info("Loaded cached transformation for {}: {}", trackName, transformation);
            return Optional.of(transformation);

        } catch (Exception e) {
            logger.error("Failed to load cached transformation for {}", trackName, e);
            return Optional.empty();
        }
    }

    /**
     * Saves a transformation to cache.
     *
     * @param trackName The track name
     * @param transformation The transformation to save
     */
    public static void save(TrackName trackName, Transformation transformation) {
        Path cacheFile = getCacheFile(trackName);

        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("track", trackName.name());
            obj.addProperty("scale", transformation.scale());
            obj.addProperty("rotationRadians", transformation.rotationRadians());
            obj.addProperty("offsetX", transformation.offset().x());
            obj.addProperty("offsetY", transformation.offset().y());
            obj.addProperty("offsetZ", transformation.offset().z());
            obj.addProperty("errorMetric", transformation.errorMetric());
            obj.addProperty("computedAt", Instant.now().toString());

            String json = GSON.toJson(obj);
            Files.writeString(cacheFile, json);

            logger.info("Saved transformation to cache for {}: {}", trackName, cacheFile);

        } catch (IOException e) {
            logger.error("Failed to save transformation cache for {}", trackName, e);
        }
    }

    /**
     * Clears the cached transformation for a track.
     *
     * @param trackName The track name
     */
    public static void clear(TrackName trackName) {
        Path cacheFile = getCacheFile(trackName);

        try {
            if (Files.exists(cacheFile)) {
                Files.delete(cacheFile);
                logger.info("Cleared cached transformation for {}", trackName);
            }
        } catch (IOException e) {
            logger.error("Failed to clear transformation cache for {}", trackName, e);
        }
    }

    /**
     * Clears all cached transformations.
     */
    public static void clearAll() {
        try {
            Files.walk(CACHE_DIR)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        logger.error("Failed to delete cache file: {}", path, e);
                    }
                });
            logger.info("Cleared all transformation caches");
        } catch (IOException e) {
            logger.error("Failed to clear transformation caches", e);
        }
    }

    /**
     * Gets the cache file path for a track.
     */
    private static Path getCacheFile(TrackName trackName) {
        return CACHE_DIR.resolve(trackName.name().toLowerCase() + ".json");
    }
}
