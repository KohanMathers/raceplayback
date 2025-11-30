package com.raceplayback.raceplaybackserver.network;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.raceplayback.raceplaybackserver.RacePlaybackServer;
import com.raceplayback.raceplaybackserver.data.Compound;
import com.raceplayback.raceplaybackserver.data.DataModelType;
import com.raceplayback.raceplaybackserver.data.Driver;
import com.raceplayback.raceplaybackserver.data.LapData;
import com.raceplayback.raceplaybackserver.data.Session;
import com.raceplayback.raceplaybackserver.data.SessionType;
import com.raceplayback.raceplaybackserver.data.TeamRadio;
import com.raceplayback.raceplaybackserver.data.TelemetryPoint;
import com.raceplayback.raceplaybackserver.data.TrackName;
import com.raceplayback.raceplaybackserver.data.WeatherData;

public class F1ApiClient {
    private URI uri;
    private HttpClient client;
    private HttpResponse<String> response;
    private Object dataObject;

    private RacePlaybackServer server = RacePlaybackServer.getInstance();
    private Logger logger = server.getLogger();

    public F1ApiClient(String url, int year, TrackName track, SessionType type, String endpoint, DataModelType modelType) {
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

            switch (modelType) {
                case SESSION:
                    dataObject = parseAsSession(response.body(), type);
                    break;
                case DRIVER:
                    int driverNumber = Integer.parseInt(endpoint.substring(endpoint.lastIndexOf('/') + 1));
                    dataObject = parseAsDriver(response.body(), driverNumber);
                    break;
                case TELEMETRY_POINT:
                    dataObject = parseAsTelemetryPoints(response.body());
                    break;
                case LAP_DATA:
                    dataObject = parseAsLapDataList(response.body());
                    break;
                case TEAM_RADIO:
                    dataObject = parseAsTeamRadioList(response.body());
                    break;
                case WEATHER_DATA:
                    dataObject = parseAsWeatherDataList(response.body());
                    break;
                case NULL:
                    break;
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Error while fetching session data from '" + uri.toString() + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Session parseAsSession(String responseBody, SessionType sessionType) {
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
        String circuitName = json.get("CircuitName").getAsString();
        String dateString = json.get("Date").getAsString();
        String grandPrix = json.get("GrandPrix").getAsString();
        int numberOfLaps = json.get("NumberOfLaps").getAsInt();
        int year = json.get("Year").getAsInt();

        LocalDateTime date = LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return new Session(circuitName, date, grandPrix, numberOfLaps, sessionType, year);
    }

    private Driver parseAsDriver(String responseBody, int driverNumber) {
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
        String abbreviation = json.get("Abbreviation").getAsString();
        String name = json.get("Name").getAsString();
        String team = json.get("Team").getAsString();

        return new Driver(name, abbreviation, driverNumber, team);
    }

    private List<TelemetryPoint> parseAsTelemetryPoints(String responseBody) {
        List<TelemetryPoint> telemetryPoints = new ArrayList<>();
        JsonArray outerArray = JsonParser.parseString(responseBody).getAsJsonArray();

        for (int i = 0; i < outerArray.size(); i++) {
            JsonObject lapObj = outerArray.get(i).getAsJsonObject();
            JsonArray telemetryArray = lapObj.getAsJsonArray("telemetry");

            for (int j = 0; j < telemetryArray.size(); j++) {
                JsonObject telemetryJson = telemetryArray.get(j).getAsJsonObject();
                boolean braking = telemetryJson.get("Brake").getAsBoolean();
                Compound compound = Compound.valueOf(telemetryJson.get("Compound").getAsString());
                int drs = telemetryJson.get("DRS").getAsInt();
                BigDecimal distance = telemetryJson.get("Distance").getAsBigDecimal();
                BigDecimal rpm = telemetryJson.get("RPM").getAsBigDecimal();
                Long sessionTime = telemetryJson.get("SessionTime_ms").getAsLong();
                BigDecimal speed = telemetryJson.get("Speed").getAsBigDecimal();
                BigDecimal throttle = telemetryJson.get("Throttle").getAsBigDecimal();
                BigDecimal x = telemetryJson.get("X").getAsBigDecimal();
                BigDecimal y = telemetryJson.get("Y").getAsBigDecimal();
                int gear = telemetryJson.get("nGear").getAsInt();

                TelemetryPoint point = new TelemetryPoint(braking, compound, drs, distance, rpm, sessionTime, speed, throttle, x, y, gear);
                telemetryPoints.add(point);
            }
        }

        return telemetryPoints;
    }

    private List<LapData> parseAsLapDataList(String responseBody) {
        List<LapData> lapDataList = new ArrayList<>();
        JsonArray jsonArray = JsonParser.parseString(responseBody).getAsJsonArray();

        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject json = jsonArray.get(i).getAsJsonObject();
            String driverName = json.get("Driver").getAsString();
            int driverNumber = json.get("DriverNumber").getAsInt();
            boolean personalBest = json.get("IsPersonalBest").getAsBoolean();
            int lapNumber = json.get("LapNumber").getAsInt();
            double lapTime = json.get("LapTime").getAsDouble();
            
            double sector1Time = 0.0;
            double sector2Time = 0.0;
            double sector3Time = 0.0;

            if (json.has("Sector1Time") && !json.get("Sector1Time").isJsonNull()) {
                sector1Time = json.get("Sector1Time").getAsDouble();
            }
            if (json.has("Sector2Time") && !json.get("Sector2Time").isJsonNull()) {
                sector2Time = json.get("Sector2Time").getAsDouble();
            }
            if (json.has("Sector3Time") && !json.get("Sector3Time").isJsonNull()) {
                sector3Time = json.get("Sector3Time").getAsDouble();
            }

            LapData lapData = new LapData(driverName, driverNumber, personalBest == true ? 1 : 0, lapNumber, lapTime, sector1Time, sector2Time, sector3Time);
            lapDataList.add(lapData);
        }

        return lapDataList;
    }

    private List<TeamRadio> parseAsTeamRadioList(String responseBody) {
        List<TeamRadio> teamRadioList = new ArrayList<>();
        JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonArray messagesArray = responseJson.getAsJsonArray("Messages");

        for (int i = 0; i < messagesArray.size(); i++) {
            JsonObject json = messagesArray.get(i).getAsJsonObject();
            String audioURLString = json.get("AudioUrl").getAsString();
            int driverNumber = json.get("RacingNumber").getAsInt();
            String timestampString = json.get("Timestamp").getAsString();
            String transcript = json.get("Transcript").getAsString();
            String utcString = json.get("Utc").getAsString();

            URI audioUrl = URI.create(audioURLString);

            String[] parts = timestampString.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);

            String[] secParts = parts[2].split("\\.");
            int seconds = Integer.parseInt(secParts[0]);
            int millis = Integer.parseInt(secParts[1]);

            long timestamp = hours * 3600_000L + minutes * 60_000L + seconds * 1_000L + millis;

            OffsetDateTime odt = OffsetDateTime.parse(utcString);
            LocalDateTime utc = odt.toLocalDateTime();

            TeamRadio teamRadio = new TeamRadio(audioUrl, driverNumber, timestamp, transcript, utc);
            teamRadioList.add(teamRadio);
        }

        return teamRadioList;
    }

    private List<WeatherData> parseAsWeatherDataList(String responseBody) {
        List<WeatherData> weatherDataList = new ArrayList<>();
        JsonArray jsonArray = JsonParser.parseString(responseBody).getAsJsonArray();

        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject json = jsonArray.get(i).getAsJsonObject();
            Long time = json.get("Time").getAsLong() * 1000;
            double rainfall = json.get("Rainfall").getAsDouble();

            WeatherData weatherData = new WeatherData(time, rainfall);
            weatherDataList.add(weatherData);
        }

        return weatherDataList;
    }

    public Object getData() {
        return dataObject;
    }
}