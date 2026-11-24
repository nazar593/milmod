package dev.milmod;

import dev.milmod.command.ModCommands;
import dev.milmod.config.ModConfig;
import dev.milmod.entity.VehicleHits;
import dev.milmod.network.DroneLink;
import dev.milmod.sound.ModSounds;
import dev.milmod.registry.ModContent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MilMod.ID)
public final class MilMod {
    public static final String ID = "milmod";

    public MilMod(FMLJavaModLoadingContext context) {
        ModContent.register(context.getModEventBus());
        ModSounds.register(context.getModEventBus());
        ModConfig.register(context);
        VehicleHits.register(MinecraftForge.EVENT_BUS);
        context.getModEventBus().addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.addListener(ModCommands::register);
        MinecraftForge.EVENT_BUS.addListener(ModContent::removeOldKits);
    }
    private void commonSetup(FMLCommonSetupEvent event) {
        DroneLink.register();
        event.enqueueWork(() -> VehicleHits.registerOptional(MinecraftForge.EVENT_BUS));
    }
}
