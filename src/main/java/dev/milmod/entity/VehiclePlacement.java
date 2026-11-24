package dev.milmod.entity;

import dev.milmod.config.ModConfig;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class VehiclePlacement {
    private VehiclePlacement() {}

    public static boolean add(ServerLevel level, VehicleEntity vehicle, Player player) {
        return add(level, vehicle, player, null);
    }

    static boolean add(ServerLevel level, VehicleEntity vehicle, Player player, VehicleEntity dock) {
        if (!vehicle.profile().available()) return false;
        if (dock == null ? !level.noCollision(vehicle)
                : level.getBlockCollisions(vehicle, vehicle.getBoundingBox()).iterator().hasNext()
                  || !level.getEntities(vehicle, vehicle.getBoundingBox(),
                          entity -> entity != dock && entity.canBeCollidedWith()).isEmpty()) return false;
        UUID owner = player == null ? null : player.getUUID();
        if (vehicle.profile().air()) {
            int total = 0;
            int owned = 0;
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof VehicleEntity other) || !other.profile().air() || !other.isAlive() || other.fragmentPart() != 0) continue;
                if (++total >= ModConfig.WORLD_DRONE_LIMIT.get()) return false;
                if (owner != null && owner.equals(other.owner()) && ++owned >= ModConfig.PLAYER_DRONE_LIMIT.get()) {
                    return false;
                }
            }
        }
        vehicle.setOwner(owner);
        return level.addFreshEntity(vehicle);
    }

    public static boolean canPlaceDirectlyOnGround(String vehicleId) {
        return vehicleId != null && (vehicleId.contains("mavic") || vehicleId.contains("launcher"));
    }

}
