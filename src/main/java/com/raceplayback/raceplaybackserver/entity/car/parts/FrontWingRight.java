package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Vec;

public class FrontWingRight extends CarPart {
    public FrontWingRight() {
        super("front_wing_right", new Vec(0.8, 0.3, 2.0));
    }
}