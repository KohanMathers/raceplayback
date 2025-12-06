package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.instance.Instance;

public class SteeringWheel extends CarPart {
    private float steeringAngle = 0f;
    private final Vec steeringWheelScale = new Vec(0.3, 0.3, 0.3);
    
    public SteeringWheel(Vec offset) {
        super("steering_wheel", offset);
        rotationOffset = 180;
    }
    
    @Override
    public void update(Pos carPosition, float yaw) {
        super.update(carPosition, yaw);

        ItemDisplayMeta meta = (ItemDisplayMeta) entity.getEntityMeta();

        float yawRad = (float) Math.toRadians(-(yaw + rotationOffset));
        float yawHalf = yawRad / 2.0f;
        float[] yawQuat = new float[] {
            0.0f,
            (float) Math.sin(yawHalf),
            0.0f,
            (float) Math.cos(yawHalf)
        };

        float steeringRad = (float) Math.toRadians(steeringAngle);
        float steeringHalf = steeringRad / 2;
        float[] steeringQuat = new float[] {
            0.0f,
            0.0f,
            (float) Math.sin(steeringHalf),
            (float) Math.cos(steeringHalf)
        };

        float[] combined = multiplyQuaternions(yawQuat, steeringQuat);

        meta.setLeftRotation(combined);
        meta.setNotifyAboutChanges(true);
    }

    private float[] multiplyQuaternions(float[] q1, float[] q2) {
        float x = q1[3] * q2[0] + q1[0] * q2[3] + q1[1] * q2[2] - q1[2] * q2[1];
        float y = q1[3] * q2[1] - q1[0] * q2[2] + q1[1] * q2[3] + q1[2] * q2[0];
        float z = q1[3] * q2[2] + q1[0] * q2[1] - q1[1] * q2[0] + q1[2] * q2[3];
        float w = q1[3] * q2[3] - q1[0] * q2[0] - q1[1] * q2[1] - q1[2] * q2[2];

        return new float[] {x, y, z, w};
    }
        
    public void setSteeringAngle(float angle) {
        this.steeringAngle = angle;
    }


    @Override
    public void spawn(Instance instance, Pos carPosition, float yaw) {
        super.spawn(instance, carPosition, yaw);

        instance.scheduleNextTick(inst -> {
            setCustomScale(steeringWheelScale);
            update(carPosition, yaw);
        });
    }
}