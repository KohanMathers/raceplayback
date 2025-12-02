package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;

public class SteeringWheel extends CarPart {
    private float steeringAngle = 0f;
    
    public SteeringWheel() {
        super("steering_wheel", new Vec(0, 0.8, 0.5));
    }
    
    @Override
    public void update(Pos carPosition, float yaw) {
        super.update(carPosition, yaw);
        
        ItemDisplayMeta meta = (ItemDisplayMeta) entity.getEntityMeta();
        
        float angleRad = (float) Math.toRadians(steeringAngle);
        float halfAngle = angleRad / 2;
        
        float[] quaternion = new float[] {
            0,
            0,
            (float) Math.sin(halfAngle),
            (float) Math.cos(halfAngle)
        };
        
        meta.setLeftRotation(quaternion);
    }
        
    public void setSteeringAngle(float angle) {
        this.steeringAngle = angle;
    }
}