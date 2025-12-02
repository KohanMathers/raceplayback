package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Vec;

public class RearAssembly extends CarPart {
    public RearAssembly() {
        super("rear_assembly", new Vec(0, 0.6, -1.5));
    }
}