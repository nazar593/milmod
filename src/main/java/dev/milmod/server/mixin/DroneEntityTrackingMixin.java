package dev.milmod.server.mixin;

import dev.milmod.server.RemoteChunkView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Group;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class DroneEntityTrackingMixin {
    @Group(name = "trackingOrigin", min = 1, max = 1)
    @Redirect(method = {"updatePlayer", "m_140497_"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"),
            remap = false, require = 0)
    private Vec3 milmod$trackingOriginDev(ServerPlayer player) {
        return RemoteChunkView.position(player);
    }

    @Group(name = "trackingOrigin", min = 1, max = 1)
    @Redirect(method = {"updatePlayer", "m_140497_"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;m_20182_()Lnet/minecraft/world/phys/Vec3;"),
            remap = false, require = 0)
    private Vec3 milmod$trackingOriginSrg(ServerPlayer player) {
        return RemoteChunkView.position(player);
    }
}
