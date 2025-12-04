package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Vec;

public class FrontWingLeft extends CarPart {
    public FrontWingLeft() {
        super("front_wing_left", new Vec(-0.439, 0.5, 1.0));
        rotationOffset = 90;
    }
}