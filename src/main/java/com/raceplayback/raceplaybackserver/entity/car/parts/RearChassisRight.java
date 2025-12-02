package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Vec;

public class RearChassisRight extends CarPart {
    public RearChassisRight() {
        super("rear_chassis_right", new Vec(0.5, 0.5, -0.8));
    }
}