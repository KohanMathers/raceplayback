package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Vec;

public class CockpitMiddle extends CarPart {
    public CockpitMiddle() {
        super("cockpit_middle", new Vec(0, 0.5, 0));
        setCustomScale(new Vec(2.13f, 1.01f, 1.5f));
    }
}