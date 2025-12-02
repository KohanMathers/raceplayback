package com.raceplayback.raceplaybackserver.entity.car;

import com.raceplayback.raceplaybackserver.data.Compound;
import com.raceplayback.raceplaybackserver.entity.car.parts.*;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Instance;

import java.util.ArrayList;
import java.util.List;

public class F1Car {
    private final CockpitLeft cockpitLeft;
    private final CockpitMiddle cockpitMiddle;
    private final CockpitRight cockpitRight;
//    private final FrontNose frontNose;
//    private final NoseCone noseCone;
//    private final FrontWingLeft frontWingLeft;
    // private final FrontWingRight frontWingRight;
    // private final RearAssembly rearAssembly;
    // private final RearChassisCentre rearChassisCentre;
    // private final RearChassisLeft rearChassisLeft;
    // private final RearChassisRight rearChassisRight;
    
    // private final FrontWheel wheelFL;
    // private final FrontWheel wheelFR;
    // private final RearWheel wheelRL;
    // private final RearWheel wheelRR;
    
    // private final SteeringWheel steeringWheel;
    
    // private final RearWing rearWing;
    
    private final List<CarPart> allParts;
    
    private Pos position;
    private float yaw;
    private String driverCode;
    
    public F1Car(String driverCode, Compound compound) {
        this.driverCode = driverCode;
        this.allParts = new ArrayList<>();
        
        cockpitLeft = new CockpitLeft();
        cockpitMiddle = new CockpitMiddle();
        cockpitRight = new CockpitRight();
        // frontNose = new FrontNose();
        // noseCone = new NoseCone();
        // frontWingLeft = new FrontWingLeft();
        // frontWingRight = new FrontWingRight();
        // rearAssembly = new RearAssembly();
        // rearChassisCentre = new RearChassisCentre();
        // rearChassisLeft = new RearChassisLeft();
        // rearChassisRight = new RearChassisRight();
        
        // wheelFL = new FrontWheel(new Vec(-0.7, 0.3, 1.2), compound);
        // wheelFR = new FrontWheel(new Vec(0.7, 0.3, 1.2), compound);
        // wheelRL = new RearWheel(new Vec(-0.7, 0.3, -1.2), compound);
        // wheelRR = new RearWheel(new Vec(0.7, 0.3, -1.2), compound);
        
        // steeringWheel = new SteeringWheel();
        
        // rearWing = new RearWing();
        
        allParts.add(cockpitLeft);
        allParts.add(cockpitMiddle);
        allParts.add(cockpitRight);
        // allParts.add(frontNose);
        // allParts.add(noseCone);
        // allParts.add(frontWingLeft);
        // allParts.add(frontWingRight);
        // allParts.add(rearAssembly);
        // allParts.add(rearChassisCentre);
        // allParts.add(rearChassisLeft);
        // allParts.add(rearChassisRight);
        // allParts.add(wheelFL);
        // allParts.add(wheelFR);
        // allParts.add(wheelRL);
        // allParts.add(wheelRR);
        // allParts.add(steeringWheel);
        // allParts.add(rearWing);
    }
    
    public void spawn(Instance instance, Pos position) {
        this.position = position;
        this.yaw = position.yaw();
        
        for (CarPart part : allParts) {
            part.spawn(instance, position, yaw);
        }
    }
    
    public void update(Pos newPosition) {
        this.position = newPosition;
        this.yaw = newPosition.yaw();
        
        for (CarPart part : allParts) {
            part.update(position, yaw);
        }
    }
    
    // public void setDRS(boolean open) {
    //     rearWing.setDRS(open);
    // }
    
    // public void setSteeringAngle(float angle) {
    //     steeringWheel.setSteeringAngle(angle);
    // }
    
    public void setVisible(boolean visible) {
        for (CarPart part : allParts) {
            part.setVisible(visible);
        }
    }
    
    public void remove() {
        for (CarPart part : allParts) {
            part.remove();
        }
    }
    
    public String getDriverCode() {
        return driverCode;
    }
    
    public Pos getPosition() {
        return position;
    }
}