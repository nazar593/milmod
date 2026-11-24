package dev.milmod.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.milmod.MilMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Pooled full-bright billboards approximate scattering, with no light created by the render pass. */
@Mod.EventBusSubscriber(modid = MilMod.ID, value = Dist.CLIENT)
public final class GlowSprites {
    public enum Style {
        FLASH(4, .60F, 2.65F, 255, 244, 211, 1F, false),
        HALO(8, 4.2F, 6.2F, 255, 123, 27, .43F, false),
        LAUNCH(4, .25F, 1.25F, 255, 234, 187, .95F, false),
        LAUNCH_HALO(5, 1.25F, 2.6F, 255, 137, 47, .32F, false),
        SHOCK(11, .36F, 4.2F, 255, 212, 172, .35F, true),
        JET(4, .14F, .42F, 255, 133, 53, .65F, false),
        HEAT(7, .26F, .63F, 255, 196, 148, .12F, true);

        final int life;
        final float start, end, alpha;
        final int red, green, blue;
        final boolean ring;

        Style(int life, float start, float end, int red, int green, int blue, float alpha, boolean ring) {
            this.life = life;
            this.start = start;
            this.end = end;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
            this.ring = ring;
        }
    }

    private static final ResourceLocation CORE = ResourceLocation.tryBuild(MilMod.ID, "textures/effect/glow.png");
    private static final ResourceLocation RING = ResourceLocation.tryBuild(MilMod.ID, "textures/effect/ring.png");
    private static final Slot[] SLOTS = new Slot[48];
    private static ClientLevel level;

    static {
        for (int i = 0; i < SLOTS.length; i++) SLOTS[i] = new Slot();
    }

    private GlowSprites() {}

    public static void add(ClientLevel world, Vec3 point, Style style) {
        updateLevel(world);
        long now = world.getGameTime();
        for (Slot slot : SLOTS) {
            if (slot.style != null && now - slot.born < slot.style.life) continue;
            slot.position = point;
            slot.style = style;
            slot.born = now;
            return;
        }
    }

    private static void updateLevel(ClientLevel next) {
        if (next != level) {
            level = next;
            for (Slot slot : SLOTS) slot.style = null;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) updateLevel(Minecraft.getInstance().level);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || level == null) return;
        long now = level.getGameTime();
        boolean visible = false;
        for (Slot slot : SLOTS) {
            if (slot.style != null && now - slot.born < slot.style.life) {
                visible = true;
                break;
            }
        }
        if (!visible) return;
        var camera = event.getCamera();
        Quaternionf rotation = camera.rotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(rotation);
        Vector3f up = new Vector3f(0, 1, 0).rotate(rotation);
        Matrix4f pose = event.getPoseStack().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        try {
            for (int pass = 0; pass < 2; pass++) {
                boolean ring = pass == 1;
                var buffer = Tesselator.getInstance().getBuilder();
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                for (Slot slot : SLOTS) {
                    Style style = slot.style;
                    if (style == null || style.ring != ring) continue;
                    float age = (now - slot.born + event.getPartialTick()) / style.life;
                    if (age >= 1 || age < 0) continue;
                    Vec3 position = slot.position.subtract(camera.getPosition());
                    float distance = (float) position.length();
                    int opacity = Math.round(255 * style.alpha * (1 - age) * (1 - age)
                            * Math.max(0, 1 - distance / 100));
                    if (opacity <= 0) continue;
                    float size = style.start + (style.end - style.start) * age;
                    put(buffer, pose, position, right, up, -size, -size, 0, 1, style, opacity);
                    put(buffer, pose, position, right, up, size, -size, 1, 1, style, opacity);
                    put(buffer, pose, position, right, up, size, size, 1, 0, style, opacity);
                    put(buffer, pose, position, right, up, -size, size, 0, 0, style, opacity);
                }
                RenderSystem.setShaderTexture(0, ring ? RING : CORE);
                var batch = buffer.endOrDiscardIfEmpty();
                if (batch != null) BufferUploader.drawWithShader(batch);
            }
        } finally {
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    private static void put(BufferBuilder buffer, Matrix4f pose, Vec3 point, Vector3f right, Vector3f up,
                            float x, float y, float u, float v, Style style, int alpha) {
        buffer.vertex(pose, (float) point.x + right.x * x + up.x * y,
                (float) point.y + right.y * x + up.y * y, (float) point.z + right.z * x + up.z * y)
                .uv(u, v).color(style.red, style.green, style.blue, alpha).endVertex();
    }

    private static final class Slot {
        private Style style;
        private Vec3 position;
        private long born;
    }
}
