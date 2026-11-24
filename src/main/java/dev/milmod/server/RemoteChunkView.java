package dev.milmod.server;

import dev.milmod.MilMod;
import dev.milmod.entity.VehicleEntity;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MilMod.ID)
public final class RemoteChunkView {
    private static final TicketType<UUID> OPERATOR_BODY =
            TicketType.create("milmod_operator_body", Comparator.<UUID>naturalOrder(), 30);

    private static final TicketType<UUID> CAMERA_FLIGHT =
            TicketType.create("milmod_camera_flight", Comparator.<UUID>naturalOrder(), 40);

    private RemoteChunkView() {}

    private static VehicleEntity camera(ServerPlayer player) {
        VehicleEntity vehicle = VehicleEntity.controlledBy(player);
        return vehicle != null && vehicle.remotelyControlledBy(player) ? vehicle : null;
    }

    public static Vec3 position(ServerPlayer player) {
        VehicleEntity camera = camera(player);
        return camera == null ? player.position() : camera.position();
    }

    public static SectionPos section(EntityAccess entity) {
        if (entity instanceof ServerPlayer player) {
            VehicleEntity camera = camera(player);
            if (camera != null) {
                // Preserve the body's vertical section. Changing flight altitude alone needn't refresh tracking.
                return SectionPos.of(camera.chunkPosition(), SectionPos.blockToSectionCoord(player.getBlockY()));
            }
        }
        return SectionPos.of(entity);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player instanceof net.minecraftforge.common.util.FakePlayer) return;
        var chunks = player.serverLevel().getChunkSource();
        VehicleEntity camera = camera(player);
        SectionPos target = section(player);
        if (camera != null && (player.tickCount % 5 == 0 || !target.equals(player.getLastSectionPos()))) {
            keepCameraFlightLoaded(player, camera);
        }
        if (!target.equals(player.getLastSectionPos())) chunks.move(player);
    }

    public static void keepCameraFlightLoaded(ServerPlayer player, VehicleEntity camera) {
        var chunks = player.serverLevel().getChunkSource();
        UUID owner = player.getUUID();
        // Bounded, expiring tickets. The camera region ticks even with a very short simulation distance.
        chunks.addRegionTicket(OPERATOR_BODY, player.chunkPosition(), 2, owner);
        ChunkPos current = camera.chunkPosition();
        chunks.addRegionTicket(CAMERA_FLIGHT, current, 2, owner);
        Vec3 predicted = camera.position().add(camera.getDeltaMovement().scale(24));
        ChunkPos ahead = new ChunkPos(net.minecraft.core.BlockPos.containing(predicted));
        if (!ahead.equals(current)) chunks.addRegionTicket(CAMERA_FLIGHT, ahead, 2, owner);
    }
}
