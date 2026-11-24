package dev.milmod.server.mixin;

import dev.milmod.server.RemoteChunkView;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Group;

@Mixin(ChunkMap.class)
abstract class DroneChunkMapMixin {
    // Both Forge production SRG names and the MDK development names are supported.
    @Group(name = "watchedSection", min = 3, max = 3)
    @Redirect(method = {"move", "m_140184_", "updatePlayerPos", "m_140373_", "updatePlayerStatus", "m_140192_"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"),
            remap = false, require = 0)
    private SectionPos milmod$watchedSectionDev(EntityAccess player) {
        return RemoteChunkView.section(player);
    }

    @Group(name = "watchedSection", min = 3, max = 3)
    @Redirect(method = {"move", "m_140184_", "updatePlayerPos", "m_140373_", "updatePlayerStatus", "m_140192_"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;m_235861_(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"),
            remap = false, require = 0)
    private SectionPos milmod$watchedSectionSrg(EntityAccess player) {
        return RemoteChunkView.section(player);
    }

    @Group(name = "watchedX", min = 2, max = 2)
    @Redirect(method = {"move", "m_140184_", "updatePlayerStatus", "m_140192_"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getBlockX()I"),
            remap = false, require = 0)
    private int milmod$watchedXDev(ServerPlayer player) {
        return RemoteChunkView.section(player).minBlockX();
    }

    @Group(name = "watchedX", min = 2, max = 2)
    @Redirect(method = {"move", "m_140184_", "updatePlayerStatus", "m_140192_"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;m_146903_()I"),
            remap = false, require = 0)
    private int milmod$watchedXSrg(ServerPlayer player) {
        return RemoteChunkView.section(player).minBlockX();
    }

    @Group(name = "watchedZ", min = 2, max = 2)
    @Redirect(method = {"move", "m_140184_", "updatePlayerStatus", "m_140192_"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getBlockZ()I"),
            remap = false, require = 0)
    private int milmod$watchedZDev(ServerPlayer player) {
        return RemoteChunkView.section(player).minBlockZ();
    }

    @Group(name = "watchedZ", min = 2, max = 2)
    @Redirect(method = {"move", "m_140184_", "updatePlayerStatus", "m_140192_"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;m_146907_()I"),
            remap = false, require = 0)
    private int milmod$watchedZSrg(ServerPlayer player) {
        return RemoteChunkView.section(player).minBlockZ();
    }
}
