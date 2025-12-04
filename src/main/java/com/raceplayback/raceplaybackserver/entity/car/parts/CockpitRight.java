package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Vec;

public class CockpitRight extends CarPart {
    public CockpitRight() {
        super("cockpit_right", new Vec(-0.3125, 0.5, 0));
        rotationOffset = 180;
    }
}