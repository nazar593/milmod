package dev.milmod.entity;

import dev.milmod.model.DroneSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** One Minecraft map marker. All distances are fictional gameplay tuning. */
public final class FlightMission {
    public enum Mode { LOITER, STRIKE }
    private Mode mode;
    private BlockPos marker;

    boolean active() { return mode != null; }
    Mode mode() { return mode; }

    boolean set(ServerLevel level, VehicleEntity craft, Mode next, BlockPos point) {
        if (next == null || point == null) return false;
        double range = craft.missionRange();
        double dx = point.getX() + .5 - craft.getX(), dz = point.getZ() + .5 - craft.getZ();
        if (!level.getWorldBorder().isWithinBounds(point) || dx * dx + dz * dz > range * range) return false;
        mode = next;
        marker = point.immutable();
        return true;
    }

    void clear() { mode = null; marker = null; }

    Vec3 direction(VehicleEntity craft) {
        Vec3 offset = new Vec3(marker.getX() + .5 - craft.getX(), 0, marker.getZ() + .5 - craft.getZ());
        double distance = offset.horizontalDistance();
        double arrival = DroneSpec.of(craft.profile()).waypointRadius();
        if (mode == Mode.STRIKE) {
            if (craft.flightPhase() == VehicleEntity.FlightPhase.AIRBORNE && distance <= arrival) {
                if (craft.level() instanceof ServerLevel server) {
                    // The mission marker triggers a visual cue only; it does not damage blocks or entities.
                    server.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                            craft.getX(), craft.getY(), craft.getZ(), 1, 0, 0, 0, 0);
                }
                clear();
                return Vec3.ZERO;
            }
            return offset;
        }
        double ring = Math.max(22, arrival * 2);
        if (distance > ring * 1.55) return offset;
        if (distance < .01) return new Vec3(1, 0, 0);
        Vec3 inward = offset.scale(1 / distance);
        return new Vec3(-inward.z, 0, inward.x).add(inward.scale((distance - ring) / ring));
    }

    void save(CompoundTag tag) {
        if (!active()) return;
        tag.putString("MissionMode", mode.name());
        tag.putLong("MissionMarker", marker.asLong());
    }

    void load(CompoundTag tag) {
        clear();
        if (!tag.contains("MissionMarker", Tag.TAG_LONG)) return;
        try {
            mode = Mode.valueOf(tag.getString("MissionMode"));
            marker = BlockPos.of(tag.getLong("MissionMarker"));
        } catch (IllegalArgumentException ignored) {
            clear();
        }
    }
}
