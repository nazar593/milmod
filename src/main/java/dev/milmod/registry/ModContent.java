package dev.milmod.registry;

import dev.milmod.MilMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import dev.milmod.entity.VehicleEntity;
import dev.milmod.entity.FlashLight;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import dev.milmod.entity.ImpactCharge;
import dev.milmod.item.VehicleItem;
import dev.milmod.item.TabletItem;
import dev.milmod.item.DroneGoggles;
import dev.milmod.model.VehicleProfile;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.MissingMappingsEvent;

public final class ModContent {
    private static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MilMod.ID);
    public static final RegistryObject<SimpleParticleType> FLASH_CORE =
            PARTICLES.register("flash_core", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> FIRE_SMOKE_PLUME =
            PARTICLES.register("fire_smoke_plume", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> EXPLOSION_SPARK =
            PARTICLES.register("explosion_spark", () -> new SimpleParticleType(false));
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MilMod.ID);
    public static final RegistryObject<Block> FLASH_LIGHT = BLOCKS.register("flash_light",
            () -> new FlashLight(BlockBehaviour.Properties.copy(Blocks.LIGHT).noLootTable()));
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MilMod.ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MilMod.ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MilMod.ID);
    private static final Map<VehicleProfile, RegistryObject<Item>> KITS = new EnumMap<>(VehicleProfile.class);
    public static final RegistryObject<EntityType<VehicleEntity>> VEHICLE = ENTITIES.register("vehicle",
            () -> EntityType.Builder.<VehicleEntity>of(VehicleEntity::new, MobCategory.MISC)
                    .sized(1, 1).clientTrackingRange(10).updateInterval(2).build("milmod:vehicle"));

    public static final RegistryObject<EntityType<ImpactCharge>> IMPACT_CHARGE = ENTITIES.register("impact_charge",
            () -> EntityType.Builder.<ImpactCharge>of(ImpactCharge::new, MobCategory.MISC)
                    .sized(0.29F, 0.29F).clientTrackingRange(8).updateInterval(2).build("milmod:impact_charge"));

    public static final RegistryObject<Item> CONTROLLER =
            ITEMS.register("controller", TabletItem::new);
    public static final RegistryObject<Item> MAVIC_CONTROLLER =
            ITEMS.register("mavic_controller", () -> new TabletItem(true));
    public static final RegistryObject<Item> MAVIC_GOGGLES =
            ITEMS.register("mavic_goggles", DroneGoggles::new);
    public static final RegistryObject<Item> MESH_MODEM =
            ITEMS.register("mesh_modem", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FLIGHT_FUEL =
            ITEMS.register("flight_fuel", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> DUMMY_GRENADE =
            ITEMS.register("training_capsule", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> DUMMY_KAB =
            ITEMS.register("training_kab", () -> new Item(new Item.Properties().stacksTo(16)));

    static {
        for (VehicleProfile profile : VehicleProfile.values()) {
            if (profile.available()) KITS.put(profile, ITEMS.register(profile.id, () -> new VehicleItem(profile)));
        }
    }

    private static final RegistryObject<CreativeModeTab> TAB = TABS.register("equipment",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.milmod.equipment"))
                    .icon(() -> CONTROLLER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(CONTROLLER.get());
                        output.accept(MAVIC_CONTROLLER.get());
                        output.accept(MAVIC_GOGGLES.get());
                        output.accept(FLIGHT_FUEL.get());
                        output.accept(MESH_MODEM.get());
                        output.accept(DUMMY_GRENADE.get());
                        output.accept(DUMMY_KAB.get());
                        KITS.values().forEach(item -> output.accept(item.get()));
                    }).build());

    private ModContent() {}

    public static Item kit(VehicleProfile profile) {
        return KITS.get(profile).get();
    }

    public static void removeOldKits(MissingMappingsEvent event) {
        for (var mapping : event.getMappings(Registries.ITEM, MilMod.ID)) {
            var old = VehicleProfile.byId(mapping.getKey().getPath());
            if (!old.id.equals(mapping.getKey().getPath()) || old.available()) continue;
            if (old.oldStand()) mapping.remap(kit(VehicleProfile.LAUNCHER));
            else mapping.ignore();
        }
    }

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
        BLOCKS.register(bus);
        ENTITIES.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
