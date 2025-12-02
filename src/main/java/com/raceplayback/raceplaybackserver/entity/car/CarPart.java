package com.raceplayback.raceplaybackserver.entity.car;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public abstract class CarPart {
    protected Entity entity;
    protected Vec offset;
    
    private static final float SCALE = 1.0f;
    
    public CarPart(String modelName, Vec offset) {
        this.offset = offset;
        this.entity = new Entity(EntityType.ITEM_DISPLAY);
        
        ItemStack model = ItemStack.of(Material.STICK)
            .withItemModel("raceplayback:" + modelName);
        
        ItemDisplayMeta meta = (ItemDisplayMeta) entity.getEntityMeta();
        meta.setItemStack(model);
        meta.setHasNoGravity(true);
        
        meta.setScale(new Vec(SCALE, SCALE, SCALE));
    }
    
    public void spawn(Instance instance, Pos carPosition, float yaw) {
        Pos partPosition = calculatePosition(carPosition, yaw);
        entity.setInstance(instance, partPosition.withYaw(yaw));
    }
    
    public void update(Pos carPosition, float yaw) {
        Pos partPosition = calculatePosition(carPosition, yaw);
        entity.teleport(partPosition.withYaw(yaw));
    }
    
    public void remove() {
        entity.remove();
    }
    
    public void setVisible(boolean visible) {
        entity.setInvisible(!visible);
    }
    
    private Pos calculatePosition(Pos carPosition, float yaw) {
        Vec rotated = rotateOffset(offset, yaw);
        return carPosition.add(rotated);
    }
    
    private Vec rotateOffset(Vec offset, float yaw) {
        double rad = Math.toRadians(yaw);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        
        return new Vec(
            offset.x() * cos - offset.z() * sin,
            offset.y(),
            offset.x() * sin + offset.z() * cos
        );
    }
    
    public Entity getEntity() {
        return entity;
    }
    
    protected void setCustomScale(Vec scale) {
        ItemDisplayMeta meta = (ItemDisplayMeta) entity.getEntityMeta();
        meta.setScale(scale);
    }
}