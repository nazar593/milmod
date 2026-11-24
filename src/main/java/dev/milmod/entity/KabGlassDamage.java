package dev.milmod.entity;

import dev.milmod.MilMod;
import dev.milmod.config.ModConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MilMod.ID)
public final class KabGlassDamage {
    static final int RADIUS = 110;
    private static final int MAX_JOBS = 16;
    private static final int CELLS_PER_TICK = 16_384;
    private static final int BREAKS_PER_TICK = 128;
    private static final Map<ServerLevel, ArrayDeque<Scan>> JOBS = new HashMap<>();

    private KabGlassDamage() {}

    static void enqueue(ServerLevel level, Vec3 center, Entity source) {
        var jobs = JOBS.computeIfAbsent(level, key -> new ArrayDeque<>());
        if (jobs.size() >= MAX_JOBS) return;
        var sections = new ArrayDeque<Section>();
        int minChunkX = Mth.floor(center.x - RADIUS) >> 4;
        int maxChunkX = Mth.floor(center.x + RADIUS) >> 4;
        int minChunkZ = Mth.floor(center.z - RADIUS) >> 4;
        int maxChunkZ = Mth.floor(center.z + RADIUS) >> 4;
        int minY = Math.max(level.getMinSection(), Mth.floor(center.y - RADIUS) >> 4);
        int maxY = Math.min(level.getMaxSection() - 1, Mth.floor(center.y + RADIUS) >> 4);
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (int y = minY; y <= maxY; y++) {
                    var section = chunk.getSection(y - level.getMinSection());
                    if (!section.hasOnlyAir() && section.maybeHas(KabGlassDamage::isGlass)) {
                        sections.addLast(new Section(x, y, z));
                    }
                }
            }
        }
        if (!sections.isEmpty()) jobs.addLast(new Scan(center, source, sections));
        else if (jobs.isEmpty()) JOBS.remove(level);
    }

    private static boolean isGlass(BlockState state) {
        return state.is(Tags.Blocks.GLASS) || state.is(Tags.Blocks.GLASS_PANES) || state.is(Blocks.TINTED_GLASS);
    }

    private static boolean inRange(Vec3 center, BlockPos pos) {
        return center.distanceToSqr(Vec3.atCenterOf(pos)) <= (double) RADIUS * RADIUS;
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) process(level);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) JOBS.remove(level);
    }

    static void process(ServerLevel level) {
        var jobs = JOBS.get(level);
        if (jobs == null) return;
        if (!ModConfig.CHARGE_BLOCK_DAMAGE.get()) {
            JOBS.remove(level);
            return;
        }
        Scan job = jobs.removeFirst();
        var glass = job.collect(level);
        if (!glass.isEmpty()) {
            var wave = new Explosion(level, job.source, job.center.x, job.center.y, job.center.z,
                    4F, false, Explosion.BlockInteraction.DESTROY_WITH_DECAY, glass);
            ForgeEventFactory.onExplosionDetonate(level, wave, new ArrayList<>(), RADIUS);
            for (BlockPos block : wave.getToBlow()) {
                if (inRange(job.center, block) && level.hasChunkAt(block) && isGlass(level.getBlockState(block))) {
                    level.destroyBlock(block, false, job.source);
                }
            }
        }
        if (!job.sections.isEmpty()) jobs.addLast(job);
        if (jobs.isEmpty()) JOBS.remove(level);
    }

    private record Section(int x, int y, int z) {}

    private static final class Scan {
        private final Vec3 center;
        private final Entity source;
        private final ArrayDeque<Section> sections;
        private int cursor;

        Scan(Vec3 center, Entity source, ArrayDeque<Section> sections) {
            this.center = center;
            this.source = source;
            this.sections = sections;
        }

        ArrayList<BlockPos> collect(ServerLevel level) {
            var result = new ArrayList<BlockPos>();
            int budget = CELLS_PER_TICK;
            while (budget > 0 && result.size() < BREAKS_PER_TICK && !sections.isEmpty()) {
                Section next = sections.getFirst();
                var chunk = level.getChunkSource().getChunkNow(next.x, next.z);
                if (chunk == null) {
                    sections.removeFirst();
                    cursor = 0;
                    continue;
                }
                var section = chunk.getSection(next.y - level.getMinSection());
                int index = cursor++;
                budget--;
                int x = index & 15, z = index >> 4 & 15, y = index >> 8;
                if (isGlass(section.getBlockState(x, y, z))) {
                    BlockPos block = new BlockPos((next.x << 4) + x, (next.y << 4) + y, (next.z << 4) + z);
                    if (inRange(center, block)) result.add(block);
                }
                if (cursor == 4096) {
                    sections.removeFirst();
                    cursor = 0;
                }
            }
            return result;
        }
    }
}
