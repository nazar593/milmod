package dev.milmod.entity;

import dev.milmod.config.ModConfig;
import dev.milmod.network.DroneLink;
import java.util.ArrayList;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/** One authoritative entry point for MilMod's heavy in-game explosion. */
public final class HeavyExplosionManager {
    private static final int KAB_PLAYER_RADIUS = 15;

    private HeavyExplosionManager() {}

    public static void detonate(ServerLevel level, Vec3 pos, float yield, @Nullable Entity source) {
        detonate(level, pos, yield, source, false);
    }

    static void detonateKab(ServerLevel level, Vec3 pos, ImpactCharge source) {
        detonate(level, pos, 4F, source, true);
    }

    private static void detonate(ServerLevel level, Vec3 pos, float yield, @Nullable Entity source, boolean kab) {
        if (!Float.isFinite(yield) || yield <= 0 || !Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)
                || !level.hasChunkAt(BlockPos.containing(pos))) return;

        // Индивидуальный профиль БЧ для каждого дрона
        WarheadRegistry.WarheadProfile warhead = null;
        if (source instanceof VehicleEntity vehicle) {
            warhead = WarheadRegistry.get(String.valueOf(vehicle.profile()));
        }

        float rawStrength = warhead != null ? warhead.blastPower() : yield;
        float strength = Mth.clamp(rawStrength, .5F, 16F);
        boolean causesFire = warhead == null || warhead.causesFire();

        boolean terrain = ModConfig.CHARGE_BLOCK_DAMAGE.get();
        var type = terrain ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.KEEP;
        var blast = new Explosion(level, source, pos.x, pos.y, pos.z, strength, false, type);
        if (ForgeEventFactory.onExplosionStart(level, blast)) return;
        blast.explode();

        if (kab) {
            harmNearbyPlayers(level, blast, pos, source);
            if (terrain && source != null) KabGlassDamage.enqueue(level, pos, source);
        }

        Set<BlockPos> surfaces = terrain ? shapeTerrain(level, blast, pos, strength) : Set.of();
        blast.finalizeExplosion(false);
        if (causesFire) {
            igniteSurfaces(level, surfaces);
        }

        blast.getHitPlayers().forEach((player, motion) -> {
            if (player instanceof ServerPlayer server && server.connection != null) {
                server.connection.send(new ClientboundSetEntityMotionPacket(server.getId(), server.getDeltaMovement()));
            }
        });

        // Индивидуальные эффекты и звук под БЧ
        if (warhead != null) {
            spawnWarheadEffects(level, pos, warhead);
            playWarheadSound(level, pos, warhead);
        }

        FlashLight.place(level, pos, 15);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            FlashLight.place(level, pos.add(side.getStepX() * 1.8, .45, side.getStepZ() * 1.8), 12);
        }
        DroneLink.heavyExplosion(level, pos, strength);
    }

    private static void spawnWarheadEffects(ServerLevel level, Vec3 pos, WarheadRegistry.WarheadProfile warhead) {
        double spread = Math.max(0.6, warhead.blastPower() * 0.28);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 0.5, pos.z,
                Math.max(1, (int) (warhead.blastPower() / 3.5F)), spread * 0.5, 0.5, spread * 0.5, 0.05);
        level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.4, pos.z,
                warhead.sparkCount(), spread, spread * 0.7, spread, 0.18);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 1.0, pos.z,
                warhead.smokeCount(), spread * 1.2, spread * 0.9, spread * 1.2, 0.06);
    }

    private static void playWarheadSound(ServerLevel level, Vec3 pos, WarheadRegistry.WarheadProfile warhead) {
        ResourceLocation soundLoc = ResourceLocation.tryParse(warhead.soundId());
        if (soundLoc == null) return;
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(soundLoc);
        if (event != null) {
            level.playSound(null, pos.x, pos.y, pos.z, event, SoundSource.BLOCKS,
                    warhead.soundVolume(), warhead.soundPitch());
        }
    }

    private static void harmNearbyPlayers(ServerLevel level, Explosion blast, Vec3 center, @Nullable Entity source) {
        var bounds = new AABB(center, center).inflate(KAB_PLAYER_RADIUS);
        var players = new ArrayList<Entity>(level.getEntitiesOfClass(Player.class, bounds,
                player -> eligiblePlayer(player, center)));
        if (players.isEmpty()) return;
        var hit = new Explosion(level, source, center.x, center.y, center.z, 4F,
                false, Explosion.BlockInteraction.KEEP);
        ForgeEventFactory.onExplosionDetonate(level, hit, players, KAB_PLAYER_RADIUS);
        for (Entity entity : players) {
            if (entity instanceof Player player && eligiblePlayer(player, center)) {
                player.hurt(blast.getDamageSource(), 1_000F);
            }
        }
    }

    private static boolean eligiblePlayer(Player player, Vec3 center) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && player.position().distanceToSqr(center) <= KAB_PLAYER_RADIUS * KAB_PLAYER_RADIUS;
    }

    private static Set<BlockPos> shapeTerrain(ServerLevel level, Explosion blast, Vec3 center, float strength) {
        var blocks = blast.getToBlow();
        blocks.removeIf(pos -> outsideCrater(pos, center, strength));
        var candidates = new ArrayList<>(blocks);
        int debris = 6 + level.random.nextInt(7);
        for (int emitted = 0; emitted < debris && !candidates.isEmpty();) {
            BlockPos pos = candidates.remove(level.random.nextInt(candidates.size()));
            BlockState state = level.getBlockState(pos);
            if (!level.hasChunkAt(pos) || state.isAir() || !state.isSolidRender(level, pos)
                    || state.hasBlockEntity() || !state.getFluidState().isEmpty()) continue;
            var fragment = FallingBlockEntity.fall(level, pos, state);
            fragment.disableDrop();
            fragment.setSecondsOnFire(3);
            Vec3 offset = Vec3.atCenterOf(pos).subtract(center).multiply(1, 0, 1).normalize();
            fragment.setDeltaMovement(offset.scale(.20 + level.random.nextDouble() * .28)
                    .add(0, .30 + level.random.nextDouble() * .42, 0));
            emitted++;
        }
        var removed = new java.util.HashSet<>(blocks);
        var surfaces = new java.util.HashSet<BlockPos>();
        for (BlockPos broken : blocks) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos rim = broken.relative(direction);
                if (!removed.contains(rim) && level.hasChunkAt(rim)) surfaces.add(rim);
            }
            BlockPos bottom = broken.below();
            if (!removed.contains(bottom)) surfaces.add(bottom);
        }
        return surfaces;
    }

    private static void igniteSurfaces(ServerLevel level, Set<BlockPos> surfaces) {
        if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOFIRETICK)) return;
        int fires = 0;
        for (BlockPos surface : surfaces) {
            if (fires == 8) break;
            if (level.random.nextInt(7) != 0 || !level.hasChunkAt(surface)) continue;
            BlockPos upper = surface.above();
            if (level.getBlockState(upper).is(Blocks.AIR)
                    && level.getBlockState(surface).isFaceSturdy(level, surface, Direction.UP)
                    && Blocks.FIRE.defaultBlockState().canSurvive(level, upper)) {
                level.setBlockAndUpdate(upper, Blocks.FIRE.defaultBlockState());
                fires++;
            }
        }
    }

    private static boolean outsideCrater(BlockPos pos, Vec3 center, float strength) {
        Vec3 offset = Vec3.atCenterOf(pos).subtract(center);
        double angle = Math.atan2(offset.z, offset.x);
        double border = strength * (1.42 + .13 * Math.sin(angle * 5) + .11 * Math.cos(angle * 9));
        return offset.horizontalDistanceSqr() + offset.y * offset.y * 1.9 > border * border;
    }
}
