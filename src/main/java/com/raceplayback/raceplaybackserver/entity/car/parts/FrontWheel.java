package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.data.Compound;
import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.item.ItemStack;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;

public class FrontWheel extends CarPart {
    public FrontWheel(Vec offset, Compound compound) {
        super("temp", offset);
        
        ItemStack wheel = compound.createWheel(true);
        ItemDisplayMeta meta = (ItemDisplayMeta) entity.getEntityMeta();
        meta.setItemStack(wheel);
    }
}