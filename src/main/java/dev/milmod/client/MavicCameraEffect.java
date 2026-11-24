package dev.milmod.client;

import com.mojang.logging.LogUtils;
import dev.milmod.MilMod;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MilMod.ID, value = Dist.CLIENT)
public final class MavicCameraEffect {
    private static final ResourceLocation CHAIN = ResourceLocation.tryBuild(MilMod.ID, "shaders/post/mavic_mono.json");
    private static ClientLevel level;
    private static PostChain effect;
    private static boolean unavailable;
    private static int width;
    private static int height;

    private MavicCameraEffect() {}

    static boolean available() { return !unavailable; }

    static void reload(ResourceManager manager) {
        close();
        unavailable = false;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        if (level != client.level) {
            close();
            level = client.level;
            unavailable = false;
        }
        if (!PilotView.mavicMonochrome(client)) close();
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Pre event) {
        var client = Minecraft.getInstance();
        if (unavailable || !PilotView.mavicMonochrome(client)) return;
        var target = client.getMainRenderTarget();
        try {
            if (effect == null) {
                effect = new PostChain(client.getTextureManager(), client.getResourceManager(), target, CHAIN);
                width = height = 0;
            }
            if (width != target.width || height != target.height) {
                width = target.width;
                height = target.height;
                effect.resize(width, height);
            }
            effect.process(event.getPartialTick());
        } catch (IOException | RuntimeException error) {
            close();
            unavailable = true;
            LogUtils.getLogger().warn("MilMod could not start the Mavic monochrome view", error);
        } finally {
            target.bindWrite(true);
        }
    }

    private static void close() {
        if (effect != null) effect.close();
        effect = null;
    }
}
