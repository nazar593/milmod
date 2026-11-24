package dev.milmod.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.milmod.MilMod;
import dev.milmod.config.ModConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid = MilMod.ID, value = Dist.CLIENT)
public final class BlastEffects {
    private static final int MAX_ACTIVE = 4;
    private static final int FLASH_TICKS = 12;
    private static final double LIGHT_RADIUS = 9.2;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final List<Flash> ACTIVE = new ArrayList<>(MAX_ACTIVE);
    private static final DustColorTransitionOptions EMBER = new DustColorTransitionOptions(
            new Vector3f(1F, 0.52F, 0.13F), new Vector3f(0.28F, 0.19F, 0.14F), 1.25F);
    private static final DustColorTransitionOptions DUST = new DustColorTransitionOptions(
            new Vector3f(0.57F, 0.43F, 0.33F), new Vector3f(0.29F, 0.26F, 0.23F), 1.6F);
    private static ClientLevel activeLevel;
    private static long particleTick = -1;
    private static int emittedThisTick;

    private BlastEffects() {}

    public static void play(Vec3 center, boolean explosion) {
        play(center, explosion, false);
    }

    static void play(Vec3 center, boolean explosion, boolean heavy) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null || client.player == null) return;
        updateLevel(level);
        double distance = center.distanceToSqr(client.gameRenderer.getMainCamera().getPosition());
        if (distance > (heavy ? 160 * 160 : 96 * 96)) return;
        if (!heavy) GlowSprites.add(level, center, explosion ? GlowSprites.Style.FLASH : GlowSprites.Style.LAUNCH);
        GlowSprites.add(level, center, explosion ? GlowSprites.Style.HALO : GlowSprites.Style.LAUNCH_HALO);
        if (!heavy) SmokeClouds.burst(level, center, distance, explosion);
        if (explosion) GlowSprites.add(level, center, GlowSprites.Style.SHOCK);
        if (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.remove(0);
        boolean light = ModConfig.BLAST_LIGHT.get() && distance <= 56 * 56;
        double radius = explosion ? LIGHT_RADIUS : 4.5;
        ACTIVE.add(new Flash(center, level.getGameTime(), explosion, heavy,
                light ? illuminatedFaces(level, center, radius) : List.of()));
        if (!explosion || heavy) return;
        emitParticles(level, center, distance);
    }

    static int claimParticles(ClientLevel level, int requested) {
        updateLevel(level);
        long now = level.getGameTime();
        if (particleTick != now) {
            particleTick = now;
            emittedThisTick = 0;
        }
        int budget = Math.min(requested, Math.max(0, ModConfig.PARTICLE_BUDGET.get() - emittedThisTick));
        emittedThisTick += budget;
        return budget;
    }

    private static void emitParticles(ClientLevel level, Vec3 center, double distance) {
        int budget = claimParticles(level, distance < 20 * 20 ? 94 : distance < 38 * 38 ? 36 : 9);
        if (budget == 0) return;
        level.addParticle(ParticleTypes.FLASH, center.x, center.y, center.z, 0, 0, 0);
        var random = level.random;
        for (int i = 0; i < budget - 1; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            switch (i % 4) {
                case 0 -> level.addParticle(EMBER, center.x + dx * 0.38, center.y + 0.23, center.z + dz * 0.38,
                        dx * 0.25, 0.17 + random.nextDouble() * 0.17, dz * 0.25);
                case 1 -> level.addParticle(DUST, center.x + dx * 0.92, center.y + 0.08, center.z + dz * 0.92,
                        dx * 0.21, 0.026, dz * 0.21);
                case 2 -> level.addParticle(ParticleTypes.CRIT, center.x, center.y + .18, center.z,
                        dx * .25, .09 + random.nextDouble() * .25, dz * .25);
                default -> level.addParticle(ParticleTypes.FLAME,
                        center.x + dx * 0.78, center.y + 0.14 + random.nextDouble() * 0.50,
                        center.z + dz * 0.78, dx * 0.13, 0.09, dz * 0.13);
            }
        }
    }

    private static List<LitFace> illuminatedFaces(ClientLevel level, Vec3 impact, double radius) {
        List<LitFace> candidates = new ArrayList<>();
        BlockPos center = BlockPos.containing(impact);
        Vec3 source = impact.add(0, 0.18, 0);
        int extent = Mth.ceil(radius);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-extent, -extent, -extent),
                center.offset(extent, extent, extent))) {
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isSolidRender(level, pos)) continue;
            Vec3 from = Vec3.atCenterOf(pos);
            if (from.distanceToSqr(source) > (radius + 0.65) * (radius + 0.65)) continue;
            for (Direction face : DIRECTIONS) {
                Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
                Vec3 sample = from.add(normal.scale(0.502));
                Vec3 ray = source.subtract(sample);
                double distance = ray.length();
                if (distance >= radius || distance < 0.015) continue;
                float intensity = (float) (Math.max(0, ray.dot(normal) / distance) * Math.pow(1 - distance / radius, 1.35));
                if (intensity < 0.024 || level.getBlockState(pos.relative(face)).isSolidRender(level, pos.relative(face))) continue;
                candidates.add(new LitFace(pos.immutable(), face, intensity));
            }
        }
        candidates.sort(Comparator.comparingDouble(LitFace::intensity).reversed());
        List<LitFace> visible = new ArrayList<>();
        var viewer = Minecraft.getInstance().player;
        if (viewer == null) return visible;
        for (int index = 0; index < Math.min(210, candidates.size()) && visible.size() < 150; index++) {
            LitFace face = candidates.get(index);
            Vec3 target = Vec3.atCenterOf(face.position).add(Vec3.atLowerCornerOf(face.face.getNormal()).scale(0.52));
            HitResult obstruction = level.clip(new ClipContext(source, target,
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer));
            if (obstruction.getType() == HitResult.Type.MISS) visible.add(face);
        }
        return visible;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientLevel current = Minecraft.getInstance().level;
        updateLevel(current);
        if (current != null) ACTIVE.removeIf(effect -> current.getGameTime() - effect.born >= FLASH_TICKS);
    }

    private static void updateLevel(ClientLevel current) {
        if (current != activeLevel) {
            ACTIVE.clear();
            particleTick = -1;
            emittedThisTick = 0;
            activeLevel = current;
        }
    }

    @SubscribeEvent
    public static void shake(ViewportEvent.ComputeCameraAngles event) {
        if (!ModConfig.CAMERA_SHAKE.get() || activeLevel == null || activeLevel != Minecraft.getInstance().level) return;
        Vec3 viewer = event.getCamera().getPosition();
        for (Flash flash : ACTIVE) {
            if (!flash.explosion || flash.heavy) continue;
            float age = (float) (activeLevel.getGameTime() - flash.born + event.getPartialTick());
            float proximity = 1F - (float) Math.min(1, viewer.distanceTo(flash.center) / 20);
            float weight = proximity * Mth.clamp(1F - age / 8, 0F, 1F);
            event.setPitch(event.getPitch() + Mth.sin(age * 2.7F) * weight * 1.65F);
            event.setRoll(event.getRoll() + Mth.cos(age * 3.1F) * weight * 1.1F);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()
                || !ModConfig.BLAST_LIGHT.get() || activeLevel == null
                || activeLevel != Minecraft.getInstance().level) return;
        var stack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        stack.pushPose();
        stack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f pose = stack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Flash flash : ACTIVE) {
            float age = Math.max(0, activeLevel.getGameTime() - flash.born + event.getPartialTick());
            float life = flash.explosion ? 8 : 5;
            float exposure = (float) Math.exp(-age * .38) * Math.max(0, 1 - age / life);
            if (!flash.explosion) exposure *= .74F;
            for (LitFace face : flash.faces) {
                if (!activeLevel.getBlockState(face.position).isAir()) quad(buffer, pose, face, exposure);
            }
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var batch = buffer.endOrDiscardIfEmpty();
        if (batch != null) BufferUploader.drawWithShader(batch);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        stack.popPose();
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix, LitFace face, float exposure) {
        double x = face.position.getX(), y = face.position.getY(), z = face.position.getZ();
        double eps = 0.003;
        int alpha = Math.round(Mth.clamp(face.intensity * exposure * 1.16F, 0F, 1F) * 255);
        if (alpha <= 0) return;
        switch (face.face) {
            case UP -> surface(buffer, matrix, x, y + 1 + eps, z, 1, 0, 0, 0, 0, 1, alpha);
            case DOWN -> surface(buffer, matrix, x, y - eps, z, 1, 0, 0, 0, 0, 1, alpha);
            case NORTH -> surface(buffer, matrix, x, y, z - eps, 1, 0, 0, 0, 1, 0, alpha);
            case SOUTH -> surface(buffer, matrix, x, y, z + 1 + eps, 1, 0, 0, 0, 1, 0, alpha);
            case WEST -> surface(buffer, matrix, x - eps, y, z, 0, 1, 0, 0, 0, 1, alpha);
            case EAST -> surface(buffer, matrix, x + 1 + eps, y, z, 0, 1, 0, 0, 0, 1, alpha);
        }
    }

    private static void surface(BufferBuilder buffer, Matrix4f matrix, double x, double y, double z,
                                int ax, int ay, int az, int bx, int by, int bz, int alpha) {
        vertex(buffer, matrix, x, y, z, alpha);
        vertex(buffer, matrix, x + ax, y + ay, z + az, alpha);
        vertex(buffer, matrix, x + ax + bx, y + ay + by, z + az + bz, alpha);
        vertex(buffer, matrix, x + bx, y + by, z + bz, alpha);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, double x, double y, double z, int alpha) {
        buffer.vertex(matrix, (float) x, (float) y, (float) z).color(255, 139, 55, alpha).endVertex();
    }

    private record Flash(Vec3 center, long born, boolean explosion, boolean heavy, List<LitFace> faces) {}
    private record LitFace(BlockPos position, Direction face, float intensity) {}
}
