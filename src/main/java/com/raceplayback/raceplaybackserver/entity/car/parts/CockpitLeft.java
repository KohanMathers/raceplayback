package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Vec;

public class CockpitLeft extends CarPart {
    public CockpitLeft() {
        super("cockpit_left", new Vec(-1, 0.5, 0));
        setCustomScale(new Vec(1.6f, 0.96f, 1.5f));
        entity.setView(180, 0);
    }
}