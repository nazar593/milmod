package dev.milmod.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Контроллер траектории БПЛА:
 * 1) Плавный вертикальный взлет без кувырков (VTOL_CLIMB)
 * 2) Стабилизированный крейсерский полет с креном на виражах (CRUISE)
 * 3) Крутое пикирование носом вниз точно в цель без раскачки (TERMINAL_DIVE)
 */
public final class DroneFlightController {

    public enum Phase {
        GROUNDED,
        VTOL_CLIMB,
        CRUISE,
        TERMINAL_DIVE
    }

    public static final class FlightState {
        public Vec3 velocity;
        public float yaw;
        public float pitch;
        public float roll;
        public Phase phase;

        public FlightState(Vec3 velocity, float yaw, float pitch, float roll, Phase phase) {
            this.velocity = velocity;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.phase = phase;
        }
    }

    private DroneFlightController() {}

    public static FlightState step(
            Vec3 currentPos,
            Vec3 currentVel,
            Vec3 targetPos,
            float currentYaw,
            float currentPitch,
            float currentRoll,
            double cruiseAltitude,
            double cruiseSpeed,
            double diveSpeed,
            double diveStartDistance,
            boolean isQuadcopter
    ) {
        Vec3 toTarget = targetPos.subtract(currentPos);
        double horizDist = Math.hypot(toTarget.x, toTarget.z);
        double altError = cruiseAltitude - currentPos.y;

        // 1. Вертикальный взлет до безопасной высоты
        if (altError > 4.0 && horizDist > diveStartDistance) {
            double climbRate = isQuadcopter ? 0.42 : 0.55;
            double vy = Mth.clamp(altError * 0.12, 0.25, climbRate);
            double vx = Mth.lerp(0.15, currentVel.x, 0.0);
            double vz = Mth.lerp(0.15, currentVel.z, 0.0);

            float targetPitch = isQuadcopter ? 0.0f : -22.0f;
            float newPitch = Mth.lerp(0.12f, currentPitch, targetPitch);
            float newRoll = Mth.lerp(0.20f, currentRoll, 0.0f);

            return new FlightState(new Vec3(vx, vy, vz), currentYaw, newPitch, newRoll, Phase.VTOL_CLIMB);
        }

        // Целевой курс (yaw)
        float desiredYaw = (float) (Mth.atan2(-toTarget.x, toTarget.z) * (180.0 / Math.PI));
        float yawDiff = Mth.wrapDegrees(desiredYaw - currentYaw);

        // 2. Терминальное пикирование (как на референсе: крутой заход носом вниз в точку)
        if (!isQuadcopter && horizDist <= diveStartDistance) {
            Vec3 diveDir = toTarget.normalize();
            Vec3 desiredVel = diveDir.scale(diveSpeed);
            Vec3 newVel = currentVel.lerp(desiredVel, 0.28);

            float divePitch = (float) (-Math.toDegrees(Math.atan2(diveDir.y, Math.hypot(diveDir.x, diveDir.z))));
            divePitch = Mth.clamp(divePitch, 15.0f, 82.0f);

            float newYaw = currentYaw + Mth.clamp(yawDiff, -8.0f, 8.0f);
            float newPitch = Mth.lerp(0.22f, currentPitch, divePitch);
            // Плавный крен в сторону доворота на цель
            float targetRoll = Mth.clamp(yawDiff * 1.4f, -45.0f, 45.0f);
            float newRoll = Mth.lerp(0.18f, currentRoll, targetRoll);

            return new FlightState(newVel, newYaw, newPitch, newRoll, Phase.TERMINAL_DIVE);
        }

        // 3. Ровный крейсерский полет
        float maxTurnRate = isQuadcopter ? 10.0f : 4.5f;
        float turnStep = Mth.clamp(yawDiff, -maxTurnRate, maxTurnRate);
        float newYaw = currentYaw + turnStep;

        double rad = Math.toRadians(newYaw);
        double dirX = -Math.sin(rad);
        double dirZ = Math.cos(rad);
        double vy = Mth.clamp(altError * 0.08, -0.35, 0.35);

        Vec3 desiredVel = new Vec3(dirX * cruiseSpeed, vy, dirZ * cruiseSpeed);
        Vec3 newVel = currentVel.lerp(desiredVel, 0.18);

        float targetPitch = (float) Mth.clamp(-vy * 28.0, -18.0, 18.0);
        float newPitch = Mth.lerp(0.15f, currentPitch, targetPitch);

        float targetRoll = isQuadcopter ? 0.0f : Mth.clamp(turnStep * 7.5f, -35.0f, 35.0f);
        float newRoll = Mth.lerp(0.15f, currentRoll, targetRoll);

        return new FlightState(newVel, newYaw, newPitch, newRoll, Phase.CRUISE);
    }
}
