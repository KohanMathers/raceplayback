package com.raceplayback.raceplaybackserver.data;

public record Driver(
    String name,
    String abbreviation,
    int number,
    String team
) {}
