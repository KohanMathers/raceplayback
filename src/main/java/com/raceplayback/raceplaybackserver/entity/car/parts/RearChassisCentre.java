package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Vec;

public class RearChassisCentre extends CarPart {
    public RearChassisCentre() {
        super("rear_chassis_centre", new Vec(0, 0.5, -0.8));
    }
}