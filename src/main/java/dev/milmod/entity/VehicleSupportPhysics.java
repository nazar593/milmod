package dev.milmod.entity;

import dev.milmod.model.VehicleProfile;
import dev.milmod.model.VehicleSuspension;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-side suspension and landing contacts in blocks and ticks. */
final class VehicleSupportPhysics {
    private static final int SUBSTEPS = 3;
    private static final double DT = 1.0 / SUBSTEPS;
    private static final double GRAVITY = .066;

    private double pitchRate;
    private double rollRate;
    private double bodyRoll;
    private boolean active;
    private double grip = 1;

    double grip() { return grip; }

    void reset() {
        pitchRate = 0;
        rollRate = 0;
        active = false;
    }

    void kick(VehicleEntity vehicle, Vec3 impulse) {
        double yaw = Math.toRadians(vehicle.getYRot());
        rollRate += (-Math.cos(yaw) * impulse.x - Math.sin(yaw) * impulse.z) * .75;
        pitchRate += (Math.sin(yaw) * impulse.x - Math.cos(yaw) * impulse.z) * .55;
    }

    void step(VehicleEntity vehicle, double vx, double vz, boolean powered, double lift) {
        VehicleProfile model = vehicle.profile();
        VehicleSuspension layout = VehicleSuspension.of(model);
        boolean airframe = model.air();
        boolean fixture = model.kind == VehicleProfile.Kind.STATIC;
        boolean groundChassis = model.kind == VehicleProfile.Kind.GROUND;
        double yaw = Math.toRadians(vehicle.getYRot());
        double cosYaw = Math.cos(yaw), sinYaw = Math.sin(yaw);
        double pitch = Math.toRadians(vehicle.getXRot());
        double roll = active ? bodyRoll : Math.toRadians(vehicle.authoritativeBank());
        active = true;
        double vy = vehicle.getDeltaMovement().y;
        double pivot = model.height * .43;
        double rollInertia = Math.max(.065, model.width * model.width * (airframe ? .032 : .125));
        double pitchInertia = Math.max(.065, model.length * model.length * (airframe ? .095 : .15));
        double gravity = vehicle.isNoGravity() ? 0 : GRAVITY;
        double verticalReach = fixture ? .29 : airframe ? .24 : .56;
        double droop = fixture ? .15 : airframe ? .095 : .30;
        double radius = layout.radius();
        boolean supported = false;
        boolean righting = !airframe && !powered && vx * vx + vz * vz < .025 && Math.abs(vy) < .16
                && Math.max(Math.abs(roll), Math.abs(pitch)) > .12
                && levelFootprint(vehicle, layout, cosYaw, sinYaw);
        long offsets = 0L;

        for (int step = 0; step < SUBSTEPS; step++) {
            double sum = 0, pitchMoment = 0, rollMoment = 0, traction = 0;
            double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
            double cosPitch = Math.cos(pitch), sinPitch = Math.sin(pitch);
            double cosRoll = Math.cos(roll), sinRoll = Math.sin(roll);
            offsets = 0L;
            for (int i = 0; i < layout.size(); i++) {
                VehicleSuspension.Contact point = layout.contact(i);
                double rx = cosRoll * point.x() + sinRoll * pivot;
                double ry = sinRoll * point.x() - cosRoll * pivot;
                double rz = sinPitch * ry + cosPitch * point.z();
                double foot = vehicle.getY() + pivot + cosPitch * ry - sinPitch * point.z();
                double px = vehicle.getX() - cosYaw * rx + sinYaw * rz;
                double pz = vehicle.getZ() - sinYaw * rx - cosYaw * rz;
                BlockHitResult contact = surface(vehicle, px, pz, foot, verticalReach, droop);
                double sample = contact == null ? Double.NEGATIVE_INFINITY : contact.getLocation().y;
                if (groundChassis && powered) {
                    double contactVx = vx, contactVz = vz;
                    if (layout.tracks()) {
                        // During a pivot, rollers at opposite ends sweep in opposite directions.
                        double turn = vehicle.steering() * .05;
                        contactVx -= turn * (pz - vehicle.getZ());
                        contactVz += turn * (px - vehicle.getX());
                    }
                    double speed = Math.hypot(contactVx, contactVz);
                    if (speed > .065) {
                        double alongX = contactVx / speed * radius;
                        double alongZ = contactVz / speed * radius;
                        for (double fraction = .45; fraction < .99; fraction += .43) {
                            BlockHitResult shoulder = surface(vehicle, px + alongX * fraction,
                                    pz + alongZ * fraction, foot, verticalReach, droop);
                            if (shoulder == null) continue;
                            // The rising tread wraps around the roller instead of acting like a flat skid.
                            double rise = radius * (1 - Math.sqrt(1 - fraction * fraction));
                            double candidate = shoulder.getLocation().y - rise;
                            if (candidate > sample) {
                                sample = candidate;
                                contact = shoulder;
                            }
                        }
                    }
                }
                if (contact == null) {
                    offsets = VehicleSuspension.encode(offsets, i, (float) -droop);
                    continue;
                }
                double offset = sample - foot;
                offsets = VehicleSuspension.encode(offsets, i, (float) offset);
                double localVy = vy + rollRate * rx - pitchRate * rz;
                // Recover from a hard hit even when the underbody rests against the ground.
                double damping = localVy < -.22 ? .38 : .59;
                double force = Mth.clamp(gravity + offset * .92 - localVy * damping, 0, .48) / layout.size();
                sum += force;
                if (force > .0015 && offset > -.08) {
                    minX = Math.min(minX, rx);
                    maxX = Math.max(maxX, rx);
                    minZ = Math.min(minZ, rz);
                    maxZ = Math.max(maxZ, rz);
                }
                if (!airframe && force > 0) {
                    var ground = contact.getBlockPos();
                    float slipperiness = vehicle.level().getBlockState(ground).getFriction(vehicle.level(), ground, vehicle);
                    traction += force * Mth.clamp((1 - slipperiness) / .4, .12, 1.15);
                }
                rollMoment += rx * force;
                pitchMoment -= (rz - cosPitch * layout.centerZ()) * force;
            }

            double supportLimit = airframe ? .58 : .39;
            supported = sum > .008 && vy < .10;
            if (airframe && sum == 0 && vy < -.14 && !powered) {
                double lean = (vehicle.getUUID().getLeastSignificantBits() & 1) == 0 ? -.19 : .19;
                rollMoment += Mth.clamp((lean - roll) * .018, -.006, .006);
                pitchMoment += Mth.clamp((-.22 - pitch) * .018, -.006, .006);
            }
            grip = !airframe && supported ? traction / sum : 1;
            if (step == 0 && supported && !powered && lift == 0
                    && Math.abs(vx) + Math.abs(vy) + Math.abs(vz) < .001
                    && Math.abs(pitchRate) + Math.abs(rollRate) < .001
                    && Math.abs(sum - gravity) < .0007
                    && Math.abs(pitchMoment) + Math.abs(rollMoment) < .0005) {
                vy = 0;
                break;
            }
            rollRate = Mth.clamp((rollRate + rollMoment / rollInertia * DT)
                    * (supported ? .86 : .991), -supportLimit, supportLimit);
            pitchRate = Mth.clamp((pitchRate + pitchMoment / pitchInertia * DT)
                    * (supported ? .86 : .991), -supportLimit, supportLimit);
            if (righting) {
                rollRate = Mth.lerp(.68, rollRate, -roll * .36);
                pitchRate = Mth.lerp(.68, pitchRate, -pitch * .36);
            }
            double angleLimit = airframe ? 1.17 : .73;
            roll = limitAngle(roll, rollRate, angleLimit);
            pitch = limitAngle(pitch, pitchRate, angleLimit);
            vy = Mth.clamp(vy + (sum + lift - gravity) * DT, -.95, .56);

            // An overhanging body moves off its last support as it pivots.
            // Without a remaining contact, there is no additional sideways push.
            if (!righting && minX <= maxX && supported) {
                double lateral = edgeSlip(0, minX, maxX, roll);
                double forward = edgeSlip(cosPitch * layout.centerZ(), minZ, maxZ, pitch);
                vx += (-cosYaw * lateral + sinYaw * forward) * DT;
                vz += (-sinYaw * lateral - cosYaw * forward) * DT;
            }
            Vec3 desired = new Vec3(vx * DT, vy * DT, vz * DT);
            AABB box = hull(vehicle, model, cosYaw, sinYaw);
            Vec3 clipped = groundChassis
                    ? ChassisCollision.move(vehicle, desired, yaw, pitch, roll, supported, powered, layout.tracks())
                    : Entity.collideBoundingBox(vehicle, desired, box, vehicle.level(),
                            vehicle.level().getEntityCollisions(vehicle, box.expandTowards(desired)));
            vehicle.setPos(vehicle.getX() + clipped.x, vehicle.getY() + clipped.y, vehicle.getZ() + clipped.z);
            boolean landed = clipped.y > desired.y && desired.y < 0;
            if (landed) {
                double impact = -vy;
                vy = impact > .14 ? Math.min(.30, impact * (fixture ? .40 : airframe ? .33 : .25)) : 0;
                if (airframe && impact > .19) {
                    rollRate += Math.copySign(Math.min(.14, impact * .24), Math.abs(roll) < .02 ? 1 : roll);
                } else if (!airframe && vx * vx + vz * vz < .018) {
                    rollRate -= roll * .22;
                    pitchRate -= pitch * .18;
                }
            } else if (clipped.y != desired.y) {
                vy = 0;
            }
            if (clipped.x != desired.x) vx = 0;
            if (clipped.z != desired.z) vz = 0;
            supported |= landed;
        }

        vehicle.setOnGround(supported && lift <= gravity);
        vehicle.setDeltaMovement(vx, vy, vz);
        bodyRoll = roll;
        vehicle.setXRot((float) Math.toDegrees(pitch));
        vehicle.setSuspension(offsets);
        vehicle.setActivity(powered || airframe && lift > .01, (float) Math.toDegrees(roll));
    }

    private static boolean levelFootprint(VehicleEntity vehicle, VehicleSuspension layout,
                                          double cosYaw, double sinYaw) {
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < layout.size(); i++) {
            var contact = layout.contact(i);
            double x = vehicle.getX() - cosYaw * contact.x() + sinYaw * contact.z();
            double z = vehicle.getZ() - sinYaw * contact.x() - cosYaw * contact.z();
            BlockHitResult hit = surface(vehicle, x, z, vehicle.getY(), .46, .27);
            if (hit == null) return false;
            low = Math.min(low, hit.getLocation().y);
            high = Math.max(high, hit.getLocation().y);
            if (high - low > .067) return false;
        }
        return true;
    }

    private static double edgeSlip(double center, double min, double max, double angle) {
        if (Math.abs(angle) < .18) return 0;
        double outside = center - Mth.clamp(center, min, max);
        return Mth.clamp(outside * .28, -.105, .105);
    }

    private static double limitAngle(double angle, double rate, double range) {
        if (angle > range && rate > 0 || angle < -range && rate < 0) return angle;
        return Mth.clamp(angle + rate * DT, Math.min(angle, -range), Math.max(angle, range));
    }

    private static BlockHitResult surface(VehicleEntity vehicle, double x, double z, double foot,
                                  double ceiling, double distance) {
        var hit = vehicle.level().clip(new ClipContext(new Vec3(x, foot + ceiling, z),
                new Vec3(x, foot - distance, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, vehicle));
        return hit.getType() == HitResult.Type.BLOCK && !hit.isInside() && hit.getDirection() == Direction.UP ? hit : null;
    }

    private static AABB hull(VehicleEntity vehicle, VehicleProfile model, double cosYaw, double sinYaw) {
        double wx = model.width * (model.air() ? .12 : .25);
        double zz = model.length * (model.air() ? .21 : .25);
        double x = Math.abs(cosYaw) * wx + Math.abs(sinYaw) * zz;
        double z = Math.abs(sinYaw) * wx + Math.abs(cosYaw) * zz;
        double bottom = model.kind == VehicleProfile.Kind.STATIC ? .04 : model.air() ? .065 : .34;
        double y = vehicle.getY();
        return new AABB(vehicle.getX() - x, y + bottom, vehicle.getZ() - z,
                vehicle.getX() + x, y + Math.max(bottom + .15, model.height * .84),
                vehicle.getZ() + z);
    }
}
