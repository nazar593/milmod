package dev.milmod.entity;

import dev.milmod.model.VehicleProfile;
import dev.milmod.model.DroneSpec;
import dev.milmod.model.VehicleSuspension;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import dev.milmod.model.VehicleHandling;
import dev.milmod.model.WingProfile;
import dev.milmod.sound.ModSounds;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

final class VehicleMotion {
    private VehicleMotion() {}

    private static final int SUBSTEPS = 3;
    private static final double DT = 1.0 / SUBSTEPS;
    private static final double GRAVITY = .026;

    static void launch(VehicleEntity vehicle, VehicleEntity stand) {
        DroneSpec spec = DroneSpec.of(vehicle.profile());
        // Стабильный вертикальный взлет для PAD и квадрокоптеров без завала
        if (spec.launch() == DroneSpec.Launch.PAD || spec.engine() == DroneSpec.Engine.ELECTRIC) {
            double speed = Math.min(.32, Math.max(.08, vehicle.getDeltaMovement().y) + .042 * Math.max(.45, vehicle.power()));
            vehicle.setXRot(Mth.approach(vehicle.getXRot(), 0F, 4F));
            vehicle.moveVehicle(new Vec3(0, speed, 0));
            if (vehicle.verticalCollision) vehicle.abortLaunch(stand);
            else {
                vehicle.advanceLaunch(speed);
                vehicle.setActivity(true, 0);
                if (vehicle.launchDistance() >= Math.max(.85, spec.railTravel()) && speed >= Math.min(.20, spec.liftOff())) {
                    vehicle.finishLaunch();
                }
            }
            return;
        }
        // Плавный вертикально-наклонный старт с катапульты/рампы без сваливания
        double speed = vehicle.getDeltaMovement().horizontalDistance();
        float startingYaw = vehicle.getYRot();
        for (int i = 0; i < SUBSTEPS; i++) {
            double drive = spec.thrust() / spec.mass() * Math.max(.65, vehicle.power());
            speed += (drive - .07 * speed * speed) * DT;
            Vec3 segment = forward(startingYaw, speed * DT).add(0, Math.max(.06 * DT, speed * DT * .26), 0);
            if (!vehicle.level().hasChunkAt(BlockPos.containing(vehicle.position().add(segment)))
                    || obstructed(vehicle, vehicle.position().add(0, .22, 0), segment)) {
                vehicle.abortLaunch(stand);
                return;
            }
            vehicle.moveVehicle(segment);
            if (vehicle.horizontalCollision || vehicle.verticalCollision) {
                vehicle.abortLaunch(stand);
                return;
            }
            vehicle.advanceLaunch(segment.horizontalDistance());
        }
        Vec3 along = forward(startingYaw, speed);
        vehicle.setXRot(Mth.approach(vehicle.getXRot(), 14F, 3F));
        vehicle.setDeltaMovement(along.add(0, Math.max(.14, speed * .26), 0));
        vehicle.setActivity(true, 0);
        if (vehicle.launchDistance() >= 1.42 && speed >= spec.liftOff()) vehicle.finishLaunch();
    }

    static void step(VehicleEntity vehicle, Player pilot) {
        VehicleProfile profile = vehicle.profile();
        Vec3 last = vehicle.getDeltaMovement();
        if (profile.kind == VehicleProfile.Kind.STATIC) {
            if (VehicleSuspension.of(profile).size() > 0) {
                double drag = vehicle.onGround() ? .69 : .97;
                vehicle.moveSupported(last.x * drag, last.z * drag, false, 0);
            } else if (!vehicle.isNoGravity() && !vehicle.onGround()) {
                vehicle.moveVehicle(new Vec3(0, Math.max(-0.65, last.y - 0.06), 0));
            }
            return;
        }
        if (vehicle.damageMask() != 0 && profile.air()) {
            damagedFlight(vehicle, last);
        } else if (!vehicle.started()) {
            double drag = vehicle.onGround() ? .77 : .994;
            vehicle.moveSupported(last.x * drag, last.z * drag, false, 0);
        } else if (profile.air()) {
            flight(vehicle, pilot, last);
        } else {
            drive(vehicle, pilot, last);
        }
    }

    private static void drive(VehicleEntity vehicle, Player pilot, Vec3 last) {
        VehicleProfile profile = vehicle.profile();
        VehicleHandling handling = VehicleHandling.of(profile);
        boolean contact = vehicle.onGround();
        double grip = vehicle.groundGrip();
        float steering = pilot == null ? 0 : vehicle.pilotSide(pilot);
        float input = pilot == null ? 0 : vehicle.pilotForward(pilot);
        double horizontal = last.horizontalDistance();
        if (contact) {
            float steeringFactor = handling == VehicleHandling.TRACKS ? 1 :
                    (float) Mth.clamp(horizontal / (profile.speed * 0.4), 0, 1);
            double yaw = Math.toRadians(vehicle.getYRot());
            double forwardSpeed = -last.x * Math.sin(yaw) + last.z * Math.cos(yaw);
            int direction = handling != VehicleHandling.TRACKS && forwardSpeed < -.005 ? -1 : 1;
            float turned = Mth.wrapDegrees(vehicle.getYRot() - steering * handling.turn * steeringFactor
                    * direction * (float) grip);
            if (ChassisCollision.canTurn(vehicle, vehicle.getYRot(), turned)) vehicle.setYRot(turned);
        }
        double speed = Math.max(-handling.reverse, input) * profile.speed;
        double heading = Math.toRadians(vehicle.getYRot());
        double sin = Math.sin(heading), cos = Math.cos(heading);
        double advance = -last.x * sin + last.z * cos;
        double slide = last.x * cos + last.z * sin;
        if (contact) {
            advance = input == 0 ? advance * (1 - (1 - handling.coast) * grip)
                    : Mth.lerp(Math.min(1, handling.response * grip), advance, speed);
            slide *= 1 - grip * (handling == VehicleHandling.TRACKS ? .62 : .46);
        } else {
            advance *= .995;
            slide *= .995;
        }
        double x = -advance * sin + slide * cos;
        double z = advance * cos + slide * sin;
        vehicle.setSteering(steering);
        vehicle.moveSupported(Math.abs(x) < .001 ? 0 : x, Math.abs(z) < .001 ? 0 : z,
                Math.abs(input) > .04 || horizontal > .07 || contact && Math.abs(steering) > .04, 0);
    }

    private static void flight(VehicleEntity vehicle, Player pilot, Vec3 velocity) {
        DroneSpec spec = DroneSpec.of(vehicle.profile());
        if (spec.engine() == DroneSpec.Engine.ELECTRIC) {
            hover(vehicle, pilot, velocity, spec);
            return;
        }
        float power = vehicle.power();
        boolean grounded = vehicle.onGround();
        float yaw = vehicle.getYRot();
        float bank = vehicle.authoritativeBank();
        float pitch = vehicle.getXRot();
        float yawError = Mth.wrapDegrees(vehicle.heldYaw() - yaw);
        double speed = velocity.horizontalDistance();

        // Уверенный вертикальный/короткий отрыв с земли
        if (grounded) {
            yaw = Mth.wrapDegrees(yaw + Mth.clamp(yawError * .22F, -3.0F, 3.0F));
            vehicle.setYRot(yaw);
            if (power < .35F) {
                vehicle.moveSupported(0, 0, power > .01, 0);
                return;
            }
            double next = Mth.clamp(speed + spec.thrust() / spec.mass() * power, spec.liftOff() * .6, spec.maximum());
            Vec3 along = forward(yaw, next * .45);
            vehicle.setXRot(Mth.approach(vehicle.getXRot(), 12F, 3.0F));
            vehicle.clearSupport();
            vehicle.setActivity(true, 0);
            vehicle.moveVehicle(along.add(0, .24, 0));
            vehicle.setDeltaMovement(along.add(0, .24, 0));
            return;
        }

        float side = pilot == null ? 0 : vehicle.pilotSide(pilot);
        float targetBank = Mth.clamp(-yawError * .78F - side * spec.bank(), -spec.bank(), spec.bank());
        // Расширенный угол тангажа: до -78 градусов для крутого пикирования носом в цель
        float targetPitch = Mth.clamp(-vehicle.heldPitch(), -78, 35);
        double ceiling = Math.min(vehicle.baseAltitude() + spec.ceiling(), vehicle.level().getMaxBuildHeight() - 8);
        if (vehicle.getY() >= ceiling) targetPitch = Math.min(targetPitch, -7);
        if (power < .03F && speed < spec.cruise() * .87) targetPitch = Math.min(targetPitch, -10);

        boolean diving = targetPitch < -18F || pitch < -18F;
        double airDensity = Math.exp(-Math.max(0, vehicle.getY() - vehicle.level().getSeaLevel()) / 8000);
        WingProfile wing = WingProfile.of(vehicle.profile());
        double parasiticDrag = 2 * spec.flightThrust() / (wing.area() * spec.flightMaximum() * spec.flightMaximum());
        vehicle.setControlSurfaces(targetBank / spec.bank(),
                Mth.clamp((targetPitch - pitch) / 20F, -1F, 1F), Mth.clamp(yawError / 32F, -1F, 1F));

        for (int i = 0; i < SUBSTEPS; i++) {
            bank = Mth.approach(bank, targetBank, (float) (spec.rollRate() * 1.25 * DT));
            pitch = Mth.approach(pitch, targetPitch, (float) ((diving ? 5.2 : 3.2) * DT));
            double turn = Math.min(spec.yawRate() * 1.15, 0.72 / Math.max(.14, speed))
                    * Math.min(1, Math.max(.35, speed / spec.liftOff()));
            yaw = Mth.wrapDegrees(yaw - (float) (bank / spec.bank() * turn * DT));

            double angle = Math.toRadians(pitch);
            if (diving) {
                // Четкое пикирование вдоль оси носа без раскачки и сваливания
                double diveSpeed = Mth.clamp(velocity.length() + (spec.flightThrust() / spec.mass() * Math.max(.5, power) + GRAVITY * 1.45) * DT,
                        spec.cruise(), spec.flightMaximum() * 1.45);
                Vec3 noseDir = forward(yaw, Math.cos(angle)).add(0, Math.sin(angle), 0).normalize();
                velocity = velocity.lerp(noseDir.scale(diveSpeed), 0.34 * DT * SUBSTEPS);
                velocity = new Vec3(velocity.x, Mth.clamp(velocity.y, -1.65, .32), velocity.z);
            } else {
                double horizontal = velocity.horizontalDistance();
                double flowAngle = Math.atan2(velocity.y, Math.max(.04, horizontal));
                double attack = angle - flowAngle + .09;
                double cl = wing.liftCoefficient(attack);
                double pressure = .5 * airDensity * velocity.lengthSqr();
                double lift = Mth.clamp(pressure * wing.area() * cl / spec.mass(), -GRAVITY, GRAVITY * 1.86);
                double drag = pressure * wing.area() * (parasiticDrag + wing.inducedDrag(cl)) / spec.mass();
                double thrust = spec.flightThrust() / spec.mass() * power;
                Vec3 thrustVector = forward(yaw, thrust * Math.cos(angle)).add(0, thrust * Math.sin(angle), 0);
                Vec3 airflow = velocity.normalize();
                Vec3 horizontalDirection = new Vec3(airflow.x, 0, airflow.z).normalize();
                if (horizontalDirection.lengthSqr() == 0) horizontalDirection = forward(yaw, 1);
                Vec3 liftDirection = new Vec3(-horizontalDirection.x * Math.sin(flowAngle), Math.cos(flowAngle),
                        -horizontalDirection.z * Math.sin(flowAngle));
                double bankAngle = Math.toRadians(bank);
                Vec3 liftVector = liftDirection.scale(lift * Math.cos(bankAngle))
                        .add(horizontalDirection.z * lift * Math.sin(bankAngle), 0,
                                -horizontalDirection.x * lift * Math.sin(bankAngle));
                Vec3 acceleration = thrustVector.add(liftVector).subtract(airflow.scale(drag)).add(0, -GRAVITY, 0);
                velocity = velocity.add(acceleration.scale(DT));
                velocity = new Vec3(velocity.x, Mth.clamp(velocity.y, -.85, .42), velocity.z);
                double airspeed = velocity.length();
                if (airspeed > spec.flightMaximum()) velocity = velocity.scale(spec.flightMaximum() / airspeed);
            }

            speed = velocity.horizontalDistance();
            vehicle.setYRot(yaw);
            vehicle.setXRot(pitch);
            vehicle.setActivity(power > .01, bank);
            if (moveAir(vehicle, velocity, DT)) return;
        }
        vehicle.setDeltaMovement(velocity);
    }

    private static void hover(VehicleEntity vehicle, Player pilot, Vec3 last, DroneSpec spec) {
        float yawError = Mth.wrapDegrees(vehicle.heldYaw() - vehicle.getYRot());
        vehicle.setYRot(Mth.wrapDegrees(vehicle.getYRot() + Mth.clamp(yawError, -spec.yawRate(), spec.yawRate())));
        float desired = Mth.clamp(-vehicle.heldPitch(), -35, 35);
        float side = pilot == null ? 0 : vehicle.pilotSide(pilot);
        float power = vehicle.power();
        double speed = spec.flightMaximum() * power;
        double forward = vehicle.onMission() ? .74 : Math.max(0, -.025 * desired);
        Vec3 along = forward(vehicle.getYRot(), forward * speed).add(forward(vehicle.getYRot() - 90, side * speed * .45));
        double ceiling = Math.min(vehicle.baseAltitude() + spec.ceiling(), vehicle.level().getMaxBuildHeight() - 5);
        double vy = power > 0 ? Mth.clamp(vehicle.onMission() ? (vehicle.baseAltitude() + spec.missionHeight() - vehicle.getY()) * .06
                : power < .18 ? -.10 : Math.max(0, desired) * .009
                        - (desired < 0 ? Math.min(.09, -desired * .004) : 0), -.28, vehicle.getY() >= ceiling ? 0 : .28) : Math.max(-.42, last.y - GRAVITY);
        vehicle.setXRot(Mth.approach(vehicle.getXRot(), desired * .51F, 2.5F));
        vehicle.setActivity(power > .01, Mth.approach(vehicle.authoritativeBank(), -side * spec.bank(), 2.5F));
        Vec3 next = new Vec3(Mth.lerp(.20, last.x, along.x), Mth.lerp(.26, last.y, vy),
                Mth.lerp(.20, last.z, along.z));
        if (vehicle.onGround() && next.y <= 0) {
            vehicle.moveSupported(0, 0, power > .01, 0);
            return;
        }
        for (int i = 0; i < SUBSTEPS; i++) if (moveAir(vehicle, next, DT)) return;
        vehicle.setDeltaMovement(next);
    }

    private static boolean moveAir(VehicleEntity vehicle, Vec3 velocity, double dt) {
        Vec3 movement = velocity.scale(dt);
        if (!vehicle.level().hasChunkAt(BlockPos.containing(vehicle.position().add(movement)))) {
            vehicle.setDeltaMovement(velocity);
            return true;
        }
        boolean contactBurst = vehicle.profile().crashEffect() > 0;
        if (!vehicle.onGround() && (contactBurst || velocity.lengthSqr() >= .035) && airContact(vehicle, movement)) return true;
        if (contactBurst && !vehicle.onGround()) {
            Vec3 clipped = net.minecraft.world.entity.Entity.collideBoundingBox(null, movement,
                    vehicle.getBoundingBox(), vehicle.level(), java.util.List.of());
            if (clipped.distanceToSqr(movement) > 1.0E-8) {
                vehicle.crashAt(vehicle.position().add(clipped));
                return true;
            }
        }
        if (!contactBurst && velocity.y < 0 && obstructed(vehicle, vehicle.position().add(0, .08, 0),
                new Vec3(0, movement.y - .10, 0))) {
            vehicle.setDeltaMovement(velocity);
            vehicle.moveSupported(velocity.x, velocity.z, vehicle.power() > .01, 0);
            return true;
        }
        vehicle.moveVehicle(movement);
        if (vehicle.horizontalCollision || vehicle.verticalCollision) {
            vehicle.setDeltaMovement(new Vec3(vehicle.horizontalCollision ? 0 : velocity.x,
                    vehicle.verticalCollision ? 0 : velocity.y, vehicle.horizontalCollision ? 0 : velocity.z));
            return true;
        }
        return false;
    }

    private static boolean airContact(VehicleEntity vehicle, Vec3 movement) {
        VehicleProfile model = vehicle.profile();
        double angle = Math.toRadians(vehicle.getYRot());
        double pitch = Math.toRadians(vehicle.getXRot());
        double bank = Math.toRadians(vehicle.authoritativeBank());
        Vec3 nose = vehicle.position().add(0, model.height * .45, 0)
                .add(forward(vehicle.getYRot(), model.length * .47))
                .add(0, Math.sin(pitch) * model.length * .43, 0);
        BlockHitResult noseHit = blockHit(vehicle, nose, movement);
        if (noseHit.getType() == HitResult.Type.BLOCK) {
            vehicle.crashAt(impactPoint(noseHit));
            return true;
        }
        if (model.kind != VehicleProfile.Kind.PLANE) return false;
        double span = model.width * .43;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 wing = vehicle.position().add(-Math.cos(angle) * span * side,
                    model.height * .46 + Math.sin(bank) * span * side, -Math.sin(angle) * span * side);
            BlockHitResult wingHit = blockHit(vehicle, wing, movement);
            if (wingHit.getType() == HitResult.Type.BLOCK) {
                if (model.crashEffect() > 0) {
                    vehicle.crashAt(impactPoint(wingHit));
                    return true;
                }
                vehicle.loseWing(side < 0 ? 1 : 2);
                vehicle.moveVehicle(movement.scale(.75));
                return true;
            }
        }
        return false;
    }

    private static boolean obstructed(VehicleEntity vehicle, Vec3 point, Vec3 movement) {
        return blockHit(vehicle, point, movement).getType() == HitResult.Type.BLOCK;
    }

    private static BlockHitResult blockHit(VehicleEntity vehicle, Vec3 point, Vec3 movement) {
        return vehicle.level().clip(new ClipContext(point, point.add(movement),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, vehicle));
    }

    private static Vec3 impactPoint(BlockHitResult hit) {
        return hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.06));
    }

    private static void damagedFlight(VehicleEntity vehicle, Vec3 last) {
        int side = vehicle.damageMask() == 2 ? -1 : 1;
        vehicle.setYRot(Mth.wrapDegrees(vehicle.getYRot() + side * 16));
        vehicle.setXRot(Mth.approach(vehicle.getXRot(), -42, 5));
        vehicle.setActivity(false, side * 63);
        Vec3 turn = forward(vehicle.getYRot(), Math.min(0.30, last.horizontalDistance()));
        Vec3 motion = new Vec3(Mth.lerp(0.36, last.x, turn.x),
                Math.max(-0.78, last.y - 0.060), Mth.lerp(0.36, last.z, turn.z));
        if (vehicle.profile().crashEffect() > 0 && !vehicle.onGround()) moveAir(vehicle, motion, 1);
        else vehicle.moveVehicle(motion);
        if (vehicle.onGround()) {
            vehicle.ejectPassengers();
            vehicle.playSound(ModSounds.failure(vehicle.profile()), 1F, 0.72F);
            vehicle.discard();
        }
    }

    private static Vec3 forward(float yaw, double power) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians) * power, 0, Math.cos(radians) * power);
    }
}
