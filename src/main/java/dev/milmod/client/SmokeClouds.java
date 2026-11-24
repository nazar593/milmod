package dev.milmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.milmod.MilMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Distance-sorted smoke with separate rising and near-ground layers. */
@Mod.EventBusSubscriber(modid = MilMod.ID, value = Dist.CLIENT)
public final class SmokeClouds {
    private static final int MAX_CLOUDS = 160;
    private static final Cloud[] POOL = new Cloud[MAX_CLOUDS];
    private static final ResourceLocation TEXTURE = ResourceLocation.tryBuild(MilMod.ID, "textures/effect/smoke.png");
    private static ClientLevel level;
    private static int active;

    static {
        for (int i = 0; i < MAX_CLOUDS; i++) POOL[i] = new Cloud();
    }

    private SmokeClouds() {}

    public static void burst(ClientLevel world, Vec3 center, double distance, boolean explosion) {
        int detail = distance < 24 * 24 ? 1 : distance < 54 * 54 ? 2 : 4;
        int count = BlastEffects.claimParticles(world, (explosion ? 36 : 12) / detail);
        var random = world.random;
        for (int i = 0; i < count; i++) {
            boolean ground = i % 3 == 0;
            double angle = random.nextDouble() * Math.PI * 2;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            double spread = (ground ? .10 : .041) * (explosion ? 1 : .67);
            double rise = ground ? .009 + random.nextDouble() * .006 : .040 + random.nextDouble() * .050;
            int shade = explosion ? ground ? 52 : 34 : 119;
            int duration = explosion ? 100 + random.nextInt(76) : 55 + random.nextInt(35);
            Vec3 start = center.add(dx * random.nextDouble() * .78, ground ? .13 : .46, dz * random.nextDouble() * .78);
            add(world, start, new Vec3(dx * spread, rise, dz * spread),
                    .37F + random.nextFloat() * .54F, explosion ? (ground ? 2.35F : 2.9F) : 1.55F,
                    duration, shade, ground ? .56F : .77F);
        }
    }

    public static void trail(Vec3 center) {
        var client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null || center.distanceToSqr(client.gameRenderer.getMainCamera().getPosition()) > 96 * 96
                || BlastEffects.claimParticles(world, 1) == 0) return;
        add(world, center, new Vec3(0, .007, 0), .24F, .90F, 90, 146, .53F);
    }

    private static void add(ClientLevel world, Vec3 origin, Vec3 motion, float size, float growth,
                            int life, int shade, float opacity) {
        updateLevel(world);
        long now = world.getGameTime();
        for (Cloud cloud : POOL) {
            if (cloud.origin != null && now - cloud.born < cloud.life) continue;
            if (cloud.origin == null) active++;
            cloud.origin = origin;
            cloud.motion = motion;
            cloud.born = now;
            cloud.life = life;
            cloud.size = size;
            cloud.growth = growth;
            cloud.shade = shade;
            cloud.opacity = opacity;
            cloud.angle = world.random.nextFloat() * Mth.TWO_PI;
            cloud.variant = world.random.nextInt(4);
            return;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        updateLevel(Minecraft.getInstance().level);
        if (active == 0) return;
        long now = level.getGameTime();
        for (Cloud cloud : POOL) {
            if (cloud.origin != null && now - cloud.born >= cloud.life) {
                cloud.origin = null;
                active--;
            }
        }
    }

    private static void updateLevel(ClientLevel next) {
        if (next == level) return;
        level = next;
        active = 0;
        for (Cloud cloud : POOL) cloud.origin = null;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || active == 0) return;
        var camera = event.getCamera();
        var rotation = camera.rotation();
        var viewer = camera.getPosition();
        var frustum = event.getFrustum();
        var right = new Vector3f(1, 0, 0).rotate(rotation);
        var up = new Vector3f(0, 1, 0).rotate(rotation);
        Matrix4f pose = event.getPoseStack().last().pose();
        double now = level.getGameTime() + event.getPartialTick();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean submitted = false;
        for (Cloud cloud : POOL) {
            if (cloud.origin == null) continue;
            float age = (float) (now - cloud.born);
            if (age < 0 || age >= cloud.life) continue;
            float progress = age / cloud.life;
            double drift = 21 * (1 - Math.exp(-age / 21.0));
            double wind = .5 + .5 * Math.sin(cloud.born * .018);
            Vec3 position = cloud.origin.add(cloud.motion.x * drift + .023 * age * wind,
                    cloud.motion.y * (drift + age * .18), cloud.motion.z * drift + age * .014).subtract(viewer);
            double distanceSquared = position.lengthSqr();
            if (distanceSquared >= 100 * 100) continue;
            float radius = cloud.size + cloud.growth * (float) Math.sqrt(progress);
            double extent = radius * 1.42;
            double x = position.x + viewer.x, y = position.y + viewer.y, z = position.z + viewer.z;
            if (frustum != null && !frustum.isVisible(new AABB(x - extent, y - extent, z - extent,
                    x + extent, y + extent, z + extent))) continue;
            double distance = Math.sqrt(distanceSquared);
            float fade = Mth.clamp((float) (100 - distance) / 27, 0, 1);
            float envelope = Math.min(1, (age + 1) / 6) * (1 - progress * progress);
            int alpha = Math.round(255 * envelope * cloud.opacity * fade);
            if (alpha == 0) continue;
            submitted = true;
            float angle = cloud.angle + age * .008F;
            float cos = Mth.cos(angle) * radius, sin = Mth.sin(angle) * radius;
            float u = cloud.variant % 2 * .5F + .006F, v = cloud.variant / 2 * .5F + .006F;
            float edge = .488F;
            vertex(buffer, pose, position, right, up, -cos + sin, -sin - cos, u, v + edge, cloud.shade, alpha);
            vertex(buffer, pose, position, right, up, cos + sin, sin - cos, u + edge, v + edge, cloud.shade, alpha);
            vertex(buffer, pose, position, right, up, cos - sin, sin + cos, u + edge, v, cloud.shade, alpha);
            vertex(buffer, pose, position, right, up, -cos - sin, cos - sin, u, v, cloud.shade, alpha);
        }
        if (submitted) buffer.setQuadSorting(VertexSorting.DISTANCE_TO_ORIGIN);
        var batch = buffer.endOrDiscardIfEmpty();
        if (batch == null) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        try {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, TEXTURE);
            BufferUploader.drawWithShader(batch);
        } finally {
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f pose, Vec3 position, Vector3f right, Vector3f up,
                               float x, float y, float u, float v, int shade, int alpha) {
        buffer.vertex(pose, (float) position.x + right.x * x + up.x * y,
                        (float) position.y + right.y * x + up.y * y, (float) position.z + right.z * x + up.z * y)
                .uv(u, v).color(shade, shade, Math.min(255, shade + 5), alpha).endVertex();
    }

    private static final class Cloud {
        Vec3 origin, motion;
        long born;
        int life, shade, variant;
        float angle, size, growth, opacity;
    }
}
