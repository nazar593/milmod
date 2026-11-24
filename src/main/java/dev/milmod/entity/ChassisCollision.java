package dev.milmod.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A subdivided chassis follows the visual yaw, pitch and roll on the server. */
final class ChassisCollision {
    private ChassisCollision() {}

    static boolean canTurn(VehicleEntity vehicle, float currentYaw, float newYaw) {
        if (currentYaw == newYaw) return true;
        var current = boxes(vehicle, Math.toRadians(currentYaw), Math.toRadians(vehicle.getXRot()),
                Math.toRadians(vehicle.authoritativeBank()));
        var rotated = boxes(vehicle, Math.toRadians(newYaw), Math.toRadians(vehicle.getXRot()),
                Math.toRadians(vehicle.authoritativeBank()));
        for (int i = 0; i < rotated.size(); i++) {
            if (clear(vehicle, current.get(i)) && !clear(vehicle, rotated.get(i))) return false;
        }
        return true;
    }

    private static boolean clear(VehicleEntity vehicle, AABB box) {
        return vehicle.level().noCollision(vehicle, box);
    }

    static Vec3 move(VehicleEntity vehicle, Vec3 desired, double yaw, double pitch, double roll,
                     boolean supported, boolean powered, boolean tracked) {
        List<AABB> boxes = boxes(vehicle, yaw, pitch, roll);
        Vec3 base = sweep(vehicle, boxes, desired, Vec3.ZERO);
        if (!supported || !powered || desired.horizontalDistanceSqr() < .00001
                || base.horizontalDistanceSqr() >= desired.horizontalDistanceSqr() * .97) return base;

        // Split the step into up, forward, and settle so a wall or low ceiling still stops the hull.
        double maxStep = tracked ? .58 : .54;
        for (double rise = .18; rise <= maxStep + 1.0E-5; rise += .18) {
            Vec3 up = sweep(vehicle, boxes, new Vec3(0, rise, 0), Vec3.ZERO);
            if (up.y < rise - .015) break;
            Vec3 flat = sweep(vehicle, boxes, new Vec3(desired.x, 0, desired.z), up);
            if (flat.horizontalDistanceSqr() <= base.horizontalDistanceSqr() + 1.0E-5) continue;
            Vec3 raised = up.add(flat);
            Vec3 down = sweep(vehicle, boxes, new Vec3(0, -rise, 0), raised);
            if (down.y > -rise + .008) return raised.add(down);
        }
        return base;
    }

    private static Vec3 sweep(VehicleEntity vehicle, List<AABB> boxes, Vec3 desired, Vec3 origin) {
        double y = clip(vehicle, boxes, origin, 0, desired.y, 0).y;
        Vec3 vertical = origin.add(0, y, 0);
        boolean xFirst = Math.abs(desired.x) >= Math.abs(desired.z);
        if (xFirst) {
            double x = clip(vehicle, boxes, vertical, desired.x, 0, 0).x;
            double z = clip(vehicle, boxes, vertical.add(x, 0, 0), 0, 0, desired.z).z;
            return new Vec3(x, y, z);
        }
        double z = clip(vehicle, boxes, vertical, 0, 0, desired.z).z;
        double x = clip(vehicle, boxes, vertical.add(0, 0, z), desired.x, 0, 0).x;
        return new Vec3(x, y, z);
    }

    private static Vec3 clip(VehicleEntity vehicle, List<AABB> boxes, Vec3 origin, double x, double y, double z) {
        Vec3 movement = new Vec3(x, y, z);
        for (AABB source : boxes) {
            if (movement.lengthSqr() == 0) break;
            AABB box = source.move(origin);
            movement = Entity.collideBoundingBox(vehicle, movement, box, vehicle.level(),
                    vehicle.level().getEntityCollisions(vehicle, box.expandTowards(movement)));
        }
        return movement;
    }

    private static List<AABB> boxes(VehicleEntity vehicle, double yaw, double pitch, double roll) {
        var model = vehicle.profile();
        double width = model.width * .40;
        double halfLength = model.length * .46;
        double halfStation = halfLength / 4;
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double pitchY = Math.sin(pitch), rollY = Math.sin(roll);
        double lower = model == dev.milmod.model.VehicleProfile.ISKANDER_K ? .37 : .31;
        List<AABB> boxes = new ArrayList<>(8);
        for (int station = 0; station < 4; station++) {
            double z = (station * 2 - 3) * halfStation;
            for (int side = -1; side <= 1; side += 2) {
                double x = side * width * .49;
                double px = vehicle.getX() - cos * x + sin * z;
                double pz = vehicle.getZ() - sin * x - cos * z;
                double py = vehicle.getY() + Mth.clamp(-pitchY * z + rollY * x, -.66, .66);
                double dx = Math.abs(cos) * width * .51 + Math.abs(sin) * halfStation;
                double dz = Math.abs(sin) * width * .51 + Math.abs(cos) * halfStation;
                boxes.add(new AABB(px - dx, py + lower, pz - dz, px + dx, py + model.height * .86, pz + dz));
            }
        }
        return boxes;
    }
}
