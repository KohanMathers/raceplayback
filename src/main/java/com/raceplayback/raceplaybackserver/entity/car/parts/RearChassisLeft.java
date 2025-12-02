package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Vec;

public class RearChassisLeft extends CarPart {
    public RearChassisLeft() {
        super("rear_chassis_left", new Vec(-0.5, 0.5, -0.8));
    }
}