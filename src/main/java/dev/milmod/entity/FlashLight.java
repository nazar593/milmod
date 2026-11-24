package dev.milmod.entity;

import dev.milmod.config.ModConfig;
import dev.milmod.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A discrete vanilla block-light pulse, without a moving light source or volumetric scattering. */
public final class FlashLight extends LightBlock {
    public FlashLight(Properties properties) { super(properties); }

    public static void place(ServerLevel level, Vec3 point, int intensity) {
        if (!ModConfig.TEMPORARY_LIGHT.get()) return;
        BlockPos position = BlockPos.containing(point);
        if (!level.hasChunkAt(position) || level.isOutsideBuildHeight(position)
                || !level.getWorldBorder().isWithinBounds(position)) return;
        // Preserve fluids, replaceable plants, cave air and players' light blocks.
        if (!level.getBlockState(position).is(Blocks.AIR)) position = position.above();
        if (!level.isOutsideBuildHeight(position) && level.getBlockState(position).is(Blocks.AIR)) {
            level.setBlock(position, ModContent.FLASH_LIGHT.get().defaultBlockState()
                    .setValue(LEVEL, intensity), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos position, BlockState old, boolean moving) {
        super.onPlace(state, level, position, old, moving);
        if (!level.isClientSide && !old.is(this)) level.scheduleTick(position, this, 4);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
        // Vanilla persists scheduled block ticks. A replacement made meanwhile wins.
        if (level.getBlockState(position).is(this)) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
