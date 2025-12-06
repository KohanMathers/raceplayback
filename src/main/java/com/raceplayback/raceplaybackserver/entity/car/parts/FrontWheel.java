package com.raceplayback.raceplaybackserver.entity.car.parts;

import com.raceplayback.raceplaybackserver.data.Compound;
import com.raceplayback.raceplaybackserver.entity.car.CarPart;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.item.ItemStack;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.instance.Instance;

public class FrontWheel extends CarPart {
    private final Vec wheelScale = new Vec(0.54f, 0.66f, 0.66f);
    private float wheelSteeringAngle = 0f;

    public FrontWheel(Vec offset, Compound compound) {
        super("temp", offset);

        ItemStack wheel = compound.createWheel(true);
        ItemDisplayMeta meta = (ItemDisplayMeta) entity.getEntityMeta();
        meta.setItemStack(wheel);
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

        float steeringRad = (float) Math.toRadians(wheelSteeringAngle);
        float steeringHalf = steeringRad / 2;
        float[] steeringQuat = new float[] {
            0.0f,
            (float) Math.sin(steeringHalf),
            0.0f,
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

    public void setWheelSteeringAngle(float angle) {
        this.wheelSteeringAngle = angle;
    }

    @Override
    public void spawn(Instance instance, Pos carPosition, float yaw) {
        super.spawn(instance, carPosition, yaw);

        instance.scheduleNextTick(inst -> {
            setCustomScale(wheelScale);
            update(carPosition, yaw);
        });
    }
}