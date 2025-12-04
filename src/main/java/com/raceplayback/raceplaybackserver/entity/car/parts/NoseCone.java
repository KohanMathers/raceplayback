package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Vec;

public class NoseCone extends CarPart {
    public NoseCone() {
        super("nose_cone", new Vec(0, 0.4375, 1.875));
        rotationOffset = 180;
    }
}