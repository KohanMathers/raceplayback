package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.data.Compound;
import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.item.ItemStack;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;

public class RearWheel extends CarPart {
    public RearWheel(Vec offset, Compound compound) {
        super("temp", offset);
        
        ItemStack wheel = compound.createWheel(false);
        ItemDisplayMeta meta = (ItemDisplayMeta) entity.getEntityMeta();
        meta.setItemStack(wheel);
    }
}