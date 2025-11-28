package com.racereplay.racereplayserver.network;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.slf4j.Logger;

import com.racereplay.racereplayserver.RaceReplayServer;
import com.racereplay.racereplayserver.data.SessionType;
import com.racereplay.racereplayserver.data.TrackName;

public class F1ApiClient {
    private URI uri;
    private HttpClient client;
    private HttpResponse<String> response;

    private RaceReplayServer server = RaceReplayServer.getInstance();
    private Logger logger = server.getLogger();

    public F1ApiClient(String url, int year, TrackName track, SessionType type, String endpoint) {
        if (!(url.startsWith("https://"))) {
            uri = URI.create("https://%s/%d/%s/%s/%s".formatted(url, year, track.toString().toLowerCase(), type.toString().toLowerCase(), endpoint));
        } else {
            uri = URI.create("%s/%d/%s/%s/%s".formatted(url, year, track.toString().toLowerCase(), type.toString().toLowerCase(), endpoint));
        }

        HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();

        client = HttpClient.newHttpClient();
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.body().contains("404")) {
                logger.warn("URL '" + uri.toString() + "' returned 404");
            }
            logger.info(response.body());
        } catch (IOException | InterruptedException e) {
            logger.error("Error while fetching session data from '" + uri.toString() + "': " + e.getMessage());
            e.printStackTrace();
        }
    }
}