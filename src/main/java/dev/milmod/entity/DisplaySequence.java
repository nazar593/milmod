package dev.milmod.entity;

import dev.milmod.network.DroneLink;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Iskander's in-game display animation and particle effect. */
public final class DisplaySequence {
    public static final int RANGE = 48;
    public static final int FIRST = 63;
    public static final int SECOND = 72;
    public static final int END = 134;
    private static final int FLIGHT = 36;
    private Vec3[] origins;
    private Vec3 marker;

    boolean begin(VehicleEntity vehicle, ServerLevel level, BlockPos point) {
        double dx = point.getX() + .5 - vehicle.getX(), dz = point.getZ() + .5 - vehicle.getZ();
        if (dx * dx + dz * dz > RANGE * RANGE || !level.getWorldBorder().isWithinBounds(point)
                || !level.hasChunkAt(point)) return false;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, point.getX(), point.getZ());
        marker = new Vec3(point.getX() + .5, y + .45, point.getZ() + .5);
        origins = new Vec3[2];
        return true;
    }

    boolean tick(ServerLevel level, VehicleEntity vehicle, int tick) {
        if (tick >= END) return false;
        trail(level, vehicle, tick - FIRST, 0);
        trail(level, vehicle, tick - SECOND, 1);
        return true;
    }

    private void trail(ServerLevel level, VehicleEntity vehicle, int elapsed, int index) {
        if (elapsed < 0 || elapsed > FLIGHT) return;
        if (elapsed == 0) {
            float yaw = (float) Math.toRadians(180 - vehicle.getYRot());
            origins[index] = vehicle.position().add(new Vec3(index == 0 ? -.49 : .49, 6.05, 2.94).yRot(yaw));
            DroneLink.exhaustFlash(level, origins[index]);
        }
        Vec3 origin = origins[index];
        if (origin == null) return;
        if (elapsed == FLIGHT) {
            level.sendParticles(ParticleTypes.END_ROD, marker.x, marker.y, marker.z, 19, .38, .34, .38, .08);
            return;
        }
        double t = elapsed / (double) FLIGHT;
        Vec3 position = origin.lerp(marker, t * t).add(0, Math.sin(Math.PI * t) * 11, 0);
        level.sendParticles(ParticleTypes.FIREWORK, position.x, position.y, position.z, 2, .04, .07, .04, .01);
        DroneLink.visualTrail(level, position);
    }

    public static float progress(float time, int begin, int finish) {
        float t = Mth.clamp((time - begin) / (finish - begin), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    public static float roof(float time) { return progress(time, 1, 19) * (1 - progress(time, 118, END)); }
    public static float doors(float time) { return progress(time, 14, 31) * (1 - progress(time, 113, 126)); }
    public static float lift(float time) { return progress(time, 31, 58) * (1 - progress(time, 103, 119)); }
}
