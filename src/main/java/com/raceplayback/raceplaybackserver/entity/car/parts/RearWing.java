package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public class RearWing extends CarPart {
    private boolean drsOpen = false;
    
    public RearWing() {
        super("rear_wing_closed", new Vec(0, 1.2, -2.0));
    }
    
    public void setDRS(boolean open) {
        this.drsOpen = open;
        
        String model = open ? "rear_wing_open" : "rear_wing_closed";
        ItemStack wingModel = ItemStack.of(Material.STICK)
            .withItemModel("raceplayback:" + model);
        
        ItemDisplayMeta meta = (ItemDisplayMeta) entity.getEntityMeta();
        meta.setItemStack(wingModel);
    }
    
    public boolean isDRSOpen() {
        return drsOpen;
    }
}