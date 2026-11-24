package dev.milmod.client;

import dev.milmod.MilMod;
import dev.milmod.config.ModConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Own render chain so camera and resource-pack effects keep their own lifetime. */
@Mod.EventBusSubscriber(modid = MilMod.ID, value = Dist.CLIENT)
public final class ConcussionBlur {
    private static final ResourceLocation CHAIN = ResourceLocation.tryBuild(MilMod.ID,"shaders/post/concussion.json");
    private static ClientLevel level;
    private static PostChain blur;
    private static List<?> passes = List.of();
    private static boolean unavailable;
    private static int width, height;

    private ConcussionBlur() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        updateLevel(client.level);
        if (level == null || client.player == null || !client.player.isAlive()
                || !ModConfig.CONCUSSION_BLUR.get() || HeavyExplosionClient.intensity(level.getGameTime()) <= 0) clean();
    }

    static void reload(ResourceManager manager) {
        clean();
        unavailable = false;
    }

    private static void updateLevel(ClientLevel next) {
        if (next != level) {
            clean();
            level=next;
            unavailable=false;
        }
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Pre event) {
        var client = Minecraft.getInstance();
        updateLevel(client.level);
        if (level==null || unavailable || !ModConfig.CONCUSSION_BLUR.get()) return;
        float strength=HeavyExplosionClient.intensity(level.getGameTime()+event.getPartialTick());
        if (strength<.018F) {
            clean();
            return;
        }
        var target=client.getMainRenderTarget();
        try {
            if (blur==null) {
                blur=new PostChain(client.getTextureManager(),client.getResourceManager(),target,CHAIN);
                Field field=ObfuscationReflectionHelper.findField(PostChain.class,"f_110009_");
                passes=(List<?>)field.get(blur);
                width=height=0;
            }
            if (width!=target.width || height!=target.height) {
                width=target.width; height=target.height;
                blur.resize(width,height);
            }
            for (Object value : passes) {
                if (value instanceof PostPass pass) {
                    var radius=pass.getEffect().getUniform("Radius");
                    if (radius!=null) radius.set(strength*4.6F);
                }
            }
            blur.process(event.getPartialTick());
        } catch (IOException | IllegalAccessException | RuntimeException exception) {
            // TODO(verify): check resource packs that replace Minecraft's blur shader.
            clean();
            unavailable=true;
            LogUtils.getLogger().debug("MilMod concussion blur skipped", exception);
        } finally {
            target.bindWrite(true);
        }
    }

    private static void clean() {
        if (blur!=null) blur.close();
        blur=null;
        passes=List.of();
    }
}
