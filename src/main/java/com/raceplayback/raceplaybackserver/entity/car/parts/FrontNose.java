package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Vec;

public class FrontNose extends CarPart {
    public FrontNose() {
        super("front_nose", new Vec(0, 0.5, 1.0));
    }
}