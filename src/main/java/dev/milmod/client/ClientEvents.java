package dev.milmod.client;

import dev.milmod.MilMod;
import dev.milmod.registry.ModContent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MilMod.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        HeavyParticles.register(event);
    }

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModContent.VEHICLE.get(), VehicleRenderer::new);
        event.registerEntityRenderer(ModContent.IMPACT_CHARGE.get(), ImpactChargeRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(PilotView.ZOOM);
        event.register(PilotView.MAVIC_MODE);
        event.register(PilotView.RELEASE);
        event.register(PilotView.TABLET);
        event.register(PilotView.DISPLAY_MAP);
    }

    @SubscribeEvent
    public static void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) VehicleRenderer::reload);
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) ConcussionBlur::reload);
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) MavicCameraEffect::reload);
    }
}
